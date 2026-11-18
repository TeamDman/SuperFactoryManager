use crate::paths::AppHome;
use facet::Facet;

/// Arguments for opening the configured client launcher without launching an instance.
#[derive(Facet, Debug)]
pub struct ClientOpenArgs;

impl ClientOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the launcher path cannot be read or opened.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        super::client_cli::open_client_launcher(app_home)
    }
}
