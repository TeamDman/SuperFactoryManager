//! Oracle-driven work queue over the actual core-only production renderer.
//!
//! Git objects are comparison inputs, never a fallback generation source.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreInputSnapshot;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::CoreSelection;
use super::core_inputs::MAX_CORE_METADATA_BYTES;
use super::core_inputs::ValidatedCoreInputs;
use super::core_inputs::add_to_budget;
use super::core_inputs::checked_core_root;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::collect_core_artifacts_with_snapshot;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::render_core_input;
use super::core_inputs::select_core_inputs;
use super::core_inputs::validate_collected_project;
use super::core_inputs::validate_collection_selection_with_snapshot;
use super::oracle_checkout::CheckoutObservations;
use super::oracle_compare::ComparisonPolicy;
use super::oracle_compare::compare_project_bytes;
use super::oracle_compare::project_comparison_inputs;
use super::oracle_git::GitFile;
use super::oracle_git::GitFileDescriptor;
use super::oracle_git::OracleGitRepository;
use super::oracle_git::ValidatedGitInventory;
use super::oracle_index::CellIdentityRef;
use super::oracle_index::ComparisonCell;
use super::oracle_index::ComparisonIndex;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::release_baseline::RELEASE_4_34_0_TAGS;
use super::sync::CatalogProjectionIdentity;
use super::sync::ProjectedArtifact;
use super::sync::SyncMode;
use super::sync::sync_catalog_projection;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::collections::HashSet;
use std::fmt::Write as _;
use std::fs;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;
use std::sync::Arc;

pub const ORACLES_PATH: &str = "platform/minecraft/projection-oracles.json";
const BINDINGS_SCHEMA: &str = "sfm:projection_oracles@1";
const SCOPE: &str = "sfm:minecraft_project_inputs@1";
const PREFIX: &str = "platform/minecraft";
const MAX_BINDINGS_BYTES: u64 = 1024 * 1024;

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct OracleBindings {
    pub schema: String,
    pub scope: String,
    pub comparison_policy: ComparisonPolicy,
    pub bindings: Vec<OracleBinding>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct OracleBinding {
    pub projection: String,
    pub target_id: String,
    pub environment: String,
    pub reference: String,
    pub commit: String,
    pub tree: String,
    pub source_prefix: String,
    pub expected_files: usize,
    pub inventory_identity: String,
}

#[derive(Debug, Facet)]
pub struct OracleFreezeReport {
    pub schema: String,
    pub operation: String,
    pub binding_path: String,
    pub writes_performed: bool,
    pub previous_identity: Option<String>,
    pub proposed_identity: String,
    pub changed_projections: Vec<String>,
    pub source_checkout_observation: String,
    pub source_checkout_details: Option<CheckoutObservations>,
    pub bindings: OracleBindings,
}

#[derive(Clone, Debug, Default, Eq, Facet, PartialEq)]
pub struct OracleCounts {
    pub expected: usize,
    pub produced: usize,
    pub matched: usize,
    pub exact: usize,
    pub normalized: usize,
    pub missing: usize,
    pub changed: usize,
    pub unexpected: usize,
    pub errors: usize,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct OracleIssue {
    pub path: String,
    pub kind: String,
    pub core_input: Option<String>,
    pub oracle_blob: Option<String>,
    pub oracle_sha256: Option<String>,
    pub rendered_sha256: Option<String>,
    pub diagnostics: Vec<String>,
    pub recommendation: String,
}

#[derive(Debug, Facet)]
pub struct OracleContextStatus {
    pub projection: String,
    pub target_id: String,
    pub environment: String,
    pub reference: String,
    pub pinned_commit: String,
    pub current_commit: Option<String>,
    pub reference_current: bool,
    pub context_identity: String,
    pub enabled_features: Vec<String>,
    pub rendered_identity: Option<String>,
    pub snapshot_matches: bool,
    pub manifested_output: String,
    pub counts: OracleCounts,
    pub normalizations: BTreeMap<String, Vec<String>>,
    pub errors: Vec<String>,
    pub issues: Vec<OracleIssue>,
}

#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Coverage, snapshot fidelity, reference freshness and completion are independent report dimensions"
)]
pub struct OracleStatus {
    pub schema: String,
    pub scope: String,
    pub included_paths: Vec<String>,
    pub excluded_paths: Vec<String>,
    pub binding_identity: String,
    pub catalog_identity: String,
    pub feature_registry_identity: String,
    pub project_inputs_identity: String,
    pub required_contexts: usize,
    pub compared_contexts: usize,
    pub complete_coverage: bool,
    pub snapshot_matches: bool,
    pub references_current: bool,
    pub done: bool,
    pub source_checkout_observation: String,
    pub source_checkout_details: Option<CheckoutObservations>,
    pub unique_unresolved_paths: usize,
    pub counts: OracleCounts,
    pub contexts: Vec<OracleContextStatus>,
}

impl OracleStatus {
    #[must_use]
    pub fn exit_code(&self) -> u8 {
        if self.contexts.is_empty() || self.counts.errors > 0 {
            2
        } else {
            u8::from(
                !self
                    .contexts
                    .iter()
                    .all(|row| row.snapshot_matches && row.reference_current),
            )
        }
    }
}

#[derive(Debug, Facet)]
pub struct OracleNext {
    pub schema: String,
    pub comparison_method: String,
    pub done: bool,
    pub binding_identity: String,
    pub source_checkout_observation: String,
    pub source_checkout_details: Option<CheckoutObservations>,
    pub remaining_cells: usize,
    pub unique_unresolved_paths: usize,
    pub projection: Option<String>,
    pub issue: Option<OracleIssue>,
    pub diagnostics: Vec<String>,
}

impl OracleNext {
    #[must_use]
    pub fn exit_code(&self) -> u8 {
        if self.diagnostics.is_empty() {
            u8::from(!self.done)
        } else {
            2
        }
    }
}

#[derive(Debug, Facet)]
pub struct OracleDiff {
    pub schema: String,
    pub projection: String,
    pub path: String,
    pub pinned_commit: String,
    pub core_input: Option<String>,
    pub oracle_sha256: Option<String>,
    pub rendered_sha256: Option<String>,
    pub exact_equal: bool,
    pub normalized_equal: bool,
    pub transformations: Vec<String>,
    pub diagnostics: Vec<String>,
    pub text_diff: Option<String>,
}

impl OracleDiff {
    #[must_use]
    pub fn exit_code(&self) -> u8 {
        if self.diagnostics.is_empty() {
            u8::from(!self.normalized_equal)
        } else {
            2
        }
    }
}

/// Capture exact refs. Preview is read-only; first apply is create-only.
/// Explicit refresh replaces only this comparison metadata, never source.
///
/// # Errors
/// Rejects unavailable/moved release refs, invalid catalogs, unsafe paths,
/// cancellation and conflicting or failed binding writes.
#[expect(
    clippy::too_many_lines,
    reason = "A bounded checkpoint captures and reports the complete reference matrix before any pin mutation"
)]
pub fn freeze(
    repo_root: &Path,
    invocation_dir: &Path,
    apply: bool,
    refresh: bool,
    cancellation: &CancellationToken,
) -> Result<OracleFreezeReport> {
    let catalog = CoreCatalog::load(repo_root, invocation_dir)?;
    let git = OracleGitRepository::open(&catalog.repo_root)?;
    let mut bindings = Vec::new();
    for (environment, prefix) in [("release", "sfm-4.34.0"), ("dev", "sfm-dev")] {
        for (target, _) in SUPPORTED_TARGETS {
            cancellation.bail_if_cancelled()?;
            let projection = format!("{prefix}/mc-{target}");
            let entry = catalog.catalog.entry(&projection)?;
            ensure!(
                entry.target_id()? == target && entry.environment.as_str() == environment,
                "Required projection {projection} has the wrong explicit context"
            );
            let reference = if environment == "release" {
                format!("refs/tags/4.34.0-{target}")
            } else {
                format!("refs/heads/{target}")
            };
            let revision = git.resolve_reference(&reference)?;
            if environment == "release" {
                let (_, _, expected) = RELEASE_4_34_0_TAGS
                    .iter()
                    .find(|(id, _, _)| *id == target)
                    .ok_or_else(|| eyre::eyre!("Missing reviewed release target {target}"))?;
                ensure!(
                    &revision.commit == expected,
                    "Released tag {reference} moved away from its reviewed commit {expected}"
                );
            }
            let files = git.files_at_commit_filtered(&revision.commit, PREFIX, in_scope)?;
            bindings.push(OracleBinding {
                projection,
                target_id: target.into(),
                environment: environment.into(),
                reference,
                commit: revision.commit,
                tree: revision.tree,
                source_prefix: PREFIX.into(),
                expected_files: files.len(),
                inventory_identity: git_inventory_identity(&files),
            });
        }
    }
    let proposed = OracleBindings {
        schema: BINDINGS_SCHEMA.into(),
        scope: SCOPE.into(),
        comparison_policy: ComparisonPolicy {
            normalize_crlf: true,
            allow_final_newline_difference: true,
            allow_settings_project_identity_override: true,
            ..ComparisonPolicy::default()
        },
        bindings,
    };
    validate_bindings(&proposed)?;
    let mut bytes = facet_json::to_string_pretty(&proposed)?.into_bytes();
    bytes.push(b'\n');
    let parent = checked_directory(&catalog.repo_root.join(PREFIX))?;
    let destination = parent.join("projection-oracles.json");
    let previous = if destination.try_exists()? {
        Some(read_bounded(
            &catalog.repo_root,
            ORACLES_PATH,
            MAX_BINDINGS_BYTES,
        )?)
    } else {
        None
    };
    let old = previous
        .as_ref()
        .map(|bytes| parse_bindings(bytes))
        .transpose()?;
    let changed_projections = proposed
        .bindings
        .iter()
        .filter(|row| {
            old.as_ref()
                .is_some_and(|old| old.comparison_policy != proposed.comparison_policy)
                || old
                    .as_ref()
                    .and_then(|old| {
                        old.bindings
                            .iter()
                            .find(|old| old.projection == row.projection)
                    })
                    .is_none_or(|old| {
                        old.commit != row.commit
                            || old.tree != row.tree
                            || old.inventory_identity != row.inventory_identity
                    })
        })
        .map(|row| row.projection.clone())
        .collect();
    let checkout_observations = super::oracle_checkout::observe(&catalog.repo_root, cancellation)?;
    let writes_performed = apply && previous.as_deref() != Some(bytes.as_slice());
    if writes_performed {
        persist_pins(&parent, &bytes, previous.as_deref(), refresh, cancellation)?;
    }
    Ok(OracleFreezeReport {
        schema: "sfm:projection_oracle_freeze@1".into(),
        operation: if apply { "apply" } else { "preview" }.into(),
        binding_path: ORACLES_PATH.into(),
        writes_performed,
        previous_identity: previous.as_deref().map(sha256),
        proposed_identity: sha256(&bytes),
        changed_projections,
        source_checkout_observation: checkout_observations.summary(),
        source_checkout_details: Some(checkout_observations),
        bindings: proposed,
    })
}

