//! Preserve canonical launch evidence while accommodating Windows Java's `MAX_PATH` boundary.
use std::io::Write as _;
use std::path::Path;

pub(super) fn prepare(argfile: &Path) -> eyre::Result<Option<tempfile::NamedTempFile>> {
    if !cfg!(windows) || argfile.as_os_str().to_string_lossy().encode_utf16().count() < 260 {
        return Ok(None);
    }
    let mut copy = tempfile::Builder::new()
        .prefix("sfm-launch-")
        .suffix(".args")
        .tempfile()?;
    eyre::ensure!(
        copy.path()
            .as_os_str()
            .to_string_lossy()
            .encode_utf16()
            .count()
            < 260,
        "Windows Java launch requires an argument-file path shorter than 260 characters; configured temporary directory is too long"
    );
    copy.write_all(&std::fs::read(argfile)?)?;
    copy.flush()?;
    Ok(Some(copy))
}

/// Own the temporary argument file until the caller has reaped its JVM.
pub(super) fn spawn(
    java: &Path,
    argfile: &Path,
    working_dir: &Path,
    env: &std::collections::BTreeMap<String, String>,
) -> eyre::Result<(std::process::Child, Option<tempfile::NamedTempFile>)> {
    use eyre::Context as _;
    use std::process::Command;
    use std::process::Stdio;
    let copy = prepare(argfile)?;
    let launch_argfile = copy.as_ref().map_or(argfile, |copy| copy.path());
    let child = Command::new(java)
        .arg(format!("@{}", launch_argfile.display()))
        .current_dir(working_dir)
        .envs(env)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .wrap_err_with(|| format!("Failed to spawn {}", java.display()))?;
    tracing::info!("minecraft_jvm_spawned");
    Ok((child, copy))
}

#[cfg(test)]
mod tests {
    use super::prepare;

    #[test]
    fn short_argument_file_keeps_original_path() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("launch.java.args");
        std::fs::write(&path, b"-version\n").unwrap();
        assert!(prepare(&path).unwrap().is_none());
    }

    #[cfg(windows)]
    #[test]
    fn long_argument_file_copy_preserves_bytes_and_is_removed_on_drop() {
        let root = tempfile::tempdir().unwrap();
        let dir = root
            .path()
            .join("projection with spaces".repeat(5))
            .join("native-project".repeat(8))
            .join("runGameTestPreview");
        std::fs::create_dir_all(&dir).unwrap();
        let original = dir.join("launch.java.args");
        let bytes = b"-Dfixture=\"spaces and UTF-8: \xc3\xa9\"\r\n-version\r\n";
        std::fs::write(&original, bytes).unwrap();
        let copy = prepare(&original).unwrap().expect("long path needs a copy");
        let copied_path = copy.path().to_owned();
        assert!(
            copied_path
                .as_os_str()
                .to_string_lossy()
                .encode_utf16()
                .count()
                < 260
        );
        assert_eq!(std::fs::read(&copied_path).unwrap(), bytes);
        drop(copy);
        assert!(!copied_path.exists());
        assert_eq!(std::fs::read(original).unwrap(), bytes);
    }
}
