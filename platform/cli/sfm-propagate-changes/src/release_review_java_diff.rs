//! Conservative Java structured review surfaces backed by pinned Arborium.
//!
//! Structural matching is deliberately narrower than a refactoring engine.
//! Unique semantic keys are matched first, followed by unique name-insensitive
//! fingerprints and unique loose declaration keys. Any parse recovery or
//! competing fingerprint candidates yields an explicit complete text fallback.

use crate::java_analysis::DiagnosticSeverity;
use crate::java_analysis::syntax::JAVA_PARSER_FINGERPRINT;
use crate::java_analysis::syntax::JavaSyntaxFile;
use crate::release_review_surface_v1::REVIEW_SURFACE_SCHEMA;
use crate::release_review_surface_v1::ReviewCorrespondenceConfidenceV1;
use crate::release_review_surface_v1::ReviewCorrespondenceKindV1;
use crate::release_review_surface_v1::ReviewCorrespondenceReportV1;
use crate::release_review_surface_v1::ReviewCorrespondenceV1;
use crate::release_review_surface_v1::ReviewSurfaceDiagnosticSeverityV1;
use crate::release_review_surface_v1::ReviewSurfaceDiagnosticV1;
use crate::release_review_surface_v1::ReviewSurfaceKindV1;
use crate::release_review_surface_v1::ReviewSurfaceLimitsV1;
use crate::release_review_surface_v1::ReviewSurfaceMappingKindV1;
use crate::release_review_surface_v1::ReviewSurfaceMappingV1;
use crate::release_review_surface_v1::ReviewSurfaceOutcomeV1;
use crate::release_review_surface_v1::ReviewSurfaceRegionKindV1;
use crate::release_review_surface_v1::ReviewSurfaceRegionV1;
use crate::release_review_surface_v1::ReviewSurfaceRequestV1;
use crate::release_review_surface_v1::ReviewSurfaceSourceRangeV1;
use crate::release_review_surface_v1::ReviewSurfaceV1;
use crate::release_review_surface_v1::review_surface_sha256;
use crate::release_review_surface_v1::source_range;
use crate::release_review_surface_v1::stable_review_surface_id;
use crate::release_review_text_diff::TEXT_DIFF_ALGORITHM_V1;
use crate::release_review_text_diff::produce_text_diff;
use crate::release_review_v1::SnapshotSideV1;
use crate::release_review_v1::Utf8RangeV1;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use tree_sitter_patched_arborium::Node;

pub const JAVA_STRUCTURED_DIFF_ALGORITHM_V1: &str =
    "arborium-java/2.18.1+sfm-declaration-correspondence/2";

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum DeclarationKind {
    Import,
    Type,
    Method,
    Constructor,
    Field,
    EnumConstant,
    AnnotationElement,
}

impl DeclarationKind {
    const fn label(self) -> &'static str {
        match self {
            Self::Import => "import",
            Self::Type => "type",
            Self::Method => "method",
            Self::Constructor => "constructor",
            Self::Field => "field",
            Self::EnumConstant => "enum constant",
            Self::AnnotationElement => "annotation element",
        }
    }

    const fn region_kind(self) -> ReviewSurfaceRegionKindV1 {
        match self {
            Self::Import => ReviewSurfaceRegionKindV1::JavaImport,
            _ => ReviewSurfaceRegionKindV1::JavaDeclaration,
        }
    }
}

#[derive(Clone, Debug)]
struct JavaDeclaration {
    kind: DeclarationKind,
    name: String,
    owner: String,
    semantic_key: String,
    loose_key: String,
    exact_hash: String,
    normalized_hash: String,
    name_insensitive_hash: String,
    range: Utf8RangeV1,
    ordinal: usize,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum MatchBasis {
    SemanticKey,
    NameInsensitiveFingerprint,
    LooseKey,
}

#[derive(Clone, Debug)]
struct DeclarationPair {
    before: Option<usize>,
    after: Option<usize>,
    basis: Option<MatchBasis>,
}

#[derive(Debug)]
struct StructuredBuildLimit {
    message: String,
}

struct StructuredSurfaceBuilder<'a> {
    request: &'a ReviewSurfaceRequestV1,
    text: String,
    mappings: Vec<ReviewSurfaceMappingV1>,
    regions: Vec<ReviewSurfaceRegionV1>,
}

impl<'a> StructuredSurfaceBuilder<'a> {
    fn new(request: &'a ReviewSurfaceRequestV1) -> Self {
        Self {
            request,
            text: String::new(),
            mappings: Vec::new(),
            regions: Vec::new(),
        }
    }

    fn push_unmapped(&mut self, value: &str) -> Result<Utf8RangeV1, StructuredBuildLimit> {
        if self.text.len().saturating_add(value.len()) > self.request.maximum_output_bytes {
            return Err(StructuredBuildLimit {
                message: format!(
                    "Java structured diff exceeds requested {}-byte output bound",
                    self.request.maximum_output_bytes
                ),
            });
        }
        let start_byte = self.text.len();
        self.text.push_str(value);
        Ok(Utf8RangeV1 {
            start_byte,
            end_byte: self.text.len(),
        })
    }

    fn push_source(
        &mut self,
        prefix: &str,
        side: SnapshotSideV1,
        declaration: &JavaDeclaration,
        mapping_kind: ReviewSurfaceMappingKindV1,
        additional_source: Option<ReviewSurfaceSourceRangeV1>,
    ) -> Result<(Utf8RangeV1, Vec<ReviewSurfaceSourceRangeV1>), StructuredBuildLimit> {
        self.push_unmapped(prefix)?;
        let source = self
            .request
            .file_pair
            .source(side)
            .expect("declaration side is present");
        let exact_text = &source.text[declaration.range.start_byte..declaration.range.end_byte];
        let surface_range = self.push_unmapped(exact_text)?;
        let mut source_ranges = vec![source_range(
            &self.request.file_pair,
            side,
            declaration.range.clone(),
        )];
        if let Some(additional_source) = additional_source {
            source_ranges.push(additional_source);
        }
        source_ranges.sort();
        source_ranges.dedup();
        if !exact_text.is_empty() {
            if self.mappings.len() >= self.request.maximum_mappings {
                return Err(StructuredBuildLimit {
                    message: format!(
                        "Java structured diff requires more than {} exact source mappings",
                        self.request.maximum_mappings
                    ),
                });
            }
            self.mappings.push(ReviewSurfaceMappingV1 {
                surface_range: surface_range.clone(),
                kind: mapping_kind,
                source_ranges: source_ranges.clone(),
            });
        }
        if !exact_text.ends_with('\n') {
            self.push_unmapped("\n")?;
        }
        Ok((surface_range, source_ranges))
    }

