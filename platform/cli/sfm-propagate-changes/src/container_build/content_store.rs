use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::provenance::sha256;
use sha2::Digest as _;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
/// Store only authored/allowlisted bytes; no links to mutable personal caches.
fn directory(stage: &Path) -> eyre::Result<PathBuf> {
    let parent = checked_directory(
        stage
            .parent()
            .ok_or_else(|| eyre::eyre!("staging parent missing"))?,
    )?;
    let root = parent.join(".sfm-container-blobs");
    std::fs::create_dir_all(&root)?;
    checked_directory(&root)
}
fn verify(root: &Path, name: &str, digest: &str) -> eyre::Result<PathBuf> {
    let path = checked_file(root, name)?;
    let mut input = std::fs::File::open(&path)?;
    let mut hash = sha2::Sha256::new();
    let mut buffer = vec![0_u8; 65536].into_boxed_slice();
    loop {
        let count = input.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hash.update(&buffer[..count]);
    }
    eyre::ensure!(
        format!("sha256:{:x}", hash.finalize()) == digest,
        "content store corrupted: {digest}; no cleanup or substitution attempted"
    );
    Ok(path)
}
fn publish(
    stage: &Path,
    relative: &str,
    digest: &str,
    write: impl FnOnce(&mut std::fs::File) -> eyre::Result<()>,
) -> eyre::Result<()> {
    crate::source_projection::promotion::validate_relative_path(relative)?;
    let root = directory(stage)?;
    let name = digest
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre::eyre!("invalid content-store digest"))?;
    eyre::ensure!(
        name.len() == 64
            && name
                .bytes()
                .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase()),
        "invalid content-store digest"
    );
    if !root.join(name).exists() {
        let mut temporary = tempfile::NamedTempFile::new_in(&root)?;
        write(temporary.as_file_mut())?;
        temporary.as_file_mut().sync_all()?;
        // Concurrent publishers cannot overwrite an existing content object.
        if let Err(error) = temporary.persist_noclobber(root.join(name)) {
            eyre::ensure!(
                error.error.kind() == std::io::ErrorKind::AlreadyExists,
                "content publication failed: {}",
                error.error
            );
        }
    }
    // Hash on every use. File size/mtime never substitutes for validation.
    let source = verify(&root, name, digest)?;
    let destination = stage.join(relative);
    std::fs::create_dir_all(
        destination
            .parent()
            .ok_or_else(|| eyre::eyre!("content destination parent missing"))?,
    )?;
    eyre::ensure!(!destination.exists(), "content output already exists");
    if std::fs::hard_link(&source, &destination).is_err() {
        std::fs::copy(source, destination)?;
    }
    Ok(())
}
pub(super) fn bytes(stage: &Path, relative: &str, bytes: &[u8]) -> eyre::Result<()> {
    publish(stage, relative, &sha256(bytes), |out| {
        out.write_all(bytes)?;
        Ok(())
    })
}
pub(super) fn file(
    stage: &Path,
    relative: &str,
    digest: &str,
    input: &mut std::fs::File,
) -> eyre::Result<()> {
    use std::io::Seek as _;
    publish(stage, relative, digest, |out| {
        input.rewind()?;
        std::io::copy(input, out)?;
        Ok(())
    })
}
