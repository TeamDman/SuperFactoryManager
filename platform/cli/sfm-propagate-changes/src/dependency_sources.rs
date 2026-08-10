use crate::cancellation::CancellationToken;
use crate::cli::Cli;
use crate::cli::Command as CliCommand;
use crate::cli::dependency::DependencyArgs;
use crate::cli::dependency::DependencyCommand;
use crate::cli::dependency::DependencySourceAcquireArgs;
use crate::cli::dependency::DependencySourceArgs;
use crate::cli::dependency::DependencySourceCommand;
use crate::cli::global_args::GlobalArgs;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::SourceStatus;
use crate::jar_build::BuildMode;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use crate::jar_build::SourceOutputCommand;
use crate::jar_build::SourceOutputLayout;
use crate::jar_build::SourceOutputOptions;
use crate::payload_fetcher::http_fetcher;
use crate::source_decompile::acquire_locked_decompiled_sources;
use crate::source_git::acquire_locked_git_sources;
use crate::source_maven::acquire_locked_maven_sources;
use crate::source_provider::SourceProviderKind;
use crate::source_provider::SourceProviderView;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use facet::Facet;
use figue::ToArgs;
use std::collections::VecDeque;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::mpsc;
use std::thread;

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DependencySourceProviderSelector {
    Any,
    MavenSources,
    Git,
    Decompile,
    PlatformPipeline,
}

impl DependencySourceProviderSelector {
    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::Any => "any",
            Self::MavenSources => "maven-sources",
            Self::Git => "git",
            Self::Decompile => "decompile",
            Self::PlatformPipeline => "platform-pipeline",
        }
    }
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub(crate) struct SourceProviderFilter {
    pub(crate) provider: Option<DependencySourceProviderSelector>,
    pub(crate) provider_id: Option<String>,
}

impl SourceProviderFilter {
    pub(crate) fn new(
        provider: Option<DependencySourceProviderSelector>,
        provider_id: Option<String>,
    ) -> eyre::Result<Self> {
        if provider.is_some() && provider_id.is_some() {
            eyre::bail!("Use either --provider or --provider-id, not both.");
        }
        Ok(Self {
            provider,
            provider_id,
        })
    }

    #[must_use]
    pub(crate) fn label(&self) -> &str {
        self.provider_id.as_deref().unwrap_or_else(|| {
            self.provider
                .unwrap_or(DependencySourceProviderSelector::Any)
                .label()
        })
    }

    #[must_use]
    pub(crate) fn matches(&self, provider: &SourceProviderV3) -> bool {
        self.provider_id.as_deref().map_or_else(
            || {
                self.provider
                    .is_none_or(|selector| provider_matches(provider, selector))
            },
            |id| provider_id(provider) == id,
        )
    }

    #[must_use]
    pub(crate) const fn is_constrained(&self) -> bool {
        self.provider.is_some() || self.provider_id.is_some()
    }

