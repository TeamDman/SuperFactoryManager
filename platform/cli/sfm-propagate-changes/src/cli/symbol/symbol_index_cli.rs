use super::ResolvedSymbolQueryWorkspace;
use crate::artifact_lock::ArtifactLock;
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
use crate::cli::output::CliOutput;
use crate::dependency_classfile_stubs::LockedMinecraftClassfileStubSource;
use crate::dependency_classfile_stubs::MINECRAFT_CLASSFILE_STUB_PROVIDER_ID;
use crate::dependency_classfile_stubs::attach_locked_minecraft_classfile_stub_source;
use crate::dependency_classfile_stubs::is_minecraft_classfile_stub_target;
use crate::dependency_classfile_stubs::materialize_locked_minecraft_classfile_stubs;
use crate::dependency_classfile_stubs::materialized_classfile_stub_tree_matches;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::SourceStatus;
use crate::dependency_locked_sources::LockedArtifactSource;
use crate::dependency_locked_sources::acquire_locked_artifact_sources;
use crate::dependency_locked_sources::append_locked_artifact_source_preflight;
use crate::dependency_locked_sources::derive_locked_loader_artifact_sources;
use crate::dependency_sources::SearchableSourceRoot;
use crate::dependency_sources::SourceAcquisitionRecommendation;
use crate::dependency_sources::SourcePreflight;
use crate::dependency_sources::SourceProviderFilter;
use crate::dependency_sources::SourceProviderSelection;
use crate::dependency_sources::acquire_selected_sources;
use crate::dependency_sources::acquisition_targets;
use crate::dependency_sources::preflight_sources;
use crate::jar_build::Parallelism;
use crate::jar_build::engine::select_branch_source_cache;
use crate::jar_build::engine::select_project_source_cache;
use crate::java_analysis::DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA;
use crate::java_analysis::DefinitionDependencySourceRoot;
use crate::java_analysis::DependencyIndexBuildArtifacts;
use crate::java_analysis::DependencyJavaSourceOrigin;
use crate::java_analysis::DependencyJavaSymbolIndexBody;
use crate::java_analysis::DependencyJavaSymbolIndexScan;
use crate::java_analysis::DependencyResolutionSelection;
use crate::java_analysis::DependencySymbolIndexCompleteness;
use crate::java_analysis::DependencySymbolIndexCounts;
use crate::java_analysis::DependencySymbolIndexCreationMetadata;
use crate::java_analysis::DependencySymbolIndexIdentity;
use crate::java_analysis::DependencySymbolIndexInputStatus;
use crate::java_analysis::DependencySymbolIndexManifest;
use crate::java_analysis::DependencySymbolIndexProbe;
use crate::java_analysis::DependencySymbolIndexProbeStatus;
use crate::java_analysis::DependencySymbolIndexQueryOutput;
use crate::java_analysis::DependencySymbolIndexSourceInput;
use crate::java_analysis::DependencySymbolIndexStore;
use crate::java_analysis::JavaAnalysisContextOutput;
use crate::java_analysis::JavaAnalysisDiagnosticOutput;
use crate::java_analysis::JavaClasspathMode;
use crate::java_analysis::JavaSourceWorkspace;
use crate::java_analysis::JavaSymbolGlob;
use crate::java_analysis::JavaSymbolIndex;
use crate::java_analysis::JavaSymbolKind;
use crate::java_analysis::JavaSymbolSelector;
use crate::java_analysis::JavaSymbolSelectorKind;
use crate::java_analysis::LiveQueryStage;
use crate::java_analysis::LiveQueryTimings;
use crate::java_analysis::SymbolQueryCompleteness;
use crate::java_analysis::build_dependency_index_sharded;
use crate::java_analysis::build_java_index_sharded;
use crate::java_analysis::build_live_definition_index_in_process;
use crate::java_analysis::build_live_definition_index_streaming;
use crate::java_analysis::derive_dependency_symbol_index_projection_inputs;
use crate::java_analysis::enforce_refresh_parent_memory_limit;
use crate::java_analysis::java_identifier_tokens;
use crate::java_analysis::java_member_access_tokens;
use crate::java_analysis::project_dependency_symbol_index_identity_with_locked_sources;
use crate::java_analysis::scan_dependency_java_symbol_index;
use crate::java_analysis::scan_generated_dependency_symbol;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use crate::payload_fetcher::http_fetcher;
use crate::source_projection::core_input_leases::DirectoryLeases;
use crate::source_provider::SourceProviderKind;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3;
use chrono::Utc;
use facet::Facet;
use figue::ToArgs;
use figue::{self as args};
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::env::var_os;
use std::fs::canonicalize;
use std::fs::read_dir;
use std::fs::read_to_string;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::time::Instant;

pub const SYMBOL_INDEX_STATUS_SCHEMA: &str = "sfm.symbol-index-status/1";
const LEGACY_DEFINITION_PIPELINE_ENV: &str = "SFM_JAVA_DEFINITION_PIPELINE";

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SymbolIndexStatusOutput {
    pub schema: String,
    pub operation: String,
    pub branch: String,
    pub status: DependencySymbolIndexProbeStatus,
    pub completeness: DependencySymbolIndexCompleteness,
    pub expected_identity: String,
    pub portable_path: String,
    pub path: String,
    pub reason: String,
    pub refresh_command: String,
    pub acquisition_commands: Vec<String>,
    pub counts: Option<DependencySymbolIndexCounts>,
}

#[derive(Facet, Debug)]
pub struct SymbolIndexArgs {
    /// Guarded cache override; defaults to the selected branch checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
    #[facet(args::subcommand)]
    pub command: SymbolIndexCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum SymbolIndexCommand {
    /// Acquire preferred dependency sources and reuse a matching managed index, or rebuild it.
    Refresh(SymbolIndexRefreshArgs),
    /// Deeply verify the payload and show its identity, status, and paths.
    Show(SymbolIndexShowArgs),
}

#[derive(Facet, Clone, Debug)]
pub struct SymbolIndexRefreshArgs {
    /// Historical branch selector; choose exactly one of this and --project-root.
    #[facet(default, args::named)]
    pub branch: Option<BranchSelector>,
    /// Exact catalogued generated projection root and its emitted lock.
    #[facet(default, args::named)]
    pub project_root: Option<PathBuf>,
    /// Exact JDK for catalog-owned platform source production.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
    /// Rebuild even when a managed completed index receipt matches.
    #[facet(default, args::named)]
    pub rebuild: bool,
}

#[derive(Facet, Clone, Debug)]
pub struct SymbolIndexShowArgs {
    /// Historical branch selector; choose exactly one of this and --project-root.
    #[facet(default, args::named)]
    pub branch: Option<BranchSelector>,
    /// Exact catalogued generated projection root and its emitted lock.
    #[facet(default, args::named)]
    pub project_root: Option<PathBuf>,
}

#[cfg(test)]
mod selected_cache_args_tests {
    use super::*;
    use crate::dependency_sources::SourceProviderPreflight;
    use crate::source_projection::core_catalog::write_project_catalog_fixture;
    use std::fs;

    #[test]
    fn materialized_classfile_stubs_do_not_request_legacy_platform_acquisition() {
        let target = "minecraft/classpath-type-stubs";
        let provider_id = "version-json-classfile-type-stubs";
        let missing = SourceProviderPreflight {
            id: provider_id.to_owned(),
            kind: SourceProviderKind::PlatformPipeline,
            priority: 0,
            status: SourceStatus::Missing,
            searchable_roots: Vec::new(),
            unavailable_reason: Some("missing".to_owned()),
        };
        let mut preflight = SourcePreflight::default();
        preflight
            .components
            .push(crate::dependency_sources::SourceComponentPreflight {
                target: target.to_owned(),
                status: SourceStatus::Missing,
                preferred_provider: Some(missing.clone()),
                providers: vec![missing],
            });
        preflight
            .missing
            .push(crate::dependency_sources::UnavailableSource {
                target: target.to_owned(),
                identity: format!("{target}/{provider_id}"),
                reason: "missing".to_owned(),
                acquirable: true,
            });
        let tree = PathBuf::from("stubs.filetree");
        promote_materialized_classfile_stub_source(
            &mut preflight,
            target,
            provider_id,
            tree.clone(),
        )
        .unwrap();
        assert!(preflight.missing.is_empty());
        assert_eq!(preflight.components[0].status, SourceStatus::Acquired);
        assert_eq!(
            preflight.components[0].providers[0].status,
            SourceStatus::Acquired
        );
        assert_eq!(preflight.roots[0].root, tree);
        assert_eq!(preflight.roots[0].identity, target);
    }

