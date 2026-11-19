use eyre::Result;
use eyre::ensure;
use std::path::Path;
use std::path::PathBuf;

/// Absolute base for paths supplied to a command invocation.
#[derive(Clone, Copy, Debug)]
pub struct InvocationDirectory<'a>(&'a Path);

impl<'a> InvocationDirectory<'a> {
    /// # Errors
    ///
    /// Rejects a relative base.
    pub fn new(path: &'a Path) -> Result<Self> {
        ensure!(path.is_absolute(), "invocation directory must be absolute");
        Ok(Self(path))
    }

    #[must_use]
    pub const fn as_path(&self) -> &'a Path {
        self.0
    }

    /// Bind an input or output path to this directory without touching the filesystem.
    ///
    /// # Errors
    ///
    /// Rejects empty paths and paths that still require a process directory,
    /// such as a Windows drive-relative path.
    pub fn resolve(&self, path: &Path) -> Result<PathBuf> {
        ensure!(
            !path.as_os_str().is_empty(),
            "invocation path must not be empty"
        );
        let resolved = self.0.join(path);
        ensure!(
            resolved.is_absolute(),
            "cannot resolve '{}' relative to '{}'",
            path.display(),
            self.0.display()
        );
        Ok(resolved)
    }
}

#[cfg(test)]
mod tests {
    use super::InvocationDirectory;
    use std::path::Path;

    #[test]
    fn resolves_paths_against_the_supplied_absolute_directory() -> eyre::Result<()> {
        let base = tempfile::tempdir()?;
        let directory = InvocationDirectory::new(base.path())?;
        let output = base.path().join("reports/plan.json");
        assert_eq!(directory.resolve(Path::new("reports/plan.json"))?, output);
        assert_eq!(directory.resolve(&output)?, output);
        assert!(directory.resolve(Path::new("")).is_err());
        assert!(InvocationDirectory::new(Path::new(".")).is_err());
        #[cfg(windows)]
        assert!(directory.resolve(Path::new("C:relative")).is_err());
        Ok(())
    }
}
