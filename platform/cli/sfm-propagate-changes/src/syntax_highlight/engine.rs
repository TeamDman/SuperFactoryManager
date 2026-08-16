use super::SYNTAX_HIGHLIGHT_FORMATTING_SCHEMA;
use super::SYNTAX_HIGHLIGHT_PARSER_FINGERPRINT;
use super::SYNTAX_HIGHLIGHT_RESULT_SCHEMA;
use super::SyntaxHighlightCacheEvidence;
use super::SyntaxHighlightCacheStatus;
use super::SyntaxHighlightDiagnostic;
use super::SyntaxHighlightDiagnosticSeverity;
use super::SyntaxHighlightLimits;
use super::SyntaxHighlightOutcome;
use super::SyntaxHighlightRequest;
use super::SyntaxHighlightResult;
use super::SyntaxHighlightSpan;
use crate::cancellation::CancellationToken;
use arborium_highlight::Span as ArboriumSpan;
use arborium_highlight::spans_to_flat_tokens;
use std::collections::VecDeque;
use std::time::Instant;
use streaming_iterator::StreamingIterator;
use tree_sitter_patched_arborium::Node;
use tree_sitter_patched_arborium::ParseOptions;
use tree_sitter_patched_arborium::ParseState;
use tree_sitter_patched_arborium::Parser;
use tree_sitter_patched_arborium::Query;
use tree_sitter_patched_arborium::QueryCursor;
use tree_sitter_patched_arborium::QueryCursorOptions;
use tree_sitter_patched_arborium::QueryCursorState;
use tree_sitter_patched_arborium::Tree;

const DEFAULT_CACHE_MAX_ENTRIES: usize = 128;
const DEFAULT_CACHE_MAX_BYTES: usize = 16 * 1024 * 1024;
const DEFAULT_MAX_CAPTURES: usize = 2 * super::DEFAULT_SYNTAX_HIGHLIGHT_MAX_SPANS;
const CONSERVATIVE_ALLOCATION_OVERHEAD_BYTES: usize = 64;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SyntaxHighlightEngineLimits {
    pub protocol: SyntaxHighlightLimits,
    pub cache_max_entries: usize,
    pub cache_max_bytes: usize,
    pub max_captures: usize,
}

