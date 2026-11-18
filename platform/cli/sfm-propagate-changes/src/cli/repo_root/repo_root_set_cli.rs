use crate::paths::AppHome;
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
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        let canonical = dunce::canonicalize(&self.path)
            .wrap_err_with(|| format!("Failed to canonicalize path: {}", self.path.display()))?;
        let repo_root_file = app_home.file_path(super::repo_root_cli::REPO_ROOT_FILE);

        app_home.ensure_dir()?;

        write(&repo_root_file, canonical.display().to_string())
            .wrap_err("Failed to write repo root file")?;

        info!("Set repo root to: {}", canonical.display());
        Ok(())
    }
}
