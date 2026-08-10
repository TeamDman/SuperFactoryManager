use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::cli::Cli;
use crate::cli::Command as CliCommand;
use crate::cli::global_args::GlobalArgs;
use crate::cli::jar::BranchSelector;
use crate::cli::output::CliOutput;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::SourceStatus;
use crate::dependency_sources::SourceAcquisitionRecommendation;
use crate::dependency_sources::SourcePreflight;
use crate::dependency_sources::SourceProviderFilter;
use crate::dependency_sources::SourceProviderSelection;
use crate::dependency_sources::acquire_selected_sources;
use crate::dependency_sources::acquisition_targets;
use crate::dependency_sources::preflight_sources;
use crate::jar_build::Parallelism;
use crate::java_analysis::DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA;
use crate::java_analysis::DependencyIndexBuildArtifacts;
use crate::java_analysis::DependencyJavaSourceOrigin;
use crate::java_analysis::DependencyResolutionSelection;
use crate::java_analysis::DependencySymbolIndexCompleteness;
use crate::java_analysis::DependencySymbolIndexCreationMetadata;
use crate::java_analysis::DependencySymbolIndexIdentity;
use crate::java_analysis::DependencySymbolIndexInputStatus;
use crate::java_analysis::DependencySymbolIndexManifest;
use crate::java_analysis::DependencySymbolIndexProbe;
use crate::java_analysis::DependencySymbolIndexProbeStatus;
use crate::java_analysis::DependencySymbolIndexQueryOutput;
use crate::java_analysis::DependencySymbolIndexSourceInput;
use crate::java_analysis::DependencySymbolIndexStore;
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
use crate::java_analysis::project_dependency_symbol_index_identity;
use crate::java_analysis::scan_dependency_java_symbol_index;
use crate::paths::CacheHome;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3;
use chrono::Utc;
use facet::Facet;
use figue::ToArgs;
use figue::{self as args};
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
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
    pub counts: Option<crate::java_analysis::DependencySymbolIndexCounts>,
}

#[derive(Facet, Debug)]
pub struct SymbolIndexArgs {
    #[facet(args::subcommand)]
    pub command: SymbolIndexCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum SymbolIndexCommand {
    /// Acquire preferred dependency sources and atomically rebuild the index.
    Refresh(SymbolIndexRefreshArgs),
    /// Show the expected cache identity, status, and concrete/portable paths.
    Show(SymbolIndexShowArgs),
}

#[derive(Facet, Clone, Debug)]
pub struct SymbolIndexRefreshArgs {
    /// Branch selector whose effective dependency lock pins this index.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

#[derive(Facet, Clone, Debug)]
pub struct SymbolIndexShowArgs {
    /// Branch selector whose effective dependency lock pins this index.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl SymbolIndexArgs {
    /// # Errors
    ///
    /// Returns an error when branch resolution, acquisition, indexing, or
    /// immutable publication fails.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        match self.command {
            SymbolIndexCommand::Refresh(args) => args.invoke_in(cancellation_token, invocation_dir),
            SymbolIndexCommand::Show(args) => args.invoke_in(invocation_dir),
        }
    }
}

