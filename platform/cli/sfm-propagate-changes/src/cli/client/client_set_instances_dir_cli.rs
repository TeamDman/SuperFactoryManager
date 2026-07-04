use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for setting the Prism Launcher instances directory.
#[derive(Facet, Debug)]
pub struct ClientSetInstancesDirArgs {
    /// Path to the Prism Launcher instances directory.
    #[facet(args::positional)]
    pub path: PathBuf,
}

impl ClientSetInstancesDirArgs {
    /// # Errors
    ///
    /// Returns an error if the instances directory cannot be validated or persisted.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::set_instances_dir(&self.path)
    }
}
