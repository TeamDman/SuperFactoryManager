//! Read-only ledger materialization. The result is an observation, not saveable authority.

use crate::cancellation::CancellationToken;
use crate::release_review_capture_io::CaptureBudget;
use crate::release_review_ledger::Document;
use crate::release_review_ledger::GitReference;
use crate::release_review_ledger::ReviewLedger;
use crate::release_review_ledger::TargetLane;
use crate::release_review_ledger::{self as ledger};
use crate::release_review_v1 as review;
use crate::review_session_v1 as session;
use eyre::eyre;
use facet::Facet;
use sha1::Digest as _;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct Resolution {
    pub schema: String,
    pub document: review::ReleaseReviewDocumentV1,
    /// Exact identity and Git references for the source bodies already in `document`.
    pub source_evidence: Vec<Document>,
    pub diagnostics: Vec<String>,
}

/// Resolve explicit authorized repository roots. No write, checkout or Git fetch occurs.
/// # Errors
/// Rejects inconsistent targets, missing current sources, or mismatched evidence.
pub fn resolve(
    ledger: &ReviewLedger,
    review_path: &Path,
    roots: &BTreeMap<String, PathBuf>,
    cancellation: &CancellationToken,
) -> eyre::Result<Resolution> {
    let started = std::time::Instant::now();
    ledger::validate(ledger)?;
    let mut result = ledger.state.clone();
    result.schema = review::OBSERVATION_SCHEMA.into();
    let mut sources = BTreeMap::new();
    for target in &ledger.targets {
        cancellation.bail_if_cancelled()?;
        let root = roots
            .get(&target.id)
            .ok_or_else(|| eyre!("missing authorized target root: {}", target.id))?;
        let target_started = std::time::Instant::now();
        let mut current = resolve_target(target, root, review_path, cancellation)?;
        tracing::info!(phase = "target_materialization", lane = %target.id,
            elapsed_ms = target_started.elapsed().as_millis(),
            documents = current.corpus_documents.len(), units = current.review_units.len(),
            generated_comments = current.review_session.comments.len(),
            "Review resolution phase completed");
        collect_sources(&current, target, &mut sources)?;
        // The old materializers name their producer by implementation; scope it per lane.
        let producer_names: BTreeMap<_, _> = current
            .producer_generations
            .iter()
            .map(|p| {
                (
                    p.producer_id.clone(),
                    format!("{}:lane:{}", p.producer_id, target.id),
                )
            })
            .collect();
        for unit in &mut current.review_units {
            unit.producer_id = producer_names[&unit.producer_id].clone();
        }
        for producer in &mut current.producer_generations {
            producer.producer_id = producer_names[&producer.producer_id].clone();
        }
        result
            .repository_bindings
            .extend(current.repository_bindings);
        result.corpus_documents.extend(current.corpus_documents);
        result.review_units.extend(current.review_units);
        result
            .producer_generations
            .extend(current.producer_generations);
        result
            .review_session
            .revision_lanes
            .extend(current.review_session.revision_lanes);
        // Change surfaces belong to review_units/corpus_documents, not the human
        // annotation stream. Older materializers still emit synthetic comments
        // for their frozen-format callers; never import them into a live ledger
        // observation. Durable historical comments already in `result` remain
        // intact, including human comments using the #release-change hashtag.
        result.review_session.comments.extend(
            current
                .review_session
                .comments
                .into_iter()
                .filter(|comment| comment.provenance.kind != "generated"),
        );
    }
    let mut diagnostics = Vec::new();
    hydrate_history(
        ledger,
        roots,
        cancellation,
        &mut result,
        &mut sources,
        &mut diagnostics,
    )?;
    let unit_ids: BTreeSet<_> = result
        .review_units
        .iter()
        .map(|unit| unit.id.as_str())
        .collect();
    if result
        .resume_state
        .current_unit_id
        .as_deref()
        .is_some_and(|id| !unit_ids.contains(id))
    {
        result.resume_state.current_unit_id = None;
        diagnostics.push("Saved review position is outside this observation; the cursor was reset, not reassigned by ordinal".into());
    }
    result
        .resume_state
        .deferred_unit_ids
        .retain(|id| unit_ids.contains(id.as_str()));
    let document = validate_observation(&result)?;
    tracing::info!(
        phase = "resolve_total",
        elapsed_ms = started.elapsed().as_millis(),
        "Review resolution phase completed"
    );
    Ok(Resolution {
        schema: "sfm.release-review-resolution/3".into(),
        document,
        source_evidence: sources.into_values().collect(),
        diagnostics,
    })
}

