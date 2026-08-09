use crate::cli::jar::BranchSelector;
use facet::Facet;
use figue as args;

/// Arguments for launching tracked servers.
#[derive(Facet, Debug)]
pub struct ServerLaunchArgs {
    /// Branch selector used to choose tracked server Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl ServerLaunchArgs {
    /// # Errors
    ///
    /// Returns an error if selected tracked servers cannot be launched.
    pub fn invoke(self) -> eyre::Result<()> {
        super::server_cli::launch_servers(self.branch)
    }
}
