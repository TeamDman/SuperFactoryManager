use super::super::context_manifest::ContextManifest;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::provenance::sha256;
use sha2::Digest as _;
use std::fs::File;
use std::io::Read;
use std::io::Write;
use std::path::Path;
pub(super) fn header(path: &str, size: u64, executable: bool) -> eyre::Result<[u8; 512]> {
    fn octal(bytes: &mut [u8], value: u64) -> eyre::Result<()> {
        let text = format!("{value:0width$o}", width = bytes.len() - 1);
        eyre::ensure!(text.len() < bytes.len(), "USTAR number out of bounds");
        bytes[..text.len()].copy_from_slice(text.as_bytes());
        Ok(())
    }
    crate::source_projection::promotion::validate_relative_path(path)?;
    let mut block = [0_u8; 512];
    if path.len() <= 100 {
        block[..path.len()].copy_from_slice(path.as_bytes());
    } else {
        let split = path
            .char_indices()
            .rev()
            .find(|(offset, ch)| *ch == '/' && *offset <= 155 && path.len() - *offset - 1 <= 100)
            .map(|(offset, _)| offset)
            .ok_or_else(|| {
                eyre::eyre!("context path exceeds supported USTAR name/prefix bounds: {path}")
            })?;
        block[..path.len() - split - 1].copy_from_slice(&path.as_bytes()[split + 1..]);
        block[345..345 + split].copy_from_slice(&path.as_bytes()[..split]);
    }
    octal(&mut block[100..108], if executable { 0o755 } else { 0o644 })?;
    octal(&mut block[108..116], 10001)?;
    octal(&mut block[116..124], 10001)?;
    octal(&mut block[124..136], size)?;
    octal(&mut block[136..148], 0)?;
    block[148..156].fill(b' ');
    block[156] = b'0';
    block[257..263].copy_from_slice(b"ustar\0");
    block[263..265].copy_from_slice(b"00");
    let sum: u64 = block.iter().map(|byte| u64::from(*byte)).sum();
    let checksum = format!("{sum:06o}\0 ");
    block[148..156].copy_from_slice(checksum.as_bytes());
    Ok(block)
}
pub(super) fn prepare(
    root: &Path,
    report: &ContextManifest,
    cancellation: &CancellationToken,
) -> eyre::Result<tempfile::NamedTempFile> {
    let _timer =
        super::super::phase_timer::PhaseTimer::start("archive_creation", report.total_bytes);
    // The backend verified the context; this archive hashes each open input again.
    let mut archive = tempfile::NamedTempFile::new()?;
    for (path, row) in &report.files {
        cancellation.bail_if_cancelled()?;
        let mut input = File::open(checked_file(root, path)?)?;
        eyre::ensure!(
            input.metadata()?.len() == row.bytes,
            "context file changed before archive"
        );
        archive.write_all(&header(
            path,
            row.bytes,
            path == "entrypoint.sh" || path == "tools/sfm-propagate-changes",
        )?)?;
        let mut hash = sha2::Sha256::new();
        let mut count = 0_u64;
        let mut bytes = [0_u8; 8192];
        loop {
            cancellation.bail_if_cancelled()?;
            let read = input.read(&mut bytes)?;
            if read == 0 {
                break;
            }
            count += read as u64;
            eyre::ensure!(count <= row.bytes, "context file grew during archive");
            hash.update(&bytes[..read]);
            archive.write_all(&bytes[..read])?;
        }
        eyre::ensure!(
            count == row.bytes && format!("sha256:{:x}", hash.finalize()) == row.sha256,
            "context file changed during archive"
        );
        let padding = (512 - count % 512) % 512;
        archive.write_all(&[0_u8; 512][..usize::try_from(padding)?])?;
    }
    let manifest = facet_json::to_string_pretty(report)?;
    let data = manifest.as_bytes();
    let original = std::fs::read(checked_file(root, "context-manifest.json")?)?;
    eyre::ensure!(
        sha256(&original) == sha256(data),
        "context manifest changed during archive"
    );
    archive.write_all(&header("context-manifest.json", data.len() as u64, false)?)?;
    archive.write_all(data)?;
    archive.write_all(&[0_u8; 512][..(512 - data.len() % 512) % 512])?;
    archive.write_all(&[0_u8; 1024])?;
    archive.flush()?;
    Ok(archive)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn ustar_rejects_escape_and_unsupported_lengths_preserves_linux_mode() {
        assert!(header("../secret", 1, false).is_err());
        assert!(header(&"x".repeat(256), 1, false).is_err());
        let h = header("entrypoint.sh", 4, true).unwrap();
        assert_eq!(&h[100..107], b"0000755");
        assert_eq!(&h[257..263], b"ustar\0");
    }
}
