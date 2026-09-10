//! Pure adapter from verified capture bytes to the existing portable review/comment kernel.

use crate::release_review_capture_io::TEXT_LIMIT;
use crate::release_review_v1 as review;
use crate::release_review_working_tree::WorkingTreeReviewConfig;
use crate::release_review_working_tree::WorkingTreeReviewInputs;
use crate::release_review_working_tree::document_id;
use crate::review_session_v1 as session;
use crate::review_session_v2 as comments;
use eyre::eyre;
use std::path::Path;

pub const PRODUCER_ID: &str = "sfm.release-review.working-tree-materializer/1";

struct Side {
    corpus: review::CorpusDocumentV1,
    document: Option<session::DocumentRevisionV1>,
    limitation: Option<String>,
}

impl Side {
    fn ranges(&self) -> Vec<review::Utf8RangeV1> {
        self.document
            .as_ref()
            .map(|doc| {
                vec![review::Utf8RangeV1 {
                    start_byte: 0,
                    end_byte: doc.text.len(),
                }]
            })
            .unwrap_or_default()
    }

    fn rule(&self) -> session::SelectionRuleV1 {
        session::SelectionRuleV1::LiteralUtf8Range {
            document_revision_id: self.corpus.document_revision_id.clone(),
            start_byte: 0,
            end_byte: self.document.as_ref().map_or(0, |doc| doc.text.len()),
            document_sha256: self.corpus.sha256.clone(),
            selected_text_sha256: self
                .document
                .as_ref()
                .map_or_else(|| session::sha256(&[]), |doc| doc.sha256.clone()),
        }
    }
}

/// Whole-file units are a conservative initial adapter: no changed byte can be
/// omitted, while ordinary diff previews still provide the finer reading view.
///
/// # Errors
/// Refuses missing captured bodies or any inconsistency with the shared review kernel.
pub fn materialize(
    config: &WorkingTreeReviewConfig,
    input: WorkingTreeReviewInputs,
) -> eyre::Result<review::ReleaseReviewDocumentV1> {
    materialize_with_markers(config, input, true)
}

/// Live observations keep changed surfaces as units, never as synthetic comments.
/// # Errors
/// Refuses inconsistent captured bytes or review invariants.
pub fn materialize_observation(
    config: &WorkingTreeReviewConfig,
    input: WorkingTreeReviewInputs,
) -> eyre::Result<review::ReleaseReviewDocumentV1> {
    materialize_with_markers(config, input, false)
}

