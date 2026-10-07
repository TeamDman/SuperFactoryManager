//! Bounded, staged authoring from pinned oracle witnesses.
//!
//! The first run produces ownership evidence only. Explicit reviewed decisions
//! may produce a candidate after the actual core selector and collector validate
//! all witnesses in an isolated tree. Canonical inputs are never written.

use super::candidate_lock::checked_directory;
use super::candidate_lock::is_within;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::core_inputs::MAX_CORE_FILE_BYTES;
use super::core_inputs::MAX_CORE_METADATA_BYTES;
use super::core_inputs::MAX_CORE_PROJECTION_BYTES;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::oracle::ORACLES_PATH;
use super::oracle::OracleBindings;
use super::oracle::in_scope;
use super::oracle::parse_bindings;
use super::oracle::read_bounded;
use super::oracle_compare::ComparisonPolicy;
use super::oracle_compare::compare_project_bytes;
use super::oracle_git::OracleGitRepository;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::variant_consolidation::RegionReviewReport;
use super::variant_consolidation::ReviewedRegionOwnership;
use super::variant_consolidation::SelectionRule;
use super::variant_consolidation::SourceVariant;
use super::variant_consolidation::consolidate_reviewed;
use super::variant_consolidation::preview_regions;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

const REVIEW_SCHEMA: &str = "sfm:oracle_draft_ownership_review@1";
const MAX_WITNESS_BYTES: u64 = 1024 * 1024;
const MAX_TOTAL_WITNESS_BYTES: usize = 20 * 1024 * 1024;
const MAX_REVIEW_BYTES: u64 = 64 * 1024 * 1024;
const MAX_BUNDLE_BYTES: usize = 96 * 1024 * 1024;
const MAX_MEMBERSHIP_VARIANTS: usize = 256;

#[derive(Clone, Debug)]
pub struct OracleDraftRequest<'a> {
    pub file: &'a str,
    pub output_dir: &'a Path,
    pub review: Option<&'a Path>,
    /// Empty means all witnesses; an explicit subset never changes acceptance scope.
    pub projections: &'a [String],
}

/// Copy this document, supply one explicit decision per region, then use --review.
/// Catalog, registry, membership metadata or witness changes invalidate it.
#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct OracleDraftReview {
    pub schema: String,
    pub file: String,
    pub projections: Vec<String>,
    pub binding_identity: String,
    pub catalog_identity: String,
    pub feature_registry_identity: String,
    pub metadata_identity: String,
    pub source_inventory_identity: String,
    pub regions: Option<RegionReviewReport>,
    pub decisions: BTreeMap<String, ReviewedRegionOwnership>,
}

#[derive(Debug, Facet)]
pub struct OracleDraftWitness {
    pub projection: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub reference: String,
    pub commit: String,
    pub tree: String,
    pub features: BTreeMap<String, bool>,
    /// None denotes an absent file, including when another witness is empty.
    pub blob: Option<String>,
    pub mode: Option<u32>,
    pub staged_file: Option<String>,
    pub factoring_representative: Option<String>,
    pub factoring_transformations: Vec<String>,
}

#[derive(Debug, Facet)]
pub struct OracleDraftReport {
    pub schema: String,
    pub operation: String,
    pub file: String,
    pub status: String,
    pub writes_performed: bool,
    pub candidate_validated: bool,
    pub selected_projections: Vec<String>,
    pub validated_projections: Vec<String>,
    pub supporting_input_identity: Option<String>,
    pub proposed_input: String,
    pub proposed_variants: Vec<InputVariant>,
    pub witnesses: Vec<OracleDraftWitness>,
    pub ownership_review: OracleDraftReview,
    pub diagnostics: Vec<String>,
    pub next_action: String,
}

struct PreparedWitnesses {
    variants: Vec<SourceVariant>,
    raw_sources: Vec<Option<Vec<u8>>>,
    contexts: Vec<ProjectionContext>,
    evidence: Vec<OracleDraftWitness>,
    files: BTreeMap<String, Vec<u8>>,
}