fn validate_observation(
    result: &review::ReleaseReviewDocumentV1,
) -> eyre::Result<review::ReleaseReviewDocumentV1> {
    let started = std::time::Instant::now();
    let canonical = review::to_canonical_json(result)?;
    let document = review::parse(&canonical)?;
    tracing::info!(
        phase = "observation_validation",
        elapsed_ms = started.elapsed().as_millis(),
        serialized_bytes = canonical.len(),
        "Review resolution phase completed"
    );
    Ok(document)
}

fn resolve_target(
    target: &TargetLane,
    root: &Path,
    output: &Path,
    cancellation: &CancellationToken,
) -> eyre::Result<review::ReleaseReviewDocumentV1> {
    let evidence = output
        .strip_prefix(root)
        .map_err(|error| eyre!("review file must be inside its authorized repository: {error}"))?
        .to_string_lossy()
        .replace('\\', "/");
    if let Some(after) = &target.after_commit {
        if target.scope_paths != ["."] {
            return Err(eyre!(
                "scoped Git ledger targets are not yet supported; refusing to ignore scope"
            ));
        }
        let mut domain = crate::release_review_git::produce_git_review_domain(
            root,
            &target.before_commit,
            after,
        )
        .map_err(|error| eyre!(error))?;
        // A new export retains the old review exclusion and adds its own identity.
        // Do not erase a rename crossing an exclusion boundary: its source side is
        // needed to explain the included destination (and vice versa).
        domain.retain_changes(|change| {
            change.old_path.iter().chain(&change.new_path).any(|path| {
                !target
                    .excluded_paths
                    .iter()
                    .chain(std::iter::once(&evidence))
                    .any(|excluded| excluded_path(path.as_bytes(), excluded.as_bytes()))
            })
        });
        return crate::release_review_materialize::materialize_observation(
            &domain,
            &crate::release_review_materialize::ReleaseReviewMaterializeConfig {
                repository_root: root.into(),
                lane_id: target.id.clone(),
                repository_id: target.repository_id.clone(),
                root_hint: target.root_hint.clone(),
                before_label: target.before_commit.clone(),
                before_revision: target.before_commit.clone(),
                after_label: after.clone(),
                candidate_revision: after.clone(),
                review_evidence_path: evidence,
            },
        );
    }
    let config = crate::release_review_working_tree::WorkingTreeReviewConfig {
        repository_root: root.into(),
        lane_id: target.id.clone(),
        repository_id: target.repository_id.clone(),
        before: target.before_commit.clone(),
        scope_paths: target.scope_paths.clone(),
        excluded_paths: target.excluded_paths.clone(),
        include_untracked: target.include_untracked,
        review_evidence_path: evidence,
    };
    let capture_started = std::time::Instant::now();
    let input = crate::release_review_working_tree::capture(&config, cancellation)?;
    tracing::info!(phase = "working_tree_capture", lane = %target.id,
        elapsed_ms = capture_started.elapsed().as_millis(), entries = input.capture.entries.len(),
        "Review resolution phase completed");
    let materialize_started = std::time::Instant::now();
    let result =
        crate::release_review_working_tree_materialize::materialize_observation(&config, input)?;
    tracing::info!(phase = "working_tree_materialization", lane = %target.id,
        elapsed_ms = materialize_started.elapsed().as_millis(),
        "Review resolution phase completed");
    Ok(result)
}

