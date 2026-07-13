use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use crate::jar_build::SourceOutputCommand;
use crate::jar_build::SourceOutputLayout;
use crate::jar_build::SourceOutputOptions;
use crate::paths::CacheHome;
use crate::payload_fetcher::http_fetcher;
use crate::source_decompile::acquire_locked_decompiled_sources;
use crate::source_git::acquire_locked_git_sources;
use crate::source_maven::acquire_locked_maven_sources;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use facet::Facet;
use figue as args;
use std::collections::VecDeque;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::mpsc;
use std::thread;

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
            provider_id: provider_id_filter,
            parallel,
            branch: selected_branch,
        } = self;
        cancellation_token.bail_if_cancelled()?;
        let branch = selected_branch.clone();
        let inventory = load_inventory(selected_branch, cache_home)?;
        if provider.is_some() && provider_id_filter.is_some() {
            eyre::bail!("Use either --provider or --provider-id, not both.");
        }
        let selector = provider.unwrap_or(DependencySourceProviderSelector::Any);
        let parallelism = Parallelism::from_cli(parallel)?;
        let targets = acquisition_targets(
            &inventory,
            target.as_deref(),
            all,
            selector,
            provider_id_filter.as_deref(),
        )?;
        acquire_selected_sources(
            &inventory,
            &targets,
            &branch,
            parallelism,
            cancellation_token,
        )?;
        for target in &targets {
            print_acquired_roots(&inventory, target)?;
        }
        Ok(())
    }
}

fn acquire_selected_sources(
    inventory: &crate::dependency_inventory::DependencyInventory,
    targets: &[AcquisitionTarget<'_>],
    branch: &BranchSelector,
    parallelism: Parallelism,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    if targets
        .iter()
        .any(|target| matches!(target.provider, SourceProviderV3::PlatformPipeline(_)))
    {
        // Minecraft and loader providers share one pipeline output and must never build it twice.
        acquire_platform_sources(branch.clone(), cancellation_token)?;
    }
    let independent = targets
        .iter()
        .filter(|target| !matches!(target.provider, SourceProviderV3::PlatformPipeline(_)))
        .collect::<Vec<_>>();
    execute_acquisition_tasks(
        &independent,
        parallelism,
        cancellation_token,
        |target, token| acquire_independent_source(inventory, target, token),
    )
}

fn acquire_independent_source(
    inventory: &crate::dependency_inventory::DependencyInventory,
    target: &AcquisitionTarget<'_>,
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

fn print_acquired_roots(
    inventory: &crate::dependency_inventory::DependencyInventory,
    target: &AcquisitionTarget<'_>,
) -> eyre::Result<()> {
    let view = inventory
        .source_providers(target.component)
        .find(|view| view.id() == provider_id(target.provider))
        .expect("selected provider belongs to component");
    for root in view.searchable_roots() {
        stdout_line(format!(
            "{}/{} {}: {}",
            target.dependency.id,
            target.component.id,
            view.id(),
            root.display()
        ))?;
    }
    Ok(())
}

#[derive(Debug)]
struct AcquisitionTarget<'a> {
    dependency: &'a crate::toolchain_lockfile_schema::version::v3::DependencyV3,
    component: &'a crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3,
    provider: &'a SourceProviderV3,
}

fn acquisition_targets<'a>(
    inventory: &'a crate::dependency_inventory::DependencyInventory,
    target: Option<&str>,
    all: bool,
    selector: DependencySourceProviderSelector,
    provider_id_filter: Option<&str>,
) -> eyre::Result<Vec<AcquisitionTarget<'a>>> {
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
        let provider = component.source_providers.iter().find(|provider| {
            provider_id_filter.map_or_else(
                || provider_matches(provider, selector),
                |id| provider_id(provider) == id,
            )
        });
        if let Some(provider) = provider {
            selected.push(AcquisitionTarget {
                dependency,
                component,
                provider,
            });
        } else if !all {
            eyre::bail!(
                "No configured source provider matching '{}' for {}/{}.",
                provider_id_filter.unwrap_or(selector.label()),
                dependency.id,
                component.id
            );
        }
    }
    if selected.is_empty() {
        eyre::bail!(
            "No declared source providers match '{}' on the selected branch.",
            provider_id_filter.unwrap_or(selector.label())
        );
    }
    Ok(selected)
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
    pub(crate) const fn label(self) -> &'static str {
        match self {
            Self::Any => "any",
            Self::MavenSources => "maven-sources",
            Self::Git => "git",
            Self::Decompile => "decompile",
            Self::PlatformPipeline => "platform-pipeline",
        }
    }
}

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

pub(crate) fn provider_id(provider: &SourceProviderV3) -> &str {
    match provider {
        SourceProviderV3::MavenSources(provider) => &provider.id,
        SourceProviderV3::Git(provider) => &provider.id,
        SourceProviderV3::Decompile(provider) => &provider.id,
        SourceProviderV3::PlatformPipeline(provider) => &provider.id,
    }
}

fn select_component<'a>(
    inventory: &'a crate::dependency_inventory::DependencyInventory,
    target: &str,
) -> eyre::Result<(
    &'a crate::toolchain_lockfile_schema::version::v3::DependencyV3,
    &'a crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3,
)> {
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
    use crate::toolchain_lockfile_schema::read_current;
    use std::path::PathBuf;
    use std::sync::atomic::AtomicUsize;
    use std::time::Duration;

    #[test]
    fn all_selection_uses_the_first_declared_provider_per_component() {
        let inventory = fixture();

        let selected = acquisition_targets(
            &inventory,
            None,
            true,
            DependencySourceProviderSelector::Any,
            None,
        )
        .expect("all source acquisition should select configured providers");
        let identities = selected
            .into_iter()
            .map(|target| {
                format!(
                    "{}/{}:{}",
                    target.dependency.id,
                    target.component.id,
                    provider_id(target.provider)
                )
            })
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
    fn selection_requires_exactly_one_target_mode() {
        let inventory = fixture();

        let neither = acquisition_targets(
            &inventory,
            None,
            false,
            DependencySourceProviderSelector::Any,
            None,
        )
        .expect_err("omitting target mode should fail");
        assert!(neither.to_string().contains("target or use --all"));

        let both = acquisition_targets(
            &inventory,
            Some("cc-tweaked"),
            true,
            DependencySourceProviderSelector::Any,
            None,
        )
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

    fn fixture() -> crate::dependency_inventory::DependencyInventory {
        let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
        crate::dependency_inventory::DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from("fixture")),
                core: true,
                mc_version: None,
            },
            lockfile_path: PathBuf::from("fixture/sfm-toolchain.lock.json"),
            cache_home: CacheHome(PathBuf::from("cache")),
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        }
    }
}