/// Stage review evidence, or a reviewed candidate, in a new explicit directory.
/// Merely staging a candidate is not promotion or completed source migration.
///
/// # Errors
/// Rejects unsafe/existing destinations, stale refs/reviews, oversized/non-text
/// witnesses, invalid contexts and candidates rejected by the production path.
#[expect(
    clippy::too_many_lines,
    reason = "Keep bounded preflight and real production validation before staged writes"
)]
pub fn draft(
    repo_root: &Path,
    invocation_dir: &Path,
    request: &OracleDraftRequest<'_>,
    cancellation: &CancellationToken,
) -> Result<OracleDraftReport> {
    cancellation.bail_if_cancelled()?;
    validate_projection_key(request.file)?;
    ensure!(
        in_scope(request.file),
        "Draft file is outside the declared oracle scope"
    );
    let catalog = CoreCatalog::load(repo_root, invocation_dir)?;
    let git = OracleGitRepository::open(&catalog.repo_root)?;
    let destination =
        checked_destination(request.output_dir, invocation_dir, &catalog.repo_root, &git)?;
    let binding_bytes = read_bounded(&catalog.repo_root, ORACLES_PATH, 1024 * 1024)?;
    let bindings = parse_bindings(&binding_bytes)?;
    let metadata_bytes = read_bounded(
        &catalog.repo_root,
        CORE_METADATA_PATH,
        MAX_CORE_METADATA_BYTES,
    )?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &catalog.registered_features,
    )?;
    let core = checked_directory(&catalog.repo_root.join(CORE_ROOT))?;
    let inventory = discover_core_source_files(&core)?;
    let mut prepared = read_witnesses(&catalog, &bindings, &git, request, cancellation)?;
    ensure!(
        prepared
            .variants
            .iter()
            .any(|variant| variant.source.is_some()),
        "All pinned contexts omit {}; review output membership instead of drafting an empty input",
        request.file
    );
    let mut diagnostics = selector_conflicts(&prepared.variants);
    let modes = prepared
        .evidence
        .iter()
        .filter_map(|row| row.mode)
        .collect::<BTreeSet<_>>();
    if modes.len() > 1 {
        diagnostics.push("Witness executable modes differ; core membership metadata cannot express per-context mode changes. Review modes before producing a candidate.".into());
    }
    let regions = if diagnostics.is_empty() {
        match preview_regions(&prepared.variants) {
            Ok(regions) => Some(regions),
            Err(error) => {
                diagnostics.push(format!(
                    "Witnesses need manual region refinement before factoring: {error:#}"
                ));
                None
            }
        }
    } else {
        None
    };
    let mut review = OracleDraftReview {
        schema: REVIEW_SCHEMA.into(),
        file: request.file.into(),
        projections: prepared
            .variants
            .iter()
            .map(|variant| variant.id.clone())
            .collect(),
        binding_identity: sha256(&binding_bytes),
        catalog_identity: catalog.catalog_sha256.clone(),
        feature_registry_identity: catalog.feature_definitions_sha256.clone(),
        metadata_identity: sha256(&metadata_bytes),
        source_inventory_identity: inventory_identity(&inventory),
        regions,
        decisions: BTreeMap::new(),
    };
    // An explicit candidate input avoids rewriting a currently shared input alias.
    let input = format!("draft/{}", request.file);
    let mut proposed_variants = Vec::new();
    let mut validated_projections = Vec::new();
    let mut supporting_inputs = BTreeMap::new();
    if let Some(path) = request.review {
        let approved = read_review(path, invocation_dir)?;
        validate_review(&approved, &review)?;
        ensure!(
            diagnostics.is_empty(),
            "Ownership/context review is still required: {}",
            diagnostics.join("; ")
        );
        let region_report = review
            .regions
            .as_ref()
            .ok_or_else(|| eyre::eyre!("No factorable region review is available"))?;
        let consolidated =
            consolidate_reviewed(&prepared.variants, region_report, &approved.decisions)?;
        let template = consolidated
            .template
            .ok_or_else(|| eyre::eyre!("Reviewed candidate has no present source"))?;
        ensure!(
            template.len() as u64 <= MAX_CORE_FILE_BYTES,
            "Draft template exceeds the core file limit"
        );
        let version_targets = prepared
            .contexts
            .iter()
            .zip(&prepared.evidence)
            .map(|(context, witness)| {
                (context.minecraft_version.clone(), witness.target_id.clone())
            })
            .collect::<BTreeMap<_, _>>();
        membership_variants(
            &consolidated.membership.decision,
            &version_targets,
            &input,
            &mut proposed_variants,
            InputPredicate::default(),
        )?;
        supporting_inputs = validate_candidate(
            &core,
            &metadata,
            &inventory,
            &prepared,
            request.file,
            &input,
            &template,
            &proposed_variants,
            &bindings,
            cancellation,
        )?;
        prepared
            .files
            .insert(format!("candidate/{input}"), template.into_bytes());
        prepared.files.insert(
            "candidate-membership.json".into(),
            facet_json::to_string_pretty(&BTreeMap::from([(
                request.file.to_owned(),
                proposed_variants.clone(),
            )]))?
            .into_bytes(),
        );
        validated_projections = prepared
            .variants
            .iter()
            .map(|variant| variant.id.clone())
            .collect();
        review.decisions = approved.decisions;
    }
    let candidate_validated = request.review.is_some();
    let (status, next_action) = if candidate_validated {
        (
            "validated_candidate",
            "Refine and review candidate text and membership before promotion. Validation covers only the named pinned witnesses; new feature combinations require their own review and testing.",
        )
    } else if review.regions.is_some() {
        (
            "awaiting_region_review",
            "Edit a copy of ownership-review.json with one decision per region, including empty owner sets for version-only regions. Rerun draft with --review and a new --output-dir. Feature candidates are evidence, not approved ownership.",
        )
    } else {
        (
            "ownership_review_required",
            "Review diagnostics and staged witnesses. Assign fine-grained functional owners and update the explicit canonical feature selections when needed, then rerun into a new directory. Never introduce a release/dev whole-file selector.",
        )
    };
    let report = OracleDraftReport {
        schema: "sfm:oracle_draft@1".into(),
        operation: "draft".into(),
        file: request.file.into(),
        status: status.into(),
        writes_performed: true,
        candidate_validated,
        selected_projections: review.projections.clone(),
        validated_projections,
        supporting_input_identity: if candidate_validated {
            Some(sha256(
                facet_json::to_string(&supporting_inputs)?.as_bytes(),
            ))
        } else {
            None
        },
        proposed_input: input,
        proposed_variants,
        witnesses: prepared.evidence,
        ownership_review: review,
        diagnostics,
        next_action: next_action.into(),
    };
    prepared.files.insert(
        "ownership-review.json".into(),
        facet_json::to_string_pretty(&report.ownership_review)?.into_bytes(),
    );
    prepared.files.insert(
        "report.json".into(),
        facet_json::to_string_pretty(&report)?.into_bytes(),
    );
    ensure_current(
        &catalog,
        &bindings,
        &git,
        &binding_bytes,
        &metadata_bytes,
        &inventory,
        cancellation,
    )?;
    for (relative, identity) in supporting_inputs {
        cancellation.bail_if_cancelled()?;
        ensure!(
            sha256(&read_bounded(&core, &relative, MAX_CORE_FILE_BYTES)?) == identity,
            "Supporting authored input {relative} changed during draft validation; rerun before claiming a current candidate"
        );
    }
    stage_bundle(&destination, &prepared.files, cancellation)?;
    Ok(report)
}

