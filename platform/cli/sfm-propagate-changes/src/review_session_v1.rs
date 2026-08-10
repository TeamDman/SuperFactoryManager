//! Frozen `sfm.review-session/1` model and pure conformance kernel.

use eyre::Context;
use eyre::eyre;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;
use unicode_normalization::UnicodeNormalization;

pub const SCHEMA: &str = "sfm.review-session/1";
pub const COORDINATE_SYSTEM: &str = "utf8_byte_half_open";
pub const EVALUATOR_VERSION: &str = "sfm-review-v1/1";

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct ReviewSessionV1 {
    pub schema: String,
    pub id: String,
    pub title: String,
    pub coordinate_system: String,
    pub revision_lanes: Vec<RevisionLaneV1>,
    pub comments: Vec<CommentV1>,
    pub style_rules: Vec<CommentStyleRuleV1>,
    pub completion_policy: CompletionPolicyV1,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct RevisionLaneV1 {
    pub id: String,
    pub repository: RepositoryV1,
    #[facet(skip_serializing_if = Option::is_none)]
    pub version_label: Option<String>,
    pub before: SnapshotV1,
    pub after: SnapshotV1,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct RepositoryV1 {
    pub id: String,
    pub root_hint: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct SnapshotV1 {
    pub id: String,
    pub documents: Vec<DocumentRevisionV1>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct DocumentRevisionV1 {
    pub id: String,
    pub path: String,
    pub encoding: String,
    pub sha256: String,
    pub text: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct CommentV1 {
    pub id: String,
    pub text: String,
    pub provenance: ProvenanceV1,
    pub selection_rule: SelectionRuleV1,
    #[facet(rename = "tags", skip_serializing_if = Option::is_none)]
    pub(crate) forbidden_authoritative_tags: Option<Vec<String>>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct ProvenanceV1 {
    pub kind: String,
    pub producer: String,
    pub version: String,
    pub parent_comment_ids: Vec<String>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(tag = "kind", rename_all = "snake_case")]
#[repr(C)]
pub enum SelectionRuleV1 {
    LiteralUtf8Range {
        document_revision_id: String,
        start_byte: usize,
        end_byte: usize,
        document_sha256: String,
        selected_text_sha256: String,
    },
    Union {
        rules: Vec<SelectionRuleV1>,
    },
    Intersection {
        rules: Vec<SelectionRuleV1>,
    },
    Difference {
        include: Box<SelectionRuleV1>,
        exclude: Vec<SelectionRuleV1>,
    },
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct CommentStyleRuleV1 {
    pub id: String,
    pub required_hashtags: Vec<String>,
    pub priority: i32,
    #[facet(skip_serializing_if = Option::is_none)]
    pub foreground: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub background: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub underline: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub gutter_marker: Option<String>,
    pub enabled: bool,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct CompletionPolicyV1 {
    pub coverage_mode: String,
    pub approval_hashtag: String,
    pub blocking_hashtags: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum EvaluationStatusV1 {
    ResolvedExactly,
    ResolvedWithRelocation,
    Ambiguous,
    NoMatch,
    InvalidRule,
    ScopeMissing,
    ContentChanged,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
pub struct DocumentRangeV1 {
    pub document_revision_id: String,
    pub start_byte: usize,
    pub end_byte: usize,
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub struct CommentEvaluationV1 {
    pub comment_id: String,
    pub evaluator_version: String,
    pub status: EvaluationStatusV1,
    pub ranges: Vec<DocumentRangeV1>,
    pub diagnostics: Vec<String>,
}

#[derive(Clone, Debug)]
struct RuleResult {
    status: EvaluationStatusV1,
    ranges: Vec<DocumentRangeV1>,
    diagnostics: Vec<String>,
}

/// Parse and validate a v1 review session.
///
/// # Errors
///
/// Returns an error when the JSON does not match the frozen schema or violates
/// a v1 invariant.
pub fn parse(input: &str) -> eyre::Result<ReviewSessionV1> {
    let session: ReviewSessionV1 = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid review-session v1 JSON: {error:?}"))?;
    validate(&session)?;
    Ok(session)
}

/// Serialize a validated session using the canonical v1 presentation.
///
/// # Errors
///
/// Returns an error when the session violates the v1 schema or serialization
/// fails.
pub fn to_canonical_json(session: &ReviewSessionV1) -> eyre::Result<String> {
    validate(session)?;
    let mut output =
        facet_json::to_string_pretty(session).wrap_err("could not serialize review-session v1")?;
    output.push('\n');
    Ok(output)
}

/// Derive normalized, de-duplicated hashtags from authoritative comment text.
#[must_use]
pub fn derived_hashtags(text: &str) -> Vec<String> {
    let normalized_text = text.nfc().collect::<String>();
    let chars: Vec<char> = normalized_text.chars().collect();
    let mut result = Vec::new();
    let mut seen = BTreeSet::new();
    let mut index = 0;
    while index < chars.len() {
        if chars[index] != '#' {
            index += 1;
            continue;
        }
        if index > 0 && (chars[index - 1].is_alphanumeric() || chars[index - 1] == '_') {
            index += 1;
            continue;
        }
        let mut cursor = index + 1;
        let tag = if cursor < chars.len() && chars[cursor] == '"' {
            cursor += 1;
            let mut value = String::new();
            let mut closed = false;
            while cursor < chars.len() {
                let character = chars[cursor];
                cursor += 1;
                if character == '"' {
                    closed = true;
                    break;
                }
                if character == '\\' && cursor < chars.len() && matches!(chars[cursor], '\\' | '"')
                {
                    value.push(chars[cursor]);
                    cursor += 1;
                } else {
                    value.push(character);
                }
            }
            (closed && !value.trim().is_empty()).then(|| format!("#\"{}\"", normalize_tag(&value)))
        } else {
            let start = cursor;
            while cursor < chars.len()
                && (chars[cursor].is_alphanumeric() || matches!(chars[cursor], '_' | '-'))
            {
                cursor += 1;
            }
            (cursor > start).then(|| {
                format!(
                    "#{}",
                    normalize_tag(&chars[start..cursor].iter().collect::<String>())
                )
            })
        };
        if let Some(tag) = tag
            && seen.insert(tag.clone())
        {
            result.push(tag);
        }
        index = cursor.max(index + 1);
    }
    result
}

/// Evaluate all comment selectors against their embedded document snapshots.
///
/// # Errors
///
/// Returns an error when the session itself is invalid. Individual selector
/// failures are represented by deterministic evaluation statuses.
pub fn evaluate_all(session: &ReviewSessionV1) -> eyre::Result<Vec<CommentEvaluationV1>> {
    validate(session)?;
    let documents = document_map(session)?;
    Ok(session
        .comments
        .iter()
        .map(|comment| {
            let result = evaluate_rule(&comment.selection_rule, &documents);
            CommentEvaluationV1 {
                comment_id: comment.id.clone(),
                evaluator_version: EVALUATOR_VERSION.to_owned(),
                status: result.status,
                ranges: result.ranges,
                diagnostics: result.diagnostics,
            }
        })
        .collect())
}

/// Return whether a resolved comment contributes effective approval.
#[must_use]
pub fn is_approval_effective(
    session: &ReviewSessionV1,
    comment: &CommentV1,
    evaluation: &CommentEvaluationV1,
) -> bool {
    let hashtags = derived_hashtags(&comment.text);
    let approval = normalize_serialized_hashtag(&session.completion_policy.approval_hashtag);
    is_resolved(evaluation.status)
        && hashtags.contains(&approval)
        && session
            .completion_policy
            .blocking_hashtags
            .iter()
            .map(|tag| normalize_serialized_hashtag(tag))
            .all(|tag| !hashtags.contains(&tag))
}

/// Count overlapping pairs of resolved comment selections.
#[must_use]
pub fn overlap_pair_count(evaluations: &[CommentEvaluationV1]) -> usize {
    let mut count = 0;
    for left in 0..evaluations.len() {
        for right in left + 1..evaluations.len() {
            if evaluations[left].ranges.iter().any(|a| {
                evaluations[right].ranges.iter().any(|b| {
                    a.document_revision_id == b.document_revision_id
                        && a.start_byte.max(b.start_byte) < a.end_byte.min(b.end_byte)
                })
            }) {
                count += 1;
            }
        }
    }
    count
}

/// Convert a checked UTF-8 byte boundary into a Unicode scalar index.
///
/// # Errors
///
/// Returns an error when the offset is out of bounds or splits a UTF-8 code
/// point.
pub fn utf8_byte_to_char_index(text: &str, byte_offset: usize) -> eyre::Result<usize> {
    if !text.is_char_boundary(byte_offset) {
        return Err(eyre!("offset {byte_offset} is not a UTF-8 boundary"));
    }
    Ok(text[..byte_offset].chars().count())
}

/// Compute lowercase hexadecimal SHA-256.
#[must_use]
pub fn sha256(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}

fn evaluate_rule(
    rule: &SelectionRuleV1,
    documents: &BTreeMap<String, &DocumentRevisionV1>,
) -> RuleResult {
    match rule {
        SelectionRuleV1::LiteralUtf8Range {
            document_revision_id,
            start_byte,
            end_byte,
            document_sha256,
            selected_text_sha256,
        } => evaluate_literal(
            document_revision_id,
            *start_byte,
            *end_byte,
            document_sha256,
            selected_text_sha256,
            documents,
        ),
        SelectionRuleV1::Union { rules } => evaluate_set(rules, documents, true),
        SelectionRuleV1::Intersection { rules } => evaluate_set(rules, documents, false),
        SelectionRuleV1::Difference { include, exclude } => {
            let mut included = evaluate_rule(include, documents);
            if !is_resolved(included.status) {
                return included;
            }
            for rule in exclude {
                let excluded = evaluate_rule(rule, documents);
                if !is_resolved(excluded.status) {
                    return excluded;
                }
                if excluded.status == EvaluationStatusV1::ResolvedWithRelocation {
                    included.status = EvaluationStatusV1::ResolvedWithRelocation;
                }
                included.ranges = difference(&included.ranges, &excluded.ranges);
                included.diagnostics.extend(excluded.diagnostics);
            }
            if included.ranges.is_empty() {
                included.status = EvaluationStatusV1::NoMatch;
            }
            included
        }
    }
}

fn evaluate_set(
    rules: &[SelectionRuleV1],
    documents: &BTreeMap<String, &DocumentRevisionV1>,
    is_union: bool,
) -> RuleResult {
    if rules.is_empty() {
        return RuleResult {
            status: EvaluationStatusV1::InvalidRule,
            ranges: Vec::new(),
            diagnostics: vec!["set rule requires children".to_owned()],
        };
    }
    let mut ranges: Option<Vec<DocumentRangeV1>> = None;
    let mut status = EvaluationStatusV1::ResolvedExactly;
    let mut diagnostics = Vec::new();
    for rule in rules {
        let child = evaluate_rule(rule, documents);
        if !is_resolved(child.status) {
            return child;
        }
        if child.status == EvaluationStatusV1::ResolvedWithRelocation {
            status = EvaluationStatusV1::ResolvedWithRelocation;
        }
        ranges = Some(match ranges {
            None => child.ranges,
            Some(current) if is_union => union(&current, &child.ranges),
            Some(current) => intersection(&current, &child.ranges),
        });
        diagnostics.extend(child.diagnostics);
    }
    let ranges = normalize(ranges.unwrap_or_default());
    if ranges.is_empty() {
        status = EvaluationStatusV1::NoMatch;
    }
    RuleResult {
        status,
        ranges,
        diagnostics,
    }
}

fn evaluate_literal(
    document_revision_id: &str,
    start_byte: usize,
    end_byte: usize,
    document_sha256: &str,
    selected_text_sha256: &str,
    documents: &BTreeMap<String, &DocumentRevisionV1>,
) -> RuleResult {
    let Some(document) = documents.get(document_revision_id) else {
        return unresolved(
            EvaluationStatusV1::ScopeMissing,
            format!("document revision not found: {document_revision_id}"),
        );
    };
    if end_byte < start_byte
        || end_byte > document.text.len()
        || !document.text.is_char_boundary(start_byte)
        || !document.text.is_char_boundary(end_byte)
    {
        return unresolved(
            EvaluationStatusV1::InvalidRule,
            "range is outside the document or splits UTF-8",
        );
    }
    if !valid_hash(document_sha256)
        || !valid_hash(selected_text_sha256)
        || !valid_hash(&document.sha256)
    {
        return unresolved(
            EvaluationStatusV1::InvalidRule,
            "hashes must be lowercase SHA-256 hex",
        );
    }
    let original = DocumentRangeV1 {
        document_revision_id: document.id.clone(),
        start_byte,
        end_byte,
    };
    let actual_document_hash = sha256(document.text.as_bytes());
    let actual_selected_hash = sha256(&document.text.as_bytes()[start_byte..end_byte]);
    if actual_document_hash == document.sha256
        && actual_document_hash == document_sha256
        && actual_selected_hash == selected_text_sha256
    {
        return RuleResult {
            status: EvaluationStatusV1::ResolvedExactly,
            ranges: vec![original],
            diagnostics: Vec::new(),
        };
    }
    let length = end_byte - start_byte;
    let mut candidates = Vec::new();
    for start in 0..=document.text.len().saturating_sub(length) {
        let end = start + length;
        if document.text.is_char_boundary(start)
            && document.text.is_char_boundary(end)
            && sha256(&document.text.as_bytes()[start..end]) == selected_text_sha256
        {
            candidates.push(DocumentRangeV1 {
                document_revision_id: document.id.clone(),
                start_byte: start,
                end_byte: end,
            });
        }
    }
    match candidates.len() {
        1 => RuleResult {
            status: EvaluationStatusV1::ResolvedWithRelocation,
            ranges: candidates,
            diagnostics: vec![
                "document changed; selected-text witness relocated uniquely".to_owned(),
            ],
        },
        2.. => RuleResult {
            status: EvaluationStatusV1::Ambiguous,
            diagnostics: vec![format!(
                "selected-text witness has {} matches",
                candidates.len()
            )],
            ranges: candidates,
        },
        _ => RuleResult {
            status: EvaluationStatusV1::ContentChanged,
            ranges: vec![original],
            diagnostics: vec!["document or selected-text SHA-256 witness changed".to_owned()],
        },
    }
}

fn unresolved(status: EvaluationStatusV1, diagnostic: impl Into<String>) -> RuleResult {
    RuleResult {
        status,
        ranges: Vec::new(),
        diagnostics: vec![diagnostic.into()],
    }
}

fn union(left: &[DocumentRangeV1], right: &[DocumentRangeV1]) -> Vec<DocumentRangeV1> {
    normalize(left.iter().chain(right).cloned().collect())
}

fn intersection(left: &[DocumentRangeV1], right: &[DocumentRangeV1]) -> Vec<DocumentRangeV1> {
    normalize(
        left.iter()
            .flat_map(|a| {
                right.iter().filter_map(move |b| {
                    if a.document_revision_id != b.document_revision_id {
                        return None;
                    }
                    let start = a.start_byte.max(b.start_byte);
                    let end = a.end_byte.min(b.end_byte);
                    (start < end).then(|| DocumentRangeV1 {
                        document_revision_id: a.document_revision_id.clone(),
                        start_byte: start,
                        end_byte: end,
                    })
                })
            })
            .collect(),
    )
}

fn difference(include: &[DocumentRangeV1], exclude: &[DocumentRangeV1]) -> Vec<DocumentRangeV1> {
    let mut current = normalize(include.to_vec());
    for cut in normalize(exclude.to_vec()) {
        let mut next = Vec::new();
        for range in current {
            if range.document_revision_id != cut.document_revision_id
                || cut.end_byte <= range.start_byte
                || cut.start_byte >= range.end_byte
            {
                next.push(range);
            } else {
                if range.start_byte < cut.start_byte {
                    next.push(DocumentRangeV1 {
                        document_revision_id: range.document_revision_id.clone(),
                        start_byte: range.start_byte,
                        end_byte: cut.start_byte,
                    });
                }
                if cut.end_byte < range.end_byte {
                    next.push(DocumentRangeV1 {
                        document_revision_id: range.document_revision_id,
                        start_byte: cut.end_byte,
                        end_byte: range.end_byte,
                    });
                }
            }
        }
        current = next;
    }
    normalize(current)
}

fn normalize(mut ranges: Vec<DocumentRangeV1>) -> Vec<DocumentRangeV1> {
    ranges.retain(|range| range.start_byte < range.end_byte);
    ranges.sort();
    let mut result: Vec<DocumentRangeV1> = Vec::new();
    for range in ranges {
        if let Some(previous) = result.last_mut()
            && previous.document_revision_id == range.document_revision_id
            && range.start_byte <= previous.end_byte
        {
            previous.end_byte = previous.end_byte.max(range.end_byte);
        } else {
            result.push(range);
        }
    }
    result
}

fn validate(session: &ReviewSessionV1) -> eyre::Result<()> {
    if session.schema != SCHEMA {
        return Err(eyre!("unsupported schema '{}'", session.schema));
    }
    if session.coordinate_system != COORDINATE_SYSTEM {
        return Err(eyre!(
            "unsupported coordinate system '{}'",
            session.coordinate_system
        ));
    }
    for comment in &session.comments {
        if comment.forbidden_authoritative_tags.is_some() {
            return Err(eyre!(
                "comment '{}' contains forbidden authoritative tags field",
                comment.id
            ));
        }
    }
    for document in document_map(session)?.values() {
        if document.encoding != "utf-8" {
            return Err(eyre!("unsupported encoding '{}'", document.encoding));
        }
        if document.path.is_empty()
            || document.path.starts_with('/')
            || document.path.contains('\\')
            || document.path.split('/').any(|component| component == "..")
        {
            return Err(eyre!(
                "document path is not normalized repository-relative: {}",
                document.path
            ));
        }
    }
    Ok(())
}

fn document_map(session: &ReviewSessionV1) -> eyre::Result<BTreeMap<String, &DocumentRevisionV1>> {
    let mut result = BTreeMap::new();
    for lane in &session.revision_lanes {
        for document in lane.before.documents.iter().chain(&lane.after.documents) {
            if result.insert(document.id.clone(), document).is_some() {
                return Err(eyre!("duplicate document revision id '{}'", document.id));
            }
        }
    }
    Ok(result)
}

fn valid_hash(hash: &str) -> bool {
    hash.len() == 64
        && hash
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn is_resolved(status: EvaluationStatusV1) -> bool {
    matches!(
        status,
        EvaluationStatusV1::ResolvedExactly | EvaluationStatusV1::ResolvedWithRelocation
    )
}

fn normalize_tag(value: &str) -> String {
    value.nfc().collect::<String>().to_lowercase()
}

fn normalize_serialized_hashtag(value: &str) -> String {
    if let Some(inner) = value
        .strip_prefix("#\"")
        .and_then(|value| value.strip_suffix('"'))
    {
        format!("#\"{}\"", normalize_tag(inner))
    } else {
        format!(
            "#{}",
            normalize_tag(value.strip_prefix('#').unwrap_or(value))
        )
    }
}

#[derive(Debug)]
pub struct SessionStoreV1 {
    path: PathBuf,
    last_valid_path: PathBuf,
}

#[derive(Debug)]
pub struct LoadResultV1 {
    pub session: Option<ReviewSessionV1>,
    pub recovered_last_valid: bool,
    pub diagnostics: Vec<String>,
}

impl SessionStoreV1 {
    /// Create a store rooted at an explicit active-session path.
    #[must_use]
    pub fn new(path: PathBuf) -> Self {
        let last_valid_path = path.with_file_name(format!(
            "{}.last-valid",
            path.file_name().unwrap_or_default().to_string_lossy()
        ));
        Self {
            path,
            last_valid_path,
        }
    }

    /// Resolve the platform-local application-data path for a session id.
    ///
    /// # Errors
    ///
    /// Returns an error when no platform application-data directory exists.
    pub fn for_session_id(session_id: &str) -> eyre::Result<Self> {
        let project = directories_next::ProjectDirs::from("ca", "teamdman", "SFM")
            .ok_or_else(|| eyre!("application-data directory is unavailable"))?;
        Ok(Self::new(
            project
                .data_local_dir()
                .join("review-sessions")
                .join(format!("{}.json", sha256(session_id.as_bytes()))),
        ))
    }

    /// Atomically save both the active and last-valid session files.
    ///
    /// # Errors
    ///
    /// Returns an error when validation, serialization, directory creation, or
    /// an atomic write fails.
    pub fn save(&self, session: &ReviewSessionV1) -> eyre::Result<()> {
        let canonical = to_canonical_json(session)?;
        let reparsed = parse(&canonical)?;
        let _evaluations = evaluate_all(&reparsed)?;
        crate::payload_fetcher::write_payload_atomically(&self.path, canonical.as_bytes())?;
        crate::payload_fetcher::write_payload_atomically(
            &self.last_valid_path,
            canonical.as_bytes(),
        )?;
        Ok(())
    }

    /// Load the active session, falling back to the last-valid copy.
    #[must_use]
    pub fn load(&self) -> LoadResultV1 {
        let mut diagnostics = Vec::new();
        if let Some(session) = read_session(&self.path, "active", &mut diagnostics) {
            return LoadResultV1 {
                session: Some(session),
                recovered_last_valid: false,
                diagnostics,
            };
        }
        let recovered = read_session(&self.last_valid_path, "last-valid", &mut diagnostics);
        if recovered.is_some() {
            diagnostics.push("recovered the last valid review session".to_owned());
        }
        LoadResultV1 {
            recovered_last_valid: recovered.is_some(),
            session: recovered,
            diagnostics,
        }
    }

    /// Return the active-session path.
    #[must_use]
    pub fn path(&self) -> &Path {
        &self.path
    }
}

fn read_session(
    path: &Path,
    label: &str,
    diagnostics: &mut Vec<String>,
) -> Option<ReviewSessionV1> {
    match std::fs::read_to_string(path)
        .wrap_err_with(|| format!("could not read {} session at {}", label, path.display()))
        .and_then(|input| parse(&input))
        .and_then(|session| evaluate_all(&session).map(|_| session))
    {
        Ok(session) => Some(session),
        Err(error) => {
            diagnostics.push(format!(
                "invalid {label} session at {}: {error}",
                path.display()
            ));
            None
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture() -> String {
        std::fs::read_to_string(
            Path::new(env!("CARGO_MANIFEST_DIR"))
                .join("../../../docs/architecture/fixtures/review-comment-session-v1.json"),
        )
        .expect("canonical fixture")
    }

    #[test]
    fn canonical_fixture_round_trips_and_evaluates() {
        let input = fixture();
        let session = parse(&input).expect("fixture parses");
        let canonical = to_canonical_json(&session).expect("writes");
        let reparsed = parse(&canonical).expect("canonical output parses");
        assert_eq!(reparsed, session);
        assert_eq!(to_canonical_json(&reparsed).expect("rewrites"), canonical);
        println!(
            "SFM_REVIEW_SESSION_V1_CANONICAL_SHA256={}",
            sha256(canonical.as_bytes())
        );
        assert_eq!(
            sha256(canonical.as_bytes()),
            "a560e879a25c2d84f41ad1b322184921fe9241c46b0b6fe4f63387f4e9af2985"
        );
        let evaluations = evaluate_all(&session).expect("evaluates");
        assert!(
            evaluations
                .iter()
                .all(|evaluation| evaluation.status == EvaluationStatusV1::ResolvedExactly)
        );
        assert_eq!(overlap_pair_count(&evaluations), 3);
        assert!(is_approval_effective(
            &session,
            &session.comments[0],
            &evaluations[0]
        ));
    }

    #[test]
    fn tags_are_derived_and_authoritative_tags_are_rejected() {
        assert_eq!(
            derived_hashtags("#Approved #\"Needs Review\" #CAFÉ #cafe\u{301} ignored#suffix"),
            vec!["#approved", "#\"needs review\"", "#café"]
        );
        let with_tags = fixture().replacen(
            "\"text\": \"#approved",
            "\"tags\": [\"#approved\"],\n      \"text\": \"#approved",
            1,
        );
        assert!(
            parse(&with_tags)
                .expect_err("tags rejected")
                .to_string()
                .contains("forbidden authoritative tags")
        );
        let future_selector = fixture().replacen(
            "\"kind\": \"literal_utf8_range\"",
            "\"kind\": \"symbol_query\"",
            1,
        );
        let diagnostic = parse(&future_selector)
            .expect_err("future selector rejected")
            .to_string();
        assert!(
            diagnostic.contains("symbol_query") || diagnostic.contains("selection_rule"),
            "unexpected diagnostic: {diagnostic}"
        );
    }

    #[test]
    fn persistence_recovers_last_valid() {
        let directory = tempfile::tempdir().expect("tempdir");
        let store = SessionStoreV1::new(directory.path().join("session.json"));
        let session = parse(&fixture()).expect("fixture");
        store.save(&session).expect("save");
        std::fs::write(store.path(), "{broken").expect("corrupt active");
        let loaded = store.load();
        assert!(loaded.recovered_last_valid);
        assert_eq!(loaded.session, Some(session));
    }

    #[test]
    fn utf8_and_set_algebra_are_checked() {
        assert_eq!(utf8_byte_to_char_index("aéz", 4).expect("boundary"), 3);
        let _ = utf8_byte_to_char_index("aéz", 2).unwrap_err();
        let session = parse(&fixture()).expect("fixture");
        let documents = document_map(&session).expect("documents");
        let broad = session.comments[0].selection_rule.clone();
        let narrow = session.comments[1].selection_rule.clone();
        let result = evaluate_rule(
            &SelectionRuleV1::Difference {
                include: Box::new(broad),
                exclude: vec![narrow],
            },
            &documents,
        );
        assert_eq!(result.status, EvaluationStatusV1::ResolvedExactly);
        assert_eq!(
            result
                .ranges
                .iter()
                .map(|range| (range.start_byte, range.end_byte))
                .collect::<Vec<_>>(),
            vec![(20, 60), (68, 74)]
        );
    }

    #[test]
    fn changed_witnesses_have_conservative_deterministic_statuses() {
        let selected = "needle";
        let original = SelectionRuleV1::LiteralUtf8Range {
            document_revision_id: "doc".to_owned(),
            start_byte: 0,
            end_byte: selected.len(),
            document_sha256: sha256(selected.as_bytes()),
            selected_text_sha256: sha256(selected.as_bytes()),
        };

        assert_eq!(
            evaluate_literal("xxneedle", &original).status,
            EvaluationStatusV1::ResolvedWithRelocation
        );
        assert_eq!(
            evaluate_literal("needleneedle", &original).status,
            EvaluationStatusV1::Ambiguous
        );
        assert_eq!(
            evaluate_literal("changed", &original).status,
            EvaluationStatusV1::ContentChanged
        );

        let missing = SelectionRuleV1::LiteralUtf8Range {
            document_revision_id: "missing".to_owned(),
            start_byte: 0,
            end_byte: 0,
            document_sha256: sha256(b""),
            selected_text_sha256: sha256(b""),
        };
        assert_eq!(
            evaluate_literal("", &missing).status,
            EvaluationStatusV1::ScopeMissing
        );
    }

    fn evaluate_literal(text: &str, rule: &SelectionRuleV1) -> RuleResult {
        let document = DocumentRevisionV1 {
            id: "doc".to_owned(),
            path: "src/Test.java".to_owned(),
            encoding: "utf-8".to_owned(),
            sha256: sha256(text.as_bytes()),
            text: text.to_owned(),
        };
        let documents = BTreeMap::from([("doc".to_owned(), &document)]);
        evaluate_rule(rule, &documents)
    }
}
