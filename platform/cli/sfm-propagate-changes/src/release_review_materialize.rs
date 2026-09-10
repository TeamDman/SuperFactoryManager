//! Deterministic projection of a commit-pinned Git review domain into the
//! portable `sfm.release-review/1` document model.
//!
//! The materializer reads Git objects by object ID with `git cat-file blob`.
//! It never checks out a revision and never writes refs, the index, or the
//! worktree.  Text that cannot be represented by the frozen UTF-8 contract is
//! retained as partial or missing corpus evidence and as an explicit bounded
//! review unit instead of being dropped.

use crate::release_review_git::GitChangeKind;
use crate::release_review_git::GitDomainChange;
use crate::release_review_git::GitPath;
use crate::release_review_git::GitReviewDomain;
use crate::release_review_git::GitReviewUnit;
use crate::release_review_git::GitReviewUnitKind;
use crate::release_review_v1::ChangeOperationV1;
use crate::release_review_v1::CorpusDocumentV1;
use crate::release_review_v1::MaterializationV1;
use crate::release_review_v1::NamedQueryV1;
use crate::release_review_v1::ProducerGenerationV1;
use crate::release_review_v1::ReleaseReviewDocumentV1;
use crate::release_review_v1::RepositoryBindingV1;
use crate::release_review_v1::ResumeStateV1;
use crate::release_review_v1::ReviewUnitV1;
use crate::release_review_v1::SnapshotSideV1;
use crate::release_review_v1::SurfaceKindV1;
use crate::release_review_v1::Utf8RangeV1;
use crate::review_session_v1::CommentStyleRuleV1;
use crate::review_session_v1::CompletionPolicyV1;
use crate::review_session_v1::DocumentRevisionV1;
use crate::review_session_v1::ProvenanceV1;
use crate::review_session_v1::RepositoryV1;
use crate::review_session_v1::RevisionLaneV1;
use crate::review_session_v1::SelectionRuleV1;
use crate::review_session_v1::SnapshotV1;
use crate::review_session_v1::{self};
use crate::review_session_v2::CommentTargetV2;
use crate::review_session_v2::CommentV2;
use crate::review_session_v2::ReviewSessionV2;
use crate::review_session_v2::{self};
use eyre::Context as _;
use eyre::eyre;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::error::Error;
use std::fmt::Display;
use std::fmt::Formatter;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;

pub const PRODUCER_ID: &str = "sfm.release-review.git-materializer/1";
pub const RETIRED_PRODUCER_ID: &str = "sfm.release-review.git-materializer-retired/1";
pub const MATERIALIZER_VERSION: &str = "sfm.release-review.git-materializer/1";
pub const PRODUCER_RULE_ID: &str = "sfm.release-review.git-unit-fallback/1";
const RETIRED_UNIT_LIMITATION: &str = "The pinned producer no longer emits this stable review unit; it is retained explicitly until a human resolves its prior review progress";

/// Explicit, pinned inputs which are not already part of `GitReviewDomain`.
///
/// `before_revision` and `candidate_revision` are resolved commit IDs, not
/// moving ref names.  They are checked against the completed Git domain before
/// any object is read.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ReleaseReviewMaterializeConfig {
    pub repository_root: PathBuf,
    pub lane_id: String,
    pub repository_id: String,
    pub root_hint: String,
    pub before_label: String,
    pub before_revision: String,
    pub after_label: String,
    pub candidate_revision: String,
    pub review_evidence_path: String,
}

/// Counts proving that materialization preserved the complete raw Git domain.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ReleaseReviewMaterializationReconciliation {
    pub raw_change_count: usize,
    pub raw_side_path_count: usize,
    pub git_unit_count: usize,
    pub review_unit_count: usize,
    pub represented_corpus_side_path_count: usize,
    pub explicitly_unsupported_corpus_side_path_count: usize,
    pub complete: bool,
}

/// Deterministic evidence describing how a producer refresh changed the
/// generated surface while retaining human-owned progress.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ReleaseReviewRefreshReconciliation {
    pub previous_generation: String,
    pub refreshed_generation: String,
    pub active_units_added: usize,
    pub active_units_updated: usize,
    pub active_units_unchanged: usize,
    pub retired_units_added: usize,
    pub retired_units_carried: usize,
    pub generated_comments: usize,
    pub human_comments_preserved: usize,
    pub selector_bindings_preserved: usize,
    pub migration_reports_preserved: usize,
    pub named_queries_preserved: usize,
    pub deferred_units_preserved: usize,
    pub completion_attestations_preserved: usize,
}

#[derive(Clone, Debug, PartialEq)]
pub struct ReleaseReviewRefreshResult {
    pub document: ReleaseReviewDocumentV1,
    pub reconciliation: ReleaseReviewRefreshReconciliation,
}

/// A stable, machine-readable refresh refusal. Callers should surface `code`
/// directly rather than reducing this to an unstructured write failure.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ReleaseReviewRefreshError {
    pub code: &'static str,
    pub message: String,
}

impl ReleaseReviewRefreshError {
    fn new(code: &'static str, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
        }
    }
}

impl Display for ReleaseReviewRefreshError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}: {}", self.code, self.message)
    }
}

impl Error for ReleaseReviewRefreshError {}

#[derive(Clone, Debug)]
struct SideDocument {
    side: SnapshotSideV1,
    path: String,
    document_revision_id: String,
    corpus_id: String,
    sha256: String,
    source_owner: String,
    source_locator: String,
    materialization: MaterializationV1,
    text: Option<String>,
    limitation: Option<String>,
}

/// Materialize a complete, reconciled Git domain into a canonical release
/// review document.
///
/// # Errors
///
/// Returns an error when the domain is incomplete, the explicit pins disagree,
/// Git cannot be launched, an invariant cannot be represented, or the produced
/// document fails the frozen canonical parser.
pub fn materialize_release_review(
    domain: &GitReviewDomain,
    config: &ReleaseReviewMaterializeConfig,
) -> eyre::Result<ReleaseReviewDocumentV1> {
    materialize_with_markers(domain, config, true)
}

/// Resolve live Git surfaces without manufacturing annotation records.
/// # Errors
/// Rejects the same invalid pins, domain or materialization as the frozen API.
pub fn materialize_observation(
    domain: &GitReviewDomain,
    config: &ReleaseReviewMaterializeConfig,
) -> eyre::Result<ReleaseReviewDocumentV1> {
    materialize_with_markers(domain, config, false)
}