#[expect(
    clippy::too_many_lines,
    reason = "Read only the selected bounded text set after checking every immutable pin"
)]
fn read_witnesses(
    catalog: &CoreCatalog,
    bindings: &OracleBindings,
    git: &OracleGitRepository,
    request: &OracleDraftRequest<'_>,
    cancellation: &CancellationToken,
) -> Result<PreparedWitnesses> {
    let mut prepared = PreparedWitnesses {
        variants: Vec::new(),
        raw_sources: Vec::new(),
        contexts: Vec::new(),
        evidence: Vec::new(),
        files: BTreeMap::new(),
    };
    let wanted = request.projections.iter().cloned().collect::<BTreeSet<_>>();
    ensure!(
        wanted.len() == request.projections.len(),
        "Draft witness selections must not repeat a projection"
    );
    for projection in &wanted {
        ensure!(
            bindings
                .bindings
                .iter()
                .any(|row| &row.projection == projection),
            "Unknown required oracle witness {projection}"
        );
    }
    let mut total = 0_usize;
    for (index, binding) in bindings.bindings.iter().enumerate() {
        cancellation.bail_if_cancelled()?;
        let entry = catalog.catalog.entry(&binding.projection)?;
        ensure!(
            entry.target_id()? == binding.target_id
                && entry.environment.as_str() == binding.environment,
            "Oracle context no longer agrees with catalog: {}",
            binding.projection
        );
        let current = git.resolve_reference(&binding.reference)?;
        ensure!(
            current.commit == binding.commit && current.tree == binding.tree,
            "Oracle reference {} moved; inspect drift before drafting",
            binding.reference
        );
        ensure!(
            git.resolve_commit(&binding.commit)?.tree == binding.tree,
            "Pinned commit/tree mismatch for {}",
            binding.projection
        );
        // Every pin is checked even when an explicit subset supplies text.
        if !wanted.is_empty() && !wanted.contains(&binding.projection) {
            continue;
        }
        let files =
            git.files_at_commit_filtered(&binding.commit, &binding.source_prefix, |path| {
                path == request.file
            })?;
        let witness = files.get(request.file);
        let source = witness.map(|witness| witness.bytes.clone());
        if let Some(bytes) = &source {
            ensure!(
                bytes.len() as u64 <= MAX_WITNESS_BYTES,
                "Draft witness {} exceeds the 1 MiB text limit",
                binding.projection
            );
            let text = std::str::from_utf8(bytes).wrap_err(
                "Drafting requires UTF-8 text; use exact-byte seeding for binary assets",
            )?;
            ensure!(
                !text.contains('\0'),
                "Drafting requires text without NUL bytes"
            );
            total = total
                .checked_add(bytes.len())
                .ok_or_else(|| eyre::eyre!("Draft witness budget overflow"))?;
            ensure!(
                total <= MAX_TOTAL_WITNESS_BYTES,
                "Draft exceeds the bounded witness byte budget"
            );
        }
        let context = catalog.context(&binding.projection)?;
        let staged_file = witness.map(|_| format!("witnesses/{index:02}"));
        if let (Some(path), Some(source)) = (&staged_file, &source) {
            prepared.files.insert(path.clone(), source.clone());
        }
        prepared.raw_sources.push(source.clone());
        prepared.variants.push(SourceVariant {
            id: binding.projection.clone(),
            minecraft_version: context.minecraft_version.clone(),
            features: context.features.clone(),
            source,
        });
        prepared.evidence.push(OracleDraftWitness {
            projection: binding.projection.clone(),
            target_id: binding.target_id.clone(),
            minecraft_version: context.minecraft_version.clone(),
            reference: binding.reference.clone(),
            commit: binding.commit.clone(),
            tree: binding.tree.clone(),
            features: context.features.clone(),
            blob: witness.map(|row| row.oid.clone()),
            mode: witness.map(|row| row.mode),
            staged_file,
            factoring_representative: None,
            factoring_transformations: Vec::new(),
        });
        prepared.contexts.push(context);
    }
    ensure!(
        !prepared.variants.is_empty(),
        "Draft witness selection is empty"
    );
    let targets = prepared
        .evidence
        .iter()
        .map(|row| row.target_id.clone())
        .collect::<Vec<_>>();
    let coalesced = coalesce_policy_equivalent(
        request.file,
        &mut prepared.variants,
        &targets,
        &bindings.comparison_policy,
    );
    for (index, (representative, transformations)) in coalesced.into_iter().enumerate() {
        if index != representative {
            prepared.evidence[index].factoring_representative =
                Some(prepared.variants[representative].id.clone());
            prepared.evidence[index].factoring_transformations = transformations;
        }
    }
    Ok(prepared)
}