fn persist_pins(
    parent: &Path,
    bytes: &[u8],
    previous: Option<&[u8]>,
    refresh: bool,
    cancellation: &CancellationToken,
) -> Result<()> {
    let destination = parent.join("projection-oracles.json");
    ensure!(
        previous.is_none() || refresh,
        "Oracle pins already exist; preview changes, then explicitly use --apply --refresh"
    );
    cancellation.bail_if_cancelled()?;
    let mut staged = tempfile::NamedTempFile::new_in(parent)?;
    staged.write_all(bytes)?;
    staged.as_file().sync_all()?;
    if let Some(expected) = previous {
        ensure!(
            read_bounded(parent, "projection-oracles.json", MAX_BINDINGS_BYTES)?.as_slice()
                == expected,
            "Oracle bindings changed during refresh; refusing overwrite"
        );
        staged.persist(&destination).map_err(|error| error.error)?;
    } else {
        staged
            .persist_noclobber(&destination)
            .map_err(|error| error.error)?;
    }
    Ok(())
}

/// Compare fresh production output with pinned Git inventories.
///
/// # Errors
/// Rejects missing/invalid bindings, metadata and unsafe inputs.
/// Per-context rendering/object failures remain explicit report errors.
#[tracing::instrument(level = "info", skip_all, name = "oracle_status")]
pub fn status(
    repo_root: &Path,
    invocation_dir: &Path,
    projection: Option<&str>,
    cancellation: &CancellationToken,
) -> Result<OracleStatus> {
    status_with_inspections(
        repo_root,
        invocation_dir,
        projection,
        cancellation,
        true,
        None,
    )
}

fn status_with_inspections(
    repo_root: &Path,
    invocation_dir: &Path,
    projection: Option<&str>,
    cancellation: &CancellationToken,
    inspect_auxiliary_state: bool,
    mut index: Option<&mut IndexSession>,
) -> Result<OracleStatus> {
    ensure!(
        !inspect_auxiliary_state || index.is_none(),
        "full status must bypass comparison index"
    );
    let catalog = CoreCatalog::load(repo_root, invocation_dir)?;
    let bytes = read_bounded(&catalog.repo_root, ORACLES_PATH, MAX_BINDINGS_BYTES)?;
    let bindings = parse_bindings(&bytes)?;
    let metadata_bytes = read_bounded(
        &catalog.repo_root,
        CORE_METADATA_PATH,
        MAX_CORE_METADATA_BYTES,
    )?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &catalog.registered_features,
    )?;
    let selector = metadata.validated_selector(&catalog.registered_features)?;
    let git = OracleGitRepository::open(&catalog.repo_root)?;
    let core = catalog.repo_root.join(CORE_ROOT);
    let inventory = discover_core_source_files(&core)?;
    if let Some(key) = projection {
        ensure!(
            bindings.bindings.iter().any(|row| row.projection == key),
            "No required oracle binding for {key}"
        );
    }
    let mut contexts = Vec::new();
    let mut counts = OracleCounts::default();
    let mut unresolved = BTreeSet::new();
    let mut input_snapshot = CoreInputSnapshot::default();
    for binding in &bindings.bindings {
        if projection.is_some_and(|key| key != binding.projection) {
            continue;
        }
        cancellation.bail_if_cancelled()?;
        let row = compare_context(
            &catalog,
            &selector,
            &inventory,
            &git,
            binding,
            &bindings.comparison_policy,
            inspect_auxiliary_state,
            &mut input_snapshot,
            index.as_deref_mut(),
        )?;
        for issue in &row.issues {
            unresolved.insert(issue.core_input.as_ref().unwrap_or(&issue.path).clone());
        }
        add_counts(&mut counts, &row.counts);
        contexts.push(row);
    }
    let complete_coverage = contexts.len() == bindings.bindings.len();
    let snapshot_matches = contexts.iter().all(|row| row.snapshot_matches);
    let references_current = contexts.iter().all(|row| row.reference_current);
    let checkout_observations = if inspect_auxiliary_state {
        let _span = tracing::info_span!("oracle_checkout_observation").entered();
        Some(super::oracle_checkout::observe(
            &catalog.repo_root,
            cancellation,
        )?)
    } else {
        None
    };
    Ok(OracleStatus {
        schema: "sfm:projection_oracle_status@1".into(),
        scope: SCOPE.into(),
        included_paths: vec![
            "src/** (all source sets and resources)".into(),
            "gradle/**".into(),
            "codestyles/**".into(),
            "project-root tracked files except explicit local/projection metadata exclusions"
                .into(),
        ],
        excluded_paths: scope_exclusions(),
        binding_identity: sha256(&bytes),
        catalog_identity: catalog.catalog_sha256,
        feature_registry_identity: catalog.feature_definitions_sha256,
        project_inputs_identity: sha256(&metadata_bytes),
        required_contexts: bindings.bindings.len(),
        compared_contexts: contexts.len(),
        complete_coverage,
        snapshot_matches,
        references_current,
        done: complete_coverage && snapshot_matches && references_current,
        source_checkout_observation: checkout_observations.as_ref().map_or_else(
            || "not_inspected: next compares pinned sources; use source oracle status for checkout and manifested-output auditing".into(),
            CheckoutObservations::summary,
        ),
        source_checkout_details: checkout_observations,
        unique_unresolved_paths: unresolved.len(),
        counts,
        contexts,
    })
}

#[tracing::instrument(level = "info", skip_all, name = "oracle_compare_context")]
#[expect(
    clippy::too_many_arguments,
    reason = "Comparison inputs remain explicit; the invocation-local byte snapshot is not a persistent freshness cache"
)]
#[expect(
    clippy::too_many_lines,
    reason = "Fresh and indexed paths share the pinned-identity and error-reporting boundary"
)]
fn compare_context(
    catalog: &CoreCatalog,
    selector: &ValidatedCoreInputs<'_>,
    inventory: &BTreeSet<String>,
    git: &OracleGitRepository,
    binding: &OracleBinding,
    policy: &ComparisonPolicy,
    inspect_auxiliary_state: bool,
    input_snapshot: &mut CoreInputSnapshot,
    index: Option<&mut IndexSession>,
) -> Result<OracleContextStatus> {
    let entry = catalog.catalog.entry(&binding.projection)?;
    ensure!(
        entry.target_id()? == binding.target_id
            && entry.environment.as_str() == binding.environment,
        "Oracle context no longer agrees with catalog: {}",
        binding.projection
    );
    let mut row = OracleContextStatus {
        projection: binding.projection.clone(),
        target_id: binding.target_id.clone(),
        environment: binding.environment.clone(),
        reference: binding.reference.clone(),
        pinned_commit: binding.commit.clone(),
        current_commit: None,
        reference_current: false,
        context_identity: catalog.catalog.context_identity(&binding.projection)?,
        enabled_features: entry.features.clone(),
        rendered_identity: None,
        snapshot_matches: false,
        manifested_output: "not_inspected".into(),
        counts: OracleCounts {
            expected: binding.expected_files,
            ..OracleCounts::default()
        },
        normalizations: BTreeMap::new(),
        errors: Vec::new(),
        issues: Vec::new(),
    };
    match git.resolve_reference(&binding.reference) {
        Ok(current) => {
            row.reference_current = current.commit == binding.commit;
            row.current_commit = Some(current.commit);
        }
        Err(error) => row
            .errors
            .push(format!("Cannot observe current reference: {error:#}")),
    }
    let compared = (|| -> Result<()> {
        let revision = git.resolve_commit(&binding.commit)?;
        ensure!(
            revision.tree == binding.tree,
            "Pinned commit/tree identity mismatch"
        );
        if let Some(index) = index {
            let expected = indexed_inventory(git, binding, index)?;
            let context = catalog.context(&binding.projection)?;
            let selection = {
                let _span = tracing::info_span!("oracle_select_inputs").entered();
                selector.select_with_snapshot(&context, inventory, input_snapshot)?
            };
            let read_oracle = |file: &GitFileDescriptor| git.read_described_file(file);
            let result = compare_cells(
                CellRequest {
                    core_root: &catalog.repo_root.join(CORE_ROOT),
                    selection: &selection,
                    context: &context,
                    expected: &expected.descriptor().files,
                    read_oracle: &read_oracle,
                    policy,
                    binding,
                    include_normalizations: false,
                },
                input_snapshot,
                index,
            )?;
            row.counts = result.counts;
            row.issues = result.issues;
            row.normalizations = result.normalizations;
            row.rendered_identity = Some(result.rendered_identity);
            return Ok(());
        }
        let expected = {
            let _span = tracing::info_span!("oracle_git_inventory").entered();
            git.files_at_commit_filtered(&binding.commit, &binding.source_prefix, in_scope)?
        };
        ensure!(
            expected.len() == binding.expected_files
                && git_inventory_identity(&expected) == binding.inventory_identity,
            "Pinned oracle inventory changed or binding scope is invalid"
        );
        let context = catalog.context(&binding.projection)?;
        let selection = {
            let _span = tracing::info_span!("oracle_select_inputs").entered();
            selector.select(&context, inventory)?
        };
        let rendered = {
            let _span = tracing::info_span!("oracle_render_inputs").entered();
            collect_core_artifacts_with_snapshot(
                &catalog.repo_root.join(CORE_ROOT),
                &selection,
                &context,
                input_snapshot,
            )?
        };
        row.rendered_identity = Some(rendered_identity(&rendered));
        let (result, issues, normalizations) = {
            let _span = tracing::info_span!("oracle_compare_inventory").entered();
            compare_inventory(&expected, &rendered, policy, &binding.target_id)
        };
        row.counts = result;
        row.issues = issues;
        row.normalizations = normalizations;
        if inspect_auxiliary_state {
            let _span = tracing::info_span!("oracle_manifested_output").entered();
            row.manifested_output =
                match observe_manifested_output(catalog, &binding.projection, &rendered) {
                    Ok(observation) => observation,
                    Err(error) => format!("invalid_or_contributor_conflict: {error:#}"),
                };
        }
        Ok(())
    })();
    if let Err(error) = compared {
        row.errors.push(format!("{error:#}"));
    }
    row.counts.errors += row.errors.len();
    row.snapshot_matches = row.counts.errors == 0
        && row.counts.missing == 0
        && row.counts.changed == 0
        && row.counts.unexpected == 0;

    Ok(row)
}

