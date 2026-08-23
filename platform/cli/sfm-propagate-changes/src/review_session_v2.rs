//! Canonical `sfm.review-session/2` model and committed-selection evaluator.

use crate::review_session_v1;
use crate::review_session_v1::CommentStyleRuleV1;
use crate::review_session_v1::CompletionPolicyV1;
use crate::review_session_v1::DocumentRangeV1;
use crate::review_session_v1::ProvenanceV1;
use crate::review_session_v1::RevisionLaneV1;
use crate::review_session_v1::SelectionRuleV1;
use eyre::Context as _;
use eyre::eyre;
use facet::Facet;
use std::collections::BTreeSet;

pub const SCHEMA: &str = "sfm.review-session/2";
pub const EVALUATOR_VERSION: &str = "sfm-review-v2/1";

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewSessionV2 {
    pub schema: String,
    pub id: String,
    pub title: String,
    pub coordinate_system: String,
    pub revision_lanes: Vec<RevisionLaneV1>,
    pub comments: Vec<CommentV2>,
    pub style_rules: Vec<CommentStyleRuleV1>,
    pub completion_policy: CompletionPolicyV1,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CommentV2 {
    pub id: String,
    pub text: String,
    pub provenance: ProvenanceV1,
    pub target: CommentTargetV2,
    #[facet(rename = "tags", skip_serializing_if = Option::is_none)]
    pub(crate) forbidden_authoritative_tags: Option<Vec<String>>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
#[facet(tag = "kind", rename_all = "snake_case")]
#[repr(C)]
pub enum CommentTargetV2 {
    CommittedSelection {
        selection_rule: SelectionRuleV1,
        #[facet(skip_serializing_if = Option::is_none)]
        candidate_promotion: Option<CandidatePromotionLinkV2>,
    },
    CandidateTrajectory {
        machine_id: String,
        machine_revision: u64,
        trajectory_plan_revision_id: String,
        route_id: String,
        route_step_position: usize,
        #[facet(skip_serializing_if = Option::is_none)]
        trajectory_step_id: Option<String>,
        predicted_state_id: String,
        #[facet(skip_serializing_if = Option::is_none)]
        predicted_state_hash: Option<String>,
        projection_status: ProjectionStatusV2,
        target_kind: CandidateTargetKindV2,
        #[facet(skip_serializing_if = Option::is_none)]
        action_intent_id: Option<String>,
        #[facet(skip_serializing_if = Option::is_none)]
        projected_document_selection: Option<ProjectedDocumentSelectionV2>,
        #[facet(skip_serializing_if = Option::is_none)]
        evaluator_revision: Option<String>,
        evaluator_evidence: Vec<EvaluatorEvidenceV2>,
    },
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum ProjectionStatusV2 {
    Unrequested,
    Queued,
    Running,
    Materialized,
    Conflict,
    Cancelled,
    BudgetExhausted,
    Unknown,
    ExternalBarrier,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum CandidateTargetKindV2 {
    Route,
    Step,
    Action,
    State,
    DocumentRegion,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct EvaluatorEvidenceV2 {
    pub key: String,
    pub value: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ProjectedDocumentSelectionV2 {
    pub document_id: String,
    pub document_state_hash: String,
    pub document_text_sha256: String,
    pub start_byte: usize,
    pub end_byte: usize,
    pub selected_text_sha256: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CandidatePromotionLinkV2 {
    pub source_candidate_comment_id: String,
    pub source_candidate_target_sha256: String,
    pub decision_id: String,
    pub executed_history_head_id: String,
    pub executed_state_id: String,
    pub executed_state_hash: String,
    pub correspondence: String,
    pub correspondence_evidence: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum EvaluationStatusV2 {
    ResolvedExactly,
    ResolvedWithRelocation,
    Ambiguous,
    NoMatch,
    InvalidRule,
    ScopeMissing,
    ContentChanged,
    CandidatePinned,
    CandidatePinnedUnavailable,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CommentEvaluationV2 {
    pub comment_id: String,
    pub evaluator_version: String,
    pub status: EvaluationStatusV2,
    pub ranges: Vec<DocumentRangeV1>,
    pub diagnostics: Vec<String>,
}

#[derive(Facet)]
struct ReviewSessionTaggedSurfaceJson {
    comments: Vec<facet_json::RawJson<'static>>,
}

#[derive(Facet)]
struct CommentTaggedSurfaceJson {
    target: facet_json::RawJson<'static>,
}

#[derive(Facet)]
struct TaggedKindJson {
    kind: String,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct StrictCommittedTargetJson {
    kind: String,
    selection_rule: facet_json::RawJson<'static>,
    #[facet(skip_serializing_if = Option::is_none)]
    candidate_promotion: Option<facet_json::RawJson<'static>>,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct StrictCandidateTargetJson {
    kind: String,
    machine_id: facet_json::RawJson<'static>,
    machine_revision: facet_json::RawJson<'static>,
    trajectory_plan_revision_id: facet_json::RawJson<'static>,
    route_id: facet_json::RawJson<'static>,
    route_step_position: facet_json::RawJson<'static>,
    #[facet(skip_serializing_if = Option::is_none)]
    trajectory_step_id: Option<facet_json::RawJson<'static>>,
    predicted_state_id: facet_json::RawJson<'static>,
    #[facet(skip_serializing_if = Option::is_none)]
    predicted_state_hash: Option<facet_json::RawJson<'static>>,
    projection_status: facet_json::RawJson<'static>,
    target_kind: facet_json::RawJson<'static>,
    #[facet(skip_serializing_if = Option::is_none)]
    action_intent_id: Option<facet_json::RawJson<'static>>,
    #[facet(skip_serializing_if = Option::is_none)]
    projected_document_selection: Option<facet_json::RawJson<'static>>,
    #[facet(skip_serializing_if = Option::is_none)]
    evaluator_revision: Option<facet_json::RawJson<'static>>,
    evaluator_evidence: facet_json::RawJson<'static>,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct StrictLiteralSelectionRuleJson {
    kind: String,
    document_revision_id: facet_json::RawJson<'static>,
    start_byte: facet_json::RawJson<'static>,
    end_byte: facet_json::RawJson<'static>,
    document_sha256: facet_json::RawJson<'static>,
    selected_text_sha256: facet_json::RawJson<'static>,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct StrictSelectionRuleListJson {
    kind: String,
    rules: Vec<facet_json::RawJson<'static>>,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct StrictSelectionRuleDifferenceJson {
    kind: String,
    include: facet_json::RawJson<'static>,
    exclude: Vec<facet_json::RawJson<'static>>,
}

/// Parse, canonicalize, and validate a review-session v2 document.
///
/// # Errors
///
/// Returns an error for unknown fields, unsupported discriminators, or violated
/// v2 invariants.
pub fn parse(input: &str) -> eyre::Result<ReviewSessionV2> {
    validate_tagged_contract_json(input)?;
    let mut session: ReviewSessionV2 = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid review-session v2 JSON: {error:?}"))?;
    canonicalize(&mut session)?;
    validate(&session)?;
    Ok(session)
}

pub(crate) fn validate_tagged_contract_json(input: &str) -> eyre::Result<()> {
    let surface: ReviewSessionTaggedSurfaceJson = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid review-session tagged surface: {error:?}"))?;
    for comment in surface.comments {
        let comment: CommentTaggedSurfaceJson = facet_json::from_str(comment.as_str())
            .map_err(|error| eyre!("invalid review-session comment surface: {error:?}"))?;
        validate_comment_target_json(comment.target.as_str())?;
    }
    Ok(())
}

fn validate_comment_target_json(input: &str) -> eyre::Result<()> {
    let kind: TaggedKindJson = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid comment-target discriminator: {error:?}"))?;
    match kind.kind.as_str() {
        "committed_selection" => {
            let target: StrictCommittedTargetJson = facet_json::from_str(input)
                .map_err(|error| eyre!("invalid strict committed-selection target: {error:?}"))?;
            validate_selection_rule_json(target.selection_rule.as_str())?;
        }
        "candidate_trajectory" => {
            let _: StrictCandidateTargetJson = facet_json::from_str(input)
                .map_err(|error| eyre!("invalid strict candidate-trajectory target: {error:?}"))?;
        }
        other => return Err(eyre!("unsupported comment-target kind `{other}`")),
    }
    Ok(())
}

pub(crate) fn validate_selection_rule_json(input: &str) -> eyre::Result<()> {
    let kind: TaggedKindJson = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid selection-rule discriminator: {error:?}"))?;
    match kind.kind.as_str() {
        "literal_utf8_range" => {
            let _: StrictLiteralSelectionRuleJson = facet_json::from_str(input)
                .map_err(|error| eyre!("invalid strict literal selection rule: {error:?}"))?;
        }
        "union" | "intersection" => {
            let rule: StrictSelectionRuleListJson = facet_json::from_str(input)
                .map_err(|error| eyre!("invalid strict selection-rule list: {error:?}"))?;
            for child in rule.rules {
                validate_selection_rule_json(child.as_str())?;
            }
        }
        "difference" => {
            let rule: StrictSelectionRuleDifferenceJson = facet_json::from_str(input)
                .map_err(|error| eyre!("invalid strict difference rule: {error:?}"))?;
            validate_selection_rule_json(rule.include.as_str())?;
            for child in rule.exclude {
                validate_selection_rule_json(child.as_str())?;
            }
        }
        other => return Err(eyre!("unsupported selection-rule kind `{other}`")),
    }
    Ok(())
}

/// Serialize a validated session using the canonical v2 presentation.
///
/// # Errors
///
/// Returns an error when validation or serialization fails.
pub fn to_canonical_json(session: &ReviewSessionV2) -> eyre::Result<String> {
    let mut canonical = session.clone();
    canonicalize(&mut canonical)?;
    validate(&canonical)?;
    let mut output = facet_json::to_string_pretty(&canonical)
        .wrap_err("could not serialize review-session v2")?;
    output.push('\n');
    Ok(output)
}

/// Evaluate every comment target. Candidate targets remain pinned discussion;
/// committed targets use the frozen v1 UTF-8 selection evaluator.
///
/// # Errors
///
/// Returns an error when the session is invalid.
pub fn evaluate_all(session: &ReviewSessionV2) -> eyre::Result<Vec<CommentEvaluationV2>> {
    validate(session)?;
    session
        .comments
        .iter()
        .map(|comment| evaluate_comment(session, comment))
        .collect()
}

fn evaluate_comment(
    session: &ReviewSessionV2,
    comment: &CommentV2,
) -> eyre::Result<CommentEvaluationV2> {
    match &comment.target {
        CommentTargetV2::CandidateTrajectory {
            trajectory_plan_revision_id,
            route_id,
            route_step_position,
            projection_status,
            target_kind,
            ..
        } => {
            let status = if *projection_status == ProjectionStatusV2::Materialized {
                EvaluationStatusV2::CandidatePinned
            } else {
                EvaluationStatusV2::CandidatePinnedUnavailable
            };
            Ok(CommentEvaluationV2 {
                comment_id: comment.id.clone(),
                evaluator_version: EVALUATOR_VERSION.to_owned(),
                status,
                ranges: Vec::new(),
                diagnostics: vec![format!(
                    "Pinned candidate {} at {}#{}@{}",
                    candidate_target_name(*target_kind),
                    trajectory_plan_revision_id,
                    route_id,
                    route_step_position
                )],
            })
        }
        CommentTargetV2::CommittedSelection { selection_rule, .. } => {
            let v1 = review_session_v1::ReviewSessionV1 {
                schema: review_session_v1::SCHEMA.to_owned(),
                id: session.id.clone(),
                title: session.title.clone(),
                coordinate_system: session.coordinate_system.clone(),
                revision_lanes: session.revision_lanes.clone(),
                comments: vec![review_session_v1::CommentV1 {
                    id: comment.id.clone(),
                    text: comment.text.clone(),
                    provenance: comment.provenance.clone(),
                    selection_rule: selection_rule.clone(),
                    forbidden_authoritative_tags: None,
                }],
                style_rules: session.style_rules.clone(),
                completion_policy: session.completion_policy.clone(),
            };
            let evaluation = review_session_v1::evaluate_all(&v1)?
                .into_iter()
                .next()
                .ok_or_else(|| eyre!("v1 evaluator did not return the committed comment"))?;
            Ok(CommentEvaluationV2 {
                comment_id: evaluation.comment_id,
                evaluator_version: EVALUATOR_VERSION.to_owned(),
                status: map_v1_status(evaluation.status),
                ranges: evaluation.ranges,
                diagnostics: evaluation.diagnostics,
            })
        }
    }
}

pub(crate) fn validate(session: &ReviewSessionV2) -> eyre::Result<()> {
    if session.schema != SCHEMA {
        return Err(eyre!(
            "unsupported review-session schema '{}'",
            session.schema
        ));
    }
    require_text(&session.id, "session.id")?;
    require_text(&session.title, "session.title")?;
    if session.coordinate_system != review_session_v1::COORDINATE_SYSTEM {
        return Err(eyre!(
            "unsupported coordinate system '{}'",
            session.coordinate_system
        ));
    }

    let v1_shell = review_session_v1::ReviewSessionV1 {
        schema: review_session_v1::SCHEMA.to_owned(),
        id: session.id.clone(),
        title: session.title.clone(),
        coordinate_system: session.coordinate_system.clone(),
        revision_lanes: session.revision_lanes.clone(),
        comments: Vec::new(),
        style_rules: session.style_rules.clone(),
        completion_policy: session.completion_policy.clone(),
    };
    review_session_v1::validate(&v1_shell)?;

    let mut comment_ids = BTreeSet::new();
    for comment in &session.comments {
        require_text(&comment.id, "comment.id")?;
        require_text(&comment.text, "comment.text")?;
        if !comment_ids.insert(comment.id.as_str()) {
            return Err(eyre!("duplicate comment id '{}'", comment.id));
        }
        if comment.forbidden_authoritative_tags.is_some() {
            return Err(eyre!(
                "comment '{}' contains forbidden authoritative tags field",
                comment.id
            ));
        }
        validate_provenance(&comment.provenance)?;
        validate_target(&comment.target)?;
    }
    Ok(())
}

pub(crate) fn canonicalize(session: &mut ReviewSessionV2) -> eyre::Result<()> {
    for comment in &mut session.comments {
        if let CommentTargetV2::CandidateTrajectory {
            evaluator_evidence, ..
        } = &mut comment.target
        {
            evaluator_evidence.sort_by(|left, right| left.key.cmp(&right.key));
            ensure_unique(
                evaluator_evidence.iter().map(|value| value.key.as_str()),
                "evaluator-evidence key",
            )?;
        }
    }
    Ok(())
}

fn validate_provenance(provenance: &ProvenanceV1) -> eyre::Result<()> {
    require_text(&provenance.kind, "provenance.kind")?;
    require_text(&provenance.producer, "provenance.producer")?;
    require_text(&provenance.version, "provenance.version")?;
    for parent in &provenance.parent_comment_ids {
        require_text(parent, "provenance.parent_comment_id")?;
    }
    Ok(())
}

fn validate_target(target: &CommentTargetV2) -> eyre::Result<()> {
    match target {
        CommentTargetV2::CommittedSelection {
            candidate_promotion,
            ..
        } => {
            if let Some(promotion) = candidate_promotion {
                validate_promotion(promotion)?;
            }
        }
        CommentTargetV2::CandidateTrajectory {
            machine_id,
            trajectory_plan_revision_id,
            route_id,
            route_step_position,
            trajectory_step_id,
            predicted_state_id,
            predicted_state_hash,
            projection_status,
            target_kind,
            action_intent_id,
            projected_document_selection,
            evaluator_revision,
            evaluator_evidence,
            ..
        } => {
            require_text(machine_id, "candidate.machineId")?;
            require_text(
                trajectory_plan_revision_id,
                "candidate.trajectoryPlanRevisionId",
            )?;
            require_text(route_id, "candidate.routeId")?;
            require_text(predicted_state_id, "candidate.predictedStateId")?;
            optional_text(trajectory_step_id.as_deref(), "candidate.trajectoryStepId")?;
            optional_text(
                predicted_state_hash.as_deref(),
                "candidate.predictedStateHash",
            )?;
            optional_text(action_intent_id.as_deref(), "candidate.actionIntentId")?;
            optional_text(evaluator_revision.as_deref(), "candidate.evaluatorRevision")?;
            if *route_step_position == 0 && trajectory_step_id.is_some() {
                return Err(eyre!(
                    "the route-start target must not name a trajectory step"
                ));
            }
            if *route_step_position > 0 && trajectory_step_id.is_none() {
                return Err(eyre!(
                    "a post-step candidate target requires its trajectory step id"
                ));
            }
            if *target_kind == CandidateTargetKindV2::Action && action_intent_id.is_none() {
                return Err(eyre!("an action target requires an action-intent id"));
            }
            if matches!(
                target_kind,
                CandidateTargetKindV2::Step | CandidateTargetKindV2::Action
            ) && trajectory_step_id.is_none()
            {
                return Err(eyre!("step and action targets require a trajectory step"));
            }
            if *target_kind == CandidateTargetKindV2::DocumentRegion {
                if *projection_status != ProjectionStatusV2::Materialized {
                    return Err(eyre!(
                        "a candidate document region requires a materialized frame"
                    ));
                }
                if predicted_state_hash.is_none() || projected_document_selection.is_none() {
                    return Err(eyre!(
                        "a candidate document region requires state and selection witnesses"
                    ));
                }
            } else if projected_document_selection.is_some() {
                return Err(eyre!(
                    "only a document-region target may carry a projected selection"
                ));
            }
            if let Some(selection) = projected_document_selection {
                validate_projected_selection(selection)?;
            }
            ensure_unique(
                evaluator_evidence.iter().map(|value| value.key.as_str()),
                "evaluator-evidence key",
            )?;
            for evidence in evaluator_evidence {
                require_text(&evidence.key, "evaluatorEvidence.key")?;
                require_text(&evidence.value, "evaluatorEvidence.value")?;
            }
        }
    }
    Ok(())
}

fn validate_projected_selection(selection: &ProjectedDocumentSelectionV2) -> eyre::Result<()> {
    require_text(&selection.document_id, "projectedSelection.documentId")?;
    require_text(
        &selection.document_state_hash,
        "projectedSelection.documentStateHash",
    )?;
    require_sha256(
        &selection.document_text_sha256,
        "projectedSelection.documentTextSha256",
    )?;
    if selection.end_byte < selection.start_byte {
        return Err(eyre!(
            "projected selection must be a forward half-open range"
        ));
    }
    require_sha256(
        &selection.selected_text_sha256,
        "projectedSelection.selectedTextSha256",
    )
}

fn validate_promotion(promotion: &CandidatePromotionLinkV2) -> eyre::Result<()> {
    require_text(
        &promotion.source_candidate_comment_id,
        "promotion.sourceCandidateCommentId",
    )?;
    require_sha256(
        &promotion.source_candidate_target_sha256,
        "promotion.sourceCandidateTargetSha256",
    )?;
    require_text(&promotion.decision_id, "promotion.decisionId")?;
    require_text(
        &promotion.executed_history_head_id,
        "promotion.executedHistoryHeadId",
    )?;
    require_text(&promotion.executed_state_id, "promotion.executedStateId")?;
    require_text(
        &promotion.executed_state_hash,
        "promotion.executedStateHash",
    )?;
    require_text(&promotion.correspondence, "promotion.correspondence")?;
    if !matches!(
        promotion.correspondence.as_str(),
        "exact" | "witnessed_migration"
    ) {
        return Err(eyre!(
            "unsupported candidate correspondence {}",
            promotion.correspondence
        ));
    }
    if promotion.correspondence == "witnessed_migration"
        && promotion.correspondence_evidence.is_empty()
    {
        return Err(eyre!(
            "witnessed migration requires correspondence evidence"
        ));
    }
    for evidence in &promotion.correspondence_evidence {
        require_text(evidence, "promotion.correspondenceEvidence")?;
    }
    Ok(())
}

const fn map_v1_status(status: review_session_v1::EvaluationStatusV1) -> EvaluationStatusV2 {
    match status {
        review_session_v1::EvaluationStatusV1::ResolvedExactly => {
            EvaluationStatusV2::ResolvedExactly
        }
        review_session_v1::EvaluationStatusV1::ResolvedWithRelocation => {
            EvaluationStatusV2::ResolvedWithRelocation
        }
        review_session_v1::EvaluationStatusV1::Ambiguous => EvaluationStatusV2::Ambiguous,
        review_session_v1::EvaluationStatusV1::NoMatch => EvaluationStatusV2::NoMatch,
        review_session_v1::EvaluationStatusV1::InvalidRule => EvaluationStatusV2::InvalidRule,
        review_session_v1::EvaluationStatusV1::ScopeMissing => EvaluationStatusV2::ScopeMissing,
        review_session_v1::EvaluationStatusV1::ContentChanged => EvaluationStatusV2::ContentChanged,
    }
}

const fn candidate_target_name(kind: CandidateTargetKindV2) -> &'static str {
    match kind {
        CandidateTargetKindV2::Route => "route",
        CandidateTargetKindV2::Step => "step",
        CandidateTargetKindV2::Action => "action",
        CandidateTargetKindV2::State => "state",
        CandidateTargetKindV2::DocumentRegion => "document_region",
    }
}

fn require_text(value: &str, label: &str) -> eyre::Result<()> {
    if value.trim().is_empty() {
        return Err(eyre!("{label} must not be blank"));
    }
    Ok(())
}

fn optional_text(value: Option<&str>, label: &str) -> eyre::Result<()> {
    if let Some(value) = value {
        require_text(value, label)?;
    }
    Ok(())
}

fn require_sha256(value: &str, label: &str) -> eyre::Result<()> {
    require_text(value, label)?;
    if value.len() != 64
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    {
        return Err(eyre!("{label} must be lowercase SHA-256"));
    }
    Ok(())
}

fn ensure_unique<'a>(values: impl IntoIterator<Item = &'a str>, label: &str) -> eyre::Result<()> {
    let mut seen = BTreeSet::new();
    for value in values {
        if !seen.insert(value) {
            return Err(eyre!("duplicate {label} '{value}'"));
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::review_session_v1::DocumentRevisionV1;
    use crate::review_session_v1::RepositoryV1;
    use crate::review_session_v1::SnapshotV1;

    fn exact_session() -> ReviewSessionV2 {
        let text = "class A {}";
        let hash = review_session_v1::sha256(text.as_bytes());
        ReviewSessionV2 {
            schema: SCHEMA.to_owned(),
            id: "review".to_owned(),
            title: "Review".to_owned(),
            coordinate_system: review_session_v1::COORDINATE_SYSTEM.to_owned(),
            revision_lanes: vec![RevisionLaneV1 {
                id: "1.19.2".to_owned(),
                repository: RepositoryV1 {
                    id: "sfm".to_owned(),
                    root_hint: ".".to_owned(),
                },
                version_label: Some("1.19.2".to_owned()),
                before: SnapshotV1 {
                    id: "before".to_owned(),
                    documents: Vec::new(),
                },
                after: SnapshotV1 {
                    id: "after".to_owned(),
                    documents: vec![DocumentRevisionV1 {
                        id: "after:A.java".to_owned(),
                        path: "A.java".to_owned(),
                        encoding: "utf-8".to_owned(),
                        sha256: hash.clone(),
                        text: text.to_owned(),
                    }],
                },
            }],
            comments: vec![CommentV2 {
                id: "comment-1".to_owned(),
                text: "#approved reviewed".to_owned(),
                provenance: ProvenanceV1 {
                    kind: "human".to_owned(),
                    producer: "maintainer".to_owned(),
                    version: "1".to_owned(),
                    parent_comment_ids: Vec::new(),
                },
                target: CommentTargetV2::CommittedSelection {
                    selection_rule: SelectionRuleV1::LiteralUtf8Range {
                        document_revision_id: "after:A.java".to_owned(),
                        start_byte: 0,
                        end_byte: text.len(),
                        document_sha256: hash.clone(),
                        selected_text_sha256: hash,
                    },
                    candidate_promotion: None,
                },
                forbidden_authoritative_tags: None,
            }],
            style_rules: Vec::new(),
            completion_policy: CompletionPolicyV1 {
                coverage_mode: "changed_surface".to_owned(),
                approval_hashtag: "#approved".to_owned(),
                blocking_hashtags: vec!["#problem".to_owned()],
            },
        }
    }

    #[test]
    fn committed_v2_round_trips_strictly_and_evaluates() {
        let session = exact_session();
        let canonical = to_canonical_json(&session).expect("canonical v2");
        let reparsed = parse(&canonical).expect("parse canonical v2");
        assert_eq!(reparsed, session);
        assert_eq!(
            evaluate_all(&reparsed).expect("evaluate")[0].status,
            EvaluationStatusV2::ResolvedExactly
        );

        let with_unknown = canonical.replacen(
            "\"title\": \"Review\"",
            "\"title\": \"Review\",\n  \"future\": true",
            1,
        );
        assert!(
            parse(&with_unknown)
                .expect_err("unknown field")
                .to_string()
                .contains("unknown field")
        );

        let target_unknown = canonical.replacen(
            "\"kind\": \"committed_selection\",",
            "\"kind\": \"committed_selection\",\n        \"future_target\": true,",
            1,
        );
        assert_ne!(target_unknown, canonical, "target fixture edit must apply");
        assert!(parse(&target_unknown).is_err());
    }

    #[test]
    fn candidate_targets_are_canonicalized_but_never_committed_evidence() {
        let mut session = exact_session();
        session.comments[0].target = CommentTargetV2::CandidateTrajectory {
            machine_id: "machine".to_owned(),
            machine_revision: 1,
            trajectory_plan_revision_id: "plan".to_owned(),
            route_id: "route".to_owned(),
            route_step_position: 0,
            trajectory_step_id: None,
            predicted_state_id: "state".to_owned(),
            predicted_state_hash: None,
            projection_status: ProjectionStatusV2::Queued,
            target_kind: CandidateTargetKindV2::Route,
            action_intent_id: None,
            projected_document_selection: None,
            evaluator_revision: Some("evaluator/1".to_owned()),
            evaluator_evidence: vec![
                EvaluatorEvidenceV2 {
                    key: "z".to_owned(),
                    value: "last".to_owned(),
                },
                EvaluatorEvidenceV2 {
                    key: "a".to_owned(),
                    value: "first".to_owned(),
                },
            ],
        };
        let canonical = to_canonical_json(&session).expect("candidate canonical");
        let reparsed = parse(&canonical).expect("candidate parses");
        let target_unknown = canonical.replacen(
            "\"kind\": \"candidate_trajectory\",",
            "\"kind\": \"candidate_trajectory\",\n        \"future_target\": true,",
            1,
        );
        assert_ne!(target_unknown, canonical, "target fixture edit must apply");
        assert!(parse(&target_unknown).is_err());
        let CommentTargetV2::CandidateTrajectory {
            evaluator_evidence, ..
        } = &reparsed.comments[0].target
        else {
            panic!("candidate target expected");
        };
        assert_eq!(evaluator_evidence[0].key, "a");
        assert_eq!(
            evaluate_all(&reparsed).expect("evaluate")[0].status,
            EvaluationStatusV2::CandidatePinnedUnavailable
        );
    }
}