/// Reuse only the declared oracle policy; raw files remain unchanged evidence.
/// Symmetric comparison prevents directional generated-banner/settings allowances
/// becoming a new transformation of historical witnesses.
fn coalesce_policy_equivalent(
    file: &str,
    variants: &mut [SourceVariant],
    targets: &[String],
    policy: &ComparisonPolicy,
) -> Vec<(usize, Vec<String>)> {
    let originals = variants
        .iter()
        .map(|variant| variant.source.clone())
        .collect::<Vec<_>>();
    let mut representatives = Vec::<usize>::new();
    let mut decisions = Vec::new();
    for (index, source) in originals.iter().enumerate() {
        let mut chosen = (index, Vec::new());
        if let Some(source) = source {
            for representative in &representatives {
                let Some(candidate) = &originals[*representative] else {
                    continue;
                };
                let compared =
                    compare_project_bytes(file, &targets[index], source, candidate, policy);
                let reverse = compare_project_bytes(
                    file,
                    &targets[*representative],
                    candidate,
                    source,
                    policy,
                );
                if compared.normalized_equal
                    && compared.diagnostics.is_empty()
                    && reverse.normalized_equal
                    && reverse.diagnostics.is_empty()
                {
                    variants[index].source = Some(candidate.clone());
                    chosen = (*representative, compared.transformations);
                    break;
                }
            }
        }
        if chosen.0 == index {
            representatives.push(index);
        }
        decisions.push(chosen);
    }
    decisions
}

fn selector_conflicts(variants: &[SourceVariant]) -> Vec<String> {
    let mut conflicts = Vec::new();
    for (index, left) in variants.iter().enumerate() {
        for right in &variants[index + 1..] {
            if left.minecraft_version == right.minecraft_version
                && left.features == right.features
                && left.source != right.source
            {
                conflicts.push(format!("{} and {} have different content or membership but identical Minecraft version and registered feature selections. Review fine-grained ownership and explicitly distinguish the catalog feature selections; a region owner cannot discriminate identical selectors.", left.id, right.id));
            }
        }
    }
    conflicts
}

fn inventory_identity(inventory: &BTreeSet<String>) -> String {
    sha256(
        inventory
            .iter()
            .flat_map(|path| path.bytes().chain(std::iter::once(0)))
            .collect::<Vec<_>>()
            .as_slice(),
    )
}

fn validate_review(approved: &OracleDraftReview, expected: &OracleDraftReview) -> Result<()> {
    ensure!(
        approved.schema == expected.schema
            && approved.file == expected.file
            && approved.projections == expected.projections
            && approved.binding_identity == expected.binding_identity
            && approved.catalog_identity == expected.catalog_identity
            && approved.feature_registry_identity == expected.feature_registry_identity
            && approved.metadata_identity == expected.metadata_identity
            && approved.source_inventory_identity == expected.source_inventory_identity
            && approved.regions == expected.regions,
        "Stale or modified draft review: catalog, registry, metadata, inventory, witnesses or region evidence changed; regenerate ownership review"
    );
    Ok(())
}

fn read_review(path: &Path, invocation_dir: &Path) -> Result<OracleDraftReview> {
    let absolute = if path.is_absolute() {
        path.to_path_buf()
    } else {
        invocation_dir.join(path)
    };
    let parent = checked_directory(
        absolute
            .parent()
            .ok_or_else(|| eyre::eyre!("Review path needs a parent"))?,
    )?;
    let name = absolute
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("Review file name is not UTF-8"))?;
    let bytes = read_bounded(&parent, name, MAX_REVIEW_BYTES)?;
    facet_json::from_str(std::str::from_utf8(&bytes)?)
        .wrap_err("Cannot parse draft ownership review")
}