fn excluded_path(path: &[u8], excluded: &[u8]) -> bool {
    excluded == b"."
        || path == excluded
        || path
            .strip_prefix(excluded)
            .is_some_and(|rest| rest.starts_with(b"/"))
}

fn collect_sources(
    current: &review::ReleaseReviewDocumentV1,
    target: &TargetLane,
    sources: &mut BTreeMap<String, Document>,
) -> eyre::Result<()> {
    let bodies: BTreeMap<_, _> = current
        .review_session
        .revision_lanes
        .iter()
        .flat_map(|lane| lane.before.documents.iter().chain(&lane.after.documents))
        .map(|doc| (doc.id.as_str(), doc))
        .collect();
    for corpus in &current.corpus_documents {
        let Some(body) = bodies.get(corpus.document_revision_id.as_str()) else {
            continue;
        };
        let commit = match corpus.snapshot_side {
            review::SnapshotSideV1::Before => Some(&target.before_commit),
            review::SnapshotSideV1::After => target.after_commit.as_ref(),
        };
        let git = commit.map(|commit| {
            let mut hash = Sha1::new();
            hash.update(format!("blob {}\0", body.text.len()).as_bytes());
            hash.update(body.text.as_bytes());
            GitReference {
                repository_id: target.repository_id.clone(),
                commit: commit.clone(),
                blob: format!("{:x}", hash.finalize()),
            }
        });
        let source = Document {
            revision_id: body.id.clone(),
            path: body.path.clone(),
            sha256: body.sha256.clone(),
            git,
        };
        if sources.insert(body.id.clone(), source).is_some() {
            return Err(eyre!("duplicate observation source identity"));
        }
    }
    Ok(())
}

fn hydrate_history(
    ledger: &ReviewLedger,
    roots: &BTreeMap<String, PathBuf>,
    cancellation: &CancellationToken,
    result: &mut review::ReleaseReviewDocumentV1,
    sources: &mut BTreeMap<String, Document>,
    diagnostics: &mut Vec<String>,
) -> eyre::Result<()> {
    let budget = CaptureBudget::new(cancellation);
    let contents: BTreeMap<_, _> = ledger
        .evidence
        .contents
        .iter()
        .map(|c| (c.sha256.as_str(), c.text.as_str()))
        .collect();
    for evidence in &ledger.evidence.documents {
        budget.check()?;
        if let Some(current) = sources.get(&evidence.revision_id) {
            if current.path != evidence.path
                || current.sha256 != evidence.sha256
                || current.git != evidence.git
            {
                return Err(eyre!("historical/current immutable identity collision"));
            }
            continue;
        }
        let storage = ledger
            .evidence
            .git_storage
            .iter()
            .find(|s| s.sha256 == evidence.sha256);
        let storage_reference = storage.map(|s| GitReference {
            repository_id: s.repository_id.clone(),
            commit: s.commit.clone(),
            blob: s.blob.clone(),
        });
        let reference = evidence.git.as_ref().or(storage_reference.as_ref());
        let storage_path = if evidence.git.is_some() {
            &evidence.path
        } else {
            storage.map_or(&evidence.path, |s| &s.path)
        };
        let body = if let Some(text) = contents.get(evidence.sha256.as_str()) {
            Some((*text).to_owned())
        } else if let Some(reference) = reference {
            let candidates: std::collections::BTreeSet<_> = ledger
                .targets
                .iter()
                .filter(|target| target.repository_id == reference.repository_id)
                .filter_map(|target| roots.get(&target.id))
                .collect();
            if candidates.len() != 1 {
                return Err(eyre!("unknown or ambiguous historical Git repository"));
            }
            let root = candidates.into_iter().next().expect("one root checked");
            match read_historical_git(&budget, root, storage_path, reference) {
                Ok(bytes) => {
                    if session::sha256(&bytes) != evidence.sha256 {
                        return Err(eyre!("historical Git evidence hash mismatch"));
                    }
                    Some(String::from_utf8(bytes)?)
                }
                Err(error) => {
                    diagnostics.push(format!(
                        "Historical Git evidence unavailable for {}: {error}",
                        evidence.revision_id
                    ));
                    None
                }
            }
        } else {
            return Err(eyre!("missing embedded historical evidence"));
        };
        add_history(result, evidence, body);
        sources.insert(evidence.revision_id.clone(), evidence.clone());
    }
    Ok(())
}

