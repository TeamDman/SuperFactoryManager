use crate::cli::jar::BranchSelector;
use facet::Facet;
use figue as args;

/// Arguments for listing tracked server directories.
#[derive(Facet, Debug)]
pub struct ServerListArgs {
    /// Glob pattern for tracked server directories.
    #[facet(default, args::positional)]
    pub glob: Option<String>,

    /// Branch selector used to choose tracked server Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl ServerListArgs {
    /// # Errors
    ///
    /// Returns an error if tracked servers cannot be read or the branch query is invalid.
    pub fn invoke(self) -> eyre::Result<()> {
        let glob = self.glob.unwrap_or_else(|| "*".to_string());
        super::server_cli::list_servers(&glob, self.branch)
    }
}