    // Partition the last copied declaration without changing its displayed bytes.
    // Neutral spans still carry exact source mappings for syntax and comments.
    fn refine_last_source(&mut self, changed: &[Utf8RangeV1]) -> Result<(), StructuredBuildLimit> {
        let original = self.mappings.pop().expect("nonempty declaration mapping");
        let length = original.surface_range.end_byte - original.surface_range.start_byte;
        let mut cursor = 0;
        for range in changed.iter().chain(std::iter::once(&Utf8RangeV1 {
            start_byte: length,
            end_byte: length,
        })) {
            for (start, end, kind) in [
                (
                    cursor,
                    range.start_byte,
                    ReviewSurfaceMappingKindV1::StructuralCorrespondence,
                ),
                (range.start_byte, range.end_byte, original.kind),
            ] {
                if start == end {
                    continue;
                }
                if self.mappings.len() >= self.request.maximum_mappings {
                    return Err(StructuredBuildLimit {
                        message: "Java changed-span refinement exceeds exact mapping bound"
                            .to_owned(),
                    });
                }
                let mut mapping = original.clone();
                mapping.kind = kind;
                mapping.surface_range.start_byte += start;
                mapping.surface_range.end_byte = original.surface_range.start_byte + end;
                for source in &mut mapping.source_ranges {
                    source.range.end_byte = source.range.start_byte + end;
                    source.range.start_byte += start;
                }
                self.mappings.push(mapping);
            }
            cursor = range.end_byte;
        }
        Ok(())
    }

    fn push_region(&mut self, region: ReviewSurfaceRegionV1) -> Result<(), StructuredBuildLimit> {
        if self.regions.len() >= self.request.maximum_regions {
            return Err(StructuredBuildLimit {
                message: format!(
                    "Java structured diff requires more than {} regions",
                    self.request.maximum_regions
                ),
            });
        }
        self.regions.push(region);
        Ok(())
    }
}

/// Produce one Java structure-aware surface or an explicit complete text fallback.
///
/// # Errors
///
/// Returns an error only when the immutable request is malformed. Parse gaps,
/// unsupported languages, ambiguity, and structured output bounds are typed
/// diagnostics with conservative fallback.
pub fn produce_java_structured_diff(
    request: &ReviewSurfaceRequestV1,
    limits: ReviewSurfaceLimitsV1,
) -> eyre::Result<ReviewSurfaceV1> {
    request.validate(limits)?;
    if request.surface_kind != ReviewSurfaceKindV1::JavaStructuredDiff {
        eyre::bail!("Java structured-diff producer received a non-Java surface request");
    }
    if !supports_java(request) {
        return text_fallback(
            request,
            limits,
            ReviewSurfaceDiagnosticV1 {
                code: "review.java-diff.unsupported-language".to_owned(),
                severity: ReviewSurfaceDiagnosticSeverityV1::Warning,
                message: "Structured review currently supports only Java UTF-8 sources; emitted complete text diff fallback"
                    .to_owned(),
                side: None,
                source_range: None,
            },
        );
    }

    let before = parse_side(request, SnapshotSideV1::Before)?;
    let after = parse_side(request, SnapshotSideV1::After)?;
    let mut parse_diagnostics = Vec::new();
    if let Some(parsed) = &before {
        parse_diagnostics.extend(convert_parse_diagnostics(parsed, SnapshotSideV1::Before));
    }
    if let Some(parsed) = &after {
        parse_diagnostics.extend(convert_parse_diagnostics(parsed, SnapshotSideV1::After));
    }
    if let Some(diagnostic) = parse_diagnostics.first().cloned() {
        return text_fallback(request, limits, diagnostic);
    }

    let before_declarations = before.as_ref().map_or_else(Vec::new, extract_declarations);
    let after_declarations = after.as_ref().map_or_else(Vec::new, extract_declarations);
    let (pairs, ambiguity) = match_declarations(&before_declarations, &after_declarations);
    if let Some(message) = ambiguity {
        return text_fallback(
            request,
            limits,
            ReviewSurfaceDiagnosticV1 {
                code: "review.java-diff.ambiguous-correspondence".to_owned(),
                severity: ReviewSurfaceDiagnosticSeverityV1::Warning,
                message,
                side: None,
                source_range: None,
            },
        );
    }

    match build_structured_surface(
        request,
        &before_declarations,
        &after_declarations,
        &pairs,
    ) {
        Ok(Some(surface)) => Ok(surface),
        Ok(None) => text_fallback(
            request,
            limits,
            ReviewSurfaceDiagnosticV1 {
                code: "review.java-diff.no-structural-change".to_owned(),
                severity: ReviewSurfaceDiagnosticSeverityV1::Info,
                message: "No changed Java declaration covered the textual change; emitted complete text diff fallback"
                    .to_owned(),
                side: None,
                source_range: None,
            },
        ),
        Err(error) => text_fallback(
            request,
            limits,
            ReviewSurfaceDiagnosticV1 {
                code: "review.java-diff.structured-limit".to_owned(),
                severity: ReviewSurfaceDiagnosticSeverityV1::Warning,
                message: format!("{}; emitted complete text diff fallback", error.message),
                side: None,
                source_range: None,
            },
        ),
    }
}

struct ParsedJavaSide {
    syntax: JavaSyntaxFile,
}

fn parse_side(
    request: &ReviewSurfaceRequestV1,
    side: SnapshotSideV1,
) -> eyre::Result<Option<ParsedJavaSide>> {
    let Some(source) = request.file_pair.source(side) else {
        return Ok(None);
    };
    let syntax = JavaSyntaxFile::parse_text_with_diagnostic_limit(
        &source.path,
        "release-review",
        source.text.clone(),
        Some(request.maximum_diagnostics),
    )?;
    Ok(Some(ParsedJavaSide { syntax }))
}