fn membership_variants(
    rule: &SelectionRule<bool>,
    version_targets: &BTreeMap<String, String>,
    input: &str,
    variants: &mut Vec<InputVariant>,
    predicate: InputPredicate,
) -> Result<()> {
    match rule {
        SelectionRule::Value(false) => {}
        SelectionRule::Value(true) => {
            ensure!(
                variants.len() < MAX_MEMBERSHIP_VARIANTS,
                "Draft membership exceeds the bounded rule count"
            );
            let mut predicate = predicate;
            if predicate.targets.is_empty() {
                predicate.targets = version_targets.values().cloned().collect();
            }
            variants.push(InputVariant {
                input: input.into(),
                when: predicate,
                template: true,
            });
        }
        SelectionRule::Feature {
            feature,
            enabled,
            disabled,
        } => {
            let mut on = predicate.clone();
            on.all_features.push(feature.clone());
            membership_variants(enabled, version_targets, input, variants, on)?;
            let mut off = predicate;
            off.none_features.push(feature.clone());
            membership_variants(disabled, version_targets, input, variants, off)?;
        }
        SelectionRule::MinecraftVersion { cases } => {
            for case in cases {
                let mut selected = predicate.clone();
                let targets = case
                    .minecraft_versions
                    .iter()
                    .map(|version| {
                        version_targets.get(version).cloned().ok_or_else(|| {
                            eyre::eyre!("Unmapped witnessed Minecraft version {version}")
                        })
                    })
                    .collect::<Result<Vec<_>>>()?;
                selected.targets = targets
                    .into_iter()
                    .filter(|target| {
                        predicate.targets.is_empty() || predicate.targets.contains(target)
                    })
                    .collect();
                if !selected.targets.is_empty() {
                    membership_variants(&case.rule, version_targets, input, variants, selected)?;
                }
            }
        }
    }
    Ok(())
}

#[expect(
    clippy::too_many_arguments,
    clippy::too_many_lines,
    reason = "Validation binds the candidate to its complete witnessed matrix and current core selection"
)]
fn validate_candidate(
    core: &Path,
    metadata: &CoreProjectInputs,
    inventory: &BTreeSet<String>,
    witnesses: &PreparedWitnesses,
    file: &str,
    input: &str,
    template: &str,
    variants: &[InputVariant],
    bindings: &OracleBindings,
    cancellation: &CancellationToken,
) -> Result<BTreeMap<String, String>> {
    let mut proposed = metadata.clone();
    if file.starts_with("src/") {
        proposed.source_rules.insert(file.into(), variants.to_vec());
    } else {
        proposed
            .project_files
            .insert(file.into(), variants.to_vec());
    }
    // Use the production collector's required fixed layout. Copy only selected
    // authored inputs; oracle blobs are not used as supporting project inputs.
    let scratch = tempfile::tempdir()?;
    let scratch_root = scratch.path().join(CORE_ROOT);
    fs::create_dir_all(&scratch_root)?;
    let mut copied = BTreeSet::new();
    let mut support_identities = BTreeMap::new();
    let mut total = 0_u64;
    for ((context, witness), raw_source) in witnesses
        .contexts
        .iter()
        .zip(&witnesses.variants)
        .zip(&witnesses.raw_sources)
    {
        cancellation.bail_if_cancelled()?;
        let binding = bindings
            .bindings
            .iter()
            .find(|row| row.projection == witness.id)
            .ok_or_else(|| eyre::eyre!("Missing selected witness binding {}", witness.id))?;
        let before = select_core_inputs(metadata, context, inventory)?;
        let selected = select_core_inputs(&proposed, context, inventory)?;
        ensure!(
            before
                .inputs
                .iter()
                .all(|(output, chosen)| output.as_str() == file || chosen.input != input),
            "Proposed draft input already owns an unrelated output; choose a different authored destination during refinement"
        );
        let present = witness.source.is_some();
        ensure!(
            selected.inputs.contains_key(file) == present,
            "Production selector changes absent-file membership in {}",
            witness.id
        );
        if let Some(chosen) = selected.inputs.get(file) {
            ensure!(
                chosen.input == input && chosen.template,
                "Production selector does not select the proposed draft input"
            );
        }
        ensure!(
            before
                .inputs
                .iter()
                .filter(|(output, _)| output.as_str() != file)
                .eq(selected
                    .inputs
                    .iter()
                    .filter(|(output, _)| output.as_str() != file)),
            "Draft would change unrelated selected outputs"
        );
        for chosen in selected.inputs.values() {
            if !copied.insert(chosen.input.clone()) {
                continue;
            }
            let bytes = if chosen.input == input {
                template.as_bytes().to_vec()
            } else {
                read_bounded(core, &chosen.input, MAX_CORE_FILE_BYTES)?
            };
            if chosen.input != input {
                support_identities.insert(chosen.input.clone(), sha256(&bytes));
            }
            total = total
                .checked_add(bytes.len() as u64)
                .ok_or_else(|| eyre::eyre!("Draft validation byte budget overflow"))?;
            ensure!(
                total <= MAX_CORE_PROJECTION_BYTES,
                "Draft validation exceeds the selected input byte budget"
            );
            create_scratch_file(&scratch_root, &chosen.input, &bytes)?;
        }
        let rendered =
            collect_core_artifacts(&scratch_root, &selected, context).wrap_err_with(|| {
                format!("Production collector rejected draft context {}", witness.id)
            })?;
        match (raw_source, rendered.get(file)) {
            (None, None) => {}
            (Some(expected), Some(actual)) => {
                let compared = compare_project_bytes(
                    file,
                    &binding.target_id,
                    expected,
                    &actual.output_bytes,
                    &bindings.comparison_policy,
                );
                ensure!(
                    compared.normalized_equal && compared.diagnostics.is_empty(),
                    "Production renderer does not reconstruct pinned witness {} for {file}",
                    witness.id
                );
            }
            _ => eyre::bail!(
                "Production candidate membership differs from {}",
                witness.id
            ),
        }
    }
    Ok(support_identities)
}

