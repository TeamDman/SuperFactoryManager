use crate::cli::jar::BranchSelector;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use crate::prism::PrismLoaderSelection;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for synchronizing Prism Launcher SFM verification instances.
#[derive(Facet, Debug)]
pub struct ClientSyncArgs {
    /// Branch selector used to choose Minecraft versions to synchronize.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// Loader version source to write into Prism metadata.
    #[facet(default = PrismLoaderSelection::Pinned, args::named)]
    pub loader: PrismLoaderSelection,

    /// Explicit exact-major JDK home for the Prism runtime, overriding a target lockfile pin.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
}

impl ClientSyncArgs {
    /// # Errors
    ///
    /// Returns an error if configured instances cannot be created, tracked, or updated.
    pub fn invoke(self, app_home: &AppHome, cache_home: &CacheHome) -> eyre::Result<()> {
        super::client_cli::sync_clients(
            self.branch,
            self.loader,
            self.java_home.as_deref(),
            app_home,
            cache_home,
        )
    }
}
