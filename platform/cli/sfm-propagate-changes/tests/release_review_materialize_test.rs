#![expect(dead_code)]

#[path = "../src/release_review_capture.rs"]
mod release_review_capture;
#[path = "../src/release_review_git.rs"]
mod release_review_git;
#[path = "../src/release_review_materialize.rs"]
mod release_review_materialize;
#[path = "../src/release_review_v1.rs"]
mod release_review_v1;
#[path = "../src/review_session_v1.rs"]
mod review_session_v1;
#[path = "../src/review_session_v2.rs"]
mod review_session_v2;

// `review_session_v1` keeps its portable store in the same source module.  The
// materializer tests do not exercise that store, but its implementation refers
// to this crate-private helper just as the production crate does.
mod payload_fetcher {
    use std::path::Path;

    pub(crate) fn write_payload_atomically(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
        std::fs::write(path, bytes)?;
        Ok(())
    }
}

use release_review_git::GitChangeKind;
use release_review_git::GitPath;
use release_review_git::produce_git_review_domain;
use release_review_materialize::PRODUCER_ID;
use release_review_materialize::RETIRED_PRODUCER_ID;
use release_review_materialize::ReleaseReviewMaterializeConfig;
use release_review_materialize::generated_comment_id;
use release_review_materialize::materialize_release_review;
use release_review_materialize::merge_refreshed_materialization;
use release_review_materialize::reconcile_materialization;
use release_review_v1::AddressedRangeV1;
use release_review_v1::ChangeOperationV1;
use release_review_v1::CommentSelectorBindingV1;
use release_review_v1::CompletionAttestationV1;
use release_review_v1::EvidenceV1;
use release_review_v1::MaterializationV1;
use release_review_v1::MigrationDecisionV1;
use release_review_v1::MigrationReportV1;
use release_review_v1::NamedQueryV1;
use release_review_v1::PinnedSelectionRangeV1;
use release_review_v1::PinnedSelectionV1;
use release_review_v1::ProposalConfidenceV1;
use release_review_v1::SelectionDirectionV1;
use release_review_v1::SelectorEvaluationResultV1;
use release_review_v1::SelectorEvaluationStatusV1;
use release_review_v1::SelectorKindV1;
use release_review_v1::SelectorProposalV1;
use release_review_v1::SurfaceKindV1;
use review_session_v1::ProvenanceV1;
use review_session_v2::CommentTargetV2;
use review_session_v2::CommentV2;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::process::Command;
use std::process::Output;
use std::process::Stdio;

