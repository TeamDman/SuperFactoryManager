use super::DependencyComponentAddArgs;
use crate::cancellation::CancellationToken;
use crate::paths::CacheHome;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencyComponentArgs {
    #[facet(args::subcommand)]
    pub command: DependencyComponentCommand,
}

impl DependencyComponentArgs {
    /// # Errors
    ///
    /// Returns an error when the selected component operation fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        match self.command {
            DependencyComponentCommand::Add(args) => args.invoke(cancellation_token, cache_home),
        }
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum DependencyComponentCommand {
    /// Add and resolve a component on an existing dependency.
    Add(DependencyComponentAddArgs),
}