struct IndexSession {
    state: ComparisonIndex,
    touched: HashSet<String>,
    touched_inventories: HashSet<String>,
    changed: bool,
    hits: usize,
    misses: usize,
}

#[derive(Clone, Copy)]
struct CellRequest<'a> {
    core_root: &'a Path,
    selection: &'a CoreSelection,
    context: &'a ProjectionContext,
    expected: &'a BTreeMap<String, GitFileDescriptor>,
    read_oracle: &'a dyn Fn(&GitFileDescriptor) -> Result<Vec<u8>>,
    policy: &'a ComparisonPolicy,
    binding: &'a OracleBinding,
    // Next's response omits normalization maps; full parity tests retain them.
    // Every cell's complete data still participates in validation and caching.
    include_normalizations: bool,
}

struct CellResult {
    counts: OracleCounts,
    issues: Vec<OracleIssue>,
    normalizations: BTreeMap<String, Vec<String>>,
    rendered_identity: String,
}

#[tracing::instrument(level = "info", skip_all, name = "oracle_indexed_inventory")]
fn indexed_inventory(
    git: &OracleGitRepository,
    binding: &OracleBinding,
    index: &mut IndexSession,
) -> Result<ValidatedGitInventory> {
    let key = sha256(facet_json::to_string(binding)?.as_bytes());
    let inventory = if let Some(cached) = index.state.inventories.get(&key) {
        cached.clone()
    } else {
        let inventory = ValidatedGitInventory::new(git.describe_at_commit_filtered(
            &binding.commit,
            &binding.source_prefix,
            in_scope,
        )?)?;
        index
            .state
            .inventories
            .insert(key.clone(), inventory.clone());
        index.changed = true;
        inventory
    };
    ensure!(
        inventory.descriptor().files.len() == binding.expected_files
            && inventory.identity() == binding.inventory_identity
            && inventory
                .descriptor()
                .files
                .keys()
                .all(|path| in_scope(path)),
        "Pinned oracle inventory changed or binding scope is invalid"
    );
    git.verify_validated_inventory(&binding.commit, &binding.source_prefix, &inventory)?;
    index.touched_inventories.insert(key);
    Ok(inventory)
}

#[expect(
    clippy::too_many_lines,
    reason = "One bounded path loop retains production selection, rendering, comparison and aggregate validation"
)]
#[tracing::instrument(level = "info", skip_all, name = "oracle_compare_cells")]
fn compare_cells(
    request: CellRequest<'_>,
    snapshot: &mut CoreInputSnapshot,
    index: &mut IndexSession,
) -> Result<CellResult> {
    validate_collection_selection_with_snapshot(request.selection, request.context, snapshot)?;
    let root = checked_core_root(request.core_root)?;
    snapshot.preload(&root, request.selection)?;
    let context_identity = sha256(facet_json::to_string(request.context)?.as_bytes());
    let policy_identity = sha256(facet_json::to_string(request.policy)?.as_bytes());
    let binding_identity = sha256(facet_json::to_string(request.binding)?.as_bytes());
    let paths = request
        .expected
        .keys()
        .chain(request.selection.inputs.keys())
        .collect::<BTreeSet<_>>();
    let mut result = CellResult {
        counts: OracleCounts::default(),
        issues: Vec::new(),
        normalizations: BTreeMap::new(),
        rendered_identity: String::new(),
    };
    let mut project_artifacts = BTreeMap::new();
    // `paths` is sorted exactly like the full renderer's BTreeMap. Hash each
    // descriptor directly instead of copying all paths/digests into a second
    // tree and then constructing an intermediate serialized byte buffer.
    let mut rendered_descriptors = Sha256::new();
    let mut total_bytes = 0;
    for path in paths {
        let selected = request.selection.inputs.get(path);
        let input = selected
            .map(|selected| snapshot.read_with_identity(&root, &selected.input))
            .transpose()?;
        let bytes = input.map(|(bytes, _)| bytes);
        if let Some(bytes) = &bytes {
            total_bytes = add_to_budget(total_bytes, bytes.len() as u64)?;
        }
        let before = request.expected.get(path);
        let key = CellIdentityRef {
            context_identity: &context_identity,
            policy_identity: &policy_identity,
            oracle_binding_identity: &binding_identity,
            output_path: path,
            input_path: selected.map(|selected| selected.input.as_str()),
            template: selected.is_some_and(|selected| selected.template),
            source_sha256: input.map(|(_, identity)| identity),
            oracle_blob: before.map(|before| before.oid.as_str()),
            oracle_mode: before.map(|before| before.mode),
        }
        .key()?;
        let scoped = in_scope(path);
        // Always render build inputs for the existing whole-project validator.
        // Cached Java/resource comparisons alone cannot certify a Gradle project.
        let validate_project = selected.is_some() && !path.starts_with("src/");
        let cached = index.state.cells.get(&key).cloned();
        let mut project_after = if validate_project {
            match (selected, bytes) {
                (Some(selected), Some(bytes)) => Some(render_core_input(
                    path,
                    selected,
                    bytes.to_vec(),
                    request.context,
                )?),
                _ => eyre::bail!("selected build input acquisition is incomplete"),
            }
        } else {
            None
        };
        let cell = if scoped && cached.is_some() {
            index.hits += 1;
            let cached = cached.ok_or_else(|| eyre::eyre!("comparison cell disappeared"))?;
            if let Some(after) = &project_after {
                ensure!(
                    cached.rendered_sha256.as_deref() == Some(sha256(&after.output_bytes).as_str())
                        && cached.rendered_bytes == after.output_bytes.len() as u64,
                    "cached build-input identity disagrees with fresh rendering; explicitly rebuild index"
                );
            }
            cached
        } else {
            index.misses += 1;
            let after = if validate_project {
                project_after.take()
            } else {
                match (selected, bytes) {
                    (Some(selected), Some(bytes)) => Some(render_core_input(
                        path,
                        selected,
                        bytes.to_vec(),
                        request.context,
                    )?),
                    (None, None) => None,
                    _ => eyre::bail!("selected input acquisition is incomplete"),
                }
            };
            let expected = if let Some(before) = before {
                let bytes = (request.read_oracle)(before)?;
                ensure!(
                    bytes.len() as u64 == before.size,
                    "oracle descriptor content size changed"
                );
                BTreeMap::from([(
                    path.clone(),
                    GitFile {
                        oid: before.oid.clone(),
                        mode: before.mode,
                        bytes,
                    },
                )])
            } else {
                BTreeMap::new()
            };
            let rendered = after
                .as_ref()
                .map(|after| BTreeMap::from([(path.clone(), after.clone())]))
                .unwrap_or_default();
            let (counts, issues, normalizations) = compare_inventory(
                &expected,
                &rendered,
                request.policy,
                &request.binding.target_id,
            );
            let cell = Arc::new(ComparisonCell {
                counts,
                issues,
                normalizations,
                rendered_sha256: after.as_ref().map(|after| sha256(&after.output_bytes)),
                rendered_bytes: after
                    .as_ref()
                    .map_or(0, |after| after.output_bytes.len() as u64),
            });
            if scoped {
                if let Some(cached) = &cached {
                    ensure!(
                        cached == &cell,
                        "cached build-input comparison disagrees with fresh rendering; explicitly rebuild index"
                    );
                } else {
                    index.state.cells.insert(key.clone(), Arc::clone(&cell));
                    index.changed = true;
                }
            }
            if validate_project && let Some(after) = after {
                project_after = Some(after);
            }
            cell
        };
        if let Some(after) = project_after {
            project_artifacts.insert(path.clone(), after);
        }
        if scoped {
            index.touched.insert(key);
        }
        if let Some(selected) = selected {
            total_bytes = add_to_budget(total_bytes, cell.rendered_bytes)?;
            let digest = cell.rendered_sha256.as_ref().ok_or_else(|| {
                eyre::eyre!("selected comparison lacks rendered content identity")
            })?;
            for part in [
                path.as_str(),
                "\0",
                CORE_ROOT,
                "/",
                &selected.input,
                "\0",
                digest,
                "\n",
            ] {
                rendered_descriptors.update(part.as_bytes());
            }
        }
        add_counts(&mut result.counts, &cell.counts);
        result.issues.extend(cell.issues.iter().cloned());
        if request.include_normalizations {
            result.normalizations.extend(
                cell.normalizations
                    .iter()
                    .map(|(path, rules)| (path.clone(), rules.clone())),
            );
        }
    }
    validate_collected_project(&project_artifacts, request.selection, request.context)?;
    result.rendered_identity = format!("sha256:{:x}", rendered_descriptors.finalize());
    sort_issues(&mut result.issues);
    Ok(result)
}

