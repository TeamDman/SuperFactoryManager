use crate::paths::AppHome;
use crate::paths::InvocationDirectory;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::fs::write;
use std::path::PathBuf;
use tracing::info;

/// Arguments for setting the configured repo root.
#[derive(Facet, Debug)]
pub struct RepoRootSetArgs {
    /// The path to the repo root.
    #[facet(args::positional)]
    pub path: PathBuf,
}

impl RepoRootSetArgs {
    /// # Errors
    ///
    /// Returns an error if the path cannot be canonicalized or persisted.
    pub fn invoke_in(
        self,
        invocation_dir: &InvocationDirectory<'_>,
        app_home: &AppHome,
    ) -> eyre::Result<()> {
        let requested = invocation_dir.resolve(&self.path)?;
        let canonical = dunce::canonicalize(&requested)
            .wrap_err_with(|| format!("Failed to canonicalize path: {}", requested.display()))?;
        let repo_root_file = app_home.file_path(super::repo_root_cli::REPO_ROOT_FILE);

        app_home.ensure_dir()?;

        write(&repo_root_file, canonical.display().to_string())
            .wrap_err("Failed to write repo root file")?;

        info!("Set repo root to: {}", canonical.display());
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::RepoRootSetArgs;
    use crate::cli::repo_root::get_repo_root;
    use crate::paths::AppHome;
    use crate::paths::InvocationDirectory;
    use std::fs::create_dir;
    use std::path::PathBuf;

    #[test]
    fn relative_repo_root_is_persisted_from_the_invocation_directory() -> eyre::Result<()> {
        let fixture = tempfile::tempdir()?;
        let invocation_dir = InvocationDirectory::new(fixture.path())?;
        let checkout = fixture.path().join("checkout");
        create_dir(&checkout)?;
        let app_home = AppHome(fixture.path().join("settings"));
        RepoRootSetArgs {
            path: PathBuf::from("checkout"),
        }
        .invoke_in(&invocation_dir, &app_home)?;
        assert_eq!(get_repo_root(&app_home)?, dunce::canonicalize(checkout)?);
        Ok(())
    }
}