impl SymbolIndexRefreshArgs {
    fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        enforce_refresh_parent_memory_limit()?;
        cancellation_token.bail_if_cancelled()?;
        let started = Instant::now();
        let resolved = resolve_index_context(&self.branch, invocation_dir)?;
        let filter = SourceProviderFilter::default();
        let mut preflight = preflight_index_sources(&resolved.inventory, &filter)?;
        let missing_targets = preflight
            .missing
            .iter()
            .filter(|missing| missing.acquirable)
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
                &self.branch,
                Parallelism::default(),
                cancellation_token,
            )?;
            preflight = preflight_index_sources(&resolved.inventory, &filter)?;
        }

        cancellation_token.bail_if_cancelled()?;
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
            let mut workspace = JavaSourceWorkspace::resolve(
                self.branch.clone(),
                &roots,
                JavaClasspathMode::Branch,
                invocation_dir,
            )?;
            apply_portable_origins(&mut workspace, &origins);
            build_dependency_index_sharded(&workspace, &resolved.identity, cancellation_token)?
        };
        cancellation_token.bail_if_cancelled()?;

        let completeness = if preflight.missing.is_empty() {
            DependencySymbolIndexCompleteness::Complete
        } else {
            DependencySymbolIndexCompleteness::Partial
        };
        let manifest = DependencySymbolIndexManifest::for_prepared_payload(
            resolved.identity.clone(),
            DependencySymbolIndexCreationMetadata {
                created_at_utc: Utc::now().to_rfc3339(),
                producer: format!("sfm-propagate-changes/{}", env!("CARGO_PKG_VERSION")),
                refresh_duration_ms: u64::try_from(started.elapsed().as_millis())
                    .unwrap_or(u64::MAX),
            },
            source_inputs(&resolved.identity, &preflight)?,
            artifacts.counts().clone(),
            completeness,
            DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
            artifacts.records_path(),
        )?;
        resolved
            .store
            .publish_prepared(&manifest, artifacts.records_path())?;
        let probe = resolved.store.probe(&resolved.identity)?;
        let output = status_output(
            "refresh",
            &self.branch,
            &resolved,
            &probe,
            completeness,
            Some(manifest.counts),
            &preflight,
        )?;
        Ok(CliOutput::facet_with_status(
            output,
            status_for_probe(probe.status),
        ))
    }
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
    fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        let resolved = resolve_index_context(&self.branch, invocation_dir)?;
        let probe = resolved.store.probe(&resolved.identity)?;
        let preflight =
            preflight_index_sources(&resolved.inventory, &SourceProviderFilter::default())?;
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
            &self.branch,
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
    inventory: DependencyInventory,
    identity: DependencySymbolIndexIdentity,
    store: DependencySymbolIndexStore,
}

fn resolve_index_context(
    branch: &BranchSelector,
    invocation_dir: &Path,
) -> eyre::Result<ResolvedIndexContext> {
    let cache_home = CacheHome::resolve()?;
    let workspace = JavaSourceWorkspace::resolve(
        branch.clone(),
        &[],
        JavaClasspathMode::Branch,
        invocation_dir,
    )?;
    resolve_index_context_from_analysis(branch, &workspace.context, cache_home)
}

fn resolve_index_context_from_analysis(
    branch: &BranchSelector,
    analysis_context: &crate::java_analysis::JavaAnalysisContextOutput,
    cache_home: CacheHome,
) -> eyre::Result<ResolvedIndexContext> {
    let inventory = DependencyInventory::load(&branch.clone().into_query()?, cache_home.clone())?;
    let inputs = derive_dependency_symbol_index_projection_inputs(&inventory, analysis_context)?;
    let projection = project_dependency_symbol_index_identity(&inventory, inputs)?;
    let identity = DependencySymbolIndexIdentity::from_projection(projection)?;
    Ok(ResolvedIndexContext {
        inventory,
        identity,
        store: DependencySymbolIndexStore::new(cache_home),
    })
}

#[derive(Clone, Copy)]
pub(super) enum DependencySymbolQuery<'a> {
    Definition(&'a JavaSymbolSelector),
    Usages(&'a JavaSymbolSelector),
    List(&'a JavaSymbolGlob),
}

impl DependencySymbolQuery<'_> {
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
    workspace: &JavaSourceWorkspace,
    branch: &BranchSelector,
    include_usages: bool,
    query: DependencySymbolQuery<'_>,
    cancellation_token: &CancellationToken,
) -> eyre::Result<(JavaSymbolIndex, Option<DependencySymbolIndexQueryOutput>)> {
    if workspace.context.classpath_mode == JavaClasspathMode::Isolated {
        let index = if !legacy_definition_pipeline_requested()
            && !include_usages
            && matches!(query, DependencySymbolQuery::Definition(_))
        {
            build_live_definition_index_in_process(workspace, &[])?
        } else if include_usages {
            JavaSymbolIndex::build(workspace)?
        } else {
            JavaSymbolIndex::build_definitions(workspace)?
        };
        return Ok((index, None));
    }

    let mut timings = LiveQueryTimings::start(workspace.files.len());
    let resolved =
        resolve_index_context_from_analysis(branch, &workspace.context, CacheHome::resolve()?)?;
    timings.finish(LiveQueryStage::ResolveContext);
    let _live_query_lock = ArtifactLock::acquire_with_cancellation(
        resolved.store.live_query_lock_path(),
        "live Java symbol query",
        cancellation_token.clone(),
    )?;
    timings.finish(LiveQueryStage::AcquireLock);
    let probe = resolved.store.probe(&resolved.identity)?;
    let preflight = preflight_index_sources(&resolved.inventory, &SourceProviderFilter::default())?;
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
        refresh_command: render_refresh_command(branch)?,
        acquisition_commands: acquisition_commands(&preflight, branch)?,
    };
    Ok((index, Some(evidence)))
}