/// Find the first unresolved required context/file.
///
/// # Errors
/// Returns catalog, binding and input failures from fresh comparison.
#[tracing::instrument(level = "info", skip_all, name = "oracle_next")]
pub fn next(
    repo_root: &Path,
    invocation_dir: &Path,
    cancellation: &CancellationToken,
) -> Result<OracleNext> {
    next_with_index(repo_root, invocation_dir, cancellation, false)
}

/// Run current comparisons through content-keyed cells. Cold initialization is
/// explicit, never an unnoticed full render in the routine next-file loop.
///
/// # Errors
/// Rejects unavailable/corrupt/wrong-renderer state unless explicitly rebuilding,
/// unsafe cache placement, source failures and failed atomic cache publication.
#[tracing::instrument(level = "info", skip_all, name = "oracle_next_indexed")]
pub fn next_with_index(
    repo_root: &Path,
    invocation_dir: &Path,
    cancellation: &CancellationToken,
    rebuild_index: bool,
) -> Result<OracleNext> {
    cancellation.bail_if_cancelled()?;
    let catalog = CoreCatalog::load(repo_root, invocation_dir)?;
    let git = OracleGitRepository::open(&catalog.repo_root)?;
    // Linked worktrees conventionally store `../..` in commondir. Resolve
    // Git's own path before applying the strict cache-boundary path check.
    let common = checked_directory(&fs::canonicalize(git.common_dir())?)?;
    let repository_identity =
        sha256(format!("{}\0{}", catalog.repo_root.display(), common.display()).as_bytes());
    let renderer_identity = current_renderer_identity()?;
    let home = crate::paths::CacheHome::resolve()?;
    let candidate = home
        .join("oracle-comparison-index")
        .join(repository_identity.trim_start_matches("sha256:"));
    ensure!(
        candidate.is_absolute()
            && !candidate.components().any(|part| matches!(
                part,
                std::path::Component::CurDir | std::path::Component::ParentDir
            )),
        "invalid oracle cache path"
    );
    let mut ancestor = candidate.as_path();
    let mut suffix = Vec::new();
    while !ancestor.try_exists()? {
        suffix.push(
            ancestor
                .file_name()
                .ok_or_else(|| eyre::eyre!("oracle cache lacks an existing ancestor"))?
                .to_owned(),
        );
        ancestor = ancestor
            .parent()
            .ok_or_else(|| eyre::eyre!("oracle cache lacks an existing ancestor"))?;
    }
    let mut directory = checked_directory(ancestor)?;
    for component in suffix.into_iter().rev() {
        directory.push(component);
    }
    ensure!(
        !super::candidate_lock::is_within(&directory, &catalog.repo_root)
            && !super::candidate_lock::is_within(&directory, &common),
        "oracle comparison cache must be outside source repository and Git state"
    );
    if !directory.try_exists()? {
        ensure!(
            rebuild_index,
            "oracle comparison index is absent; explicitly run source oracle next --rebuild-index"
        );
        fs::create_dir_all(&directory)?;
    }
    let state = if rebuild_index {
        ComparisonIndex::new(repository_identity, renderer_identity)?
    } else {
        ComparisonIndex::load(&directory, &repository_identity, &renderer_identity)?
            .ok_or_else(|| eyre::eyre!("oracle comparison index is absent; explicitly run source oracle next --rebuild-index"))?
    };
    let mut index = IndexSession {
        state,
        touched: HashSet::new(),
        touched_inventories: HashSet::new(),
        changed: rebuild_index,
        hits: 0,
        misses: 0,
    };
    let report = status_with_inspections(
        repo_root,
        invocation_dir,
        None,
        cancellation,
        false,
        Some(&mut index),
    )?;
    cancellation.bail_if_cancelled()?;
    if report.counts.errors == 0 {
        let old_len = index.state.cells.len();
        index
            .state
            .cells
            .retain(|key, _| index.touched.contains(key));
        index.changed |= old_len != index.state.cells.len();
        let old_len = index.state.inventories.len();
        index
            .state
            .inventories
            .retain(|key, _| index.touched_inventories.contains(key));
        index.changed |= old_len != index.state.inventories.len();
        if index.changed {
            index.state.save(&directory)?;
        }
    }
    tracing::info!(
        reused_cells = index.hits,
        fresh_cells = index.misses,
        cache_written = index.changed && report.counts.errors == 0,
        "oracle comparison index evaluated against current inputs"
    );
    let mut next = next_from_status(&report);
    next.comparison_method = "current_content_verified_comparison_index".into();
    Ok(next)
}

#[tracing::instrument(level = "info", skip_all, name = "oracle_renderer_identity")]
fn current_renderer_identity() -> Result<String> {
    let executable = std::env::current_exe()?;
    ensure!(
        fs::metadata(&executable)?.len() <= 512 * 1024 * 1024,
        "renderer executable exceeds identity limit"
    );
    let bytes = fs::read(executable)?;
    ensure!(
        bytes.len() <= 512 * 1024 * 1024,
        "renderer executable exceeds identity limit"
    );
    // Identify all executable bytes afresh, using the already-locked faster
    // hash with an explicit algorithm label. Source/oracle SHA-256 is unchanged.
    Ok(renderer_bytes_identity(&bytes))
}

fn renderer_bytes_identity(bytes: &[u8]) -> String {
    format!("blake3:{}", blake3::hash(bytes).to_hex())
}

fn next_from_status(report: &OracleStatus) -> OracleNext {
    let first = report
        .contexts
        .iter()
        .find(|row| !row.errors.is_empty() || !row.reference_current || !row.snapshot_matches);
    let mut diagnostics = Vec::new();
    if report.contexts.is_empty()
        || !report.complete_coverage
        || report.compared_contexts != report.required_contexts
        || report.contexts.len() != report.required_contexts
    {
        diagnostics.push("Next requires complete coverage of all required oracle contexts".into());
    }
    if let Some(row) = first {
        diagnostics.extend(row.errors.clone());
        for issue in row.issues.iter().filter(|issue| issue.kind == "error") {
            diagnostics.extend(
                issue
                    .diagnostics
                    .iter()
                    .map(|message| format!("{}: {message}", issue.path)),
            );
        }
        if !row.reference_current {
            diagnostics.push(format!("{} moved or is unavailable; compare pinned snapshot, review current head, and explicitly refresh when intended", row.reference));
        }
    }
    OracleNext {
        schema: "sfm:projection_oracle_next@1".into(),
        comparison_method: "fresh_production_render".into(),
        done: report.done && diagnostics.is_empty() && first.is_none(),
        binding_identity: report.binding_identity.clone(),
        source_checkout_observation: report.source_checkout_observation.clone(),
        source_checkout_details: report.source_checkout_details.clone(),
        remaining_cells: report.counts.missing
            + report.counts.changed
            + report.counts.unexpected
            + report.counts.errors,
        unique_unresolved_paths: report.unique_unresolved_paths,
        projection: first.map(|row| row.projection.clone()),
        issue: first.and_then(|row| {
            if diagnostics.is_empty() {
                row.issues.first().cloned()
            } else {
                None
            }
        }),
        diagnostics,
    }
}

/// Show a bounded native diff using the declared comparison policy.
///
/// # Errors
/// Rejects unknown/unsafe paths, altered context/pins, unavailable objects,
/// invalid templates and paths absent from both inventories.
pub fn diff(
    repo_root: &Path,
    invocation_dir: &Path,
    projection: &str,
    file: &str,
    cancellation: &CancellationToken,
) -> Result<OracleDiff> {
    validate_projection_key(file)?;
    ensure!(
        in_scope(file),
        "Path {file} is outside the declared oracle scope"
    );
    cancellation.bail_if_cancelled()?;
    let catalog = CoreCatalog::load(repo_root, invocation_dir)?;
    let bindings = parse_bindings(&read_bounded(
        &catalog.repo_root,
        ORACLES_PATH,
        MAX_BINDINGS_BYTES,
    )?)?;
    let binding = bindings
        .bindings
        .iter()
        .find(|row| row.projection == projection)
        .ok_or_else(|| eyre::eyre!("Unknown oracle projection {projection}"))?;
    let git = OracleGitRepository::open(&catalog.repo_root)?;
    let entry = catalog.catalog.entry(projection)?;
    ensure!(
        entry.target_id()? == binding.target_id
            && entry.environment.as_str() == binding.environment,
        "Oracle context no longer agrees with catalog: {projection}"
    );
    ensure!(
        git.resolve_commit(&binding.commit)?.tree == binding.tree,
        "Pinned commit/tree identity mismatch"
    );
    let expected =
        git.files_at_commit_filtered(&binding.commit, &binding.source_prefix, |path| {
            path == file && in_scope(path)
        })?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&read_bounded(
            &catalog.repo_root,
            CORE_METADATA_PATH,
            MAX_CORE_METADATA_BYTES,
        )?)?,
        &catalog.registered_features,
    )?;
    let context = catalog.context(projection)?;
    let inventory = discover_core_source_files(&catalog.repo_root.join(CORE_ROOT))?;
    let selection = select_core_inputs(&metadata, &context, &inventory)?;
    let rendered =
        collect_core_artifacts(&catalog.repo_root.join(CORE_ROOT), &selection, &context)?;
    let before = expected.get(file);
    let after = rendered.get(file);
    ensure!(
        before.is_some() || after.is_some(),
        "Path {file} is absent from both oracle and rendered output"
    );
    let comparison = compare_project_bytes(
        file,
        &binding.target_id,
        before.map_or(&[], |file| file.bytes.as_slice()),
        after.map_or(&[], |file| file.output_bytes.as_slice()),
        &bindings.comparison_policy,
    );
    let both_present = before.is_some() && after.is_some();
    let (view_before, view_after) = project_comparison_inputs(
        file,
        &binding.target_id,
        before.map_or(&[], |file| file.bytes.as_slice()),
        after.map_or(&[], |file| file.output_bytes.as_slice()),
        &bindings.comparison_policy,
    );
    Ok(OracleDiff {
        schema: "sfm:projection_oracle_diff@1".into(),
        projection: projection.into(),
        path: file.into(),
        pinned_commit: binding.commit.clone(),
        core_input: after.map(|file| file.source_path.clone()),
        oracle_sha256: before.map(|file| sha256(&file.bytes)),
        rendered_sha256: after.map(|file| sha256(&file.output_bytes)),
        exact_equal: both_present && comparison.exact_equal,
        normalized_equal: both_present && comparison.normalized_equal,
        transformations: comparison.transformations,
        diagnostics: comparison.diagnostics,
        text_diff: bounded_diff(&view_before, &view_after),
    })
}

