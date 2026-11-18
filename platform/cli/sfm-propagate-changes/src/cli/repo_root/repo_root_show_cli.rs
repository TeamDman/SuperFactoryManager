use crate::paths::AppHome;
use crate::terminal_output::stdout_line;
use facet::Facet;

/// Arguments for showing the configured repo root.
#[derive(Facet, Debug)]
pub struct RepoRootShowArgs;

impl RepoRootShowArgs {
    /// # Errors
    ///
    /// Returns an error if the repo root cannot be read or stdout cannot be written.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        let path = super::get_repo_root(app_home)?;
        stdout_line(path.display())
    }
}
