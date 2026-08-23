//! Portable `sfm.release-review/1` contract, query algebra, and completion kernel.

use crate::review_session_v1;
use crate::review_session_v1::DocumentRangeV1;
use crate::review_session_v1::DocumentRevisionV1;
use crate::review_session_v1::SelectionRuleV1;
use crate::review_session_v2;
use crate::review_session_v2::CommentEvaluationV2;
use crate::review_session_v2::CommentTargetV2;
use crate::review_session_v2::EvaluationStatusV2;
use crate::review_session_v2::ReviewSessionV2;
use eyre::Context as _;
use eyre::eyre;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use unicode_normalization::UnicodeNormalization as _;

pub const SCHEMA: &str = "sfm.release-review/1";
pub const HASH_DOMAIN: &str = "sfm.release-review/1:semantic-state\n";

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReviewDocumentV1 {
    pub schema: String,
    pub review_session: ReviewSessionV2,
    pub repository_bindings: Vec<RepositoryBindingV1>,
    pub corpus_documents: Vec<CorpusDocumentV1>,
    pub review_units: Vec<ReviewUnitV1>,
    pub selector_bindings: Vec<CommentSelectorBindingV1>,
    pub migration_reports: Vec<MigrationReportV1>,
    pub named_queries: Vec<NamedQueryV1>,
    pub resume_state: ResumeStateV1,
    pub producer_generations: Vec<ProducerGenerationV1>,
    pub completion_attestations: Vec<CompletionAttestationV1>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct RepositoryBindingV1 {
    pub lane_id: String,
    pub repository_id: String,
    pub root_hint: String,
    pub before_label: String,
    pub before_commit: String,
    pub before_tree: String,
    pub after_label: String,
    pub candidate_commit: String,
    pub candidate_tree: String,
    pub review_evidence_paths: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum SnapshotSideV1 {
    Before,
    After,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum MaterializationV1 {
    Complete,
    Partial,
    Missing,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CorpusDocumentV1 {
    pub id: String,
    pub lane_id: String,
    pub snapshot_side: SnapshotSideV1,
    pub path: String,
    pub document_revision_id: String,
    pub sha256: String,
    pub source_owner: String,
    pub source_locator: String,
    pub materialization: MaterializationV1,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum ChangeOperationV1 {
    Added,
    Deleted,
    Modified,
    Renamed,
    Copied,
    TypeChanged,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum SurfaceKindV1 {
    Declaration,
    Signature,
    Body,
    Field,
    Import,
    DiffHunk,
    File,
    Binary,
    Unsupported,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct Utf8RangeV1 {
    pub start_byte: usize,
    pub end_byte: usize,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewUnitV1 {
    pub id: String,
    pub lane_id: String,
    pub operation: ChangeOperationV1,
    #[facet(skip_serializing_if = Option::is_none)]
    pub path_before: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub path_after: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub before_document_revision_id: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub after_document_revision_id: Option<String>,
    pub before_ranges: Vec<Utf8RangeV1>,
    pub after_ranges: Vec<Utf8RangeV1>,
    pub language: String,
    pub surface_kind: SurfaceKindV1,
    #[facet(skip_serializing_if = Option::is_none)]
    pub semantic_key: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub limitation: Option<String>,
    pub producer_id: String,
    pub producer_generation: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct NamedQueryV1 {
    pub id: String,
    pub expression: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ResumeStateV1 {
    #[facet(skip_serializing_if = Option::is_none)]
    pub active_query_id: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub active_query_expression: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub current_unit_id: Option<String>,
    pub deferred_unit_ids: Vec<String>,
    pub generation: u64,
}

impl ResumeStateV1 {
    #[must_use]
    pub const fn empty() -> Self {
        Self {
            active_query_id: None,
            active_query_expression: None,
            current_unit_id: None,
            deferred_unit_ids: Vec::new(),
            generation: 0,
        }
    }
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ProducerGenerationV1 {
    pub producer_id: String,
    pub generation: String,
    pub input_fingerprint: String,
    pub output_fingerprint: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CompletionAttestationV1 {
    pub id: String,
    pub review_semantic_state_hash: String,
    pub maintainer: String,
    pub attested_at: String,
    pub statement: String,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum SelectionDirectionV1 {
    Forward,
    Backward,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct PinnedSelectionV1 {
    pub selection_revision: String,
    pub source_expression: String,
    pub primary_range_index: usize,
    pub ranges: Vec<PinnedSelectionRangeV1>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct PinnedSelectionRangeV1 {
    pub direction: SelectionDirectionV1,
    pub document_revision_id: String,
    pub document_sha256: String,
    pub start_byte: usize,
    pub end_byte: usize,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum SelectorKindV1 {
    Literal,
    Declaration,
    Signature,
    Body,
    ReturnType,
    Symbol,
    BoundedMultiRegion,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum ProposalConfidenceV1 {
    Exact,
    Conservative,
    Unavailable,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct EvidenceV1 {
    pub key: String,
    pub value: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct SelectorProposalV1 {
    pub id: String,
    pub kind: SelectorKindV1,
    pub selection_rule: SelectionRuleV1,
    pub literal_witness: PinnedSelectionV1,
    #[facet(skip_serializing_if = Option::is_none)]
    pub semantic_provider: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub semantic_key: Option<String>,
    pub semantic_provenance: Vec<EvidenceV1>,
    pub confidence: ProposalConfidenceV1,
    pub projection_fingerprint: String,
    pub source_snapshot_id: String,
    pub diagnostics: Vec<String>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CommentSelectorBindingV1 {
    pub comment_id: String,
    pub captured_selection: PinnedSelectionV1,
    pub selected_proposal: SelectorProposalV1,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum SelectorEvaluationStatusV1 {
    Exact,
    Relocated,
    ContentChanged,
    Ambiguous,
    Missing,
    Invalid,
    ScopeMissing,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct AddressedRangeV1 {
    pub document_revision_id: String,
    pub start_byte: usize,
    pub end_byte: usize,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct InvalidationKeyV1 {
    pub owner: String,
    pub generation: String,
    pub fingerprint: String,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct SelectorEvaluationResultV1 {
    pub selector_id: String,
    pub status: SelectorEvaluationStatusV1,
    pub ranges: Vec<AddressedRangeV1>,
    pub candidates: Vec<AddressedRangeV1>,
    pub invalidation_keys: Vec<InvalidationKeyV1>,
    pub diagnostics: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum MigrationDecisionV1 {
    Unresolved,
    Retargeted,
    RelocationConfirmed,
    SelectorEdited,
    Archived,
    Discarded,
    Deferred,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct MigrationReportV1 {
    pub id: String,
    pub source_selector_id: String,
    pub source_evaluation: SelectorEvaluationResultV1,
    pub candidate_evaluation: SelectorEvaluationResultV1,
    pub old_witnesses: Vec<PinnedSelectionRangeV1>,
    pub new_candidates: Vec<AddressedRangeV1>,
    pub decision: MigrationDecisionV1,
    #[facet(skip_serializing_if = Option::is_none)]
    pub decision_comment_id: Option<String>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReviewQueryResultV1 {
    pub schema: String,
    pub expression: String,
    pub normalized_expression: String,
    pub review_unit_ids: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum CompletionStatusV1 {
    InProgress,
    ReadyForMaintainerAttestation,
    Complete,
    Stale,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CompletionWitnessesV1 {
    pub changed_domain: Vec<String>,
    pub approved_raw: Vec<String>,
    pub approved_effective: Vec<String>,
    pub remaining: Vec<String>,
    pub blocking: Vec<String>,
    pub suspended: Vec<String>,
    pub missing: Vec<String>,
    pub deferred: Vec<String>,
    pub unsupported: Vec<String>,
    pub stale_producer: Vec<String>,
}

#[derive(Clone, Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CompletionReportV1 {
    pub schema: String,
    pub status: CompletionStatusV1,
    pub review_semantic_state_hash: String,
    pub changed_domain: usize,
    pub approved_raw: usize,
    pub approved_effective: usize,
    pub remaining: usize,
    pub blocking: usize,
    pub suspended: usize,
    pub missing: usize,
    pub deferred: usize,
    pub unsupported: usize,
    pub stale_producer: usize,
    pub witnesses: CompletionWitnessesV1,
    pub diagnostics: Vec<String>,
}

#[derive(Facet)]
struct ReleaseReviewTaggedSurfaceJson {
    review_session: facet_json::RawJson<'static>,
    selector_bindings: Vec<facet_json::RawJson<'static>>,
}

#[derive(Facet)]
struct SelectorBindingTaggedSurfaceJson {
    selected_proposal: facet_json::RawJson<'static>,
}

#[derive(Facet)]
struct SelectorProposalTaggedSurfaceJson {
    selection_rule: facet_json::RawJson<'static>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
enum QueryExpression {
    Atom(String),
    Effective(Box<QueryExpression>),
    Binary {
        left: Box<QueryExpression>,
        operator: QueryOperator,
        right: Box<QueryExpression>,
    },
}

impl QueryExpression {
    fn normalized(&self) -> String {
        match self {
            Self::Atom(value) => value.clone(),
            Self::Effective(expression) => format!("effective({})", expression.normalized()),
            Self::Binary {
                left,
                operator,
                right,
            } => format!(
                "({} {} {})",
                left.normalized(),
                operator.name(),
                right.normalized()
            ),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum QueryOperator {
    Union,
    Intersect,
    Difference,
}

impl QueryOperator {
    const fn name(self) -> &'static str {
        match self {
            Self::Union => "union",
            Self::Intersect => "intersect",
            Self::Difference => "difference",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum QueryTokenKind {
    Word,
    Left,
    Right,
    End,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct QueryToken {
    kind: QueryTokenKind,
    text: String,
    offset: usize,
}

struct QueryParser {
    tokens: Vec<QueryToken>,
    position: usize,
}

impl QueryParser {
    fn parse(input: &str) -> eyre::Result<QueryExpression> {
        let mut parser = Self {
            tokens: tokenize_query(input),
            position: 0,
        };
        let answer = parser.union()?;
        let trailing = parser.peek();
        if trailing.kind != QueryTokenKind::End {
            return Err(Self::error(
                format!("unexpected token '{}'", trailing.text),
                trailing,
            ));
        }
        Ok(answer)
    }

    fn union(&mut self) -> eyre::Result<QueryExpression> {
        let mut answer = self.difference()?;
        while self.take_word("union") {
            answer = QueryExpression::Binary {
                left: Box::new(answer),
                operator: QueryOperator::Union,
                right: Box::new(self.difference()?),
            };
        }
        Ok(answer)
    }

    fn difference(&mut self) -> eyre::Result<QueryExpression> {
        let mut answer = self.intersection()?;
        while self.take_word("difference") {
            answer = QueryExpression::Binary {
                left: Box::new(answer),
                operator: QueryOperator::Difference,
                right: Box::new(self.intersection()?),
            };
        }
        Ok(answer)
    }

    fn intersection(&mut self) -> eyre::Result<QueryExpression> {
        let mut answer = self.unary()?;
        loop {
            if self.take_word("intersect") || starts_query_unary(self.peek()) {
                answer = QueryExpression::Binary {
                    left: Box::new(answer),
                    operator: QueryOperator::Intersect,
                    right: Box::new(self.unary()?),
                };
            } else {
                return Ok(answer);
            }
        }
    }

    fn unary(&mut self) -> eyre::Result<QueryExpression> {
        if self.peek_word("effective") {
            self.take();
            self.expect(QueryTokenKind::Left, "expected '(' after effective")?;
            let child = self.union()?;
            self.expect(
                QueryTokenKind::Right,
                "expected ')' after effective expression",
            )?;
            return Ok(QueryExpression::Effective(Box::new(child)));
        }
        self.primary()
    }

    fn primary(&mut self) -> eyre::Result<QueryExpression> {
        let token = self.take().clone();
        if token.kind == QueryTokenKind::Left {
            let child = self.union()?;
            self.expect(QueryTokenKind::Right, "expected ')' to close query group")?;
            return Ok(child);
        }
        if token.kind != QueryTokenKind::Word || query_keyword(&token.text) {
            return Err(Self::error("expected a query atom", &token));
        }
        Ok(QueryExpression::Atom(token.text))
    }

    fn peek(&self) -> &QueryToken {
        &self.tokens[self.position]
    }

    fn take(&mut self) -> &QueryToken {
        let position = self.position;
        if self.tokens[position].kind != QueryTokenKind::End {
            self.position += 1;
        }
        &self.tokens[position]
    }

    fn peek_word(&self, value: &str) -> bool {
        let token = self.peek();
        token.kind == QueryTokenKind::Word && token.text.eq_ignore_ascii_case(value)
    }

    fn take_word(&mut self, value: &str) -> bool {
        if !self.peek_word(value) {
            return false;
        }
        self.take();
        true
    }

    fn expect(&mut self, kind: QueryTokenKind, message: &str) -> eyre::Result<()> {
        let token = self.take().clone();
        if token.kind != kind {
            return Err(Self::error(message, &token));
        }
        Ok(())
    }

    fn error(message: impl std::fmt::Display, token: &QueryToken) -> eyre::Report {
        eyre!("{message} at query offset {}", token.offset)
    }
}

fn tokenize_query(input: &str) -> Vec<QueryToken> {
    let mut answer = Vec::new();
    let mut indices = input.char_indices().peekable();
    while let Some((offset, current)) = indices.next() {
        if current.is_whitespace() {
            continue;
        }
        if current == '(' || current == ')' {
            answer.push(QueryToken {
                kind: if current == '(' {
                    QueryTokenKind::Left
                } else {
                    QueryTokenKind::Right
                },
                text: current.to_string(),
                offset,
            });
            continue;
        }
        let mut end = offset + current.len_utf8();
        while let Some((next_offset, next)) = indices.peek().copied() {
            if next.is_whitespace() || matches!(next, '(' | ')') {
                break;
            }
            indices.next();
            end = next_offset + next.len_utf8();
        }
        answer.push(QueryToken {
            kind: QueryTokenKind::Word,
            text: input[offset..end].to_owned(),
            offset,
        });
    }
    answer.push(QueryToken {
        kind: QueryTokenKind::End,
        text: String::new(),
        offset: input.len(),
    });
    answer
}

fn starts_query_unary(token: &QueryToken) -> bool {
    token.kind == QueryTokenKind::Left
        || (token.kind == QueryTokenKind::Word
            && !matches!(
                token.text.to_ascii_lowercase().as_str(),
                "union" | "intersect" | "difference"
            ))
}

fn query_keyword(value: &str) -> bool {
    matches!(
        value.to_ascii_lowercase().as_str(),
        "union" | "intersect" | "difference" | "effective"
    )
}

/// Normalize a release-review query using the frozen precedence rules.
///
/// # Errors
///
/// Returns an error when the expression is malformed.
pub fn normalize_query(expression: &str) -> eyre::Result<String> {
    Ok(QueryParser::parse(expression)?.normalized())
}

/// Parse, canonicalize, and validate a portable release-review document.
///
/// # Errors
///
/// Returns an error for unknown fields, unsupported discriminators, dangling
/// identities, malformed queries, or violated contract invariants.
pub fn parse(input: &str) -> eyre::Result<ReleaseReviewDocumentV1> {
    validate_tagged_contract_json(input)?;
    let mut document: ReleaseReviewDocumentV1 = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid release-review v1 JSON: {error:?}"))?;
    canonicalize(&mut document)?;
    validate(&document)?;
    Ok(document)
}

fn validate_tagged_contract_json(input: &str) -> eyre::Result<()> {
    let surface: ReleaseReviewTaggedSurfaceJson = facet_json::from_str(input)
        .map_err(|error| eyre!("invalid release-review tagged surface: {error:?}"))?;
    review_session_v2::validate_tagged_contract_json(surface.review_session.as_str())?;
    for binding in surface.selector_bindings {
        let binding: SelectorBindingTaggedSurfaceJson = facet_json::from_str(binding.as_str())
            .map_err(|error| eyre!("invalid selector-binding tagged surface: {error:?}"))?;
        let proposal: SelectorProposalTaggedSurfaceJson =
            facet_json::from_str(binding.selected_proposal.as_str())
                .map_err(|error| eyre!("invalid selector-proposal tagged surface: {error:?}"))?;
        review_session_v2::validate_selection_rule_json(proposal.selection_rule.as_str())?;
    }
    Ok(())
}

/// Serialize a validated document with the canonical Java/Rust field order.
///
/// # Errors
///
/// Returns an error when canonicalization, validation, or serialization fails.
pub fn to_canonical_json(document: &ReleaseReviewDocumentV1) -> eyre::Result<String> {
    let mut canonical = document.clone();
    canonicalize(&mut canonical)?;
    validate(&canonical)?;
    let mut output = facet_json::to_string_pretty(&canonical)
        .wrap_err("could not serialize release-review v1")?;
    output.push('\n');
    Ok(output)
}

fn canonicalize(document: &mut ReleaseReviewDocumentV1) -> eyre::Result<()> {
    review_session_v2::canonicalize(&mut document.review_session)?;

    for binding in &mut document.repository_bindings {
        for path in &mut binding.review_evidence_paths {
            *path = normalize_relative_path(path);
        }
        binding.review_evidence_paths.sort();
    }
    document
        .repository_bindings
        .sort_by(|left, right| left.lane_id.cmp(&right.lane_id));

    for corpus in &mut document.corpus_documents {
        corpus.path = normalize_relative_path(&corpus.path);
    }
    document.corpus_documents.sort_by(|left, right| {
        (
            left.lane_id.as_str(),
            left.snapshot_side,
            left.path.as_str(),
            left.id.as_str(),
        )
            .cmp(&(
                right.lane_id.as_str(),
                right.snapshot_side,
                right.path.as_str(),
                right.id.as_str(),
            ))
    });

    for unit in &mut document.review_units {
        unit.path_before = unit
            .path_before
            .take()
            .map(|path| normalize_relative_path(&path));
        unit.path_after = unit
            .path_after
            .take()
            .map(|path| normalize_relative_path(&path));
        unit.before_ranges.sort();
        unit.after_ranges.sort();
    }
    document.review_units.sort_by(|left, right| {
        (
            left.lane_id.as_str(),
            canonical_unit_path(left),
            operation_name(left.operation),
            first_range_start(left),
            left.id.as_str(),
        )
            .cmp(&(
                right.lane_id.as_str(),
                canonical_unit_path(right),
                operation_name(right.operation),
                first_range_start(right),
                right.id.as_str(),
            ))
    });

    for binding in &mut document.selector_bindings {
        binding
            .selected_proposal
            .semantic_provenance
            .sort_by(|left, right| left.key.cmp(&right.key));
    }
    document
        .selector_bindings
        .sort_by(|left, right| left.comment_id.cmp(&right.comment_id));
    document
        .migration_reports
        .sort_by(|left, right| left.id.cmp(&right.id));
    document
        .named_queries
        .sort_by(|left, right| left.id.cmp(&right.id));
    document.resume_state.deferred_unit_ids.sort();
    document
        .producer_generations
        .sort_by(|left, right| left.producer_id.cmp(&right.producer_id));
    document
        .completion_attestations
        .sort_by(|left, right| left.id.cmp(&right.id));
    Ok(())
}

fn validate(document: &ReleaseReviewDocumentV1) -> eyre::Result<()> {
    if document.schema != SCHEMA {
        return Err(eyre!(
            "unsupported release-review schema '{}'",
            document.schema
        ));
    }
    review_session_v2::validate(&document.review_session)?;
    let evaluations = review_session_v2::evaluate_all(&document.review_session)?;

    validate_unique_identities(document)?;

    validate_repository_corpus(document)?;

    validate_units_and_resume(document)?;

    validate_review_relationships(document)?;
    validate_named_queries(document, evaluations)
}

fn validate_unique_identities(document: &ReleaseReviewDocumentV1) -> eyre::Result<()> {
    ensure_unique(
        document
            .repository_bindings
            .iter()
            .map(|value| value.lane_id.as_str()),
        "repository-binding lane id",
    )?;
    ensure_unique(
        document
            .corpus_documents
            .iter()
            .map(|value| value.id.as_str()),
        "corpus-document id",
    )?;
    ensure_unique(
        document
            .corpus_documents
            .iter()
            .map(canonical_corpus_address),
        "corpus-document address",
    )?;
    ensure_unique(
        document.review_units.iter().map(|value| value.id.as_str()),
        "review-unit id",
    )?;
    ensure_unique(
        document
            .selector_bindings
            .iter()
            .map(|value| value.comment_id.as_str()),
        "selector-binding comment id",
    )?;
    ensure_unique(
        document
            .migration_reports
            .iter()
            .map(|value| value.id.as_str()),
        "migration-report id",
    )?;
    ensure_unique(
        document.named_queries.iter().map(|value| value.id.as_str()),
        "named-query id",
    )?;
    ensure_unique(
        document
            .producer_generations
            .iter()
            .map(|value| value.producer_id.as_str()),
        "producer id",
    )?;
    ensure_unique(
        document
            .completion_attestations
            .iter()
            .map(|value| value.id.as_str()),
        "completion-attestation id",
    )
}

fn validate_repository_corpus(document: &ReleaseReviewDocumentV1) -> eyre::Result<()> {
    let mut session_lanes = BTreeMap::new();
    for lane in &document.review_session.revision_lanes {
        if session_lanes.insert(lane.id.as_str(), lane).is_some() {
            return Err(eyre!("duplicate embedded review lane '{}'", lane.id));
        }
    }
    let mut repository_bindings = BTreeMap::new();
    for binding in &document.repository_bindings {
        validate_repository_binding(binding)?;
        if !session_lanes.contains_key(binding.lane_id.as_str()) {
            return Err(eyre!(
                "repository binding has no embedded lane '{}'",
                binding.lane_id
            ));
        }
        repository_bindings.insert(binding.lane_id.as_str(), binding);
    }

    let session_documents = embedded_documents(&document.review_session)?;
    let mut corpus_by_revision = BTreeMap::new();
    for corpus in &document.corpus_documents {
        validate_corpus_document(corpus)?;
        if !repository_bindings.contains_key(corpus.lane_id.as_str()) {
            return Err(eyre!(
                "corpus document has no repository binding '{}'",
                corpus.id
            ));
        }
        let embedded = session_documents.get(corpus.document_revision_id.as_str());
        if corpus.materialization == MaterializationV1::Complete && embedded.is_none() {
            return Err(eyre!(
                "complete corpus document is absent from embedded session '{}'",
                corpus.id
            ));
        }
        if let Some(embedded) = embedded
            && (embedded.path != corpus.path || embedded.sha256 != corpus.sha256)
        {
            return Err(eyre!(
                "corpus witness disagrees with embedded document '{}'",
                corpus.id
            ));
        }
        if let Some(previous) =
            corpus_by_revision.insert(corpus.document_revision_id.as_str(), corpus)
            && previous != corpus
        {
            return Err(eyre!(
                "document revision is bound to multiple corpus entries '{}'",
                corpus.document_revision_id
            ));
        }
    }
    Ok(())
}

fn validate_units_and_resume(document: &ReleaseReviewDocumentV1) -> eyre::Result<()> {
    let repository_bindings = document
        .repository_bindings
        .iter()
        .map(|value| value.lane_id.as_str())
        .collect::<BTreeSet<_>>();
    let corpus_revisions = document
        .corpus_documents
        .iter()
        .map(|value| value.document_revision_id.as_str())
        .collect::<BTreeSet<_>>();
    let producers = document
        .producer_generations
        .iter()
        .map(|value| value.producer_id.as_str())
        .collect::<BTreeSet<_>>();
    for producer in &document.producer_generations {
        validate_producer(producer)?;
    }
    let unit_ids = document
        .review_units
        .iter()
        .map(|unit| unit.id.as_str())
        .collect::<BTreeSet<_>>();
    for unit in &document.review_units {
        validate_review_unit(unit)?;
        if !repository_bindings.contains(unit.lane_id.as_str()) {
            return Err(eyre!("review unit has no repository binding '{}'", unit.id));
        }
        for revision in [
            unit.before_document_revision_id.as_deref(),
            unit.after_document_revision_id.as_deref(),
        ]
        .into_iter()
        .flatten()
        {
            if !corpus_revisions.contains(revision) {
                return Err(eyre!(
                    "review unit '{}' references unknown corpus revision '{}'",
                    unit.id,
                    revision
                ));
            }
        }
        if !producers.contains(unit.producer_id.as_str()) {
            return Err(eyre!(
                "review unit has no producer declaration '{}'",
                unit.id
            ));
        }
    }

    validate_resume_state(&document.resume_state, &unit_ids)?;
    let query_ids = document
        .named_queries
        .iter()
        .map(|query| query.id.as_str())
        .collect::<BTreeSet<_>>();
    if let Some(active) = document.resume_state.active_query_id.as_deref()
        && !query_ids.contains(active)
    {
        return Err(eyre!("unknown active named query '{active}'"));
    }
    Ok(())
}

fn validate_review_relationships(document: &ReleaseReviewDocumentV1) -> eyre::Result<()> {
    let comments = document
        .review_session
        .comments
        .iter()
        .map(|comment| (comment.id.as_str(), comment))
        .collect::<BTreeMap<_, _>>();
    let corpus_by_revision = document
        .corpus_documents
        .iter()
        .map(|corpus| (corpus.document_revision_id.as_str(), corpus))
        .collect::<BTreeMap<_, _>>();
    let mut selector_ids = BTreeSet::new();
    for binding in &document.selector_bindings {
        validate_selector_binding(binding)?;
        if !comments.contains_key(binding.comment_id.as_str()) {
            return Err(eyre!(
                "selector binding references unknown comment '{}'",
                binding.comment_id
            ));
        }
        if !selector_ids.insert(binding.selected_proposal.id.as_str()) {
            return Err(eyre!(
                "duplicate selected-proposal id '{}'",
                binding.selected_proposal.id
            ));
        }
        for range in &binding.captured_selection.ranges {
            let Some(corpus) = corpus_by_revision.get(range.document_revision_id.as_str()) else {
                return Err(eyre!(
                    "pinned selection references unknown corpus revision '{}'",
                    range.document_revision_id
                ));
            };
            if corpus.sha256 != range.document_sha256 {
                return Err(eyre!(
                    "pinned selection disagrees with corpus revision '{}'",
                    range.document_revision_id
                ));
            }
        }
    }
    for report in &document.migration_reports {
        validate_migration_report(report)?;
        if !selector_ids.contains(report.source_selector_id.as_str()) {
            return Err(eyre!(
                "migration references unknown selector '{}'",
                report.source_selector_id
            ));
        }
        if let Some(comment_id) = report.decision_comment_id.as_deref()
            && !comments.contains_key(comment_id)
        {
            return Err(eyre!(
                "migration references unknown decision comment '{comment_id}'"
            ));
        }
    }
    for attestation in &document.completion_attestations {
        validate_attestation(attestation)?;
    }
    Ok(())
}

fn validate_named_queries(
    document: &ReleaseReviewDocumentV1,
    evaluations: Vec<CommentEvaluationV2>,
) -> eyre::Result<()> {
    for query in &document.named_queries {
        require_text(&query.id, "namedQuery.id")?;
        require_text(&query.expression, "namedQuery.expression")?;
        QueryParser::parse(&query.expression)?;
    }
    let context = QueryContext::new_unchecked(document, evaluations);
    for query in &document.named_queries {
        let parsed = QueryParser::parse(&query.expression)?;
        let mut stack = vec![query.id.to_ascii_lowercase()];
        let _ = evaluate_expression(&context, &parsed, false, &mut stack)?;
    }
    Ok(())
}

fn validate_repository_binding(binding: &RepositoryBindingV1) -> eyre::Result<()> {
    require_text(&binding.lane_id, "repositoryBinding.laneId")?;
    require_text(&binding.repository_id, "repositoryBinding.repositoryId")?;
    require_text(&binding.root_hint, "repositoryBinding.rootHint")?;
    require_text(&binding.before_label, "repositoryBinding.beforeLabel")?;
    require_git_sha1(&binding.before_commit, "repositoryBinding.beforeCommit")?;
    require_git_sha1(&binding.before_tree, "repositoryBinding.beforeTree")?;
    require_text(&binding.after_label, "repositoryBinding.afterLabel")?;
    require_git_sha1(
        &binding.candidate_commit,
        "repositoryBinding.candidateCommit",
    )?;
    require_git_sha1(&binding.candidate_tree, "repositoryBinding.candidateTree")?;
    ensure_unique(
        binding.review_evidence_paths.iter().map(String::as_str),
        "review evidence path",
    )?;
    for path in &binding.review_evidence_paths {
        require_relative_path(path, "review evidence path")?;
    }
    Ok(())
}

fn validate_corpus_document(corpus: &CorpusDocumentV1) -> eyre::Result<()> {
    require_text(&corpus.id, "corpusDocument.id")?;
    require_text(&corpus.lane_id, "corpusDocument.laneId")?;
    require_relative_path(&corpus.path, "corpusDocument.path")?;
    require_text(
        &corpus.document_revision_id,
        "corpusDocument.documentRevisionId",
    )?;
    require_sha256(&corpus.sha256, "corpusDocument.sha256")?;
    require_text(&corpus.source_owner, "corpusDocument.sourceOwner")?;
    require_text(&corpus.source_locator, "corpusDocument.sourceLocator")
}

fn validate_review_unit(unit: &ReviewUnitV1) -> eyre::Result<()> {
    require_text(&unit.id, "reviewUnit.id")?;
    require_text(&unit.lane_id, "reviewUnit.laneId")?;
    optional_path(unit.path_before.as_deref(), "reviewUnit.pathBefore")?;
    optional_path(unit.path_after.as_deref(), "reviewUnit.pathAfter")?;
    if unit.path_before.is_none() && unit.path_after.is_none() {
        return Err(eyre!("a review unit requires a before or after path"));
    }
    optional_text(
        unit.before_document_revision_id.as_deref(),
        "reviewUnit.beforeDocumentRevisionId",
    )?;
    optional_text(
        unit.after_document_revision_id.as_deref(),
        "reviewUnit.afterDocumentRevisionId",
    )?;
    for range in unit.before_ranges.iter().chain(&unit.after_ranges) {
        if range.end_byte < range.start_byte {
            return Err(eyre!("UTF-8 range must be forward and non-negative"));
        }
    }
    require_text(&unit.language, "reviewUnit.language")?;
    optional_text(unit.semantic_key.as_deref(), "reviewUnit.semanticKey")?;
    optional_text(unit.limitation.as_deref(), "reviewUnit.limitation")?;
    require_text(&unit.producer_id, "reviewUnit.producerId")?;
    require_text(&unit.producer_generation, "reviewUnit.producerGeneration")?;
    if unit.surface_kind == SurfaceKindV1::Unsupported && unit.limitation.is_none() {
        return Err(eyre!("an unsupported review unit requires a limitation"));
    }
    Ok(())
}

fn validate_resume_state(resume: &ResumeStateV1, unit_ids: &BTreeSet<&str>) -> eyre::Result<()> {
    optional_text(
        resume.active_query_id.as_deref(),
        "resumeState.activeQueryId",
    )?;
    optional_text(
        resume.active_query_expression.as_deref(),
        "resumeState.activeQueryExpression",
    )?;
    optional_text(
        resume.current_unit_id.as_deref(),
        "resumeState.currentUnitId",
    )?;
    ensure_unique(
        resume.deferred_unit_ids.iter().map(String::as_str),
        "deferred unit id",
    )?;
    if let Some(current) = resume.current_unit_id.as_deref()
        && !unit_ids.contains(current)
    {
        return Err(eyre!("unknown current resume unit '{current}'"));
    }
    for deferred in &resume.deferred_unit_ids {
        require_text(deferred, "deferred unit id")?;
        if !unit_ids.contains(deferred.as_str()) {
            return Err(eyre!("unknown deferred unit '{deferred}'"));
        }
    }
    Ok(())
}

fn validate_producer(producer: &ProducerGenerationV1) -> eyre::Result<()> {
    require_text(&producer.producer_id, "producerGeneration.producerId")?;
    require_text(&producer.generation, "producerGeneration.generation")?;
    require_sha256(
        &producer.input_fingerprint,
        "producerGeneration.inputFingerprint",
    )?;
    require_sha256(
        &producer.output_fingerprint,
        "producerGeneration.outputFingerprint",
    )
}

fn validate_selector_binding(binding: &CommentSelectorBindingV1) -> eyre::Result<()> {
    require_text(&binding.comment_id, "selectorBinding.commentId")?;
    validate_pinned_selection(&binding.captured_selection)?;
    validate_selector_proposal(&binding.selected_proposal)?;
    if binding.captured_selection != binding.selected_proposal.literal_witness {
        return Err(eyre!(
            "selected proposal must retain the exact captured literal witness"
        ));
    }
    Ok(())
}

fn validate_pinned_selection(selection: &PinnedSelectionV1) -> eyre::Result<()> {
    require_text(&selection.selection_revision, "selection.selectionRevision")?;
    require_text(&selection.source_expression, "selection.sourceExpression")?;
    if selection.ranges.is_empty() {
        return Err(eyre!("a pinned selection requires at least one range"));
    }
    if selection.primary_range_index >= selection.ranges.len() {
        return Err(eyre!("primary range index is outside the selection"));
    }
    for range in &selection.ranges {
        validate_pinned_range(range)?;
    }
    Ok(())
}

fn validate_pinned_range(range: &PinnedSelectionRangeV1) -> eyre::Result<()> {
    require_text(
        &range.document_revision_id,
        "selectionRange.documentRevisionId",
    )?;
    require_sha256(&range.document_sha256, "selectionRange.documentSha256")?;
    if range.end_byte < range.start_byte {
        return Err(eyre!(
            "pinned selection range must be forward and non-negative"
        ));
    }
    Ok(())
}

fn validate_selector_proposal(proposal: &SelectorProposalV1) -> eyre::Result<()> {
    require_text(&proposal.id, "selectorProposal.id")?;
    validate_pinned_selection(&proposal.literal_witness)?;
    optional_text(
        proposal.semantic_provider.as_deref(),
        "selectorProposal.semanticProvider",
    )?;
    optional_text(
        proposal.semantic_key.as_deref(),
        "selectorProposal.semanticKey",
    )?;
    ensure_unique(
        proposal
            .semantic_provenance
            .iter()
            .map(|value| value.key.as_str()),
        "semantic-provenance key",
    )?;
    for evidence in &proposal.semantic_provenance {
        require_text(&evidence.key, "evidence.key")?;
        require_text(&evidence.value, "evidence.value")?;
    }
    require_sha256(
        &proposal.projection_fingerprint,
        "selectorProposal.projectionFingerprint",
    )?;
    require_text(
        &proposal.source_snapshot_id,
        "selectorProposal.sourceSnapshotId",
    )?;
    Ok(())
}

fn validate_migration_report(report: &MigrationReportV1) -> eyre::Result<()> {
    require_text(&report.id, "migration.id")?;
    require_text(&report.source_selector_id, "migration.sourceSelectorId")?;
    validate_selector_evaluation(&report.source_evaluation)?;
    validate_selector_evaluation(&report.candidate_evaluation)?;
    for witness in &report.old_witnesses {
        validate_pinned_range(witness)?;
    }
    for candidate in &report.new_candidates {
        validate_addressed_range(candidate)?;
    }
    optional_text(
        report.decision_comment_id.as_deref(),
        "migration.decisionCommentId",
    )?;
    if report.decision != MigrationDecisionV1::Unresolved && report.decision_comment_id.is_none() {
        return Err(eyre!(
            "a migration decision requires an ordinary decision comment id"
        ));
    }
    Ok(())
}

fn validate_selector_evaluation(evaluation: &SelectorEvaluationResultV1) -> eyre::Result<()> {
    require_text(&evaluation.selector_id, "evaluation.selectorId")?;
    for range in evaluation.ranges.iter().chain(&evaluation.candidates) {
        validate_addressed_range(range)?;
    }
    for key in &evaluation.invalidation_keys {
        require_text(&key.owner, "invalidationKey.owner")?;
        require_text(&key.generation, "invalidationKey.generation")?;
        require_sha256(&key.fingerprint, "invalidationKey.fingerprint")?;
    }
    Ok(())
}

fn validate_addressed_range(range: &AddressedRangeV1) -> eyre::Result<()> {
    require_text(
        &range.document_revision_id,
        "addressedRange.documentRevisionId",
    )?;
    if range.end_byte < range.start_byte {
        return Err(eyre!("addressed range must be forward and non-negative"));
    }
    Ok(())
}

fn validate_attestation(attestation: &CompletionAttestationV1) -> eyre::Result<()> {
    require_text(&attestation.id, "attestation.id")?;
    require_sha256(
        &attestation.review_semantic_state_hash,
        "attestation.reviewSemanticStateHash",
    )?;
    require_text(&attestation.maintainer, "attestation.maintainer")?;
    require_text(&attestation.attested_at, "attestation.attestedAt")?;
    chrono::DateTime::parse_from_rfc3339(&attestation.attested_at)
        .map_err(|error| eyre!("attestation.attestedAt must be RFC 3339: {error}"))?;
    require_text(&attestation.statement, "attestation.statement")
}

struct QueryContext<'a> {
    document: &'a ReleaseReviewDocumentV1,
    named_queries: BTreeMap<String, &'a NamedQueryV1>,
    comments: BTreeMap<String, &'a review_session_v2::CommentV2>,
    evaluations: BTreeMap<String, CommentEvaluationV2>,
    domain: BTreeSet<String>,
    approved_raw: BTreeSet<String>,
    approved_effective: BTreeSet<String>,
    blocking: BTreeSet<String>,
    suspended: BTreeSet<String>,
    missing: BTreeSet<String>,
    deferred: BTreeSet<String>,
    unsupported: BTreeSet<String>,
    stale_producer: BTreeSet<String>,
}

impl<'a> QueryContext<'a> {
    fn new_unchecked(
        document: &'a ReleaseReviewDocumentV1,
        evaluations: Vec<CommentEvaluationV2>,
    ) -> Self {
        let comments = document
            .review_session
            .comments
            .iter()
            .map(|comment| (comment.id.clone(), comment))
            .collect::<BTreeMap<_, _>>();
        let evaluations = evaluations
            .into_iter()
            .map(|evaluation| (evaluation.comment_id.clone(), evaluation))
            .collect::<BTreeMap<_, _>>();
        let domain = document
            .review_units
            .iter()
            .map(|unit| unit.id.clone())
            .collect::<BTreeSet<_>>();
        let approval =
            normalize_hashtag(&document.review_session.completion_policy.approval_hashtag);
        let approved_raw = hashtag_units(document, &comments, &evaluations, &approval, false);
        let mut blocking = BTreeSet::new();
        for hashtag in &document.review_session.completion_policy.blocking_hashtags {
            blocking.extend(hashtag_units(
                document,
                &comments,
                &evaluations,
                &normalize_hashtag(hashtag),
                false,
            ));
        }
        let approved_candidate = hashtag_units(document, &comments, &evaluations, &approval, true);
        let approved_effective = set_difference(&approved_candidate, &blocking);
        let suspended = set_difference(&approved_raw, &approved_effective);
        let missing = unresolved_units(document, &comments, &evaluations);
        let deferred = document
            .resume_state
            .deferred_unit_ids
            .iter()
            .cloned()
            .collect::<BTreeSet<_>>();
        let unsupported = document
            .review_units
            .iter()
            .filter(|unit| unit.surface_kind == SurfaceKindV1::Unsupported)
            .map(|unit| unit.id.clone())
            .collect::<BTreeSet<_>>();
        let generations = document
            .producer_generations
            .iter()
            .map(|producer| (producer.producer_id.as_str(), producer.generation.as_str()))
            .collect::<BTreeMap<_, _>>();
        let stale_producer = document
            .review_units
            .iter()
            .filter(|unit| {
                generations.get(unit.producer_id.as_str()).copied()
                    != Some(unit.producer_generation.as_str())
            })
            .map(|unit| unit.id.clone())
            .collect::<BTreeSet<_>>();
        let named_queries = document
            .named_queries
            .iter()
            .map(|query| (query.id.to_ascii_lowercase(), query))
            .collect::<BTreeMap<_, _>>();
        Self {
            document,
            named_queries,
            comments,
            evaluations,
            domain,
            approved_raw,
            approved_effective,
            blocking,
            suspended,
            missing,
            deferred,
            unsupported,
            stale_producer,
        }
    }
}

/// Evaluate a set expression against the pinned review domain.
///
/// The unwrapped `#approved` atom is deliberately raw; callers request
/// fail-closed approval with `effective(#approved)` or `approved-effective`.
///
/// # Errors
///
/// Returns an error when the document or expression is invalid, a named query
/// cycles, or an atom is unknown.
pub fn query(
    document: &ReleaseReviewDocumentV1,
    expression: &str,
) -> eyre::Result<ReleaseReviewQueryResultV1> {
    validate(document)?;
    let parsed = QueryParser::parse(expression)?;
    let evaluations = review_session_v2::evaluate_all(&document.review_session)?;
    let context = QueryContext::new_unchecked(document, evaluations);
    let mut stack = Vec::new();
    let review_unit_ids = evaluate_expression(&context, &parsed, false, &mut stack)?
        .into_iter()
        .collect();
    Ok(ReleaseReviewQueryResultV1 {
        schema: "sfm.release-review-query-result/1".to_owned(),
        expression: expression.to_owned(),
        normalized_expression: parsed.normalized(),
        review_unit_ids,
    })
}

/// Compute fail-closed review readiness and the non-circular semantic hash.
///
/// # Errors
///
/// Returns an error when the document is invalid or cannot be canonicalized.
pub fn completion(document: &ReleaseReviewDocumentV1) -> eyre::Result<CompletionReportV1> {
    validate(document)?;
    let evaluations = review_session_v2::evaluate_all(&document.review_session)?;
    let context = QueryContext::new_unchecked(document, evaluations);
    let remaining = set_difference(&context.domain, &context.approved_effective);
    let mut diagnostics = Vec::new();
    if !context.stale_producer.is_empty() {
        diagnostics.push("Producer generations are stale".to_owned());
    }
    if !context.blocking.is_empty() {
        diagnostics.push("Blocking comments remain".to_owned());
    }
    if !context.suspended.is_empty() {
        diagnostics.push("Raw approval is suspended by current evaluation".to_owned());
    }
    if !context.missing.is_empty() {
        diagnostics.push("Comments have missing or ambiguous targets".to_owned());
    }
    if !remaining.is_empty() {
        diagnostics.push("Review units remain without effective approval".to_owned());
    }
    let stale_query_revision = active_query_revision_stale(document)?;
    if stale_query_revision {
        diagnostics.push(
            "Active named-query revision differs from its persisted work-queue expression"
                .to_owned(),
        );
    }

    let review_semantic_state_hash = semantic_state_hash(document)?;
    let has_attestations = !document.completion_attestations.is_empty();
    let has_matching_attestation = document
        .completion_attestations
        .iter()
        .any(|attestation| attestation.review_semantic_state_hash == review_semantic_state_hash);
    let stale_attestation = has_attestations && !has_matching_attestation;
    if stale_attestation {
        diagnostics
            .push("Completion attestations do not match the current semantic state".to_owned());
    }
    let stale = !context.stale_producer.is_empty() || stale_attestation || stale_query_revision;
    let ready = !stale
        && remaining.is_empty()
        && context.blocking.is_empty()
        && context.suspended.is_empty()
        && context.missing.is_empty();
    let attested = ready && has_matching_attestation;
    let status = if stale {
        CompletionStatusV1::Stale
    } else if attested {
        CompletionStatusV1::Complete
    } else if ready {
        CompletionStatusV1::ReadyForMaintainerAttestation
    } else {
        CompletionStatusV1::InProgress
    };
    let witnesses = CompletionWitnessesV1 {
        changed_domain: context.domain.iter().cloned().collect(),
        approved_raw: context.approved_raw.iter().cloned().collect(),
        approved_effective: context.approved_effective.iter().cloned().collect(),
        remaining: remaining.iter().cloned().collect(),
        blocking: context.blocking.iter().cloned().collect(),
        suspended: context.suspended.iter().cloned().collect(),
        missing: context.missing.iter().cloned().collect(),
        deferred: context.deferred.iter().cloned().collect(),
        unsupported: context.unsupported.iter().cloned().collect(),
        stale_producer: context.stale_producer.iter().cloned().collect(),
    };
    Ok(CompletionReportV1 {
        schema: "sfm.release-review-status/1".to_owned(),
        status,
        review_semantic_state_hash,
        changed_domain: context.domain.len(),
        approved_raw: context.approved_raw.len(),
        approved_effective: context.approved_effective.len(),
        remaining: remaining.len(),
        blocking: context.blocking.len(),
        suspended: context.suspended.len(),
        missing: context.missing.len(),
        deferred: context.deferred.len(),
        unsupported: context.unsupported.len(),
        stale_producer: context.stale_producer.len(),
        witnesses,
        diagnostics,
    })
}

/// Compute SHA-256 over the canonical semantic projection. Resume state and
/// attestations are replaced before serialization so the hash is non-circular.
///
/// # Errors
///
/// Returns an error when the semantic projection is invalid or cannot be
/// serialized.
pub fn semantic_state_hash(document: &ReleaseReviewDocumentV1) -> eyre::Result<String> {
    let mut projection = document.clone();
    projection.resume_state = ResumeStateV1::empty();
    projection.completion_attestations.clear();
    projection.review_session.style_rules.clear();
    let canonical = to_canonical_json(&projection)?;
    Ok(review_session_v1::sha256(
        format!("{HASH_DOMAIN}{canonical}").as_bytes(),
    ))
}

fn active_query_revision_stale(document: &ReleaseReviewDocumentV1) -> eyre::Result<bool> {
    let (Some(active_id), Some(captured_expression)) = (
        document.resume_state.active_query_id.as_deref(),
        document.resume_state.active_query_expression.as_deref(),
    ) else {
        return Ok(false);
    };
    let current = document
        .named_queries
        .iter()
        .find(|query| query.id == active_id)
        .ok_or_else(|| eyre!("unknown active named query '{active_id}'"))?;
    Ok(QueryParser::parse(&current.expression)?.normalized()
        != QueryParser::parse(captured_expression)?.normalized())
}

fn evaluate_expression(
    context: &QueryContext<'_>,
    expression: &QueryExpression,
    effective: bool,
    query_stack: &mut Vec<String>,
) -> eyre::Result<BTreeSet<String>> {
    match expression {
        QueryExpression::Atom(value) => evaluate_atom(context, value, effective, query_stack),
        QueryExpression::Effective(child) => evaluate_expression(context, child, true, query_stack),
        QueryExpression::Binary {
            left,
            operator,
            right,
        } => {
            let left = evaluate_expression(context, left, effective, query_stack)?;
            let right = evaluate_expression(context, right, effective, query_stack)?;
            Ok(match operator {
                QueryOperator::Union => set_union(&left, &right),
                QueryOperator::Intersect => set_intersection(&left, &right),
                QueryOperator::Difference => set_difference(&left, &right),
            })
        }
    }
}

fn evaluate_atom(
    context: &QueryContext<'_>,
    value: &str,
    effective: bool,
    query_stack: &mut Vec<String>,
) -> eyre::Result<BTreeSet<String>> {
    let lower = value.to_ascii_lowercase();
    if lower.starts_with('#') {
        let hashtag = normalize_hashtag(&lower);
        let approval = normalize_hashtag(
            &context
                .document
                .review_session
                .completion_policy
                .approval_hashtag,
        );
        if hashtag == approval {
            return Ok(if effective {
                context.approved_effective.clone()
            } else {
                context.approved_raw.clone()
            });
        }
        return Ok(hashtag_units(
            context.document,
            &context.comments,
            &context.evaluations,
            &hashtag,
            effective,
        ));
    }
    let builtin = match lower.as_str() {
        "head" | "changed" | "changed-domain" => Some(context.domain.clone()),
        "approved-raw" => Some(context.approved_raw.clone()),
        "approved-effective" => Some(context.approved_effective.clone()),
        "remaining" | "uncovered" => {
            Some(set_difference(&context.domain, &context.approved_effective))
        }
        "blocking" => Some(context.blocking.clone()),
        "suspended" => Some(context.suspended.clone()),
        "missing" => Some(context.missing.clone()),
        "deferred" => Some(context.deferred.clone()),
        "unsupported" => Some(context.unsupported.clone()),
        "stale-producer" => Some(context.stale_producer.clone()),
        _ => None,
    };
    if let Some(builtin) = builtin {
        return Ok(builtin);
    }

    let lane = context
        .document
        .review_units
        .iter()
        .filter(|unit| unit.lane_id.eq_ignore_ascii_case(value))
        .map(|unit| unit.id.clone())
        .collect::<BTreeSet<_>>();
    let configured_lane = context
        .document
        .review_session
        .revision_lanes
        .iter()
        .any(|lane| lane.id.eq_ignore_ascii_case(value))
        || context
            .document
            .repository_bindings
            .iter()
            .any(|binding| binding.lane_id.eq_ignore_ascii_case(value));
    if configured_lane {
        return Ok(lane);
    }

    if let Some(named) = context.named_queries.get(&lower) {
        if query_stack.iter().any(|entry| entry == &lower) {
            return Err(eyre!(
                "named-query cycle {} -> {}",
                query_stack.join(" -> "),
                named.id
            ));
        }
        query_stack.push(lower);
        let parsed = QueryParser::parse(&named.expression)?;
        let result = evaluate_expression(context, &parsed, effective, query_stack);
        query_stack.pop();
        return result;
    }
    Err(eyre!("unknown release-review query atom '{value}'"))
}

fn hashtag_units(
    document: &ReleaseReviewDocumentV1,
    comments: &BTreeMap<String, &review_session_v2::CommentV2>,
    evaluations: &BTreeMap<String, CommentEvaluationV2>,
    hashtag: &str,
    effective: bool,
) -> BTreeSet<String> {
    let mut answer = BTreeSet::new();
    let approval = normalize_hashtag(&document.review_session.completion_policy.approval_hashtag);
    let blocking_tags = document
        .review_session
        .completion_policy
        .blocking_hashtags
        .iter()
        .map(|value| normalize_hashtag(value))
        .collect::<BTreeSet<_>>();
    for comment in comments.values() {
        let tags = review_session_v1::derived_hashtags(&comment.text);
        if !tags.iter().any(|tag| tag == hashtag) {
            continue;
        }
        let Some(evaluation) = evaluations.get(&comment.id) else {
            continue;
        };
        if effective {
            let CommentTargetV2::CommittedSelection {
                candidate_promotion,
                ..
            } = &comment.target
            else {
                continue;
            };
            if candidate_promotion.is_some() {
                continue;
            }
            let resolved = evaluation.status == EvaluationStatusV2::ResolvedExactly
                || (hashtag != approval
                    && evaluation.status == EvaluationStatusV2::ResolvedWithRelocation);
            if !resolved {
                continue;
            }
            if hashtag == approval && tags.iter().any(|tag| blocking_tags.contains(tag)) {
                continue;
            }
        }
        let ranges = if effective || !evaluation.ranges.is_empty() {
            evaluation.ranges.clone()
        } else {
            original_ranges(&comment.target)
        };
        answer.extend(units_intersecting(document, &ranges));
    }
    answer
}

fn unresolved_units(
    document: &ReleaseReviewDocumentV1,
    comments: &BTreeMap<String, &review_session_v2::CommentV2>,
    evaluations: &BTreeMap<String, CommentEvaluationV2>,
) -> BTreeSet<String> {
    let mut answer = BTreeSet::new();
    for comment in comments.values() {
        let Some(evaluation) = evaluations.get(&comment.id) else {
            continue;
        };
        if matches!(
            evaluation.status,
            EvaluationStatusV2::ResolvedExactly
                | EvaluationStatusV2::ResolvedWithRelocation
                | EvaluationStatusV2::CandidatePinned
        ) {
            continue;
        }
        answer.extend(units_intersecting(
            document,
            &original_ranges(&comment.target),
        ));
    }
    answer
}

fn original_ranges(target: &CommentTargetV2) -> Vec<DocumentRangeV1> {
    let CommentTargetV2::CommittedSelection { selection_rule, .. } = target else {
        return Vec::new();
    };
    let mut answer = Vec::new();
    literal_ranges(selection_rule, &mut answer);
    answer
}

fn literal_ranges(rule: &SelectionRuleV1, answer: &mut Vec<DocumentRangeV1>) {
    match rule {
        SelectionRuleV1::LiteralUtf8Range {
            document_revision_id,
            start_byte,
            end_byte,
            ..
        } => answer.push(DocumentRangeV1 {
            document_revision_id: document_revision_id.clone(),
            start_byte: *start_byte,
            end_byte: *end_byte,
        }),
        SelectionRuleV1::Union { rules } | SelectionRuleV1::Intersection { rules } => {
            for rule in rules {
                literal_ranges(rule, answer);
            }
        }
        SelectionRuleV1::Difference { include, exclude } => {
            literal_ranges(include, answer);
            for rule in exclude {
                literal_ranges(rule, answer);
            }
        }
    }
}

fn units_intersecting(
    document: &ReleaseReviewDocumentV1,
    ranges: &[DocumentRangeV1],
) -> BTreeSet<String> {
    document
        .review_units
        .iter()
        .filter(|unit| {
            ranges.iter().any(|range| {
                unit_side_matches(
                    unit.before_document_revision_id.as_deref(),
                    &unit.before_ranges,
                    range,
                ) || unit_side_matches(
                    unit.after_document_revision_id.as_deref(),
                    &unit.after_ranges,
                    range,
                )
            })
        })
        .map(|unit| unit.id.clone())
        .collect()
}

fn unit_side_matches(
    document_revision_id: Option<&str>,
    unit_ranges: &[Utf8RangeV1],
    comment_range: &DocumentRangeV1,
) -> bool {
    if document_revision_id != Some(comment_range.document_revision_id.as_str()) {
        return false;
    }
    unit_ranges.is_empty()
        || unit_ranges.iter().any(|unit_range| {
            ranges_overlap(
                unit_range.start_byte,
                unit_range.end_byte,
                comment_range.start_byte,
                comment_range.end_byte,
            )
        })
}

fn ranges_overlap(
    first_start: usize,
    first_end: usize,
    second_start: usize,
    second_end: usize,
) -> bool {
    if first_start == first_end || second_start == second_end {
        first_start <= second_end && second_start <= first_end
    } else {
        first_start.max(second_start) < first_end.min(second_end)
    }
}

fn set_union(left: &BTreeSet<String>, right: &BTreeSet<String>) -> BTreeSet<String> {
    left.union(right).cloned().collect()
}

fn set_intersection(left: &BTreeSet<String>, right: &BTreeSet<String>) -> BTreeSet<String> {
    left.intersection(right).cloned().collect()
}

fn set_difference(left: &BTreeSet<String>, right: &BTreeSet<String>) -> BTreeSet<String> {
    left.difference(right).cloned().collect()
}

fn embedded_documents(
    session: &ReviewSessionV2,
) -> eyre::Result<BTreeMap<&str, &DocumentRevisionV1>> {
    let mut answer = BTreeMap::new();
    for lane in &session.revision_lanes {
        for document in lane.before.documents.iter().chain(&lane.after.documents) {
            if let Some(previous) = answer.insert(document.id.as_str(), document)
                && previous != document
            {
                return Err(eyre!(
                    "duplicate embedded document revision '{}'",
                    document.id
                ));
            }
        }
    }
    Ok(answer)
}

fn normalize_hashtag(value: &str) -> String {
    let value = value.nfc().collect::<String>().to_lowercase();
    if value.starts_with('#') {
        value
    } else {
        format!("#{value}")
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

fn optional_path(value: Option<&str>, label: &str) -> eyre::Result<()> {
    if let Some(value) = value {
        require_relative_path(value, label)?;
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

fn require_git_sha1(value: &str, label: &str) -> eyre::Result<()> {
    require_text(value, label)?;
    if value.len() != 40
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    {
        return Err(eyre!("{label} must be lowercase Git SHA-1"));
    }
    Ok(())
}

fn normalize_relative_path(value: &str) -> String {
    value.replace('\\', "/")
}

fn require_relative_path(value: &str, label: &str) -> eyre::Result<()> {
    require_text(value, label)?;
    if value.starts_with('/')
        || (value.len() >= 3
            && value.as_bytes()[0].is_ascii_alphabetic()
            && value.as_bytes()[1] == b':'
            && value.as_bytes()[2] == b'/')
        || value.contains("../")
        || value == ".."
    {
        return Err(eyre!("{label} must be a repository-relative path"));
    }
    Ok(())
}

fn ensure_unique<T: Ord>(values: impl IntoIterator<Item = T>, label: &str) -> eyre::Result<()> {
    let mut seen = BTreeSet::new();
    for value in values {
        if !seen.insert(value) {
            return Err(eyre!("duplicate {label}"));
        }
    }
    Ok(())
}

fn canonical_corpus_address(corpus: &CorpusDocumentV1) -> String {
    format!(
        "{}\0{}\0{}",
        corpus.lane_id,
        snapshot_side_name(corpus.snapshot_side),
        corpus.path
    )
}

const fn snapshot_side_name(side: SnapshotSideV1) -> &'static str {
    match side {
        SnapshotSideV1::Before => "BEFORE",
        SnapshotSideV1::After => "AFTER",
    }
}

const fn operation_name(operation: ChangeOperationV1) -> &'static str {
    match operation {
        ChangeOperationV1::Added => "ADDED",
        ChangeOperationV1::Copied => "COPIED",
        ChangeOperationV1::Deleted => "DELETED",
        ChangeOperationV1::Modified => "MODIFIED",
        ChangeOperationV1::Renamed => "RENAMED",
        ChangeOperationV1::TypeChanged => "TYPE_CHANGED",
    }
}

fn canonical_unit_path(unit: &ReviewUnitV1) -> &str {
    unit.path_after
        .as_deref()
        .or(unit.path_before.as_deref())
        .unwrap_or("")
}

fn first_range_start(unit: &ReviewUnitV1) -> usize {
    unit.after_ranges
        .first()
        .or_else(|| unit.before_ranges.first())
        .map_or(0, |range| range.start_byte)
}

#[cfg(test)]
pub(crate) mod test_support {
    use super::*;
    use crate::review_session_v1::CommentStyleRuleV1;
    use crate::review_session_v1::CompletionPolicyV1;
    use crate::review_session_v1::ProvenanceV1;
    use crate::review_session_v1::RepositoryV1;
    use crate::review_session_v1::RevisionLaneV1;
    use crate::review_session_v1::SnapshotV1;
    use crate::review_session_v2::CandidatePromotionLinkV2;
    use crate::review_session_v2::CommentV2;

    pub fn exact_document() -> ReleaseReviewDocumentV1 {
        let before_text = "class A { int oldValue; }\n";
        let after_text = "class A { int value; }\n";
        let before_sha = review_session_v1::sha256(before_text.as_bytes());
        let after_sha = review_session_v1::sha256(after_text.as_bytes());
        let selection = PinnedSelectionV1 {
            selection_revision: "selection/1".to_owned(),
            source_expression: "editor://focused".to_owned(),
            primary_range_index: 0,
            ranges: vec![PinnedSelectionRangeV1 {
                direction: SelectionDirectionV1::Forward,
                document_revision_id: "after:A.java".to_owned(),
                document_sha256: after_sha.clone(),
                start_byte: 0,
                end_byte: after_text.len(),
            }],
        };
        let rule = SelectionRuleV1::LiteralUtf8Range {
            document_revision_id: "after:A.java".to_owned(),
            start_byte: 0,
            end_byte: after_text.len(),
            document_sha256: after_sha.clone(),
            selected_text_sha256: after_sha.clone(),
        };
        let proposal = SelectorProposalV1 {
            id: "selector-1".to_owned(),
            kind: SelectorKindV1::Declaration,
            selection_rule: rule.clone(),
            literal_witness: selection.clone(),
            semantic_provider: Some("sfm.java-interaction-map".to_owned()),
            semantic_key: Some("A".to_owned()),
            semantic_provenance: vec![
                EvidenceV1 {
                    key: "provider".to_owned(),
                    value: "java".to_owned(),
                },
                EvidenceV1 {
                    key: "generation".to_owned(),
                    value: "1".to_owned(),
                },
            ],
            confidence: ProposalConfidenceV1::Exact,
            projection_fingerprint: "3".repeat(64),
            source_snapshot_id: "after".to_owned(),
            diagnostics: Vec::new(),
        };
        let evaluation = SelectorEvaluationResultV1 {
            selector_id: "selector-1".to_owned(),
            status: SelectorEvaluationStatusV1::Exact,
            ranges: vec![AddressedRangeV1 {
                document_revision_id: "after:A.java".to_owned(),
                start_byte: 0,
                end_byte: after_text.len(),
            }],
            candidates: Vec::new(),
            invalidation_keys: vec![InvalidationKeyV1 {
                owner: "java".to_owned(),
                generation: "1".to_owned(),
                fingerprint: "4".repeat(64),
            }],
            diagnostics: Vec::new(),
        };
        ReleaseReviewDocumentV1 {
            schema: SCHEMA.to_owned(),
            review_session: ReviewSessionV2 {
                schema: review_session_v2::SCHEMA.to_owned(),
                id: "release-review".to_owned(),
                title: "1.19.2 release review".to_owned(),
                coordinate_system: review_session_v1::COORDINATE_SYSTEM.to_owned(),
                revision_lanes: vec![RevisionLaneV1 {
                    id: "1.19.2".to_owned(),
                    repository: RepositoryV1 {
                        id: "sfm".to_owned(),
                        root_hint: ".".to_owned(),
                    },
                    version_label: Some("1.19.2".to_owned()),
                    before: SnapshotV1 {
                        id: "previous_release".to_owned(),
                        documents: vec![DocumentRevisionV1 {
                            id: "before:A.java".to_owned(),
                            path: "A.java".to_owned(),
                            encoding: "utf-8".to_owned(),
                            sha256: before_sha.clone(),
                            text: before_text.to_owned(),
                        }],
                    },
                    after: SnapshotV1 {
                        id: "HEAD".to_owned(),
                        documents: vec![DocumentRevisionV1 {
                            id: "after:A.java".to_owned(),
                            path: "A.java".to_owned(),
                            encoding: "utf-8".to_owned(),
                            sha256: after_sha.clone(),
                            text: after_text.to_owned(),
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
                        selection_rule: rule,
                        candidate_promotion: None,
                    },
                    forbidden_authoritative_tags: None,
                }],
                style_rules: vec![CommentStyleRuleV1 {
                    id: "approved".to_owned(),
                    required_hashtags: vec!["#approved".to_owned()],
                    priority: 10,
                    foreground: Some("green".to_owned()),
                    background: None,
                    underline: None,
                    gutter_marker: Some("A".to_owned()),
                    enabled: true,
                }],
                completion_policy: CompletionPolicyV1 {
                    coverage_mode: "changed_surface".to_owned(),
                    approval_hashtag: "#approved".to_owned(),
                    blocking_hashtags: vec!["#problem".to_owned(), "#needs-change".to_owned()],
                },
            },
            repository_bindings: vec![RepositoryBindingV1 {
                lane_id: "1.19.2".to_owned(),
                repository_id: "sfm".to_owned(),
                root_hint: ".".to_owned(),
                before_label: "4.34.0-1.19.2".to_owned(),
                before_commit: "1".repeat(40),
                before_tree: "2".repeat(40),
                after_label: "HEAD".to_owned(),
                candidate_commit: "3".repeat(40),
                candidate_tree: "4".repeat(40),
                review_evidence_paths: vec!["docs/reviews/release.sfm-review.json".to_owned()],
            }],
            corpus_documents: vec![
                CorpusDocumentV1 {
                    id: "corpus-before".to_owned(),
                    lane_id: "1.19.2".to_owned(),
                    snapshot_side: SnapshotSideV1::Before,
                    path: "A.java".to_owned(),
                    document_revision_id: "before:A.java".to_owned(),
                    sha256: before_sha,
                    source_owner: "git:before".to_owned(),
                    source_locator: "git:1:A.java".to_owned(),
                    materialization: MaterializationV1::Complete,
                },
                CorpusDocumentV1 {
                    id: "corpus-after".to_owned(),
                    lane_id: "1.19.2".to_owned(),
                    snapshot_side: SnapshotSideV1::After,
                    path: "A.java".to_owned(),
                    document_revision_id: "after:A.java".to_owned(),
                    sha256: after_sha,
                    source_owner: "git:after".to_owned(),
                    source_locator: "git:3:A.java".to_owned(),
                    materialization: MaterializationV1::Complete,
                },
            ],
            review_units: vec![ReviewUnitV1 {
                id: "unit-1".to_owned(),
                lane_id: "1.19.2".to_owned(),
                operation: ChangeOperationV1::Modified,
                path_before: Some("A.java".to_owned()),
                path_after: Some("A.java".to_owned()),
                before_document_revision_id: Some("before:A.java".to_owned()),
                after_document_revision_id: Some("after:A.java".to_owned()),
                before_ranges: vec![Utf8RangeV1 {
                    start_byte: 0,
                    end_byte: before_text.len(),
                }],
                after_ranges: vec![Utf8RangeV1 {
                    start_byte: 0,
                    end_byte: after_text.len(),
                }],
                language: "java".to_owned(),
                surface_kind: SurfaceKindV1::Declaration,
                semantic_key: Some("A".to_owned()),
                limitation: None,
                producer_id: "git-java".to_owned(),
                producer_generation: "1".to_owned(),
            }],
            selector_bindings: vec![CommentSelectorBindingV1 {
                comment_id: "comment-1".to_owned(),
                captured_selection: selection,
                selected_proposal: proposal,
            }],
            migration_reports: vec![MigrationReportV1 {
                id: "migration-1".to_owned(),
                source_selector_id: "selector-1".to_owned(),
                source_evaluation: evaluation.clone(),
                candidate_evaluation: evaluation,
                old_witnesses: Vec::new(),
                new_candidates: Vec::new(),
                decision: MigrationDecisionV1::Unresolved,
                decision_comment_id: None,
            }],
            named_queries: vec![NamedQueryV1 {
                id: "reviewable".to_owned(),
                expression: "#approved intersect 1.19.2 HEAD".to_owned(),
            }],
            resume_state: ResumeStateV1 {
                active_query_id: Some("reviewable".to_owned()),
                active_query_expression: None,
                current_unit_id: Some("unit-1".to_owned()),
                deferred_unit_ids: Vec::new(),
                generation: 1,
            },
            producer_generations: vec![ProducerGenerationV1 {
                producer_id: "git-java".to_owned(),
                generation: "1".to_owned(),
                input_fingerprint: "1".repeat(64),
                output_fingerprint: "2".repeat(64),
            }],
            completion_attestations: Vec::new(),
        }
    }

    pub fn promoted_document() -> ReleaseReviewDocumentV1 {
        let mut document = exact_document();
        let CommentTargetV2::CommittedSelection {
            candidate_promotion,
            ..
        } = &mut document.review_session.comments[0].target
        else {
            panic!("committed target expected");
        };
        *candidate_promotion = Some(CandidatePromotionLinkV2 {
            source_candidate_comment_id: "candidate-comment".to_owned(),
            source_candidate_target_sha256: "5".repeat(64),
            decision_id: "decision".to_owned(),
            executed_history_head_id: "history".to_owned(),
            executed_state_id: "state".to_owned(),
            executed_state_hash: "state-hash".to_owned(),
            correspondence: "exact".to_owned(),
            correspondence_evidence: Vec::new(),
        });
        document
    }

    pub fn in_progress_document() -> ReleaseReviewDocumentV1 {
        let mut document = exact_document();
        document.review_session.comments[0].text = "review pending".to_owned();
        document
    }

    pub fn stale_document() -> ReleaseReviewDocumentV1 {
        let mut document = exact_document();
        document.review_units[0].producer_generation = "2".to_owned();
        document
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::review_session_v1::ProvenanceV1;
    use crate::review_session_v2::CommentV2;

    const JAVA_CANONICAL_FIXTURE: &str =
        include_str!("../../../../docs/architecture/fixtures/release-review-v1.json");

    fn zero_lane_document() -> ReleaseReviewDocumentV1 {
        let mut document = test_support::exact_document();
        document.review_session.revision_lanes.clear();
        document.review_session.comments.clear();
        document.repository_bindings.clear();
        document.corpus_documents.clear();
        document.review_units.clear();
        document.selector_bindings.clear();
        document.migration_reports.clear();
        document.named_queries.clear();
        document.resume_state = ResumeStateV1::empty();
        document.producer_generations.clear();
        document.completion_attestations.clear();
        document
    }

    fn two_lane_document() -> ReleaseReviewDocumentV1 {
        let mut document = test_support::exact_document();
        let mut second_lane = document.review_session.revision_lanes[0].clone();
        second_lane.id = "1.20.1".to_owned();
        second_lane.version_label = Some("1.20.1".to_owned());
        second_lane.before.id = "previous_release-1.20.1".to_owned();
        second_lane.after.id = "HEAD-1.20.1".to_owned();
        second_lane.before.documents[0].id = "before-1.20.1:A.java".to_owned();
        second_lane.after.documents[0].id = "after-1.20.1:A.java".to_owned();
        document.review_session.revision_lanes.push(second_lane);

        let mut repository = document.repository_bindings[0].clone();
        repository.lane_id = "1.20.1".to_owned();
        repository.before_commit = "5".repeat(40);
        repository.before_tree = "6".repeat(40);
        repository.candidate_commit = "7".repeat(40);
        repository.candidate_tree = "8".repeat(40);
        document.repository_bindings.push(repository);

        let mut before_corpus = document
            .corpus_documents
            .iter()
            .find(|corpus| corpus.snapshot_side == SnapshotSideV1::Before)
            .expect("before corpus")
            .clone();
        before_corpus.id = "corpus-before-1.20.1".to_owned();
        before_corpus.lane_id = "1.20.1".to_owned();
        before_corpus.document_revision_id = "before-1.20.1:A.java".to_owned();
        before_corpus.source_locator = "git:5:A.java".to_owned();
        document.corpus_documents.push(before_corpus);

        let mut after_corpus = document
            .corpus_documents
            .iter()
            .find(|corpus| corpus.snapshot_side == SnapshotSideV1::After)
            .expect("after corpus")
            .clone();
        after_corpus.id = "corpus-after-1.20.1".to_owned();
        after_corpus.lane_id = "1.20.1".to_owned();
        after_corpus.document_revision_id = "after-1.20.1:A.java".to_owned();
        after_corpus.source_locator = "git:7:A.java".to_owned();
        document.corpus_documents.push(after_corpus);

        let mut unit = document.review_units[0].clone();
        unit.id = "unit-2".to_owned();
        unit.lane_id = "1.20.1".to_owned();
        unit.before_document_revision_id = Some("before-1.20.1:A.java".to_owned());
        unit.after_document_revision_id = Some("after-1.20.1:A.java".to_owned());
        document.review_units.push(unit);
        document
    }

    fn literal_comment(
        document: &DocumentRevisionV1,
        id: &str,
        text: &str,
        start_byte: usize,
        end_byte: usize,
        selected_text_sha256: Option<String>,
    ) -> CommentV2 {
        CommentV2 {
            id: id.to_owned(),
            text: text.to_owned(),
            provenance: ProvenanceV1 {
                kind: "human".to_owned(),
                producer: "maintainer".to_owned(),
                version: "1".to_owned(),
                parent_comment_ids: Vec::new(),
            },
            target: CommentTargetV2::CommittedSelection {
                selection_rule: SelectionRuleV1::LiteralUtf8Range {
                    document_revision_id: document.id.clone(),
                    start_byte,
                    end_byte,
                    document_sha256: document.sha256.clone(),
                    selected_text_sha256: selected_text_sha256.unwrap_or_else(|| {
                        review_session_v1::sha256(&document.text.as_bytes()[start_byte..end_byte])
                    }),
                },
                candidate_promotion: None,
            },
            forbidden_authoritative_tags: None,
        }
    }

    fn completion_witness_matrix_document() -> ReleaseReviewDocumentV1 {
        let mut document = test_support::exact_document();
        let after = document.review_session.revision_lanes[0].after.documents[0].clone();
        let template = document.review_units[0].clone();
        let unit = |id: &str,
                    start_byte: usize,
                    surface_kind: SurfaceKindV1,
                    producer_generation: &str| {
            let mut unit = template.clone();
            unit.id = id.to_owned();
            unit.before_document_revision_id = None;
            unit.before_ranges.clear();
            unit.after_ranges = vec![Utf8RangeV1 {
                start_byte,
                end_byte: start_byte + 1,
            }];
            unit.surface_kind = surface_kind;
            unit.limitation = (surface_kind == SurfaceKindV1::Unsupported)
                .then(|| "structural analysis unavailable".to_owned());
            unit.producer_generation = producer_generation.to_owned();
            unit
        };
        document.review_units = vec![
            unit("unit-approved", 0, SurfaceKindV1::Declaration, "1"),
            unit("unit-blocking", 2, SurfaceKindV1::Declaration, "1"),
            unit("unit-deferred", 4, SurfaceKindV1::Declaration, "1"),
            unit("unit-missing", 6, SurfaceKindV1::Declaration, "1"),
            unit("unit-remaining", 8, SurfaceKindV1::Declaration, "1"),
            unit("unit-stale", 10, SurfaceKindV1::Declaration, "2"),
            unit("unit-suspended", 12, SurfaceKindV1::Declaration, "1"),
            unit("unit-unsupported", 14, SurfaceKindV1::Unsupported, "1"),
        ];
        document.review_session.comments = vec![
            literal_comment(&after, "comment-approved", "#approved", 0, 1, None),
            literal_comment(&after, "comment-blocking", "#needs-change", 2, 3, None),
            literal_comment(
                &after,
                "comment-missing",
                "unresolved target",
                6,
                7,
                Some("f".repeat(64)),
            ),
            literal_comment(
                &after,
                "comment-suspended",
                "#approved #problem",
                12,
                13,
                None,
            ),
        ];
        document.selector_bindings.clear();
        document.migration_reports.clear();
        document.resume_state.current_unit_id = Some("unit-deferred".to_owned());
        document.resume_state.deferred_unit_ids = vec!["unit-deferred".to_owned()];
        document
    }

    #[test]
    fn rust_parser_and_writer_preserve_the_java_canonical_fixture_bytes() {
        let parsed = parse(JAVA_CANONICAL_FIXTURE).expect("Java canonical fixture should parse");
        assert_eq!(
            to_canonical_json(&parsed).expect("Rust canonical fixture emission"),
            JAVA_CANONICAL_FIXTURE.replace("\r\n", "\n")
        );
    }

    #[test]
    fn shared_fixture_freezes_three_snapshots_multidocument_unicode_and_one_approval_authority() {
        let parsed = parse(JAVA_CANONICAL_FIXTURE).expect("shared canonical fixture should parse");
        let pinned_lane = parsed
            .review_session
            .revision_lanes
            .iter()
            .find(|lane| lane.id == "1.19.2")
            .expect("pinned release lane");
        let live_lane = parsed
            .review_session
            .revision_lanes
            .iter()
            .find(|lane| lane.id == "1.19.2-live")
            .expect("current-live witness lane");
        assert_eq!(
            pinned_lane.before.id,
            "1111111111111111111111111111111111111111"
        );
        assert_eq!(
            pinned_lane.after.id,
            "2222222222222222222222222222222222222222"
        );
        assert_eq!(pinned_lane.after.id, live_lane.before.id);
        assert_eq!(
            live_lane.after.id,
            "5555555555555555555555555555555555555555"
        );
        assert_eq!(
            pinned_lane
                .after
                .documents
                .iter()
                .map(|document| document.path.as_str())
                .collect::<Vec<_>>(),
            ["src/Cafe.java", "src/Other.java", "src/Unicode.java"]
        );

        let pinned_repository = parsed
            .repository_bindings
            .iter()
            .find(|binding| binding.lane_id == "1.19.2")
            .expect("pinned repository binding");
        let live_repository = parsed
            .repository_bindings
            .iter()
            .find(|binding| binding.lane_id == "1.19.2-live")
            .expect("live repository binding");
        assert_eq!(pinned_repository.candidate_commit, pinned_lane.after.id);
        assert_eq!(
            live_repository.before_commit,
            pinned_repository.candidate_commit
        );
        assert_eq!(live_repository.candidate_commit, live_lane.after.id);
        assert_ne!(
            pinned_repository.candidate_commit,
            live_repository.candidate_commit
        );

        let binding = parsed
            .selector_bindings
            .iter()
            .find(|binding| binding.comment_id == "human:unicode-multidoc-note")
            .expect("multi-document Unicode selector binding");
        assert_eq!(binding.captured_selection.primary_range_index, 1);
        assert_eq!(
            binding.captured_selection.selection_revision,
            "selection-fixture@2222222222222222222222222222222222222222"
        );
        assert!(
            !binding
                .captured_selection
                .selection_revision
                .contains("5555555555555555555555555555555555555555")
        );
        assert_eq!(
            binding
                .captured_selection
                .ranges
                .iter()
                .map(|range| format!(
                    "{}:{}-{}:{:?}",
                    range.document_revision_id, range.start_byte, range.end_byte, range.direction
                ))
                .collect::<Vec<_>>(),
            [
                "1.19.2:after:src/Cafe.java:23-28:Backward",
                "1.19.2:after:src/Unicode.java:61-67:Forward",
                "1.19.2:after:src/Unicode.java:23-30:Forward",
            ]
        );
        assert_eq!(
            binding.captured_selection,
            binding.selected_proposal.literal_witness
        );
        assert_eq!(
            binding.selected_proposal.kind,
            SelectorKindV1::BoundedMultiRegion
        );
        assert_eq!(
            binding.selected_proposal.source_snapshot_id,
            pinned_repository.candidate_commit
        );
        assert_eq!(
            binding
                .selected_proposal
                .semantic_provenance
                .iter()
                .map(|evidence| (evidence.key.as_str(), evidence.value.as_str()))
                .collect::<Vec<_>>(),
            [
                (
                    "current-live-revision",
                    "5555555555555555555555555555555555555555"
                ),
                (
                    "pinned-review-revision",
                    "2222222222222222222222222222222222222222"
                ),
                ("region-kind", "disjoint-multi-document-unicode"),
            ]
        );
        let SelectionRuleV1::Union { rules } = &binding.selected_proposal.selection_rule else {
            panic!("multi-document selector must remain an ordered union");
        };
        assert_eq!(rules.len(), 3);
        let comment = parsed
            .review_session
            .comments
            .iter()
            .find(|comment| comment.id == binding.comment_id)
            .expect("selector owner comment");
        let CommentTargetV2::CommittedSelection {
            selection_rule,
            candidate_promotion,
        } = &comment.target
        else {
            panic!("fixture selector owner must be a committed comment");
        };
        assert_eq!(selection_rule, &binding.selected_proposal.selection_rule);
        assert!(candidate_promotion.is_none());

        assert_eq!(
            parsed
                .corpus_documents
                .iter()
                .map(|document| format!(
                    "{}|{}|{}|{:?}",
                    document.id,
                    document.source_owner,
                    document.source_locator,
                    document.materialization
                ))
                .collect::<Vec<_>>(),
            [
                "corpus:before:cafe|git|1111111111111111111111111111111111111111:src/Cafe.java|Complete",
                "corpus:after:cafe|git|2222222222222222222222222222222222222222:src/Cafe.java|Complete",
                "corpus:after:other|git|2222222222222222222222222222222222222222:src/Other.java|Complete",
                "corpus:after:unicode|git|2222222222222222222222222222222222222222:src/Unicode.java|Complete",
                "corpus:live-before:cafe|git|2222222222222222222222222222222222222222:src/Cafe.java|Complete",
                "corpus:live-after:cafe|git|5555555555555555555555555555555555555555:src/Cafe.java|Complete",
                "corpus:live-after:missing|candidate-history-frame|fixture-plan#live:src/Missing.java|Missing",
                "corpus:live-after:partial|candidate-history-frame|fixture-plan#live:src/Partial.java|Partial",
            ]
        );
        let complete = parsed
            .corpus_documents
            .iter()
            .find(|document| document.id == "corpus:after:cafe")
            .expect("complete corpus witness");
        assert_eq!(complete.source_owner, "git");
        assert_eq!(
            complete.source_locator,
            "2222222222222222222222222222222222222222:src/Cafe.java"
        );
        assert_eq!(complete.materialization, MaterializationV1::Complete);
        let partial = parsed
            .corpus_documents
            .iter()
            .find(|document| document.id == "corpus:live-after:partial")
            .expect("partial frame witness");
        assert_eq!(partial.materialization, MaterializationV1::Partial);
        assert_eq!(partial.source_owner, "candidate-history-frame");
        assert_eq!(partial.source_locator, "fixture-plan#live:src/Partial.java");
        let missing = parsed
            .corpus_documents
            .iter()
            .find(|document| document.id == "corpus:live-after:missing")
            .expect("missing frame witness");
        assert_eq!(missing.materialization, MaterializationV1::Missing);
        assert_eq!(missing.source_owner, "candidate-history-frame");
        assert_eq!(missing.source_locator, "fixture-plan#live:src/Missing.java");

        let embedded_ids = parsed
            .review_session
            .revision_lanes
            .iter()
            .flat_map(|lane| lane.before.documents.iter().chain(&lane.after.documents))
            .map(|document| document.id.as_str())
            .collect::<BTreeSet<_>>();
        assert!(!embedded_ids.contains(partial.document_revision_id.as_str()));
        assert!(!embedded_ids.contains(missing.document_revision_id.as_str()));

        let approval_comments = parsed
            .review_session
            .comments
            .iter()
            .filter(|comment| {
                review_session_v1::derived_hashtags(&comment.text)
                    .iter()
                    .any(|hashtag| hashtag == "#approved")
            })
            .map(|comment| comment.id.as_str())
            .collect::<Vec<_>>();
        assert_eq!(approval_comments, ["human:approved-value"]);
        assert_eq!(JAVA_CANONICAL_FIXTURE.matches("\"comments\":").count(), 1);
        assert_eq!(JAVA_CANONICAL_FIXTURE.matches("\"approved\":").count(), 0);
        assert_eq!(
            query(&parsed, "#approved intersect 1.19.2 HEAD")
                .expect("raw approval query")
                .review_unit_ids,
            ["unit:src/Cafe.java:value"]
        );

        let mut corpus_hash_mismatch = parsed.clone();
        corpus_hash_mismatch
            .corpus_documents
            .iter_mut()
            .find(|document| document.id == "corpus:after:cafe")
            .expect("mutable corpus witness")
            .sha256 = "0".repeat(64);
        assert!(validate(&corpus_hash_mismatch).is_err());
    }

    #[test]
    fn canonical_document_round_trips_and_rejects_unknown_fields() {
        let document = test_support::exact_document();
        let canonical = to_canonical_json(&document).expect("canonical release review");
        let reparsed = parse(&canonical).expect("canonical release review should parse");
        assert_eq!(
            to_canonical_json(&reparsed).expect("canonical re-emission"),
            canonical
        );

        let root_unknown = canonical.replacen('{', "{\"future_root\":true,", 1);
        assert!(parse(&root_unknown).is_err(), "root fields must be strict");

        let nested_unknown = canonical.replacen(
            "\"text\": \"#approved reviewed\",",
            "\"text\": \"#approved reviewed\",\n      \"future_comment\": true,",
            1,
        );
        assert_ne!(nested_unknown, canonical, "nested fixture edit must apply");
        assert!(
            parse(&nested_unknown).is_err(),
            "nested contract fields must be strict"
        );

        let rule_unknown = canonical.replacen(
            "\"kind\": \"literal_utf8_range\",",
            "\"kind\": \"literal_utf8_range\",\n            \"future_rule_field\": true,",
            1,
        );
        assert_ne!(
            rule_unknown, canonical,
            "selection-rule fixture edit must apply"
        );
        assert!(
            parse(&rule_unknown).is_err(),
            "tagged selection-rule fields must be strict"
        );
    }

    #[test]
    fn exact_document_query_preserves_spelling_and_raw_effective_distinction() {
        let expression = "#approved intersect 1.19.2 HEAD";
        let exact = query(&test_support::exact_document(), expression).expect("exact query");
        assert_eq!(exact.expression, expression);
        assert_eq!(
            exact.normalized_expression,
            "((#approved intersect 1.19.2) intersect HEAD)"
        );
        assert_eq!(exact.review_unit_ids, ["unit-1"]);

        let promoted = test_support::promoted_document();
        let raw = query(&promoted, expression).expect("raw promoted query");
        assert_eq!(raw.review_unit_ids, ["unit-1"]);
        let effective = query(&promoted, "effective(#approved) intersect 1.19.2 HEAD")
            .expect("effective promoted query");
        assert!(effective.review_unit_ids.is_empty());

        let report = completion(&promoted).expect("promoted completion");
        assert_eq!(report.status, CompletionStatusV1::InProgress);
        assert_eq!(report.approved_raw, 1);
        assert_eq!(report.approved_effective, 0);
        assert_eq!(report.suspended, 1);
        assert_eq!(report.witnesses.changed_domain, ["unit-1"]);
        assert_eq!(report.witnesses.approved_raw, ["unit-1"]);
        assert!(report.witnesses.approved_effective.is_empty());
        assert_eq!(report.witnesses.remaining, ["unit-1"]);
        assert_eq!(report.witnesses.suspended, ["unit-1"]);
    }

    #[test]
    fn effective_approval_excludes_a_separate_overlapping_blocking_comment() {
        let mut document = test_support::exact_document();
        let mut blocking_comment = document.review_session.comments[0].clone();
        blocking_comment.id = "comment-blocking".to_owned();
        blocking_comment.text = "#problem separate overlapping blocker".to_owned();
        document.review_session.comments.push(blocking_comment);

        assert_eq!(
            query(&document, "#approved")
                .expect("raw approval query")
                .review_unit_ids,
            ["unit-1"]
        );
        assert_eq!(
            query(&document, "#problem")
                .expect("raw blocking hashtag query")
                .review_unit_ids,
            ["unit-1"]
        );
        assert_eq!(
            query(&document, "blocking")
                .expect("aggregate blocking query")
                .review_unit_ids,
            ["unit-1"]
        );
        assert!(
            query(&document, "effective(#approved)")
                .expect("effective approval query")
                .review_unit_ids
                .is_empty(),
            "a blocker on a separate overlapping comment must suspend approval"
        );

        let report = completion(&document).expect("blocked completion report");
        assert_eq!(report.status, CompletionStatusV1::InProgress);
        assert_eq!(report.approved_raw, 1);
        assert_eq!(report.approved_effective, 0);
        assert_eq!(report.blocking, 1);
        assert_eq!(report.suspended, 1);
    }

    #[test]
    fn changed_and_uncovered_aliases_and_empty_configured_lanes_are_queryable() {
        let exact = test_support::exact_document();
        assert_eq!(
            query(&exact, "changed")
                .expect("changed alias")
                .review_unit_ids,
            ["unit-1"]
        );
        assert!(
            query(&exact, "uncovered")
                .expect("covered document has no uncovered units")
                .review_unit_ids
                .is_empty()
        );

        let in_progress = test_support::in_progress_document();
        assert_eq!(
            query(&in_progress, "uncovered")
                .expect("uncovered alias")
                .review_unit_ids,
            ["unit-1"]
        );

        let mut empty_lane = exact;
        empty_lane.review_units.clear();
        empty_lane.resume_state.current_unit_id = None;
        assert!(
            query(&empty_lane, "1.19.2")
                .expect("configured zero-cardinality lane")
                .review_unit_ids
                .is_empty()
        );
    }

    #[test]
    fn queries_cover_zero_one_and_many_revision_lanes() {
        let zero = zero_lane_document();
        assert!(zero.review_session.revision_lanes.is_empty());
        assert!(
            query(&zero, "HEAD")
                .expect("zero-lane changed domain")
                .review_unit_ids
                .is_empty()
        );

        let one = test_support::exact_document();
        assert_eq!(one.review_session.revision_lanes.len(), 1);
        assert_eq!(
            query(&one, "1.19.2 HEAD")
                .expect("one-lane query")
                .review_unit_ids,
            ["unit-1"]
        );

        let many = two_lane_document();
        assert_eq!(many.review_session.revision_lanes.len(), 2);
        assert_eq!(
            query(&many, "1.19.2").expect("first lane").review_unit_ids,
            ["unit-1"]
        );
        assert_eq!(
            query(&many, "1.20.1").expect("second lane").review_unit_ids,
            ["unit-2"]
        );
        assert_eq!(
            query(&many, "1.19.2 union 1.20.1")
                .expect("many-lane union")
                .review_unit_ids,
            ["unit-1", "unit-2"]
        );
    }

    #[test]
    fn query_precedence_parentheses_and_unknown_atoms_fail_closed() {
        let document = two_lane_document();

        let implicit_precedence = query(&document, "#approved union remaining intersect 1.20.1")
            .expect("intersection binds more tightly than union");
        assert_eq!(
            implicit_precedence.normalized_expression,
            "(#approved union (remaining intersect 1.20.1))"
        );
        assert_eq!(implicit_precedence.review_unit_ids, ["unit-1", "unit-2"]);

        let grouped = query(&document, "(#approved union remaining) intersect 1.20.1")
            .expect("parentheses override precedence");
        assert_eq!(
            grouped.normalized_expression,
            "((#approved union remaining) intersect 1.20.1)"
        );
        assert_eq!(grouped.review_unit_ids, ["unit-2"]);

        let difference = query(&document, "HEAD difference remaining intersect 1.20.1")
            .expect("intersection binds more tightly than difference");
        assert_eq!(
            difference.normalized_expression,
            "(HEAD difference (remaining intersect 1.20.1))"
        );
        assert_eq!(difference.review_unit_ids, ["unit-1"]);

        let unknown = query(&document, "known-nowhere")
            .expect_err("unknown atoms must fail instead of yielding an empty set");
        assert!(
            unknown
                .to_string()
                .contains("unknown release-review query atom 'known-nowhere'")
        );
        assert!(
            normalize_query("(#approved union remaining")
                .expect_err("unclosed groups must fail")
                .to_string()
                .contains("expected ')' to close query group")
        );
    }

    #[test]
    fn zero_change_lane_is_ready_with_exact_empty_completion_witnesses() {
        let mut document = test_support::exact_document();
        document.review_session.comments.clear();
        document.review_units.clear();
        document.selector_bindings.clear();
        document.migration_reports.clear();
        document.resume_state.current_unit_id = None;
        document.resume_state.deferred_unit_ids.clear();

        let report = completion(&document).expect("zero-change completion");
        assert_eq!(
            report.status,
            CompletionStatusV1::ReadyForMaintainerAttestation
        );
        assert_eq!(report.changed_domain, 0);
        assert_eq!(report.approved_raw, 0);
        assert_eq!(report.approved_effective, 0);
        assert_eq!(report.remaining, 0);
        assert_eq!(report.blocking, 0);
        assert_eq!(report.suspended, 0);
        assert_eq!(report.missing, 0);
        assert_eq!(report.deferred, 0);
        assert_eq!(report.unsupported, 0);
        assert_eq!(report.stale_producer, 0);
        assert_eq!(
            report.witnesses,
            CompletionWitnessesV1 {
                changed_domain: Vec::new(),
                approved_raw: Vec::new(),
                approved_effective: Vec::new(),
                remaining: Vec::new(),
                blocking: Vec::new(),
                suspended: Vec::new(),
                missing: Vec::new(),
                deferred: Vec::new(),
                unsupported: Vec::new(),
                stale_producer: Vec::new(),
            }
        );
        assert!(report.diagnostics.is_empty());
    }

    #[test]
    fn completion_reports_exact_sorted_witness_lists_for_every_kernel_category() {
        let report =
            completion(&completion_witness_matrix_document()).expect("completion witness matrix");
        assert_eq!(report.status, CompletionStatusV1::Stale);
        assert_eq!(report.changed_domain, 8);
        assert_eq!(report.approved_raw, 2);
        assert_eq!(report.approved_effective, 1);
        assert_eq!(report.remaining, 7);
        assert_eq!(report.blocking, 2);
        assert_eq!(report.suspended, 1);
        assert_eq!(report.missing, 1);
        assert_eq!(report.deferred, 1);
        assert_eq!(report.unsupported, 1);
        assert_eq!(report.stale_producer, 1);
        assert_eq!(
            report.witnesses,
            CompletionWitnessesV1 {
                changed_domain: vec![
                    "unit-approved".to_owned(),
                    "unit-blocking".to_owned(),
                    "unit-deferred".to_owned(),
                    "unit-missing".to_owned(),
                    "unit-remaining".to_owned(),
                    "unit-stale".to_owned(),
                    "unit-suspended".to_owned(),
                    "unit-unsupported".to_owned(),
                ],
                approved_raw: vec!["unit-approved".to_owned(), "unit-suspended".to_owned(),],
                approved_effective: vec!["unit-approved".to_owned()],
                remaining: vec![
                    "unit-blocking".to_owned(),
                    "unit-deferred".to_owned(),
                    "unit-missing".to_owned(),
                    "unit-remaining".to_owned(),
                    "unit-stale".to_owned(),
                    "unit-suspended".to_owned(),
                    "unit-unsupported".to_owned(),
                ],
                blocking: vec!["unit-blocking".to_owned(), "unit-suspended".to_owned(),],
                suspended: vec!["unit-suspended".to_owned()],
                missing: vec!["unit-missing".to_owned()],
                deferred: vec!["unit-deferred".to_owned()],
                unsupported: vec!["unit-unsupported".to_owned()],
                stale_producer: vec!["unit-stale".to_owned()],
            }
        );
    }

    #[test]
    fn candidate_change_before_resume_stales_attestation_without_moving_cursor() {
        let mut document = test_support::exact_document();
        let original_cursor = document.resume_state.clone();
        let baseline = semantic_state_hash(&document).expect("baseline semantic state");
        document
            .completion_attestations
            .push(CompletionAttestationV1 {
                id: "attestation-1".to_owned(),
                review_semantic_state_hash: baseline.clone(),
                maintainer: "maintainer".to_owned(),
                attested_at: "2026-08-23T00:00:00-04:00".to_owned(),
                statement: "I reviewed the pinned release domain.".to_owned(),
            });

        document.repository_bindings[0].candidate_commit = "a".repeat(40);
        document.repository_bindings[0].candidate_tree = "b".repeat(40);
        assert_eq!(document.resume_state, original_cursor);
        assert_ne!(
            semantic_state_hash(&document).expect("changed candidate semantic state"),
            baseline
        );
        let report = completion(&document).expect("candidate change completion");
        assert_eq!(report.status, CompletionStatusV1::Stale);
        assert!(report.diagnostics.iter().any(|diagnostic| {
            diagnostic == "Completion attestations do not match the current semantic state"
        }));
    }

    #[test]
    fn target_only_and_named_query_changes_invalidate_semantic_state() {
        let mut document = test_support::exact_document();
        document.selector_bindings.clear();
        document.migration_reports.clear();
        let baseline = semantic_state_hash(&document).expect("baseline semantic state");

        let mut target_changed = document.clone();
        let after = &target_changed.review_session.revision_lanes[0]
            .after
            .documents[0];
        target_changed.review_session.comments[0].target = CommentTargetV2::CommittedSelection {
            selection_rule: SelectionRuleV1::LiteralUtf8Range {
                document_revision_id: after.id.clone(),
                start_byte: 0,
                end_byte: 5,
                document_sha256: after.sha256.clone(),
                selected_text_sha256: review_session_v1::sha256(&after.text.as_bytes()[0..5]),
            },
            candidate_promotion: None,
        };
        assert_ne!(
            semantic_state_hash(&target_changed).expect("target-dependent hash"),
            baseline
        );

        let mut query_changed = document.clone();
        query_changed.named_queries[0].expression = "remaining intersect 1.19.2 HEAD".to_owned();
        assert_ne!(
            semantic_state_hash(&query_changed).expect("query-dependent hash"),
            baseline
        );

        let mut policy_changed = document;
        policy_changed
            .review_session
            .completion_policy
            .blocking_hashtags
            .push("#security".to_owned());
        assert_ne!(
            semantic_state_hash(&policy_changed).expect("policy-dependent hash"),
            baseline
        );
    }

    #[test]
    fn changed_named_query_revision_is_explicitly_stale_before_or_after_attestation() {
        let mut document = test_support::exact_document();
        document.resume_state.active_query_expression =
            Some(document.named_queries[0].expression.clone());
        let original_resume = document.resume_state.clone();
        let baseline = semantic_state_hash(&document).expect("baseline semantic state");
        document.named_queries[0].expression = "remaining intersect 1.19.2 HEAD".to_owned();
        assert_eq!(document.resume_state, original_resume);
        let unattested = completion(&document).expect("unattested changed query completion");
        assert_eq!(unattested.status, CompletionStatusV1::Stale);
        assert!(unattested.diagnostics.iter().any(|diagnostic| {
            diagnostic
                == "Active named-query revision differs from its persisted work-queue expression"
        }));

        document
            .completion_attestations
            .push(CompletionAttestationV1 {
                id: "attestation-1".to_owned(),
                review_semantic_state_hash: baseline,
                maintainer: "maintainer".to_owned(),
                attested_at: "2026-08-23T00:00:00-04:00".to_owned(),
                statement: "I reviewed the pinned release domain.".to_owned(),
            });
        let report = completion(&document).expect("changed query completion");
        assert_eq!(report.status, CompletionStatusV1::Stale);
        assert!(report.diagnostics.iter().any(|diagnostic| {
            diagnostic == "Completion attestations do not match the current semantic state"
        }));
    }

    #[test]
    fn semantic_hash_is_non_circular_and_covers_semantic_inputs() {
        let document = test_support::exact_document();
        let baseline = semantic_state_hash(&document).expect("baseline semantic hash");

        let mut navigated = document.clone();
        navigated.resume_state.generation += 1;
        navigated.resume_state.current_unit_id = None;
        assert_eq!(
            semantic_state_hash(&navigated).expect("resume-independent hash"),
            baseline
        );

        let mut attested = document.clone();
        attested
            .completion_attestations
            .push(CompletionAttestationV1 {
                id: "attestation-1".to_owned(),
                review_semantic_state_hash: baseline.clone(),
                maintainer: "maintainer".to_owned(),
                attested_at: "2026-08-23T00:00:00-04:00".to_owned(),
                statement: "I reviewed the pinned release domain.".to_owned(),
            });
        assert_eq!(
            semantic_state_hash(&attested).expect("attestation-independent hash"),
            baseline
        );

        let mut presentation_changed = document.clone();
        presentation_changed.review_session.style_rules.clear();
        assert_eq!(
            semantic_state_hash(&presentation_changed).expect("presentation-independent hash"),
            baseline
        );

        let mut comment_changed = document.clone();
        comment_changed.review_session.comments[0]
            .text
            .push_str(" again");
        assert_ne!(
            semantic_state_hash(&comment_changed).expect("comment-dependent hash"),
            baseline
        );

        let mut policy_changed = document.clone();
        policy_changed
            .review_session
            .completion_policy
            .blocking_hashtags
            .push("#security".to_owned());
        assert_ne!(
            semantic_state_hash(&policy_changed).expect("policy-dependent hash"),
            baseline
        );

        let mut producer_changed = document.clone();
        producer_changed.producer_generations[0].input_fingerprint = "9".repeat(64);
        assert_ne!(
            semantic_state_hash(&producer_changed).expect("producer-dependent hash"),
            baseline
        );

        let mut corpus_changed = document;
        corpus_changed.corpus_documents[0].source_locator = "git:other:A.java".to_owned();
        assert_ne!(
            semantic_state_hash(&corpus_changed).expect("corpus-dependent hash"),
            baseline
        );
    }

    #[test]
    fn completion_is_ready_complete_in_progress_or_stale_fail_closed() {
        let ready_document = test_support::exact_document();
        let ready = completion(&ready_document).expect("ready completion");
        assert_eq!(
            ready.status,
            CompletionStatusV1::ReadyForMaintainerAttestation
        );

        let mut complete_document = ready_document.clone();
        complete_document
            .completion_attestations
            .push(CompletionAttestationV1 {
                id: "attestation-1".to_owned(),
                review_semantic_state_hash: ready.review_semantic_state_hash,
                maintainer: "maintainer".to_owned(),
                attested_at: "2026-08-23T00:00:00-04:00".to_owned(),
                statement: "I reviewed the pinned release domain.".to_owned(),
            });
        assert_eq!(
            completion(&complete_document)
                .expect("complete status")
                .status,
            CompletionStatusV1::Complete
        );

        assert_eq!(
            completion(&test_support::in_progress_document())
                .expect("in-progress status")
                .status,
            CompletionStatusV1::InProgress
        );

        let stale = completion(&test_support::stale_document()).expect("stale status");
        assert_eq!(stale.status, CompletionStatusV1::Stale);
        assert_eq!(stale.stale_producer, 1);

        let mut invalid_attestation = complete_document;
        invalid_attestation.completion_attestations[0].attested_at = "tomorrow".to_owned();
        assert!(completion(&invalid_attestation).is_err());
    }

    #[test]
    fn completion_is_stale_when_attestation_no_longer_matches_semantic_state() {
        let mut document = test_support::exact_document();
        let attested_hash = semantic_state_hash(&document).expect("attested semantic state");
        document
            .completion_attestations
            .push(CompletionAttestationV1 {
                id: "attestation-1".to_owned(),
                review_semantic_state_hash: attested_hash.clone(),
                maintainer: "maintainer".to_owned(),
                attested_at: "2026-08-23T00:00:00-04:00".to_owned(),
                statement: "I reviewed the pinned release domain.".to_owned(),
            });
        assert_eq!(
            completion(&document).expect("matching attestation").status,
            CompletionStatusV1::Complete
        );

        document.review_session.comments[0]
            .text
            .push_str(" with a semantic amendment");
        assert_ne!(
            semantic_state_hash(&document).expect("amended semantic state"),
            attested_hash,
            "attestations must remain outside the hash while semantic inputs remain inside it"
        );

        let stale = completion(&document).expect("stale attestation report");
        assert_eq!(stale.status, CompletionStatusV1::Stale);
        assert_eq!(stale.stale_producer, 0);
        assert!(stale.diagnostics.iter().any(|diagnostic| {
            diagnostic == "Completion attestations do not match the current semantic state"
        }));
    }
}
