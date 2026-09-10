use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeSet;

pub const SYNTAX_HIGHLIGHT_REQUEST_SCHEMA: &str = "sfm.syntax-highlight.request/1";
pub const SYNTAX_HIGHLIGHT_RESULT_SCHEMA: &str = "sfm.syntax-highlight.result/1";
pub const SYNTAX_HIGHLIGHT_FORMATTING_SCHEMA: &str = "minecraft.chat-formatting/1";
pub const SYNTAX_HIGHLIGHT_PARSER_FINGERPRINT: &str =
    "arborium-source-grammars/2.18.1+arborium-highlight/2.18.1+sfm-chat-formatting/2";
pub const DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES: usize = 4 * 1024 * 1024;
pub const DEFAULT_SYNTAX_HIGHLIGHT_MAX_SPANS: usize = 262_144;
pub const DEFAULT_SYNTAX_HIGHLIGHT_MAX_DIAGNOSTICS: usize = 256;

const SHA256_PREFIX: &str = "sha256:";
const SHA256_HEX_LENGTH: usize = 64;

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SyntaxHighlightRequest {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub origin_id: String,
    pub origin_generation: u64,
    pub language: String,
    pub source: String,
    pub source_sha256: String,
    pub maximum_spans: u64,
}

impl SyntaxHighlightRequest {
    #[must_use]
    pub fn new(
        request_id: u64,
        request_generation: u64,
        origin_id: impl Into<String>,
        origin_generation: u64,
        language: impl Into<String>,
        source: String,
        maximum_spans: usize,
    ) -> Self {
        let source_sha256 = syntax_highlight_sha256(&source);
        Self {
            schema: SYNTAX_HIGHLIGHT_REQUEST_SCHEMA.to_owned(),
            request_id,
            request_generation,
            origin_id: origin_id.into(),
            origin_generation,
            language: language.into(),
            source,
            source_sha256,
            maximum_spans: maximum_spans as u64,
        }
    }

    /// Validate identity, language, source, hash, and caller-provided bounds.
    ///
    /// # Errors
    ///
    /// Returns an error when this request cannot safely describe one immutable
    /// syntax snapshot.
    pub fn validate(&self, limits: SyntaxHighlightLimits) -> eyre::Result<()> {
        limits.validate()?;
        if self.schema != SYNTAX_HIGHLIGHT_REQUEST_SCHEMA {
            eyre::bail!(
                "syntax request schema `{}` does not match `{SYNTAX_HIGHLIGHT_REQUEST_SCHEMA}`",
                self.schema
            );
        }
        if self.request_id == 0 || self.request_generation == 0 || self.origin_generation == 0 {
            eyre::bail!("syntax request ids and generations must be positive");
        }
        if self.origin_id.trim().is_empty() || self.origin_id.len() > 256 {
            eyre::bail!("syntax request origin id must contain 1..=256 bytes");
        }
        if !valid_language_id(&self.language) {
            eyre::bail!(
                "syntax request language must contain 1..=64 lower-case ASCII id characters"
            );
        }
        if self.source.len() > limits.max_source_bytes {
            eyre::bail!(
                "syntax source contains {} bytes, exceeding maximum {}",
                self.source.len(),
                limits.max_source_bytes
            );
        }
        validate_sha256(&self.source_sha256)?;
        let actual_hash = syntax_highlight_sha256(&self.source);
        if self.source_sha256 != actual_hash {
            eyre::bail!(
                "syntax source hash `{}` does not match exact request text",
                self.source_sha256
            );
        }
        let maximum_spans = usize::try_from(self.maximum_spans).map_err(|error| {
            eyre::eyre!("syntax maximum span count is not representable: {error}")
        })?;
        if maximum_spans == 0 || maximum_spans > limits.max_spans {
            eyre::bail!(
                "syntax maximum span count must be within 1..={}",
                limits.max_spans
            );
        }
        Ok(())
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq)]
pub struct SyntaxHighlightLimits {
    pub max_source_bytes: usize,
    pub max_spans: usize,
    pub max_diagnostics: usize,
}

impl SyntaxHighlightLimits {
    /// Validate process-level hard limits before accepting requests.
    ///
    /// # Errors
    ///
    /// Returns an error when any limit is zero.
    pub fn validate(self) -> eyre::Result<()> {
        if self.max_source_bytes == 0 || self.max_spans == 0 || self.max_diagnostics == 0 {
            eyre::bail!("syntax highlight process limits must be positive");
        }
        Ok(())
    }
}