fn read_historical_git(
    budget: &CaptureBudget,
    root: &Path,
    path: &str,
    reference: &GitReference,
) -> eyre::Result<Vec<u8>> {
    let address = format!("{}:{path}", reference.commit);
    let actual = budget.git(
        root,
        &["rev-parse", "--verify", "--end-of-options", &address],
        None,
        1024,
    )?;
    if std::str::from_utf8(&actual)?.trim() != reference.blob {
        return Err(eyre!(
            "historical Git commit/path does not identify the recorded blob"
        ));
    }
    budget.git(
        root,
        &["cat-file", "blob", &reference.blob],
        None,
        crate::release_review_capture_io::FILE_LIMIT,
    )
}

fn add_history(
    result: &mut review::ReleaseReviewDocumentV1,
    source: &Document,
    body: Option<String>,
) {
    let lane_id = format!(
        "review-evidence:{}",
        session::sha256(source.revision_id.as_bytes())
    );
    let materialization = if body.is_some() {
        review::MaterializationV1::Complete
    } else {
        review::MaterializationV1::Missing
    };
    let documents = body
        .into_iter()
        .map(|text| session::DocumentRevisionV1 {
            id: source.revision_id.clone(),
            path: source.path.clone(),
            encoding: "utf-8".into(),
            sha256: source.sha256.clone(),
            text,
        })
        .collect();
    result
        .review_session
        .revision_lanes
        .push(session::RevisionLaneV1 {
            id: lane_id.clone(),
            repository: session::RepositoryV1 {
                id: review::EVIDENCE_OWNER.into(),
                root_hint: ".".into(),
            },
            version_label: Some("Historical comment evidence (not current source)".into()),
            before: session::SnapshotV1 {
                id: "evidence:empty".into(),
                documents: Vec::new(),
            },
            after: session::SnapshotV1 {
                id: format!("evidence:sha256:{}", source.sha256),
                documents,
            },
        });
    result.corpus_documents.push(review::CorpusDocumentV1 {
        id: format!("evidence-corpus:{}", source.revision_id),
        lane_id,
        snapshot_side: review::SnapshotSideV1::After,
        path: source.path.clone(),
        document_revision_id: source.revision_id.clone(),
        sha256: source.sha256.clone(),
        source_owner: review::EVIDENCE_OWNER.into(),
        source_locator: format!("review-evidence://sha256/{}", source.sha256),
        materialization,
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::review_session_v2 as comments;
    use std::process::Command;

    fn git(root: &Path, args: &[&str]) -> String {
        let output = Command::new("git")
            .arg("-C")
            .arg(root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().into()
    }

    fn fixture() -> (tempfile::TempDir, ReviewLedger) {
        let root = tempfile::tempdir().unwrap();
        git(root.path(), &["init", "--quiet"]);
        git(root.path(), &["config", "user.name", "Review test"]);
        git(
            root.path(),
            &["config", "user.email", "review@example.invalid"],
        );
        std::fs::write(root.path().join("A.java"), "class A { int x = 1; }\r\n").unwrap();
        git(root.path(), &["add", "A.java"]);
        git(root.path(), &["commit", "--quiet", "-m", "baseline"]);
        let before = git(root.path(), &["rev-parse", "HEAD"]);
        let mut state = review::parse(include_str!(
            "../../../../docs/architecture/fixtures/release-review-v1.json"
        ))
        .unwrap();
        state.repository_bindings.clear();
        state.corpus_documents.clear();
        state.review_units.clear();
        state.producer_generations.clear();
        state.selector_bindings.clear();
        state.migration_reports.clear();
        state.named_queries.clear();
        state.completion_attestations.clear();
        state.resume_state = review::ResumeStateV1::empty();
        state.review_session.revision_lanes.clear();
        state.review_session.comments.clear();
        state.review_session.style_rules.clear();
        let ledger = ReviewLedger {
            schema: ledger::SCHEMA.into(),
            state,
            evidence: ledger::EvidenceTable::default(),
            targets: vec![TargetLane {
                id: "main".into(),
                repository_id: "fixture".into(),
                root_hint: ".".into(),
                before_commit: before,
                after_commit: None,
                after_kind: "working_tree".into(),
                scope_paths: vec![".".into()],
                excluded_paths: vec!["review.json".into()],
                include_untracked: true,
            }],
        };
        (root, ledger)
    }

    fn observe(root: &Path, ledger: &ReviewLedger) -> Resolution {
        resolve(
            ledger,
            &root.join("review.json"),
            &BTreeMap::from([("main".into(), root.into())]),
            &CancellationToken::new(),
        )
        .unwrap()
    }

    #[test]
    fn copied_git_review_excludes_original_and_new_authority_without_dangling_units() {
        let (root, mut ledger) = fixture();
        std::fs::write(root.path().join("A.java"), "class A { int x = 2; }\r\n").unwrap();
        std::fs::write(root.path().join("review.json"), "original evidence").unwrap();
        std::fs::write(root.path().join("copy.json"), "export evidence").unwrap();
        git(root.path(), &["add", "."]);
        git(root.path(), &["commit", "--quiet", "-m", "candidate"]);
        ledger.targets[0].after_commit = Some(git(root.path(), &["rev-parse", "HEAD"]));
        ledger.targets[0].after_kind = "git".into();
        let serialized = ledger::to_json(&ledger).unwrap();
        let resolution = resolve(
            &ledger,
            &root.path().join("copy.json"),
            &BTreeMap::from([("main".into(), root.path().into())]),
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(!resolution.document.review_units.is_empty());
        assert!(resolution.document.review_session.comments.is_empty());
        assert!(resolution.document.review_session.style_rules.is_empty());
        assert_eq!(
            review::completion(&resolution.document)
                .unwrap()
                .approved_effective,
            0
        );
        assert!(
            resolution
                .document
                .review_session
                .revision_lanes
                .iter()
                .flat_map(|lane| lane.before.documents.iter().chain(&lane.after.documents))
                .all(|document| document.path == "A.java")
        );
        assert_eq!(serialized, ledger::to_json(&ledger).unwrap());
        assert!(review::to_canonical_json(&resolution.document).is_ok());
    }

    #[test]
    fn exclusion_is_segment_exact_not_a_text_prefix() {
        assert!(excluded_path(b"reviews/one.json", b"reviews"));
        assert!(!excluded_path(b"reviews-old/one.json", b"reviews"));
        assert!(excluded_path(b"copy.json", b"copy.json"));
        assert!(!excluded_path(b"copy.json.java", b"copy.json"));
    }

    #[test]
    fn historical_git_requires_commit_path_membership_not_only_an_existing_blob() {
        let (root, ledger) = fixture();
        let commit = &ledger.targets[0].before_commit;
        let blob = git(root.path(), &["rev-parse", &format!("{commit}:A.java")]);
        let reference = GitReference {
            repository_id: "fixture".into(),
            commit: commit.clone(),
            blob,
        };
        let mut evidence = Document {
            revision_id: "history".into(),
            path: "A.java".into(),
            sha256: String::new(),
            git: Some(reference.clone()),
        };
        let budget = CaptureBudget::new(&CancellationToken::new());
        assert!(read_historical_git(&budget, root.path(), &evidence.path, &reference).is_ok());
        evidence.path = "Missing.java".into();
        assert!(read_historical_git(&budget, root.path(), &evidence.path, &reference).is_err());
        evidence.path = "A.java".into();
        let mut wrong_blob = reference;
        wrong_blob.blob = "a".repeat(40);
        assert!(read_historical_git(&budget, root.path(), &evidence.path, &wrong_blob).is_err());
    }

    #[test]
    fn separate_git_storage_restores_original_non_git_identity() {
        let (root, mut ledger) = fixture();
        let commit = ledger.targets[0].before_commit.clone();
        let body = git(root.path(), &["show", &format!("{commit}:A.java")]);
        // git helper trims output: use exact blob bytes for the content digest.
        let token = CancellationToken::new();
        let budget = CaptureBudget::new(&token);
        let blob = git(root.path(), &["rev-parse", &format!("{commit}:A.java")]);
        let reference = GitReference {
            repository_id: "fixture".into(),
            commit: commit.clone(),
            blob: blob.clone(),
        };
        let bytes = read_historical_git(&budget, root.path(), "A.java", &reference).unwrap();
        assert!(String::from_utf8_lossy(&bytes).starts_with(&body));
        let original = Document {
            revision_id: "historical-disk-capture".into(),
            path: "before-rename.java".into(),
            sha256: session::sha256(&bytes),
            git: None,
        };
        ledger.evidence.documents.push(original.clone());
        ledger.evidence.git_storage.push(ledger::GitStorage {
            sha256: original.sha256.clone(),
            path: "A.java".into(),
            repository_id: "fixture".into(),
            commit,
            blob,
        });
        std::fs::write(root.path().join("A.java"), "different current bytes").unwrap();
        let resolved = observe(root.path(), &ledger);
        assert!(resolved.source_evidence.contains(&original));
        let historical = resolved
            .document
            .review_session
            .revision_lanes
            .iter()
            .flat_map(|lane| lane.after.documents.iter())
            .find(|doc| doc.id == original.revision_id)
            .unwrap();
        assert_eq!(historical.path, original.path);
        assert_eq!(historical.text.as_bytes(), bytes);
        assert_eq!(ledger.evidence.documents[0], original);
    }

    #[test]
    fn live_resolution_does_not_grow_authority_and_historical_approval_does_not_cover_changed_bytes()
     {
        let (root, mut ledger) = fixture();
        let authority = ledger::to_json(&ledger).unwrap();
        std::fs::write(root.path().join("review.json"), &authority).unwrap();
        std::fs::write(root.path().join("A.java"), "class A { int x = 2; }\r\n").unwrap();
        let first = observe(root.path(), &ledger);
        assert_eq!(1, first.document.review_units.len());
        assert!(first.document.review_session.comments.is_empty());
        assert!(first.document.review_session.style_rules.is_empty());
        assert!(!first.document.corpus_documents.is_empty());
        assert_eq!(
            authority,
            std::fs::read_to_string(root.path().join("review.json")).unwrap()
        );
        assert!(ledger.evidence.contents.is_empty());
        let mut rules = Vec::new();
        for source in &first.source_evidence {
            let body = first
                .document
                .review_session
                .revision_lanes
                .iter()
                .flat_map(|lane| lane.before.documents.iter().chain(&lane.after.documents))
                .find(|doc| doc.id == source.revision_id)
                .unwrap();
            rules.push(session::SelectionRuleV1::LiteralUtf8Range {
                document_revision_id: body.id.clone(),
                start_byte: 0,
                end_byte: body.text.len(),
                document_sha256: body.sha256.clone(),
                selected_text_sha256: body.sha256.clone(),
            });
            ledger.evidence.documents.push(source.clone());
            if source.git.is_none() {
                ledger.evidence.contents.push(ledger::Content {
                    sha256: body.sha256.clone(),
                    text: body.text.clone(),
                });
            }
        }
        ledger
            .state
            .review_session
            .comments
            .push(comments::CommentV2 {
                id: "human".into(),
                text: "#approved".into(),
                provenance: session::ProvenanceV1 {
                    kind: "human".into(),
                    producer: "test".into(),
                    version: "1".into(),
                    parent_comment_ids: vec![],
                },
                target: comments::CommentTargetV2::CommittedSelection {
                    selection_rule: session::SelectionRuleV1::Union { rules },
                    candidate_promotion: None,
                },
                forbidden_authoritative_tags: None,
            });
        bind_test_comment(&mut ledger);
        let mut missing_evidence = ledger.clone();
        missing_evidence.evidence = ledger::EvidenceTable::default();
        assert!(ledger::validate(&missing_evidence).is_err());
        let same = observe(root.path(), &ledger);
        assert_eq!(
            1,
            review::completion(&same.document)
                .unwrap()
                .approved_effective
        );
        std::fs::write(root.path().join("A.java"), "class A { int x = 3; }\r\n").unwrap();
        let changed = observe(root.path(), &ledger);
        assert_eq!(
            0,
            review::completion(&changed.document)
                .unwrap()
                .approved_effective
        );
        assert!(
            changed
                .document
                .corpus_documents
                .iter()
                .any(|c| c.source_owner == review::EVIDENCE_OWNER)
        );
        assert!(
            changed
                .document
                .review_session
                .revision_lanes
                .iter()
                .any(|lane| lane
                    .after
                    .documents
                    .iter()
                    .any(|doc| doc.text.contains("x = 2")))
        );
        let reopened = ledger::parse(&ledger::to_json(&ledger).unwrap()).unwrap();
        std::fs::remove_file(root.path().join("A.java")).unwrap();
        let deleted = observe(root.path(), &reopened);
        assert!(
            deleted
                .document
                .review_session
                .comments
                .iter()
                .any(|c| c.id == "human")
        );
        assert!(
            deleted
                .document
                .review_session
                .revision_lanes
                .iter()
                .any(|lane| lane
                    .after
                    .documents
                    .iter()
                    .any(|doc| doc.text.contains("x = 2")))
        );
        // The historical extension is opt-in: legacy envelopes still reject unbound evidence.
        let mut invalid = deleted.document;
        invalid.schema = review::WORKING_TREE_SCHEMA.into();
        assert!(review::to_canonical_json(&invalid).is_err());
    }

    fn bind_test_comment(ledger: &mut ReviewLedger) {
        let comments::CommentTargetV2::CommittedSelection { selection_rule, .. } =
            &ledger.state.review_session.comments[0].target
        else {
            unreachable!()
        };
        let session::SelectionRuleV1::Union { rules } = selection_rule else {
            unreachable!()
        };
        let ranges = rules
            .iter()
            .map(|rule| {
                let session::SelectionRuleV1::LiteralUtf8Range {
                    document_revision_id,
                    document_sha256,
                    start_byte,
                    end_byte,
                    ..
                } = rule
                else {
                    unreachable!()
                };
                review::PinnedSelectionRangeV1 {
                    direction: review::SelectionDirectionV1::Forward,
                    document_revision_id: document_revision_id.clone(),
                    document_sha256: document_sha256.clone(),
                    start_byte: *start_byte,
                    end_byte: *end_byte,
                }
            })
            .collect();
        let witness = review::PinnedSelectionV1 {
            selection_revision: "test".into(),
            source_expression: "explicit".into(),
            primary_range_index: 0,
            ranges,
        };
        ledger
            .state
            .selector_bindings
            .push(review::CommentSelectorBindingV1 {
                comment_id: "human".into(),
                captured_selection: witness.clone(),
                selected_proposal: review::SelectorProposalV1 {
                    id: "test".into(),
                    kind: review::SelectorKindV1::Literal,
                    selection_rule: selection_rule.clone(),
                    literal_witness: witness,
                    semantic_provider: None,
                    semantic_key: None,
                    semantic_provenance: vec![],
                    confidence: review::ProposalConfidenceV1::Exact,
                    projection_fingerprint: "0".repeat(64),
                    source_snapshot_id: "test".into(),
                    diagnostics: vec![],
                },
            });
    }
}
