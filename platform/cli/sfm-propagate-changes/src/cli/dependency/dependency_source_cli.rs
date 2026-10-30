use super::DependencySourceAcquireArgs;
use super::DependencySourceCacheArgs;
use super::DependencySourceConfigureArgs;
use super::DependencySourceProviderArgs;
use super::DependencySourceSearchArgs;
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
    pub(crate) fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<()> {
        let selected = if let DependencySourceCommand::Acquire(args) = &self.command {
            if let Some(project_root) = &args.project_root {
                eyre::ensure!(
                    args.branch.is_none(),
                    "select either --branch or --project-root"
                );
                crate::jar_build::engine::select_project_source_cache(
                    project_root,
                    self.cache_root.as_deref(),
                    cancellation_token,
                )?
            } else {
                let branch = args.branch.as_ref().ok_or_else(|| {
                    eyre::eyre!("source acquire requires --branch or --project-root")
                })?;
                crate::jar_build::engine::select_branch_source_cache(
                    branch,
                    self.cache_root.as_deref(),
                    cancellation_token,
                )?
            }
        } else {
            crate::jar_build::engine::select_branch_source_cache(
                self.command
                    .branch()
                    .expect("non-acquire commands require a branch"),
                self.cache_root.as_deref(),
                cancellation_token,
            )?
        };
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
    fn branch(&self) -> Option<&BranchSelector> {
        match self {
            Self::Acquire(args) => args.branch.as_ref(),
            Self::Configure(args) => Some(&args.branch),
            Self::Search(args) => Some(&args.branch),
            Self::Provider(args) => match &args.command {
                super::DependencySourceProviderCommand::List(args) => Some(&args.branch),
            },
            Self::Cache(args) => match &args.command {
                super::dependency_source_cache_cli::DependencySourceCacheCommand::Audit(args) => {
                    Some(&args.branch)
                }
                super::dependency_source_cache_cli::DependencySourceCacheCommand::Cleanup(args) => {
                    Some(&args.branch)
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
        assert_eq!(parsed.command.branch().expect("branch").as_ref(), "1.19.2");
    }

    #[test]
    fn parses_catalog_source_acquisition_without_a_historical_branch() {
        let parsed = figue::from_slice::<DependencySourceArgs>(&[
            "acquire",
            "minecraft/main",
            "--project-root",
            "D:/repo/projections/sfm-dev/mc-1.21.0",
        ])
        .unwrap();
        let DependencySourceCommand::Acquire(acquire) = parsed.command else {
            panic!("expected acquire");
        };
        assert!(acquire.branch.is_none());
        assert_eq!(
            acquire.project_root,
            Some(PathBuf::from("D:/repo/projections/sfm-dev/mc-1.21.0"))
        );
    }
}
