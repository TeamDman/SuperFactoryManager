use crate::cli::jar::BranchSelector;
use crate::paths::AppHome;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for launching tracked servers.
#[derive(Facet, Debug)]
pub struct ServerLaunchArgs {
    /// Branch selector used to choose tracked server Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Exact-major JDK home for all selected servers. Without this, use unpinned local JDK discovery; tracked servers have no branch lockfile context.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
}

impl ServerLaunchArgs {
    /// # Errors
    ///
    /// Returns an error if selected tracked servers cannot be launched.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        super::server_cli::launch_servers(self.branch, self.java_home.as_deref(), app_home)
    }
}
