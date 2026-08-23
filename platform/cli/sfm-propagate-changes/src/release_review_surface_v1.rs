//! Versioned, transport-neutral contracts for source-backed release-review surfaces.
//!
//! Generated diff text is presentation, never durable review authority. Every
//! mapped byte range therefore points back to one or more immutable corpus
//! source ranges and validation proves that the displayed bytes are copied
//! exactly from those sources. Unmapped headers and diagnostics are explicit.

use crate::release_review_v1::ChangeOperationV1;
use crate::release_review_v1::SnapshotSideV1;
use crate::release_review_v1::Utf8RangeV1;
use eyre::Context as _;
use facet::Facet;
use sha2::Digest as _;
use sha2::Sha256;

pub const REVIEW_FILE_PAIR_SCHEMA: &str = "sfm.review-file-pair/1";
pub const REVIEW_SURFACE_REQUEST_SCHEMA: &str = "sfm.review-surface-request/1";
pub const REVIEW_SURFACE_SCHEMA: &str = "sfm.review-surface/1";
pub const REVIEW_CORRESPONDENCE_REPORT_SCHEMA: &str = "sfm.review-correspondence-report/1";

pub const DEFAULT_REVIEW_SURFACE_MAX_SOURCE_BYTES_PER_SIDE: usize = 4 * 1024 * 1024;
pub const DEFAULT_REVIEW_SURFACE_MAX_OUTPUT_BYTES: usize = 8 * 1024 * 1024;
pub const DEFAULT_REVIEW_SURFACE_MAX_MAPPINGS: usize = 262_144;
pub const DEFAULT_REVIEW_SURFACE_MAX_REGIONS: usize = 65_536;
pub const DEFAULT_REVIEW_SURFACE_MAX_DIAGNOSTICS: usize = 256;
pub const DEFAULT_REVIEW_SURFACE_CONTEXT_LINES: usize = 3;
pub const MAX_REVIEW_SURFACE_CONTEXT_LINES: usize = 64;

const SHA256_PREFIX: &str = "sha256:";
const SHA256_HEX_LENGTH: usize = 64;

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
pub struct ReviewSurfaceLimitsV1 {
    pub max_source_bytes_per_side: usize,
    pub max_output_bytes: usize,
    pub max_mappings: usize,
    pub max_regions: usize,
    pub max_diagnostics: usize,
}

impl ReviewSurfaceLimitsV1 {
    /// Validate process-level hard limits.
    ///
    /// # Errors
    ///
    /// Returns an error if any limit is zero.
    pub fn validate(self) -> eyre::Result<()> {
        if self.max_source_bytes_per_side == 0
            || self.max_output_bytes == 0
            || self.max_mappings == 0
            || self.max_regions == 0
            || self.max_diagnostics == 0
        {
            eyre::bail!("review-surface process limits must be positive");
        }
        Ok(())
    }
}

impl Default for ReviewSurfaceLimitsV1 {
    fn default() -> Self {
        Self {
            max_source_bytes_per_side: DEFAULT_REVIEW_SURFACE_MAX_SOURCE_BYTES_PER_SIDE,
            max_output_bytes: DEFAULT_REVIEW_SURFACE_MAX_OUTPUT_BYTES,
            max_mappings: DEFAULT_REVIEW_SURFACE_MAX_MAPPINGS,
            max_regions: DEFAULT_REVIEW_SURFACE_MAX_REGIONS,
            max_diagnostics: DEFAULT_REVIEW_SURFACE_MAX_DIAGNOSTICS,
        }
    }
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewSurfaceSourceV1 {
    pub document_revision_id: String,
    pub path: String,
    pub language: String,
    pub sha256: String,
    pub text: String,
}

impl ReviewSurfaceSourceV1 {
    #[must_use]
    pub fn new(
        document_revision_id: impl Into<String>,
        path: impl Into<String>,
        language: impl Into<String>,
        text: impl Into<String>,
    ) -> Self {
        let text = text.into();
        Self {
            document_revision_id: document_revision_id.into(),
            path: path.into(),
            language: language.into(),
            sha256: review_surface_sha256(&text),
            text,
        }
    }

