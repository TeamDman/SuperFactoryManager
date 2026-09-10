//! Sparse, single-file review authority. Parsing never materializes source content.

use crate::release_review_v1;
use crate::review_session_v1;
use crate::review_session_v1::SelectionRuleV1;
use eyre::eyre;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

pub const SCHEMA: &str = "sfm.release-review/3";

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewLedger {
    pub schema: String,
    pub targets: Vec<TargetLane>,
    pub state: release_review_v1::ReleaseReviewDocumentV1,
    pub evidence: EvidenceTable,
}

impl ReviewLedger {
    /// A new authority contains policy and targets, never a materialized source corpus.
    #[must_use]
    pub fn new(id: String, title: String, targets: Vec<TargetLane>) -> Self {
        Self {
            schema: SCHEMA.into(),
            targets,
            evidence: EvidenceTable::default(),
            state: release_review_v1::ReleaseReviewDocumentV1 {
                schema: release_review_v1::SCHEMA.into(),
                review_session: crate::review_session_v2::ReviewSessionV2 {
                    schema: crate::review_session_v2::SCHEMA.into(),
                    id,
                    title,
                    coordinate_system: review_session_v1::COORDINATE_SYSTEM.into(),
                    revision_lanes: Vec::new(),
                    comments: Vec::new(),
                    style_rules: Vec::new(),
                    completion_policy: review_session_v1::CompletionPolicyV1 {
                        coverage_mode: "changed_surface".into(),
                        approval_hashtag: "#approved".into(),
                        blocking_hashtags: vec!["#needs-change".into()],
                    },
                },
                repository_bindings: Vec::new(),
                corpus_documents: Vec::new(),
                review_units: Vec::new(),
                selector_bindings: Vec::new(),
                migration_reports: Vec::new(),
                named_queries: Vec::new(),
                resume_state: release_review_v1::ResumeStateV1::empty(),
                producer_generations: Vec::new(),
                completion_attestations: Vec::new(),
            },
        }
    }
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct TargetLane {
    pub id: String,
    pub repository_id: String,
    pub root_hint: String,
    pub before_commit: String,
    #[facet(skip_serializing_if = Option::is_none)]
    pub after_commit: Option<String>,
    pub after_kind: String,
    pub scope_paths: Vec<String>,
    pub excluded_paths: Vec<String>,
    pub include_untracked: bool,
}

#[derive(Clone, Debug, Default, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct EvidenceTable {
    pub contents: Vec<Content>,
    pub documents: Vec<Document>,
    #[facet(default, skip_serializing_if = Vec::is_empty)]
    pub git_storage: Vec<GitStorage>,
}