    #[test]
    fn index_paths_resolve_against_the_supplied_invocation_directory() -> eyre::Result<()> {
        let invocation = tempfile::tempdir()?;
        for operation in ["show", "refresh"] {
            let mut arguments = vec![
                "--cache-root",
                "managed cache",
                operation,
                "--project-root",
                "selected projection",
            ];
            if operation == "refresh" {
                arguments.extend(["--java-home", "exact jdk"]);
            }
            let mut parsed = figue::from_slice::<SymbolIndexArgs>(&arguments)
                .into_result()?
                .get_silent();
            parsed.resolve_paths_in(invocation.path());
            assert_eq!(
                parsed.cache_root,
                Some(invocation.path().join("managed cache"))
            );
            let project_root = match parsed.command {
                SymbolIndexCommand::Refresh(refresh) => {
                    assert_eq!(refresh.java_home, Some(invocation.path().join("exact jdk")));
                    refresh.project_root
                }
                SymbolIndexCommand::Show(show) => show.project_root,
            };
            assert_eq!(
                project_root,
                Some(invocation.path().join("selected projection"))
            );
        }
        Ok(())
    }

    #[test]
    fn relative_index_cache_preserves_guarded_ownership_admission() -> eyre::Result<()> {
        let invocation = tempfile::tempdir()?;
        write_project_catalog_fixture(invocation.path(), "fixture", "1.19.2")?;
        let cache = invocation.path().join("unowned cache");
        fs::create_dir(&cache)?;
        let sentinel = cache.join("existing-input");
        fs::write(&sentinel, b"preserve me")?;
        let parsed = figue::from_slice::<SymbolIndexArgs>(&[
            "--cache-root",
            "unowned cache",
            "show",
            "--project-root",
            "platform/minecraft/projections/fixture",
        ])
        .into_result()?
        .get_silent();
        let error = parsed
            .invoke_in(
                &CancellationToken::new(),
                invocation.path(),
                &AppHome(invocation.path().join("app-home")),
            )
            .err()
            .ok_or_else(|| eyre::eyre!("unowned index cache was accepted"))?;
        assert!(
            error.to_string().contains("no valid tool-owned marker"),
            "{error:#}"
        );
        assert_eq!(fs::read(&sentinel)?, b"preserve me");
        assert_eq!(fs::read_dir(&cache)?.count(), 1);
        Ok(())
    }

    #[test]
    fn managed_index_receipts_exclude_private_and_parent_traversal_roots() {
        let directory = tempfile::tempdir().expect("roots");
        let cache_home = CacheHome(directory.path().join("managed"));
        let mut preflight = SourcePreflight::default();
        preflight.roots.push(SearchableSourceRoot {
            identity: "minecraft/main".to_owned(),
            provider_id: "platform".to_owned(),
            root: cache_home.0.join("source.filetree"),
        });
        assert!(managed_source_roots(&preflight, &cache_home));
        preflight.roots[0].root = directory.path().join("private/source.filetree");
        assert!(!managed_source_roots(&preflight, &cache_home));
        preflight.roots[0].root = cache_home.0.join("../private/source.filetree");
        assert!(!managed_source_roots(&preflight, &cache_home));
    }

    #[test]
    fn post_stub_preflight_reuse_requires_managed_ready_stubs_and_no_pending_acquisition() {
        let directory = tempfile::tempdir().expect("roots");
        let cache_home = CacheHome(directory.path().join("managed"));
        let target = "minecraft/classpath-type-stubs";
        let mut preflight = SourcePreflight::default();
        preflight.roots.push(SearchableSourceRoot {
            identity: target.to_owned(),
            provider_id: MINECRAFT_CLASSFILE_STUB_PROVIDER_ID.to_owned(),
            root: cache_home.0.join("stubs.filetree"),
        });
        assert!(reusable_managed_stub_preflight(
            &preflight,
            &cache_home,
            target,
            true,
        ));
        assert!(!reusable_managed_stub_preflight(
            &preflight,
            &cache_home,
            target,
            false,
        ));

        preflight
            .missing
            .push(crate::dependency_sources::UnavailableSource {
                target: "new/source".to_owned(),
                identity: "new/source/provider".to_owned(),
                reason: "pending".to_owned(),
                acquirable: true,
            });
        assert!(!reusable_managed_stub_preflight(
            &preflight,
            &cache_home,
            target,
            true,
        ));
        preflight.missing.clear();
        preflight
            .missing
            .push(crate::dependency_sources::UnavailableSource {
                target: "locked/synthetic".to_owned(),
                identity: "locked/synthetic/provider".to_owned(),
                reason: "missing locked source".to_owned(),
                acquirable: false,
            });
        assert!(!reusable_managed_stub_preflight(
            &preflight,
            &cache_home,
            target,
            true,
        ));
        preflight.missing.clear();
        preflight
            .components
            .push(crate::dependency_sources::SourceComponentPreflight {
                target: "undeclared/source".to_owned(),
                status: SourceStatus::NoneDeclared,
                preferred_provider: None,
                providers: Vec::new(),
            });
        preflight
            .missing
            .push(crate::dependency_sources::UnavailableSource {
                target: "undeclared/source".to_owned(),
                identity: "undeclared/source".to_owned(),
                reason: "no source providers are configured".to_owned(),
                acquirable: false,
            });
        assert!(reusable_managed_stub_preflight(
            &preflight,
            &cache_home,
            target,
            true,
        ));

        preflight.roots[0].root = directory.path().join("private/stubs.filetree");
        assert!(!reusable_managed_stub_preflight(
            &preflight,
            &cache_home,
            target,
            true,
        ));
        preflight.roots.clear();
        assert!(!reusable_managed_stub_preflight(
            &preflight,
            &cache_home,
            target,
            true,
        ));
    }

    #[test]
    fn catalog_source_reader_holds_nested_directories_until_index_publication() {
        let temporary = tempfile::tempdir().unwrap();
        let tree = temporary.path().join("platform.filetree");
        std::fs::create_dir_all(tree.join("src")).unwrap();
        std::fs::write(tree.join("src/Example.java"), b"class Example {}").unwrap();
        let selected = SourceProviderPreflight {
            id: "minecraft-pipeline".to_owned(),
            kind: SourceProviderKind::PlatformPipeline,
            priority: 0,
            status: SourceStatus::Acquired,
            searchable_roots: vec![tree.clone()],
            unavailable_reason: None,
        };
        let mut original = SourcePreflight::default();
        original
            .components
            .push(crate::dependency_sources::SourceComponentPreflight {
                target: "minecraft/client".to_owned(),
                status: SourceStatus::Acquired,
                preferred_provider: Some(selected.clone()),
                providers: vec![selected],
            });
        let mut held = hold_platform_source_roots(&original).unwrap();
        held.recheck().unwrap();
        let moved = temporary.path().join("moved");
        let rename = std::fs::rename(tree.join("src"), &moved);
        #[cfg(windows)]
        assert!(rename.is_err(), "nested source directory was not held");
        #[cfg(unix)]
        {
            rename.unwrap();
            assert!(held.recheck().is_err());
        }
        let mut stale = original.components[0].providers[0].clone();
        stale.status = SourceStatus::Missing;
        let mut changed = SourcePreflight::default();
        changed
            .components
            .push(crate::dependency_sources::SourceComponentPreflight {
                target: "minecraft/client".to_owned(),
                status: SourceStatus::Missing,
                preferred_provider: Some(stale.clone()),
                providers: vec![stale],
            });
        assert!(ensure_platform_sources_unchanged(&original, &changed).is_err());
        drop(held);
    }
}

impl SymbolIndexArgs {
    fn resolve_paths_in(&mut self, invocation_dir: &Path) {
        let resolve = |path: &mut Option<PathBuf>| {
            if let Some(path) = path
                && path.is_relative()
            {
                *path = invocation_dir.join(&*path);
            }
        };
        resolve(&mut self.cache_root);
        match &mut self.command {
            SymbolIndexCommand::Refresh(args) => {
                resolve(&mut args.project_root);
                resolve(&mut args.java_home);
            }
            SymbolIndexCommand::Show(args) => resolve(&mut args.project_root),
        }
    }