    fn acquire_arguments(&self) -> (Option<DependencySourceProviderSelector>, Option<String>) {
        if let Some(provider_id) = &self.provider_id {
            (None, Some(provider_id.clone()))
        } else {
            (
                Some(
                    self.provider
                        .unwrap_or(DependencySourceProviderSelector::Any),
                ),
                None,
            )
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum SourceProviderSelection {
    AllMatching,
    PreferredMatching,
}

#[derive(Debug, Default, Eq, PartialEq)]
pub(crate) struct SourcePreflight {
    pub(crate) all_dependencies_selected: bool,
    pub(crate) components: Vec<SourceComponentPreflight>,
    pub(crate) roots: Vec<SearchableSourceRoot>,
    pub(crate) missing: Vec<UnavailableSource>,
}

#[derive(Debug, Eq, PartialEq)]
pub(crate) struct SourceComponentPreflight {
    pub(crate) target: String,
    pub(crate) status: SourceStatus,
    pub(crate) preferred_provider: Option<SourceProviderPreflight>,
    pub(crate) providers: Vec<SourceProviderPreflight>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct SourceProviderPreflight {
    pub(crate) id: String,
    pub(crate) kind: SourceProviderKind,
    pub(crate) priority: usize,
    pub(crate) status: SourceStatus,
    pub(crate) searchable_roots: Vec<PathBuf>,
    pub(crate) unavailable_reason: Option<String>,
}

impl SourceProviderPreflight {
    fn from_view(view: SourceProviderView<'_>) -> Self {
        Self {
            id: view.id().to_owned(),
            kind: view.kind(),
            priority: view.priority(),
            status: view.status(),
            searchable_roots: view.searchable_roots(),
            unavailable_reason: view.unavailable_reason().map(ToOwned::to_owned),
        }
    }
}

#[derive(Debug, Eq, PartialEq)]
pub(crate) struct SearchableSourceRoot {
    pub(crate) identity: String,
    pub(crate) provider_id: String,
    pub(crate) root: PathBuf,
}

#[derive(Debug, Eq, PartialEq)]
pub(crate) struct UnavailableSource {
    pub(crate) target: String,
    pub(crate) identity: String,
    pub(crate) reason: String,
    pub(crate) acquirable: bool,
}

pub(crate) fn preferred_provider<'a>(
    inventory: &'a DependencyInventory,
    component: &'a DependencyComponentV3,
    filter: &SourceProviderFilter,
) -> Option<SourceProviderView<'a>> {
    inventory
        .source_providers(component)
        .find(|view| filter.matches(view.definition()))
}

pub(crate) fn preflight_sources(
    inventory: &DependencyInventory,
    dependency_filters: &[String],
    filter: &SourceProviderFilter,
    selection: SourceProviderSelection,
) -> eyre::Result<SourcePreflight> {
    for dependency_id in dependency_filters {
        inventory.dependency(dependency_id)?;
    }

    let mut result = SourcePreflight {
        all_dependencies_selected: dependency_filters.is_empty(),
        ..SourcePreflight::default()
    };
    for dependency in inventory.dependencies() {
        if !dependency_filters.is_empty() && !dependency_filters.contains(&dependency.id) {
            continue;
        }
        for component in &dependency.components {
            let target = format!("{}/{}", dependency.id, component.id);
            let matching = inventory
                .source_providers(component)
                .filter(|view| filter.matches(view.definition()))
                .collect::<Vec<_>>();
            if matching.is_empty() {
                if filter.is_constrained() {
                    continue;
                }
                result.components.push(SourceComponentPreflight {
                    target: target.clone(),
                    status: inventory.source_status(component),
                    preferred_provider: None,
                    providers: Vec::new(),
                });
                result.missing.push(UnavailableSource {
                    target: target.clone(),
                    identity: target,
                    reason: "no source providers are configured".to_owned(),
                    acquirable: false,
                });
                continue;
            }

            let preferred_provider = matching
                .first()
                .copied()
                .map(SourceProviderPreflight::from_view);
            let selected = match selection {
                SourceProviderSelection::AllMatching => matching.as_slice(),
                SourceProviderSelection::PreferredMatching => &matching[..1],
            };
            let providers = selected
                .iter()
                .copied()
                .map(SourceProviderPreflight::from_view)
                .collect::<Vec<_>>();
            for provider in &providers {
                if provider.status == SourceStatus::Acquired {
                    result
                        .roots
                        .extend(provider.searchable_roots.iter().cloned().map(|root| {
                            SearchableSourceRoot {
                                identity: target.clone(),
                                provider_id: provider.id.clone(),
                                root,
                            }
                        }));
                } else {
                    result.missing.push(UnavailableSource {
                        target: target.clone(),
                        identity: format!("{target}/{}", provider.id),
                        reason: provider.status.label().to_owned(),
                        acquirable: true,
                    });
                }
            }
            result.components.push(SourceComponentPreflight {
                target,
                status: inventory.source_status(component),
                preferred_provider,
                providers,
            });
        }
    }
    Ok(result)
}

#[derive(Debug)]
pub(crate) struct SourceAcquisitionTarget<'a> {
    pub(crate) dependency: &'a DependencyV3,
    pub(crate) component: &'a DependencyComponentV3,
    pub(crate) provider: &'a SourceProviderV3,
}

impl SourceAcquisitionTarget<'_> {
    #[must_use]
    pub(crate) fn identity(&self) -> String {
        format!("{}/{}", self.dependency.id, self.component.id)
    }