/// Exact-byte storage provenance, independent of the original document identity.
#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct GitStorage {
    pub sha256: String,
    pub path: String,
    pub repository_id: String,
    pub commit: String,
    pub blob: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct Content {
    pub sha256: String,
    pub text: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct Document {
    pub revision_id: String,
    pub path: String,
    pub sha256: String,
    #[facet(skip_serializing_if = Option::is_none)]
    pub git: Option<GitReference>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct GitReference {
    pub repository_id: String,
    pub commit: String,
    pub blob: String,
}

#[derive(Facet)]
struct TaggedSurface {
    state: facet_json::RawJson<'static>,
}

/// # Errors
/// Rejects unknown fields, unsupported variants and inconsistent sparse evidence.
pub fn parse(input: &str) -> eyre::Result<ReviewLedger> {
    let tagged: TaggedSurface = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid ledger tagged surface: {error:?}"))?;
    release_review_v1::validate_tagged_contract_json(tagged.state.as_str())?;
    let ledger: ReviewLedger =
        facet_json::from_str(input).map_err(|error| eyre!("invalid review ledger: {error:?}"))?;
    validate(&ledger)?;
    Ok(ledger)
}

/// # Errors
/// Refuses to write an invalid ledger or derived browsing state.
pub fn to_json(ledger: &ReviewLedger) -> eyre::Result<String> {
    validate(ledger)?;
    let mut json = facet_json::to_string_pretty(ledger)?;
    json.push('\n');
    Ok(json)
}

/// Validate authority without requiring any Git object or working-tree read.
/// # Errors
/// Rejects malformed identities, populated observation fields or invalid evidence hashes.
pub fn validate(ledger: &ReviewLedger) -> eyre::Result<()> {
    if ledger.schema != SCHEMA || ledger.targets.is_empty() {
        return Err(eyre!("invalid review ledger schema or empty target set"));
    }
    let mut lanes = BTreeSet::new();
    for target in &ledger.targets {
        text(&target.id)?;
        text(&target.repository_id)?;
        text(&target.root_hint)?;
        hash(&target.before_commit, 40)?;
        if !lanes.insert(&target.id) || target.scope_paths.is_empty() {
            return Err(eyre!("duplicate target or empty scope"));
        }
        match (target.after_kind.as_str(), &target.after_commit) {
            ("git", Some(commit)) => hash(commit, 40)?,
            ("working_tree", None) => {}
            _ => return Err(eyre!("target kind/commit mismatch")),
        }
        for path in target.scope_paths.iter().chain(&target.excluded_paths) {
            relative_path(path)?;
        }
    }
    let state = &ledger.state;
    if state.schema != release_review_v1::SCHEMA
        || !state.repository_bindings.is_empty()
        || !state.corpus_documents.is_empty()
        || !state.review_units.is_empty()
        || !state.producer_generations.is_empty()
        || !state.review_session.revision_lanes.is_empty()
        || state
            .review_session
            .comments
            .iter()
            .any(|c| c.provenance.kind == "generated")
    {
        return Err(eyre!("ledger cannot contain derived browsing data"));
    }
    crate::review_session_v2::validate(&state.review_session)?;
    validate_evidence(&ledger.evidence)?;
    validate_comment_evidence(ledger)
}

fn validate_comment_evidence(ledger: &ReviewLedger) -> eyre::Result<()> {
    let documents: BTreeMap<_, _> = ledger
        .evidence
        .documents
        .iter()
        .map(|d| (d.revision_id.as_str(), d))
        .collect();
    let contents: BTreeMap<_, _> = ledger
        .evidence
        .contents
        .iter()
        .map(|c| (c.sha256.as_str(), c.text.as_str()))
        .collect();
    let mut bindings = BTreeMap::new();
    for binding in &ledger.state.selector_bindings {
        if bindings
            .insert(binding.comment_id.as_str(), binding)
            .is_some()
        {
            return Err(eyre!("duplicate durable comment selector binding"));
        }
    }
    for comment in &ledger.state.review_session.comments {
        let crate::review_session_v2::CommentTargetV2::CommittedSelection {
            selection_rule, ..
        } = &comment.target
        else {
            return Err(eyre!(
                "durable source comment requires a committed selector"
            ));
        };
        let binding = bindings
            .remove(comment.id.as_str())
            .ok_or_else(|| eyre!("missing durable selector binding"))?;
        let proposal = &binding.selected_proposal;
        if &proposal.selection_rule != selection_rule
            || binding.captured_selection != proposal.literal_witness
        {
            return Err(eyre!("durable selector binding mismatch"));
        }
        for range in &proposal.literal_witness.ranges {
            retained_range(
                &documents,
                &contents,
                &range.document_revision_id,
                &range.document_sha256,
                range.start_byte,
                range.end_byte,
            )?;
        }
        let mut pending = vec![(selection_rule, 0)];
        let mut visited = 0;
        while let Some((rule, depth)) = pending.pop() {
            visited += 1;
            if depth > 64 || visited > 100_000 {
                return Err(eyre!("evidence selector exceeds bounds"));
            }
            match rule {
                SelectionRuleV1::LiteralUtf8Range {
                    document_revision_id,
                    document_sha256,
                    start_byte,
                    end_byte,
                    selected_text_sha256,
                } => {
                    if let Some(slice) = retained_range(
                        &documents,
                        &contents,
                        document_revision_id,
                        document_sha256,
                        *start_byte,
                        *end_byte,
                    )? && review_session_v1::sha256(slice.as_bytes()) != *selected_text_sha256
                    {
                        return Err(eyre!("retained selected evidence hash mismatch"));
                    }
                }
                SelectionRuleV1::Union { rules } | SelectionRuleV1::Intersection { rules } => {
                    pending.extend(rules.iter().map(|r| (r, depth + 1)));
                }
                SelectionRuleV1::Difference { include, exclude } => {
                    pending.push((include, depth + 1));
                    pending.extend(exclude.iter().map(|r| (r, depth + 1)));
                }
            }
        }
    }
    if !bindings.is_empty() {
        return Err(eyre!("orphan durable selector binding"));
    }
    Ok(())
}

fn retained_range<'a>(
    documents: &BTreeMap<&str, &Document>,
    contents: &BTreeMap<&str, &'a str>,
    id: &str,
    sha: &str,
    start: usize,
    end: usize,
) -> eyre::Result<Option<&'a str>> {
    let document = documents
        .get(id)
        .ok_or_else(|| eyre!("saved comment is missing retained evidence"))?;
    if document.sha256 != sha || start > end {
        return Err(eyre!("retained evidence identity/range mismatch"));
    }
    contents
        .get(sha)
        .map(|text| {
            text.get(start..end)
                .ok_or_else(|| eyre!("retained evidence range is outside UTF-8 boundaries"))
        })
        .transpose()
}