#[expect(
    clippy::too_many_lines,
    reason = "explicit shared-envelope assembly keeps source sides, units and generated comment provenance visible together"
)]
fn materialize_with_markers(
    config: &WorkingTreeReviewConfig,
    mut input: WorkingTreeReviewInputs,
    include_markers: bool,
) -> eyre::Result<review::ReleaseReviewDocumentV1> {
    input.capture.validate()?;
    let generation = format!("working-tree-materializer/1:{}", input.capture.id);
    let input_hash = session::sha256(
        format!(
            "{PRODUCER_ID}\0{}\0{}\0{}\0{}",
            config.repository_id, config.lane_id, input.before_commit, input.capture.id
        )
        .as_bytes(),
    );
    let mut units = Vec::new();
    let mut generated = Vec::new();
    let mut corpus = Vec::new();
    let mut before_docs = Vec::new();
    let mut after_docs = Vec::new();
    for entry in &input.capture.entries {
        if entry.materialization == "unchanged" {
            continue;
        }
        let old = input.before_entries.get(&entry.path);
        let before = old.map(|_| {
            let bytes = input.before_bytes.remove(&entry.path);
            let hash = bytes
                .as_ref()
                .map_or_else(|| session::sha256(&[]), |bytes| session::sha256(bytes));
            let text = bytes.as_ref().and_then(|bytes| {
                (bytes.len() <= TEXT_LIMIT && !bytes.contains(&0))
                    .then(|| std::str::from_utf8(bytes).ok().map(str::to_owned))
                    .flatten()
            });
            let limitation = text
                .is_none()
                .then(|| "Before source is binary, oversized or not a regular Git file".into());
            side(
                config,
                &input.before_commit,
                review::SnapshotSideV1::Before,
                &entry.path,
                hash,
                text,
                limitation,
            )
        });
        let after = if entry.kind == "deleted" {
            None
        } else {
            let text = input.after_text.remove(&entry.path);
            if entry.materialization == "utf8" && text.is_none() {
                return Err(eyre!("missing captured bytes: {}", entry.path));
            }
            let hash = entry.sha256.clone().unwrap_or_else(|| session::sha256(&[]));
            let mut result = side(
                config,
                &input.capture.id,
                review::SnapshotSideV1::After,
                &entry.path,
                hash,
                text,
                entry.diagnostic.clone(),
            );
            if let Some(id) = &entry.document_revision_id
                && result.corpus.document_revision_id != *id
            {
                return Err(eyre!("derived captured document identity mismatch"));
            }
            result.corpus.source_locator = format!(
                "working-tree-capture://{}/{}",
                input
                    .capture
                    .id
                    .strip_prefix(crate::release_review_capture::ID_PREFIX)
                    .ok_or_else(|| eyre!("invalid capture identity prefix"))?,
                entry.path
            );
            Some(result)
        };
        // A newly staged then deleted path absent from the baseline has no change surface.
        if before.is_none() && after.is_none() {
            continue;
        }
        let limitation = before
            .as_ref()
            .and_then(|side| side.limitation.clone())
            .or_else(|| after.as_ref().and_then(|side| side.limitation.clone()));
        let supported = before
            .iter()
            .chain(after.iter())
            .all(|side| side.document.is_some());
        let operation = match (before.is_some(), after.is_some()) {
            (false, true) => review::ChangeOperationV1::Added,
            (true, false) => review::ChangeOperationV1::Deleted,
            _ if old.is_some_and(|old| !matches!(old.mode.as_str(), "100644" | "100755"))
                || entry.kind != "regular_file" =>
            {
                review::ChangeOperationV1::TypeChanged
            }
            _ => review::ChangeOperationV1::Modified,
        };
        let unit_id = format!(
            "working-tree-unit:sha256:{}",
            session::sha256(format!("{input_hash}\0{}", entry.path).as_bytes())
        );
        let unit = review::ReviewUnitV1 { id: unit_id.clone(), lane_id: config.lane_id.clone(), operation,
            path_before: before.as_ref().map(|_| entry.path.clone()), path_after: after.as_ref().map(|_| entry.path.clone()),
            before_document_revision_id: before.as_ref().map(|side| side.corpus.document_revision_id.clone()),
            after_document_revision_id: after.as_ref().map(|side| side.corpus.document_revision_id.clone()),
            before_ranges: if supported { before.as_ref().map_or_else(Vec::new, Side::ranges) } else { Vec::new() },
            after_ranges: if supported { after.as_ref().map_or_else(Vec::new, Side::ranges) } else { Vec::new() },
            language: Path::new(&entry.path).extension().and_then(|value| value.to_str()).unwrap_or("text").to_ascii_lowercase(),
            surface_kind: if supported { review::SurfaceKindV1::File } else { review::SurfaceKindV1::Unsupported },
            semantic_key: None, limitation: limitation.or_else(|| Some("Conservative whole-file capture unit; diff previews provide a finer reading view".into())),
            producer_id: PRODUCER_ID.into(), producer_generation: generation.clone() };
        if include_markers {
            let rules = before
                .iter()
                .chain(after.iter())
                .map(Side::rule)
                .collect::<Vec<_>>();
            generated.push(comments::CommentV2 {
                id: format!(
                    "release-change-comment:sha256:{}",
                    session::sha256(unit_id.as_bytes())
                ),
                text: format!(
                    "#release-change\nCaptured change to `{}`. {}",
                    entry.path,
                    unit.limitation.as_deref().unwrap_or("")
                ),
                provenance: session::ProvenanceV1 {
                    kind: "generated".into(),
                    producer: PRODUCER_ID.into(),
                    version: generation.clone(),
                    parent_comment_ids: Vec::new(),
                },
                target: comments::CommentTargetV2::CommittedSelection {
                    selection_rule: session::SelectionRuleV1::Union { rules },
                    candidate_promotion: None,
                },
                forbidden_authoritative_tags: None,
            });
        }
        units.push(unit);
        for side in before.into_iter().chain(after) {
            if let Some(document) = side.document {
                match side.corpus.snapshot_side {
                    review::SnapshotSideV1::Before => before_docs.push(document),
                    review::SnapshotSideV1::After => after_docs.push(document),
                }
            }
            corpus.push(side.corpus);
        }
    }
    if !input.after_text.is_empty() || !input.before_bytes.is_empty() {
        return Err(eyre!("capture bytes were not reconciled to review units"));
    }
    let remaining = format!(
        "({} {}) difference effective(#approved)",
        config.lane_id, input.capture.id
    );
    let mut result = review::ReleaseReviewDocumentV1 {
        schema: review::WORKING_TREE_SCHEMA.into(),
        review_session: comments::ReviewSessionV2 {
            schema: comments::SCHEMA.into(),
            id: format!("release-review:sha256:{input_hash}"),
            title: format!(
                "{} release review: {} to captured working tree",
                config.lane_id,
                &input.before_commit[..12]
            ),
            coordinate_system: session::COORDINATE_SYSTEM.into(),
            revision_lanes: vec![session::RevisionLaneV1 {
                id: config.lane_id.clone(),
                repository: session::RepositoryV1 {
                    id: config.repository_id.clone(),
                    root_hint: ".".into(),
                },
                version_label: Some(config.lane_id.clone()),
                before: session::SnapshotV1 {
                    id: format!("git:{}", input.before_commit),
                    documents: before_docs,
                },
                after: session::SnapshotV1 {
                    id: input.capture.id.clone(),
                    documents: after_docs,
                },
            }],
            comments: generated,
            style_rules: vec![session::CommentStyleRuleV1 {
                id: "release-change".into(),
                required_hashtags: vec!["#release-change".into()],
                priority: 0,
                foreground: None,
                background: None,
                underline: None,
                gutter_marker: Some("R".into()),
                enabled: true,
            }],
            completion_policy: session::CompletionPolicyV1 {
                coverage_mode: "changed_surface".into(),
                approval_hashtag: "#approved".into(),
                blocking_hashtags: vec!["#problem".into(), "#needs-change".into()],
            },
        },
        repository_bindings: vec![review::RepositoryBindingV1 {
            lane_id: config.lane_id.clone(),
            repository_id: config.repository_id.clone(),
            root_hint: ".".into(),
            before_label: config.before.clone(),
            before_commit: input.before_commit,
            before_tree: input.before_tree,
            after_label: "Captured working tree".into(),
            candidate_commit: None,
            candidate_tree: None,
            review_evidence_paths: vec![config.review_evidence_path.clone()],
            working_tree_capture: Some(input.capture),
        }],
        corpus_documents: corpus,
        resume_state: review::ResumeStateV1 {
            active_query_id: Some("remaining".into()),
            active_query_expression: Some(remaining.clone()),
            current_unit_id: units.first().map(|unit| unit.id.clone()),
            deferred_unit_ids: Vec::new(),
            generation: 0,
        },
        review_units: units,
        selector_bindings: Vec::new(),
        migration_reports: Vec::new(),
        named_queries: vec![
            review::NamedQueryV1 {
                id: "approved".into(),
                expression: format!(
                    "effective(#approved) intersect {} candidate",
                    config.lane_id
                ),
            },
            review::NamedQueryV1 {
                id: "remaining".into(),
                expression: remaining,
            },
        ],
        producer_generations: vec![review::ProducerGenerationV1 {
            producer_id: PRODUCER_ID.into(),
            generation,
            input_fingerprint: input_hash,
            output_fingerprint: "0".repeat(64),
        }],
        completion_attestations: Vec::new(),
    };
    if !include_markers {
        result.review_session.style_rules.clear();
    }
    result.producer_generations[0].output_fingerprint =
        session::sha256(review::to_canonical_json(&result)?.as_bytes());
    review::parse(&review::to_canonical_json(&result)?)
}

fn side(
    config: &WorkingTreeReviewConfig,
    source: &str,
    kind: review::SnapshotSideV1,
    path: &str,
    hash: String,
    text: Option<String>,
    limitation: Option<String>,
) -> Side {
    let name = match kind {
        review::SnapshotSideV1::Before => "before",
        review::SnapshotSideV1::After => "after",
    };
    let id = document_id(config, source, name, path, &hash);
    let materialization = if text.is_some() {
        review::MaterializationV1::Complete
    } else {
        review::MaterializationV1::Partial
    };
    let document = text.map(|text| session::DocumentRevisionV1 {
        id: id.clone(),
        path: path.into(),
        encoding: "utf-8".into(),
        sha256: hash.clone(),
        text,
    });
    Side {
        corpus: review::CorpusDocumentV1 {
            id: format!("corpus:{id}"),
            lane_id: config.lane_id.clone(),
            snapshot_side: kind,
            path: path.into(),
            document_revision_id: id,
            sha256: hash,
            source_owner: source.into(),
            source_locator: format!("git:{source}:{path}"),
            materialization,
        },
        document,
        limitation,
    }
}
