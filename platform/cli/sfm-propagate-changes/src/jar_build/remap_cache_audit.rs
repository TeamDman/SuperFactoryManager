//! Explicit full audit of managed remap outputs. Never seals, repairs or deletes.

use super::managed_remap_receipt;
use crate::artifact_lock::ArtifactLock;
use crate::source_projection::candidate_lock::{checked_directory, is_reparse};
use eyre::{Result, ensure};
use std::fs;
use std::path::{Path, PathBuf};

const MAX_KEYS: usize = 4_096;
const MAX_ENTRIES_PER_KEY: usize = 256;

#[derive(Default, Debug)]
pub(crate) struct RemapAuditSummary {
    pub(crate) keys: usize,
    pub(crate) scratch_roots: usize,
    pub(crate) verified: usize,
    pub(crate) incomplete: Vec<PathBuf>,
    pub(crate) damaged: Vec<(PathBuf, String)>,
}

fn valid_key(key: &str) -> bool {
    key.len() == 64
        && key
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase())
}

fn valid_entry(name: &str) -> bool {
    name.starts_with("entry-")
        && name.len() > 6
        && name.len() <= 96
        && name[6..]
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
}

pub(crate) fn audit_managed_remaps(root: &Path, key: Option<&str>) -> Result<RemapAuditSummary> {
    checked_directory(root)?;
    let mut keys = Vec::new();
    let mut scratch_roots = 0;
    if let Some(key) = key {
        ensure!(valid_key(key), "Invalid remap audit key");
        keys.push(key.to_owned());
    } else {
        for item in fs::read_dir(root)? {
            let item = item?;
            let name = item.file_name();
            let name = name
                .to_str()
                .ok_or_else(|| eyre::eyre!("Non-UTF8 remap key"))?;
            let metadata = fs::symlink_metadata(item.path())?;
            ensure!(
                !is_reparse(&metadata),
                "Remap store contains a reparse point"
            );
            if name == "work" {
                ensure!(metadata.is_dir(), "Remap work root is not a directory");
                checked_directory(&item.path())?;
                scratch_roots += 1;
                continue;
            }
            if let Some(key) = name.strip_suffix(".lock") {
                ensure!(
                    valid_key(key) && metadata.is_file(),
                    "Unexpected remap lock file"
                );
                continue;
            }
            ensure!(
                valid_key(name) && metadata.is_dir(),
                "Unexpected remap store child: {name}"
            );
            keys.push(name.to_owned());
            ensure!(keys.len() <= MAX_KEYS, "Too many remap audit keys");
        }
        keys.sort();
    }
    let mut summary = RemapAuditSummary {
        scratch_roots,
        ..RemapAuditSummary::default()
    };
    for key in keys {
        let directory = root.join(&key);
        checked_directory(&directory)?;
        let _lock = ArtifactLock::acquire(
            root.join(format!("{key}.lock")),
            format!("remap audit {key}"),
        )?;
        summary.keys += 1;
        let mut entries = 0;
        for item in fs::read_dir(&directory)? {
            let item = item?;
            entries += 1;
            ensure!(
                entries <= MAX_ENTRIES_PER_KEY,
                "Too many remap entries in {key}"
            );
            let path = item.path();
            let name = item.file_name();
            let name = name
                .to_str()
                .ok_or_else(|| eyre::eyre!("Non-UTF8 remap entry"))?;
            ensure!(
                valid_entry(name),
                "Unexpected remap entry: {}",
                path.display()
            );
            checked_directory(&path)?;
            if !path.join("inputs.sha256").exists() {
                summary.incomplete.push(path);
                continue;
            }
            match managed_remap_receipt::audit_full(&path, &format!("sha256:{key}"), &|| Ok(())) {
                Ok(true) => summary.verified += 1,
                Ok(false) => summary
                    .damaged
                    .push((path, "output or receipt mismatch".into())),
                Err(error) => summary.damaged.push((path, format!("{error:#}"))),
            }
        }
    }
    Ok(summary)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::jar_build::engine::project_compilation_cache;

    #[test]
    fn full_audit_finds_changed_bytes_and_retains_unsealed_entries() -> Result<()> {
        let root = tempfile::tempdir()?;
        let key = "a".repeat(64);
        let entry = root.path().join(&key).join("entry-fixture");
        let payload = entry.join("payload");
        fs::create_dir_all(&payload)?;
        fs::write(payload.join("specialsource.jar"), b"special")?;
        fs::write(payload.join("remapped.jar"), b"remapped")?;
        project_compilation_cache::record(
            &entry,
            &entry.join("inputs.sha256"),
            &format!("sha256:{key}"),
            &[("payload", &payload)],
            || Ok(()),
        )?;
        fs::create_dir(root.path().join("work"))?;
        let first = audit_managed_remaps(root.path(), None)?;
        assert_eq!(
            (
                first.keys,
                first.verified,
                first.incomplete.len(),
                first.damaged.len(),
                first.scratch_roots
            ),
            (1, 1, 0, 0, 1)
        );
        assert!(!entry.join("managed-remap.sealed").exists());
        assert!(managed_remap_receipt::matches_or_seals(
            &entry,
            &format!("sha256:{key}"),
            &|| Ok(())
        )?);
        fs::write(entry.join("managed-remap.sealed"), b"wrong")?;
        assert_eq!(
            audit_managed_remaps(root.path(), Some(&key))?.damaged.len(),
            1
        );
        fs::remove_file(entry.join("managed-remap.sealed"))?;
        fs::write(payload.join("remapped.jar"), b"REMAPPED")?;
        let second = audit_managed_remaps(root.path(), Some(&key))?;
        assert_eq!((second.verified, second.damaged.len()), (0, 1));
        fs::remove_file(entry.join("inputs.sha256"))?;
        let third = audit_managed_remaps(root.path(), Some(&key))?;
        assert_eq!((third.incomplete.len(), third.damaged.len()), (1, 0));
        fs::create_dir(root.path().join("unexpected"))?;
        assert!(audit_managed_remaps(root.path(), None).is_err());
        Ok(())
    }
}
