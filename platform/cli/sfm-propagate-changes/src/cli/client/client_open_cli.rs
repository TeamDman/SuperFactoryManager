use facet::Facet;

/// Arguments for opening the configured client launcher without launching an instance.
#[derive(Facet, Debug)]
pub struct ClientOpenArgs;

impl ClientOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the launcher path cannot be read or opened.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::open_client_launcher()
    }
}