fn convert_parse_diagnostics(
    parsed: &ParsedJavaSide,
    side: SnapshotSideV1,
) -> Vec<ReviewSurfaceDiagnosticV1> {
    parsed
        .syntax
        .diagnostics
        .iter()
        .map(|diagnostic| ReviewSurfaceDiagnosticV1 {
            code: format!("review.java-diff.{}", diagnostic.code),
            severity: match diagnostic.severity {
                DiagnosticSeverity::Info => ReviewSurfaceDiagnosticSeverityV1::Info,
                DiagnosticSeverity::Warning => ReviewSurfaceDiagnosticSeverityV1::Warning,
                DiagnosticSeverity::Error => ReviewSurfaceDiagnosticSeverityV1::Error,
            },
            message: format!(
                "{}; emitted complete text diff fallback",
                diagnostic.message
            ),
            side: diagnostic.span.as_ref().map(|_| side),
            source_range: diagnostic.span.as_ref().map(|span| Utf8RangeV1 {
                start_byte: usize::try_from(span.start_byte).unwrap_or(usize::MAX),
                end_byte: usize::try_from(span.end_byte).unwrap_or(usize::MAX),
            }),
        })
        .collect()
}

fn supports_java(request: &ReviewSurfaceRequestV1) -> bool {
    request
        .file_pair
        .before
        .iter()
        .chain(&request.file_pair.after)
        .all(|source| {
            source.language.eq_ignore_ascii_case("java")
                && source
                    .path
                    .rsplit_once('.')
                    .is_some_and(|(_, extension)| extension.eq_ignore_ascii_case("java"))
        })
}

fn extract_declarations(parsed: &ParsedJavaSide) -> Vec<JavaDeclaration> {
    let mut declarations = Vec::new();
    visit_declarations(
        parsed.syntax.tree.root_node(),
        &parsed.syntax.source,
        "<file>",
        &mut declarations,
    );
    declarations.sort_by(|left, right| {
        left.range
            .start_byte
            .cmp(&right.range.start_byte)
            .then_with(|| left.range.end_byte.cmp(&right.range.end_byte))
            .then_with(|| left.semantic_key.cmp(&right.semantic_key))
    });
    for (ordinal, declaration) in declarations.iter_mut().enumerate() {
        declaration.ordinal = ordinal;
    }
    declarations
}

fn visit_declarations(
    node: Node<'_>,
    source: &str,
    owner: &str,
    declarations: &mut Vec<JavaDeclaration>,
) {
    let declaration_kind = declaration_kind(node.kind());
    let mut child_owner = owner.to_owned();
    if let Some(kind) = declaration_kind {
        let name = declaration_name(node, source, kind);
        let semantic_key = semantic_key(node, source, owner, kind, &name);
        let range = declaration_range(node, kind);
        let name_ranges = declaration_name_ranges(node, kind);
        let declaration = JavaDeclaration {
            kind,
            name: name.clone(),
            owner: owner.to_owned(),
            loose_key: format!("{}:{name}", kind.label()),
            exact_hash: hash_bytes(&source.as_bytes()[range.start_byte..range.end_byte]),
            normalized_hash: normalized_node_hash(node, source, &range, &[]),
            name_insensitive_hash: normalized_node_hash(node, source, &range, &name_ranges),
            semantic_key: semantic_key.clone(),
            range,
            ordinal: declarations.len(),
        };
        if kind == DeclarationKind::Type {
            child_owner = semantic_key;
        }
        declarations.push(declaration);
    }

    let mut cursor = node.walk();
    for child in node.named_children(&mut cursor) {
        if child.kind() != "line_comment" && child.kind() != "block_comment" {
            visit_declarations(child, source, &child_owner, declarations);
        }
    }
}

fn declaration_range(node: Node<'_>, kind: DeclarationKind) -> Utf8RangeV1 {
    let end_byte = if kind == DeclarationKind::Type {
        node.child_by_field_name("body")
            .map_or(node.end_byte(), |body| body.start_byte())
    } else {
        node.end_byte()
    };
    Utf8RangeV1 {
        start_byte: node.start_byte(),
        end_byte,
    }
}

fn declaration_name_ranges(node: Node<'_>, kind: DeclarationKind) -> Vec<std::ops::Range<usize>> {
    if kind == DeclarationKind::Import {
        return Vec::new();
    }
    if let Some(name) = node.child_by_field_name("name") {
        return vec![name.byte_range()];
    }
    let mut ranges = Vec::new();
    collect_field_name_ranges(node, &mut ranges);
    ranges.sort_by_key(|range| (range.start, range.end));
    ranges.dedup();
    ranges
}

fn collect_field_name_ranges(node: Node<'_>, ranges: &mut Vec<std::ops::Range<usize>>) {
    if node.kind() == "variable_declarator"
        && let Some(name) = node.child_by_field_name("name")
    {
        ranges.push(name.byte_range());
        return;
    }
    let mut cursor = node.walk();
    for child in node.named_children(&mut cursor) {
        collect_field_name_ranges(child, ranges);
    }
}

fn declaration_kind(kind: &str) -> Option<DeclarationKind> {
    match kind {
        "import_declaration" => Some(DeclarationKind::Import),
        "class_declaration"
        | "interface_declaration"
        | "enum_declaration"
        | "record_declaration"
        | "annotation_type_declaration" => Some(DeclarationKind::Type),
        "method_declaration" => Some(DeclarationKind::Method),
        "constructor_declaration" | "compact_constructor_declaration" => {
            Some(DeclarationKind::Constructor)
        }
        "field_declaration" | "constant_declaration" => Some(DeclarationKind::Field),
        "enum_constant" => Some(DeclarationKind::EnumConstant),
        "annotation_type_element_declaration" => Some(DeclarationKind::AnnotationElement),
        _ => None,
    }
}