impl Default for SyntaxHighlightEngineLimits {
    fn default() -> Self {
        Self {
            protocol: SyntaxHighlightLimits::default(),
            cache_max_entries: DEFAULT_CACHE_MAX_ENTRIES,
            cache_max_bytes: DEFAULT_CACHE_MAX_BYTES,
            max_captures: DEFAULT_MAX_CAPTURES,
        }
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct SyntaxHighlightEngineMetrics {
    pub query_compilations: u64,
    pub parse_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub cache_evictions: u64,
    pub cache_entries: u64,
    pub cache_retained_bytes: u64,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct SyntaxHighlightCacheKey {
    language: String,
    source_sha256: String,
    formatting_schema: String,
}

#[derive(Clone, Debug)]
struct SyntaxHighlightCacheEntry {
    key: SyntaxHighlightCacheKey,
    spans: Vec<SyntaxHighlightSpan>,
    diagnostics: Vec<SyntaxHighlightDiagnostic>,
    retained_bytes: usize,
}

enum CachedHighlight {
    Highlighted {
        spans: Vec<SyntaxHighlightSpan>,
        diagnostics: Vec<SyntaxHighlightDiagnostic>,
    },
    SpanLimitExceeded {
        actual_spans: usize,
    },
}

enum CaptureCollection {
    Complete(Vec<ArboriumSpan>),
    Cancelled,
    LimitExceeded { maximum: usize },
}

enum SpanConstruction {
    Complete(Vec<SyntaxHighlightSpan>),
    Cancelled,
    LimitExceeded { at_least: usize },
}

/// Reusable Java highlighting engine. The grammar query, parser, query cursor,
/// and bounded immutable-result cache all outlive individual requests.
pub struct SyntaxHighlightEngine {
    query: Query,
    parser: Parser,
    cursor: QueryCursor,
    cache: VecDeque<SyntaxHighlightCacheEntry>,
    cache_retained_bytes: usize,
    metrics: SyntaxHighlightEngineMetrics,
    limits: SyntaxHighlightEngineLimits,
}

impl SyntaxHighlightEngine {
    /// Compile the supported Java grammar and highlight query once.
    ///
    /// # Errors
    ///
    /// Returns an error when process/cache limits are invalid or Arborium's
    /// Java grammar/query cannot be configured.
    pub fn new(limits: SyntaxHighlightEngineLimits) -> eyre::Result<Self> {
        limits.protocol.validate()?;
        if limits.cache_max_entries == 0 || limits.cache_max_bytes == 0 || limits.max_captures == 0
        {
            eyre::bail!("syntax highlight cache and capture bounds must be positive");
        }
        let language = arborium_java::language().into();
        let query = Query::new(&language, arborium_java::HIGHLIGHTS_QUERY)
            .map_err(|error| eyre::eyre!("Failed to compile Arborium Java highlights: {error}"))?;
        let mut parser = Parser::new();
        parser
            .set_language(&language)
            .map_err(|error| eyre::eyre!("Failed to load Arborium Java grammar: {error}"))?;
        Ok(Self {
            query,
            parser,
            cursor: QueryCursor::new(),
            cache: VecDeque::new(),
            cache_retained_bytes: 0,
            metrics: SyntaxHighlightEngineMetrics {
                query_compilations: 1,
                ..SyntaxHighlightEngineMetrics::default()
            },
            limits,
        })
    }

    #[must_use]
    pub const fn limits(&self) -> SyntaxHighlightEngineLimits {
        self.limits
    }

    #[must_use]
    pub fn metrics(&self) -> SyntaxHighlightEngineMetrics {
        SyntaxHighlightEngineMetrics {
            cache_entries: self.cache.len() as u64,
            cache_retained_bytes: self.cache_retained_bytes as u64,
            ..self.metrics
        }
    }

    /// Highlight one immutable exact-source request.
    ///
    /// Validation and unsupported-language failures are returned as typed
    /// terminal results. Infrastructure failures remain Rust errors.
    ///
    /// # Errors
    ///
    /// Returns an error only when Arborium cannot return a parse tree or an
    /// internally produced result violates the frozen protocol contract.
    pub fn highlight(
        &mut self,
        request: &SyntaxHighlightRequest,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<SyntaxHighlightResult> {
        let started = Instant::now();
        if let Err(error) = request.validate(self.limits.protocol) {
            return Ok(self.finish_terminal(
                request,
                SyntaxHighlightOutcome::InvalidRequest,
                SyntaxHighlightDiagnostic {
                    code: "syntax.invalid-request".to_owned(),
                    severity: SyntaxHighlightDiagnosticSeverity::Error,
                    message: error.to_string(),
                    start_byte: None,
                    end_byte: None,
                },
                started,
            ));
        }
        if request.language != "java" {
            return Ok(self.finish_terminal(
                request,
                SyntaxHighlightOutcome::UnsupportedLanguage,
                SyntaxHighlightDiagnostic {
                    code: "syntax.unsupported-language".to_owned(),
                    severity: SyntaxHighlightDiagnosticSeverity::Info,
                    message: format!(
                        "Syntax language `{}` is not enabled; enabled languages: java",
                        request.language
                    ),
                    start_byte: None,
                    end_byte: None,
                },
                started,
            ));
        }
        if cancellation_token.is_cancelled() {
            return Ok(self.finish_terminal(
                request,
                SyntaxHighlightOutcome::Cancelled,
                cancellation_diagnostic(cancellation_token),
                started,
            ));
        }

        let key = SyntaxHighlightCacheKey {
            language: request.language.clone(),
            source_sha256: request.source_sha256.clone(),
            formatting_schema: SYNTAX_HIGHLIGHT_FORMATTING_SCHEMA.to_owned(),
        };
        let maximum_spans = usize::try_from(request.maximum_spans).map_err(|error| {
            eyre::eyre!("syntax request span count is not representable: {error}")
        })?;
        if let Some(cached) = self.take_cached(&key, maximum_spans) {
            return match cached {
                CachedHighlight::Highlighted { spans, diagnostics } => self.finish_highlighted(
                    request,
                    spans,
                    diagnostics,
                    SyntaxHighlightCacheStatus::Hit,
                    started,
                ),
                CachedHighlight::SpanLimitExceeded { actual_spans } => {
                    Ok(self.span_limit_exceeded(request, actual_spans, started))
                }
            };
        }

        self.metrics.cache_misses = self.metrics.cache_misses.saturating_add(1);
        self.metrics.parse_count = self.metrics.parse_count.saturating_add(1);
        self.highlight_uncached(request, cancellation_token, key, started)
    }

    fn highlight_uncached(
        &mut self,
        request: &SyntaxHighlightRequest,
        cancellation_token: &CancellationToken,
        key: SyntaxHighlightCacheKey,
        started: Instant,
    ) -> eyre::Result<SyntaxHighlightResult> {
        let Some(tree) = self.parse_java(&request.source, cancellation_token)? else {
            return Ok(self.finish_terminal(
                request,
                SyntaxHighlightOutcome::Cancelled,
                cancellation_diagnostic(cancellation_token),
                started,
            ));
        };
        if cancellation_token.is_cancelled() {
            return Ok(self.finish_terminal(
                request,
                SyntaxHighlightOutcome::Cancelled,
                cancellation_diagnostic(cancellation_token),
                started,
            ));
        }

        let Some(diagnostics) = collect_parse_diagnostics(
            tree.root_node(),
            self.limits.protocol.max_diagnostics,
            cancellation_token,
        ) else {
            return Ok(self.finish_terminal(
                request,
                SyntaxHighlightOutcome::Cancelled,
                cancellation_diagnostic(cancellation_token),
                started,
            ));
        };
        let captures =
            match self.collect_captures(tree.root_node(), &request.source, cancellation_token)? {
                CaptureCollection::Complete(captures) => captures,
                CaptureCollection::Cancelled => {
                    return Ok(self.finish_terminal(
                        request,
                        SyntaxHighlightOutcome::Cancelled,
                        cancellation_diagnostic(cancellation_token),
                        started,
                    ));
                }
                CaptureCollection::LimitExceeded { maximum } => {
                    return Ok(self.finish_terminal(
                        request,
                        SyntaxHighlightOutcome::Failed,
                        SyntaxHighlightDiagnostic {
                            code: "syntax.capture-limit-exceeded".to_owned(),
                            severity: SyntaxHighlightDiagnosticSeverity::Error,
                            message: format!(
                                "Java highlighting exceeded the process capture maximum {maximum}"
                            ),
                            start_byte: None,
                            end_byte: None,
                        },
                        started,
                    ));
                }
            };
        if cancellation_token.is_cancelled() {
            return Ok(self.finish_terminal(
                request,
                SyntaxHighlightOutcome::Cancelled,
                cancellation_diagnostic(cancellation_token),
                started,
            ));
        }
        let flat = spans_to_flat_tokens(&request.source, captures);
        let maximum_spans = usize::try_from(request.maximum_spans).map_err(|error| {
            eyre::eyre!("syntax request span count is not representable: {error}")
        })?;
        let spans = match construct_spans(flat, maximum_spans, cancellation_token) {
            SpanConstruction::Complete(spans) => spans,
            SpanConstruction::Cancelled => {
                return Ok(self.finish_terminal(
                    request,
                    SyntaxHighlightOutcome::Cancelled,
                    cancellation_diagnostic(cancellation_token),
                    started,
                ));
            }
            SpanConstruction::LimitExceeded { at_least } => {
                return Ok(self.span_limit_exceeded(request, at_least, started));
            }
        };

        self.insert_cache(key, spans.clone(), diagnostics.clone());
        self.finish_highlighted(
            request,
            spans,
            diagnostics,
            SyntaxHighlightCacheStatus::Miss,
            started,
        )
    }

    fn parse_java(
        &mut self,
        source: &str,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Option<Tree>> {
        let source_bytes = source.as_bytes();
        let source_length = source_bytes.len();
        let mut read_source = |offset, _position| {
            if offset < source_length {
                &source_bytes[offset..]
            } else {
                &[]
            }
        };
        let mut parse_progress = |_state: &ParseState| cancellation_token.is_cancelled();
        let tree = self.parser.parse_with_options(
            &mut read_source,
            None,
            Some(ParseOptions::new().progress_callback(&mut parse_progress)),
        );
        if tree.is_none() && cancellation_token.is_cancelled() {
            self.parser.reset();
            return Ok(None);
        }
        tree.map(Some).ok_or_else(|| {
            eyre::eyre!("Arborium did not produce a Java parse tree for syntax request")
        })
    }

    fn collect_captures(
        &mut self,
        root: Node<'_>,
        source: &str,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<CaptureCollection> {
        let mut captures = Vec::new();
        let capture_names = self.query.capture_names();
        let maximum = self.limits.max_captures;
        let mut query_progress = |_state: &QueryCursorState| cancellation_token.is_cancelled();
        let mut matches = self.cursor.matches_with_options(
            &self.query,
            root,
            source.as_bytes(),
            QueryCursorOptions::new().progress_callback(&mut query_progress),
        );
        while let Some(query_match) = matches.next() {
            if cancellation_token.is_cancelled() {
                return Ok(CaptureCollection::Cancelled);
            }
            for capture in query_match.captures {
                if cancellation_token.is_cancelled() {
                    return Ok(CaptureCollection::Cancelled);
                }
                let capture_index = usize::try_from(capture.index).map_err(|error| {
                    eyre::eyre!("Arborium capture index is not representable: {error}")
                })?;
                let capture_name = capture_names.get(capture_index).ok_or_else(|| {
                    eyre::eyre!("Arborium returned unknown capture index {capture_index}")
                })?;
                if capture_name.starts_with('_') || capture_name.starts_with("injection.") {
                    continue;
                }
                if captures.len() >= maximum {
                    return Ok(CaptureCollection::LimitExceeded { maximum });
                }
                let start = u32::try_from(capture.node.start_byte()).map_err(|error| {
                    eyre::eyre!("Arborium capture start is not representable: {error}")
                })?;
                let end = u32::try_from(capture.node.end_byte()).map_err(|error| {
                    eyre::eyre!("Arborium capture end is not representable: {error}")
                })?;
                let pattern_index = u32::try_from(query_match.pattern_index).map_err(|error| {
                    eyre::eyre!("Arborium pattern index is not representable: {error}")
                })?;
                captures.push(ArboriumSpan {
                    start,
                    end,
                    capture: (*capture_name).to_owned(),
                    pattern_index,
                });
            }
        }
        drop(matches);
        if cancellation_token.is_cancelled() {
            Ok(CaptureCollection::Cancelled)
        } else {
            Ok(CaptureCollection::Complete(captures))
        }
    }

    fn take_cached(
        &mut self,
        key: &SyntaxHighlightCacheKey,
        maximum_spans: usize,
    ) -> Option<CachedHighlight> {
        let index = self.cache.iter().position(|entry| &entry.key == key)?;
        let entry = self.cache.remove(index)?;
        self.metrics.cache_hits = self.metrics.cache_hits.saturating_add(1);
        let cached = if entry.spans.len() > maximum_spans {
            CachedHighlight::SpanLimitExceeded {
                actual_spans: entry.spans.len(),
            }
        } else {
            CachedHighlight::Highlighted {
                spans: entry.spans.clone(),
                diagnostics: entry.diagnostics.clone(),
            }
        };
        self.cache.push_back(entry);
        Some(cached)
    }

    fn finish_highlighted(
        &self,
        request: &SyntaxHighlightRequest,
        spans: Vec<SyntaxHighlightSpan>,
        diagnostics: Vec<SyntaxHighlightDiagnostic>,
        cache_status: SyntaxHighlightCacheStatus,
        started: Instant,
    ) -> eyre::Result<SyntaxHighlightResult> {
        let result = SyntaxHighlightResult {
            schema: SYNTAX_HIGHLIGHT_RESULT_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            origin_id: request.origin_id.clone(),
            origin_generation: request.origin_generation,
            language: request.language.clone(),
            source_sha256: request.source_sha256.clone(),
            source_bytes: request.source.len() as u64,
            outcome: SyntaxHighlightOutcome::Highlighted,
            complete: true,
            parser_fingerprint: SYNTAX_HIGHLIGHT_PARSER_FINGERPRINT.to_owned(),
            formatting_schema: SYNTAX_HIGHLIGHT_FORMATTING_SCHEMA.to_owned(),
            elapsed_micros: elapsed_micros(started),
            cache: self.cache_evidence(cache_status),
            diagnostics,
            spans,
        };
        result.validate_against(request, self.limits.protocol)?;
        Ok(result)
    }

    fn finish_terminal(
        &self,
        request: &SyntaxHighlightRequest,
        outcome: SyntaxHighlightOutcome,
        diagnostic: SyntaxHighlightDiagnostic,
        started: Instant,
    ) -> SyntaxHighlightResult {
        let mut result = SyntaxHighlightResult::terminal(request, outcome, vec![diagnostic]);
        result.elapsed_micros = elapsed_micros(started);
        result.cache = self.cache_evidence(SyntaxHighlightCacheStatus::Bypassed);
        result
    }

    fn span_limit_exceeded(
        &self,
        request: &SyntaxHighlightRequest,
        actual_spans: usize,
        started: Instant,
    ) -> SyntaxHighlightResult {
        self.finish_terminal(
            request,
            SyntaxHighlightOutcome::Failed,
            SyntaxHighlightDiagnostic {
                code: "syntax.span-limit-exceeded".to_owned(),
                severity: SyntaxHighlightDiagnosticSeverity::Error,
                message: format!(
                    "Java highlighting produced at least {actual_spans} spans, exceeding request maximum {}",
                    request.maximum_spans
                ),
                start_byte: None,
                end_byte: None,
            },
            started,
        )
    }

    fn cache_evidence(&self, status: SyntaxHighlightCacheStatus) -> SyntaxHighlightCacheEvidence {
        SyntaxHighlightCacheEvidence {
            status,
            entries: self.cache.len() as u64,
            retained_bytes: self.cache_retained_bytes as u64,
            hits: self.metrics.cache_hits,
            misses: self.metrics.cache_misses,
            evictions: self.metrics.cache_evictions,
        }
    }

    fn insert_cache(
        &mut self,
        key: SyntaxHighlightCacheKey,
        spans: Vec<SyntaxHighlightSpan>,
        diagnostics: Vec<SyntaxHighlightDiagnostic>,
    ) {
        let retained_bytes = conservative_cache_entry_payload_bytes(
            &key,
            &spans,
            spans.capacity(),
            &diagnostics,
            diagnostics.capacity(),
        );
        self.cache.push_back(SyntaxHighlightCacheEntry {
            key,
            spans,
            diagnostics,
            retained_bytes,
        });
        self.refresh_cache_retained_bytes();
        while self.cache.len() > self.limits.cache_max_entries
            || self.cache_retained_bytes > self.limits.cache_max_bytes
        {
            let Some(evicted) = self.cache.pop_front() else {
                break;
            };
            drop(evicted);
            self.metrics.cache_evictions = self.metrics.cache_evictions.saturating_add(1);
            self.cache.shrink_to_fit();
            self.refresh_cache_retained_bytes();
        }
    }

    fn refresh_cache_retained_bytes(&mut self) {
        self.cache_retained_bytes = conservative_cache_retained_bytes(&self.cache);
    }
}

fn collect_parse_diagnostics(
    root: Node<'_>,
    maximum: usize,
    cancellation_token: &CancellationToken,
) -> Option<Vec<SyntaxHighlightDiagnostic>> {
    let mut diagnostics = Vec::new();
    let mut stack = vec![root];
    let mut total = 0_usize;
    while let Some(node) = stack.pop() {
        if cancellation_token.is_cancelled() {
            return None;
        }
        if node.kind() == "ERROR" || node.is_missing() {
            total = total.saturating_add(1);
            if diagnostics.len() < maximum {
                diagnostics.push(SyntaxHighlightDiagnostic {
                    code: "syntax.java.parse-gap".to_owned(),
                    severity: SyntaxHighlightDiagnosticSeverity::Warning,
                    message: if node.is_missing() {
                        format!("Arborium inserted missing Java node `{}`", node.kind())
                    } else {
                        "Arborium encountered recoverable Java syntax".to_owned()
                    },
                    start_byte: (node.start_byte() < node.end_byte())
                        .then_some(node.start_byte() as u64),
                    end_byte: (node.start_byte() < node.end_byte())
                        .then_some(node.end_byte() as u64),
                });
            }
        }
        let mut cursor = node.walk();
        stack.extend(node.children(&mut cursor));
    }
    if total > diagnostics.len() && diagnostics.len() < maximum {
        diagnostics.push(SyntaxHighlightDiagnostic {
            code: "syntax.java.parse-gaps-suppressed".to_owned(),
            severity: SyntaxHighlightDiagnosticSeverity::Warning,
            message: format!(
                "Suppressed {} additional Java parse diagnostics",
                total - diagnostics.len()
            ),
            start_byte: None,
            end_byte: None,
        });
    }
    diagnostics.sort();
    diagnostics.dedup();
    Some(diagnostics)
}

fn construct_spans(
    flat: Vec<arborium_highlight::FlatToken>,
    maximum: usize,
    cancellation_token: &CancellationToken,
) -> SpanConstruction {
    let mut coalesced: Vec<SyntaxHighlightSpan> = Vec::with_capacity(flat.len().min(maximum));
    for token in flat {
        if cancellation_token.is_cancelled() {
            return SpanConstruction::Cancelled;
        }
        let tag = arborium_tag_name(token.tag);
        let span = SyntaxHighlightSpan {
            start_byte: u64::from(token.start),
            end_byte: u64::from(token.end),
            arborium_tag: tag.to_owned(),
            chat_formatting: chat_formatting_for_tag(tag),
        };
        if let Some(previous) = coalesced.last_mut()
            && previous.end_byte == span.start_byte
            && previous.arborium_tag == span.arborium_tag
            && previous.chat_formatting == span.chat_formatting
        {
            previous.end_byte = span.end_byte;
        } else {
            if coalesced.len() >= maximum {
                return SpanConstruction::LimitExceeded {
                    at_least: maximum.saturating_add(1),
                };
            }
            coalesced.push(span);
        }
    }
    SpanConstruction::Complete(coalesced)
}

fn chat_formatting_for_tag(tag: &str) -> Vec<String> {
    let names: &[&str] = match tag {
        "keyword" => &["light_purple"],
        "function" => &["yellow"],
        "string" | "literal" | "diff-add" => &["green"],
        "comment" => &["dark_gray", "italic"],
        "type" | "constructor" | "namespace" => &["aqua"],
        "variable" => &["white"],
        "constant" | "number" | "label" => &["gold"],
        "punctuation" | "operator" => &["gray"],
        "property" | "attribute" | "embedded" => &["dark_aqua"],
        "tag" => &["blue"],
        "macro" | "diff-delete" | "error" => &["red"],
        "title" => &["yellow", "bold"],
        "strong" => &["bold"],
        "emphasis" => &["italic"],
        "link" => &["blue", "underline"],
        "strikethrough" => &["strikethrough"],
        _ => &[],
    };
    names.iter().map(|name| (*name).to_owned()).collect()
}

fn arborium_tag_name(tag: &'static str) -> &'static str {
    match tag {
        "k" => "keyword",
        "f" => "function",
        "s" => "string",
        "c" => "comment",
        "t" => "type",
        "v" => "variable",
        "co" => "constant",
        "n" => "number",
        "o" => "operator",
        "p" => "punctuation",
        "pr" => "property",
        "at" => "attribute",
        "tg" => "tag",
        "m" => "macro",
        "l" => "label",
        "ns" => "namespace",
        "cr" => "constructor",
        "tt" => "title",
        "st" => "strong",
        "em" => "emphasis",
        "tu" => "link",
        "tl" => "literal",
        "tx" => "strikethrough",
        "da" => "diff-add",
        "dd" => "diff-delete",
        "eb" => "embedded",
        "er" => "error",
        other => other,
    }
}

fn conservative_cache_entry_payload_bytes(
    key: &SyntaxHighlightCacheKey,
    spans: &[SyntaxHighlightSpan],
    spans_capacity: usize,
    diagnostics: &[SyntaxHighlightDiagnostic],
    diagnostics_capacity: usize,
) -> usize {
    let mut retained = conservative_string_allocation_bytes(&key.language)
        .saturating_add(conservative_string_allocation_bytes(&key.source_sha256))
        .saturating_add(conservative_string_allocation_bytes(&key.formatting_schema))
        .saturating_add(conservative_vec_capacity_bytes::<SyntaxHighlightSpan>(
            spans_capacity,
        ));
    for span in spans {
        retained = retained
            .saturating_add(conservative_string_allocation_bytes(&span.arborium_tag))
            .saturating_add(conservative_vec_capacity_bytes::<String>(
                span.chat_formatting.capacity(),
            ));
        for formatting in &span.chat_formatting {
            retained = retained.saturating_add(conservative_string_allocation_bytes(formatting));
        }
    }
    retained = retained.saturating_add(
        conservative_vec_capacity_bytes::<SyntaxHighlightDiagnostic>(diagnostics_capacity),
    );
    for diagnostic in diagnostics {
        retained = retained
            .saturating_add(conservative_string_allocation_bytes(&diagnostic.code))
            .saturating_add(conservative_string_allocation_bytes(&diagnostic.message));
    }
    retained
}

/// Account every cache-owned heap allocation by allocated capacity and charge
/// an additional fixed allowance for allocator metadata/alignment. This is an
/// intentionally conservative budget model rather than a payload-byte count.
fn conservative_cache_retained_bytes(cache: &VecDeque<SyntaxHighlightCacheEntry>) -> usize {
    if cache.is_empty() {
        return 0;
    }
    cache
        .capacity()
        .saturating_mul(std::mem::size_of::<SyntaxHighlightCacheEntry>())
        .saturating_add(CONSERVATIVE_ALLOCATION_OVERHEAD_BYTES)
        .saturating_add(
            cache
                .iter()
                .map(|entry| entry.retained_bytes)
                .fold(0_usize, usize::saturating_add),
        )
}

fn conservative_vec_capacity_bytes<T>(capacity: usize) -> usize {
    if capacity == 0 {
        0
    } else {
        capacity
            .saturating_mul(std::mem::size_of::<T>())
            .saturating_add(CONSERVATIVE_ALLOCATION_OVERHEAD_BYTES)
    }
}

fn conservative_string_allocation_bytes(value: &String) -> usize {
    if value.capacity() == 0 {
        0
    } else {
        value
            .capacity()
            .saturating_add(CONSERVATIVE_ALLOCATION_OVERHEAD_BYTES)
    }
}

fn cancellation_diagnostic(token: &CancellationToken) -> SyntaxHighlightDiagnostic {
    SyntaxHighlightDiagnostic {
        code: "syntax.cancelled".to_owned(),
        severity: SyntaxHighlightDiagnosticSeverity::Info,
        message: token
            .cancellation_reason()
            .unwrap_or_else(|| "Syntax highlighting was cancelled".to_owned()),
        start_byte: None,
        end_byte: None,
    }
}

fn elapsed_micros(started: Instant) -> u64 {
    u64::try_from(started.elapsed().as_micros()).unwrap_or(u64::MAX)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn request(id: u64, source: &str) -> SyntaxHighlightRequest {
        SyntaxHighlightRequest::new(id, 1, "editor:java", 1, "java", source.to_owned(), 4096)
    }

    #[test]
    fn syntax_highlight_engine_formats_representative_java_deterministically() {
        let source = r#"
            package example;
            import java.util.List;
            @Deprecated
            class Example<T> {
                // comment
                String text = "hello";
                int number = 42;
                List<T> values() { return null; }
            }
        "#;
        let request = request(1, source);
        let mut engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("engine");
        let first = engine
            .highlight(&request, &CancellationToken::new())
            .expect("first highlight");
        assert_eq!(first.outcome, SyntaxHighlightOutcome::Highlighted);
        assert!(!first.spans.is_empty());
        assert!(
            first
                .spans
                .windows(2)
                .all(|pair| { pair[0].end_byte <= pair[1].start_byte })
        );
        assert!(first.spans.iter().any(|span| {
            span.arborium_tag == "comment" && span.chat_formatting == ["dark_gray", "italic"]
        }));
        assert!(first.spans.iter().any(|span| span.arborium_tag == "string"));

        let second = engine
            .highlight(&request, &CancellationToken::new())
            .expect("cached highlight");
        assert_eq!(second.cache.status, SyntaxHighlightCacheStatus::Hit);
        assert_eq!(second.spans, first.spans);
        assert_eq!(engine.metrics().query_compilations, 1);
        assert_eq!(engine.metrics().parse_count, 1);
    }

    #[test]
    fn syntax_highlight_engine_preserves_utf8_and_crlf_boundaries() {
        let source = "class Café { String smile = \"🙂\"; }\r\n";
        let request = request(1, source);
        let mut engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("engine");
        let result = engine
            .highlight(&request, &CancellationToken::new())
            .expect("highlight");
        result
            .validate_against(&request, SyntaxHighlightLimits::default())
            .expect("UTF-8 spans");
        assert!(result.spans.iter().all(|span| {
            usize::try_from(span.start_byte).is_ok_and(|offset| source.is_char_boundary(offset))
                && usize::try_from(span.end_byte)
                    .is_ok_and(|offset| source.is_char_boundary(offset))
        }));
    }

    #[test]
    fn syntax_highlight_engine_returns_typed_unsupported_invalid_and_cancelled_results() {
        let mut engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("engine");
        let mut unsupported = request(1, "fn main() {}");
        unsupported.language = "rust".to_owned();
        let result = engine
            .highlight(&unsupported, &CancellationToken::new())
            .expect("unsupported result");
        assert_eq!(result.outcome, SyntaxHighlightOutcome::UnsupportedLanguage);

        let mut invalid = request(2, "class A {}");
        invalid.source_sha256 = format!("sha256:{}", "0".repeat(64));
        let result = engine
            .highlight(&invalid, &CancellationToken::new())
            .expect("invalid result");
        assert_eq!(result.outcome, SyntaxHighlightOutcome::InvalidRequest);

        let cancelled = CancellationToken::new();
        cancelled.request_cancel("test cancellation");
        let result = engine
            .highlight(&request(3, "class B {}"), &cancelled)
            .expect("cancelled result");
        assert_eq!(result.outcome, SyntaxHighlightOutcome::Cancelled);
    }

    #[test]
    fn syntax_highlight_engine_reports_recoverable_malformed_java() {
        let request = request(1, "class Broken { void run( {");
        let mut engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("engine");
        let result = engine
            .highlight(&request, &CancellationToken::new())
            .expect("recovery highlight");
        assert_eq!(result.outcome, SyntaxHighlightOutcome::Highlighted);
        assert!(
            result
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == "syntax.java.parse-gap")
        );
    }

    #[test]
    fn syntax_highlight_engine_cache_is_bounded_and_evicts_old_entries() {
        let limits = SyntaxHighlightEngineLimits {
            cache_max_entries: 1,
            cache_max_bytes: 1024 * 1024,
            ..SyntaxHighlightEngineLimits::default()
        };
        let mut engine = SyntaxHighlightEngine::new(limits).expect("engine");
        engine
            .highlight(&request(1, "class A {}"), &CancellationToken::new())
            .expect("A");
        engine
            .highlight(&request(2, "class B {}"), &CancellationToken::new())
            .expect("B");
        assert_eq!(engine.metrics().cache_entries, 1);
        assert_eq!(engine.metrics().cache_evictions, 1);
    }

    #[test]
    fn syntax_highlight_engine_cached_result_honors_current_span_maximum() {
        let source = "class A { int value = 42; String text = \"hello\"; }";
        let mut engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("engine");
        let cached = engine
            .highlight(&request(1, source), &CancellationToken::new())
            .expect("populate cache");
        assert_eq!(cached.outcome, SyntaxHighlightOutcome::Highlighted);
        assert!(cached.spans.len() > 1);

        let limited =
            SyntaxHighlightRequest::new(2, 1, "editor:java", 1, "java", source.to_owned(), 1);
        let cached_failure = engine
            .highlight(&limited, &CancellationToken::new())
            .expect("cached bounded result");

        let mut fresh_engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("fresh");
        let miss_failure = fresh_engine
            .highlight(&limited, &CancellationToken::new())
            .expect("miss bounded result");
        assert_eq!(cached_failure.outcome, SyntaxHighlightOutcome::Failed);
        assert_eq!(cached_failure.outcome, miss_failure.outcome);
        assert_eq!(
            cached_failure.diagnostics[0].code,
            "syntax.span-limit-exceeded"
        );
        assert_eq!(
            cached_failure.diagnostics[0].code,
            miss_failure.diagnostics[0].code
        );
        assert_eq!(engine.metrics().parse_count, 1);
        assert_eq!(engine.metrics().cache_hits, 1);
    }

    #[test]
    fn syntax_highlight_engine_capture_work_has_a_typed_hard_limit() {
        let limits = SyntaxHighlightEngineLimits {
            max_captures: 1,
            ..SyntaxHighlightEngineLimits::default()
        };
        let mut engine = SyntaxHighlightEngine::new(limits).expect("engine");
        let result = engine
            .highlight(
                &request(1, "class A { int value = 42; String text = \"hello\"; }"),
                &CancellationToken::new(),
            )
            .expect("bounded capture result");
        assert_eq!(result.outcome, SyntaxHighlightOutcome::Failed);
        assert_eq!(result.diagnostics[0].code, "syntax.capture-limit-exceeded");
        assert!(result.spans.is_empty());
    }

    #[test]
    fn syntax_highlight_engine_cache_budget_rejects_unaccountable_entry() {
        let limits = SyntaxHighlightEngineLimits {
            cache_max_bytes: 1,
            ..SyntaxHighlightEngineLimits::default()
        };
        let mut engine = SyntaxHighlightEngine::new(limits).expect("engine");
        let target = request(1, "class A {}");
        engine
            .highlight(&target, &CancellationToken::new())
            .expect("first highlight");
        assert_eq!(engine.metrics().cache_entries, 0);
        assert_eq!(engine.metrics().cache_retained_bytes, 0);

        engine
            .highlight(&target, &CancellationToken::new())
            .expect("second highlight");
        assert_eq!(engine.metrics().parse_count, 2);
        assert_eq!(engine.metrics().cache_hits, 0);
        assert_eq!(engine.metrics().cache_entries, 0);
        assert_eq!(engine.metrics().cache_retained_bytes, 0);
    }

    #[test]
    fn syntax_highlight_engine_parser_cancellation_resets_for_reuse() {
        let mut engine =
            SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default()).expect("engine");
        let source = format!("class Slow {{ {} }}", "int value = 1;".repeat(10_000));
        let cancellation = CancellationToken::new();
        cancellation.request_cancel("superseded");
        assert!(
            engine
                .parse_java(&source, &cancellation)
                .expect("cancelled parse")
                .is_none()
        );

        let result = engine
            .highlight(&request(2, "class Reused {}"), &CancellationToken::new())
            .expect("parser remains reusable");
        assert_eq!(result.outcome, SyntaxHighlightOutcome::Highlighted);
    }
}
