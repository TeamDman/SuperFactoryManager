use crate::cli::jar::BranchSelector;
use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for launching tracked Prism client instances.
#[derive(Facet, Debug)]
pub struct ClientLaunchArgs {
    /// Branch selector used to choose tracked client Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl ClientLaunchArgs {
    /// # Errors
    ///
    /// Returns an error if selected tracked clients cannot be launched.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        super::client_cli::launch_clients(self.branch, app_home)
    }
}
