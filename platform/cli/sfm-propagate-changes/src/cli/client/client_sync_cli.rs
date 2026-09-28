use crate::cli::jar::BranchSelector;
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
    pub fn invoke(self) -> eyre::Result<()> {
        super::client_cli::sync_clients(self.branch, self.loader, self.java_home.as_deref())
    }
}

#[cfg(test)]
mod tests {
    use super::ClientSyncArgs;

    #[test]
    fn client_sync_parses_explicit_java_home() {
        let parsed = figue::from_slice::<ClientSyncArgs>(&[
            "--branch",
            "1.19.2",
            "--java-home",
            "test-jdk-17",
        ])
        .into_result()
        .expect("client sync arguments should parse")
        .get_silent();

        assert_eq!(parsed.branch.as_ref(), "1.19.2");
        assert_eq!(
            parsed.java_home.as_deref(),
            Some(std::path::Path::new("test-jdk-17"))
        );
    }
}