#[test]
#[expect(
    clippy::too_many_lines,
    reason = "one integration assertion keeps the complete vertical slice auditable"
)]
fn materializes_complete_real_git_domain_canonically_and_without_mutation() {
    let fixture = changed_repository();
    let domain = produce_git_review_domain(fixture.root(), "review-before", "HEAD")
        .expect("Git domain should be complete");
    let config = fixture.config(&domain);
    let head_before = git_stdout(fixture.root(), ["rev-parse", "HEAD"]);
    let branch_before = git_stdout(fixture.root(), ["symbolic-ref", "--short", "HEAD"]);
    let refs_before = git_stdout(fixture.root(), ["show-ref"]);
    let status_before = git_stdout(fixture.root(), ["status", "--porcelain"]);

    let first = materialize_release_review(&domain, &config).expect("materialization should work");
    let second = materialize_release_review(&domain, &config).expect("rerun should be stable");
    assert_eq!(first, second);

    // Synthetic annotations are presentation history, not the changed-domain
    // denominator. The live path must retain every side and unit without them.
    let observation = release_review_materialize::materialize_observation(&domain, &config)
        .expect("comment-free observation should work");
    assert!(observation.review_session.comments.is_empty());
    assert!(observation.review_session.style_rules.is_empty());
    assert!(!first.review_units.is_empty());
    assert_eq!(first.review_units, observation.review_units);
    assert_eq!(first.corpus_documents, observation.corpus_documents);
    assert_eq!(
        first.review_session.revision_lanes,
        observation.review_session.revision_lanes
    );
    for expression in [
        "1.19.2 HEAD",
        "(1.19.2 HEAD) difference effective(#approved)",
        "effective(#approved) intersect 1.19.2 HEAD",
    ] {
        assert_eq!(
            release_review_v1::query(&first, expression).expect("legacy coverage query"),
            release_review_v1::query(&observation, expression).expect("observation coverage query"),
            "removing generated annotations changed coverage for {expression}"
        );
    }

    let first_json = release_review_v1::to_canonical_json(&first).expect("canonical JSON");
    let parsed = release_review_v1::parse(&first_json).expect("canonical JSON should parse");
    assert_eq!(
        first_json,
        release_review_v1::to_canonical_json(&parsed).expect("canonical re-emission")
    );
    assert_eq!(first, parsed);

    assert_eq!(first.repository_bindings.len(), 1);
    let binding = &first.repository_bindings[0];
    assert_eq!(binding.before_commit, fixture.before_commit);
    assert_eq!(
        binding.candidate_commit.as_deref(),
        Some(fixture.candidate_commit.as_str())
    );
    assert_eq!(binding.before_tree, domain.before.tree_id);
    assert_eq!(
        binding.candidate_tree.as_deref(),
        Some(domain.candidate.tree_id.as_str())
    );
    assert_eq!(binding.after_label, "HEAD");
    assert_eq!(
        binding.review_evidence_paths,
        ["docs/reviews/test.sfm-review.json"]
    );

    let reconciliation = reconcile_materialization(&domain, &first).expect("reconciliation");
    assert!(reconciliation.complete);
    assert_eq!(reconciliation.raw_change_count, domain.changes.len());
    assert_eq!(
        reconciliation.raw_side_path_count,
        domain.reconciliation.raw_side_path_count
    );
    assert_eq!(reconciliation.git_unit_count, domain.units.len());
    assert_eq!(reconciliation.review_unit_count, domain.units.len());
    assert!(reconciliation.explicitly_unsupported_corpus_side_path_count >= 4);
    assert_eq!(
        reconciliation.represented_corpus_side_path_count
            + reconciliation.explicitly_unsupported_corpus_side_path_count,
        reconciliation.raw_side_path_count
    );
    assert_eq!(domain.reconciliation.unreconciled_side_path_count, 0);

    let git_unit_ids = domain
        .units
        .iter()
        .map(|unit| unit.unit_id.as_str())
        .collect::<BTreeSet<_>>();
    let review_unit_ids = first
        .review_units
        .iter()
        .map(|unit| unit.id.as_str())
        .collect::<BTreeSet<_>>();
    assert_eq!(git_unit_ids, review_unit_ids);
    assert_eq!(first.review_session.comments.len(), domain.units.len());
    assert!(first.review_session.comments.iter().all(|comment| {
        comment.text.contains("#release-change")
            && comment.provenance.kind == "generated"
            && comment.provenance.producer == release_review_materialize::PRODUCER_ID
    }));

    let operations = first
        .review_units
        .iter()
        .map(|unit| unit.operation)
        .collect::<BTreeSet<_>>();
    for operation in [
        ChangeOperationV1::Added,
        ChangeOperationV1::Deleted,
        ChangeOperationV1::Modified,
        ChangeOperationV1::Renamed,
        ChangeOperationV1::Copied,
    ] {
        assert!(operations.contains(&operation), "missing {operation:?}");
    }
    assert!(first.review_units.iter().any(|unit| {
        unit.operation == ChangeOperationV1::Added
            && unit.path_before.is_none()
            && unit.before_document_revision_id.is_none()
    }));
    assert!(first.review_units.iter().any(|unit| {
        unit.operation == ChangeOperationV1::Deleted
            && unit.path_after.is_none()
            && unit.after_document_revision_id.is_none()
    }));
    assert!(first.review_units.iter().any(|unit| {
        unit.surface_kind == SurfaceKindV1::Binary
            && unit
                .limitation
                .as_deref()
                .is_some_and(|value| value.contains("Binary"))
    }));
    assert!(first.review_units.iter().any(|unit| {
        unit.surface_kind == SurfaceKindV1::Unsupported
            && unit
                .limitation
                .as_deref()
                .is_some_and(|value| value.contains("not valid UTF-8"))
    }));

    let unicode_corpus = first
        .corpus_documents
        .iter()
        .find(|document| {
            document.path == "src/Café.java"
                && document.snapshot_side == release_review_v1::SnapshotSideV1::After
        })
        .expect("Unicode path should be embedded");
    assert_eq!(unicode_corpus.materialization, MaterializationV1::Complete);
    let embedded_unicode = first.review_session.revision_lanes[0]
        .after
        .documents
        .iter()
        .find(|document| document.id == unicode_corpus.document_revision_id)
        .expect("complete corpus must have embedded UTF-8 bytes");
    assert_eq!(
        embedded_unicode.sha256,
        review_session_v1::sha256(embedded_unicode.text.as_bytes())
    );
    assert!(first.corpus_documents.iter().any(|document| {
        document.path == "binary.bin" && document.materialization == MaterializationV1::Partial
    }));
    let non_utf8_corpus = first
        .corpus_documents
        .iter()
        .find(|document| {
            document.path == "non-utf8.txt"
                && document.snapshot_side == release_review_v1::SnapshotSideV1::After
        })
        .expect("non-UTF-8 candidate corpus should remain explicit");
    assert_eq!(non_utf8_corpus.materialization, MaterializationV1::Partial);
    assert_eq!(
        non_utf8_corpus.sha256,
        review_session_v1::sha256(&[0xfe, b'b', b'\n'])
    );

    let query_ids = first
        .named_queries
        .iter()
        .map(|query| query.id.as_str())
        .collect::<BTreeSet<_>>();
    assert!(query_ids.contains("approved"));
    assert!(query_ids.contains("remaining"));
    assert_eq!(
        first.resume_state.active_query_id.as_deref(),
        Some("remaining")
    );
    assert_eq!(
        first.resume_state.current_unit_id.as_deref(),
        first.review_units.first().map(|unit| unit.id.as_str())
    );
    assert!(
        release_review_v1::query(&first, "approved")
            .expect("approved query")
            .review_unit_ids
            .is_empty()
    );
    assert_eq!(
        release_review_v1::query(&first, "remaining")
            .expect("remaining query")
            .review_unit_ids
            .len(),
        first.review_units.len()
    );
    let completion = release_review_v1::completion(&first).expect("completion status");
    assert_eq!(
        completion.status,
        release_review_v1::CompletionStatusV1::InProgress
    );
    assert!(completion.unsupported > 0);
    assert_eq!(first.producer_generations.len(), 1);
    assert_ne!(
        first.producer_generations[0].input_fingerprint,
        "0".repeat(64)
    );
    assert_ne!(
        first.producer_generations[0].output_fingerprint,
        "0".repeat(64)
    );

    assert_eq!(
        git_stdout(fixture.root(), ["rev-parse", "HEAD"]),
        head_before
    );
    assert_eq!(
        git_stdout(fixture.root(), ["symbolic-ref", "--short", "HEAD"]),
        branch_before
    );
    assert_eq!(git_stdout(fixture.root(), ["show-ref"]), refs_before);
    assert_eq!(
        git_stdout(fixture.root(), ["status", "--porcelain"]),
        status_before
    );
}

