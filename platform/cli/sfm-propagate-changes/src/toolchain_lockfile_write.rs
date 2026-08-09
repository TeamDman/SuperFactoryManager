use eyre::Context;
use std::io::Write;
use std::path::Path;

pub(crate) fn write_lockfile_atomically(
    path: &Path,
    expected_input: &str,
    output: &[u8],
) -> eyre::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Lockfile has no parent: {}", path.display()))?;
    let mut temporary = tempfile::Builder::new()
        .prefix(".sfm-toolchain.lock.")
        .suffix(".tmp")
        .tempfile_in(parent)
        .wrap_err_with(|| {
            format!(
                "Failed to create a temporary file beside {}",
                path.display()
            )
        })?;
    temporary
        .write_all(output)
        .wrap_err_with(|| format!("Failed to write temporary lockfile for {}", path.display()))?;
    temporary
        .as_file_mut()
        .sync_all()
        .wrap_err_with(|| format!("Failed to sync temporary lockfile for {}", path.display()))?;

    let current = std::fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to re-read {} before replacement", path.display()))?;
    if current != expected_input {
        eyre::bail!(
            "{} changed while the operation was running; no files were modified",
            path.display()
        );
    }
    temporary
        .persist(path)
        .map_err(|error| error.error)
        .wrap_err_with(|| format!("Failed to atomically replace {}", path.display()))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::write_lockfile_atomically;

    #[test]
    fn atomic_write_replaces_expected_content() {
        let directory = tempfile::tempdir().expect("temp directory");
        let path = directory.path().join("lock.json");
        std::fs::write(&path, "old").expect("fixture write");

        write_lockfile_atomically(&path, "old", b"new").expect("atomic replacement");

        assert_eq!(std::fs::read_to_string(path).expect("result read"), "new");
    }

    #[test]
    fn atomic_write_preserves_concurrent_change() {
        let directory = tempfile::tempdir().expect("temp directory");
        let path = directory.path().join("lock.json");
        std::fs::write(&path, "changed").expect("fixture write");

        let error = write_lockfile_atomically(&path, "old", b"new")
            .expect_err("concurrent update should fail");

        assert!(error.to_string().contains("changed while the operation"));
        assert_eq!(
            std::fs::read_to_string(path).expect("result read"),
            "changed"
        );
    }
}