pub(super) fn parse_bindings(bytes: &[u8]) -> Result<OracleBindings> {
    let parsed: OracleBindings = facet_json::from_str(std::str::from_utf8(bytes)?)
        .wrap_err("Cannot parse oracle bindings")?;
    validate_bindings(&parsed)?;
    Ok(parsed)
}

fn validate_bindings(bindings: &OracleBindings) -> Result<()> {
    ensure!(
        bindings.schema == BINDINGS_SCHEMA && bindings.scope == SCOPE,
        "Unsupported oracle binding schema/scope"
    );
    ensure!(
        bindings.comparison_policy.schema_version == 1,
        "Unsupported oracle comparison policy"
    );
    ensure!(
        bindings.bindings.len() == 20,
        "Required oracle matrix has exactly twenty contexts"
    );
    for (index, row) in bindings.bindings.iter().enumerate() {
        let target = SUPPORTED_TARGETS[index % 10].0;
        let (environment, prefix, reference) = if index < 10 {
            (
                "release",
                "sfm-4.34.0",
                format!("refs/tags/4.34.0-{target}"),
            )
        } else {
            ("dev", "sfm-dev", format!("refs/heads/{target}"))
        };
        ensure!(
            row.projection == format!("{prefix}/mc-{target}")
                && row.target_id == target
                && row.environment == environment
                && row.reference == reference
                && row.source_prefix == PREFIX,
            "Oracle matrix entry {index} changed required identity, order or scope"
        );
        for oid in [&row.commit, &row.tree] {
            ensure!(
                oid.len() == 40 && oid.bytes().all(|byte| byte.is_ascii_hexdigit()),
                "Invalid oracle object ID"
            );
        }
        ensure!(
            row.inventory_identity.starts_with("sha256:")
                && row.inventory_identity.len() == 71
                && row.expected_files > 0,
            "Invalid/empty oracle inventory identity"
        );
        if index < 10 {
            ensure!(
                row.commit == RELEASE_4_34_0_TAGS[index].2,
                "Published release pin cannot change"
            );
        }
    }
    Ok(())
}

pub(super) fn in_scope(path: &str) -> bool {
    if path.starts_with("src/generated/resources/.cache/") {
        return false;
    }
    if path.starts_with("src/") || path.starts_with("gradle/") || path.starts_with("codestyles/") {
        return true;
    }
    !path.contains('/')
        && !matches!(
            path,
            ".gitignore"
                | ".gitattributes"
                | "projections.json"
                | "projection-oracles.json"
                | "source-projection.json"
                | "source-projection-candidate.json"
        )
        && !path.starts_with('.')
}

fn scope_exclusions() -> Vec<String> {
    vec!["src/generated/resources/.cache/** (datagen cache bookkeeping)".into(), "all top-level directories except src, gradle, codestyles (includes build/cache/world/IDE and historical/projection roots)".into(), "dot-prefixed project-root metadata and .gitignore/.gitattributes".into(), "projections.json, projection-oracles.json, source-projection.json, source-projection-candidate.json (generation/comparison metadata)".into()]
}

pub(super) fn read_bounded(root: &Path, relative: &str, limit: u64) -> Result<Vec<u8>> {
    let path = checked_file(root, relative)?;
    let file = fs::File::open(path)?;
    ensure!(
        file.metadata()?.len() <= limit,
        "Input {relative} exceeds {limit} bytes"
    );
    let mut bytes = Vec::new();
    file.take(limit + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= limit,
        "Input {relative} grew beyond {limit} bytes"
    );
    Ok(bytes)
}

fn git_inventory_identity(files: &BTreeMap<String, GitFile>) -> String {
    let mut bytes = Vec::new();
    for (path, file) in files {
        bytes.extend_from_slice(
            format!(
                "{path}\0{:o}\0{}\0{}\n",
                file.mode,
                file.oid,
                file.bytes.len()
            )
            .as_bytes(),
        );
    }
    sha256(&bytes)
}

fn rendered_identity(files: &BTreeMap<String, ProjectedArtifact>) -> String {
    let mut bytes = Vec::new();
    for (path, file) in files {
        bytes.extend_from_slice(
            format!(
                "{path}\0{}\0{}\n",
                file.source_path,
                sha256(&file.output_bytes)
            )
            .as_bytes(),
        );
    }
    sha256(&bytes)
}

type InventoryComparison = (
    OracleCounts,
    Vec<OracleIssue>,
    BTreeMap<String, Vec<String>>,
);

fn compare_inventory(
    expected: &BTreeMap<String, GitFile>,
    rendered: &BTreeMap<String, ProjectedArtifact>,
    policy: &ComparisonPolicy,
    target_id: &str,
) -> InventoryComparison {
    let mut counts = OracleCounts {
        expected: expected.keys().filter(|path| in_scope(path)).count(),
        produced: rendered.keys().filter(|path| in_scope(path)).count(),
        ..OracleCounts::default()
    };
    let mut issues = Vec::new();
    let mut normalizations = BTreeMap::new();
    let paths = expected
        .keys()
        .chain(rendered.keys())
        .filter(|path| in_scope(path))
        .collect::<BTreeSet<_>>();
    for path in paths {
        let before = expected.get(path);
        let after = rendered.get(path);
        let (kind, diagnostics, recommendation) = match (before, after) {
            (Some(_), None) => {
                counts.missing += 1;
                (
                    "missing",
                    Vec::new(),
                    "Review core membership; seed an absent template from this pinned oracle, then refine feature ownership",
                )
            }
            (None, Some(_)) => {
                counts.unexpected += 1;
                (
                    "unexpected",
                    Vec::new(),
                    "Review membership and feature guards; do not automatically delete authored work",
                )
            }
            (Some(before), Some(after)) => {
                let compared = compare_project_bytes(
                    path,
                    target_id,
                    &before.bytes,
                    &after.output_bytes,
                    policy,
                );
                if compared.normalized_equal {
                    counts.matched += 1;
                    if compared.exact_equal {
                        counts.exact += 1;
                    } else {
                        counts.normalized += 1;
                    }
                    if !compared.transformations.is_empty() {
                        normalizations.insert(path.clone(), compared.transformations);
                    }
                    continue;
                }
                if compared.diagnostics.is_empty() {
                    counts.changed += 1;
                    (
                        "changed",
                        compared.diagnostics,
                        "Inspect source oracle diff; refine the shared Liquid template and feature guards",
                    )
                } else {
                    counts.errors += 1;
                    (
                        "error",
                        compared.diagnostics,
                        "Repair the declared comparison-policy or malformed generated-output boundary",
                    )
                }
            }
            (None, None) => unreachable!("union member"),
        };
        issues.push(OracleIssue {
            path: path.clone(),
            kind: kind.into(),
            core_input: after.map(|file| file.source_path.clone()),
            oracle_blob: before.map(|file| file.oid.clone()),
            oracle_sha256: before.map(|file| sha256(&file.bytes)),
            rendered_sha256: after.map(|file| sha256(&file.output_bytes)),
            diagnostics,
            recommendation: recommendation.into(),
        });
    }
    sort_issues(&mut issues);
    (counts, issues, normalizations)
}

fn sort_issues(issues: &mut [OracleIssue]) {
    issues.sort_by(|a, b| {
        issue_priority(&a.kind)
            .cmp(&issue_priority(&b.kind))
            .then(a.path.cmp(&b.path))
    });
}

fn issue_priority(kind: &str) -> usize {
    match kind {
        "error" => 0,
        "missing" => 1,
        "changed" => 2,
        _ => 3,
    }
}

fn add_counts(total: &mut OracleCounts, row: &OracleCounts) {
    total.expected += row.expected;
    total.produced += row.produced;
    total.matched += row.matched;
    total.exact += row.exact;
    total.normalized += row.normalized;
    total.missing += row.missing;
    total.changed += row.changed;
    total.unexpected += row.unexpected;
    total.errors += row.errors;
}