    fn validate(&self, side: SnapshotSideV1, limits: ReviewSurfaceLimitsV1) -> eyre::Result<()> {
        require_bounded_text(&self.document_revision_id, 512, "document revision id")?;
        require_bounded_text(&self.path, 4096, "source path")?;
        require_bounded_text(&self.language, 64, "source language")?;
        if self.text.len() > limits.max_source_bytes_per_side {
            eyre::bail!(
                "{side:?} source contains {} bytes, exceeding maximum {}",
                self.text.len(),
                limits.max_source_bytes_per_side
            );
        }
        validate_sha256(&self.sha256)?;
        if self.sha256 != review_surface_sha256(&self.text) {
            eyre::bail!("{side:?} source hash does not match exact source text");
        }
        Ok(())
    }
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewFilePairV1 {
    pub schema: String,
    pub id: String,
    pub lane_id: String,
    pub operation: ChangeOperationV1,
    pub review_unit_ids: Vec<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub before: Option<ReviewSurfaceSourceV1>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub after: Option<ReviewSurfaceSourceV1>,
}

impl ReviewFilePairV1 {
    #[must_use]
    pub fn new(
        id: impl Into<String>,
        lane_id: impl Into<String>,
        operation: ChangeOperationV1,
        review_unit_ids: Vec<String>,
        before: Option<ReviewSurfaceSourceV1>,
        after: Option<ReviewSurfaceSourceV1>,
    ) -> Self {
        Self {
            schema: REVIEW_FILE_PAIR_SCHEMA.to_owned(),
            id: id.into(),
            lane_id: lane_id.into(),
            operation,
            review_unit_ids,
            before,
            after,
        }
    }

    fn validate(&self, limits: ReviewSurfaceLimitsV1) -> eyre::Result<()> {
        if self.schema != REVIEW_FILE_PAIR_SCHEMA {
            eyre::bail!("unsupported review file-pair schema `{}`", self.schema);
        }
        require_bounded_text(&self.id, 512, "file-pair id")?;
        require_bounded_text(&self.lane_id, 512, "file-pair lane id")?;
        if self.review_unit_ids.is_empty() || self.review_unit_ids.len() > limits.max_regions {
            eyre::bail!("file-pair review-unit count exceeds process limit");
        }
        if self.review_unit_ids.iter().any(|id| id.trim().is_empty()) {
            eyre::bail!("file-pair review-unit ids must not be empty");
        }
        if !is_strictly_sorted_unique(&self.review_unit_ids) {
            eyre::bail!("file-pair review-unit ids must be sorted and unique");
        }
        if self.before.is_none() && self.after.is_none() {
            eyre::bail!("review file-pair must contain at least one source side");
        }
        match self.operation {
            ChangeOperationV1::Added if self.before.is_some() || self.after.is_none() => {
                eyre::bail!("added file-pair must have only an after source")
            }
            ChangeOperationV1::Deleted if self.before.is_none() || self.after.is_some() => {
                eyre::bail!("deleted file-pair must have only a before source")
            }
            ChangeOperationV1::Modified
            | ChangeOperationV1::Renamed
            | ChangeOperationV1::Copied
            | ChangeOperationV1::TypeChanged
                if self.before.is_none() || self.after.is_none() =>
            {
                eyre::bail!("non-add/delete file-pair must have before and after sources")
            }
            _ => {}
        }
        if let Some(before) = &self.before {
            before.validate(SnapshotSideV1::Before, limits)?;
        }
        if let Some(after) = &self.after {
            after.validate(SnapshotSideV1::After, limits)?;
        }
        Ok(())
    }

