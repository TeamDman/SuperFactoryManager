use super::DependencySourceAcquireArgs;
use super::DependencySourceCacheArgs;
use super::DependencySourceConfigureArgs;
use super::DependencySourceProviderArgs;
use super::DependencySourceSearchArgs;
use crate::cancellation::CancellationToken;
use crate::paths::CacheHome;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencySourceArgs {
    #[facet(args::subcommand)]
    pub command: DependencySourceCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum DependencySourceCommand {
    /// Acquire source trees from already locked provider metadata.
    Acquire(DependencySourceAcquireArgs),
    /// Configure and lock source-provider intent and evidence.
    Configure(DependencySourceConfigureArgs),
    /// Search already acquired source roots without fetching or generating sources.
    Search(DependencySourceSearchArgs),
    /// Inspect configured source providers.
    Provider(DependencySourceProviderArgs),
    /// Inspect or explicitly maintain managed source caches.
    Cache(DependencySourceCacheArgs),
}

impl DependencySourceArgs {
    pub(crate) fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        match self.command {
            DependencySourceCommand::Acquire(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Configure(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Search(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Provider(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Cache(args) => args.invoke(cancellation_token, cache_home),
        }
    }
}
