use super::DependencyArgs;
use super::DependencyCommand;
use super::DependencySourceAcquireArgs;
use super::DependencySourceArgs;
use super::DependencySourceCommand;
use super::DependencySourceProviderSelector;
use super::dependency_context::load_inventory;
use super::dependency_source_acquire_cli::provider_id;
use super::dependency_source_acquire_cli::provider_matches;
use crate::cancellation::CancellationToken;
use crate::cli::cli::Cli;
use crate::cli::cli::Command as CliCommand;
use crate::cli::global_args::GlobalArgs;
use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::SourceStatus;
use crate::paths::CacheHome;
use crate::terminal_output::stderr_line;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;
use figue::ToArgs;
use std::path::PathBuf;
use std::process::Command;

#[derive(Facet, Debug)]
pub struct DependencySourceSearchArgs {
    /// Ripgrep pattern to search for in acquired source roots.
    #[facet(args::positional)]
    pub pattern: String,
    /// Dependency ID to search. Repeat to search more than one dependency.
    #[facet(default, args::named)]
    pub dependency: Vec<String>,
    /// Restrict search to a built-in provider kind.
    #[facet(default, args::named)]
    pub provider: Option<DependencySourceProviderSelector>,
    /// Restrict search to one stable provider ID.
    #[facet(default, args::named)]
    pub provider_id: Option<String>,
    /// Fail without searching if any selected source root is unavailable.
    #[facet(default = false, args::named)]
    pub require_complete: bool,
    /// Branch selector to read. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl DependencySourceSearchArgs {
    pub(crate) fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        if self.provider.is_some() && self.provider_id.is_some() {
            eyre::bail!("Use either --provider or --provider-id, not both.");
        }
        let branch = self.branch.clone();
        let inventory = load_inventory(self.branch, cache_home)?;
        let preflight = preflight(
            &inventory,
            &self.dependency,
            self.provider,
            self.provider_id.as_deref(),
        )?;
        if !preflight.missing.is_empty() {
            warn_incomplete_sources(
                &preflight,
                &branch,
                self.provider,
                self.provider_id.as_deref(),
            )?;
            require_complete_sources(&preflight, self.require_complete)?;
        }
        for root in &preflight.roots {
            cancellation_token.bail_if_cancelled()?;
            run_ripgrep(&self.pattern, root)?;
        }
        Ok(())
    }
}

fn warn_incomplete_sources(
    preflight: &SearchPreflight,
    branch: &BranchSelector,
    provider: Option<DependencySourceProviderSelector>,
    provider_id: Option<&str>,
) -> eyre::Result<()> {
    for line in incomplete_source_warning_lines(preflight, branch, provider, provider_id)? {
        stderr_line(line)?;
    }
    Ok(())
}

fn incomplete_source_warning_lines(
    preflight: &SearchPreflight,
    branch: &BranchSelector,
    provider: Option<DependencySourceProviderSelector>,
    provider_id: Option<&str>,
) -> eyre::Result<Vec<String>> {
    if preflight.missing.is_empty() {
        return Ok(Vec::new());
    }
    let mut lines =
        vec!["Warning: source search results are incomplete. Missing source roots:".to_owned()];
    let recommend_all = preflight.all_dependencies_selected
        && preflight.roots.is_empty()
        && preflight.missing.iter().all(|missing| missing.acquirable);
    for missing in &preflight.missing {
        lines.push(format!("  - {} ({})", missing.identity, missing.reason));
        if missing.acquirable && !recommend_all {
            lines.push(format!(
                "    Acquire with: {}",
                acquisition_recommendation(missing, branch, provider, provider_id)?
            ));
        }
    }
    if recommend_all {
        lines.push(format!(
            "Acquire all with: {}",
            all_acquisition_recommendation(branch, provider, provider_id)?
        ));
    }
    Ok(lines)
}

fn acquisition_recommendation(
    missing: &MissingSource,
    branch: &BranchSelector,
    provider: Option<DependencySourceProviderSelector>,
    provider_id: Option<&str>,
) -> eyre::Result<String> {
    render_typed_acquire_command(&typed_acquire_command(
        missing,
        branch,
        provider,
        provider_id,
    ))
}

fn all_acquisition_recommendation(
    branch: &BranchSelector,
    provider: Option<DependencySourceProviderSelector>,
    provider_id: Option<&str>,
) -> eyre::Result<String> {
    render_typed_acquire_command(&typed_acquire_all_command(branch, provider, provider_id))
}

