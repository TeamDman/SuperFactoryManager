use crate::paths::APP_HOME;
use eyre::Context;
use facet::Facet;
use figue as args;
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
    pub fn invoke(self) -> eyre::Result<()> {
        let canonical = dunce::canonicalize(&self.path)
            .wrap_err_with(|| format!("Failed to canonicalize path: {}", self.path.display()))?;
        let repo_root_file = APP_HOME.file_path(super::repo_root_cli::REPO_ROOT_FILE);

        APP_HOME.ensure_dir()?;

        std::fs::write(&repo_root_file, canonical.display().to_string())
            .wrap_err("Failed to write repo root file")?;

        info!("Set repo root to: {}", canonical.display());
        Ok(())
    }
}