fn observe_manifested_output(
    catalog: &CoreCatalog,
    key: &str,
    rendered: &BTreeMap<String, ProjectedArtifact>,
) -> Result<String> {
    let directory = catalog.repo_root.join(catalog.catalog.project_dir(key)?);
    if !directory.try_exists()? {
        return Ok("absent".into());
    }
    let directory = super::named_root::catalog_projection_root(
        &catalog.repo_root,
        &catalog.catalog,
        key,
        rendered,
    )?;
    let identity = CatalogProjectionIdentity::from_catalog(&catalog.catalog, key)?;
    observe_owned_output(&directory, &identity, rendered)
}

fn observe_owned_output(
    directory: &Path,
    identity: &CatalogProjectionIdentity,
    rendered: &BTreeMap<String, ProjectedArtifact>,
) -> Result<String> {
    checked_directory(directory)?;
    // Reuse the contributor-edit/ownership boundary, not a parallel byte-only check.
    sync_catalog_projection(directory, identity, rendered, SyncMode::Check)?;
    let mut unowned = Vec::new();
    let mut pending = vec![(directory.to_path_buf(), String::new())];
    let mut inspected = 0usize;
    while let Some((parent, prefix)) = pending.pop() {
        checked_directory(&parent)?;
        for entry in fs::read_dir(parent)? {
            let entry = entry?;
            inspected += 1;
            ensure!(
                inspected <= 100_000,
                "Manifested project inventory exceeds 100000 entries"
            );
            let name = entry
                .file_name()
                .into_string()
                .map_err(|path| eyre::eyre!("Manifested project path is not UTF-8: {path:?}"))?;
            let relative = if prefix.is_empty() {
                name.clone()
            } else {
                format!("{prefix}/{name}")
            };
            let metadata = fs::symlink_metadata(entry.path())?;
            if metadata.is_dir() {
                if matches!(relative.as_str(), "src" | "gradle" | "codestyles")
                    || (!prefix.is_empty() && relative != "src/generated/resources/.cache")
                {
                    validate_projection_key(&relative)?;
                    checked_directory(&entry.path())?;
                    pending.push((entry.path(), relative));
                }
            } else if in_scope(&relative) {
                checked_file(directory, &relative)?;
                if !rendered.contains_key(&relative) {
                    unowned.push(relative);
                }
            }
        }
    }
    unowned.sort();
    if unowned.is_empty() {
        Ok(
            "current: managed bytes, provenance and scoped file membership match fresh output"
                .into(),
        )
    } else {
        Ok(format!(
            "unexpected_unowned_inputs: {}; inspect before sync",
            unowned.join(", ")
        ))
    }
}

fn bounded_diff(before: &[u8], after: &[u8]) -> Option<String> {
    if before.len().max(after.len()) > 256 * 1024 || before.contains(&0) || after.contains(&0) {
        return None;
    }
    let before = std::str::from_utf8(before).ok()?;
    let after = std::str::from_utf8(after).ok()?;
    let input = gix::diff::blob::InternedInput::new(before, after);
    let before_lines = before.lines().collect::<Vec<_>>();
    let after_lines = after.lines().collect::<Vec<_>>();
    let mut output = String::from("--- pinned oracle\n+++ fresh rendered output\n");
    for hunk in
        gix::diff::blob::diff_with_slider_heuristics(gix::diff::blob::Algorithm::Histogram, &input)
            .hunks()
            .take(100)
    {
        let _ = writeln!(
            output,
            "@@ -{},{} +{},{} @@",
            hunk.before.start + 1,
            hunk.before.len(),
            hunk.after.start + 1,
            hunk.after.len()
        );
        for line in &before_lines[hunk.before.start as usize..hunk.before.end as usize] {
            output.push('-');
            output.push_str(line);
            output.push('\n');
        }
        for line in &after_lines[hunk.after.start as usize..hunk.after.end as usize] {
            output.push('+');
            output.push_str(line);
            output.push('\n');
        }
        if output.len() > 128 * 1024 {
            output.push_str("[diff truncated at bounded output budget]\n");
            break;
        }
    }
    Some(output)
}

#[cfg(test)]
mod tests {
    #[test]
    fn renderer_identity_hashes_exact_bytes_not_length_or_timestamps() {
        let original = super::renderer_bytes_identity(b"renderer A");
        assert_eq!(original, super::renderer_bytes_identity(b"renderer A"));
        assert_ne!(original, super::renderer_bytes_identity(b"renderer B"));
        assert_eq!(
            original,
            format!("blake3:{}", blake3::hash(b"renderer A").to_hex())
        );
    }

    use super::super::oracle_compare::compare_bytes;
    use super::super::oracle_compare::comparison_inputs;
    use super::*;

    #[test]
    fn indexed_cells_equal_full_renderer_across_restart_edits_and_earlier_regressions() -> Result<()>
    {
        use super::super::core_inputs::tests::Fixture;
        use super::super::core_inputs::tests::context;
        let fixture = Fixture::new();
        let shared = "src/main/java/Shared.java";
        let source = b"{% if features.alpha %}\nclass Enabled {}\n{% else %}\nclass Shared {}\n{% endif %}\n";
        fixture.write(shared, source);
        fixture.write("src/main/java/A.java", b"class A {}\n");
        let mut context = context("1.19.2", false);
        let original = fixture.prepare(&context)?;
        let expected = original
            .iter()
            .map(|(path, artifact)| {
                let bytes = artifact
                    .output_bytes
                    .strip_prefix(super::super::inputs::GENERATED_BANNER.as_bytes())
                    .and_then(|bytes| bytes.strip_prefix(b"\n"))
                    .unwrap_or(&artifact.output_bytes)
                    .to_vec();
                (
                    path.clone(),
                    GitFile {
                        oid: sha256(&bytes)[7..47].to_owned(),
                        mode: 0o100644,
                        bytes,
                    },
                )
            })
            .collect::<BTreeMap<_, _>>();
        let mut binding = bindings_fixture().bindings[10].clone();
        let descriptors = expected
            .iter()
            .map(|(path, file)| {
                (
                    path.clone(),
                    GitFileDescriptor {
                        oid: file.oid.clone(),
                        mode: file.mode,
                        size: file.bytes.len() as u64,
                    },
                )
            })
            .collect();
        let oracle_reads = std::cell::Cell::new(0_usize);
        let read_oracle = |file: &GitFileDescriptor| -> Result<Vec<u8>> {
            oracle_reads.set(oracle_reads.get() + 1);
            Ok(expected
                .values()
                .find(|expected| expected.oid == file.oid)
                .ok_or_else(|| eyre::eyre!("fixture oracle object missing"))?
                .bytes
                .clone())
        };
        binding.expected_files = expected.len();
        binding.inventory_identity = git_inventory_identity(&expected);
        let mut policy = ComparisonPolicy::default();
        let repository = sha256(b"fixture repository");
        let renderer = sha256(b"fixture renderer");
        let cache = tempfile::tempdir()?;
        let mut index = IndexSession {
            state: ComparisonIndex::new(repository.clone(), renderer.clone())?,
            touched: HashSet::new(),
            touched_inventories: HashSet::new(),
            changed: true,
            hits: 0,
            misses: 0,
        };
        for round in 0..9 {
            let inventory = discover_core_source_files(&fixture.core)?;
            let selection = select_core_inputs(&fixture.metadata, &context, &inventory)?;
            let full = collect_core_artifacts(&fixture.core, &selection, &context)?;
            let (counts, issues, normalizations) =
                compare_inventory(&expected, &full, &policy, &binding.target_id);
            let request = CellRequest {
                core_root: &fixture.core,
                selection: &selection,
                context: &context,
                expected: &descriptors,
                read_oracle: &read_oracle,
                policy: &policy,
                binding: &binding,
                include_normalizations: true,
            };
            let summarized = compare_cells(
                CellRequest {
                    include_normalizations: false,
                    ..request
                },
                &mut CoreInputSnapshot::default(),
                &mut index,
            )?;
            let reads_before_cached_call = oracle_reads.get();
            let result = compare_cells(request, &mut CoreInputSnapshot::default(), &mut index)?;
            assert_eq!(oracle_reads.get(), reads_before_cached_call);
            assert_eq!(summarized.counts, result.counts);
            assert_eq!(summarized.issues, result.issues);
            assert_eq!(summarized.rendered_identity, result.rendered_identity);
            assert!(summarized.normalizations.is_empty());
            assert_eq!(
                facet_json::to_string(&result.counts)?,
                facet_json::to_string(&counts)?,
                "counts round {round}"
            );
            assert_eq!(
                facet_json::to_string(&result.issues)?,
                facet_json::to_string(&issues)?,
                "issues round {round}"
            );
            assert_eq!(result.normalizations, normalizations);
            assert_eq!(result.rendered_identity, rendered_identity(&full));
            if round == 1 {
                assert!(index.hits > 0);
            }
            if round == 3 {
                assert_eq!(result.issues[0].path, "src/main/java/A.java");
            }
            index.state.save(cache.path())?;
            index = IndexSession {
                state: ComparisonIndex::load(cache.path(), &repository, &renderer)?.unwrap(),
                touched: HashSet::new(),
                touched_inventories: HashSet::new(),
                changed: false,
                hits: 0,
                misses: 0,
            };
            match round {
                0 => {} // unchanged process-restart comparison
                1 => {
                    let timestamp = fs::metadata(fixture.core.join(shared))?.modified()?;
                    fixture.write(shared, b"class Changed {}\n");
                    fs::File::options()
                        .write(true)
                        .open(fixture.core.join(shared))?
                        .set_times(fs::FileTimes::new().set_modified(timestamp))?;
                    assert_eq!(
                        fs::metadata(fixture.core.join(shared))?.modified()?,
                        timestamp
                    );
                }
                2 => fixture.write("src/main/java/A.java", b"class EarlierRegression {}\n"),
                3 => fixture.write("src/main/java/Added.java", b"class Added {}\n"),
                4 => {
                    fs::remove_file(fixture.core.join(shared))?;
                }
                5 => {
                    fixture.write(shared, source);
                    fixture.write("src/main/java/A.java", b"class A {}\n");
                    fs::remove_file(fixture.core.join("src/main/java/Added.java"))?;
                }
                6 => context
                    .features
                    .insert("alpha".into(), true)
                    .map_or((), |_| ()),
                7 => {
                    policy.normalize_crlf = true;
                    binding.tree = "c".repeat(40);
                }
                _ => {}
            }
            if round == 1 {
                let build = fixture
                    .core
                    .join(selection.inputs["build.gradle"].input.as_str());
                let timestamp = fs::metadata(&build)?.modified()?;
                let mut content = fs::read(&build)?;
                content.extend_from_slice(b"\n// realistic build input edit\n");
                fs::write(&build, content)?;
                fs::File::options()
                    .write(true)
                    .open(&build)?
                    .set_times(fs::FileTimes::new().set_modified(timestamp))?;
            }
            if round == 2 {
                fixture.write(
                    selection.inputs["build.gradle"].input.as_str(),
                    &original["build.gradle"].source_bytes,
                );
            }
        }
        // A checksum-valid cache record still cannot substitute a different
        // build output for the freshly rendered project validator inputs.
        for cell in index.state.cells.values_mut() {
            Arc::make_mut(cell).rendered_sha256 = Some(sha256(b"incorrect build identity"));
        }
        let selection = select_core_inputs(
            &fixture.metadata,
            &context,
            &discover_core_source_files(&fixture.core)?,
        )?;
        let corrupt = compare_cells(
            CellRequest {
                core_root: &fixture.core,
                selection: &selection,
                context: &context,
                expected: &descriptors,
                read_oracle: &read_oracle,
                policy: &policy,
                binding: &binding,
                include_normalizations: false,
            },
            &mut CoreInputSnapshot::default(),
            &mut index,
        );
        assert!(format!("{:#}", corrupt.err().unwrap()).contains("cached build-input identity"));
        fixture.write(
            shared,
            b"{% if features.unregistered %}\nclass Bad {}\n{% endif %}\n",
        );
        let selection = select_core_inputs(
            &fixture.metadata,
            &context,
            &discover_core_source_files(&fixture.core)?,
        )?;
        assert!(collect_core_artifacts(&fixture.core, &selection, &context).is_err());
        assert!(
            compare_cells(
                CellRequest {
                    core_root: &fixture.core,
                    selection: &selection,
                    context: &context,
                    expected: &descriptors,
                    read_oracle: &read_oracle,
                    policy: &policy,
                    binding: &binding,
                    include_normalizations: true,
                },
                &mut CoreInputSnapshot::default(),
                &mut index
            )
            .is_err()
        );
        Ok(())
    }