fn validate_evidence(evidence: &EvidenceTable) -> eyre::Result<()> {
    let mut storage_hashes = BTreeSet::new();
    for storage in &evidence.git_storage {
        hash(&storage.sha256, 64)?;
        hash(&storage.commit, 40)?;
        hash(&storage.blob, 40)?;
        text(&storage.repository_id)?;
        if storage.path.contains(['\\', ':'])
            || storage
                .path
                .split('/')
                .any(|part| part.is_empty() || part == "." || part == "..")
            || !storage_hashes.insert(&storage.sha256)
        {
            return Err(eyre!("invalid or duplicate Git storage reference"));
        }
    }
    let mut hashes = BTreeSet::new();
    for content in &evidence.contents {
        hash(&content.sha256, 64)?;
        if review_session_v1::sha256(content.text.as_bytes()) != content.sha256
            || !hashes.insert(&content.sha256)
        {
            return Err(eyre!("invalid or duplicate embedded evidence"));
        }
    }
    let mut identities = BTreeSet::new();
    let mut referenced = BTreeSet::new();
    for document in &evidence.documents {
        text(&document.revision_id)?;
        text(&document.path)?;
        hash(&document.sha256, 64)?;
        if !identities.insert(&document.revision_id) {
            return Err(eyre!("duplicate evidence document identity"));
        }
        referenced.insert(&document.sha256);
        if let Some(git) = &document.git {
            text(&git.repository_id)?;
            hash(&git.commit, 40)?;
            hash(&git.blob, 40)?;
        } else if !hashes.contains(&document.sha256) && !storage_hashes.contains(&document.sha256) {
            return Err(eyre!("non-Git evidence requires embedded content"));
        }
    }
    if !hashes.is_subset(&referenced) || !storage_hashes.is_subset(&referenced) {
        return Err(eyre!("unreferenced embedded evidence"));
    }
    Ok(())
}

fn text(value: &str) -> eyre::Result<()> {
    if value.trim().is_empty() {
        return Err(eyre!("missing ledger identity"));
    }
    Ok(())
}

fn hash(value: &str, length: usize) -> eyre::Result<()> {
    if value.len() != length
        || !value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
    {
        return Err(eyre!("invalid ledger hash"));
    }
    Ok(())
}

