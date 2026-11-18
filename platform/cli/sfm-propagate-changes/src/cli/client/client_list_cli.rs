use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for listing tracked client directories.
#[derive(Facet, Debug)]
pub struct ClientListArgs {
    /// Glob pattern for tracked client directories.
    #[facet(default, args::positional)]
    pub glob: Option<String>,
}

impl ClientListArgs {
    /// # Errors
    ///
    /// Returns an error if tracked clients cannot be read.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        let glob = self.glob.unwrap_or_else(|| "*".to_string());
        super::client_cli::list_clients(&glob, app_home)
    }
}