#[expect(
    clippy::too_many_lines,
    reason = "the top-level projection keeps the frozen wire model visibly auditable"
)]
fn materialize_with_markers(
    domain: &GitReviewDomain,
    config: &ReleaseReviewMaterializeConfig,
    include_markers: bool,
) -> eyre::Result<ReleaseReviewDocumentV1> {
    validate_inputs(domain, config)?;

    let input_fingerprint = input_fingerprint(domain, config);
    let producer_generation = format!("git-materializer-v1:{input_fingerprint}");
    let mut side_documents = materialize_side_documents(domain, config)?;
    let change_by_id = domain
        .changes
        .iter()
        .map(|change| (change.change_id.as_str(), change))
        .collect::<BTreeMap<_, _>>();

    let mut review_units = Vec::with_capacity(domain.units.len());
    for unit in &domain.units {
        let change = change_by_id
            .get(unit.change_id.as_str())
            .copied()
            .ok_or_else(|| {
                eyre!(
                    "Git unit '{}' references unknown change '{}'",
                    unit.unit_id,
                    unit.change_id
                )
            })?;
        review_units.push(materialize_unit(
            unit,
            change,
            config,
            &side_documents,
            &producer_generation,
        )?);
    }
    review_units.sort_by(compare_review_units);

    let comments = review_units
        .iter()
        .filter(|_| include_markers)
        .map(|unit| generated_comment(unit, &side_documents, &producer_generation))
        .collect::<eyre::Result<Vec<_>>>()?;

    let mut before_documents = Vec::new();
    let mut after_documents = Vec::new();
    let mut corpus_documents = Vec::with_capacity(side_documents.len());
    for side_document in side_documents.values_mut() {
        if let Some(text) = side_document.text.take() {
            let embedded = DocumentRevisionV1 {
                id: side_document.document_revision_id.clone(),
                path: side_document.path.clone(),
                encoding: "utf-8".to_owned(),
                sha256: side_document.sha256.clone(),
                text,
            };
            match side_document.side {
                SnapshotSideV1::Before => before_documents.push(embedded),
                SnapshotSideV1::After => after_documents.push(embedded),
            }
        }
        corpus_documents.push(CorpusDocumentV1 {
            id: side_document.corpus_id.clone(),
            lane_id: config.lane_id.clone(),
            snapshot_side: side_document.side,
            path: side_document.path.clone(),
            document_revision_id: side_document.document_revision_id.clone(),
            sha256: side_document.sha256.clone(),
            source_owner: side_document.source_owner.clone(),
            source_locator: side_document.source_locator.clone(),
            materialization: side_document.materialization,
        });
    }
    before_documents.sort_by(|left, right| left.path.cmp(&right.path));
    after_documents.sort_by(|left, right| left.path.cmp(&right.path));

    let approved_expression = format!("#approved intersect {} HEAD", config.lane_id);
    let remaining_expression = format!("({} HEAD) difference effective(#approved)", config.lane_id);
    let current_unit_id = review_units.first().map(|unit| unit.id.clone());
    let session_id = stable_id(
        "release-review-session",
        &[
            config.repository_id.as_bytes(),
            config.lane_id.as_bytes(),
            domain.before.commit_id.as_bytes(),
            domain.candidate.commit_id.as_bytes(),
        ],
    );

    let mut document = ReleaseReviewDocumentV1 {
        schema: crate::release_review_v1::SCHEMA.to_owned(),
        review_session: ReviewSessionV2 {
            schema: review_session_v2::SCHEMA.to_owned(),
            id: session_id,
            title: format!(
                "{} release review: {} to {}",
                config.lane_id, config.before_label, config.after_label
            ),
            coordinate_system: review_session_v1::COORDINATE_SYSTEM.to_owned(),
            revision_lanes: vec![RevisionLaneV1 {
                id: config.lane_id.clone(),
                repository: RepositoryV1 {
                    id: config.repository_id.clone(),
                    root_hint: config.root_hint.clone(),
                },
                version_label: Some(config.lane_id.clone()),
                before: SnapshotV1 {
                    id: format!("git:{}", domain.before.commit_id),
                    documents: before_documents,
                },
                after: SnapshotV1 {
                    id: format!("git:{}", domain.candidate.commit_id),
                    documents: after_documents,
                },
            }],
            comments,
            style_rules: vec![CommentStyleRuleV1 {
                id: "release-change".to_owned(),
                required_hashtags: vec!["#release-change".to_owned()],
                priority: 0,
                foreground: None,
                background: None,
                underline: None,
                gutter_marker: Some("R".to_owned()),
                enabled: true,
            }],
            completion_policy: CompletionPolicyV1 {
                coverage_mode: "changed_surface".to_owned(),
                approval_hashtag: "#approved".to_owned(),
                blocking_hashtags: vec!["#problem".to_owned(), "#needs-change".to_owned()],
            },
        },
        repository_bindings: vec![RepositoryBindingV1 {
            lane_id: config.lane_id.clone(),
            repository_id: config.repository_id.clone(),
            root_hint: config.root_hint.clone(),
            before_label: config.before_label.clone(),
            before_commit: domain.before.commit_id.clone(),
            before_tree: domain.before.tree_id.clone(),
            after_label: config.after_label.clone(),
            candidate_commit: Some(domain.candidate.commit_id.clone()),
            candidate_tree: Some(domain.candidate.tree_id.clone()),
            working_tree_capture: None,
            review_evidence_paths: vec![config.review_evidence_path.clone()],
        }],
        corpus_documents,
        review_units,
        selector_bindings: Vec::new(),
        migration_reports: Vec::new(),
        named_queries: vec![
            NamedQueryV1 {
                id: "approved".to_owned(),
                expression: approved_expression,
            },
            NamedQueryV1 {
                id: "remaining".to_owned(),
                expression: remaining_expression.clone(),
            },
        ],
        resume_state: ResumeStateV1 {
            active_query_id: Some("remaining".to_owned()),
            active_query_expression: Some(remaining_expression),
            current_unit_id,
            deferred_unit_ids: Vec::new(),
            generation: 0,
        },
        producer_generations: vec![ProducerGenerationV1 {
            producer_id: PRODUCER_ID.to_owned(),
            generation: producer_generation,
            input_fingerprint,
            // The output fingerprint is defined over the canonical projection
            // with this field zeroed, avoiding a recursive fixed-point hash.
            output_fingerprint: "0".repeat(64),
        }],
        completion_attestations: Vec::new(),
    };

    if !include_markers {
        document.review_session.style_rules.clear();
    }
    let projected = crate::release_review_v1::to_canonical_json(&document)
        .wrap_err("could not canonicalize materializer output projection")?;
    document.producer_generations[0].output_fingerprint = sha256(projected.as_bytes());

    let canonical = crate::release_review_v1::to_canonical_json(&document)
        .wrap_err("materialized release-review document is invalid")?;
    let document = crate::release_review_v1::parse(&canonical)
        .wrap_err("canonical materializer output did not parse")?;
    let reconciliation = reconcile_materialization(domain, &document)?;
    if !reconciliation.complete {
        return Err(eyre!(
            "materialization reconciliation failed: {reconciliation:?}"
        ));
    }
    Ok(document)
}

