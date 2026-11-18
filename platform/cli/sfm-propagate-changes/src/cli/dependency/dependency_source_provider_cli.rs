use super::DependencySourceProviderListArgs;
use crate::cancellation::CancellationToken;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencySourceProviderArgs {
    #[facet(args::subcommand)]
    pub command: DependencySourceProviderCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum DependencySourceProviderCommand {
    /// List configured source providers without network or cache writes.
    List(DependencySourceProviderListArgs),
}

impl DependencySourceProviderArgs {
    pub(crate) fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
        app_home: &AppHome,
    ) -> eyre::Result<()> {
        match self.command {
            DependencySourceProviderCommand::List(args) => {
                args.invoke(cancellation_token, cache_home, app_home)
            }
        }
    }
}