fn ensure_current(
    catalog: &CoreCatalog,
    bindings: &OracleBindings,
    git: &OracleGitRepository,
    binding_bytes: &[u8],
    metadata_bytes: &[u8],
    inventory: &BTreeSet<String>,
    cancellation: &CancellationToken,
) -> Result<()> {
    let current = CoreCatalog::load(&catalog.repo_root, &catalog.repo_root)?;
    ensure!(
        current.catalog_sha256 == catalog.catalog_sha256
            && current.feature_definitions_sha256 == catalog.feature_definitions_sha256,
        "Catalog or feature registry changed during drafting"
    );
    ensure!(
        read_bounded(&catalog.repo_root, ORACLES_PATH, 1024 * 1024)? == binding_bytes,
        "Oracle bindings changed during drafting"
    );
    ensure!(
        read_bounded(
            &catalog.repo_root,
            CORE_METADATA_PATH,
            MAX_CORE_METADATA_BYTES
        )? == metadata_bytes
            && discover_core_source_files(&catalog.repo_root.join(CORE_ROOT))? == *inventory,
        "Core membership metadata or source inventory changed during drafting"
    );
    for binding in &bindings.bindings {
        cancellation.bail_if_cancelled()?;
        let current = git.resolve_reference(&binding.reference)?;
        ensure!(
            current.commit == binding.commit && current.tree == binding.tree,
            "Oracle reference {} moved during drafting",
            binding.reference
        );
    }
    Ok(())
}

fn checked_destination(
    output: &Path,
    invocation_dir: &Path,
    repo_root: &Path,
    git: &OracleGitRepository,
) -> Result<PathBuf> {
    ensure!(
        !output.as_os_str().is_empty(),
        "Draft output directory must be explicit"
    );
    let path = if output.is_absolute() {
        output.to_path_buf()
    } else {
        invocation_dir.join(output)
    };
    ensure!(
        !path
            .components()
            .any(|part| matches!(part, Component::ParentDir | Component::CurDir)),
        "Draft output directory cannot contain parent/current traversal"
    );
    let parent = checked_directory(
        path.parent()
            .ok_or_else(|| eyre::eyre!("Draft output directory needs an existing parent"))?,
    )?;
    let name = path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("Draft directory name must be portable UTF-8"))?;
    validate_projection_key(name)?;
    let destination = parent.join(name);
    // Compare canonical native paths on both sides. Windows canonicalization
    // adds a verbatim prefix and normalizes separators; mixing it with the
    // unresolved repository spelling can bypass the containment check.
    let repository = checked_directory(repo_root)?;
    let minecraft = checked_directory(&repository.join("platform").join("minecraft"))?;
    // Linked worktrees use a .git pointer file with state outside the checkout.
    // gix's common directory may retain legitimate ../.. from the commondir file.
    let git_dir =
        fs::canonicalize(git.git_dir()).wrap_err("Cannot resolve oracle worktree Git directory")?;
    let common_dir = fs::canonicalize(git.common_dir())
        .wrap_err("Cannot resolve shared oracle Git directory")?;
    ensure!(
        !is_within(&destination, &minecraft)
            && !is_within(&destination, &repository.join(".git"))
            && !is_within(&destination, &git_dir)
            && !is_within(&destination, &common_dir),
        "Draft destination must be outside canonical Minecraft inputs, projections and Git state"
    );
    match fs::symlink_metadata(&destination) {
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
        Ok(_) => eyre::bail!(
            "Draft destination already exists; choose a new directory to preserve all staged and contributor work"
        ),
    }
    Ok(destination)
}

fn create_scratch_file(root: &Path, relative: &str, bytes: &[u8]) -> Result<()> {
    validate_projection_key(relative)?;
    let mut parent = checked_directory(root)?;
    let parts = relative.split('/').collect::<Vec<_>>();
    for part in &parts[..parts.len() - 1] {
        checked_directory(&parent)?;
        let next = parent.join(part);
        match fs::create_dir(&next) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error.into()),
        }
        parent = checked_directory(&next)?;
    }
    let path = parent.join(parts[parts.len() - 1]);
    let mut file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&path)?;
    file.write_all(bytes)?;
    file.sync_all()?;
    Ok(())
}