fn declaration_name(node: Node<'_>, source: &str, kind: DeclarationKind) -> String {
    if kind == DeclarationKind::Import {
        return source[node.byte_range()]
            .trim()
            .trim_start_matches("import")
            .trim_end_matches(';')
            .trim()
            .to_owned();
    }
    if let Some(name) = node.child_by_field_name("name") {
        return source[name.byte_range()].to_owned();
    }
    let mut names = Vec::new();
    collect_field_names(node, source, &mut names);
    if names.is_empty() {
        format!("<anonymous@{}>", node.start_byte())
    } else {
        names.join(",")
    }
}

fn collect_field_names(node: Node<'_>, source: &str, names: &mut Vec<String>) {
    if node.kind() == "variable_declarator"
        && let Some(name) = node.child_by_field_name("name")
    {
        names.push(source[name.byte_range()].to_owned());
        return;
    }
    let mut cursor = node.walk();
    for child in node.named_children(&mut cursor) {
        collect_field_names(child, source, names);
    }
}

fn semantic_key(
    node: Node<'_>,
    source: &str,
    owner: &str,
    kind: DeclarationKind,
    name: &str,
) -> String {
    let discriminator = if matches!(kind, DeclarationKind::Method | DeclarationKind::Constructor) {
        node.child_by_field_name("parameters")
            .map_or_else(String::new, |parameters| {
                normalize_text(&source[parameters.byte_range()])
            })
    } else {
        String::new()
    };
    format!("{owner}/{}:{name}{discriminator}", kind.label())
}

fn normalized_node_hash(
    node: Node<'_>,
    source: &str,
    retained_range: &Utf8RangeV1,
    replaced_ranges: &[std::ops::Range<usize>],
) -> String {
    let mut normalized = String::new();
    append_normalized_node(
        node,
        source,
        retained_range,
        replaced_ranges,
        &mut normalized,
    );
    hash_bytes(normalized.as_bytes())
}

fn append_normalized_node(
    node: Node<'_>,
    source: &str,
    retained_range: &Utf8RangeV1,
    replaced_ranges: &[std::ops::Range<usize>],
    output: &mut String,
) {
    if node.start_byte() >= retained_range.end_byte || node.end_byte() <= retained_range.start_byte
    {
        return;
    }
    if matches!(node.kind(), "line_comment" | "block_comment") {
        return;
    }
    if node.child_count() == 0 {
        output.push_str(node.kind());
        output.push(':');
        if replaced_ranges
            .iter()
            .any(|range| range == &node.byte_range())
        {
            output.push_str("<declaration-name>");
        } else {
            output.push_str(source[node.byte_range()].trim());
        }
        output.push(';');
        return;
    }
    output.push('(');
    output.push_str(node.kind());
    for index in 0..node.child_count() {
        if let Some(child) = node.child(index) {
            append_normalized_node(child, source, retained_range, replaced_ranges, output);
        }
    }
    output.push(')');
}

fn normalize_text(text: &str) -> String {
    text.chars()
        .filter(|character| !character.is_whitespace())
        .collect()
}

fn hash_bytes(bytes: &[u8]) -> String {
    format!("sha256:{:x}", Sha256::digest(bytes))
}

fn match_declarations(
    before: &[JavaDeclaration],
    after: &[JavaDeclaration],
) -> (Vec<DeclarationPair>, Option<String>) {
    let mut matched_before = BTreeSet::new();
    let mut matched_after = BTreeSet::new();
    let mut pairs = Vec::new();

    match_unique_by(
        before,
        after,
        &mut matched_before,
        &mut matched_after,
        &mut pairs,
        MatchBasis::SemanticKey,
        |declaration| declaration.semantic_key.clone(),
    );

    if let Some(message) = ambiguous_fingerprint(
        before,
        after,
        &matched_before,
        &matched_after,
        |declaration| declaration.name_insensitive_hash.clone(),
    ) {
        return (Vec::new(), Some(message));
    }
    match_unique_by(
        before,
        after,
        &mut matched_before,
        &mut matched_after,
        &mut pairs,
        MatchBasis::NameInsensitiveFingerprint,
        |declaration| declaration.name_insensitive_hash.clone(),
    );
    match_unique_by(
        before,
        after,
        &mut matched_before,
        &mut matched_after,
        &mut pairs,
        MatchBasis::LooseKey,
        |declaration| declaration.loose_key.clone(),
    );

    pairs.extend(
        (0..before.len())
            .filter(|index| !matched_before.contains(index))
            .map(|index| DeclarationPair {
                before: Some(index),
                after: None,
                basis: None,
            }),
    );
    pairs.extend(
        (0..after.len())
            .filter(|index| !matched_after.contains(index))
            .map(|index| DeclarationPair {
                before: None,
                after: Some(index),
                basis: None,
            }),
    );
    pairs.sort_by_key(|pair| {
        (
            pair.before
                .map_or(usize::MAX, |index| before[index].ordinal),
            pair.after.map_or(usize::MAX, |index| after[index].ordinal),
        )
    });
    (pairs, None)
}

fn match_unique_by<F>(
    before: &[JavaDeclaration],
    after: &[JavaDeclaration],
    matched_before: &mut BTreeSet<usize>,
    matched_after: &mut BTreeSet<usize>,
    pairs: &mut Vec<DeclarationPair>,
    basis: MatchBasis,
    key: F,
) where
    F: Fn(&JavaDeclaration) -> String,
{
    let before_index = index_unmatched(before, matched_before, &key);
    let after_index = index_unmatched(after, matched_after, &key);
    for (value, before_candidates) in before_index {
        let Some(after_candidates) = after_index.get(&value) else {
            continue;
        };
        if before_candidates.len() == 1 && after_candidates.len() == 1 {
            let before_index = before_candidates[0];
            let after_index = after_candidates[0];
            matched_before.insert(before_index);
            matched_after.insert(after_index);
            pairs.push(DeclarationPair {
                before: Some(before_index),
                after: Some(after_index),
                basis: Some(basis),
            });
        }
    }
}

fn index_unmatched<F>(
    declarations: &[JavaDeclaration],
    matched: &BTreeSet<usize>,
    key: &F,
) -> BTreeMap<String, Vec<usize>>
where
    F: Fn(&JavaDeclaration) -> String,
{
    let mut output = BTreeMap::<String, Vec<usize>>::new();
    for (index, declaration) in declarations.iter().enumerate() {
        if !matched.contains(&index) {
            output.entry(key(declaration)).or_default().push(index);
        }
    }
    output
}