    /// # Errors
    ///
    /// Returns an error when branch resolution, acquisition, indexing, or
    /// immutable publication fails.
    pub fn invoke_in(
        mut self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
        app_home: &AppHome,
    ) -> eyre::Result<CliOutput> {
        self.resolve_paths_in(invocation_dir);
        let (branch, project_root) = match &self.command {
            SymbolIndexCommand::Refresh(args) => (&args.branch, &args.project_root),
            SymbolIndexCommand::Show(args) => (&args.branch, &args.project_root),
        };
        eyre::ensure!(
            branch.is_some() != project_root.is_some(),
            "symbol index requires exactly one of --branch and --project-root"
        );
        let selected = if let Some(project_root) = project_root {
            select_project_source_cache(
                project_root,
                self.cache_root.as_deref(),
                cancellation_token,
            )?
        } else {
            select_branch_source_cache(
                branch.as_ref().ok_or_else(|| {
                    eyre::eyre!("symbol index requires exactly one of --branch and --project-root")
                })?,
                self.cache_root.as_deref(),
                cancellation_token,
                app_home,
            )?
        };
        match self.command {
            SymbolIndexCommand::Refresh(args) => args.invoke_in(
                cancellation_token,
                invocation_dir,
                selected.home(),
                app_home,
            ),
            SymbolIndexCommand::Show(args) => {
                args.invoke_in(invocation_dir, selected.home().clone(), app_home)
            }
        }
    }
}

impl SymbolIndexRefreshArgs {
    fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
        cache_home: &CacheHome,
        app_home: &AppHome,
    ) -> eyre::Result<CliOutput> {
        enforce_refresh_parent_memory_limit()?;
        cancellation_token.bail_if_cancelled()?;
        let started = Instant::now();
        let mut stage_started = Instant::now();
        let resolved = resolve_selected_index_context(
            self.branch.as_ref(),
            self.project_root.as_deref(),
            invocation_dir,
            cache_home.clone(),
            self.java_home.as_deref(),
            app_home,
        )?;
        let branch = self
            .branch
            .clone()
            .unwrap_or_else(|| BranchSelector(resolved.inventory.target.branch.0.clone()));
        record_index_refresh_stage("resolve_context", &mut stage_started);
        let preflight = self.acquire_declared_sources(
            &resolved,
            &branch,
            cancellation_token,
            &mut stage_started,
            app_home,
        )?;
        let filter = SourceProviderFilter::default();
        cancellation_token.bail_if_cancelled()?;
        let mut held_platform_roots = hold_platform_source_roots(&preflight)?;
        held_platform_roots.recheck()?;
        record_index_refresh_stage("hold_platform_roots", &mut stage_started);
        let completeness = if preflight.missing.is_empty() {
            DependencySymbolIndexCompleteness::Complete
        } else {
            DependencySymbolIndexCompleteness::Partial
        };
        let selected_source_inputs = source_inputs(&resolved.identity, &preflight)?;
        if managed_source_roots(&preflight, cache_home) && !self.rebuild {
            let reusable = resolved.store.reusable_completed(
                &resolved.identity,
                &selected_source_inputs,
                completeness,
                cancellation_token,
            )?;
            record_index_refresh_stage("managed_receipt_lookup", &mut stage_started);
            if let Some((manifest, probe)) = reusable {
                cancellation_token.bail_if_cancelled()?;
                held_platform_roots.recheck()?;
                let current = preflight_resolved_index_sources(&resolved, &filter)?;
                ensure_platform_sources_unchanged(&preflight, &current)?;
                record_index_refresh_stage("recheck_reused_sources", &mut stage_started);
                let output = status_output(
                    "refresh",
                    &branch,
                    &resolved,
                    &probe,
                    completeness,
                    Some(manifest.counts),
                    &preflight,
                )?;
                return Ok(CliOutput::facet_with_status(
                    output,
                    status_for_probe(probe.status),
                ));
            }
        }
        let artifacts = self.build_selected_sources(
            &resolved,
            &preflight,
            &branch,
            invocation_dir,
            cancellation_token,
            app_home,
        )?;
        cancellation_token.bail_if_cancelled()?;
        held_platform_roots.recheck()?;
        let current = preflight_resolved_index_sources(&resolved, &filter)?;
        ensure_platform_sources_unchanged(&preflight, &current)?;

        let manifest = DependencySymbolIndexManifest::for_prepared_payload(
            resolved.identity.clone(),
            DependencySymbolIndexCreationMetadata {
                created_at_utc: Utc::now().to_rfc3339(),
                producer: format!("sfm-propagate-changes/{}", env!("CARGO_PKG_VERSION")),
                refresh_duration_ms: u64::try_from(started.elapsed().as_millis())
                    .unwrap_or(u64::MAX),
            },
            selected_source_inputs,
            artifacts.counts().clone(),
            completeness,
            DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
            artifacts.records_path(),
        )?;
        resolved.publish_refreshed(&branch, manifest, &artifacts, &preflight)
    }

    fn acquire_declared_sources(
        &self,
        resolved: &ResolvedIndexContext,
        branch: &BranchSelector,
        cancellation_token: &CancellationToken,
        stage_started: &mut Instant,
        app_home: &AppHome,
    ) -> eyre::Result<SourcePreflight> {
        acquire_locked_index_artifact_sources(resolved, cancellation_token)?;
        record_index_refresh_stage("acquire_locked_artifacts", stage_started);
        let filter = SourceProviderFilter::default();
        let mut preflight =
            materialize_classfile_stub_source(resolved, &filter, cancellation_token)?;
        record_index_refresh_stage("prepare_classfile_stubs", stage_started);
        let missing_targets = preflight
            .missing
            .iter()
            .filter(|missing| {
                missing.acquirable && !is_minecraft_classfile_stub_target(&missing.target)
            })
            .map(|missing| missing.target.clone())
            .collect::<BTreeSet<_>>();
        if !missing_targets.is_empty() {
            let mut targets = Vec::new();
            for target in &missing_targets {
                targets.extend(acquisition_targets(
                    &resolved.inventory,
                    Some(target),
                    false,
                    &filter,
                )?);
            }
            acquire_selected_sources(
                &resolved.inventory,
                &targets,
                branch,
                Parallelism::default(),
                self.java_home.as_deref(),
                cancellation_token,
                app_home,
            )?;
            preflight = preflight_resolved_index_sources(resolved, &filter)?;
        }

        record_index_refresh_stage("acquire_declared_sources", stage_started);
        Ok(preflight)
    }

    fn build_selected_sources(
        &self,
        resolved: &ResolvedIndexContext,
        preflight: &SourcePreflight,
        branch: &BranchSelector,
        invocation_dir: &Path,
        cancellation_token: &CancellationToken,
        app_home: &AppHome,
    ) -> eyre::Result<DependencyIndexBuildArtifacts> {
        let roots = preflight
            .roots
            .iter()
            .map(|root| root.root.clone())
            .collect::<Vec<_>>();
        let origins = preflight
            .roots
            .iter()
            .enumerate()
            .map(|(index, root)| DependencyJavaSourceOrigin {
                report_root: format!("custom-{index}"),
                portable_prefix: format!("dependency/{}/{}", root.identity, root.provider_id),
                source_set: format!("dependency:{}", root.identity.replace('/', ":")),
            })
            .collect::<Vec<_>>();
        let artifacts = if roots.is_empty() {
            DependencyIndexBuildArtifacts::empty(&resolved.identity)?
        } else {
            let mut workspace = if let Some(project_root) = &self.project_root {
                JavaSourceWorkspace::resolve_generated_dependency_roots(
                    project_root,
                    &roots,
                    &resolved.cache_home.0,
                    self.java_home.as_deref(),
                )?
            } else {
                JavaSourceWorkspace::resolve(
                    branch.clone(),
                    &roots,
                    JavaClasspathMode::Branch,
                    invocation_dir,
                    app_home,
                )?
            };
            apply_portable_origins(&mut workspace, &origins);
            build_dependency_index_sharded(&workspace, &resolved.identity, cancellation_token)?
        };
        Ok(artifacts)
    }
}

