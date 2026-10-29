use super::DependencySourceAcquireArgs;
use super::DependencySourceCacheArgs;
use super::DependencySourceConfigureArgs;
use super::DependencySourceProviderArgs;
use super::DependencySourceSearchArgs;
use crate::branch_targets::select_single_worktree_target;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct DependencySourceArgs {
    /// Guarded source-cache override; defaults to the selected branch checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
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
    ) -> eyre::Result<()> {
        let branch = self.command.branch();
        let target = select_single_worktree_target(&branch.clone().into_query()?)?;
        let selected = crate::jar_build::engine::select_source_cache(
            target.worktree_path.as_path(),
            self.cache_root.as_deref(),
            cancellation_token,
        )?;
        let cache_home = selected.home();
        match self.command {
            DependencySourceCommand::Acquire(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Configure(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Search(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Provider(args) => args.invoke(cancellation_token, cache_home),
            DependencySourceCommand::Cache(args) => args.invoke(cancellation_token, cache_home),
        }
    }
}

impl DependencySourceCommand {
    fn branch(&self) -> &BranchSelector {
        match self {
            Self::Acquire(args) => &args.branch,
            Self::Configure(args) => &args.branch,
            Self::Search(args) => &args.branch,
            Self::Provider(args) => match &args.command {
                super::DependencySourceProviderCommand::List(args) => &args.branch,
            },
            Self::Cache(args) => match &args.command {
                super::dependency_source_cache_cli::DependencySourceCacheCommand::Audit(args) => {
                    &args.branch
                }
                super::dependency_source_cache_cli::DependencySourceCacheCommand::Cleanup(args) => {
                    &args.branch
                }
            },
        }
    }
}

#[cfg(test)]
mod selected_cache_args_tests {
    use super::*;

    #[test]
    fn parses_source_cache_override_without_changing_branch_selection() {
        let parsed = figue::from_slice::<DependencySourceArgs>(&[
            "--cache-root",
            "C:/selected/shared",
            "search",
            "IPeripheral",
            "--branch",
            "1.19.2",
        ])
        .unwrap();
        assert_eq!(parsed.cache_root, Some(PathBuf::from("C:/selected/shared")));
        assert_eq!(parsed.command.branch().as_ref(), "1.19.2");
    }
}