    fn bindings_fixture() -> OracleBindings {
        OracleBindings {
            schema: BINDINGS_SCHEMA.into(),
            scope: SCOPE.into(),
            comparison_policy: ComparisonPolicy::default(),
            bindings: (0..20)
                .map(|index| {
                    let target = SUPPORTED_TARGETS[index % 10].0;
                    let (environment, prefix, reference, commit) = if index < 10 {
                        (
                            "release",
                            "sfm-4.34.0",
                            format!("refs/tags/4.34.0-{target}"),
                            RELEASE_4_34_0_TAGS[index].2.to_owned(),
                        )
                    } else {
                        (
                            "dev",
                            "sfm-dev",
                            format!("refs/heads/{target}"),
                            "a".repeat(40),
                        )
                    };
                    OracleBinding {
                        projection: format!("{prefix}/mc-{target}"),
                        target_id: target.into(),
                        environment: environment.into(),
                        reference,
                        commit,
                        tree: "b".repeat(40),
                        source_prefix: PREFIX.into(),
                        expected_files: 1,
                        inventory_identity: sha256(b"fixture"),
                    }
                })
                .collect(),
        }
    }

    #[test]
    fn required_matrix_rejects_missing_extra_reordered_and_altered_bindings() {
        let original = bindings_fixture();
        validate_bindings(&original).unwrap();
        for mutation in 0..15 {
            let mut changed = original.clone();
            match mutation {
                0 => {
                    changed.bindings.pop();
                }
                1 => changed.bindings.push(changed.bindings[0].clone()),
                2 => changed.bindings.swap(0, 1),
                3 => changed.bindings[10] = changed.bindings[0].clone(),
                4 => changed.bindings[0].commit = "c".repeat(40),
                5 => changed.bindings[10].reference = "HEAD".into(),
                6 => changed.bindings[10].source_prefix = "platform".into(),
                7 => changed.bindings[10].target_id = "1.19.4".into(),
                8 => changed.bindings[10].environment = "release".into(),
                9 => changed.bindings[10].projection = "custom/mc-1.19.2".into(),
                10 => changed.bindings[10].expected_files = 0,
                11 => changed.bindings[10].tree = "b".repeat(39),
                12 => changed.bindings[10].commit = "z".repeat(40),
                13 => changed.scope.push_str("-unknown"),
                14 => changed.comparison_policy.schema_version = 2,
                _ => unreachable!(),
            }
            assert!(
                validate_bindings(&changed).is_err(),
                "accepted mutation {mutation}"
            );
        }
    }

    fn status_fixture() -> OracleStatus {
        let contexts = bindings_fixture()
            .bindings
            .into_iter()
            .map(|row| OracleContextStatus {
                projection: row.projection,
                target_id: row.target_id,
                environment: row.environment,
                reference: row.reference,
                pinned_commit: row.commit.clone(),
                current_commit: Some(row.commit),
                reference_current: true,
                context_identity: sha256(b"context"),
                enabled_features: Vec::new(),
                rendered_identity: Some(sha256(b"rendered")),
                snapshot_matches: true,
                manifested_output: "absent".into(),
                counts: OracleCounts {
                    expected: 1,
                    produced: 1,
                    matched: 1,
                    exact: 1,
                    ..OracleCounts::default()
                },
                normalizations: BTreeMap::new(),
                errors: Vec::new(),
                issues: Vec::new(),
            })
            .collect();
        OracleStatus {
            schema: "sfm:projection_oracle_status@1".into(),
            scope: SCOPE.into(),
            included_paths: Vec::new(),
            excluded_paths: Vec::new(),
            binding_identity: sha256(b"pins"),
            catalog_identity: sha256(b"catalog"),
            feature_registry_identity: sha256(b"features"),
            project_inputs_identity: sha256(b"inputs"),
            required_contexts: 20,
            compared_contexts: 20,
            complete_coverage: true,
            snapshot_matches: true,
            references_current: true,
            done: true,
            source_checkout_observation: "not_inspected".into(),
            source_checkout_details: None,
            unique_unresolved_paths: 0,
            counts: OracleCounts {
                expected: 20,
                produced: 20,
                matched: 20,
                exact: 20,
                ..OracleCounts::default()
            },
            contexts,
        }
    }

    #[test]
    fn next_preserves_checkout_uncertainty_separately_from_committed_completion() {
        let mut report = status_fixture();
        let unobserved = next_from_status(&report);
        assert_eq!(unobserved.source_checkout_observation, "not_inspected");
        assert!(unobserved.source_checkout_details.is_none());
        report.source_checkout_observation = "incomplete_observation: fixture unavailable".into();
        report.source_checkout_details = Some(CheckoutObservations {
            schema: "sfm:oracle_checkout_observations@1".into(),
            method: "fixture_read_only_observation".into(),
            complete: false,
            ignored_policy: "fixture".into(),
            errors: vec!["fixture observation unavailable".into()],
            targets: Vec::new(),
        });
        let next = next_from_status(&report);
        assert_eq!(
            next.source_checkout_observation,
            report.source_checkout_observation
        );
        let details = next.source_checkout_details.as_ref().unwrap();
        assert!(!details.complete);
        assert_eq!(details.errors, ["fixture observation unavailable"]);
        assert!(next.done && next.diagnostics.is_empty());
        assert_eq!(next.exit_code(), 0); // This remains a committed-comparison exit code.
    }

    #[test]
    fn next_uses_required_context_priority_and_disappears_after_correction() {
        let mut report = status_fixture();
        let before = BTreeMap::from([("src/missing.txt".into(), git(b""))]);
        let (counts, issues, _) = compare_inventory(
            &before,
            &BTreeMap::new(),
            &ComparisonPolicy::default(),
            "1.19.2",
        );
        for index in [10, 1, 0] {
            report.contexts[index].counts = counts.clone();
            report.contexts[index].issues = issues.clone();
            report.contexts[index].snapshot_matches = false;
        }
        report.done = false;
        report.counts.missing = 3;
        report.unique_unresolved_paths = 1;
        for index in [0, 1, 10] {
            let next = next_from_status(&report);
            assert_eq!(
                next.projection.as_deref(),
                Some(report.contexts[index].projection.as_str())
            );
            assert_eq!(next.issue.as_ref().unwrap().kind, "missing");
            assert_eq!(
                next.issue.as_ref().unwrap().oracle_sha256,
                Some(sha256(b""))
            );
            assert_eq!(next.remaining_cells, report.counts.missing);
            assert_eq!(next.exit_code(), 1);
            report.contexts[index].snapshot_matches = true;
            report.contexts[index].issues.clear();
            report.counts.missing -= 1;
        }
        report.done = true;
        report.snapshot_matches = true;
        report.unique_unresolved_paths = 0;
        let next = next_from_status(&report);
        assert!(next.done && next.projection.is_none() && next.issue.is_none());
        assert_eq!((next.remaining_cells, next.exit_code()), (0, 0));
    }