fn relative_path(value: &str) -> eyre::Result<()> {
    if value == "." {
        return Ok(());
    }
    if value.contains(['\\', ':'])
        || value
            .split('/')
            .any(|part| part.is_empty() || part == "." || part == "..")
    {
        return Err(eyre!("scope must be a normalized repository-relative path"));
    }
    text(value)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ledger() -> ReviewLedger {
        let mut state = release_review_v1::parse(include_str!(
            "../../../../docs/architecture/fixtures/release-review-v1.json"
        ))
        .unwrap();
        state.repository_bindings.clear();
        state.corpus_documents.clear();
        state.review_units.clear();
        state.producer_generations.clear();
        state.review_session.revision_lanes.clear();
        state.review_session.comments.clear();
        state.selector_bindings.clear();
        state.migration_reports.clear();
        state.completion_attestations.clear();
        ReviewLedger {
            schema: SCHEMA.into(),
            targets: vec![TargetLane {
                id: "main".into(),
                repository_id: "sfm".into(),
                root_hint: ".".into(),
                before_commit: "a".repeat(40),
                after_commit: None,
                after_kind: "working_tree".into(),
                scope_paths: vec![".".into()],
                excluded_paths: vec!["review.json".into()],
                include_untracked: true,
            }],
            state,
            evidence: EvidenceTable::default(),
        }
    }

    #[test]
    fn sparse_authority_round_trips_without_source_resolution() {
        let value = ledger();
        let encoded = to_json(&value).unwrap();
        assert_eq!(value, parse(&encoded).unwrap());
        assert!(encoded.len() < 8_000);
        assert!(parse(&encoded.replacen("{", "{\"unexpected\":true,", 1)).is_err());
    }

    #[test]
    fn evidence_is_exact_and_git_references_are_immutable() {
        let mut value = ledger();
        let body = "hello 😀\r\n";
        let sha = review_session_v1::sha256(body.as_bytes());
        value.evidence.contents.push(Content {
            sha256: sha.clone(),
            text: body.into(),
        });
        value.evidence.documents.push(Document {
            revision_id: "doc".into(),
            path: "A.java".into(),
            sha256: sha,
            git: None,
        });
        assert_eq!(value, parse(&to_json(&value).unwrap()).unwrap());
        value.evidence.contents[0].text.push('x');
        assert!(validate(&value).is_err());
        value.evidence.contents.clear();
        value.evidence.documents[0].git = Some(GitReference {
            repository_id: "sfm".into(),
            commit: "HEAD".into(),
            blob: "b".repeat(40),
        });
        assert!(validate(&value).is_err());
        value.evidence.documents[0].git.as_mut().unwrap().commit = "a".repeat(40);
        assert!(validate(&value).is_ok());
    }

    #[test]
    fn separate_storage_roundtrips_without_mutating_non_git_identity() {
        let mut value = ledger();
        let sha = review_session_v1::sha256(b"captured");
        let document = Document {
            revision_id: "disk-capture".into(),
            path: "old.java".into(),
            sha256: sha.clone(),
            git: None,
        };
        value.evidence.documents.push(document.clone());
        value.evidence.git_storage.push(GitStorage {
            sha256: sha,
            path: "new.java".into(),
            repository_id: "sfm".into(),
            commit: "a".repeat(40),
            blob: "b".repeat(40),
        });
        assert_eq!(value, parse(&to_json(&value).unwrap()).unwrap());
        assert_eq!(document, value.evidence.documents[0]);
        value
            .evidence
            .git_storage
            .push(value.evidence.git_storage[0].clone());
        assert!(validate(&value).is_err());
        value.evidence.git_storage.pop();
        value.evidence.git_storage[0].path = "../escape".into();
        assert!(validate(&value).is_err());
        value.evidence.git_storage[0].path = "new.java".into();
        value.evidence.git_storage[0].commit = "HEAD".into();
        assert!(validate(&value).is_err());
    }
}
