use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for untracking server directories.
#[derive(Facet, Debug)]
pub struct ServerRemoveArgs {
    /// Glob pattern for tracked server directories.
    #[facet(args::positional)]
    pub glob: String,
}

impl ServerRemoveArgs {
    /// # Errors
    ///
    /// Returns an error if server targets cannot be updated.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        super::server_cli::remove_servers(&self.glob, app_home)
    }
}