fn record_index_refresh_stage(stage: &'static str, started: &mut Instant) {
    tracing::info!(
        stage,
        elapsed_ms = started.elapsed().as_millis(),
        "dependency index refresh stage"
    );
    *started = Instant::now();
}

fn materialize_classfile_stub_source(
    resolved: &ResolvedIndexContext,
    filter: &SourceProviderFilter,
    cancellation_token: &CancellationToken,
) -> eyre::Result<SourcePreflight> {
    let mut stage_started = Instant::now();
    let preflight = preflight_resolved_index_sources(resolved, filter)?;
    record_index_refresh_stage("initial_source_preflight", &mut stage_started);
    let classfile_stubs = materialize_locked_minecraft_classfile_stubs(
        &resolved.inventory,
        &resolved.minecraft_classfile_stub_source,
        &preflight,
        cancellation_token,
        &http_fetcher()?,
    )?;
    record_index_refresh_stage("materialize_or_reuse_stubs", &mut stage_started);
    tracing::info!(
        libraries = classfile_stubs.libraries,
        types = classfile_stubs.types,
        shadowed_types = classfile_stubs.shadowed_types,
        reused = classfile_stubs.reused,
        "materialized locked Minecraft classfile type stubs"
    );
    if reusable_managed_stub_preflight(
        &preflight,
        &resolved.inventory.cache_home,
        &resolved.minecraft_classfile_stub_source.target,
        classfile_stubs.reused,
    ) {
        // The initial pass already admitted these exact managed roots and the
        // stub materializer changed nothing. Retain the final preflight after
        // the index receipt/build: another tool-owned stub writer may replace
        // this version-scoped tree under a different shadow set meanwhile.
        record_index_refresh_stage("post_stub_source_preflight_reused", &mut stage_started);
        return Ok(preflight);
    }
    let current = preflight_resolved_index_sources(resolved, filter)?;
    record_index_refresh_stage("post_stub_source_preflight", &mut stage_started);
    Ok(current)
}

fn reusable_managed_stub_preflight(
    preflight: &SourcePreflight,
    cache_home: &CacheHome,
    stub_target: &str,
    stubs_reused: bool,
) -> bool {
    stubs_reused
        && managed_source_roots(preflight, cache_home)
        // An unacquirable synthetic source can still appear in a separate
        // process. Only undeclared providers are permanently unavailable for
        // this frozen effective lock.
        && preflight.missing.iter().all(|missing| {
            !missing.acquirable
                && preflight.components.iter().any(|component| {
                    component.target == missing.target
                        && component.providers.is_empty()
                        && component.status == SourceStatus::NoneDeclared
                })
        })
        && preflight.roots.iter().any(|root| {
            root.identity == stub_target && root.provider_id == MINECRAFT_CLASSFILE_STUB_PROVIDER_ID
        })
}

fn apply_portable_origins(
    workspace: &mut JavaSourceWorkspace,
    origins: &[DependencyJavaSourceOrigin],
) {
    let origins = origins
        .iter()
        .map(|origin| (origin.report_root.as_str(), origin))
        .collect::<BTreeMap<_, _>>();
    for file in &mut workspace.files {
        let Some((root, relative)) = file.report_path.split_once('/') else {
            continue;
        };
        let Some(origin) = origins.get(root) else {
            continue;
        };
        file.report_path = format!(
            "{}/{}",
            origin.portable_prefix.trim_end_matches('/'),
            relative
        );
        file.source_set.clone_from(&origin.source_set);
    }
}

impl SymbolIndexShowArgs {
    fn invoke_in(
        self,
        invocation_dir: &Path,
        cache_home: CacheHome,
        app_home: &AppHome,
    ) -> eyre::Result<CliOutput> {
        let resolved = resolve_selected_index_context(
            self.branch.as_ref(),
            self.project_root.as_deref(),
            invocation_dir,
            cache_home,
            None,
            app_home,
        )?;
        let branch = self
            .branch
            .clone()
            .unwrap_or_else(|| BranchSelector(resolved.inventory.target.branch.0.clone()));
        let probe = resolved.store.probe(&resolved.identity)?;
        let preflight =
            preflight_resolved_index_sources(&resolved, &SourceProviderFilter::default())?;
        let loaded = probe
            .loadable
            .then(|| {
                resolved.store.validated_payload(
                    &resolved.identity,
                    DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
                )
            })
            .transpose()?;
        let completeness = loaded.as_ref().map_or(
            DependencySymbolIndexCompleteness::Partial,
            |(manifest, _)| manifest.completeness,
        );
        let counts = loaded.map(|(manifest, _)| manifest.counts);
        let output = status_output(
            "show",
            &branch,
            &resolved,
            &probe,
            completeness,
            counts,
            &preflight,
        )?;
        Ok(CliOutput::facet_with_status(
            output,
            status_for_probe(probe.status),
        ))
    }
}

struct ResolvedIndexContext {
    cache_home: CacheHome,
    inventory: DependencyInventory,
    identity: DependencySymbolIndexIdentity,
    store: DependencySymbolIndexStore,
    locked_artifact_sources: Vec<LockedArtifactSource>,
    minecraft_classfile_stub_source: LockedMinecraftClassfileStubSource,
}

impl ResolvedIndexContext {
    fn publish_refreshed(
        &self,
        branch: &BranchSelector,
        manifest: DependencySymbolIndexManifest,
        artifacts: &DependencyIndexBuildArtifacts,
        preflight: &SourcePreflight,
    ) -> eyre::Result<CliOutput> {
        self.store
            .publish_prepared(&manifest, artifacts.records_path())?;
        let probe = self.store.probe(&self.identity)?;
        let output = status_output(
            "refresh",
            branch,
            self,
            &probe,
            manifest.completeness,
            Some(manifest.counts),
            preflight,
        )?;
        Ok(CliOutput::facet_with_status(
            output,
            status_for_probe(probe.status),
        ))
    }
}

fn acquire_locked_index_artifact_sources(
    resolved: &ResolvedIndexContext,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    acquire_locked_artifact_sources(
        &resolved.inventory,
        &resolved.locked_artifact_sources,
        cancellation_token,
        &http_fetcher()?,
    )
}

fn resolve_selected_index_context(
    branch: Option<&BranchSelector>,
    project_root: Option<&Path>,
    invocation_dir: &Path,
    cache_home: CacheHome,
    java_home: Option<&Path>,
    app_home: &AppHome,
) -> eyre::Result<ResolvedIndexContext> {
    if let Some(project_root) = project_root {
        eyre::ensure!(branch.is_none(), "select either branch or catalog project");
        let jdk_cache = cache_home.join("minecraft-toolchain").join("jbrsdk");
        let workspace = JavaSourceWorkspace::resolve_generated_project_with_cache(
            project_root,
            &jdk_cache,
            &cache_home.0,
            java_home,
        )?;
        let inventory = DependencyInventory::load_project(project_root, cache_home.clone())?;
        return resolve_index_context_with_inventory(inventory, &workspace.context, cache_home);
    }
    let branch = branch.ok_or_else(|| eyre::eyre!("branch or catalog project required"))?;
    let workspace = JavaSourceWorkspace::resolve(
        branch.clone(),
        &[],
        JavaClasspathMode::Branch,
        invocation_dir,
        app_home,
    )?;
    resolve_index_context_from_analysis(branch, &workspace.context, cache_home, app_home)
}

fn resolve_index_context_from_analysis(
    branch: &BranchSelector,
    analysis_context: &JavaAnalysisContextOutput,
    cache_home: CacheHome,
    app_home: &AppHome,
) -> eyre::Result<ResolvedIndexContext> {
    let inventory = DependencyInventory::load(
        &branch.clone().into_query(app_home)?,
        cache_home.clone(),
        app_home,
    )?;
    resolve_index_context_with_inventory(inventory, analysis_context, cache_home)
}

