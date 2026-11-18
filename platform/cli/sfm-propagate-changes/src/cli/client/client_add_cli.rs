use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for tracking client directories.
#[derive(Facet, Debug)]
pub struct ClientAddArgs {
    /// Glob pattern for client directories.
    #[facet(args::positional)]
    pub glob: String,
}

impl ClientAddArgs {
    /// # Errors
    ///
    /// Returns an error if client targets cannot be resolved or persisted.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        super::client_cli::add_clients(&self.glob, app_home)
    }
}
