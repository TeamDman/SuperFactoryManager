use super::DependencyAddArgs;
use super::DependencyArtifactArgs;
use super::DependencyComponentArgs;
use super::DependencyListArgs;
use super::DependencyMigrateArgs;
use super::DependencyRefreshArgs;
use super::DependencyRemoveArgs;
use super::DependencyShowArgs;
use super::DependencySourceArgs;
use crate::cancellation::CancellationToken;
use crate::paths::CacheHome;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencyArgs {
    #[facet(args::subcommand)]
    pub command: DependencyCommand,
}

impl DependencyArgs {
    /// # Errors
    ///
    /// Returns an error if the selected dependency command fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let cache_home = CacheHome::resolve()?;
        self.command.invoke(cancellation_token, &cache_home)
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum DependencyCommand {
    /// Add and resolve a schema v3 dependency declaration.
    Add(DependencyAddArgs),
    /// Inspect or update locked dependency artifacts.
    Artifact(DependencyArtifactArgs),
    /// Add or inspect components of an existing dependency.
    Component(DependencyComponentArgs),
    /// List logical dependencies from the schema v3 lockfile.
    List(DependencyListArgs),
    /// Validate or migrate a legacy dependency lockfile to schema v3.
    Migrate(DependencyMigrateArgs),
    /// Remove a non-platform dependency or component.
    Remove(DependencyRemoveArgs),
    /// Explicitly reacquire remote dependency artifacts and update derived checks.
    Refresh(DependencyRefreshArgs),
    /// Show one logical dependency and its components.
    Show(DependencyShowArgs),
    /// Configure, inspect, or acquire dependency sources.
    Source(DependencySourceArgs),
}

impl DependencyCommand {
    /// # Errors
    ///
    /// Returns an error if the selected dependency command fails.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        match self {
            Self::Add(args) => args.invoke(&cancellation_token, cache_home),
            Self::Artifact(args) => args.invoke(&cancellation_token, cache_home),
            Self::Component(args) => args.invoke(&cancellation_token, cache_home),
            Self::List(args) => args.invoke(cancellation_token, cache_home),
            Self::Migrate(args) => args.invoke(cancellation_token),
            Self::Remove(args) => args.invoke(&cancellation_token, cache_home),
            Self::Refresh(args) => args.invoke(&cancellation_token, cache_home),
            Self::Show(args) => args.invoke(cancellation_token, cache_home),
            Self::Source(args) => args.invoke(&cancellation_token, cache_home),
        }
    }
}
