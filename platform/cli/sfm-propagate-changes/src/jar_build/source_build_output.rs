//! Resolve locked source-build output paths on the current host.

use std::path::{Path, PathBuf};

pub(super) fn resolve(root: &Path, output: &Path) -> std::io::Result<PathBuf> {
    let portable = output.to_string_lossy().replace('\\', "/");
    if portable.is_empty()
        || portable.contains([':', '\0'])
        || portable
            .split('/')
            .any(|part| part.is_empty() || part == "." || part == "..")
    {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            "source-build output must be a bounded relative path",
        ));
    }
    Ok(root.join(portable))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn windows_lock_spelling_finds_native_output() -> std::io::Result<()> {
        let root = std::env::temp_dir().join(format!("sfm-output-probe-{}", std::process::id()));
        let output = root.join("vox/java/target/output.jar");
        std::fs::create_dir_all(output.parent().unwrap())?;
        std::fs::write(&output, b"pinned output fixture")?;
        let resolved = resolve(&root, Path::new(r"vox\java\target\output.jar"))?;
        assert_eq!(std::fs::read(resolved)?, b"pinned output fixture");
        assert_eq!(
            resolve(&root, Path::new("vox/java/target/output.jar"))?,
            output
        );
        std::fs::remove_file(output)?;
        Ok(())
    }

    #[test]
    fn rejects_absolute_and_escaping_paths() {
        for output in [
            "",
            "/tmp/out.jar",
            r"C:\out.jar",
            "../out.jar",
            r"vox\..\out.jar",
            "./out.jar",
            "vox//out.jar",
            "vox/",
            "out\0.jar",
        ] {
            assert!(
                resolve(Path::new("root"), Path::new(output)).is_err(),
                "{output:?}"
            );
        }
    }
}
