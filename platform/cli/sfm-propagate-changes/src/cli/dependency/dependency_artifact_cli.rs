use super::DependencyArtifactAcceptArgs;
use super::DependencyArtifactImportArgs;
use crate::cancellation::CancellationToken;
use crate::paths::CacheHome;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencyArtifactArgs {
    #[facet(args::subcommand)]
    pub command: DependencyArtifactCommand,
}

impl DependencyArtifactArgs {
    /// # Errors
    ///
    /// Returns an error when the selected artifact operation fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        match self.command {
            DependencyArtifactCommand::Accept(args) => args.invoke(cancellation_token, cache_home),
            DependencyArtifactCommand::Import(_) => {
                unreachable!("project import selects its own catalog-owned cache")
            }
        }
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum DependencyArtifactCommand {
    /// Accept the current bytes and hash for a locked dependency component.
    Accept(DependencyArtifactAcceptArgs),
    /// Admit a user-supplied exact source-built JAR and pinned producer receipt.
    Import(DependencyArtifactImportArgs),
}