#[test]
fn non_utf8_git_path_is_explicit_and_does_not_disappear() {
    let fixture = changed_repository();
    let mut domain = produce_git_review_domain(fixture.root(), "review-before", "HEAD")
        .expect("Git domain should be complete");
    let change = domain
        .changes
        .iter_mut()
        .find(|change| change.status.kind == GitChangeKind::Added)
        .expect("added change");
    let change_id = change.change_id.clone();
    let invalid = GitPath::from_bytes(vec![b'n', b'o', b'n', b'-', 0xff, b'.', b't', b'x', b't']);
    change.new_path = Some(invalid.clone());
    for unit in domain
        .units
        .iter_mut()
        .filter(|unit| unit.change_id == change_id)
    {
        unit.new_path = Some(invalid.clone());
    }
    for path in domain
        .reconciliation
        .paths
        .iter_mut()
        .filter(|path| path.change_id == change_id)
    {
        path.path = invalid.clone();
    }

    let document = materialize_release_review(&domain, &fixture.config(&domain))
        .expect("non-UTF-8 path should remain explicit");
    let corpus = document
        .corpus_documents
        .iter()
        .find(|document| document.source_locator.contains("6e6f6e2dff2e747874"))
        .expect("raw path bytes should be retained");
    assert!(
        corpus
            .path
            .starts_with(".sfm-review/unsupported-path/bytes-")
    );
    assert_eq!(corpus.materialization, MaterializationV1::Partial);
    assert!(document.review_units.iter().any(|unit| {
        unit.path_after.as_deref() == Some(corpus.path.as_str())
            && unit.surface_kind == SurfaceKindV1::Unsupported
            && unit
                .limitation
                .as_deref()
                .is_some_and(|value| value.contains("not representable"))
    }));
    assert!(
        reconcile_materialization(&domain, &document)
            .expect("reconciliation")
            .complete
    );
}

