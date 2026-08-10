use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::dependency_sources::DependencySourceProviderSelector;
use crate::dependency_sources::SourceProviderFilter;
use crate::dependency_sources::acquire_selected_sources;
use crate::dependency_sources::acquisition_targets;
use crate::jar_build::Parallelism;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencySourceAcquireArgs {
    /// Dependency or dependency/component to acquire. Omit only with `--all`.
    #[facet(default, args::positional)]
    pub target: Option<String>,
    /// Acquire the first matching declared provider for every dependency component.
    #[facet(default = false, args::named)]
    pub all: bool,
    /// Built-in provider kind. Defaults to `any`.
    #[facet(default, args::named)]
    pub provider: Option<DependencySourceProviderSelector>,
    /// Stable provider ID, for selecting among providers of the same kind.
    #[facet(default, args::named)]
    pub provider_id: Option<String>,
    /// Run independent source acquisitions in parallel. Bare `--parallel` defaults to 10 workers.
    #[facet(default, args::named)]
    pub parallel: Option<Option<usize>>,
    /// Branch selector to read. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl DependencySourceAcquireArgs {
    pub(crate) fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        let Self {
            target,
            all,
            provider,
            provider_id,
            parallel,
            branch: selected_branch,
        } = self;
        cancellation_token.bail_if_cancelled()?;
        let branch = selected_branch.clone();
        let inventory = load_inventory(selected_branch, cache_home)?;
        let filter = SourceProviderFilter::new(provider, provider_id)?;
        let parallelism = Parallelism::from_cli(parallel)?;
        let targets = acquisition_targets(&inventory, target.as_deref(), all, &filter)?;
        acquire_selected_sources(
            &inventory,
            &targets,
            &branch,
            parallelism,
            cancellation_token,
        )?;
        for target in &targets {
            for root in target.searchable_roots(&inventory) {
                stdout_line(format!(
                    "{} {}: {}",
                    target.identity(),
                    target.provider_id(),
                    root.display()
                ))?;
            }
        }
        Ok(())
    }
}