impl Default for SyntaxHighlightLimits {
    fn default() -> Self {
        Self {
            max_source_bytes: DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES,
            max_spans: DEFAULT_SYNTAX_HIGHLIGHT_MAX_SPANS,
            max_diagnostics: DEFAULT_SYNTAX_HIGHLIGHT_MAX_DIAGNOSTICS,
        }
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SyntaxHighlightOutcome {
    Highlighted,
    UnsupportedLanguage,
    InvalidRequest,
    Cancelled,
    Failed,
}

impl SyntaxHighlightOutcome {
    #[must_use]
    pub const fn exit_code(self) -> u8 {
        match self {
            Self::Highlighted => 0,
            Self::UnsupportedLanguage => 4,
            Self::InvalidRequest => 2,
            Self::Cancelled => 130,
            Self::Failed => 1,
        }
    }

    #[must_use]
    pub const fn is_complete(self) -> bool {
        matches!(
            self,
            Self::Highlighted | Self::UnsupportedLanguage | Self::InvalidRequest
        )
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SyntaxHighlightCacheStatus {
    Hit,
    Miss,
    Bypassed,
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SyntaxHighlightDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct SyntaxHighlightDiagnostic {
    pub code: String,
    pub severity: SyntaxHighlightDiagnosticSeverity,
    pub message: String,
    pub start_byte: Option<u64>,
    pub end_byte: Option<u64>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub struct SyntaxHighlightSpan {
    pub start_byte: u64,
    pub end_byte: u64,
    pub arborium_tag: String,
    pub chat_formatting: Vec<String>,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SyntaxHighlightCacheEvidence {
    pub status: SyntaxHighlightCacheStatus,
    pub entries: u64,
    pub retained_bytes: u64,
    pub hits: u64,
    pub misses: u64,
    pub evictions: u64,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub struct SyntaxHighlightResult {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub origin_id: String,
    pub origin_generation: u64,
    pub language: String,
    pub source_sha256: String,
    pub source_bytes: u64,
    pub outcome: SyntaxHighlightOutcome,
    pub complete: bool,
    pub parser_fingerprint: String,
    pub formatting_schema: String,
    pub elapsed_micros: u64,
    pub cache: SyntaxHighlightCacheEvidence,
    pub diagnostics: Vec<SyntaxHighlightDiagnostic>,
    pub spans: Vec<SyntaxHighlightSpan>,
}

impl SyntaxHighlightResult {
    #[must_use]
    pub fn terminal(
        request: &SyntaxHighlightRequest,
        outcome: SyntaxHighlightOutcome,
        diagnostics: Vec<SyntaxHighlightDiagnostic>,
    ) -> Self {
        Self {
            schema: SYNTAX_HIGHLIGHT_RESULT_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            origin_id: request.origin_id.clone(),
            origin_generation: request.origin_generation,
            language: request.language.clone(),
            source_sha256: request.source_sha256.clone(),
            source_bytes: request.source.len() as u64,
            outcome,
            complete: outcome.is_complete(),
            parser_fingerprint: SYNTAX_HIGHLIGHT_PARSER_FINGERPRINT.to_owned(),
            formatting_schema: SYNTAX_HIGHLIGHT_FORMATTING_SCHEMA.to_owned(),
            elapsed_micros: 0,
            cache: SyntaxHighlightCacheEvidence {
                status: SyntaxHighlightCacheStatus::Bypassed,
                entries: 0,
                retained_bytes: 0,
                hits: 0,
                misses: 0,
                evictions: 0,
            },
            diagnostics,
            spans: Vec::new(),
        }
    }

    #[must_use]
    pub const fn status(&self) -> u8 {
        self.outcome.exit_code()
    }

    /// Validate a result against the exact immutable request it answers.
    ///
    /// # Errors
    ///
    /// Returns an error for identity/schema/hash disagreement, malformed ranges,
    /// unknown formatting names, overlap, or violated response bounds.
    pub fn validate_against(
        &self,
        request: &SyntaxHighlightRequest,
        limits: SyntaxHighlightLimits,
    ) -> eyre::Result<()> {
        request.validate(limits)?;
        if self.schema != SYNTAX_HIGHLIGHT_RESULT_SCHEMA {
            eyre::bail!("syntax result has unsupported schema `{}`", self.schema);
        }
        if self.request_id != request.request_id
            || self.request_generation != request.request_generation
            || self.origin_id != request.origin_id
            || self.origin_generation != request.origin_generation
            || self.language != request.language
            || self.source_sha256 != request.source_sha256
            || self.source_bytes != request.source.len() as u64
        {
            eyre::bail!("syntax result identity does not match its request");
        }
        if self.complete != self.outcome.is_complete() {
            eyre::bail!("syntax result completeness disagrees with its outcome");
        }
        if self.parser_fingerprint != SYNTAX_HIGHLIGHT_PARSER_FINGERPRINT {
            eyre::bail!("syntax result parser fingerprint is unsupported");
        }
        if self.formatting_schema != SYNTAX_HIGHLIGHT_FORMATTING_SCHEMA {
            eyre::bail!("syntax result formatting schema is unsupported");
        }
        if self.diagnostics.len() > limits.max_diagnostics {
            eyre::bail!("syntax result diagnostic count exceeds process limit");
        }
        let request_span_limit = usize::try_from(request.maximum_spans)
            .map_err(|error| eyre::eyre!("request span count is not representable: {error}"))?;
        if self.spans.len() > request_span_limit || self.spans.len() > limits.max_spans {
            eyre::bail!("syntax result span count exceeds request or process limit");
        }
        validate_diagnostics(&self.diagnostics, &request.source)?;
        validate_spans(&self.spans, &request.source)?;
        if self.outcome != SyntaxHighlightOutcome::Highlighted && !self.spans.is_empty() {
            eyre::bail!("non-highlighted syntax outcome must not contain spans");
        }
        Ok(())
    }
}

#[must_use]
pub fn syntax_highlight_sha256(source: &str) -> String {
    let digest = Sha256::digest(source.as_bytes());
    format!("{SHA256_PREFIX}{digest:x}")
}

fn valid_language_id(language: &str) -> bool {
    !language.is_empty()
        && language.len() <= 64
        && language.bytes().all(|byte| {
            byte.is_ascii_lowercase()
                || byte.is_ascii_digit()
                || matches!(byte, b'-' | b'+' | b'.' | b'_')
        })
}

fn validate_sha256(hash: &str) -> eyre::Result<()> {
    let Some(hex) = hash.strip_prefix(SHA256_PREFIX) else {
        eyre::bail!("syntax source hash must begin with `{SHA256_PREFIX}`");
    };
    if hex.len() != SHA256_HEX_LENGTH || !hex.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        eyre::bail!("syntax source hash must contain 64 hexadecimal digits");
    }
    if hex.bytes().any(|byte| byte.is_ascii_uppercase()) {
        eyre::bail!("syntax source hash must use canonical lower-case hexadecimal");
    }
    Ok(())
}

fn validate_diagnostics(
    diagnostics: &[SyntaxHighlightDiagnostic],
    source: &str,
) -> eyre::Result<()> {
    for diagnostic in diagnostics {
        if diagnostic.code.trim().is_empty() || diagnostic.code.len() > 128 {
            eyre::bail!("syntax diagnostic code must contain 1..=128 bytes");
        }
        if diagnostic.message.len() > 4096 {
            eyre::bail!("syntax diagnostic message exceeds 4096 bytes");
        }
        match (diagnostic.start_byte, diagnostic.end_byte) {
            (None, None) => {}
            (Some(start), Some(end)) => validate_source_range(source, start, end)?,
            _ => eyre::bail!("syntax diagnostic range must provide both endpoints"),
        }
    }
    Ok(())
}

fn validate_spans(spans: &[SyntaxHighlightSpan], source: &str) -> eyre::Result<()> {
    let valid_formatting = valid_chat_formatting_names();
    let mut previous_end = 0_u64;
    for (index, span) in spans.iter().enumerate() {
        validate_source_range(source, span.start_byte, span.end_byte)?;
        if index != 0 && span.start_byte < previous_end {
            eyre::bail!("syntax spans overlap or are out of order at index {index}");
        }
        if span.arborium_tag.trim().is_empty() || span.arborium_tag.len() > 64 {
            eyre::bail!("syntax Arborium tag must contain 1..=64 bytes");
        }
        let mut seen = BTreeSet::new();
        for formatting in &span.chat_formatting {
            if !valid_formatting.contains(formatting.as_str()) {
                eyre::bail!("unknown ChatFormatting name `{formatting}`");
            }
            if !seen.insert(formatting.as_str()) {
                eyre::bail!("duplicate ChatFormatting name `{formatting}`");
            }
        }
        previous_end = span.end_byte;
    }
    Ok(())
}

fn validate_source_range(source: &str, start: u64, end: u64) -> eyre::Result<()> {
    let start = usize::try_from(start)
        .map_err(|error| eyre::eyre!("syntax range start is not representable: {error}"))?;
    let end = usize::try_from(end)
        .map_err(|error| eyre::eyre!("syntax range end is not representable: {error}"))?;
    if start >= end || end > source.len() {
        eyre::bail!("syntax range {start}..{end} is outside exact source text");
    }
    if !source.is_char_boundary(start) || !source.is_char_boundary(end) {
        eyre::bail!("syntax range {start}..{end} splits a UTF-8 scalar");
    }
    Ok(())
}

fn valid_chat_formatting_names() -> BTreeSet<&'static str> {
    [
        "black",
        "dark_blue",
        "dark_green",
        "dark_aqua",
        "dark_red",
        "dark_purple",
        "gold",
        "gray",
        "dark_gray",
        "blue",
        "green",
        "aqua",
        "red",
        "light_purple",
        "yellow",
        "white",
        "obfuscated",
        "bold",
        "strikethrough",
        "underline",
        "italic",
        "reset",
    ]
    .into_iter()
    .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn request(source: &str) -> SyntaxHighlightRequest {
        SyntaxHighlightRequest::new(1, 2, "editor:7", 3, "java", source.to_owned(), 100)
    }

    #[test]
    fn syntax_highlight_contract_round_trips_exact_source_and_identity() {
        let request = request("class Café { String value = \"🙂\"; }\r\n");
        request
            .validate(SyntaxHighlightLimits::default())
            .expect("request should validate");
        let json = facet_json::to_string_pretty(&request).expect("request JSON");
        let decoded: SyntaxHighlightRequest = facet_json::from_str(&json).expect("request decode");
        assert_eq!(decoded, request);
        assert!(request.source_sha256.starts_with("sha256:"));
    }

    #[test]
    fn syntax_highlight_contract_rejects_hash_bounds_and_utf8_splits() {
        let mut invalid_hash = request("class A {}");
        invalid_hash.source_sha256 = format!("sha256:{}", "0".repeat(64));
        let _ = invalid_hash
            .validate(SyntaxHighlightLimits::default())
            .expect_err("wrong exact-source hash must fail");

        let source = "class Café {}";
        let request = request(source);
        let mut result = SyntaxHighlightResult::terminal(
            &request,
            SyntaxHighlightOutcome::Highlighted,
            Vec::new(),
        );
        let accent = source.find('é').expect("accent byte");
        result.spans.push(SyntaxHighlightSpan {
            start_byte: (accent + 1) as u64,
            end_byte: (accent + 2) as u64,
            arborium_tag: "type".to_owned(),
            chat_formatting: vec!["aqua".to_owned()],
        });
        let _ = result
            .validate_against(&request, SyntaxHighlightLimits::default())
            .expect_err("UTF-8 split must fail");
    }

    #[test]
    fn syntax_highlight_contract_rejects_overlap_and_unknown_formatting() {
        let request = request("class A {}");
        let mut result = SyntaxHighlightResult::terminal(
            &request,
            SyntaxHighlightOutcome::Highlighted,
            Vec::new(),
        );
        result.spans = vec![
            SyntaxHighlightSpan {
                start_byte: 0,
                end_byte: 5,
                arborium_tag: "keyword".to_owned(),
                chat_formatting: vec!["light_purple".to_owned()],
            },
            SyntaxHighlightSpan {
                start_byte: 4,
                end_byte: 7,
                arborium_tag: "type".to_owned(),
                chat_formatting: vec!["chartreuse".to_owned()],
            },
        ];
        let _ = result
            .validate_against(&request, SyntaxHighlightLimits::default())
            .expect_err("overlap and unknown formatting must fail");
    }

    #[test]
    fn syntax_highlight_outcomes_have_stable_status_and_completeness() {
        assert_eq!(SyntaxHighlightOutcome::Highlighted.exit_code(), 0);
        assert!(SyntaxHighlightOutcome::UnsupportedLanguage.is_complete());
        assert!(!SyntaxHighlightOutcome::Cancelled.is_complete());
        assert_eq!(SyntaxHighlightOutcome::Cancelled.exit_code(), 130);
    }
}