#[test]
fn rejects_moving_or_mismatched_revision_inputs() {
    let fixture = changed_repository();
    let domain = produce_git_review_domain(fixture.root(), "review-before", "HEAD")
        .expect("Git domain should be complete");
    let mut config = fixture.config(&domain);
    config.candidate_revision = fixture.before_commit.clone();
    let error = materialize_release_review(&domain, &config)
        .expect_err("mismatched explicit pin must fail");
    assert!(error.to_string().contains("candidate revision differs"));
}

#[test]
#[expect(
    clippy::too_many_lines,
    reason = "one refresh scenario proves the complete progress-preservation transaction"
)]
fn refresh_preserves_human_progress_and_reconciles_added_removed_and_changed_units() {
    let fixture = changed_repository();
    let domain = produce_git_review_domain(fixture.root(), "review-before", "HEAD")
        .expect("Git domain should be complete");
    let mut existing = materialize_release_review(&domain, &fixture.config(&domain))
        .expect("initial materialization");
    let removed_id = existing.review_units[0].id.clone();
    add_human_progress(&mut existing, &removed_id);
    existing.review_session.title = "Maintainer-authored release review".to_owned();
    existing.review_session.style_rules[0].gutter_marker = Some("H".to_owned());
    existing
        .review_session
        .completion_policy
        .blocking_hashtags
        .push("#hold".to_owned());
    existing.producer_generations[0].generation = "git-materializer-v1:stale".to_owned();
    for unit in existing
        .review_units
        .iter_mut()
        .filter(|unit| unit.producer_id == PRODUCER_ID)
    {
        unit.producer_generation = "git-materializer-v1:stale".to_owned();
    }
    for comment in existing
        .review_session
        .comments
        .iter_mut()
        .filter(|comment| comment.provenance.producer == PRODUCER_ID)
    {
        comment.provenance.version = "git-materializer-v1:stale".to_owned();
    }
    let preserved_human_comment = existing
        .review_session
        .comments
        .iter()
        .find(|comment| comment.id == "human-approval")
        .expect("human comment")
        .clone();
    let preserved_bindings = existing.selector_bindings.clone();
    let preserved_migrations = existing.migration_reports.clone();
    let preserved_queries = existing.named_queries.clone();
    let preserved_resume = existing.resume_state.clone();
    let preserved_attestations = existing.completion_attestations.clone();
    let preserved_title = existing.review_session.title.clone();
    let preserved_styles = existing.review_session.style_rules.clone();
    let preserved_policy = existing.review_session.completion_policy.clone();

    let mut refreshed = materialize_release_review(&domain, &fixture.config(&domain))
        .expect("fresh materialization");
    let refreshed_generation = "git-materializer-v1:refreshed".to_owned();
    refreshed.producer_generations[0].generation = refreshed_generation.clone();
    refreshed.producer_generations[0].input_fingerprint = "a".repeat(64);
    refreshed.producer_generations[0].output_fingerprint = "b".repeat(64);
    for unit in &mut refreshed.review_units {
        unit.producer_generation.clone_from(&refreshed_generation);
    }
    for comment in &mut refreshed.review_session.comments {
        comment.provenance.version.clone_from(&refreshed_generation);
    }
    refreshed.review_units.retain(|unit| unit.id != removed_id);
    refreshed
        .review_session
        .comments
        .retain(|comment| comment.id != generated_comment_id(&removed_id));
    refreshed.resume_state.current_unit_id =
        refreshed.review_units.first().map(|unit| unit.id.clone());

    let changed_id = refreshed.review_units[0].id.clone();
    refreshed.review_units[0].limitation = Some("refreshed unit shape".to_owned());
    let mut added_unit = refreshed.review_units[1].clone();
    added_unit.id = "synthetic-added-unit".to_owned();
    let mut added_comment = refreshed
        .review_session
        .comments
        .iter()
        .find(|comment| comment.id == generated_comment_id(&refreshed.review_units[1].id))
        .expect("source generated comment")
        .clone();
    added_comment.id = generated_comment_id(&added_unit.id);
    added_comment.text = "#release-change #added\nSynthetic refreshed unit.".to_owned();
    refreshed.review_units.push(added_unit);
    refreshed.review_session.comments.push(added_comment);

    let result = merge_refreshed_materialization(&existing, &refreshed)
        .expect("refresh should preserve progress");
    let document = &result.document;
    assert_eq!(
        result.reconciliation.previous_generation,
        "git-materializer-v1:stale"
    );
    assert_eq!(
        result.reconciliation.refreshed_generation,
        refreshed_generation
    );
    assert_eq!(result.reconciliation.active_units_added, 1);
    assert_eq!(result.reconciliation.active_units_updated, 1);
    assert!(result.reconciliation.active_units_unchanged > 0);
    assert_eq!(result.reconciliation.retired_units_added, 1);
    assert_eq!(result.reconciliation.retired_units_carried, 0);
    assert_eq!(result.reconciliation.human_comments_preserved, 1);
    assert_eq!(result.reconciliation.selector_bindings_preserved, 1);
    assert_eq!(result.reconciliation.migration_reports_preserved, 1);
    assert_eq!(result.reconciliation.deferred_units_preserved, 1);
    assert_eq!(result.reconciliation.completion_attestations_preserved, 1);

    assert_eq!(
        document
            .review_session
            .comments
            .iter()
            .find(|comment| comment.id == "human-approval"),
        Some(&preserved_human_comment)
    );
    assert_eq!(document.selector_bindings, preserved_bindings);
    assert_eq!(document.migration_reports, preserved_migrations);
    assert_eq!(
        document
            .named_queries
            .iter()
            .map(|query| (query.id.as_str(), query.expression.as_str()))
            .collect::<std::collections::BTreeMap<_, _>>(),
        preserved_queries
            .iter()
            .map(|query| (query.id.as_str(), query.expression.as_str()))
            .collect::<std::collections::BTreeMap<_, _>>()
    );
    assert_eq!(document.resume_state, preserved_resume);
    assert_eq!(document.completion_attestations, preserved_attestations);
    assert_eq!(document.review_session.title, preserved_title);
    assert_eq!(document.review_session.style_rules, preserved_styles);
    assert_eq!(document.review_session.completion_policy, preserved_policy);
    assert!(document.review_units.iter().any(|unit| {
        unit.id == removed_id
            && unit.producer_id == RETIRED_PRODUCER_ID
            && unit.surface_kind == SurfaceKindV1::Unsupported
            && unit
                .limitation
                .as_deref()
                .is_some_and(|value| value.contains("no longer emits"))
    }));
    assert!(document.review_units.iter().any(|unit| {
        unit.id == changed_id && unit.limitation.as_deref() == Some("refreshed unit shape")
    }));
    assert!(
        document
            .review_units
            .iter()
            .any(|unit| unit.id == "synthetic-added-unit" && unit.producer_id == PRODUCER_ID)
    );
    let generated_ids = document
        .review_session
        .comments
        .iter()
        .filter(|comment| {
            matches!(
                comment.provenance.producer.as_str(),
                PRODUCER_ID | RETIRED_PRODUCER_ID
            )
        })
        .map(|comment| comment.id.as_str())
        .collect::<BTreeSet<_>>();
    assert_eq!(
        generated_ids.len(),
        result.reconciliation.generated_comments
    );
    let completion = release_review_v1::completion(document).expect("completion evidence");
    assert_eq!(
        completion.stale_producer, 0,
        "refresh must clear stale generations while retired units remain explicit"
    );
    assert!(
        completion.witnesses.blocking.contains(&removed_id),
        "a retired producer unit must remain fail-closed even if its old range had approval"
    );

    let repeated = merge_refreshed_materialization(document, &refreshed)
        .expect("repeated refresh should be deterministic");
    assert_eq!(repeated.document, *document);
    assert_eq!(repeated.reconciliation.retired_units_added, 0);
    assert_eq!(repeated.reconciliation.retired_units_carried, 1);
}