/// Re-run the pinned producer and merge its generated state into an existing
/// portable review without discarding human-owned progress.
///
/// # Errors
///
/// Returns a typed refusal when the pinned source domain cannot be reproduced
/// or when producer ownership cannot be reconciled safely.
pub fn refresh_release_review(
    existing: &ReleaseReviewDocumentV1,
    domain: &GitReviewDomain,
    config: &ReleaseReviewMaterializeConfig,
) -> Result<ReleaseReviewRefreshResult, ReleaseReviewRefreshError> {
    let refreshed = materialize_release_review(domain, config).map_err(|error| {
        ReleaseReviewRefreshError::new(
            "refresh.materialization-failed",
            format!("could not reproduce the pinned producer domain: {error:#}"),
        )
    })?;
    merge_refreshed_materialization(existing, &refreshed)
}

/// Merge a freshly materialized producer document with an existing portable
/// review. This separate boundary makes producer-generation transitions
/// directly testable without a mutable Git fixture.
///
/// Producer-owned active units and comments are replaced by the fresh output.
/// Units which disappear are retained as explicit unsupported retirement
/// witnesses. Everything without this materializer's provenance is preserved.
///
/// # Errors
///
/// Returns a typed refusal when source snapshots differ, generated ownership
/// is internally inconsistent, or preserving progress would create an ID
/// collision or invalid portable document.
#[expect(
    clippy::too_many_lines,
    reason = "the ownership transfer remains in one auditable transaction"
)]
pub fn merge_refreshed_materialization(
    existing: &ReleaseReviewDocumentV1,
    refreshed: &ReleaseReviewDocumentV1,
) -> Result<ReleaseReviewRefreshResult, ReleaseReviewRefreshError> {
    validate_refresh_source_identity(existing, refreshed)?;

    let previous_generation = producer(existing, PRODUCER_ID)?.generation.clone();
    let refreshed_producer = producer(refreshed, PRODUCER_ID)?.clone();
    let refreshed_generation = refreshed_producer.generation.clone();

    let existing_owned_units = existing
        .review_units
        .iter()
        .filter(|unit| is_owned_producer(&unit.producer_id))
        .map(|unit| (unit.id.clone(), unit.clone()))
        .collect::<BTreeMap<_, _>>();
    let refreshed_active_units = refreshed
        .review_units
        .iter()
        .filter(|unit| unit.producer_id == PRODUCER_ID)
        .map(|unit| (unit.id.clone(), unit.clone()))
        .collect::<BTreeMap<_, _>>();
    if refreshed_active_units.len() != refreshed.review_units.len() {
        return Err(ReleaseReviewRefreshError::new(
            "refresh.fresh-producer-ownership-invalid",
            "fresh materialization contains a review unit not owned by the pinned Git producer",
        ));
    }

    let existing_comments = existing
        .review_session
        .comments
        .iter()
        .map(|comment| (comment.id.clone(), comment.clone()))
        .collect::<BTreeMap<_, _>>();
    validate_owned_comment_pairs(&existing_owned_units, &existing_comments)?;
    let refreshed_comments = refreshed
        .review_session
        .comments
        .iter()
        .filter(|comment| comment.provenance.producer == PRODUCER_ID)
        .map(|comment| (comment.id.clone(), comment.clone()))
        .collect::<BTreeMap<_, _>>();
    validate_owned_comment_pairs(&refreshed_active_units, &refreshed_comments)?;

    let active_units_added = refreshed_active_units
        .keys()
        .filter(|id| !existing_owned_units.contains_key(*id))
        .count();
    let active_units_unchanged = refreshed_active_units
        .iter()
        .filter(|(id, fresh)| {
            existing_owned_units
                .get(*id)
                .is_some_and(|old| equivalent_active_unit(old, fresh))
        })
        .count();
    let active_units_updated = refreshed_active_units
        .len()
        .saturating_sub(active_units_added + active_units_unchanged);

    let retired_ids = existing_owned_units
        .keys()
        .filter(|id| !refreshed_active_units.contains_key(*id))
        .cloned()
        .collect::<Vec<_>>();
    let retired_units_added = retired_ids
        .iter()
        .filter(|id| {
            existing_owned_units
                .get(*id)
                .is_some_and(|unit| unit.producer_id == PRODUCER_ID)
        })
        .count();
    let retired_units_carried = retired_ids.len().saturating_sub(retired_units_added);
    let retirement_input_fingerprint =
        retirement_input_fingerprint(&refreshed_generation, &retired_ids);
    let retirement_generation =
        format!("git-materializer-retired-v1:{retirement_input_fingerprint}");

    let mut review_units = existing
        .review_units
        .iter()
        .filter(|unit| !is_owned_producer(&unit.producer_id))
        .cloned()
        .collect::<Vec<_>>();
    review_units.extend(refreshed_active_units.values().cloned());
    for id in &retired_ids {
        let mut unit = existing_owned_units
            .get(id)
            .ok_or_else(|| {
                ReleaseReviewRefreshError::new(
                    "refresh.retired-unit-missing",
                    format!("retired review unit '{id}' disappeared during reconciliation"),
                )
            })?
            .clone();
        unit.surface_kind = SurfaceKindV1::Unsupported;
        append_limitation(&mut unit.limitation, RETIRED_UNIT_LIMITATION);
        RETIRED_PRODUCER_ID.clone_into(&mut unit.producer_id);
        unit.producer_generation.clone_from(&retirement_generation);
        review_units.push(unit);
    }

    let human_comments = existing
        .review_session
        .comments
        .iter()
        .filter(|comment| !is_owned_producer(&comment.provenance.producer))
        .cloned()
        .collect::<Vec<_>>();
    let human_comments_preserved = human_comments.len();
    let mut comments_by_id = BTreeMap::new();
    for comment in human_comments {
        comments_by_id.insert(comment.id.clone(), comment);
    }
    for comment in refreshed_comments.into_values() {
        insert_comment_without_collision(&mut comments_by_id, comment)?;
    }
    for id in &retired_ids {
        let old = existing_comments
            .get(&generated_comment_id(id))
            .ok_or_else(|| {
                ReleaseReviewRefreshError::new(
                    "refresh.generated-comment-missing",
                    format!("retired review unit '{id}' has no generated comment"),
                )
            })?;
        let retired = retired_comment(old, id, &retirement_generation);
        insert_comment_without_collision(&mut comments_by_id, retired)?;
    }

    let mut named_queries = existing
        .named_queries
        .iter()
        .map(|query| (query.id.clone(), query.clone()))
        .collect::<BTreeMap<_, _>>();
    let named_queries_preserved = named_queries.len();
    for query in &refreshed.named_queries {
        named_queries
            .entry(query.id.clone())
            .or_insert_with(|| query.clone());
    }

    let mut style_rules = existing
        .review_session
        .style_rules
        .iter()
        .map(|rule| (rule.id.clone(), rule.clone()))
        .collect::<BTreeMap<_, _>>();
    for rule in &refreshed.review_session.style_rules {
        style_rules
            .entry(rule.id.clone())
            .or_insert_with(|| rule.clone());
    }

    let mut producer_generations = existing
        .producer_generations
        .iter()
        .filter(|generation| !is_owned_producer(&generation.producer_id))
        .cloned()
        .collect::<Vec<_>>();
    producer_generations.push(refreshed_producer);
    if !retired_ids.is_empty() {
        producer_generations.push(ProducerGenerationV1 {
            producer_id: RETIRED_PRODUCER_ID.to_owned(),
            generation: retirement_generation,
            input_fingerprint: retirement_input_fingerprint,
            output_fingerprint: "0".repeat(64),
        });
    }

    let mut document = refreshed.clone();
    document
        .review_session
        .id
        .clone_from(&existing.review_session.id);
    document
        .review_session
        .title
        .clone_from(&existing.review_session.title);
    document.review_session.comments = comments_by_id.into_values().collect();
    document.review_session.style_rules = style_rules.into_values().collect();
    document
        .review_session
        .completion_policy
        .clone_from(&existing.review_session.completion_policy);
    document.review_units = review_units;
    document
        .selector_bindings
        .clone_from(&existing.selector_bindings);
    document
        .migration_reports
        .clone_from(&existing.migration_reports);
    document.named_queries = named_queries.into_values().collect();
    document.resume_state.clone_from(&existing.resume_state);
    document.producer_generations = producer_generations;
    document
        .completion_attestations
        .clone_from(&existing.completion_attestations);

    refresh_owned_output_fingerprints(&mut document)?;
    let canonical = crate::release_review_v1::to_canonical_json(&document).map_err(|error| {
        ReleaseReviewRefreshError::new(
            "refresh.merged-document-invalid",
            format!("preserved review progress is incompatible with refreshed output: {error:#}"),
        )
    })?;
    let document = crate::release_review_v1::parse(&canonical).map_err(|error| {
        ReleaseReviewRefreshError::new(
            "refresh.merged-document-invalid",
            format!("canonical refreshed review could not be parsed: {error:#}"),
        )
    })?;

    Ok(ReleaseReviewRefreshResult {
        reconciliation: ReleaseReviewRefreshReconciliation {
            previous_generation,
            refreshed_generation,
            active_units_added,
            active_units_updated,
            active_units_unchanged,
            retired_units_added,
            retired_units_carried,
            generated_comments: document
                .review_session
                .comments
                .iter()
                .filter(|comment| is_owned_producer(&comment.provenance.producer))
                .count(),
            human_comments_preserved,
            selector_bindings_preserved: existing.selector_bindings.len(),
            migration_reports_preserved: existing.migration_reports.len(),
            named_queries_preserved,
            deferred_units_preserved: existing.resume_state.deferred_unit_ids.len(),
            completion_attestations_preserved: existing.completion_attestations.len(),
        },
        document,
    })
}

