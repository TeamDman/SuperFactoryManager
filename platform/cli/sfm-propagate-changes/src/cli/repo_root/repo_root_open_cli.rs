use crate::paths::AppHome;
use facet::Facet;

/// Arguments for opening the configured repo root.
#[derive(Facet, Debug)]
pub struct RepoRootOpenArgs;

impl RepoRootOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the repo root cannot be read or opened.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        let path = super::get_repo_root(app_home)?;
        open::that(&path)?;
        Ok(())
    }
}
