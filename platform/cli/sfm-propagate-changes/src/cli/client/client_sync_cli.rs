use crate::cli::jar::BranchSelector;
use crate::prism::PrismLoaderSelection;
use facet::Facet;
use figue as args;

/// Arguments for synchronizing Prism Launcher SFM verification instances.
#[derive(Facet, Debug)]
pub struct ClientSyncArgs {
    /// Branch selector used to choose Minecraft versions to synchronize.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// Loader version source to write into Prism metadata.
    #[facet(default = PrismLoaderSelection::Pinned, args::named)]
    pub loader: PrismLoaderSelection,
}

impl ClientSyncArgs {
    /// # Errors
    ///
    /// Returns an error if configured instances cannot be created, tracked, or updated.
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::sync_clients(self.branch, self.loader)
    }
}
