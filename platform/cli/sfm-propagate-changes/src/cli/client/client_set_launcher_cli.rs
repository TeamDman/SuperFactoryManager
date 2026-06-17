use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for setting the client launcher path.
#[derive(Facet, Debug)]
pub struct ClientSetLauncherArgs {
    /// Path to the launcher executable.
    #[facet(args::positional)]
    pub path: PathBuf,
}

impl ClientSetLauncherArgs {
    /// # Errors
    ///
    /// Returns an error if the launcher path cannot be validated or persisted.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::set_launcher(&self.path)
    }
}
