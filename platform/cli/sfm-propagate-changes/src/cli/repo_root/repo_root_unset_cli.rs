use crate::paths::APP_HOME;
use eyre::Context;
use facet::Facet;
use tracing::info;

/// Arguments for unsetting the configured repo root.
#[derive(Facet, Debug)]
pub struct RepoRootUnsetArgs;

impl RepoRootUnsetArgs {
    /// # Errors
    ///
    /// Returns an error if the repo root file exists but cannot be removed.
    pub fn invoke(self) -> eyre::Result<()> {
        let repo_root_file = APP_HOME.file_path(super::repo_root_cli::REPO_ROOT_FILE);

        if repo_root_file.exists() {
            std::fs::remove_file(&repo_root_file).wrap_err("Failed to remove repo root file")?;
            info!("Repo root unset");
        } else {
            info!("Repo root was not set");
        }

        Ok(())
    }
}