fn validate_refresh_source_identity(
    existing: &ReleaseReviewDocumentV1,
    refreshed: &ReleaseReviewDocumentV1,
) -> Result<(), ReleaseReviewRefreshError> {
    crate::release_review_v1::to_canonical_json(existing).map_err(|error| {
        ReleaseReviewRefreshError::new(
            "refresh.existing-document-invalid",
            format!("existing review document is invalid: {error:#}"),
        )
    })?;
    crate::release_review_v1::to_canonical_json(refreshed).map_err(|error| {
        ReleaseReviewRefreshError::new(
            "refresh.fresh-document-invalid",
            format!("fresh producer document is invalid: {error:#}"),
        )
    })?;
    if existing.repository_bindings != refreshed.repository_bindings {
        return Err(ReleaseReviewRefreshError::new(
            "refresh.repository-binding-divergence",
            "the fresh producer did not reproduce the existing repository, before commit, candidate commit, trees, lane, and evidence-path binding exactly",
        ));
    }
    if existing.review_session.id != refreshed.review_session.id {
        return Err(ReleaseReviewRefreshError::new(
            "refresh.session-identity-divergence",
            "the fresh producer did not reproduce the pinned review-session identity",
        ));
    }
    if existing.review_session.coordinate_system != refreshed.review_session.coordinate_system {
        return Err(ReleaseReviewRefreshError::new(
            "refresh.coordinate-system-divergence",
            "the fresh producer uses a different coordinate system",
        ));
    }
    if existing.review_session.revision_lanes != refreshed.review_session.revision_lanes
        || existing.corpus_documents != refreshed.corpus_documents
    {
        return Err(ReleaseReviewRefreshError::new(
            "refresh.source-snapshot-divergence",
            "the fresh producer did not reproduce the byte-pinned source snapshots; refresh cannot safely retarget human selections",
        ));
    }
    Ok(())
}

fn producer<'a>(
    document: &'a ReleaseReviewDocumentV1,
    producer_id: &str,
) -> Result<&'a ProducerGenerationV1, ReleaseReviewRefreshError> {
    document
        .producer_generations
        .iter()
        .find(|generation| generation.producer_id == producer_id)
        .ok_or_else(|| {
            ReleaseReviewRefreshError::new(
                "refresh.producer-generation-missing",
                format!("review document has no generation for producer '{producer_id}'"),
            )
        })
}

fn validate_owned_comment_pairs(
    units: &BTreeMap<String, ReviewUnitV1>,
    comments: &BTreeMap<String, CommentV2>,
) -> Result<(), ReleaseReviewRefreshError> {
    let expected = units
        .keys()
        .map(|id| generated_comment_id(id))
        .collect::<BTreeSet<_>>();
    for id in &expected {
        let Some(comment) = comments.get(id) else {
            return Err(ReleaseReviewRefreshError::new(
                "refresh.generated-comment-missing",
                format!("producer-owned review unit has no generated comment '{id}'"),
            ));
        };
        if !is_owned_producer(&comment.provenance.producer) {
            return Err(ReleaseReviewRefreshError::new(
                "refresh.generated-comment-ownership-conflict",
                format!("generated comment ID '{id}' is now owned by another producer"),
            ));
        }
    }
    for comment in comments
        .values()
        .filter(|comment| is_owned_producer(&comment.provenance.producer))
    {
        if !expected.contains(&comment.id) {
            return Err(ReleaseReviewRefreshError::new(
                "refresh.generated-comment-orphan",
                format!(
                    "producer-owned generated comment '{}' has no matching review unit",
                    comment.id
                ),
            ));
        }
    }
    Ok(())
}

fn insert_comment_without_collision(
    comments: &mut BTreeMap<String, CommentV2>,
    comment: CommentV2,
) -> Result<(), ReleaseReviewRefreshError> {
    if comments.contains_key(&comment.id) {
        return Err(ReleaseReviewRefreshError::new(
            "refresh.comment-id-collision",
            format!(
                "preserved human comment collides with generated comment ID '{}'",
                comment.id
            ),
        ));
    }
    comments.insert(comment.id.clone(), comment);
    Ok(())
}

fn equivalent_active_unit(old: &ReviewUnitV1, fresh: &ReviewUnitV1) -> bool {
    let mut old = old.clone();
    old.producer_id.clone_from(&fresh.producer_id);
    old.producer_generation
        .clone_from(&fresh.producer_generation);
    old == *fresh
}