#[test]
fn refresh_refuses_candidate_and_source_snapshot_divergence() {
    let fixture = changed_repository();
    let domain = produce_git_review_domain(fixture.root(), "review-before", "HEAD")
        .expect("Git domain should be complete");
    let existing = materialize_release_review(&domain, &fixture.config(&domain))
        .expect("initial materialization");

    let mut candidate_diverged = existing.clone();
    candidate_diverged.repository_bindings[0].candidate_commit = Some("e".repeat(40));
    let candidate_error = merge_refreshed_materialization(&existing, &candidate_diverged)
        .expect_err("candidate divergence must fail closed");
    assert_eq!(
        candidate_error.code,
        "refresh.repository-binding-divergence"
    );

    let mut source_diverged = existing.clone();
    source_diverged.review_session.revision_lanes[0]
        .after
        .id
        .push_str("-different");
    let source_error = merge_refreshed_materialization(&existing, &source_diverged)
        .expect_err("source snapshot divergence must fail closed");
    assert_eq!(source_error.code, "refresh.source-snapshot-divergence");
}

fn add_human_progress(document: &mut release_review_v1::ReleaseReviewDocumentV1, unit_id: &str) {
    let unit = document
        .review_units
        .iter()
        .find(|unit| unit.id == unit_id)
        .expect("review unit");
    let (document_revision_id, range) = unit
        .after_document_revision_id
        .as_ref()
        .zip(unit.after_ranges.first())
        .or_else(|| {
            unit.before_document_revision_id
                .as_ref()
                .zip(unit.before_ranges.first())
        })
        .expect("fixture unit with a textual range");
    let corpus = document
        .corpus_documents
        .iter()
        .find(|corpus| corpus.document_revision_id == *document_revision_id)
        .expect("unit corpus");
    let captured_selection = PinnedSelectionV1 {
        selection_revision: "human-selection/1".to_owned(),
        source_expression: "selection://human-review".to_owned(),
        primary_range_index: 0,
        ranges: vec![PinnedSelectionRangeV1 {
            direction: SelectionDirectionV1::Forward,
            document_revision_id: document_revision_id.clone(),
            document_sha256: corpus.sha256.clone(),
            start_byte: range.start_byte,
            end_byte: range.end_byte,
        }],
    };
    let generated = document
        .review_session
        .comments
        .iter()
        .find(|comment| comment.id == generated_comment_id(unit_id))
        .expect("generated unit comment");
    let CommentTargetV2::CommittedSelection { selection_rule, .. } = &generated.target else {
        panic!("generated review comment must have a committed target");
    };
    let selection_rule = selection_rule.clone();
    let human_comment = CommentV2 {
        id: "human-approval".to_owned(),
        text: "#approved Human review progress.".to_owned(),
        provenance: ProvenanceV1 {
            kind: "human".to_owned(),
            producer: "maintainer".to_owned(),
            version: "1".to_owned(),
            parent_comment_ids: Vec::new(),
        },
        target: CommentTargetV2::CommittedSelection {
            selection_rule: selection_rule.clone(),
            candidate_promotion: None,
        },
        forbidden_authoritative_tags: None,
    };
    document.review_session.comments.push(human_comment);
    let proposal = SelectorProposalV1 {
        id: "human-selector".to_owned(),
        kind: SelectorKindV1::Literal,
        selection_rule,
        literal_witness: captured_selection.clone(),
        semantic_provider: None,
        semantic_key: None,
        semantic_provenance: vec![EvidenceV1 {
            key: "owner".to_owned(),
            value: "maintainer".to_owned(),
        }],
        confidence: ProposalConfidenceV1::Exact,
        projection_fingerprint: "c".repeat(64),
        source_snapshot_id: document_revision_id.clone(),
        diagnostics: Vec::new(),
    };
    document.selector_bindings.push(CommentSelectorBindingV1 {
        comment_id: "human-approval".to_owned(),
        captured_selection: captured_selection.clone(),
        selected_proposal: proposal,
    });
    let addressed = AddressedRangeV1 {
        document_revision_id: document_revision_id.clone(),
        start_byte: range.start_byte,
        end_byte: range.end_byte,
    };
    let evaluation = SelectorEvaluationResultV1 {
        selector_id: "human-selector".to_owned(),
        status: SelectorEvaluationStatusV1::Exact,
        ranges: vec![addressed.clone()],
        candidates: Vec::new(),
        invalidation_keys: Vec::new(),
        diagnostics: Vec::new(),
    };
    document.migration_reports.push(MigrationReportV1 {
        id: "human-migration-decision".to_owned(),
        source_selector_id: "human-selector".to_owned(),
        source_evaluation: evaluation.clone(),
        candidate_evaluation: evaluation,
        old_witnesses: captured_selection.ranges.clone(),
        new_candidates: vec![addressed],
        decision: MigrationDecisionV1::Deferred,
        decision_comment_id: Some("human-approval".to_owned()),
    });
    document.named_queries.push(NamedQueryV1 {
        id: "human-focus".to_owned(),
        expression: "remaining".to_owned(),
    });
    document.resume_state.active_query_id = Some("human-focus".to_owned());
    document.resume_state.active_query_expression = Some("remaining".to_owned());
    document.resume_state.current_unit_id = Some(unit_id.to_owned());
    document.resume_state.deferred_unit_ids = vec![unit_id.to_owned()];
    document.resume_state.generation = 17;
    document
        .completion_attestations
        .push(CompletionAttestationV1 {
            id: "prior-attestation".to_owned(),
            review_semantic_state_hash: "d".repeat(64),
            maintainer: "maintainer".to_owned(),
            attested_at: "2026-08-23T00:00:00Z".to_owned(),
            statement: "Prior state only; refresh must preserve this as stale evidence.".to_owned(),
        });
    release_review_v1::to_canonical_json(document).expect("human progress must remain valid");
}

