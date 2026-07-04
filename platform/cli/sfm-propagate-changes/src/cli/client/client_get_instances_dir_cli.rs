use facet::Facet;

/// Arguments for showing the configured Prism Launcher instances directory.
#[derive(Facet, Debug)]
pub struct ClientGetInstancesDirArgs;

impl ClientGetInstancesDirArgs {
    /// # Errors
    ///
    /// Returns an error if the instances directory cannot be read or stdout cannot be written.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::get_instances_dir()
    }
}