    #[must_use]
    pub(crate) fn provider_id(&self) -> &str {
        provider_id(self.provider)
    }

    #[must_use]
    pub(crate) fn searchable_roots(&self, inventory: &DependencyInventory) -> Vec<PathBuf> {
        inventory
            .source_providers(self.component)
            .find(|view| view.id() == self.provider_id())
            .expect("selected provider belongs to component")
            .searchable_roots()
    }
}

pub(crate) fn acquisition_targets<'a>(
    inventory: &'a DependencyInventory,
    target: Option<&str>,
    all: bool,
    filter: &SourceProviderFilter,
) -> eyre::Result<Vec<SourceAcquisitionTarget<'a>>> {
    if target.is_some() && all {
        eyre::bail!("Use either a dependency target or --all, not both.");
    }
    let components = match target {
        Some(target) => {
            let (dependency, component) = select_component(inventory, target)?;
            vec![(dependency, component)]
        }
        None if all => inventory
            .dependencies()
            .flat_map(|dependency| {
                dependency
                    .components
                    .iter()
                    .map(move |component| (dependency, component))
            })
            .collect(),
        None => eyre::bail!("Provide a dependency target or use --all."),
    };
    let mut selected = Vec::new();
    for (dependency, component) in components {
        if let Some(provider) = preferred_provider(inventory, component, filter) {
            selected.push(SourceAcquisitionTarget {
                dependency,
                component,
                provider: provider.definition(),
            });
        } else if !all {
            eyre::bail!(
                "No configured source provider matching '{}' for {}/{}.",
                filter.label(),
                dependency.id,
                component.id
            );
        }
    }
    if selected.is_empty() {
        eyre::bail!(
            "No declared source providers match '{}' on the selected branch.",
            filter.label()
        );
    }
    Ok(selected)
}