struct GitFixture {
    repository: tempfile::TempDir,
    before_commit: String,
    candidate_commit: String,
}

impl GitFixture {
    fn root(&self) -> &Path {
        self.repository.path()
    }

    fn config(
        &self,
        domain: &release_review_git::GitReviewDomain,
    ) -> ReleaseReviewMaterializeConfig {
        ReleaseReviewMaterializeConfig {
            repository_root: self.root().to_path_buf(),
            lane_id: "1.19.2".to_owned(),
            repository_id: "sfm-test".to_owned(),
            root_hint: ".".to_owned(),
            before_label: "review-before".to_owned(),
            before_revision: domain.before.commit_id.clone(),
            after_label: "HEAD".to_owned(),
            candidate_revision: domain.candidate.commit_id.clone(),
            review_evidence_path: "docs/reviews/test.sfm-review.json".to_owned(),
        }
    }
}

fn changed_repository() -> GitFixture {
    let repository = tempfile::tempdir().expect("temporary Git repository");
    git(repository.path(), ["init", "--quiet"]);
    git(repository.path(), ["config", "user.name", "SFM Test"]);
    git(
        repository.path(),
        ["config", "user.email", "sfm-test@example.invalid"],
    );

    write(repository.path(), "delete.txt", b"deleted content\n");
    write(repository.path(), "modify.txt", b"before\nshared\n");
    write(repository.path(), "rename-old.txt", b"unique rename body\n");
    write(repository.path(), "copy-source.txt", b"stable copy body\n");
    write(repository.path(), "binary.bin", b"before\0binary\x01");
    write(repository.path(), "white space.txt", b"alpha\nbeta\n");
    write(
        repository.path(),
        "src/Café.java",
        "class Café { String value = \"avant\"; }\n".as_bytes(),
    );
    write(repository.path(), "non-utf8.txt", &[0xff, b'a', b'\n']);
    git(repository.path(), ["add", "--all"]);
    git(repository.path(), ["commit", "--quiet", "-m", "before"]);
    git(repository.path(), ["tag", "review-before"]);
    let before_commit = git_stdout(repository.path(), ["rev-parse", "HEAD"]);

    fs::remove_file(repository.path().join("delete.txt")).expect("delete fixture file");
    write(
        repository.path(),
        "add.txt",
        b"brand new unique content\nsecond line\n",
    );
    write(repository.path(), "modify.txt", b"after\nshared\nextra\n");
    fs::rename(
        repository.path().join("rename-old.txt"),
        repository.path().join("rename-new.txt"),
    )
    .expect("rename fixture file");
    fs::copy(
        repository.path().join("copy-source.txt"),
        repository.path().join("copy-destination.txt"),
    )
    .expect("copy fixture file");
    write(repository.path(), "binary.bin", b"after\0binary\x02");
    write(repository.path(), "white space.txt", b"alpha  \nbeta\n");
    write(
        repository.path(),
        "src/Café.java",
        "class Café { String value = \"après\"; }\n".as_bytes(),
    );
    write(repository.path(), "non-utf8.txt", &[0xfe, b'b', b'\n']);
    git(repository.path(), ["add", "--all"]);
    git(repository.path(), ["commit", "--quiet", "-m", "candidate"]);
    let candidate_commit = git_stdout(repository.path(), ["rev-parse", "HEAD"]);

    GitFixture {
        repository,
        before_commit,
        candidate_commit,
    }
}

fn write(root: &Path, relative: &str, contents: &[u8]) {
    let path = root.join(relative);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).expect("create fixture directory");
    }
    fs::write(&path, contents).unwrap_or_else(|error| panic!("write {relative}: {error}"));
}

fn git<const N: usize>(root: &Path, arguments: [&str; N]) -> Output {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(arguments)
        .stdin(Stdio::null())
        .output()
        .expect("Git should launch without a shell");
    assert!(
        output.status.success(),
        "Git failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    output
}

fn git_stdout<const N: usize>(root: &Path, arguments: [&str; N]) -> String {
    String::from_utf8(git(root, arguments).stdout)
        .expect("Git output should be UTF-8")
        .trim()
        .to_owned()
}
