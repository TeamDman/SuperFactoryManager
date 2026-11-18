use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for tracking server directories.
#[derive(Facet, Debug)]
pub struct ServerAddArgs {
    /// Glob pattern for server directories.
    #[facet(args::positional)]
    pub glob: String,
}

impl ServerAddArgs {
    /// # Errors
    ///
    /// Returns an error if server targets cannot be resolved or persisted.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        super::server_cli::add_servers(&self.glob, app_home)
    }
}