fn resolve_index_context_with_inventory(
    mut inventory: DependencyInventory,
    analysis_context: &JavaAnalysisContextOutput,
    cache_home: CacheHome,
) -> eyre::Result<ResolvedIndexContext> {
    let minecraft_classfile_stub_source =
        attach_locked_minecraft_classfile_stub_source(&mut inventory)?;
    let locked_artifact_sources = derive_locked_loader_artifact_sources(&inventory)?;
    let inputs = derive_dependency_symbol_index_projection_inputs(&inventory, analysis_context)?;
    let projection = project_dependency_symbol_index_identity_with_locked_sources(
        &inventory,
        inputs,
        &locked_artifact_sources,
    )?;
    let identity = DependencySymbolIndexIdentity::from_projection(projection)?;
    Ok(ResolvedIndexContext {
        cache_home: cache_home.clone(),
        inventory,
        identity,
        store: DependencySymbolIndexStore::new(cache_home),
        locked_artifact_sources,
        minecraft_classfile_stub_source,
    })
}

/// Resolve the exact index identity and source inputs used by a CLI scenario.
///
/// This shares the production projection and performs no acquisition or
/// publication. The caller supplies its scoped cache home explicitly.
///
/// # Errors
///
/// Returns an error if the branch, dependency projection, or source preflight
/// cannot be resolved from the supplied scenario inputs.
pub fn scenario_dependency_index_inputs(
    branch: &BranchSelector,
    analysis_context: &JavaAnalysisContextOutput,
    cache_home: CacheHome,
    app_home: &AppHome,
) -> eyre::Result<(
    DependencySymbolIndexIdentity,
    Vec<DependencySymbolIndexSourceInput>,
)> {
    let resolved =
        resolve_index_context_from_analysis(branch, analysis_context, cache_home, app_home)?;
    let preflight = preflight_resolved_index_sources(&resolved, &SourceProviderFilter::default())?;
    let inputs = source_inputs(&resolved.identity, &preflight)?;
    Ok((resolved.identity, inputs))
}