fn retirement_input_fingerprint(refreshed_generation: &str, retired_ids: &[String]) -> String {
    let mut owned = vec![refreshed_generation.as_bytes().to_vec()];
    for id in retired_ids {
        owned.push(id.as_bytes().to_vec());
    }
    stable_hash(&owned.iter().map(Vec::as_slice).collect::<Vec<_>>())
}

fn retired_comment(old: &CommentV2, unit_id: &str, generation: &str) -> CommentV2 {
    let mut comment = old.clone();
    comment.text = format!(
        "#release-change #producer-retired #needs-change\nGenerated review unit `{unit_id}` is no longer emitted by the pinned producer generation `{generation}`. It remains explicit and blocking so prior human progress cannot disappear silently."
    );
    comment.provenance = ProvenanceV1 {
        kind: "generated".to_owned(),
        producer: RETIRED_PRODUCER_ID.to_owned(),
        version: generation.to_owned(),
        parent_comment_ids: Vec::new(),
    };
    comment
}

fn refresh_owned_output_fingerprints(
    document: &mut ReleaseReviewDocumentV1,
) -> Result<(), ReleaseReviewRefreshError> {
    for generation in &mut document.producer_generations {
        if is_owned_producer(&generation.producer_id) {
            generation.output_fingerprint = "0".repeat(64);
        }
    }
    let projected = crate::release_review_v1::to_canonical_json(document).map_err(|error| {
        ReleaseReviewRefreshError::new(
            "refresh.output-fingerprint-failed",
            format!("could not canonicalize refreshed output projection: {error:#}"),
        )
    })?;
    let fingerprint = sha256(projected.as_bytes());
    for generation in &mut document.producer_generations {
        if is_owned_producer(&generation.producer_id) {
            generation.output_fingerprint.clone_from(&fingerprint);
        }
    }
    Ok(())
}

pub(crate) fn generated_comment_id(unit_id: &str) -> String {
    stable_id("release-change-comment", &[unit_id.as_bytes()])
}

fn is_owned_producer(producer_id: &str) -> bool {
    matches!(producer_id, PRODUCER_ID | RETIRED_PRODUCER_ID)
}

/// Reconcile the serialized review domain with the raw Git producer counts.
///
/// # Errors
///
/// Returns an error when unit identities are duplicated or do not map one to
/// one, or when a side-qualified Git path has no corpus representation.
pub fn reconcile_materialization(
    domain: &GitReviewDomain,
    document: &ReleaseReviewDocumentV1,
) -> eyre::Result<ReleaseReviewMaterializationReconciliation> {
    let git_units = domain
        .units
        .iter()
        .map(|unit| unit.unit_id.as_str())
        .collect::<BTreeSet<_>>();
    let review_units = document
        .review_units
        .iter()
        .map(|unit| unit.id.as_str())
        .collect::<BTreeSet<_>>();
    if git_units.len() != domain.units.len() || review_units.len() != document.review_units.len() {
        return Err(eyre!("duplicate Git or materialized review-unit identity"));
    }

    let corpus_addresses = document
        .corpus_documents
        .iter()
        .map(|corpus| {
            (
                (corpus.snapshot_side, corpus.path.as_str()),
                corpus.materialization,
            )
        })
        .collect::<BTreeMap<_, _>>();
    let mut represented = 0;
    let mut explicitly_unsupported = 0;
    for path in &domain.reconciliation.paths {
        let (materialized_path, path_limitation) = portable_path(&path.path);
        let side = match path.side {
            crate::release_review_git::GitRevisionSide::Before => SnapshotSideV1::Before,
            crate::release_review_git::GitRevisionSide::Candidate => SnapshotSideV1::After,
        };
        let Some(materialization) = corpus_addresses.get(&(side, materialized_path.as_str()))
        else {
            return Err(eyre!(
                "side-qualified Git path has no corpus representation: {:?} {}",
                side,
                path.path.display_lossy()
            ));
        };
        if path_limitation.is_some()
            || *materialization != MaterializationV1::Complete
            || matches!(
                path.disposition,
                crate::release_review_git::GitReconciliationDisposition::ExplicitlyUnsupported
            )
        {
            explicitly_unsupported += 1;
        } else {
            represented += 1;
        }
    }

    Ok(ReleaseReviewMaterializationReconciliation {
        raw_change_count: domain.reconciliation.raw_change_count,
        raw_side_path_count: domain.reconciliation.raw_side_path_count,
        git_unit_count: domain.units.len(),
        review_unit_count: document.review_units.len(),
        represented_corpus_side_path_count: represented,
        explicitly_unsupported_corpus_side_path_count: explicitly_unsupported,
        complete: domain.reconciliation.complete
            && domain.reconciliation.unreconciled_side_path_count == 0
            && git_units == review_units
            && represented + explicitly_unsupported == domain.reconciliation.raw_side_path_count,
    })
}

fn validate_inputs(
    domain: &GitReviewDomain,
    config: &ReleaseReviewMaterializeConfig,
) -> eyre::Result<()> {
    if domain.schema != crate::release_review_git::GIT_DOMAIN_SCHEMA {
        return Err(eyre!("unsupported Git review domain schema"));
    }
    if !domain.reconciliation.complete || domain.reconciliation.unreconciled_side_path_count != 0 {
        return Err(eyre!("Git review domain reconciliation is incomplete"));
    }
    if domain.reconciliation.raw_change_count != domain.changes.len() {
        return Err(eyre!("Git raw-change reconciliation count is inconsistent"));
    }
    let domain_root = std::fs::canonicalize(&domain.repository_root)
        .wrap_err("could not canonicalize Git-domain repository root")?;
    let configured_root = std::fs::canonicalize(&config.repository_root)
        .wrap_err("could not canonicalize configured repository root")?;
    if domain_root != configured_root {
        return Err(eyre!("configured repository root differs from Git domain"));
    }
    if config.before_revision != domain.before.commit_id {
        return Err(eyre!(
            "configured before revision differs from pinned Git domain"
        ));
    }
    if config.candidate_revision != domain.candidate.commit_id {
        return Err(eyre!(
            "configured candidate revision differs from pinned Git domain"
        ));
    }
    for (name, value) in [
        ("lane id", config.lane_id.as_str()),
        ("repository id", config.repository_id.as_str()),
        ("root hint", config.root_hint.as_str()),
        ("before label", config.before_label.as_str()),
        ("after label", config.after_label.as_str()),
        ("review evidence path", config.review_evidence_path.as_str()),
    ] {
        if value.trim().is_empty() {
            return Err(eyre!("{name} must not be blank"));
        }
    }
    let unit_change_ids = domain
        .changes
        .iter()
        .flat_map(|change| {
            change
                .unit_ids
                .iter()
                .map(move |unit| (unit, &change.change_id))
        })
        .collect::<BTreeMap<_, _>>();
    for unit in &domain.units {
        if unit_change_ids.get(&unit.unit_id) != Some(&&unit.change_id) {
            return Err(eyre!(
                "Git unit '{}' is absent from its owning raw change",
                unit.unit_id
            ));
        }
    }
    Ok(())
}

