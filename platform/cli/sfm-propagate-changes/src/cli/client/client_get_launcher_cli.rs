use facet::Facet;

/// Arguments for showing the configured client launcher path.
#[derive(Facet, Debug)]
pub struct ClientGetLauncherArgs;

impl ClientGetLauncherArgs {
    /// # Errors
    ///
    /// Returns an error if the launcher path cannot be read or stdout cannot be written.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::get_launcher()
    }
}