fn legacy_definition_pipeline_requested() -> bool {
    std::env::var_os(LEGACY_DEFINITION_PIPELINE_ENV)
        .is_some_and(|value| value.to_string_lossy().eq_ignore_ascii_case("legacy"))
}

fn load_dependency_query_scan(
    resolved: &ResolvedIndexContext,
    query: DependencySymbolQuery<'_>,
    retain_all_resolution_types: bool,
) -> eyre::Result<(
    DependencySymbolIndexManifest,
    crate::java_analysis::DependencyJavaSymbolIndexScan,
)> {
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
        let source = std::fs::read_to_string(&file.absolute_path)?;
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
    filter: &SourceProviderFilter,
) -> eyre::Result<SourcePreflight> {
    let dependencies = inventory
        .dependencies()
        .filter(|dependency| {
            dependency.kind != DependencyKindV3::Tool && dependency.role != DependencyRoleV3::Build
        })
        .map(|dependency| dependency.id.clone())
        .collect::<Vec<_>>();
    preflight_sources(
        inventory,
        &dependencies,
        filter,
        SourceProviderSelection::PreferredMatching,
    )
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
    counts: Option<crate::java_analysis::DependencySymbolIndexCounts>,
    preflight: &SourcePreflight,
) -> eyre::Result<SymbolIndexStatusOutput> {
    let acquisition_commands = acquisition_commands(preflight, branch)?;
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
        refresh_command: render_refresh_command(branch)?,
        acquisition_commands,
        counts,
    })
}

fn acquisition_commands(
    preflight: &SourcePreflight,
    branch: &BranchSelector,
) -> eyre::Result<Vec<String>> {
    let commands = preflight
        .missing
        .iter()
        .filter(|missing| missing.acquirable)
        .map(|missing| {
            SourceAcquisitionRecommendation::target(
                missing.target.clone(),
                branch.clone(),
                SourceProviderFilter::default(),
            )
            .render()
        })
        .collect::<eyre::Result<BTreeSet<_>>>()?
        .into_iter()
        .collect::<Vec<_>>();
    Ok(commands)
}

fn render_refresh_command(branch: &BranchSelector) -> eyre::Result<String> {
    Cli {
        global_args: GlobalArgs::default(),
        command: CliCommand::Symbol(super::SymbolArgs {
            command: super::SymbolCommand::Index(SymbolIndexArgs {
                command: SymbolIndexCommand::Refresh(SymbolIndexRefreshArgs {
                    branch: branch.clone(),
                }),
            }),
        }),
        builtins: figue::FigueBuiltins::default(),
    }
    .to_args_string_with_current_exe()
    .map(|command| command.to_string_lossy().into_owned())
    .map_err(eyre::Report::from)
}

const fn status_for_probe(status: DependencySymbolIndexProbeStatus) -> u8 {
    match status {
        DependencySymbolIndexProbeStatus::Ready => 0,
        DependencySymbolIndexProbeStatus::Missing
        | DependencySymbolIndexProbeStatus::Stale
        | DependencySymbolIndexProbeStatus::Partial => 5,
    }
}