    #[must_use]
    pub const fn source(&self, side: SnapshotSideV1) -> Option<&ReviewSurfaceSourceV1> {
        match side {
            SnapshotSideV1::Before => self.before.as_ref(),
            SnapshotSideV1::After => self.after.as_ref(),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ReviewSurfaceKindV1 {
    TextDiff,
    JavaStructuredDiff,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewSurfaceRequestV1 {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub file_pair: ReviewFilePairV1,
    pub surface_kind: ReviewSurfaceKindV1,
    pub context_lines: usize,
    pub maximum_output_bytes: usize,
    pub maximum_mappings: usize,
    pub maximum_regions: usize,
    pub maximum_diagnostics: usize,
}

impl ReviewSurfaceRequestV1 {
    #[must_use]
    pub fn new(
        request_id: u64,
        request_generation: u64,
        file_pair: ReviewFilePairV1,
        surface_kind: ReviewSurfaceKindV1,
        limits: ReviewSurfaceLimitsV1,
    ) -> Self {
        Self {
            schema: REVIEW_SURFACE_REQUEST_SCHEMA.to_owned(),
            request_id,
            request_generation,
            file_pair,
            surface_kind,
            context_lines: DEFAULT_REVIEW_SURFACE_CONTEXT_LINES,
            maximum_output_bytes: limits.max_output_bytes,
            maximum_mappings: limits.max_mappings,
            maximum_regions: limits.max_regions,
            maximum_diagnostics: limits.max_diagnostics,
        }
    }

    /// Validate immutable identity, source hashes, and caller-provided bounds.
    ///
    /// # Errors
    ///
    /// Returns an error when the request is malformed or exceeds process limits.
    pub fn validate(&self, limits: ReviewSurfaceLimitsV1) -> eyre::Result<()> {
        limits.validate()?;
        if self.schema != REVIEW_SURFACE_REQUEST_SCHEMA {
            eyre::bail!(
                "unsupported review-surface request schema `{}`",
                self.schema
            );
        }
        if self.request_id == 0 || self.request_generation == 0 {
            eyre::bail!("review-surface request identity and generation must be positive");
        }
        self.file_pair.validate(limits)?;
        if self.context_lines > MAX_REVIEW_SURFACE_CONTEXT_LINES {
            eyre::bail!(
                "review-surface context lines exceed maximum {MAX_REVIEW_SURFACE_CONTEXT_LINES}"
            );
        }
        validate_requested_limit(
            self.maximum_output_bytes,
            limits.max_output_bytes,
            "output bytes",
        )?;
        validate_requested_limit(self.maximum_mappings, limits.max_mappings, "mappings")?;
        validate_requested_limit(self.maximum_regions, limits.max_regions, "regions")?;
        validate_requested_limit(
            self.maximum_diagnostics,
            limits.max_diagnostics,
            "diagnostics",
        )?;
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ReviewSurfaceOutcomeV1 {
    Produced,
    Unchanged,
    Fallback,
    Unsupported,
    LimitExceeded,
}

impl ReviewSurfaceOutcomeV1 {
    #[must_use]
    pub const fn is_complete(self) -> bool {
        matches!(self, Self::Produced | Self::Unchanged | Self::Fallback)
    }
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ReviewSurfaceDiagnosticSeverityV1 {
    Info,
    Warning,
    Error,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct ReviewSurfaceDiagnosticV1 {
    pub code: String,
    pub severity: ReviewSurfaceDiagnosticSeverityV1,
    pub message: String,
    #[facet(skip_serializing_if = Option::is_none)]
    pub side: Option<SnapshotSideV1>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub source_range: Option<Utf8RangeV1>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ReviewSurfaceMappingKindV1 {
    Context,
    Addition,
    Deletion,
    StructuralBefore,
    StructuralAfter,
    StructuralCorrespondence,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct ReviewSurfaceSourceRangeV1 {
    pub side: SnapshotSideV1,
    pub document_revision_id: String,
    pub document_sha256: String,
    pub path: String,
    pub range: Utf8RangeV1,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct ReviewSurfaceMappingV1 {
    pub surface_range: Utf8RangeV1,
    pub kind: ReviewSurfaceMappingKindV1,
    pub source_ranges: Vec<ReviewSurfaceSourceRangeV1>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ReviewSurfaceRegionKindV1 {
    TextHunk,
    Addition,
    Deletion,
    Context,
    JavaDeclaration,
    JavaImport,
    Fallback,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct ReviewSurfaceRegionV1 {
    pub id: String,
    pub kind: ReviewSurfaceRegionKindV1,
    pub label: String,
    pub surface_range: Utf8RangeV1,
    pub source_ranges: Vec<ReviewSurfaceSourceRangeV1>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ReviewCorrespondenceKindV1 {
    Unchanged,
    Edited,
    Added,
    Deleted,
    Moved,
    Renamed,
    MovedAndRenamed,
    FormattingOnly,
    Ambiguous,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ReviewCorrespondenceConfidenceV1 {
    Exact,
    Structural,
    Conservative,
    Unavailable,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct ReviewCorrespondenceV1 {
    pub id: String,
    pub kind: ReviewCorrespondenceKindV1,
    pub confidence: ReviewCorrespondenceConfidenceV1,
    #[facet(skip_serializing_if = Option::is_none)]
    pub semantic_key_before: Option<String>,
    #[facet(skip_serializing_if = Option::is_none)]
    pub semantic_key_after: Option<String>,
    pub before_ranges: Vec<ReviewSurfaceSourceRangeV1>,
    pub after_ranges: Vec<ReviewSurfaceSourceRangeV1>,
    pub evidence: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewCorrespondenceReportV1 {
    pub schema: String,
    pub file_pair_id: String,
    pub complete: bool,
    pub correspondences: Vec<ReviewCorrespondenceV1>,
    pub diagnostics: Vec<ReviewSurfaceDiagnosticV1>,
}

impl ReviewCorrespondenceReportV1 {
    #[must_use]
    pub fn empty(file_pair_id: impl Into<String>, complete: bool) -> Self {
        Self {
            schema: REVIEW_CORRESPONDENCE_REPORT_SCHEMA.to_owned(),
            file_pair_id: file_pair_id.into(),
            complete,
            correspondences: Vec::new(),
            diagnostics: Vec::new(),
        }
    }
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewSurfaceV1 {
    pub schema: String,
    pub request_id: u64,
    pub request_generation: u64,
    pub file_pair_id: String,
    pub surface_kind: ReviewSurfaceKindV1,
    pub algorithm: String,
    pub outcome: ReviewSurfaceOutcomeV1,
    pub complete: bool,
    #[facet(skip_serializing_if = Option::is_none)]
    pub fallback_kind: Option<ReviewSurfaceKindV1>,
    pub text: String,
    pub text_sha256: String,
    pub mappings: Vec<ReviewSurfaceMappingV1>,
    pub regions: Vec<ReviewSurfaceRegionV1>,
    pub correspondence: ReviewCorrespondenceReportV1,
    pub diagnostics: Vec<ReviewSurfaceDiagnosticV1>,
}

impl ReviewSurfaceV1 {
    /// Validate a generated surface against its exact immutable request.
    ///
    /// # Errors
    ///
    /// Returns an error for schema/identity disagreement, violated bounds,
    /// malformed UTF-8 ranges, or any mapping whose bytes differ from source.
    pub fn validate_against(
        &self,
        request: &ReviewSurfaceRequestV1,
        limits: ReviewSurfaceLimitsV1,
    ) -> eyre::Result<()> {
        request.validate(limits)?;
        if self.schema != REVIEW_SURFACE_SCHEMA {
            eyre::bail!("unsupported review-surface schema `{}`", self.schema);
        }
        if self.request_id != request.request_id
            || self.request_generation != request.request_generation
            || self.file_pair_id != request.file_pair.id
            || self.surface_kind != request.surface_kind
        {
            eyre::bail!("review-surface identity does not match its request");
        }
        require_bounded_text(&self.algorithm, 512, "surface algorithm")?;
        if self.complete != self.outcome.is_complete() {
            eyre::bail!("review-surface completeness disagrees with its outcome");
        }
        if self.outcome == ReviewSurfaceOutcomeV1::Fallback && self.fallback_kind.is_none() {
            eyre::bail!("fallback surface must identify its fallback kind");
        }
        if self.outcome != ReviewSurfaceOutcomeV1::Fallback && self.fallback_kind.is_some() {
            eyre::bail!("non-fallback surface must not identify a fallback kind");
        }
        if self.text.len() > request.maximum_output_bytes
            || self.text.len() > limits.max_output_bytes
        {
            eyre::bail!("review-surface text exceeds request or process limit");
        }
        validate_sha256(&self.text_sha256)?;
        if self.text_sha256 != review_surface_sha256(&self.text) {
            eyre::bail!("review-surface text hash does not match exact text");
        }
        if self.mappings.len() > request.maximum_mappings
            || self.mappings.len() > limits.max_mappings
        {
            eyre::bail!("review-surface mapping count exceeds request or process limit");
        }
        if self.regions.len() > request.maximum_regions || self.regions.len() > limits.max_regions {
            eyre::bail!("review-surface region count exceeds request or process limit");
        }
        if self.diagnostics.len() > request.maximum_diagnostics
            || self.diagnostics.len() > limits.max_diagnostics
            || self.correspondence.diagnostics.len() > request.maximum_diagnostics
            || self.correspondence.diagnostics.len() > limits.max_diagnostics
        {
            eyre::bail!("review-surface diagnostic count exceeds request or process limit");
        }
        validate_diagnostics(&self.diagnostics, &request.file_pair)?;
        validate_diagnostics(&self.correspondence.diagnostics, &request.file_pair)?;
        validate_mappings(&self.mappings, &self.text, &request.file_pair)?;
        validate_regions(&self.regions, &self.text, &request.file_pair)?;
        validate_correspondence(&self.correspondence, &request.file_pair)?;
        Ok(())
    }

    /// Project one half-open generated-surface range back to exact pinned sources.
    #[must_use]
    pub fn source_ranges_for_surface_range(
        &self,
        range: &Utf8RangeV1,
    ) -> Vec<ReviewSurfaceSourceRangeV1> {
        let mut projected = Vec::new();
        for mapping in &self.mappings {
            let Some(overlap) = intersection(&mapping.surface_range, range) else {
                continue;
            };
            let offset = overlap.start_byte - mapping.surface_range.start_byte;
            let length = overlap.end_byte - overlap.start_byte;
            for source in &mapping.source_ranges {
                let mut source = source.clone();
                source.range = Utf8RangeV1 {
                    start_byte: source.range.start_byte + offset,
                    end_byte: source.range.start_byte + offset + length,
                };
                projected.push(source);
            }
        }
        projected.sort();
        projected.dedup();
        projected
    }

    /// Project one exact pinned-source range into generated-surface ranges.
    #[must_use]
    pub fn surface_ranges_for_source_range(
        &self,
        source_range: &ReviewSurfaceSourceRangeV1,
    ) -> Vec<Utf8RangeV1> {
        let mut projected = Vec::new();
        for mapping in &self.mappings {
            for source in &mapping.source_ranges {
                if source.side != source_range.side
                    || source.document_revision_id != source_range.document_revision_id
                    || source.document_sha256 != source_range.document_sha256
                    || source.path != source_range.path
                {
                    continue;
                }
                let Some(overlap) = intersection(&source.range, &source_range.range) else {
                    continue;
                };
                let offset = overlap.start_byte - source.range.start_byte;
                let length = overlap.end_byte - overlap.start_byte;
                projected.push(Utf8RangeV1 {
                    start_byte: mapping.surface_range.start_byte + offset,
                    end_byte: mapping.surface_range.start_byte + offset + length,
                });
            }
        }
        projected.sort();
        projected.dedup();
        projected
    }
}

#[must_use]
pub fn review_surface_sha256(text: &str) -> String {
    format!("{SHA256_PREFIX}{:x}", Sha256::digest(text.as_bytes()))
}

/// Parse one strict request document.
///
/// # Errors
///
/// Returns an error when JSON cannot be decoded. Call `validate` separately
/// with the process limits that apply to the transport.
pub fn parse_request(input: &str) -> eyre::Result<ReviewSurfaceRequestV1> {
    facet_json::from_str(input).wrap_err("could not parse review-surface request v1")
}

/// Parse one strict result document.
///
/// # Errors
///
/// Returns an error when JSON cannot be decoded. Call `validate_against`
/// separately with the immutable request and process limits.
pub fn parse_surface(input: &str) -> eyre::Result<ReviewSurfaceV1> {
    facet_json::from_str(input).wrap_err("could not parse review-surface v1")
}

/// Serialize a request deterministically.
///
/// # Errors
///
/// Returns an error when Facet JSON serialization fails.
pub fn request_to_canonical_json(request: &ReviewSurfaceRequestV1) -> eyre::Result<String> {
    let mut request = request.clone();
    request.file_pair.review_unit_ids.sort();
    request.file_pair.review_unit_ids.dedup();
    pretty_json(&request, "review-surface request v1")
}

/// Serialize a surface deterministically after sorting all set-like evidence.
///
/// # Errors
///
/// Returns an error when Facet JSON serialization fails.
pub fn surface_to_canonical_json(surface: &ReviewSurfaceV1) -> eyre::Result<String> {
    let mut surface = surface.clone();
    canonicalize_surface(&mut surface);
    pretty_json(&surface, "review-surface v1")
}

fn pretty_json<T: Facet<'static>>(value: &T, label: &str) -> eyre::Result<String> {
    let mut output = facet_json::to_string_pretty(value)
        .wrap_err_with(|| format!("could not serialize {label}"))?;
    output.push('\n');
    Ok(output)
}

fn canonicalize_surface(surface: &mut ReviewSurfaceV1) {
    for mapping in &mut surface.mappings {
        mapping.source_ranges.sort();
        mapping.source_ranges.dedup();
    }
    surface.mappings.sort();
    surface.mappings.dedup();
    for region in &mut surface.regions {
        region.source_ranges.sort();
        region.source_ranges.dedup();
    }
    surface.regions.sort();
    surface.regions.dedup();
    canonicalize_correspondence(&mut surface.correspondence);
    surface.diagnostics.sort();
    surface.diagnostics.dedup();
}

fn canonicalize_correspondence(report: &mut ReviewCorrespondenceReportV1) {
    for correspondence in &mut report.correspondences {
        correspondence.before_ranges.sort();
        correspondence.before_ranges.dedup();
        correspondence.after_ranges.sort();
        correspondence.after_ranges.dedup();
        correspondence.evidence.sort();
        correspondence.evidence.dedup();
    }
    report.correspondences.sort();
    report.correspondences.dedup();
    report.diagnostics.sort();
    report.diagnostics.dedup();
}

fn validate_requested_limit(requested: usize, maximum: usize, label: &str) -> eyre::Result<()> {
    if requested == 0 || requested > maximum {
        eyre::bail!("review-surface requested {label} must be within 1..={maximum}");
    }
    Ok(())
}

fn validate_sha256(hash: &str) -> eyre::Result<()> {
    let Some(hex) = hash.strip_prefix(SHA256_PREFIX) else {
        eyre::bail!("review-surface hash must begin with `{SHA256_PREFIX}`");
    };
    if hex.len() != SHA256_HEX_LENGTH
        || !hex.bytes().all(|byte| byte.is_ascii_hexdigit())
        || hex.bytes().any(|byte| byte.is_ascii_uppercase())
    {
        eyre::bail!("review-surface hash must contain 64 lower-case hexadecimal digits");
    }
    Ok(())
}

fn validate_diagnostics(
    diagnostics: &[ReviewSurfaceDiagnosticV1],
    file_pair: &ReviewFilePairV1,
) -> eyre::Result<()> {
    for diagnostic in diagnostics {
        require_bounded_text(&diagnostic.code, 128, "diagnostic code")?;
        if diagnostic.message.len() > 4096 {
            eyre::bail!("review-surface diagnostic message exceeds 4096 bytes");
        }
        match (diagnostic.side, &diagnostic.source_range) {
            (None, None) => {}
            (Some(side), Some(range)) => {
                let source = file_pair
                    .source(side)
                    .ok_or_else(|| eyre::eyre!("diagnostic references absent {side:?} side"))?;
                validate_text_range(&source.text, range)?;
            }
            _ => eyre::bail!("diagnostic source range must include side and both endpoints"),
        }
    }
    Ok(())
}

fn validate_mappings(
    mappings: &[ReviewSurfaceMappingV1],
    surface_text: &str,
    file_pair: &ReviewFilePairV1,
) -> eyre::Result<()> {
    let mut previous_end = 0;
    for mapping in mappings {
        validate_text_range(surface_text, &mapping.surface_range)?;
        if mapping.surface_range.start_byte == mapping.surface_range.end_byte {
            eyre::bail!("review-surface mappings must be non-empty");
        }
        if mapping.surface_range.start_byte < previous_end {
            eyre::bail!("review-surface mappings must be ordered and non-overlapping");
        }
        if mapping.source_ranges.is_empty() || !is_strictly_sorted_unique(&mapping.source_ranges) {
            eyre::bail!("mapping source ranges must be non-empty, sorted, and unique");
        }
        let displayed =
            &surface_text[mapping.surface_range.start_byte..mapping.surface_range.end_byte];
        for source_range in &mapping.source_ranges {
            let source = validate_source_range(source_range, file_pair)?;
            let source_text =
                &source.text[source_range.range.start_byte..source_range.range.end_byte];
            if source_text != displayed {
                eyre::bail!(
                    "mapped surface bytes differ from exact {:?} source bytes",
                    source_range.side
                );
            }
        }
        previous_end = mapping.surface_range.end_byte;
    }
    Ok(())
}

fn validate_regions(
    regions: &[ReviewSurfaceRegionV1],
    surface_text: &str,
    file_pair: &ReviewFilePairV1,
) -> eyre::Result<()> {
    for region in regions {
        require_bounded_text(&region.id, 512, "region id")?;
        require_bounded_text(&region.label, 4096, "region label")?;
        validate_text_range(surface_text, &region.surface_range)?;
        for source in &region.source_ranges {
            validate_source_range(source, file_pair)?;
        }
    }
    Ok(())
}

fn validate_correspondence(
    report: &ReviewCorrespondenceReportV1,
    file_pair: &ReviewFilePairV1,
) -> eyre::Result<()> {
    if report.schema != REVIEW_CORRESPONDENCE_REPORT_SCHEMA || report.file_pair_id != file_pair.id {
        eyre::bail!("review correspondence identity does not match file-pair");
    }
    for correspondence in &report.correspondences {
        require_bounded_text(&correspondence.id, 512, "correspondence id")?;
        for range in correspondence
            .before_ranges
            .iter()
            .chain(&correspondence.after_ranges)
        {
            validate_source_range(range, file_pair)?;
        }
        if correspondence
            .before_ranges
            .iter()
            .any(|range| range.side != SnapshotSideV1::Before)
            || correspondence
                .after_ranges
                .iter()
                .any(|range| range.side != SnapshotSideV1::After)
        {
            eyre::bail!("correspondence ranges are stored under the wrong side");
        }
    }
    Ok(())
}

fn validate_source_range<'a>(
    range: &ReviewSurfaceSourceRangeV1,
    file_pair: &'a ReviewFilePairV1,
) -> eyre::Result<&'a ReviewSurfaceSourceV1> {
    let source = file_pair
        .source(range.side)
        .ok_or_else(|| eyre::eyre!("source mapping references absent {:?} side", range.side))?;
    if range.document_revision_id != source.document_revision_id
        || range.document_sha256 != source.sha256
        || range.path != source.path
    {
        eyre::bail!("source mapping identity does not match immutable file-pair source");
    }
    validate_text_range(&source.text, &range.range)?;
    Ok(source)
}

fn validate_text_range(text: &str, range: &Utf8RangeV1) -> eyre::Result<()> {
    if range.start_byte > range.end_byte
        || range.end_byte > text.len()
        || !text.is_char_boundary(range.start_byte)
        || !text.is_char_boundary(range.end_byte)
    {
        eyre::bail!("review-surface range is outside UTF-8 character boundaries");
    }
    Ok(())
}

fn require_bounded_text(value: &str, maximum: usize, label: &str) -> eyre::Result<()> {
    if value.trim().is_empty() || value.len() > maximum {
        eyre::bail!("{label} must contain 1..={maximum} bytes");
    }
    Ok(())
}

fn is_strictly_sorted_unique<T: Ord>(values: &[T]) -> bool {
    values.windows(2).all(|window| window[0] < window[1])
}

fn intersection(left: &Utf8RangeV1, right: &Utf8RangeV1) -> Option<Utf8RangeV1> {
    let start_byte = left.start_byte.max(right.start_byte);
    let end_byte = left.end_byte.min(right.end_byte);
    (start_byte < end_byte).then_some(Utf8RangeV1 {
        start_byte,
        end_byte,
    })
}

#[must_use]
pub(crate) fn source_range(
    file_pair: &ReviewFilePairV1,
    side: SnapshotSideV1,
    range: Utf8RangeV1,
) -> ReviewSurfaceSourceRangeV1 {
    let source = file_pair
        .source(side)
        .expect("producer only creates ranges for present source sides");
    ReviewSurfaceSourceRangeV1 {
        side,
        document_revision_id: source.document_revision_id.clone(),
        document_sha256: source.sha256.clone(),
        path: source.path.clone(),
        range,
    }
}

#[must_use]
pub(crate) fn stable_review_surface_id(prefix: &str, fields: &[&str]) -> String {
    let mut hasher = Sha256::new();
    for field in fields {
        hasher.update(field.len().to_le_bytes());
        hasher.update(field.as_bytes());
    }
    format!("{prefix}-{:x}", hasher.finalize())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn request() -> ReviewSurfaceRequestV1 {
        let limits = ReviewSurfaceLimitsV1::default();
        ReviewSurfaceRequestV1::new(
            7,
            3,
            ReviewFilePairV1::new(
                "pair-1",
                "1.19.2",
                ChangeOperationV1::Modified,
                vec!["unit-1".to_owned()],
                Some(ReviewSurfaceSourceV1::new(
                    "before-1",
                    "src/A.java",
                    "java",
                    "caf\u{e9}\r\n",
                )),
                Some(ReviewSurfaceSourceV1::new(
                    "after-1",
                    "src/A.java",
                    "java",
                    "caf\u{e9}!\r\n",
                )),
            ),
            ReviewSurfaceKindV1::TextDiff,
            limits,
        )
    }

    fn mapped_surface(request: &ReviewSurfaceRequestV1) -> ReviewSurfaceV1 {
        let copied = "caf\u{e9}!";
        let start = "+++ src/A.java\n+".len();
        ReviewSurfaceV1 {
            schema: REVIEW_SURFACE_SCHEMA.to_owned(),
            request_id: request.request_id,
            request_generation: request.request_generation,
            file_pair_id: request.file_pair.id.clone(),
            surface_kind: request.surface_kind,
            algorithm: "test/1".to_owned(),
            outcome: ReviewSurfaceOutcomeV1::Produced,
            complete: true,
            fallback_kind: None,
            text: format!("+++ src/A.java\n+{copied}"),
            text_sha256: review_surface_sha256(&format!("+++ src/A.java\n+{copied}")),
            mappings: vec![ReviewSurfaceMappingV1 {
                surface_range: Utf8RangeV1 {
                    start_byte: start,
                    end_byte: start + copied.len(),
                },
                kind: ReviewSurfaceMappingKindV1::Addition,
                source_ranges: vec![source_range(
                    &request.file_pair,
                    SnapshotSideV1::After,
                    Utf8RangeV1 {
                        start_byte: 0,
                        end_byte: copied.len(),
                    },
                )],
            }],
            regions: Vec::new(),
            correspondence: ReviewCorrespondenceReportV1::empty(request.file_pair.id.clone(), true),
            diagnostics: Vec::new(),
        }
    }

    #[test]
    fn request_and_surface_round_trip_with_stable_json() {
        let request = request();
        request
            .validate(ReviewSurfaceLimitsV1::default())
            .expect("valid request");
        let request_json = request_to_canonical_json(&request).expect("request JSON");
        let reparsed_request = parse_request(&request_json).expect("request round trip");
        assert_eq!(reparsed_request, request);
        assert_eq!(
            request_to_canonical_json(&reparsed_request).expect("request rewrite"),
            request_json
        );

        let surface = mapped_surface(&request);
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("valid surface");
        let surface_json = surface_to_canonical_json(&surface).expect("surface JSON");
        let reparsed_surface = parse_surface(&surface_json).expect("surface round trip");
        assert_eq!(reparsed_surface, surface);
        assert_eq!(
            surface_to_canonical_json(&reparsed_surface).expect("surface rewrite"),
            surface_json
        );
    }

    #[test]
    fn mappings_are_bidirectional_across_unicode_bytes() {
        let request = request();
        let surface = mapped_surface(&request);
        let mapped = &surface.mappings[0];
        let selected_surface = Utf8RangeV1 {
            start_byte: mapped.surface_range.start_byte + 3,
            end_byte: mapped.surface_range.end_byte,
        };
        let sources = surface.source_ranges_for_surface_range(&selected_surface);
        assert_eq!(sources.len(), 1);
        assert_eq!(
            &request.file_pair.after.as_ref().expect("after").text
                [sources[0].range.start_byte..sources[0].range.end_byte],
            "\u{e9}!"
        );
        assert_eq!(
            surface.surface_ranges_for_source_range(&sources[0]),
            vec![selected_surface]
        );
    }

    #[test]
    fn validation_rejects_mapping_that_invents_bytes() {
        let request = request();
        let mut surface = mapped_surface(&request);
        surface.text = surface.text.replace("caf\u{e9}!", "xxxxx!");
        surface.text_sha256 = review_surface_sha256(&surface.text);
        let error = surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect_err("invented mapped bytes must fail");
        assert!(error.to_string().contains("differ from exact"));
    }
}