fn ambiguous_fingerprint<F>(
    before: &[JavaDeclaration],
    after: &[JavaDeclaration],
    matched_before: &BTreeSet<usize>,
    matched_after: &BTreeSet<usize>,
    key: F,
) -> Option<String>
where
    F: Fn(&JavaDeclaration) -> String,
{
    let before_index = index_unmatched(before, matched_before, &key);
    let after_index = index_unmatched(after, matched_after, &key);
    before_index.iter().find_map(|(fingerprint, before_candidates)| {
        let after_candidates = after_index.get(fingerprint)?;
        (before_candidates.len() > 1 || after_candidates.len() > 1).then(|| {
            format!(
                "Name-insensitive declaration fingerprint `{fingerprint}` has {} before and {} after candidates; emitted complete text diff fallback",
                before_candidates.len(),
                after_candidates.len()
            )
        })
    })
}

#[expect(
    clippy::too_many_lines,
    reason = "one pass emits each declaration's presentation, exact mappings, region, and correspondence evidence"
)]
fn build_structured_surface(
    request: &ReviewSurfaceRequestV1,
    before: &[JavaDeclaration],
    after: &[JavaDeclaration],
    pairs: &[DeclarationPair],
) -> Result<Option<ReviewSurfaceV1>, StructuredBuildLimit> {
    let mut classified = pairs
        .iter()
        .map(|pair| (pair, classify_pair(pair, before, after, pairs)))
        .filter(|(_, kind)| *kind != ReviewCorrespondenceKindV1::Unchanged)
        .collect::<Vec<_>>();
    if classified.is_empty() {
        return Ok(None);
    }
    classified.sort_by(|(left, _), (right, _)| {
        pair_sort_key(left, before, after).cmp(&pair_sort_key(right, before, after))
    });

    let before_path = request
        .file_pair
        .before
        .as_ref()
        .map_or("/dev/null", |source| source.path.as_str());
    let after_path = request
        .file_pair
        .after
        .as_ref()
        .map_or("/dev/null", |source| source.path.as_str());
    let mut builder = StructuredSurfaceBuilder::new(request);
    builder.push_unmapped(&format!(
        "=== Java structured diff ===\n--- {before_path}\n+++ {after_path}\n"
    ))?;
    let mut report = ReviewCorrespondenceReportV1::empty(request.file_pair.id.clone(), true);

    for (ordinal, (pair, kind)) in classified.into_iter().enumerate() {
        let before_declaration = pair.before.map(|index| &before[index]);
        let after_declaration = pair.after.map(|index| &after[index]);
        let label = correspondence_label(kind, before_declaration, after_declaration);
        let region_start = builder.text.len();
        builder.push_unmapped(&format!("@@ {}: {label} @@\n", correspondence_name(kind)))?;
        let mut region_sources = Vec::new();

        let same_exact_bytes = before_declaration
            .zip(after_declaration)
            .is_some_and(|(before, after)| before.exact_hash == after.exact_hash);
        if same_exact_bytes {
            let before_declaration = before_declaration.expect("paired before");
            let after_declaration = after_declaration.expect("paired after");
            let after_source = source_range(
                &request.file_pair,
                SnapshotSideV1::After,
                after_declaration.range.clone(),
            );
            let (_, sources) = builder.push_source(
                " ",
                SnapshotSideV1::Before,
                before_declaration,
                ReviewSurfaceMappingKindV1::StructuralCorrespondence,
                Some(after_source),
            )?;
            region_sources.extend(sources);
        } else {
            let refinement = before_declaration
                .zip(after_declaration)
                .map(|(before, after)| {
                    changed_spans(
                        &request
                            .file_pair
                            .before
                            .as_ref()
                            .expect("before source")
                            .text[before.range.start_byte..before.range.end_byte],
                        &request.file_pair.after.as_ref().expect("after source").text
                            [after.range.start_byte..after.range.end_byte],
                    )
                })
                .transpose()?;
            if let Some(before_declaration) = before_declaration {
                let (_, sources) = builder.push_source(
                    "-",
                    SnapshotSideV1::Before,
                    before_declaration,
                    ReviewSurfaceMappingKindV1::StructuralBefore,
                    None,
                )?;
                if let Some((before_changes, _)) = &refinement {
                    builder.refine_last_source(before_changes)?;
                }
                region_sources.extend(sources);
            }
            if let Some(after_declaration) = after_declaration {
                let (_, sources) = builder.push_source(
                    "+",
                    SnapshotSideV1::After,
                    after_declaration,
                    ReviewSurfaceMappingKindV1::StructuralAfter,
                    None,
                )?;
                if let Some((_, after_changes)) = &refinement {
                    builder.refine_last_source(after_changes)?;
                }
                region_sources.extend(sources);
            }
        }
        region_sources.sort();
        region_sources.dedup();
        let region_end = builder.text.len();
        let region_kind = after_declaration
            .or(before_declaration)
            .map_or(ReviewSurfaceRegionKindV1::JavaDeclaration, |declaration| {
                declaration.kind.region_kind()
            });
        builder.push_region(ReviewSurfaceRegionV1 {
            id: stable_review_surface_id(
                "java-region",
                &[
                    &request.file_pair.id,
                    &ordinal.to_string(),
                    &label,
                    correspondence_name(kind),
                ],
            ),
            kind: region_kind,
            label: label.clone(),
            surface_range: Utf8RangeV1 {
                start_byte: region_start,
                end_byte: region_end,
            },
            source_ranges: region_sources,
        })?;

        let before_ranges = before_declaration.map_or_else(Vec::new, |declaration| {
            vec![source_range(
                &request.file_pair,
                SnapshotSideV1::Before,
                declaration.range.clone(),
            )]
        });
        let after_ranges = after_declaration.map_or_else(Vec::new, |declaration| {
            vec![source_range(
                &request.file_pair,
                SnapshotSideV1::After,
                declaration.range.clone(),
            )]
        });
        report.correspondences.push(ReviewCorrespondenceV1 {
            id: stable_review_surface_id(
                "java-correspondence",
                &[
                    &request.file_pair.id,
                    &ordinal.to_string(),
                    &label,
                    correspondence_name(kind),
                ],
            ),
            kind,
            confidence: match pair.basis {
                Some(MatchBasis::SemanticKey) => ReviewCorrespondenceConfidenceV1::Exact,
                Some(MatchBasis::NameInsensitiveFingerprint) => {
                    ReviewCorrespondenceConfidenceV1::Structural
                }
                Some(MatchBasis::LooseKey) => ReviewCorrespondenceConfidenceV1::Conservative,
                None if pair.before.is_none() || pair.after.is_none() => {
                    ReviewCorrespondenceConfidenceV1::Exact
                }
                None => ReviewCorrespondenceConfidenceV1::Unavailable,
            },
            semantic_key_before: before_declaration
                .map(|declaration| declaration.semantic_key.clone()),
            semantic_key_after: after_declaration
                .map(|declaration| declaration.semantic_key.clone()),
            before_ranges,
            after_ranges,
            evidence: vec![
                JAVA_STRUCTURED_DIFF_ALGORITHM_V1.to_owned(),
                JAVA_PARSER_FINGERPRINT.to_owned(),
                format!("match-basis={:?}", pair.basis),
            ],
        });
    }

    let text_sha256 = review_surface_sha256(&builder.text);
    Ok(Some(ReviewSurfaceV1 {
        schema: REVIEW_SURFACE_SCHEMA.to_owned(),
        request_id: request.request_id,
        request_generation: request.request_generation,
        file_pair_id: request.file_pair.id.clone(),
        surface_kind: request.surface_kind,
        algorithm: JAVA_STRUCTURED_DIFF_ALGORITHM_V1.to_owned(),
        outcome: ReviewSurfaceOutcomeV1::Produced,
        complete: true,
        fallback_kind: None,
        text: builder.text,
        text_sha256,
        mappings: builder.mappings,
        regions: builder.regions,
        correspondence: report,
        diagnostics: Vec::new(),
    }))
}