fn materialize_side_documents(
    domain: &GitReviewDomain,
    config: &ReleaseReviewMaterializeConfig,
) -> eyre::Result<BTreeMap<(SnapshotSideV1, Vec<u8>), SideDocument>> {
    let mut documents = BTreeMap::new();
    for change in &domain.changes {
        if let Some(path) = change.old_path.as_ref() {
            insert_side_document(
                &mut documents,
                domain,
                config,
                SnapshotSideV1::Before,
                path,
                change.old_blob_id.as_deref(),
                change.numstat.as_ref().is_some_and(|value| value.binary),
            )?;
        }
        if let Some(path) = change.new_path.as_ref() {
            insert_side_document(
                &mut documents,
                domain,
                config,
                SnapshotSideV1::After,
                path,
                change.new_blob_id.as_deref(),
                change.numstat.as_ref().is_some_and(|value| value.binary),
            )?;
        }
    }
    Ok(documents)
}

fn insert_side_document(
    documents: &mut BTreeMap<(SnapshotSideV1, Vec<u8>), SideDocument>,
    domain: &GitReviewDomain,
    config: &ReleaseReviewMaterializeConfig,
    side: SnapshotSideV1,
    path: &GitPath,
    blob_id: Option<&str>,
    force_binary: bool,
) -> eyre::Result<()> {
    let key = (side, path.as_bytes().to_vec());
    if let Some(existing) = documents.get_mut(&key) {
        let expected_object = blob_id.unwrap_or("none");
        if existing
            .source_locator
            .starts_with(&format!("git-object:{expected_object};"))
        {
            if force_binary {
                mark_binary(existing);
            }
            return Ok(());
        }
        return Err(eyre!(
            "one side-qualified Git path resolved to conflicting object IDs"
        ));
    }
    let commit_id = match side {
        SnapshotSideV1::Before => &domain.before.commit_id,
        SnapshotSideV1::After => &domain.candidate.commit_id,
    };
    let (portable_path, path_limitation) = portable_path(path);
    let path_hex = hex(path.as_bytes());
    let mut limitation = path_limitation;
    let (raw_sha256, text, materialization, locator_object) = if let Some(blob_id) = blob_id {
        materialize_blob(
            &config.repository_root,
            blob_id,
            limitation.is_some(),
            &mut limitation,
        )?
    } else {
        append_limitation(
            &mut limitation,
            "Git side has no blob object and is retained as missing evidence",
        );
        (
            sha256(&[]),
            None,
            MaterializationV1::Missing,
            "none".to_owned(),
        )
    };
    let revision_hash = stable_hash(&[
        commit_id.as_bytes(),
        path.as_bytes(),
        locator_object.as_bytes(),
        raw_sha256.as_bytes(),
    ]);
    let mut document = SideDocument {
        side,
        path: portable_path,
        document_revision_id: format!("git-document:sha256:{revision_hash}"),
        corpus_id: format!("git-corpus:sha256:{revision_hash}"),
        sha256: raw_sha256,
        source_owner: format!("git:{commit_id}"),
        source_locator: format!(
            "git-object:{locator_object};commit:{commit_id};path-bytes:{path_hex}"
        ),
        materialization,
        text,
        limitation,
    };
    if force_binary {
        mark_binary(&mut document);
    }
    documents.insert(key, document);
    Ok(())
}

fn mark_binary(document: &mut SideDocument) {
    document.text = None;
    if document.materialization == MaterializationV1::Complete {
        document.materialization = MaterializationV1::Partial;
    }
    append_limitation(
        &mut document.limitation,
        "Git classified this blob as binary; bytes are retained as hash-only evidence",
    );
}

fn materialize_blob(
    repository_root: &Path,
    blob_id: &str,
    path_is_limited: bool,
    limitation: &mut Option<String>,
) -> eyre::Result<(String, Option<String>, MaterializationV1, String)> {
    let Some(bytes) = read_blob(repository_root, blob_id)? else {
        append_limitation(
            limitation,
            "Git object could not be read as a blob and is retained as missing evidence",
        );
        return Ok((
            sha256(&[]),
            None,
            MaterializationV1::Missing,
            blob_id.to_owned(),
        ));
    };
    let raw_sha256 = sha256(&bytes);
    let Ok(text) = String::from_utf8(bytes) else {
        append_limitation(
            limitation,
            "Git blob is not valid UTF-8 and is retained as hash-only evidence",
        );
        return Ok((
            raw_sha256,
            None,
            MaterializationV1::Partial,
            blob_id.to_owned(),
        ));
    };
    Ok(if path_is_limited {
        (
            raw_sha256,
            None,
            MaterializationV1::Partial,
            blob_id.to_owned(),
        )
    } else {
        (
            raw_sha256,
            Some(text),
            MaterializationV1::Complete,
            blob_id.to_owned(),
        )
    })
}

fn materialize_unit(
    unit: &GitReviewUnit,
    change: &GitDomainChange,
    config: &ReleaseReviewMaterializeConfig,
    documents: &BTreeMap<(SnapshotSideV1, Vec<u8>), SideDocument>,
    producer_generation: &str,
) -> eyre::Result<ReviewUnitV1> {
    let before = side_document(documents, SnapshotSideV1::Before, unit.old_path.as_ref());
    let after = side_document(documents, SnapshotSideV1::After, unit.new_path.as_ref());
    let mut limitation = before
        .and_then(|document| document.limitation.clone())
        .or_else(|| after.and_then(|document| document.limitation.clone()));
    if let (Some(before_limitation), Some(after_limitation)) = (
        before.and_then(|document| document.limitation.as_deref()),
        after.and_then(|document| document.limitation.as_deref()),
    ) && before_limitation != after_limitation
    {
        limitation = Some(format!("{before_limitation}; {after_limitation}"));
    }

    let (mut before_ranges, mut after_ranges, mut surface_kind) = match &unit.kind {
        GitReviewUnitKind::TextHunk(hunk) => {
            append_limitation(
                &mut limitation,
                "Structural analysis was not supplied; this is a bounded zero-context Git hunk fallback",
            );
            (
                text_hunk_range(before, hunk.before_start, hunk.before_lines)?,
                text_hunk_range(after, hunk.candidate_start, hunk.candidate_lines)?,
                SurfaceKindV1::DiffHunk,
            )
        }
        GitReviewUnitKind::FileChange => {
            append_limitation(
                &mut limitation,
                "Structural analysis was not supplied; this is a whole-file Git fallback",
            );
            (
                whole_document_range(before),
                whole_document_range(after),
                SurfaceKindV1::File,
            )
        }
        GitReviewUnitKind::BinaryFile => {
            append_limitation(
                &mut limitation,
                "Binary Git content is represented by hashes and explicit tombstone sides",
            );
            (Vec::new(), Vec::new(), SurfaceKindV1::Binary)
        }
        GitReviewUnitKind::ExplicitlyUnsupported { reason } => {
            append_limitation(&mut limitation, reason);
            (Vec::new(), Vec::new(), SurfaceKindV1::Unsupported)
        }
    };
    if matches!(surface_kind, SurfaceKindV1::DiffHunk | SurfaceKindV1::File)
        && before
            .into_iter()
            .chain(after)
            .any(|document| document.materialization != MaterializationV1::Complete)
    {
        append_limitation(
            &mut limitation,
            "Textual review surface is unavailable under the frozen UTF-8 corpus contract",
        );
        before_ranges.clear();
        after_ranges.clear();
        surface_kind = SurfaceKindV1::Unsupported;
    }
    if surface_kind == SurfaceKindV1::Unsupported && limitation.is_none() {
        limitation = Some("Git review unit is explicitly unsupported".to_owned());
    }

    Ok(ReviewUnitV1 {
        // Preserving the producer unit ID proves a literal one-to-one mapping.
        id: unit.unit_id.clone(),
        lane_id: config.lane_id.clone(),
        operation: operation(change),
        path_before: before.map(|document| document.path.clone()),
        path_after: after.map(|document| document.path.clone()),
        before_document_revision_id: before.map(|document| document.document_revision_id.clone()),
        after_document_revision_id: after.map(|document| document.document_revision_id.clone()),
        before_ranges,
        after_ranges,
        language: language(
            after
                .map(|document| document.path.as_str())
                .or_else(|| before.map(|document| document.path.as_str()))
                .unwrap_or("unknown"),
        ),
        surface_kind,
        semantic_key: None,
        limitation,
        producer_id: PRODUCER_ID.to_owned(),
        producer_generation: producer_generation.to_owned(),
    })
}