    #[test]
    fn next_rejects_issue_errors_moved_refs_and_context_failures() {
        for case in 0..3 {
            let mut report = status_fixture();
            report.done = false;
            let row = &mut report.contexts[0];
            match case {
                0 => {
                    let malformed = format!(
                        "{} changed\nclass A {{}}\n",
                        super::super::inputs::GENERATED_BANNER
                    );
                    let (counts, issues, _) = compare_inventory(
                        &BTreeMap::from([("src/A.java".into(), git(b"class A {}\n"))]),
                        &BTreeMap::from([("src/A.java".into(), artifact(malformed.as_bytes()))]),
                        &ComparisonPolicy::default(),
                        "1.19.2",
                    );
                    assert_eq!(counts.errors, 1);
                    row.counts = counts;
                    row.issues = issues;
                    row.snapshot_matches = false;
                    report.counts.errors = 1;
                }
                1 => {
                    row.reference_current = false;
                    row.current_commit = Some("c".repeat(40));
                }
                2 => {
                    row.errors.push("unreadable oracle object".into());
                    row.snapshot_matches = false;
                    report.counts.errors = 1;
                }
                _ => unreachable!(),
            }
            let next = next_from_status(&report);
            assert!(!next.done && !next.diagnostics.is_empty());
            assert_eq!(next.exit_code(), 2);
            assert!(next.issue.is_none());
            assert_ne!(report.exit_code(), 0);
        }
    }

    #[test]
    fn filtered_matches_are_explicitly_partial_and_cannot_finish_the_queue() {
        let mut report = status_fixture();
        report.contexts.truncate(1);
        report.compared_contexts = 1;
        report.complete_coverage = false;
        report.done = false;
        report.counts = report.contexts[0].counts.clone();
        assert_eq!(report.exit_code(), 0); // One requested context can pass.
        let next = next_from_status(&report);
        assert!(!next.done);
        assert_eq!(next.exit_code(), 2);
        report.done = true; // A stale boolean cannot override missing coverage.
        assert!(!next_from_status(&report).done);
        report.contexts.clear();
        report.complete_coverage = true;
        assert_eq!(report.exit_code(), 2);
        let next = next_from_status(&report);
        assert!(!next.done);
        assert_eq!(next.exit_code(), 2);
    }

    fn git(bytes: &[u8]) -> GitFile {
        GitFile {
            oid: "a".repeat(40),
            mode: 0o100644,
            bytes: bytes.to_vec(),
        }
    }
    fn artifact(bytes: &[u8]) -> ProjectedArtifact {
        ProjectedArtifact {
            source_path: format!("{CORE_ROOT}/src/Example.java"),
            source_bytes: bytes.to_vec(),
            output_bytes: bytes.to_vec(),
            overlay: None,
        }
    }

    #[test]
    fn inventory_accounts_for_membership_bytes_errors_and_absent_empty() {
        let before = BTreeMap::from([
            ("src/equal.txt".into(), git(b"same")),
            ("src/missing.txt".into(), git(b"")),
            ("src/change.txt".into(), git(b"before")),
        ]);
        let after = BTreeMap::from([
            ("src/equal.txt".into(), artifact(b"same")),
            ("src/extra.txt".into(), artifact(b"")),
            ("src/change.txt".into(), artifact(b"after")),
        ]);
        let (counts, issues, _) =
            compare_inventory(&before, &after, &ComparisonPolicy::default(), "1.19.2");
        assert_eq!(
            (
                counts.expected,
                counts.produced,
                counts.matched,
                counts.missing,
                counts.changed,
                counts.unexpected
            ),
            (3, 3, 1, 1, 1, 1)
        );
        assert_eq!(
            issues
                .iter()
                .map(|row| row.kind.as_str())
                .collect::<Vec<_>>(),
            ["missing", "changed", "unexpected"]
        );
        assert_eq!(issues[0].oracle_sha256, Some(sha256(b"")));
        assert_eq!(issues[0].rendered_sha256, None);
    }

    #[test]
    fn declared_exclusions_apply_to_both_inventories_without_hiding_source_extras() {
        let before = BTreeMap::from([
            ("src/main/resources/fixture.json".into(), git(b"{}\n")),
            (
                "src/generated/resources/.cache/old-state".into(),
                git(b"old cache"),
            ),
        ]);
        let after = BTreeMap::from([
            ("src/main/resources/fixture.json".into(), artifact(b"{}\n")),
            (
                "src/generated/resources/.cache/new-state".into(),
                artifact(b"new cache"),
            ),
            ("src/main/resources/extra.json".into(), artifact(b"[]\n")),
        ]);
        let (counts, issues, _) =
            compare_inventory(&before, &after, &ComparisonPolicy::default(), "26.1.2");
        assert_eq!(
            (counts.expected, counts.produced, counts.matched),
            (1, 2, 1)
        );
        assert_eq!(
            (
                counts.missing,
                counts.changed,
                counts.unexpected,
                counts.errors
            ),
            (0, 0, 1, 0)
        );
        assert_eq!(issues.len(), 1);
        assert_eq!(issues[0].path, "src/main/resources/extra.json");
        assert_eq!(issues[0].kind, "unexpected");
    }

    #[test]
    fn normalization_is_reported_not_used_to_hide_resource_changes() {
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            ..ComparisonPolicy::default()
        };
        let before = BTreeMap::from([
            ("src/a.txt".into(), git(b"a\r\n")),
            ("src/a.png".into(), git(b"a\r\n")),
        ]);
        let after = BTreeMap::from([
            ("src/a.txt".into(), artifact(b"a\n")),
            ("src/a.png".into(), artifact(b"a\n")),
        ]);
        let (counts, issues, normalizations) =
            compare_inventory(&before, &after, &policy, "1.19.2");
        assert_eq!(
            (counts.matched, counts.normalized, counts.changed),
            (1, 1, 1)
        );
        assert_eq!(issues[0].path, "src/a.png");
        assert_eq!(normalizations.len(), 1);
    }

    #[test]
    fn declared_scope_contains_all_source_sets_assets_and_project_inputs() {
        for path in [
            "src/gametest/java/Test.java",
            "src/test/resources/input.json",
            "src/generated/resources/blob.nbt",
            "gradle/wrapper/gradle-wrapper.jar",
            "sfm-toolchain.lock.json",
            "Run-Test.ps1",
            "codestyles/style.xml",
        ] {
            assert!(in_scope(path), "{path}");
        }
        for path in [
            "build/output.jar",
            "run/saves/world/level.dat",
            "core-liquid-template/src/Test.java",
            "development-baselines/x.java",
            "projections/old/src/Test.java",
            "projections.json",
            ".idea/config.xml",
            "src/generated/resources/.cache/generator-state",
        ] {
            assert!(!in_scope(path), "{path}");
        }
    }

    #[test]
    fn native_diff_is_bounded_and_binary_is_explicitly_unavailable() {
        let diff = bounded_diff(b"same\nbefore\n", b"same\nafter\n").unwrap();
        assert!(diff.contains("-before\n+after\n"));
        assert!(bounded_diff(b"a\0b", b"c").is_none());
        assert!(bounded_diff(&vec![b'a'; 262145], b"b").is_none());
    }

    #[test]
    fn policy_diff_is_readable_without_hiding_real_source_changes() {
        let before = b"class A {\n    int value = 1;\n}\n";
        let after = format!(
            "{}\r\nclass A {{\r\n    int value = 2;\r\n}}\r\n",
            super::super::inputs::GENERATED_BANNER
        );
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            ..ComparisonPolicy::default()
        };
        let (left, right) = comparison_inputs("src/A.java", before, after.as_bytes(), &policy);
        let diff = bounded_diff(&left, &right).unwrap();
        assert!(diff.contains("-    int value = 1;\n+    int value = 2;\n"));
        assert!(!diff.contains("THIS IS A GENERATED FILE"));
        assert!(!compare_bytes("src/A.java", before, after.as_bytes(), &policy).normalized_equal);
    }

    #[test]
    fn manifested_observation_checks_ownership_and_unowned_source_membership_without_writes() {
        use super::super::projection_catalog::ProjectionEnvironment;
        let temp = tempfile::tempdir().unwrap();
        let directory = temp.path().join("projection");
        let identity = CatalogProjectionIdentity {
            target_id: "1.19.2".into(),
            minecraft_version: "1.19.2".into(),
            projection_key: "sfm-4.34.0/mc-1.19.2".into(),
            environment: ProjectionEnvironment::Release,
            context_identity: format!("blake3:{}", blake3::hash(b"context").to_hex()),
        };
        let rendered =
            BTreeMap::from([("src/Example.java".into(), artifact(b"class Example {}\n"))]);
        sync_catalog_projection(&directory, &identity, &rendered, SyncMode::Apply).unwrap();
        assert!(
            observe_owned_output(&directory, &identity, &rendered)
                .unwrap()
                .starts_with("current:")
        );
        let manifest_before = fs::read(directory.join(super::super::sync::MANIFEST_FILE)).unwrap();
        fs::write(directory.join("src/Extra.java"), b"class Extra {}\n").unwrap();
        assert!(
            observe_owned_output(&directory, &identity, &rendered)
                .unwrap()
                .contains("src/Extra.java")
        );
        fs::create_dir(directory.join("build")).unwrap();
        fs::write(directory.join("build/temporary.bin"), b"not a source").unwrap();
        assert!(
            !observe_owned_output(&directory, &identity, &rendered)
                .unwrap()
                .contains("temporary.bin")
        );
        fs::write(directory.join("src/Example.java"), b"contributor edit\n").unwrap();
        assert!(
            observe_owned_output(&directory, &identity, &rendered)
                .unwrap_err()
                .to_string()
                .contains("was edited")
        );
        assert_eq!(
            fs::read(directory.join(super::super::sync::MANIFEST_FILE)).unwrap(),
            manifest_before
        );
        assert_eq!(
            fs::read(directory.join("src/Example.java")).unwrap(),
            b"contributor edit\n"
        );
    }
}