#[derive(Clone, Copy)]
pub(super) enum DependencySymbolQuery<'a> {
    Definition(&'a JavaSymbolSelector),
    Usages(&'a JavaSymbolSelector),
    List(&'a JavaSymbolGlob),
}

pub(super) struct SymbolQueryIndex {
    pub index: JavaSymbolIndex,
    pub dependency_index: Option<DependencySymbolIndexQueryOutput>,
    pub project_diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    pub project_incomplete: bool,
}

impl DependencySymbolQuery<'_> {
    fn exact_generated_selector(self) -> Option<JavaSymbolSelector> {
        match self {
            Self::Definition(selector) | Self::Usages(selector) => {
                let owner = selector.owner();
                (owner.contains('.') && !owner.contains(['*', '?', ' '])).then(|| selector.clone())
            }
            Self::List(pattern) => {
                let owner = pattern.pattern();
                (owner.contains('.') && !owner.contains(['*', '?', ' '])).then(|| {
                    JavaSymbolSelector::Type {
                        owner: owner.to_owned(),
                    }
                })
            }
        }
    }

    fn has_local_match(self, index: &JavaSymbolIndex) -> bool {
        match self {
            Self::Definition(selector) | Self::Usages(selector) => {
                !index.definition(selector).definitions.is_empty()
            }
            Self::List(pattern) => !index.list(pattern).definitions.is_empty(),
        }
    }

    fn requires_live_members(self) -> bool {
        match self {
            Self::Definition(selector) => selector.to_output().kind != JavaSymbolSelectorKind::Type,
            Self::Usages(_) | Self::List(_) => true,
        }
    }

    fn matches_definition(
        self,
        kind: JavaSymbolKind,
        owner: &str,
        name: &str,
        descriptor: Option<&str>,
        qualified_name: &str,
    ) -> bool {
        match self {
            Self::Definition(selector) | Self::Usages(selector) => {
                selector.matches_parts(kind, owner, name, descriptor, qualified_name)
            }
            Self::List(pattern) => {
                pattern.matches_parts(kind, owner, name, descriptor, qualified_name)
            }
        }
    }

    fn matches_usage(
        self,
        kind: JavaSymbolKind,
        owner: &str,
        name: &str,
        descriptor: Option<&str>,
        qualified_name: &str,
    ) -> bool {
        match self {
            Self::Usages(selector) => {
                selector.matches_parts(kind, owner, name, descriptor, qualified_name)
            }
            Self::Definition(_) | Self::List(_) => false,
        }
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "query orchestration keeps the measured dependency/live stages and both migration paths explicit"
)]
pub(super) fn build_query_index(
    selection: &ResolvedSymbolQueryWorkspace,
    include_usages: bool,
    query: DependencySymbolQuery<'_>,
    cancellation_token: &CancellationToken,
    app_home: &AppHome,
) -> eyre::Result<SymbolQueryIndex> {
    let workspace = &selection.workspace;
    let branch = &selection.branch;
    let project_root = selection.project_root.as_deref();
    let cache_root = selection.cache_root.as_deref();
    let indexed_catalog_project = catalog_project_has_dependency_index(project_root)?;
    if workspace.context.classpath_mode == JavaClasspathMode::Isolated && !indexed_catalog_project {
        let mut index = if !legacy_definition_pipeline_requested()
            && !include_usages
            && matches!(query, DependencySymbolQuery::Definition(_))
        {
            let DependencySymbolQuery::Definition(selector) = query else {
                unreachable!("isolated fast path requires a definition selector")
            };
            build_live_definition_index_in_process(workspace, &[], selector)?
        } else if include_usages {
            JavaSymbolIndex::build(workspace)?
        } else {
            JavaSymbolIndex::build_definitions(workspace)?
        };
        let mut project_diagnostics = Vec::new();
        let mut project_incomplete = false;
        if let (Some(project_root), Some(selector)) =
            (project_root, query.exact_generated_selector())
            && !query.has_local_match(&index)
        {
            let selected =
                select_project_source_cache(project_root, cache_root, cancellation_token)?;
            let scan = scan_generated_dependency_symbol(
                project_root,
                &selected.home().0,
                &selector,
                cancellation_token,
            )?;
            project_diagnostics.clone_from(&scan.body.diagnostics);
            project_incomplete = !scan.complete;
            if !scan.body.definitions.is_empty() {
                index = JavaSymbolIndex::build_with_dependencies(
                    workspace,
                    &scan.body,
                    include_usages,
                )?;
            }
        }
        return Ok(SymbolQueryIndex {
            index,
            dependency_index: None,
            project_diagnostics,
            project_incomplete,
        });
    }

    let mut timings = LiveQueryTimings::start(workspace.files.len());
    let selected = if let Some(project_root) = project_root {
        select_project_source_cache(project_root, cache_root, cancellation_token)?
    } else {
        select_branch_source_cache(branch, cache_root, cancellation_token, app_home)?
    };
    let resolved = if let Some(project_root) = project_root {
        resolve_index_context_with_inventory(
            DependencyInventory::load_project(project_root, selected.home().clone())?,
            &workspace.context,
            selected.home().clone(),
        )?
    } else {
        resolve_index_context_from_analysis(
            branch,
            &workspace.context,
            selected.home().clone(),
            app_home,
        )?
    };
    timings.finish(LiveQueryStage::ResolveContext);
    let _live_query_lock = ArtifactLock::acquire_with_cancellation(
        resolved.store.live_query_lock_path(),
        "live Java symbol query",
        cancellation_token.clone(),
    )?;
    timings.finish(LiveQueryStage::AcquireLock);
    let probe = resolved.store.probe(&resolved.identity)?;
    let preflight = preflight_resolved_index_sources(&resolved, &SourceProviderFilter::default())?;
    timings.finish(LiveQueryStage::ProbeSources);
    let complete_definition = probe.loadable
        && !legacy_definition_pipeline_requested()
        && !include_usages
        && matches!(query, DependencySymbolQuery::Definition(_));
    let (loaded, mut prebuilt_live_index) = if complete_definition {
        timings.finish(LiveQueryStage::ReadWorkspaceVocabulary);
        let loaded = load_dependency_query_scan(&resolved, query, true)?;
        timings.finish(LiveQueryStage::ScanDependencyIndex);
        let live_index = build_live_definition_index_streaming(
            workspace,
            &loaded.1.resolution,
            true,
            cancellation_token,
        )?;
        timings.finish(LiveQueryStage::BuildLiveIndex);
        (Some(loaded), Some(live_index))
    } else if probe.loadable {
        let (manifest, payload_path) = resolved.store.validated_payload(
            &resolved.identity,
            DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
        )?;
        let (resolution_identifiers, resolution_member_accesses) = if query.requires_live_members()
        {
            workspace_resolution_vocabulary(workspace)?
        } else {
            (BTreeSet::new(), BTreeSet::new())
        };
        timings.finish(LiveQueryStage::ReadWorkspaceVocabulary);
        let scan = scan_dependency_java_symbol_index(
            &payload_path,
            &resolved.identity,
            &manifest.counts,
            DependencyResolutionSelection::MatchingVocabulary {
                identifiers: &resolution_identifiers,
                member_accesses: &resolution_member_accesses,
            },
            |kind, owner, name, descriptor, qualified_name| {
                query.matches_definition(kind, owner, name, descriptor, qualified_name)
            },
            |kind, owner, name, descriptor, qualified_name| {
                query.matches_usage(kind, owner, name, descriptor, qualified_name)
            },
        )?;
        timings.finish(LiveQueryStage::ScanDependencyIndex);
        (Some((manifest, scan)), None)
    } else {
        timings.finish(LiveQueryStage::ReadWorkspaceVocabulary);
        timings.finish(LiveQueryStage::ScanDependencyIndex);
        (None, None)
    };
    let index = match &loaded {
        Some((_, scan))
            if !legacy_definition_pipeline_requested()
                && !include_usages
                && matches!(query, DependencySymbolQuery::Definition(_)) =>
        {
            let live_index = if let Some(index) = prebuilt_live_index.take() {
                index
            } else {
                let index = build_live_definition_index_streaming(
                    workspace,
                    &scan.resolution,
                    query.requires_live_members(),
                    cancellation_token,
                )?;
                timings.finish(LiveQueryStage::BuildLiveIndex);
                index
            };
            let mut live_body = live_index.dependency_body(&[]);
            live_body.definitions.retain(|definition| {
                query.matches_definition(
                    definition.symbol.kind,
                    &definition.symbol.owner,
                    &definition.symbol.name,
                    definition.symbol.descriptor.as_deref(),
                    &definition.symbol.qualified_name,
                )
            });
            live_body.usages.retain(|usage| {
                query.matches_usage(
                    usage.target.kind,
                    &usage.target.owner,
                    &usage.target.name,
                    usage.target.descriptor.as_deref(),
                    &usage.target.qualified_name,
                )
            });
            timings.finish(LiveQueryStage::ScanLiveIndex);
            let index = JavaSymbolIndex::from_sharded_query_bodies(
                workspace.context.clone(),
                live_body,
                scan.body.clone(),
            );
            timings.finish(LiveQueryStage::MergeIndex);
            index
        }
        Some((_, scan)) => {
            let live_artifacts = build_java_index_sharded(
                workspace,
                &resolved.identity,
                &scan.resolution,
                include_usages,
                cancellation_token,
            )?;
            timings.finish(LiveQueryStage::BuildLiveIndex);
            let live_scan = scan_dependency_java_symbol_index(
                live_artifacts.records_path(),
                &resolved.identity,
                live_artifacts.counts(),
                DependencyResolutionSelection::None,
                |kind, owner, name, descriptor, qualified_name| {
                    query.matches_definition(kind, owner, name, descriptor, qualified_name)
                },
                |kind, owner, name, descriptor, qualified_name| {
                    query.matches_usage(kind, owner, name, descriptor, qualified_name)
                },
            )?;
            timings.finish(LiveQueryStage::ScanLiveIndex);
            let index = JavaSymbolIndex::from_sharded_query_bodies(
                workspace.context.clone(),
                live_scan.body,
                scan.body.clone(),
            );
            timings.finish(LiveQueryStage::MergeIndex);
            index
        }
        None if !legacy_definition_pipeline_requested()
            && !include_usages
            && matches!(query, DependencySymbolQuery::Definition(_)) =>
        {
            let index =
                build_live_definition_index_streaming(workspace, &[], true, cancellation_token)?;
            timings.finish(LiveQueryStage::BuildLiveIndex);
            timings.finish(LiveQueryStage::ScanLiveIndex);
            timings.finish(LiveQueryStage::MergeIndex);
            index
        }
        None if include_usages => {
            let index = JavaSymbolIndex::build(workspace)?;
            timings.finish(LiveQueryStage::BuildLiveIndex);
            timings.finish(LiveQueryStage::ScanLiveIndex);
            timings.finish(LiveQueryStage::MergeIndex);
            index
        }
        None => {
            let index = JavaSymbolIndex::build_definitions(workspace)?;
            timings.finish(LiveQueryStage::BuildLiveIndex);
            timings.finish(LiveQueryStage::ScanLiveIndex);
            timings.finish(LiveQueryStage::MergeIndex);
            index
        }
    };
    let completeness = loaded.as_ref().map_or(
        SymbolQueryCompleteness::Incomplete,
        |(manifest, _)| match manifest.completeness {
            DependencySymbolIndexCompleteness::Complete => SymbolQueryCompleteness::Complete,
            DependencySymbolIndexCompleteness::Partial => SymbolQueryCompleteness::Incomplete,
        },
    );
    let evidence = DependencySymbolIndexQueryOutput {
        status: probe.status,
        completeness,
        expected_identity: resolved.identity.digest,
        portable_path: probe.layout.portable.root,
        path: probe.layout.concrete.root.display().to_string(),
        reason: probe.reason,
        refresh_command: render_refresh_command(branch, project_root, cache_root)?,
        acquisition_commands: acquisition_commands(&preflight, branch, project_root, cache_root)?,
    };
    Ok(SymbolQueryIndex {
        index,
        dependency_index: Some(evidence),
        project_diagnostics: Vec::new(),
        project_incomplete: false,
    })
}

fn catalog_project_has_dependency_index(project_root: Option<&Path>) -> eyre::Result<bool> {
    let Some(project_root) = project_root else {
        return Ok(false);
    };
    let lockfile = project_root.join("sfm-toolchain.lock.json");
    if !lockfile.is_file() {
        return Ok(false);
    }
    Ok(matches!(
        parse_document(&read_to_string(lockfile)?)?,
        ToolchainLockfileDocument::V3(_) | ToolchainLockfileDocument::V4(_)
    ))
}

/// Load the immutable dependency declarations needed by a correctness-first
/// direct definition-at-position query. The later supervised provider can
/// retain this body instead of rescanning it for each request.
pub(super) fn load_definition_at_position_dependencies(
    selection: &ResolvedSymbolQueryWorkspace,
    cancellation_token: &CancellationToken,
    app_home: &AppHome,
) -> eyre::Result<(
    Option<DependencyJavaSymbolIndexBody>,
    Option<DependencySymbolIndexQueryOutput>,
    Vec<DefinitionDependencySourceRoot>,
)> {
    let workspace = &selection.workspace;
    let branch = &selection.branch;
    let project_root = selection.project_root.as_deref();
    let cache_root = selection.cache_root.as_deref();
    if workspace.context.classpath_mode == JavaClasspathMode::Isolated
        && !catalog_project_has_dependency_index(project_root)?
    {
        return Ok((None, None, Vec::new()));
    }

    let selected = if let Some(project_root) = project_root {
        select_project_source_cache(project_root, cache_root, cancellation_token)?
    } else {
        select_branch_source_cache(branch, cache_root, cancellation_token, app_home)?
    };
    let resolved = if let Some(project_root) = project_root {
        resolve_index_context_with_inventory(
            DependencyInventory::load_project(project_root, selected.home().clone())?,
            &workspace.context,
            selected.home().clone(),
        )?
    } else {
        resolve_index_context_from_analysis(
            branch,
            &workspace.context,
            selected.home().clone(),
            app_home,
        )?
    };
    let _live_query_lock = ArtifactLock::acquire_with_cancellation(
        resolved.store.live_query_lock_path(),
        "direct definition-at-position query",
        cancellation_token.clone(),
    )?;
    let probe = resolved.store.probe(&resolved.identity)?;
    let preflight = preflight_resolved_index_sources(&resolved, &SourceProviderFilter::default())?;
    let loaded = if probe.loadable {
        let (manifest, payload_path) = resolved.store.validated_payload(
            &resolved.identity,
            DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
        )?;
        let scan = scan_dependency_java_symbol_index(
            &payload_path,
            &resolved.identity,
            &manifest.counts,
            DependencyResolutionSelection::None,
            |_, _, _, _, _| true,
            |_, _, _, _, _| false,
        )?;
        Some((manifest, scan.body))
    } else {
        None
    };
    let completeness = loaded.as_ref().map_or(
        SymbolQueryCompleteness::Incomplete,
        |(manifest, _)| match manifest.completeness {
            DependencySymbolIndexCompleteness::Complete => SymbolQueryCompleteness::Complete,
            DependencySymbolIndexCompleteness::Partial => SymbolQueryCompleteness::Incomplete,
        },
    );
    let evidence = DependencySymbolIndexQueryOutput {
        status: probe.status,
        completeness,
        expected_identity: resolved.identity.digest,
        portable_path: probe.layout.portable.root,
        path: probe.layout.concrete.root.display().to_string(),
        reason: probe.reason,
        refresh_command: render_refresh_command(branch, project_root, cache_root)?,
        acquisition_commands: acquisition_commands(&preflight, branch, project_root, cache_root)?,
    };
    let source_roots = definition_dependency_source_roots(&preflight)?;
    Ok((loaded.map(|(_, body)| body), Some(evidence), source_roots))
}

fn definition_dependency_source_roots(
    preflight: &SourcePreflight,
) -> eyre::Result<Vec<DefinitionDependencySourceRoot>> {
    let mut projected = preflight
        .roots
        .iter()
        .map(|root| {
            Ok((
                format!("dependency/{}/{}", root.identity, root.provider_id),
                format!("dependency:{}", root.identity.replace('/', ":")),
                canonicalize(&root.root)?,
            ))
        })
        .collect::<eyre::Result<Vec<_>>>()?;
    projected.sort();
    projected.dedup();
    Ok(projected
        .into_iter()
        .enumerate()
        .map(
            |(index, (report_prefix, source_set, canonical_absolute_path))| {
                DefinitionDependencySourceRoot {
                    root_id: format!("dependency-source-{index}"),
                    report_prefix,
                    source_set,
                    canonical_absolute_path,
                }
            },
        )
        .collect())
}

fn legacy_definition_pipeline_requested() -> bool {
    var_os(LEGACY_DEFINITION_PIPELINE_ENV)
        .is_some_and(|value| value.to_string_lossy().eq_ignore_ascii_case("legacy"))
}

fn load_dependency_query_scan(
    resolved: &ResolvedIndexContext,
    query: DependencySymbolQuery<'_>,
    retain_all_resolution_types: bool,
) -> eyre::Result<(DependencySymbolIndexManifest, DependencyJavaSymbolIndexScan)> {
    let (manifest, payload_path) = resolved.store.validated_payload(
        &resolved.identity,
        DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
    )?;
    let scan = scan_dependency_java_symbol_index(
        &payload_path,
        &resolved.identity,
        &manifest.counts,
        if retain_all_resolution_types {
            DependencyResolutionSelection::AllTypes
        } else {
            DependencyResolutionSelection::None
        },
        |kind, owner, name, descriptor, qualified_name| {
            query.matches_definition(kind, owner, name, descriptor, qualified_name)
        },
        |kind, owner, name, descriptor, qualified_name| {
            query.matches_usage(kind, owner, name, descriptor, qualified_name)
        },
    )?;
    Ok((manifest, scan))
}

fn workspace_resolution_vocabulary(
    workspace: &JavaSourceWorkspace,
) -> eyre::Result<(BTreeSet<String>, BTreeSet<String>)> {
    let mut identifiers = BTreeSet::new();
    let mut member_accesses = BTreeSet::new();
    for file in &workspace.files {
        let source = read_to_string(&file.absolute_path)?;
        identifiers.extend(
            java_identifier_tokens(&source)
                .into_iter()
                .map(str::to_owned),
        );
        member_accesses.extend(
            java_member_access_tokens(&source)
                .into_iter()
                .map(str::to_owned),
        );
    }
    Ok((identifiers, member_accesses))
}

fn preflight_index_sources(
    inventory: &DependencyInventory,
    locked_artifact_sources: &[LockedArtifactSource],
    filter: &SourceProviderFilter,
) -> eyre::Result<SourcePreflight> {
    let dependencies = inventory
        .dependencies()
        .filter(|dependency| {
            dependency.kind != DependencyKindV3::Tool && dependency.role != DependencyRoleV3::Build
        })
        .map(|dependency| dependency.id.clone())
        .collect::<Vec<_>>();
    let mut preflight = preflight_sources(
        inventory,
        &dependencies,
        filter,
        SourceProviderSelection::PreferredMatching,
    )?;
    append_locked_artifact_source_preflight(inventory, locked_artifact_sources, &mut preflight)?;
    Ok(preflight)
}

fn preflight_resolved_index_sources(
    resolved: &ResolvedIndexContext,
    filter: &SourceProviderFilter,
) -> eyre::Result<SourcePreflight> {
    let mut preflight = preflight_index_sources(
        &resolved.inventory,
        &resolved.locked_artifact_sources,
        filter,
    )?;
    let source = &resolved.minecraft_classfile_stub_source;
    if materialized_classfile_stub_tree_matches(&resolved.inventory, source, &preflight)? {
        let tree = resolved
            .inventory
            .local_path(&source.provider.derived_checks.tree_cache_path)?;
        promote_materialized_classfile_stub_source(
            &mut preflight,
            &source.target,
            &source.provider.id,
            tree,
        )?;
    }
    Ok(preflight)
}

fn promote_materialized_classfile_stub_source(
    preflight: &mut SourcePreflight,
    target: &str,
    provider_id: &str,
    tree: PathBuf,
) -> eyre::Result<()> {
    let component = preflight
        .components
        .iter_mut()
        .find(|component| component.target == target)
        .ok_or_else(|| eyre::eyre!("materialized classfile stub target {target} is absent"))?;
    component.status = SourceStatus::Acquired;
    if let Some(provider) = component.preferred_provider.as_mut() {
        provider.status = SourceStatus::Acquired;
        provider.unavailable_reason = None;
    }
    for provider in &mut component.providers {
        provider.status = SourceStatus::Acquired;
        provider.unavailable_reason = None;
    }
    preflight.missing.retain(|missing| missing.target != target);
    preflight.roots.push(SearchableSourceRoot {
        identity: target.to_owned(),
        provider_id: provider_id.to_owned(),
        root: tree,
    });
    Ok(())
}

/// Keep catalog-owned platform directory ancestry stable while the index reads
/// it. A final sealed-archive check below rejects changed leaf bytes before
/// publishing, including on Unix where an open directory cannot deny rename.
#[derive(Default)]
struct HeldPlatformSourceRoots {
    leases: DirectoryLeases,
    directories: Vec<PathBuf>,
}

impl HeldPlatformSourceRoots {
    fn recheck(&mut self) -> eyre::Result<()> {
        for directory in &self.directories {
            self.leases.prepare(directory, "__sfm_source_reader__")?;
        }
        Ok(())
    }
}

fn hold_platform_source_roots(
    preflight: &SourcePreflight,
) -> eyre::Result<HeldPlatformSourceRoots> {
    let mut held = HeldPlatformSourceRoots::default();
    let mut pending = preflight
        .components
        .iter()
        .flat_map(|component| &component.providers)
        .filter(|provider| {
            provider.kind == SourceProviderKind::PlatformPipeline
                && provider.status == SourceStatus::Acquired
        })
        .flat_map(|provider| provider.searchable_roots.iter().cloned())
        .collect::<Vec<_>>();
    let mut seen = BTreeSet::new();
    while let Some(directory) = pending.pop() {
        if !seen.insert(directory.clone()) {
            continue;
        }
        held.leases.prepare(&directory, "__sfm_source_reader__")?;
        for entry in read_dir(&directory)? {
            let entry = entry?;
            let kind = entry.file_type()?;
            eyre::ensure!(
                !kind.is_symlink(),
                "platform source tree contains a link: {}",
                entry.path().display()
            );
            if kind.is_dir() {
                pending.push(entry.path());
            }
        }
        held.directories.push(directory);
    }
    Ok(held)
}

fn ensure_platform_sources_unchanged(
    original: &SourcePreflight,
    current: &SourcePreflight,
) -> eyre::Result<()> {
    for component in &original.components {
        for provider in component.providers.iter().filter(|provider| {
            provider.kind == SourceProviderKind::PlatformPipeline
                && provider.status == SourceStatus::Acquired
        }) {
            let still_ready = current.components.iter().any(|candidate| {
                candidate.target == component.target
                    && candidate.providers.iter().any(|now| {
                        now.id == provider.id
                            && now.kind == SourceProviderKind::PlatformPipeline
                            && now.status == SourceStatus::Acquired
                            && now.searchable_roots == provider.searchable_roots
                    })
            });
            eyre::ensure!(
                still_ready,
                "platform source changed while building the dependency index: {}/{}",
                component.target,
                provider.id
            );
        }
    }
    Ok(())
}

// Private or editable roots retain exhaustive refresh. A matching receipt
// alone is only sufficient for tool-owned source trees under the selected cache.
fn managed_source_roots(preflight: &SourcePreflight, cache_home: &CacheHome) -> bool {
    preflight.roots.iter().all(|root| {
        root.root.is_absolute()
            && root.root.starts_with(&cache_home.0)
            && !root
                .root
                .components()
                .any(|component| matches!(component, Component::ParentDir))
    })
}

fn source_inputs(
    identity: &DependencySymbolIndexIdentity,
    preflight: &SourcePreflight,
) -> eyre::Result<Vec<DependencySymbolIndexSourceInput>> {
    let mut inputs = Vec::new();
    for component in &preflight.components {
        let (dependency_id, component_id) = component.target.split_once('/').ok_or_else(|| {
            eyre::eyre!(
                "dependency source target `{}` is not dependency/component",
                component.target
            )
        })?;
        if component.providers.is_empty() {
            inputs.push(DependencySymbolIndexSourceInput {
                dependency: dependency_id.to_owned(),
                component: component_id.to_owned(),
                provider: "<none>".to_owned(),
                portable_origin: format!("dependency/{}/<none>", component.target),
                fingerprint: "unavailable:no-source-provider".to_owned(),
                status: DependencySymbolIndexInputStatus::Unavailable,
            });
            continue;
        }
        for provider in &component.providers {
            let semantic_provider = identity
                .projection
                .effective_lock
                .dependencies
                .iter()
                .find(|dependency| dependency.id == dependency_id)
                .and_then(|dependency| {
                    dependency
                        .components
                        .iter()
                        .find(|candidate| candidate.id == component_id)
                })
                .and_then(|component| component.preferred_provider.as_ref())
                .ok_or_else(|| {
                    eyre::eyre!(
                        "dependency index identity omitted preferred provider for {dependency_id}/{component_id}"
                    )
                })?;
            let semantic_json = facet_json::to_string(semantic_provider)?;
            inputs.push(DependencySymbolIndexSourceInput {
                dependency: dependency_id.to_owned(),
                component: component_id.to_owned(),
                provider: provider.id.clone(),
                portable_origin: format!(
                    "dependency/{}/{}/{}",
                    dependency_id, component_id, provider.id
                ),
                fingerprint: format!("blake3:{}", blake3::hash(semantic_json.as_bytes()).to_hex()),
                status: source_input_status(provider.status),
            });
        }
    }
    Ok(inputs)
}

const fn source_input_status(status: SourceStatus) -> DependencySymbolIndexInputStatus {
    match status {
        SourceStatus::Acquired => DependencySymbolIndexInputStatus::Ready,
        SourceStatus::Stale => DependencySymbolIndexInputStatus::Stale,
        SourceStatus::Missing | SourceStatus::Partial => DependencySymbolIndexInputStatus::Missing,
        SourceStatus::NoneDeclared => DependencySymbolIndexInputStatus::Unavailable,
    }
}

fn status_output(
    operation: &str,
    branch: &BranchSelector,
    resolved: &ResolvedIndexContext,
    probe: &DependencySymbolIndexProbe,
    completeness: DependencySymbolIndexCompleteness,
    counts: Option<DependencySymbolIndexCounts>,
    preflight: &SourcePreflight,
) -> eyre::Result<SymbolIndexStatusOutput> {
    let project_root = resolved
        .inventory
        .target
        .branch
        .as_str()
        .starts_with("project:")
        .then(|| resolved.inventory.lockfile_path.parent())
        .flatten();
    let cache_root = Some(resolved.cache_home.0.as_path());
    let acquisition_commands = acquisition_commands(preflight, branch, project_root, cache_root)?;
    Ok(SymbolIndexStatusOutput {
        schema: SYMBOL_INDEX_STATUS_SCHEMA.to_owned(),
        operation: operation.to_owned(),
        branch: branch.0.clone(),
        status: probe.status,
        completeness,
        expected_identity: resolved.identity.digest.clone(),
        portable_path: probe.layout.portable.root.clone(),
        path: probe.layout.concrete.root.display().to_string(),
        reason: probe.reason.clone(),
        refresh_command: render_refresh_command(branch, project_root, cache_root)?,
        acquisition_commands,
        counts,
    })
}

fn acquisition_commands(
    preflight: &SourcePreflight,
    branch: &BranchSelector,
    project_root: Option<&Path>,
    cache_root: Option<&Path>,
) -> eyre::Result<Vec<String>> {
    let commands = preflight
        .missing
        .iter()
        .filter(|missing| {
            missing.acquirable && !is_minecraft_classfile_stub_target(&missing.target)
        })
        .map(|missing| {
            if project_root.is_some() || cache_root.is_some() {
                let arguments = Cli {
                    global_args: GlobalArgs::default(),
                    command: CliCommand::Dependency(DependencyArgs {
                        command: DependencyCommand::Source(DependencySourceArgs {
                            cache_root: cache_root.map(Path::to_path_buf),
                            command: DependencySourceCommand::Acquire(
                                DependencySourceAcquireArgs {
                                    target: Some(missing.target.clone()),
                                    all: false,
                                    provider: None,
                                    provider_id: None,
                                    parallel: None,
                                    branch: project_root.is_none().then(|| branch.clone()),
                                    project_root: project_root.map(Path::to_path_buf),
                                    java_home: None,
                                },
                            ),
                        }),
                    }),
                    builtins: figue::FigueBuiltins::default(),
                }
                .to_args_string()
                .map_err(eyre::Report::from)?;
                Ok(format!(
                    "sfm-propagate-changes.exe {}",
                    arguments.to_string_lossy()
                ))
            } else {
                SourceAcquisitionRecommendation::target(
                    missing.target.clone(),
                    branch.clone(),
                    SourceProviderFilter::default(),
                )
                .render()
            }
        })
        .collect::<eyre::Result<BTreeSet<_>>>()?
        .into_iter()
        .collect::<Vec<_>>();
    Ok(commands)
}

fn render_refresh_command(
    branch: &BranchSelector,
    project_root: Option<&Path>,
    cache_root: Option<&Path>,
) -> eyre::Result<String> {
    let arguments = Cli {
        global_args: GlobalArgs::default(),
        command: CliCommand::Symbol(super::SymbolArgs {
            command: super::SymbolCommand::Index(SymbolIndexArgs {
                cache_root: cache_root.map(Path::to_path_buf),
                command: SymbolIndexCommand::Refresh(SymbolIndexRefreshArgs {
                    branch: project_root.is_none().then(|| branch.clone()),
                    project_root: project_root.map(Path::to_path_buf),
                    java_home: None,
                    rebuild: false,
                }),
            }),
        }),
        builtins: figue::FigueBuiltins::default(),
    }
    .to_args_string()
    .map_err(eyre::Report::from)?;
    Ok(format!(
        "sfm-propagate-changes.exe {}",
        arguments.to_string_lossy()
    ))
}

const fn status_for_probe(status: DependencySymbolIndexProbeStatus) -> u8 {
    match status {
        DependencySymbolIndexProbeStatus::Ready => 0,
        DependencySymbolIndexProbeStatus::Missing
        | DependencySymbolIndexProbeStatus::Stale
        | DependencySymbolIndexProbeStatus::Partial => 5,
    }
}
