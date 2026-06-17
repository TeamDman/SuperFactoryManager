use facet::Facet;

/// Arguments for launching the configured client launcher.
#[derive(Facet, Debug)]
pub struct ClientLaunchArgs;

impl ClientLaunchArgs {
    /// # Errors
    ///
    /// Returns an error if the launcher path cannot be read or launched.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::launch_client()
    }
}
