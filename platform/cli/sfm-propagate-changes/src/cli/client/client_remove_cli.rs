use facet::Facet;
use figue as args;

/// Arguments for untracking client directories.
#[derive(Facet, Debug)]
pub struct ClientRemoveArgs {
    /// Glob pattern for tracked client directories.
    #[facet(args::positional)]
    pub glob: String,
}

impl ClientRemoveArgs {
    /// # Errors
    ///
    /// Returns an error if client targets cannot be updated.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::remove_clients(&self.glob)
    }
}