pub(crate) fn acquire_selected_sources(
    inventory: &DependencyInventory,
    targets: &[SourceAcquisitionTarget<'_>],
    branch: &BranchSelector,
    parallelism: Parallelism,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    coordinate_acquisition(
        targets,
        parallelism,
        cancellation_token,
        |token| acquire_platform_sources(branch.clone(), token),
        |target, token| acquire_independent_source(inventory, target, token),
    )
}

fn coordinate_acquisition(
    targets: &[SourceAcquisitionTarget<'_>],
    parallelism: Parallelism,
    cancellation_token: &CancellationToken,
    acquire_platform: impl Fn(&CancellationToken) -> eyre::Result<()>,
    acquire_independent: impl Fn(&SourceAcquisitionTarget<'_>, &CancellationToken) -> eyre::Result<()>
    + Sync,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    if targets
        .iter()
        .any(|target| matches!(target.provider, SourceProviderV3::PlatformPipeline(_)))
    {
        // Minecraft and loader providers share one pipeline output and must never build it twice.
        acquire_platform(cancellation_token)?;
    }
    let independent = targets
        .iter()
        .filter(|target| !matches!(target.provider, SourceProviderV3::PlatformPipeline(_)))
        .collect::<Vec<_>>();
    execute_acquisition_tasks(
        &independent,
        parallelism,
        cancellation_token,
        |target, token| acquire_independent(target, token),
    )
}

fn acquire_independent_source(
    inventory: &DependencyInventory,
    target: &SourceAcquisitionTarget<'_>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    match target.provider {
        SourceProviderV3::MavenSources(provider) => {
            acquire_locked_maven_sources(inventory, provider, cancellation_token, &http_fetcher()?)
        }
        SourceProviderV3::Git(provider) => {
            acquire_locked_git_sources(inventory, provider, cancellation_token)
        }
        SourceProviderV3::Decompile(provider) => acquire_locked_decompiled_sources(
            inventory,
            provider,
            cancellation_token,
            &http_fetcher()?,
        ),
        SourceProviderV3::PlatformPipeline(_) => {
            unreachable!("platform providers are dispatched once")
        }
    }
}

fn execute_acquisition_tasks<T: Sync>(
    tasks: &[T],
    parallelism: Parallelism,
    cancellation_token: &CancellationToken,
    execute: impl Fn(&T, &CancellationToken) -> eyre::Result<()> + Sync,
) -> eyre::Result<()> {
    let Some(limit) = parallelism.limit() else {
        for task in tasks {
            cancellation_token.bail_if_cancelled()?;
            execute(task, cancellation_token)?;
        }
        return Ok(());
    };
    if tasks.is_empty() {
        return Ok(());
    }

    let queue = Arc::new(Mutex::new((0..tasks.len()).collect::<VecDeque<_>>()));
    let stop_starting = Arc::new(AtomicBool::new(false));
    let (sender, receiver) = mpsc::channel::<(usize, eyre::Result<()>)>();
    thread::scope(|scope| {
        for _ in 0..limit.min(tasks.len()) {
            let queue = Arc::clone(&queue);
            let stop_starting = Arc::clone(&stop_starting);
            let sender = sender.clone();
            let cancellation_token = cancellation_token.clone();
            let execute = &execute;
            scope.spawn(move || {
                loop {
                    if stop_starting.load(Ordering::Acquire) || cancellation_token.is_cancelled() {
                        break;
                    }
                    let task_index = {
                        let mut queue = queue
                            .lock()
                            .expect("source acquisition queue should not be poisoned");
                        queue.pop_front()
                    };
                    let Some(task_index) = task_index else {
                        break;
                    };
                    let result = execute(&tasks[task_index], &cancellation_token);
                    let failed = result.is_err();
                    if failed {
                        stop_starting.store(true, Ordering::Release);
                    }
                    if sender.send((task_index, result)).is_err() || failed {
                        break;
                    }
                }
            });
        }
        drop(sender);
        let mut results = receiver.into_iter().collect::<Vec<_>>();
        results.sort_by_key(|(index, _)| *index);
        for (_, result) in results {
            result?;
        }
        cancellation_token.bail_if_cancelled()
    })
}

fn acquire_platform_sources(
    branch: BranchSelector,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    let build = JarBuildOptionsArgs {
        branch,
        refresh: false,
        explain_rebuild: false,
        plan_json: None,
        java_home: None,
        dry_run: false,
        allow_local_artifact_cache: false,
        artifact_sources: Vec::new(),
        require_portable_artifacts: false,
        error_action: ErrorAction::default(),
        parallel: None,
        wait_for_build_lock: false,
    }
    .into_options(BuildMode::Build)?;
    SourceOutputCommand::new(
        SourceOutputOptions {
            build,
            layout: SourceOutputLayout::Filetree,
        },
        cancellation_token.clone(),
    )
    .invoke()
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) enum SourceAcquisitionScope {
    Target(String),
    All,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct SourceAcquisitionRecommendation {
    pub(crate) scope: SourceAcquisitionScope,
    pub(crate) filter: SourceProviderFilter,
    pub(crate) branch: BranchSelector,
}

impl SourceAcquisitionRecommendation {
    #[must_use]
    pub(crate) fn target(
        target: impl Into<String>,
        branch: BranchSelector,
        filter: SourceProviderFilter,
    ) -> Self {
        Self {
            scope: SourceAcquisitionScope::Target(target.into()),
            filter,
            branch,
        }
    }

    #[must_use]
    pub(crate) const fn all(branch: BranchSelector, filter: SourceProviderFilter) -> Self {
        Self {
            scope: SourceAcquisitionScope::All,
            filter,
            branch,
        }
    }

    #[must_use]
    pub(crate) fn to_cli(&self) -> Cli {
        let (provider, provider_id) = self.filter.acquire_arguments();
        let (target, all) = match &self.scope {
            SourceAcquisitionScope::Target(target) => (Some(target.clone()), false),
            SourceAcquisitionScope::All => (None, true),
        };
        Cli {
            global_args: GlobalArgs::default(),
            command: CliCommand::Dependency(DependencyArgs {
                command: DependencyCommand::Source(DependencySourceArgs {
                    command: DependencySourceCommand::Acquire(DependencySourceAcquireArgs {
                        target,
                        all,
                        provider,
                        provider_id,
                        parallel: None,
                        branch: self.branch.clone(),
                    }),
                }),
            }),
            builtins: figue::FigueBuiltins::default(),
        }
    }

    pub(crate) fn render(&self) -> eyre::Result<String> {
        self.to_cli()
            .to_args_string_with_current_exe()
            .map(|command| command.to_string_lossy().into_owned())
            .map_err(eyre::Report::from)
    }
}

#[must_use]
pub(crate) fn provider_matches(
    provider: &SourceProviderV3,
    selector: DependencySourceProviderSelector,
) -> bool {
    matches!(selector, DependencySourceProviderSelector::Any)
        || matches!(
            (provider, selector),
            (
                SourceProviderV3::MavenSources(_),
                DependencySourceProviderSelector::MavenSources
            )
        )
        || matches!(
            (provider, selector),
            (
                SourceProviderV3::Git(_),
                DependencySourceProviderSelector::Git
            )
        )
        || matches!(
            (provider, selector),
            (
                SourceProviderV3::Decompile(_),
                DependencySourceProviderSelector::Decompile
            )
        )
        || matches!(
            (provider, selector),
            (
                SourceProviderV3::PlatformPipeline(_),
                DependencySourceProviderSelector::PlatformPipeline
            )
        )
}

#[must_use]
pub(crate) fn provider_id(provider: &SourceProviderV3) -> &str {
    match provider {
        SourceProviderV3::MavenSources(provider) => &provider.id,
        SourceProviderV3::Git(provider) => &provider.id,
        SourceProviderV3::Decompile(provider) => &provider.id,
        SourceProviderV3::PlatformPipeline(provider) => &provider.id,
    }
}

fn select_component<'a>(
    inventory: &'a DependencyInventory,
    target: &str,
) -> eyre::Result<(&'a DependencyV3, &'a DependencyComponentV3)> {
    let mut parts = target.split('/');
    let dependency_id = parts.next().unwrap_or_default();
    let component_id = parts.next();
    if dependency_id.is_empty() || parts.next().is_some() {
        eyre::bail!("Expected dependency or dependency/component, got '{target}'.");
    }
    let dependency = inventory.dependency(dependency_id)?;
    let component = match component_id {
        Some(component_id) => dependency
            .components
            .iter()
            .find(|component| component.id == component_id)
            .ok_or_else(|| eyre::eyre!("Unknown component '{target}'."))?,
        None if dependency.components.len() == 1 => &dependency.components[0],
        None => eyre::bail!(
            "Dependency '{dependency_id}' has multiple components; select dependency/component explicitly."
        ),
    };
    Ok((dependency, component))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::jar_build::hash::ContentHash;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use crate::paths::CacheHome;
    use crate::toolchain_lockfile_schema::read_current;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceProviderV3;
    use std::sync::atomic::AtomicUsize;
    use std::time::Duration;

    #[test]
    fn preflight_covers_acquired_missing_stale_and_partial_component_statuses() {
        let acquired_cache = tempfile::tempdir().expect("acquired cache");
        let mut acquired = fixture(CacheHome(acquired_cache.path().to_path_buf()));
        set_git_providers(&mut acquired, &["first"]);
        materialize_git_provider(&acquired, "first");
        let acquired_preflight = cc_preflight(&acquired);
        assert_eq!(
            acquired_preflight.components[0].status,
            SourceStatus::Acquired
        );
        assert_eq!(acquired_preflight.roots.len(), 1);
        assert!(acquired_preflight.missing.is_empty());

        let missing_cache = tempfile::tempdir().expect("missing cache");
        let mut missing = fixture(CacheHome(missing_cache.path().to_path_buf()));
        set_git_providers(&mut missing, &["first"]);
        let missing_preflight = cc_preflight(&missing);
        assert_eq!(
            missing_preflight.components[0].status,
            SourceStatus::Missing
        );
        assert!(missing_preflight.roots.is_empty());
        assert_eq!(missing_preflight.missing[0].reason, "missing");

        let stale_cache = tempfile::tempdir().expect("stale cache");
        let mut stale = fixture(CacheHome(stale_cache.path().to_path_buf()));
        set_stale_maven_provider(&mut stale);
        let stale_preflight = cc_preflight(&stale);
        assert_eq!(stale_preflight.components[0].status, SourceStatus::Stale);
        assert_eq!(stale_preflight.missing[0].reason, "stale");

        let partial_cache = tempfile::tempdir().expect("partial cache");
        let mut partial = fixture(CacheHome(partial_cache.path().to_path_buf()));
        set_git_providers(&mut partial, &["first", "second"]);
        materialize_git_provider(&partial, "first");
        let partial_preflight = cc_preflight(&partial);
        assert_eq!(
            partial_preflight.components[0].status,
            SourceStatus::Partial
        );
        assert_eq!(partial_preflight.roots.len(), 1);
        assert_eq!(partial_preflight.missing.len(), 1);
    }

    #[test]
    fn preferred_provider_follows_declaration_priority_even_when_later_provider_is_acquired() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let mut inventory = fixture(CacheHome(cache.path().to_path_buf()));
        set_git_providers(&mut inventory, &["first", "second"]);
        materialize_git_provider(&inventory, "second");
        let filter = SourceProviderFilter::default();
        let component = cc_component(&inventory);

        let preferred = preferred_provider(&inventory, component, &filter)
            .expect("preferred provider should exist");
        assert_eq!(preferred.id(), "first");
        assert_eq!(preferred.priority(), 0);
        assert_eq!(preferred.status(), SourceStatus::Missing);

        let preflight = preflight_sources(
            &inventory,
            &["cc-tweaked".to_owned()],
            &filter,
            SourceProviderSelection::PreferredMatching,
        )
        .expect("preferred preflight");
        assert_eq!(preflight.components[0].providers.len(), 1);
        assert_eq!(preflight.components[0].providers[0].id, "first");
        assert_eq!(
            preflight.components[0]
                .preferred_provider
                .as_ref()
                .unwrap()
                .id,
            "first"
        );
    }

    #[test]
    fn all_acquisition_uses_first_matching_provider_per_component() {
        let inventory = fixture(CacheHome(PathBuf::from("cache")));
        let selected =
            acquisition_targets(&inventory, None, true, &SourceProviderFilter::default())
                .expect("all source acquisition should select configured providers");
        let identities = selected
            .iter()
            .map(|target| format!("{}:{}", target.identity(), target.provider_id()))
            .collect::<Vec<_>>();

        assert!(identities.contains(&"cc-tweaked/main:git".to_owned()));
        assert!(identities.contains(&"mekanism/main:git".to_owned()));
        assert!(identities.contains(&"minecraft/main:minecraft-pipeline".to_owned()));
        assert!(identities.contains(&"forge/userdev:loader-pipeline".to_owned()));
        assert!(
            !identities
                .iter()
                .any(|identity| identity.contains("cloth-config"))
        );
    }

    #[test]
    fn shared_platform_pipeline_is_dispatched_exactly_once() {
        let inventory = fixture(CacheHome(PathBuf::from("cache")));
        let targets = acquisition_targets(
            &inventory,
            None,
            true,
            &SourceProviderFilter {
                provider: Some(DependencySourceProviderSelector::PlatformPipeline),
                provider_id: None,
            },
        )
        .expect("platform targets");
        assert!(
            targets.len() >= 2,
            "fixture should share the platform pipeline"
        );
        let platform_calls = AtomicUsize::new(0);
        let independent_calls = AtomicUsize::new(0);

        coordinate_acquisition(
            &targets,
            Parallelism::Sequential,
            &CancellationToken::new(),
            |_token| {
                platform_calls.fetch_add(1, Ordering::SeqCst);
                Ok(())
            },
            |_target, _token| {
                independent_calls.fetch_add(1, Ordering::SeqCst);
                Ok(())
            },
        )
        .expect("coordinated acquisition");

        assert_eq!(platform_calls.load(Ordering::SeqCst), 1);
        assert_eq!(independent_calls.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn typed_target_and_all_recommendations_roundtrip_through_figue() {
        let recommendations = [
            SourceAcquisitionRecommendation::target(
                "cc-tweaked/main",
                BranchSelector("1.19.2".to_owned()),
                SourceProviderFilter::default(),
            ),
            SourceAcquisitionRecommendation::all(
                BranchSelector("1.19.2".to_owned()),
                SourceProviderFilter {
                    provider: None,
                    provider_id: Some("upstream".to_owned()),
                },
            ),
            SourceAcquisitionRecommendation::all(
                BranchSelector("1.19.2".to_owned()),
                SourceProviderFilter {
                    provider: Some(DependencySourceProviderSelector::Git),
                    provider_id: None,
                },
            ),
        ];

        for (index, recommendation) in recommendations.into_iter().enumerate() {
            let rendered = recommendation.render().expect("recommendation display");
            assert!(rendered.contains("dependency source acquire"));
            let arguments = recommendation
                .to_cli()
                .to_args()
                .expect("typed recommendation should render")
                .iter()
                .map(|argument| argument.to_string_lossy().into_owned())
                .collect::<Vec<_>>();
            let argument_refs = arguments.iter().map(String::as_str).collect::<Vec<_>>();
            let parsed = figue::from_slice::<Cli>(&argument_refs)
                .into_result()
                .expect("typed recommendation should parse")
                .get_silent();
            let CliCommand::Dependency(DependencyArgs {
                command:
                    DependencyCommand::Source(DependencySourceArgs {
                        command: DependencySourceCommand::Acquire(acquire),
                    }),
            }) = parsed.command
            else {
                panic!("expected dependency source acquire command");
            };
            assert_eq!(acquire.branch.as_ref(), "1.19.2");
            assert_ne!(acquire.provider.is_some(), acquire.provider_id.is_some());
            match index {
                0 => {
                    assert_eq!(acquire.target.as_deref(), Some("cc-tweaked/main"));
                    assert!(!acquire.all);
                    assert_eq!(
                        acquire.provider,
                        Some(DependencySourceProviderSelector::Any)
                    );
                }
                1 => {
                    assert_eq!(acquire.target, None);
                    assert!(acquire.all);
                    assert_eq!(acquire.provider_id.as_deref(), Some("upstream"));
                }
                2 => {
                    assert_eq!(acquire.target, None);
                    assert!(acquire.all);
                    assert_eq!(
                        acquire.provider,
                        Some(DependencySourceProviderSelector::Git)
                    );
                }
                _ => unreachable!(),
            }
        }
    }

    #[test]
    fn selection_requires_exactly_one_target_mode() {
        let inventory = fixture(CacheHome(PathBuf::from("cache")));
        let filter = SourceProviderFilter::default();
        let neither = acquisition_targets(&inventory, None, false, &filter)
            .expect_err("omitting target mode should fail");
        assert!(neither.to_string().contains("target or use --all"));
        let both = acquisition_targets(&inventory, Some("cc-tweaked"), true, &filter)
            .expect_err("combining target and --all should fail");
        assert!(
            both.to_string()
                .contains("either a dependency target or --all")
        );
    }

    #[test]
    fn bounded_parallel_dispatch_runs_independent_tasks_concurrently() {
        let tasks = [0, 1, 2, 3];
        let active = AtomicUsize::new(0);
        let maximum_active = AtomicUsize::new(0);
        let completed = AtomicUsize::new(0);
        execute_acquisition_tasks(
            &tasks,
            Parallelism::Parallel { limit: 2 },
            &CancellationToken::new(),
            |_task, _token| {
                let current = active.fetch_add(1, Ordering::SeqCst) + 1;
                maximum_active.fetch_max(current, Ordering::SeqCst);
                thread::sleep(Duration::from_millis(25));
                active.fetch_sub(1, Ordering::SeqCst);
                completed.fetch_add(1, Ordering::SeqCst);
                Ok(())
            },
        )
        .expect("parallel task dispatch should complete");

        assert_eq!(completed.load(Ordering::SeqCst), tasks.len());
        assert_eq!(maximum_active.load(Ordering::SeqCst), 2);
    }

    #[test]
    fn bounded_parallel_dispatch_stops_starting_after_failure() {
        let tasks = [0, 1, 2];
        let seen = Mutex::new(Vec::new());
        let error = execute_acquisition_tasks(
            &tasks,
            Parallelism::Parallel { limit: 1 },
            &CancellationToken::new(),
            |task, _token| {
                seen.lock()
                    .expect("task list should not be poisoned")
                    .push(*task);
                if *task == 1 {
                    eyre::bail!("expected source acquisition failure");
                }
                Ok(())
            },
        )
        .expect_err("failing source acquisition should stop new work");

        assert!(
            error
                .to_string()
                .contains("expected source acquisition failure")
        );
        assert_eq!(
            *seen.lock().expect("task list should not be poisoned"),
            [0, 1]
        );
    }

    fn cc_preflight(inventory: &DependencyInventory) -> SourcePreflight {
        preflight_sources(
            inventory,
            &["cc-tweaked".to_owned()],
            &SourceProviderFilter::default(),
            SourceProviderSelection::AllMatching,
        )
        .expect("source preflight")
    }

    fn cc_component(inventory: &DependencyInventory) -> &DependencyComponentV3 {
        &inventory
            .dependency("cc-tweaked")
            .expect("CC:Tweaked fixture")
            .components[0]
    }

    fn cc_component_mut(inventory: &mut DependencyInventory) -> &mut DependencyComponentV3 {
        &mut inventory
            .lockfile
            .dependencies
            .iter_mut()
            .find(|dependency| dependency.id == "cc-tweaked")
            .expect("CC:Tweaked fixture")
            .components[0]
    }

    fn set_git_providers(inventory: &mut DependencyInventory, ids: &[&str]) {
        cc_component_mut(inventory).source_providers = ids
            .iter()
            .map(|id| {
                SourceProviderV3::Git(GitSourceProviderV3 {
                    id: (*id).to_owned(),
                    declaration: GitSourceDeclarationV3 {
                        remote_url: format!("https://example.invalid/{id}.git"),
                        requested_revision: "v1".to_owned(),
                        roots: vec!["src/main/java".to_owned()],
                    },
                    derived_checks: GitSourceDerivedChecksV3 {
                        commit: "0123456789abcdef".to_owned(),
                        repository_cache_path: PathBuf::from(format!(
                            "$sfm-cache/sources/git/repositories/{id}.git"
                        )),
                        tree_cache_path: PathBuf::from(format!(
                            "$sfm-cache/sources/git/trees/{id}/revision"
                        )),
                    },
                })
            })
            .collect();
    }

    fn materialize_git_provider(inventory: &DependencyInventory, id: &str) {
        let provider = cc_component(inventory)
            .source_providers
            .iter()
            .find(|provider| provider_id(provider) == id)
            .expect("Git provider");
        let SourceProviderV3::Git(provider) = provider else {
            panic!("expected Git provider");
        };
        std::fs::create_dir_all(
            inventory.local_path(&provider.derived_checks.repository_cache_path),
        )
        .expect("repository cache");
        std::fs::create_dir_all(
            inventory
                .local_path(&provider.derived_checks.tree_cache_path)
                .join("src/main/java"),
        )
        .expect("source root");
    }

    fn set_stale_maven_provider(inventory: &mut DependencyInventory) {
        let expected = ContentHash::from_bytes(b"expected", ContentHashAlgorithm::Blake3);
        cc_component_mut(inventory).source_providers =
            vec![SourceProviderV3::MavenSources(MavenSourceProviderV3 {
                id: "maven-sources".to_owned(),
                declaration: MavenSourceDeclarationV3 {
                    requested_coordinate: "example:test:1:sources".to_owned(),
                    repository_id: "fixture".to_owned(),
                    roots: vec!["src".to_owned()],
                },
                derived_checks: MavenSourceDerivedChecksV3 {
                    resolved_coordinate: "example:test:1:sources".to_owned(),
                    url: "https://example.invalid/test-sources.jar".to_owned(),
                    hash: expected,
                    archive_cache_path: PathBuf::from("$sfm-cache/sources/maven/test-sources.jar"),
                    tree_cache_path: PathBuf::from("$sfm-cache/sources/maven/test/tree"),
                },
            })];
        let SourceProviderV3::MavenSources(provider) = &cc_component(inventory).source_providers[0]
        else {
            unreachable!()
        };
        let archive = inventory.local_path(&provider.derived_checks.archive_cache_path);
        std::fs::create_dir_all(archive.parent().expect("archive parent")).expect("archive parent");
        std::fs::write(archive, b"stale").expect("stale archive");
    }

    fn fixture(cache_home: CacheHome) -> DependencyInventory {
        let input = include_str!("../../../minecraft/sfm-toolchain.lock.json");
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from("fixture")),
                core: true,
                mc_version: None,
            },
            lockfile_path: PathBuf::from("fixture/sfm-toolchain.lock.json"),
            cache_home,
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        }
    }
}