fn render_typed_acquire_command(command: &Cli) -> eyre::Result<String> {
    command
        .to_args_string_with_current_exe()
        .map(|command| command.to_string_lossy().into_owned())
        .map_err(eyre::Report::from)
}

fn typed_acquire_command(
    missing: &MissingSource,
    branch: &BranchSelector,
    provider: Option<DependencySourceProviderSelector>,
    provider_id: Option<&str>,
) -> Cli {
    Cli {
        global_args: GlobalArgs::default(),
        command: CliCommand::Dependency(DependencyArgs {
            command: DependencyCommand::Source(DependencySourceArgs {
                command: DependencySourceCommand::Acquire(DependencySourceAcquireArgs {
                    target: Some(missing.target.clone()),
                    all: false,
                    provider: Some(provider.unwrap_or(DependencySourceProviderSelector::Any)),
                    provider_id: provider_id.map(ToOwned::to_owned),
                    parallel: None,
                    branch: branch.clone(),
                }),
            }),
        }),
        builtins: figue::FigueBuiltins::default(),
    }
}

fn typed_acquire_all_command(
    branch: &BranchSelector,
    provider: Option<DependencySourceProviderSelector>,
    provider_id: Option<&str>,
) -> Cli {
    Cli {
        global_args: GlobalArgs::default(),
        command: CliCommand::Dependency(DependencyArgs {
            command: DependencyCommand::Source(DependencySourceArgs {
                command: DependencySourceCommand::Acquire(DependencySourceAcquireArgs {
                    target: None,
                    all: true,
                    provider: Some(provider.unwrap_or(DependencySourceProviderSelector::Any)),
                    provider_id: provider_id.map(ToOwned::to_owned),
                    parallel: None,
                    branch: branch.clone(),
                }),
            }),
        }),
        builtins: figue::FigueBuiltins::default(),
    }
}

fn require_complete_sources(
    preflight: &SearchPreflight,
    require_complete: bool,
) -> eyre::Result<()> {
    if require_complete && !preflight.missing.is_empty() {
        eyre::bail!(
            "Source search requires complete sources; acquire the missing roots before searching."
        );
    }
    Ok(())
}

#[derive(Debug, Default, Eq, PartialEq)]
struct SearchPreflight {
    all_dependencies_selected: bool,
    roots: Vec<SearchRoot>,
    missing: Vec<MissingSource>,
}

#[derive(Debug, Eq, PartialEq)]
struct SearchRoot {
    identity: String,
    provider_id: String,
    root: PathBuf,
}

#[derive(Debug, Eq, PartialEq)]
struct MissingSource {
    target: String,
    identity: String,
    reason: String,
    acquirable: bool,
}

fn preflight(
    inventory: &DependencyInventory,
    dependency_filters: &[String],
    provider: Option<DependencySourceProviderSelector>,
    provider_id_filter: Option<&str>,
) -> eyre::Result<SearchPreflight> {
    for dependency_id in dependency_filters {
        inventory.dependency(dependency_id)?;
    }

    let mut result = SearchPreflight {
        all_dependencies_selected: dependency_filters.is_empty(),
        ..SearchPreflight::default()
    };
    for dependency in inventory.dependencies() {
        if !dependency_filters.is_empty() && !dependency_filters.contains(&dependency.id) {
            continue;
        }
        for component in &dependency.components {
            let identity = format!("{}/{}", dependency.id, component.id);
            let mut matched_provider = false;
            for view in inventory.source_providers(component) {
                let selected = provider_id_filter.map_or_else(
                    || {
                        provider
                            .is_none_or(|selector| provider_matches(view.definition(), selector))
                    },
                    |id| view.id() == id,
                );
                if !selected {
                    continue;
                }
                matched_provider = true;
                if view.status() == SourceStatus::Acquired {
                    result
                        .roots
                        .extend(view.searchable_roots().into_iter().map(|root| SearchRoot {
                            identity: identity.clone(),
                            provider_id: provider_id(view.definition()).to_owned(),
                            root,
                        }));
                } else {
                    result.missing.push(MissingSource {
                        target: identity.clone(),
                        identity: format!("{identity}/{}", view.id()),
                        reason: view.status().label().to_owned(),
                        acquirable: true,
                    });
                }
            }
            if !matched_provider {
                if provider.is_some() || provider_id_filter.is_some() {
                    continue;
                }
                let reason = if component.source_providers.is_empty() {
                    "no source providers are configured".to_owned()
                } else {
                    unreachable!("an unfiltered component always has matching providers")
                };
                result.missing.push(MissingSource {
                    target: identity.clone(),
                    identity,
                    reason,
                    acquirable: false,
                });
            }
        }
    }
    Ok(result)
}