fn changed_spans(
    before: &str,
    after: &str,
) -> Result<(Vec<Utf8RangeV1>, Vec<Utf8RangeV1>), StructuredBuildLimit> {
    // Bound worst-case character comparison work independently of output size.
    // Large declarations use the existing explicit text fallback, never a
    // misleading claim that an entire matched declaration changed structurally.
    if before.len().saturating_mul(after.len()) > 16_777_216 {
        return Err(StructuredBuildLimit {
            message: "Java changed-span refinement exceeds bounded comparison work".to_owned(),
        });
    }
    let offsets = |text: &str| {
        text.char_indices()
            .map(|(offset, _)| offset)
            .chain(std::iter::once(text.len()))
            .collect::<Vec<_>>()
    };
    let before_offsets = offsets(before);
    let after_offsets = offsets(after);
    let mut input = gix::diff::blob::InternedInput::<&str>::default();
    input.update_before(
        before_offsets
            .windows(2)
            .map(|pair| &before[pair[0]..pair[1]]),
    );
    input.update_after(
        after_offsets
            .windows(2)
            .map(|pair| &after[pair[0]..pair[1]]),
    );
    let diff =
        gix::diff::blob::diff_with_slider_heuristics(gix::diff::blob::Algorithm::Myers, &input);
    let mut before_changes = Vec::new();
    let mut after_changes = Vec::new();
    for hunk in diff.hunks() {
        if !hunk.before.is_empty() {
            before_changes.push(Utf8RangeV1 {
                start_byte: before_offsets[hunk.before.start as usize],
                end_byte: before_offsets[hunk.before.end as usize],
            });
        }
        if !hunk.after.is_empty() {
            after_changes.push(Utf8RangeV1 {
                start_byte: after_offsets[hunk.after.start as usize],
                end_byte: after_offsets[hunk.after.end as usize],
            });
        }
    }
    Ok((before_changes, after_changes))
}

fn classify_pair(
    pair: &DeclarationPair,
    before: &[JavaDeclaration],
    after: &[JavaDeclaration],
    all_pairs: &[DeclarationPair],
) -> ReviewCorrespondenceKindV1 {
    let (Some(before_index), Some(after_index)) = (pair.before, pair.after) else {
        return if pair.before.is_some() {
            ReviewCorrespondenceKindV1::Deleted
        } else {
            ReviewCorrespondenceKindV1::Added
        };
    };
    let before_declaration = &before[before_index];
    let after_declaration = &after[after_index];
    let moved = before_declaration.owner != after_declaration.owner
        || declaration_order_changed(pair, before, after, all_pairs);
    let renamed = before_declaration.name != after_declaration.name;
    if moved && renamed {
        return ReviewCorrespondenceKindV1::MovedAndRenamed;
    }
    if moved {
        return ReviewCorrespondenceKindV1::Moved;
    }
    if renamed {
        return ReviewCorrespondenceKindV1::Renamed;
    }
    if before_declaration.exact_hash == after_declaration.exact_hash {
        ReviewCorrespondenceKindV1::Unchanged
    } else if before_declaration.normalized_hash == after_declaration.normalized_hash {
        ReviewCorrespondenceKindV1::FormattingOnly
    } else {
        ReviewCorrespondenceKindV1::Edited
    }
}

fn declaration_order_changed(
    pair: &DeclarationPair,
    before: &[JavaDeclaration],
    after: &[JavaDeclaration],
    all_pairs: &[DeclarationPair],
) -> bool {
    let (Some(before_index), Some(after_index)) = (pair.before, pair.after) else {
        return false;
    };
    let current_before = &before[before_index];
    let current_after = &after[after_index];
    all_pairs.iter().any(|other| {
        let (Some(other_before), Some(other_after)) = (other.before, other.after) else {
            return false;
        };
        let other_before = &before[other_before];
        let other_after = &after[other_after];
        current_before.owner == other_before.owner
            && current_after.owner == other_after.owner
            && current_before.ordinal.cmp(&other_before.ordinal)
                != current_after.ordinal.cmp(&other_after.ordinal)
    })
}

