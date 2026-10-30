use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
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
    /// Historical branch selector; choose exactly one of this and --project-root.
    #[facet(default, args::named)]
    pub branch: Option<BranchSelector>,
    /// Exact catalogued generated projection root.
    #[facet(default, args::named)]
    pub project_root: Option<std::path::PathBuf>,
    /// Exact JDK for catalog-owned platform source production.
    #[facet(default, args::named)]
    pub java_home: Option<std::path::PathBuf>,
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
            project_root,
            java_home,
        } = self;
        cancellation_token.bail_if_cancelled()?;
        eyre::ensure!(
            selected_branch.is_some() != project_root.is_some(),
            "source acquire requires exactly one of --branch and --project-root"
        );
        let (branch, inventory) = if let Some(project_root) = project_root {
            let inventory = DependencyInventory::load_project(&project_root, cache_home.clone())?;
            (BranchSelector(inventory.target.branch.0.clone()), inventory)
        } else {
            let branch = selected_branch.expect("one selection checked");
            let inventory = load_inventory(branch.clone(), cache_home)?;
            (branch, inventory)
        };
        let filter = SourceProviderFilter::new(provider, provider_id)?;
        let parallelism = Parallelism::from_cli(parallel)?;
        let targets = acquisition_targets(&inventory, target.as_deref(), all, &filter)?;
        acquire_selected_sources(
            &inventory,
            &targets,
            &branch,
            parallelism,
            java_home.as_deref(),
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
