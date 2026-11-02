//! A completed remap pair is immutable to SFM writers. The legacy full
//! inventory authenticates an unsealed entry once; later hits trust its small
//! completion receipt while the selected output stays held read-only.

use super::project_compilation_cache;
use eyre::{Result, ensure};
use facet::Facet;
use sha2::{Digest, Sha256};
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;

const SCHEMA: &str = "sfm:managed-remap-seal@1";
const MAX_MANIFEST_BYTES: u64 = 8 * 1024 * 1024;
const MAX_OUTPUT_BYTES: u64 = 128 * 1024 * 1024;

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Manifest {
    schema: String,
    input_fingerprint: String,
    outputs: Vec<Entry>,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Entry {
    relative_path: String,
    bytes: u64,
    sha256: String,
}

pub(super) fn matches_or_seals(
    entry: &Path,
    fingerprint: &str,
    check: &impl Fn() -> Result<()>,
) -> Result<bool> {
    check()?;
    let result = checked_match_or_seal(entry, fingerprint, check);
    check()?;
    match result {
        Ok(hit) => Ok(hit),
        Err(error) => {
            tracing::debug!(%error, "managed remap receipt miss");
            Ok(false)
        }
    }
}

fn checked_match_or_seal(
    entry: &Path,
    fingerprint: &str,
    check: &impl Fn() -> Result<()>,
) -> Result<bool> {
    let state = entry.join("inputs.sha256");
    let mut state_file = project_compilation_cache::open_read(&state)?;
    ensure!(
        state_file.metadata()?.len() <= 128,
        "oversized remap input marker"
    );
    let mut state_bytes = Vec::new();
    state_file.read_to_end(&mut state_bytes)?;
    if state_bytes != fingerprint.as_bytes() {
        return Ok(false);
    }
    let manifest_path = entry.join("inputs.outputs.json");
    let mut file = project_compilation_cache::open_read(&manifest_path)?;
    let length = file.metadata()?.len();
    ensure!(
        length <= MAX_MANIFEST_BYTES,
        "oversized remap output manifest"
    );
    let mut raw = Vec::new();
    file.read_to_end(&mut raw)?;
    ensure!(
        raw.len() as u64 == length,
        "remap manifest changed while reading"
    );
    let manifest: Manifest = facet_json::from_slice(&raw)?;
    if manifest.schema != "sfm:local_compilation_outputs@1"
        || manifest.input_fingerprint != fingerprint
        || manifest.outputs.len() != 2
    {
        return Ok(false);
    }
    let mut found = BTreeSet::new();
    for output in &manifest.outputs {
        if !matches!(
            output.relative_path.as_str(),
            "payload/remapped.jar" | "payload/specialsource.jar"
        ) || !found.insert(output.relative_path.as_str())
            || output.bytes > MAX_OUTPUT_BYTES
            || output.sha256.len() != 64
            || !output
                .sha256
                .bytes()
                .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase())
        {
            return Ok(false);
        }
        let leaf = output.relative_path.strip_prefix("payload/").unwrap();
        let payload = project_compilation_cache::open_read(&entry.join("payload").join(leaf))?;
        if payload.metadata()?.len() != output.bytes {
            return Ok(false);
        }
    }
    if found.len() != 2 {
        return Ok(false);
    }
    let seal = entry.join("managed-remap.sealed");
    let expected = format!("{SCHEMA}\n{fingerprint}\n{:x}\n", Sha256::digest(&raw));
    match fs::symlink_metadata(&seal) {
        Ok(_) => {
            let mut sealed = project_compilation_cache::open_read(&seal)?;
            ensure!(sealed.metadata()?.len() <= 256, "oversized remap seal");
            let mut bytes = Vec::new();
            sealed.read_to_end(&mut bytes)?;
            Ok(bytes == expected.as_bytes())
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            // The producer writes the input marker last. Full verification is
            // mandatory before first adoption, including retained old entries.
            if !project_compilation_cache::matches(
                entry,
                &state,
                fingerprint,
                &[("payload", &entry.join("payload"))],
                check,
            )? {
                return Ok(false);
            }
            check()?;
            let mut temporary = tempfile::Builder::new()
                .prefix(".managed-remap-seal.")
                .tempfile_in(entry)?;
            temporary.write_all(expected.as_bytes())?;
            temporary.as_file_mut().sync_all()?;
            match temporary.persist_noclobber(&seal) {
                Ok(_) => Ok(true),
                Err(error) if error.error.kind() == std::io::ErrorKind::AlreadyExists => {
                    let existing = project_compilation_cache::open_read(&seal)?;
                    let mut bytes = Vec::new();
                    existing.take(257).read_to_end(&mut bytes)?;
                    Ok(bytes == expected.as_bytes())
                }
                Err(error) => Err(error.error.into()),
            }
        }
        Err(error) => Err(error.into()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pair() -> Result<(tempfile::TempDir, std::path::PathBuf)> {
        let root = tempfile::tempdir()?;
        let entry = root.path().join("entry");
        let payload = entry.join("payload");
        fs::create_dir_all(&payload)?;
        fs::write(payload.join("specialsource.jar"), b"special")?;
        fs::write(payload.join("remapped.jar"), b"remapped")?;
        project_compilation_cache::record(
            &entry,
            &entry.join("inputs.sha256"),
            "sha256:fixture",
            &[("payload", payload.as_path())],
            || Ok(()),
        )?;
        Ok((root, entry))
    }

    #[test]
    fn full_first_check_then_cheap_completed_reuse_and_explicit_audit() -> Result<()> {
        let (_root, entry) = pair()?;
        assert!(matches_or_seals(&entry, "sha256:fixture", &|| Ok(()))?);
        assert!(entry.join("managed-remap.sealed").exists());
        fs::write(entry.join("payload/specialsource.jar"), b"changed")?;
        assert!(matches_or_seals(&entry, "sha256:fixture", &|| Ok(()))?);
        assert!(!project_compilation_cache::matches(
            &entry,
            &entry.join("inputs.sha256"),
            "sha256:fixture",
            &[("payload", &entry.join("payload"))],
            || Ok(()),
        )?);
        Ok(())
    }

    #[test]
    fn missing_or_mismatched_receipt_and_payload_refuse_reuse() -> Result<()> {
        let (_root, entry) = pair()?;
        assert!(!matches_or_seals(&entry, "sha256:other", &|| Ok(()))?);
        assert!(matches_or_seals(&entry, "sha256:fixture", &|| Ok(()))?);
        fs::write(entry.join("managed-remap.sealed"), b"wrong")?;
        assert!(!matches_or_seals(&entry, "sha256:fixture", &|| Ok(()))?);
        fs::remove_file(entry.join("managed-remap.sealed"))?;
        fs::remove_file(entry.join("payload/remapped.jar"))?;
        assert!(!matches_or_seals(&entry, "sha256:fixture", &|| Ok(()))?);
        Ok(())
    }

    #[test]
    fn unfinished_publication_never_seals() -> Result<()> {
        let (_root, entry) = pair()?;
        fs::remove_file(entry.join("inputs.sha256"))?;
        assert!(!matches_or_seals(&entry, "sha256:fixture", &|| Ok(()))?);
        assert!(!entry.join("managed-remap.sealed").exists());
        Ok(())
    }
}