fn pair_sort_key(
    pair: &DeclarationPair,
    before: &[JavaDeclaration],
    after: &[JavaDeclaration],
) -> (usize, usize, String) {
    (
        pair.before
            .map_or(usize::MAX, |index| before[index].ordinal),
        pair.after.map_or(usize::MAX, |index| after[index].ordinal),
        pair.after
            .map(|index| after[index].semantic_key.clone())
            .or_else(|| pair.before.map(|index| before[index].semantic_key.clone()))
            .unwrap_or_default(),
    )
}

fn correspondence_label(
    kind: ReviewCorrespondenceKindV1,
    before: Option<&JavaDeclaration>,
    after: Option<&JavaDeclaration>,
) -> String {
    match (before, after) {
        (Some(before), Some(after)) if before.semantic_key != after.semantic_key => {
            format!("{} -> {}", before.semantic_key, after.semantic_key)
        }
        (Some(before), _) => before.semantic_key.clone(),
        (_, Some(after)) => after.semantic_key.clone(),
        (None, None) => format!("<invalid {kind:?} correspondence>"),
    }
}

const fn correspondence_name(kind: ReviewCorrespondenceKindV1) -> &'static str {
    match kind {
        ReviewCorrespondenceKindV1::Unchanged => "unchanged",
        ReviewCorrespondenceKindV1::Edited => "edited",
        ReviewCorrespondenceKindV1::Added => "added",
        ReviewCorrespondenceKindV1::Deleted => "deleted",
        ReviewCorrespondenceKindV1::Moved => "moved",
        ReviewCorrespondenceKindV1::Renamed => "renamed",
        ReviewCorrespondenceKindV1::MovedAndRenamed => "moved-and-renamed",
        ReviewCorrespondenceKindV1::FormattingOnly => "formatting-only",
        ReviewCorrespondenceKindV1::Ambiguous => "ambiguous",
    }
}