fn run_ripgrep(pattern: &str, root: &SearchRoot) -> eyre::Result<()> {
    let Some(output) = ripgrep_output(pattern, &root.root)? else {
        return Ok(());
    };
    for line in output.lines() {
        let path = line
            .strip_prefix("./")
            .or_else(|| line.strip_prefix(".\\"))
            .unwrap_or(line);
        stdout_line(format!("{}/{}/{}", root.identity, root.provider_id, path))?;
    }
    Ok(())
}

fn ripgrep_output(pattern: &str, root: &std::path::Path) -> eyre::Result<Option<String>> {
    let output = Command::new("rg")
        .args([
            "--line-number",
            "--column",
            "--with-filename",
            "--color=never",
            "--",
        ])
        .arg(pattern)
        .arg(".")
        .current_dir(root)
        .output()
        .map_err(|error| eyre::eyre!("Failed to start ripgrep for {}: {error}", root.display()))?;
    match output.status.code() {
        Some(0) => Ok(Some(String::from_utf8_lossy(&output.stdout).into_owned())),
        Some(1) => Ok(None),
        _ => eyre::bail!(
            "ripgrep failed for {}: {}",
            root.display(),
            String::from_utf8_lossy(&output.stderr).trim()
        ),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::toolchain_lockfile_schema::read_current;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
    use std::path::Path;

    #[test]
    fn preflight_uses_only_acquired_matching_roots_without_writing_cache() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let mut inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let provider = add_git_provider(&mut inventory);
        let repository = inventory.local_path(&provider.derived_checks.repository_cache_path);
        let root = inventory
            .local_path(&provider.derived_checks.tree_cache_path)
            .join("src/main/java");
        std::fs::create_dir_all(&repository).expect("repository cache");
        std::fs::create_dir_all(&root).expect("source root");
        let before = directory_entries(cache.path());

        let preflight = preflight(
            &inventory,
            &["cc-tweaked".to_owned()],
            Some(DependencySourceProviderSelector::Git),
            None,
        )
        .expect("preflight succeeds");

        assert_eq!(preflight.missing, []);
        assert_eq!(preflight.roots.len(), 1);
        assert_eq!(preflight.roots[0].identity, "cc-tweaked/main");
        assert_eq!(preflight.roots[0].provider_id, "upstream");
        assert_eq!(preflight.roots[0].root, root);
        assert_eq!(directory_entries(cache.path()), before);
    }

    #[test]
    fn preflight_reports_missing_selected_roots() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let mut inventory = fixture(CacheHome(cache.path().to_path_buf()));
        add_git_provider(&mut inventory);

        let preflight = preflight(
            &inventory,
            &["cc-tweaked".to_owned()],
            Some(DependencySourceProviderSelector::Git),
            None,
        )
        .expect("preflight succeeds");

        assert!(preflight.roots.is_empty());
        assert_eq!(preflight.missing.len(), 1);
        assert_eq!(preflight.missing[0].identity, "cc-tweaked/main/upstream");
        assert_eq!(preflight.missing[0].reason, "missing");
    }

    #[test]
    fn provider_filter_skips_components_without_that_provider() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let mut inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let provider = add_git_provider(&mut inventory);
        let tree = inventory
            .local_path(&provider.derived_checks.tree_cache_path)
            .join("src/main/java");
        let repository = inventory.local_path(&provider.derived_checks.repository_cache_path);
        std::fs::create_dir_all(&repository).expect("repository cache");
        std::fs::create_dir_all(&tree).expect("source root");
        let dependency = inventory
            .lockfile
            .dependencies
            .iter_mut()
            .find(|dependency| dependency.id == "cc-tweaked")
            .expect("CC:Tweaked fixture");
        let mut api = dependency.components[0].clone();
        api.id = "api".to_owned();
        api.source_providers.clear();
        dependency.components.push(api);

        let preflight = preflight(
            &inventory,
            &["cc-tweaked".to_owned()],
            Some(DependencySourceProviderSelector::Git),
            None,
        )
        .expect("preflight succeeds");

        assert_eq!(preflight.roots.len(), 1);
        assert!(preflight.missing.is_empty());
    }

    #[test]
    fn preflight_rejects_unknown_dependency_before_search() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));

        let error = preflight(&inventory, &["missing".to_owned()], None, None)
            .expect_err("unknown dependency should fail before search");

        assert!(error.to_string().contains("Unknown dependency 'missing'"));
    }

    #[test]
    fn incomplete_warning_recommends_all_when_every_selected_root_is_missing() {
        let preflight = SearchPreflight {
            all_dependencies_selected: true,
            roots: Vec::new(),
            missing: vec![
                MissingSource {
                    target: "cc-tweaked/main".to_owned(),
                    identity: "cc-tweaked/main/maven-sources".to_owned(),
                    reason: "missing".to_owned(),
                    acquirable: true,
                },
                MissingSource {
                    target: "minecraft/main".to_owned(),
                    identity: "minecraft/main/platform".to_owned(),
                    reason: "stale".to_owned(),
                    acquirable: true,
                },
            ],
        };

        let lines = incomplete_source_warning_lines(
            &preflight,
            &BranchSelector("1.19.2".to_owned()),
            None,
            None,
        )
        .expect("warning rendering");
        assert_eq!(
            lines[0],
            "Warning: source search results are incomplete. Missing source roots:"
        );
        assert_eq!(lines[1], "  - cc-tweaked/main/maven-sources (missing)");
        assert_eq!(lines[2], "  - minecraft/main/platform (stale)");
        assert!(
            lines[3].ends_with("dependency source acquire --all --provider any --branch 1.19.2")
        );
    }

    #[test]
    fn filtered_warning_recommends_only_the_selected_target() {
        let preflight = SearchPreflight {
            all_dependencies_selected: false,
            roots: Vec::new(),
            missing: vec![MissingSource {
                target: "cc-tweaked/main".to_owned(),
                identity: "cc-tweaked/main/maven-sources".to_owned(),
                reason: "missing".to_owned(),
                acquirable: true,
            }],
        };

        let lines = incomplete_source_warning_lines(
            &preflight,
            &BranchSelector("1.19.2".to_owned()),
            None,
            None,
        )
        .expect("warning rendering");

        assert!(
            lines[2].contains(
                "dependency source acquire --provider any --branch 1.19.2 cc-tweaked/main"
            )
        );
        assert!(!lines[2].contains("--all"));
    }

    #[test]
    fn require_complete_fails_before_ripgrep_when_sources_are_missing() {
        let preflight = SearchPreflight {
            all_dependencies_selected: false,
            roots: vec![SearchRoot {
                identity: "cc-tweaked/main".to_owned(),
                provider_id: "maven-sources".to_owned(),
                root: PathBuf::from("not-used"),
            }],
            missing: vec![MissingSource {
                target: "minecraft/main".to_owned(),
                identity: "minecraft/main/platform".to_owned(),
                reason: "missing".to_owned(),
                acquirable: true,
            }],
        };

        let error = require_complete_sources(&preflight, true)
            .expect_err("incomplete search should fail before invoking ripgrep");

        assert!(error.to_string().contains("requires complete sources"));
    }

    #[test]
    fn ripgrep_reports_matches_and_normal_no_match_without_mutation() {
        let directory = tempfile::tempdir().expect("temporary source root");
        let source = directory.path().join("Example.java");
        std::fs::write(&source, "interface IPeripheralProvider {}\n").expect("source file");
        let before = directory_entries(directory.path());

        let matches = ripgrep_output("IPeripheralProvider", directory.path())
            .expect("matching ripgrep invocation")
            .expect("expected source match");
        let no_match = ripgrep_output("AbsentSourceType", directory.path())
            .expect("no-match ripgrep invocation");

        assert!(matches.contains("Example.java:1:"));
        assert_eq!(no_match, None);
        assert_eq!(directory_entries(directory.path()), before);
    }

    #[test]
    fn typed_missing_source_recommendation_roundtrips_through_figue() {
        let missing = MissingSource {
            target: "cc-tweaked/main".to_owned(),
            identity: "cc-tweaked/main/maven-sources".to_owned(),
            reason: "missing".to_owned(),
            acquirable: true,
        };
        let command =
            typed_acquire_command(&missing, &BranchSelector("1.19.2".to_owned()), None, None);
        let arguments = command
            .to_args()
            .expect("typed recommendation should render");
        let arguments = arguments
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        let argument_refs = arguments.iter().map(String::as_str).collect::<Vec<_>>();
        let parsed = figue::from_slice::<Cli>(&argument_refs)
            .into_result()
            .expect("rendered recommendation should parse")
            .get_silent();

        let CliCommand::Dependency(DependencyArgs {
            command:
                DependencyCommand::Source(DependencySourceArgs {
                    command: DependencySourceCommand::Acquire(acquire),
                }),
        }) = parsed.command
        else {
            panic!("expected rendered acquire command");
        };
        assert_eq!(acquire.target.as_deref(), Some("cc-tweaked/main"));
        assert_eq!(
            acquire.provider,
            Some(DependencySourceProviderSelector::Any)
        );
        assert_eq!(acquire.branch.as_ref(), "1.19.2");

        let rendered =
            acquisition_recommendation(&missing, &BranchSelector("1.19.2".to_owned()), None, None)
                .expect("recommendation should render");
        assert!(rendered.contains("--provider any"));
    }

    #[test]
    fn typed_all_missing_source_recommendation_roundtrips_through_figue() {
        let command = typed_acquire_all_command(&BranchSelector("1.19.2".to_owned()), None, None);
        let arguments = command
            .to_args()
            .expect("typed all recommendation should render");
        let arguments = arguments
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        let argument_refs = arguments.iter().map(String::as_str).collect::<Vec<_>>();
        let parsed = figue::from_slice::<Cli>(&argument_refs)
            .into_result()
            .expect("rendered all recommendation should parse")
            .get_silent();

        let CliCommand::Dependency(DependencyArgs {
            command:
                DependencyCommand::Source(DependencySourceArgs {
                    command: DependencySourceCommand::Acquire(acquire),
                }),
        }) = parsed.command
        else {
            panic!("expected rendered all acquire command");
        };
        assert_eq!(acquire.target, None);
        assert!(acquire.all);
        assert_eq!(
            acquire.provider,
            Some(DependencySourceProviderSelector::Any)
        );
        assert_eq!(acquire.branch.as_ref(), "1.19.2");
    }

    #[test]
    fn typed_provider_selector_renders_through_figue() {
        let mut command =
            typed_acquire_all_command(&BranchSelector("1.19.2".to_owned()), None, None);
        let CliCommand::Dependency(DependencyArgs {
            command:
                DependencyCommand::Source(DependencySourceArgs {
                    command: DependencySourceCommand::Acquire(acquire),
                }),
        }) = &mut command.command
        else {
            panic!("expected typed acquire command");
        };
        acquire.provider = Some(DependencySourceProviderSelector::Any);

        let arguments = command
            .to_args()
            .expect("typed provider selector should render");
        let arguments = arguments
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect::<Vec<_>>();

        assert!(
            arguments
                .windows(2)
                .any(|window| window == ["--provider", "any"])
        );
    }

    fn add_git_provider(inventory: &mut DependencyInventory) -> GitSourceProviderV3 {
        let dependency = inventory
            .lockfile
            .dependencies
            .iter_mut()
            .find(|dependency| dependency.id == "cc-tweaked")
            .expect("CC:Tweaked fixture");
        let component = dependency
            .components
            .iter_mut()
            .find(|component| component.id == "main")
            .expect("CC:Tweaked main component");
        component.source_providers.clear();
        let provider = GitSourceProviderV3 {
            id: "upstream".to_owned(),
            declaration: GitSourceDeclarationV3 {
                remote_url: "https://example.invalid/upstream.git".to_owned(),
                requested_revision: "v1".to_owned(),
                roots: vec!["src/main/java".to_owned()],
            },
            derived_checks: GitSourceDerivedChecksV3 {
                commit: "0123456789abcdef".to_owned(),
                repository_cache_path: PathBuf::from(
                    "$sfm-cache/sources/git/repositories/test.git",
                ),
                tree_cache_path: PathBuf::from("$sfm-cache/sources/git/trees/test/revision"),
            },
        };
        component
            .source_providers
            .push(SourceProviderV3::Git(provider.clone()));
        provider
    }

    fn fixture(cache_home: CacheHome) -> DependencyInventory {
        let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
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

    fn directory_entries(root: &Path) -> Vec<PathBuf> {
        let mut paths = Vec::new();
        collect_entries(root, root, &mut paths);
        paths.sort();
        paths
    }

    fn collect_entries(root: &Path, directory: &Path, entries: &mut Vec<PathBuf>) {
        for entry in std::fs::read_dir(directory).expect("read cache directory") {
            let path = entry.expect("cache entry").path();
            entries.push(
                path.strip_prefix(root)
                    .expect("relative entry")
                    .to_path_buf(),
            );
            if path.is_dir() {
                collect_entries(root, &path, entries);
            }
        }
    }
}