fn generated_comment(
    unit: &ReviewUnitV1,
    documents: &BTreeMap<(SnapshotSideV1, Vec<u8>), SideDocument>,
    producer_generation: &str,
) -> eyre::Result<CommentV2> {
    let mut rules = Vec::new();
    add_rules_for_ranges(
        &mut rules,
        unit.before_document_revision_id.as_deref(),
        &unit.before_ranges,
        documents,
    )?;
    add_rules_for_ranges(
        &mut rules,
        unit.after_document_revision_id.as_deref(),
        &unit.after_ranges,
        documents,
    )?;
    if rules.is_empty() {
        let revision_id = unit
            .after_document_revision_id
            .as_deref()
            .or(unit.before_document_revision_id.as_deref())
            .ok_or_else(|| eyre!("review unit '{}' has no corpus side", unit.id))?;
        let document = find_document_by_revision(documents, revision_id)
            .ok_or_else(|| eyre!("review unit '{}' has unknown corpus side", unit.id))?;
        rules.push(SelectionRuleV1::LiteralUtf8Range {
            document_revision_id: revision_id.to_owned(),
            start_byte: 0,
            end_byte: 0,
            document_sha256: document.sha256.clone(),
            selected_text_sha256: sha256(&[]),
        });
    }
    let selection_rule = if rules.len() == 1 {
        rules.remove(0)
    } else {
        SelectionRuleV1::Union { rules }
    };
    let operation = operation_name(unit.operation);
    let path = unit
        .path_after
        .as_deref()
        .or(unit.path_before.as_deref())
        .unwrap_or("<unsupported-path>");
    let source_snapshots = [
        unit.before_document_revision_id.as_deref(),
        unit.after_document_revision_id.as_deref(),
    ]
    .into_iter()
    .flatten()
    .collect::<Vec<_>>()
    .join(",");
    let comment_id = stable_id("release-change-comment", &[unit.id.as_bytes()]);
    Ok(CommentV2 {
        id: comment_id,
        text: format!(
            "#release-change #{operation}\nGenerated review unit `{}` for `{path}` by rule `{PRODUCER_RULE_ID}` at generation `{producer_generation}` from `{source_snapshots}`.",
            unit.id,
        ),
        provenance: ProvenanceV1 {
            kind: "generated".to_owned(),
            producer: PRODUCER_ID.to_owned(),
            version: producer_generation.to_owned(),
            parent_comment_ids: Vec::new(),
        },
        target: CommentTargetV2::CommittedSelection {
            selection_rule,
            candidate_promotion: None,
        },
        forbidden_authoritative_tags: None,
    })
}

fn compare_review_units(left: &ReviewUnitV1, right: &ReviewUnitV1) -> std::cmp::Ordering {
    let left_path = left
        .path_after
        .as_deref()
        .or(left.path_before.as_deref())
        .unwrap_or("");
    let right_path = right
        .path_after
        .as_deref()
        .or(right.path_before.as_deref())
        .unwrap_or("");
    let left_start = left
        .after_ranges
        .first()
        .or_else(|| left.before_ranges.first())
        .map_or(0, |range| range.start_byte);
    let right_start = right
        .after_ranges
        .first()
        .or_else(|| right.before_ranges.first())
        .map_or(0, |range| range.start_byte);
    (
        left.lane_id.as_str(),
        left_path,
        operation_name(left.operation),
        left_start,
        left.id.as_str(),
    )
        .cmp(&(
            right.lane_id.as_str(),
            right_path,
            operation_name(right.operation),
            right_start,
            right.id.as_str(),
        ))
}

fn add_rules_for_ranges(
    rules: &mut Vec<SelectionRuleV1>,
    revision_id: Option<&str>,
    ranges: &[Utf8RangeV1],
    documents: &BTreeMap<(SnapshotSideV1, Vec<u8>), SideDocument>,
) -> eyre::Result<()> {
    let Some(revision_id) = revision_id else {
        return Ok(());
    };
    let document = find_document_by_revision(documents, revision_id)
        .ok_or_else(|| eyre!("unknown corpus revision '{revision_id}'"))?;
    let text = document.text.as_deref();
    for range in ranges {
        let selected = text
            .and_then(|text| text.as_bytes().get(range.start_byte..range.end_byte))
            .ok_or_else(|| eyre!("review range is outside embedded UTF-8 text"))?;
        rules.push(SelectionRuleV1::LiteralUtf8Range {
            document_revision_id: revision_id.to_owned(),
            start_byte: range.start_byte,
            end_byte: range.end_byte,
            document_sha256: document.sha256.clone(),
            selected_text_sha256: sha256(selected),
        });
    }
    Ok(())
}

fn find_document_by_revision<'a>(
    documents: &'a BTreeMap<(SnapshotSideV1, Vec<u8>), SideDocument>,
    revision_id: &str,
) -> Option<&'a SideDocument> {
    documents
        .values()
        .find(|document| document.document_revision_id == revision_id)
}

fn side_document<'a>(
    documents: &'a BTreeMap<(SnapshotSideV1, Vec<u8>), SideDocument>,
    side: SnapshotSideV1,
    path: Option<&GitPath>,
) -> Option<&'a SideDocument> {
    path.and_then(|path| documents.get(&(side, path.as_bytes().to_vec())))
}