fn text_fallback(
    request: &ReviewSurfaceRequestV1,
    limits: ReviewSurfaceLimitsV1,
    diagnostic: ReviewSurfaceDiagnosticV1,
) -> eyre::Result<ReviewSurfaceV1> {
    let mut text_request = request.clone();
    text_request.surface_kind = ReviewSurfaceKindV1::TextDiff;
    let mut surface = produce_text_diff(&text_request, limits)?;
    surface.surface_kind = request.surface_kind;
    surface.algorithm =
        format!("{JAVA_STRUCTURED_DIFF_ALGORITHM_V1};fallback={TEXT_DIFF_ALGORITHM_V1}");
    if surface.complete {
        surface.outcome = ReviewSurfaceOutcomeV1::Fallback;
        surface.fallback_kind = Some(ReviewSurfaceKindV1::TextDiff);
    }
    surface.correspondence.complete = false;
    surface.correspondence.diagnostics.push(diagnostic.clone());
    surface.diagnostics.push(diagnostic);
    surface.diagnostics.sort();
    surface.diagnostics.dedup();
    surface.diagnostics.truncate(request.maximum_diagnostics);
    surface
        .correspondence
        .diagnostics
        .truncate(request.maximum_diagnostics);
    Ok(surface)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::release_review_surface_v1::ReviewFilePairV1;
    use crate::release_review_surface_v1::ReviewSurfaceSourceV1;
    use crate::release_review_surface_v1::parse_surface;
    use crate::release_review_surface_v1::surface_to_canonical_json;
    use crate::release_review_v1::ChangeOperationV1;

    fn request(before: &str, after: &str) -> ReviewSurfaceRequestV1 {
        let limits = ReviewSurfaceLimitsV1::default();
        ReviewSurfaceRequestV1::new(
            11,
            4,
            ReviewFilePairV1::new(
                "pair-java",
                "1.19.2",
                ChangeOperationV1::Modified,
                vec!["unit-java".to_owned()],
                Some(ReviewSurfaceSourceV1::new(
                    "before-java",
                    "src/A.java",
                    "java",
                    before,
                )),
                Some(ReviewSurfaceSourceV1::new(
                    "after-java",
                    "src/A.java",
                    "java",
                    after,
                )),
            ),
            ReviewSurfaceKindV1::JavaStructuredDiff,
            limits,
        )
    }

    #[test]
    fn changed_spans_leave_annotations_and_unicode_context_neutral() {
        let request = request(
            "class A { @Override public String toString() { return \"😀old\"; } }",
            "class A { @Override public String toString() { return \"😀new\"; } }",
        );
        let limits = ReviewSurfaceLimitsV1::default();
        let surface = produce_java_structured_diff(&request, limits).expect("refined diff");
        assert_eq!(surface.outcome, ReviewSurfaceOutcomeV1::Produced);
        surface
            .validate_against(&request, limits)
            .expect("exact UTF-8 mappings");
        let colored = surface
            .mappings
            .iter()
            .filter(|mapping| {
                matches!(
                    mapping.kind,
                    ReviewSurfaceMappingKindV1::StructuralBefore
                        | ReviewSurfaceMappingKindV1::StructuralAfter
                )
            })
            .map(|mapping| {
                &surface.text[mapping.surface_range.start_byte..mapping.surface_range.end_byte]
            })
            .collect::<Vec<_>>();
        assert_eq!(colored, vec!["old", "new"]);
        assert!(surface.mappings.iter().any(|mapping| {
            mapping.kind == ReviewSurfaceMappingKindV1::StructuralCorrespondence
                && surface.text[mapping.surface_range.start_byte..mapping.surface_range.end_byte]
                    .contains("@Override")
        }));
    }

    #[test]
    fn changed_spans_preserve_common_islands_and_bound_work() {
        let (before, after) = changed_spans("a😀b\r\nc", "x😀y\r\nz").expect("bounded diff");
        assert_eq!(
            before
                .iter()
                .map(|range| &"a😀b\r\nc"[range.start_byte..range.end_byte])
                .collect::<Vec<_>>(),
            vec!["a", "b", "c"]
        );
        assert_eq!(
            after
                .iter()
                .map(|range| &"x😀y\r\nz"[range.start_byte..range.end_byte])
                .collect::<Vec<_>>(),
            vec!["x", "y", "z"]
        );
        assert!(changed_spans(&"a".repeat(4097), &"b".repeat(4097)).is_err());
    }

    #[test]
    fn matches_edited_moved_renamed_and_reordered_declarations() {
        let before = r#"
            import java.util.List;
            class A {
                void alpha() { System.out.println("before"); }
                void beta() { }
                void gamma() { }
                void renamed() { return; }
            }
        "#;
        let after = r#"
            import java.util.List;
            class A {
                void alpha() { System.out.println("after"); }
                void gamma() { }
                void beta() { }
                void renamedTo() { return; }
            }
        "#;
        let request = request(before, after);
        let surface = produce_java_structured_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("structured diff");
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("exact structured mappings");
        let kinds = surface
            .correspondence
            .correspondences
            .iter()
            .map(|correspondence| correspondence.kind)
            .collect::<BTreeSet<_>>();
        assert!(kinds.contains(&ReviewCorrespondenceKindV1::Edited));
        assert!(kinds.contains(&ReviewCorrespondenceKindV1::Moved));
        assert!(
            kinds.contains(&ReviewCorrespondenceKindV1::Renamed)
                || kinds.contains(&ReviewCorrespondenceKindV1::MovedAndRenamed)
        );
    }

    #[test]
    fn comments_whitespace_and_crlf_are_reported_without_losing_source_bytes() {
        let request = request(
            "class Caf\u{e9} {\r\n    void caf\u{e9}() { return; }\r\n}\r\n",
            "class Caf\u{e9} {\r\n    // note\r\n    void caf\u{e9}() {  return;  }\r\n}\r\n",
        );
        let surface = produce_java_structured_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("structured formatting diff");
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("CRLF mappings");
        assert!(surface.text.contains("Caf\u{e9}"));
        assert!(
            surface
                .correspondence
                .correspondences
                .iter()
                .any(|item| item.kind == ReviewCorrespondenceKindV1::FormattingOnly)
        );
    }

    #[test]
    fn parse_gap_and_unsupported_language_use_explicit_text_fallback() {
        let malformed = request("class A { void x( { }", "class A { void x() { } }");
        let malformed_surface =
            produce_java_structured_diff(&malformed, ReviewSurfaceLimitsV1::default())
                .expect("parse fallback");
        assert_eq!(malformed_surface.outcome, ReviewSurfaceOutcomeV1::Fallback);
        assert_eq!(
            malformed_surface.fallback_kind,
            Some(ReviewSurfaceKindV1::TextDiff)
        );
        assert!(
            malformed_surface
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code.contains("parse-gap"))
        );
        malformed_surface
            .validate_against(&malformed, ReviewSurfaceLimitsV1::default())
            .expect("parse fallback mappings");

        let mut unsupported = request("old\n", "new\n");
        for source in unsupported
            .file_pair
            .before
            .iter_mut()
            .chain(&mut unsupported.file_pair.after)
        {
            source.path = "README.md".to_owned();
            source.language = "markdown".to_owned();
        }
        let unsupported_surface =
            produce_java_structured_diff(&unsupported, ReviewSurfaceLimitsV1::default())
                .expect("unsupported fallback");
        assert_eq!(
            unsupported_surface.outcome,
            ReviewSurfaceOutcomeV1::Fallback
        );
        assert!(
            unsupported_surface
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == "review.java-diff.unsupported-language")
        );
        unsupported_surface
            .validate_against(&unsupported, ReviewSurfaceLimitsV1::default())
            .expect("unsupported fallback mappings");
    }

    #[test]
    fn import_changes_and_field_rename_remain_individually_addressable() {
        let request = request(
            "import java.util.List;\nclass A { int oldName; }\n",
            "import java.util.Set;\nclass A { int newName; }\n",
        );
        let surface = produce_java_structured_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("import and field diff");
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("addressable structured mappings");
        let kinds = surface
            .correspondence
            .correspondences
            .iter()
            .map(|item| item.kind)
            .collect::<BTreeSet<_>>();
        assert!(kinds.contains(&ReviewCorrespondenceKindV1::Added));
        assert!(kinds.contains(&ReviewCorrespondenceKindV1::Deleted));
        assert!(kinds.contains(&ReviewCorrespondenceKindV1::Renamed));
        assert!(
            surface
                .regions
                .iter()
                .any(|region| region.kind == ReviewSurfaceRegionKindV1::JavaImport)
        );
    }

    #[test]
    fn added_java_file_has_only_after_correspondence_ranges() {
        let mut request = request("", "class Added { void x() {} }\n");
        request.file_pair.before = None;
        request.file_pair.operation = ChangeOperationV1::Added;
        let surface = produce_java_structured_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("added Java file");
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("added Java mappings");
        assert!(
            surface
                .correspondence
                .correspondences
                .iter()
                .all(|item| item.before_ranges.is_empty() && !item.after_ranges.is_empty())
        );
    }

    #[test]
    fn ambiguous_name_insensitive_matches_fall_back_conservatively() {
        let request = request(
            "class A { void a() { return; } void b() { return; } }",
            "class A { void c() { return; } void d() { return; } }",
        );
        let surface = produce_java_structured_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("ambiguous fallback");
        assert_eq!(surface.outcome, ReviewSurfaceOutcomeV1::Fallback);
        assert!(
            surface
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == "review.java-diff.ambiguous-correspondence")
        );
    }

    #[test]
    fn repeated_structured_generation_and_json_are_stable() {
        let request = request(
            "class A { int value = 1; }\n",
            "class A { int value = 2; }\n",
        );
        let first = produce_java_structured_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("first surface");
        let second = produce_java_structured_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("second surface");
        assert_eq!(first, second);
        let first_json = surface_to_canonical_json(&first).expect("first JSON");
        assert_eq!(
            first_json,
            surface_to_canonical_json(&second).expect("second JSON")
        );
        let decoded = parse_surface(&first_json).expect("structured surface round trip");
        decoded
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("round-tripped structured mappings");
        assert_eq!(
            surface_to_canonical_json(&decoded).expect("rewritten structured JSON"),
            first_json
        );
    }
}