fn stage_bundle(
    destination: &Path,
    files: &BTreeMap<String, Vec<u8>>,
    cancellation: &CancellationToken,
) -> Result<()> {
    let total = files
        .values()
        .try_fold(0_usize, |total, bytes| total.checked_add(bytes.len()))
        .ok_or_else(|| eyre::eyre!("Draft bundle byte budget overflow"))?;
    ensure!(
        total <= MAX_BUNDLE_BYTES,
        "Draft bundle exceeds its byte budget"
    );
    for path in files.keys() {
        validate_projection_key(path)?;
    }
    cancellation.bail_if_cancelled()?;
    checked_directory(
        destination
            .parent()
            .ok_or_else(|| eyre::eyre!("Draft destination has no parent"))?,
    )?;
    fs::create_dir(destination).wrap_err(
        "Cannot create the new draft bundle directory; no existing output is overwritten",
    )?;
    for (relative, bytes) in files {
        cancellation.bail_if_cancelled()?;
        checked_directory(destination)?;
        create_scratch_file(destination, relative, bytes).wrap_err_with(|| {
            format!(
                "Incomplete staged draft bundle at {}; existing work is preserved",
                destination.display()
            )
        })?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn equal_selector_pair(left: Option<&[u8]>, right: Option<&[u8]>) -> Vec<SourceVariant> {
        [left, right]
            .into_iter()
            .enumerate()
            .map(|(index, source)| SourceVariant {
                id: format!("witness-{index}"),
                minecraft_version: "1.19.2".into(),
                features: BTreeMap::new(),
                source: source.map(<[u8]>::to_vec),
            })
            .collect()
    }

    #[test]
    fn declared_crlf_and_single_final_newline_differences_need_no_owner() {
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            allow_final_newline_difference: true,
            ..ComparisonPolicy::default()
        };
        let targets = vec!["1.19.2".into(), "1.19.2".into()];
        for (left, right) in [
            (b"{\"value\":1}\n".as_slice(), b"{\"value\":1}".as_slice()),
            (
                b"{\"value\":1}\r\n".as_slice(),
                b"{\"value\":1}\n".as_slice(),
            ),
        ] {
            let mut variants = equal_selector_pair(Some(left), Some(right));
            let coalesced =
                coalesce_policy_equivalent("sfm.mixins.json", &mut variants, &targets, &policy);
            assert_eq!(coalesced[1].0, 0);
            assert!(!coalesced[1].1.is_empty());
            assert!(selector_conflicts(&variants).is_empty());
            assert!(preview_regions(&variants).unwrap().regions.is_empty());
            // The original right-hand bytes remain suitable for final validation.
            assert!(
                compare_project_bytes(
                    "sfm.mixins.json",
                    "1.19.2",
                    right,
                    variants[1].source.as_deref().unwrap(),
                    &policy
                )
                .normalized_equal
            );
        }
        let mut undeclared = equal_selector_pair(Some(b"one\r\n"), Some(b"one\n"));
        coalesce_policy_equivalent(
            "file.json",
            &mut undeclared,
            &targets,
            &ComparisonPolicy::default(),
        );
        assert_eq!(selector_conflicts(&undeclared).len(), 1);
    }

    #[test]
    fn content_extra_blank_lines_and_absent_vs_empty_still_need_review() {
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            allow_final_newline_difference: true,
            ..ComparisonPolicy::default()
        };
        let targets = vec!["1.19.2".into(), "1.19.2".into()];
        for mut variants in [
            equal_selector_pair(Some(b"one\n"), Some(b"two\n")),
            equal_selector_pair(Some(b"one\n"), Some(b"one\n\n")),
            equal_selector_pair(None, Some(b"")),
        ] {
            let coalesced =
                coalesce_policy_equivalent("file.json", &mut variants, &targets, &policy);
            assert_eq!(coalesced[1].0, 1);
            assert_eq!(selector_conflicts(&variants).len(), 1);
        }
    }

    #[test]
    fn identical_selector_conflicts_require_actionable_ownership_review() {
        let variants = vec![
            SourceVariant {
                id: "release".into(),
                minecraft_version: "1.19.2".into(),
                features: BTreeMap::from([("fix".into(), false)]),
                source: Some(b"same\nold\n".to_vec()),
            },
            SourceVariant {
                id: "dev".into(),
                minecraft_version: "1.19.2".into(),
                features: BTreeMap::from([("fix".into(), false)]),
                source: Some(b"same\nnew\n".to_vec()),
            },
        ];
        let conflicts = selector_conflicts(&variants);
        assert_eq!(conflicts.len(), 1);
        assert!(conflicts[0].contains("identical Minecraft version"));
        assert!(conflicts[0].contains("catalog feature selections"));
        let mut absent = variants.clone();
        absent[1].source = None;
        assert_eq!(selector_conflicts(&absent).len(), 1);
        absent[1].source = Some(Vec::new());
        assert_eq!(selector_conflicts(&absent).len(), 1);
    }

    #[test]
    fn membership_translation_preserves_absence_and_reviewed_feature_boundary() {
        let rule = SelectionRule::Feature {
            feature: "fix".into(),
            enabled: Box::new(SelectionRule::Value(true)),
            disabled: Box::new(SelectionRule::Value(false)),
        };
        let mut variants = Vec::new();
        membership_variants(
            &rule,
            &BTreeMap::from([("1.21".into(), "1.21.0".into())]),
            "draft/src/A.java",
            &mut variants,
            InputPredicate::default(),
        )
        .unwrap();
        assert_eq!(variants.len(), 1);
        assert_eq!(variants[0].when.targets, ["1.21.0"]);
        assert_eq!(variants[0].when.all_features, ["fix"]);
        assert!(variants[0].when.none_features.is_empty());
        assert!(variants[0].template);
    }

    #[test]
    fn catalog_edits_invalidate_review_even_when_regions_are_identical() {
        let expected = OracleDraftReview {
            schema: REVIEW_SCHEMA.into(),
            file: "src/A.java".into(),
            projections: vec!["release".into()],
            binding_identity: "pins".into(),
            catalog_identity: "catalog".into(),
            feature_registry_identity: "features".into(),
            metadata_identity: "metadata".into(),
            source_inventory_identity: "inventory".into(),
            regions: None,
            decisions: BTreeMap::new(),
        };
        let mut stale = expected.clone();
        stale.catalog_identity = "changed".into();
        assert!(validate_review(&stale, &expected).is_err());
        stale = expected.clone();
        stale.projections.push("dev".into());
        assert!(validate_review(&stale, &expected).is_err());
        validate_review(&expected, &expected).unwrap();
    }

    fn git_fixture_command(repo: &Path, args: &[&str]) {
        let output = std::process::Command::new("git")
            .arg("-C")
            .arg(repo)
            .args([
                "-c",
                "user.name=Oracle draft fixture",
                "-c",
                "user.email=oracle@example.invalid",
                "-c",
                "commit.gpgSign=false",
                "-c",
                "core.hooksPath=.",
            ])
            .args(args)
            .env_remove("GIT_DIR")
            .env_remove("GIT_WORK_TREE")
            .env_remove("GIT_INDEX_FILE")
            .env_remove("GIT_NAMESPACE")
            .env_remove("GIT_COMMON_DIR")
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {args:?}: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    #[test]
    fn staged_destination_is_create_only_and_refuses_canonical_inputs() {
        let temp = tempfile::tempdir().unwrap();
        git_fixture_command(temp.path(), &["init", "--quiet"]);
        let git = OracleGitRepository::open(temp.path()).unwrap();
        let minecraft = temp.path().join("platform/minecraft");
        fs::create_dir_all(&minecraft).unwrap();
        assert!(
            checked_destination(
                Path::new("platform/minecraft/new"),
                temp.path(),
                temp.path(),
                &git,
            )
            .is_err()
        );
        let canonical_repository = checked_directory(temp.path()).unwrap();
        assert!(
            checked_destination(
                &minecraft.join("new"),
                temp.path(),
                &canonical_repository,
                &git,
            )
            .is_err()
        );
        assert!(
            checked_destination(Path::new(".git/new"), temp.path(), temp.path(), &git).is_err()
        );
        assert!(
            checked_destination(
                Path::new("platform/minecraft-sibling"),
                temp.path(),
                temp.path(),
                &git,
            )
            .is_ok()
        );
        let destination =
            checked_destination(Path::new("bundle"), temp.path(), temp.path(), &git).unwrap();
        let files = BTreeMap::from([("witnesses/00".into(), b"original".to_vec())]);
        stage_bundle(&destination, &files, &CancellationToken::new()).unwrap();
        assert_eq!(
            fs::read(destination.join("witnesses/00")).unwrap(),
            b"original"
        );
        assert!(checked_destination(Path::new("bundle"), temp.path(), temp.path(), &git).is_err());
        assert!(stage_bundle(&destination, &files, &CancellationToken::new()).is_err());
        assert_eq!(
            fs::read(destination.join("witnesses/00")).unwrap(),
            b"original"
        );
    }

    #[test]
    fn staged_destination_refuses_actual_linked_worktree_git_state() {
        let temp = tempfile::tempdir().unwrap();
        let main = temp.path().join("main");
        fs::create_dir(&main).unwrap();
        git_fixture_command(&main, &["init", "--quiet"]);
        git_fixture_command(
            &main,
            &["commit", "--quiet", "--allow-empty", "-m", "fixture"],
        );
        let linked = temp.path().join("linked");
        git_fixture_command(
            &main,
            &[
                "worktree",
                "add",
                "--quiet",
                "--detach",
                linked.to_str().unwrap(),
            ],
        );
        fs::create_dir_all(linked.join("platform/minecraft")).unwrap();
        assert!(fs::symlink_metadata(linked.join(".git")).unwrap().is_file());
        let git = OracleGitRepository::open(&linked).unwrap();
        let repository = checked_directory(&linked).unwrap();
        let git_dir = fs::canonicalize(git.git_dir()).unwrap();
        let common_dir = fs::canonicalize(git.common_dir()).unwrap();
        assert_ne!(git_dir, common_dir);
        assert!(!is_within(&git_dir, &repository));
        assert!(!is_within(&common_dir, &repository));
        for parent in [
            git.git_dir().to_path_buf(),
            common_dir.clone(),
            common_dir.join("objects"),
        ] {
            let output = parent.join("draft-evidence");
            assert!(checked_destination(&output, &linked, &linked, &git).is_err());
            let canonical_output = checked_directory(&parent).unwrap().join("draft-evidence");
            assert!(checked_destination(&canonical_output, &linked, &linked, &git).is_err());
            assert!(!output.exists());
        }
        assert!(
            checked_destination(&temp.path().join("allowed-bundle"), &linked, &linked, &git)
                .is_ok()
        );
    }
}