fn text_hunk_range(
    document: Option<&SideDocument>,
    start_line: u64,
    line_count: u64,
) -> eyre::Result<Vec<Utf8RangeV1>> {
    let Some(text) = document.and_then(|document| document.text.as_deref()) else {
        return Ok(Vec::new());
    };
    let start_line = usize::try_from(start_line).wrap_err("hunk start line exceeds usize")?;
    let line_count = usize::try_from(line_count).wrap_err("hunk line count exceeds usize")?;
    let starts = line_starts(text);
    let line_index = start_line
        .saturating_sub(1)
        .min(starts.len().saturating_sub(1));
    let start_byte = starts[line_index];
    let end_byte = if line_count == 0 {
        start_byte
    } else {
        starts
            .get(line_index.saturating_add(line_count))
            .copied()
            .unwrap_or(text.len())
    };
    Ok(vec![Utf8RangeV1 {
        start_byte,
        end_byte,
    }])
}

fn whole_document_range(document: Option<&SideDocument>) -> Vec<Utf8RangeV1> {
    document
        .and_then(|document| document.text.as_ref())
        .map(|text| {
            vec![Utf8RangeV1 {
                start_byte: 0,
                end_byte: text.len(),
            }]
        })
        .unwrap_or_default()
}

fn line_starts(text: &str) -> Vec<usize> {
    let mut starts = vec![0];
    starts.extend(
        text.bytes()
            .enumerate()
            .filter_map(|(index, byte)| (byte == b'\n').then_some(index + 1)),
    );
    starts
}

fn operation(change: &GitDomainChange) -> ChangeOperationV1 {
    match change.status.kind {
        GitChangeKind::Added => ChangeOperationV1::Added,
        GitChangeKind::Deleted => ChangeOperationV1::Deleted,
        GitChangeKind::Renamed => ChangeOperationV1::Renamed,
        GitChangeKind::Copied => ChangeOperationV1::Copied,
        GitChangeKind::TypeChanged => ChangeOperationV1::TypeChanged,
        GitChangeKind::Unsupported if change.old_path.is_none() => ChangeOperationV1::Added,
        GitChangeKind::Unsupported if change.new_path.is_none() => ChangeOperationV1::Deleted,
        GitChangeKind::Modified | GitChangeKind::Unsupported => ChangeOperationV1::Modified,
    }
}

const fn operation_name(operation: ChangeOperationV1) -> &'static str {
    match operation {
        ChangeOperationV1::Added => "added",
        ChangeOperationV1::Deleted => "deleted",
        ChangeOperationV1::Modified => "modified",
        ChangeOperationV1::Renamed => "renamed",
        ChangeOperationV1::Copied => "copied",
        ChangeOperationV1::TypeChanged => "type-changed",
    }
}

fn language(path: &str) -> String {
    Path::new(path)
        .extension()
        .and_then(|extension| extension.to_str())
        .map(str::to_ascii_lowercase)
        .filter(|extension| !extension.is_empty())
        .unwrap_or_else(|| "text".to_owned())
}

fn portable_path(path: &GitPath) -> (String, Option<String>) {
    if let Some(path) = path.as_utf8()
        && !path.is_empty()
        && !path.starts_with('/')
        && !path.contains('\\')
        && !path.split('/').any(|component| component == "..")
    {
        return (path.to_owned(), None);
    }
    (
        format!(
            ".sfm-review/unsupported-path/bytes-{}",
            hex(path.as_bytes())
        ),
        Some(
            "Git path is not representable as a normalized repository-relative UTF-8 path; raw bytes are retained in source_locator"
                .to_owned(),
        ),
    )
}

fn read_blob(repository_root: &Path, blob_id: &str) -> eyre::Result<Option<Vec<u8>>> {
    let output = Command::new("git")
        .arg("-C")
        .arg(repository_root)
        .args(["cat-file", "blob", blob_id])
        .env("LC_ALL", "C")
        .env("LANG", "C")
        .env("GIT_NO_REPLACE_OBJECTS", "1")
        .output()
        .wrap_err("could not launch Git to read a pinned blob")?;
    Ok(output.status.success().then_some(output.stdout))
}

fn input_fingerprint(domain: &GitReviewDomain, config: &ReleaseReviewMaterializeConfig) -> String {
    let mut fields = vec![
        MATERIALIZER_VERSION.as_bytes(),
        config.lane_id.as_bytes(),
        config.repository_id.as_bytes(),
        config.root_hint.as_bytes(),
        config.before_label.as_bytes(),
        domain.before.commit_id.as_bytes(),
        domain.before.tree_id.as_bytes(),
        config.after_label.as_bytes(),
        domain.candidate.commit_id.as_bytes(),
        domain.candidate.tree_id.as_bytes(),
        config.review_evidence_path.as_bytes(),
    ];
    let owned = domain_fingerprint_fields(domain);
    fields.extend(owned.iter().map(Vec::as_slice));
    stable_hash(&fields)
}

fn domain_fingerprint_fields(domain: &GitReviewDomain) -> Vec<Vec<u8>> {
    let mut fields = Vec::new();
    fields.push(
        domain
            .reconciliation
            .raw_change_count
            .to_string()
            .into_bytes(),
    );
    fields.push(
        domain
            .reconciliation
            .raw_side_path_count
            .to_string()
            .into_bytes(),
    );
    for change in &domain.changes {
        fields.push(change.change_id.as_bytes().to_vec());
        fields.push(change.status.raw.as_bytes().to_vec());
        fields.push(
            change
                .old_path
                .as_ref()
                .map_or_else(Vec::new, |path| path.as_bytes().to_vec()),
        );
        fields.push(
            change
                .new_path
                .as_ref()
                .map_or_else(Vec::new, |path| path.as_bytes().to_vec()),
        );
        fields.push(
            change
                .old_blob_id
                .as_ref()
                .map_or_else(Vec::new, |value| value.as_bytes().to_vec()),
        );
        fields.push(
            change
                .new_blob_id
                .as_ref()
                .map_or_else(Vec::new, |value| value.as_bytes().to_vec()),
        );
        for unit_id in &change.unit_ids {
            fields.push(unit_id.as_bytes().to_vec());
        }
    }
    fields
}

fn stable_id(domain: &str, fields: &[&[u8]]) -> String {
    format!("{domain}:sha256:{}", stable_hash(fields))
}

fn stable_hash(fields: &[&[u8]]) -> String {
    let mut hasher = Sha256::new();
    for field in fields {
        hasher.update(field.len().to_le_bytes());
        hasher.update(field);
    }
    format!("{:x}", hasher.finalize())
}

fn sha256(bytes: &[u8]) -> String {
    review_session_v1::sha256(bytes)
}

fn hex(bytes: &[u8]) -> String {
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        use std::fmt::Write as _;
        let _ = write!(output, "{byte:02x}");
    }
    output
}

fn append_limitation(target: &mut Option<String>, value: &str) {
    match target {
        Some(current) if !current.contains(value) => {
            current.push_str("; ");
            current.push_str(value);
        }
        None => *target = Some(value.to_owned()),
        Some(_) => {}
    }
}
