//! Bounded deterministic textual review surfaces backed by `gix` Histogram diff.

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
use crate::release_review_v1::SnapshotSideV1;
use crate::release_review_v1::Utf8RangeV1;

pub const TEXT_DIFF_ALGORITHM_V1: &str = "gix-histogram+sfm-source-map/1";

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct RawHunk {
    before_start: usize,
    before_count: usize,
    after_start: usize,
    after_count: usize,
}

#[derive(Clone, Debug)]
struct HunkGroup {
    before_start: usize,
    before_end: usize,
    after_start: usize,
    after_end: usize,
    hunks: Vec<RawHunk>,
}

#[derive(Clone, Copy, Debug)]
struct SourceLine {
    start: usize,
    end: usize,
}

impl SourceLine {
    const fn range(self) -> Utf8RangeV1 {
        Utf8RangeV1 {
            start_byte: self.start,
            end_byte: self.end,
        }
    }
}

#[derive(Debug)]
struct SurfaceLimitError {
    message: String,
}

struct TextSurfaceBuilder<'a> {
    request: &'a ReviewSurfaceRequestV1,
    text: String,
    mappings: Vec<ReviewSurfaceMappingV1>,
    regions: Vec<ReviewSurfaceRegionV1>,
}

impl<'a> TextSurfaceBuilder<'a> {
    fn new(request: &'a ReviewSurfaceRequestV1) -> Self {
        Self {
            request,
            text: String::new(),
            mappings: Vec::new(),
            regions: Vec::new(),
        }
    }

    fn push_unmapped(&mut self, text: &str) -> Result<Utf8RangeV1, SurfaceLimitError> {
        let start_byte = self.text.len();
        self.ensure_output_capacity(text.len())?;
        self.text.push_str(text);
        Ok(Utf8RangeV1 {
            start_byte,
            end_byte: self.text.len(),
        })
    }

    fn push_mapped(
        &mut self,
        prefix: &str,
        exact_text: &str,
        kind: ReviewSurfaceMappingKindV1,
        source_ranges: Vec<ReviewSurfaceSourceRangeV1>,
    ) -> Result<Utf8RangeV1, SurfaceLimitError> {
        self.push_unmapped(prefix)?;
        let surface_range = self.push_unmapped(exact_text)?;
        if !exact_text.is_empty() {
            if self.mappings.len() >= self.request.maximum_mappings {
                return Err(SurfaceLimitError {
                    message: format!(
                        "text diff requires more than {} exact source mappings",
                        self.request.maximum_mappings
                    ),
                });
            }
            self.mappings.push(ReviewSurfaceMappingV1 {
                surface_range: surface_range.clone(),
                kind,
                source_ranges,
            });
        }
        if !exact_text.ends_with('\n') {
            self.push_unmapped("\n")?;
        }
        Ok(surface_range)
    }

    fn push_region(&mut self, region: ReviewSurfaceRegionV1) -> Result<(), SurfaceLimitError> {
        if self.regions.len() >= self.request.maximum_regions {
            return Err(SurfaceLimitError {
                message: format!(
                    "text diff requires more than {} regions",
                    self.request.maximum_regions
                ),
            });
        }
        self.regions.push(region);
        Ok(())
    }

    fn ensure_output_capacity(&self, additional: usize) -> Result<(), SurfaceLimitError> {
        if self.text.len().saturating_add(additional) > self.request.maximum_output_bytes {
            return Err(SurfaceLimitError {
                message: format!(
                    "text diff exceeds requested {}-byte output bound",
                    self.request.maximum_output_bytes
                ),
            });
        }
        Ok(())
    }
}

/// Produce one complete text-diff surface without consulting the worktree.
///
/// # Errors
///
/// Returns an error only when the immutable request is malformed. Output-size
/// exhaustion is represented as a typed bounded result.
pub fn produce_text_diff(
    request: &ReviewSurfaceRequestV1,
    limits: ReviewSurfaceLimitsV1,
) -> eyre::Result<ReviewSurfaceV1> {
    request.validate(limits)?;
    if request.surface_kind != ReviewSurfaceKindV1::TextDiff {
        eyre::bail!("text-diff producer received a non-text surface request");
    }
    Ok(produce_validated_text_diff(request))
}

pub(crate) fn produce_validated_text_diff(request: &ReviewSurfaceRequestV1) -> ReviewSurfaceV1 {
    let before_text = request
        .file_pair
        .before
        .as_ref()
        .map_or("", |source| source.text.as_str());
    let after_text = request
        .file_pair
        .after
        .as_ref()
        .map_or("", |source| source.text.as_str());
    let before_lines = source_lines(before_text);
    let after_lines = source_lines(after_text);
    let hunks = histogram_hunks(before_text, after_text);
    if hunks.is_empty() {
        return unchanged_surface(request);
    }
    let groups = group_hunks(
        &hunks,
        before_lines.len(),
        after_lines.len(),
        request.context_lines,
    );
    match build_text_surface(request, &before_lines, &after_lines, &groups) {
        Ok(surface) => surface,
        Err(error) => limit_surface(request, error.message),
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "one pass emits ordered hunk text, exact mappings, regions, and correspondence evidence"
)]
fn build_text_surface(
    request: &ReviewSurfaceRequestV1,
    before_lines: &[SourceLine],
    after_lines: &[SourceLine],
    groups: &[HunkGroup],
) -> Result<ReviewSurfaceV1, SurfaceLimitError> {
    let mut builder = TextSurfaceBuilder::new(request);
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
    builder.push_unmapped(&format!("--- {before_path}\n+++ {after_path}\n"))?;
    let mut correspondence =
        ReviewCorrespondenceReportV1::empty(request.file_pair.id.clone(), true);

    for (group_index, group) in groups.iter().enumerate() {
        let region_start = builder.text.len();
        builder.push_unmapped(&format!(
            "@@ -{},{} +{},{} @@\n",
            display_line_start(group.before_start, group.before_end),
            group.before_end - group.before_start,
            display_line_start(group.after_start, group.after_end),
            group.after_end - group.after_start,
        ))?;
        let mut before_cursor = group.before_start;
        let mut after_cursor = group.after_start;
        let mut changed_before = Vec::new();
        let mut changed_after = Vec::new();

        for hunk in &group.hunks {
            emit_common_context(
                request,
                &mut builder,
                before_lines,
                after_lines,
                &mut before_cursor,
                &mut after_cursor,
                hunk.before_start,
                hunk.after_start,
            )?;
            for line in before_lines
                .iter()
                .copied()
                .skip(hunk.before_start)
                .take(hunk.before_count)
            {
                let range = line.range();
                changed_before.push(source_range(
                    &request.file_pair,
                    SnapshotSideV1::Before,
                    range.clone(),
                ));
                emit_source_line(
                    request,
                    &mut builder,
                    SnapshotSideV1::Before,
                    line,
                    "-",
                    ReviewSurfaceMappingKindV1::Deletion,
                    ReviewSurfaceRegionKindV1::Deletion,
                )?;
            }
            for line in after_lines
                .iter()
                .copied()
                .skip(hunk.after_start)
                .take(hunk.after_count)
            {
                let range = line.range();
                changed_after.push(source_range(
                    &request.file_pair,
                    SnapshotSideV1::After,
                    range.clone(),
                ));
                emit_source_line(
                    request,
                    &mut builder,
                    SnapshotSideV1::After,
                    line,
                    "+",
                    ReviewSurfaceMappingKindV1::Addition,
                    ReviewSurfaceRegionKindV1::Addition,
                )?;
            }
            before_cursor = hunk.before_start + hunk.before_count;
            after_cursor = hunk.after_start + hunk.after_count;
        }
        emit_common_context(
            request,
            &mut builder,
            before_lines,
            after_lines,
            &mut before_cursor,
            &mut after_cursor,
            group.before_end,
            group.after_end,
        )?;

        let region_end = builder.text.len();
        let mut group_sources = changed_before.clone();
        group_sources.extend(changed_after.clone());
        group_sources.sort();
        group_sources.dedup();
        builder.push_region(ReviewSurfaceRegionV1 {
            id: stable_review_surface_id(
                "text-hunk",
                &[
                    &request.file_pair.id,
                    &group_index.to_string(),
                    &group.before_start.to_string(),
                    &group.after_start.to_string(),
                ],
            ),
            kind: ReviewSurfaceRegionKindV1::TextHunk,
            label: format!("Text hunk {}", group_index + 1),
            surface_range: Utf8RangeV1 {
                start_byte: region_start,
                end_byte: region_end,
            },
            source_ranges: group_sources,
        })?;

        let correspondence_kind = match (changed_before.is_empty(), changed_after.is_empty()) {
            (true, false) => ReviewCorrespondenceKindV1::Added,
            (false, true) => ReviewCorrespondenceKindV1::Deleted,
            _ => ReviewCorrespondenceKindV1::Edited,
        };
        correspondence.correspondences.push(ReviewCorrespondenceV1 {
            id: stable_review_surface_id(
                "text-correspondence",
                &[
                    &request.file_pair.id,
                    &group_index.to_string(),
                    &group.before_start.to_string(),
                    &group.after_start.to_string(),
                ],
            ),
            kind: correspondence_kind,
            confidence: ReviewCorrespondenceConfidenceV1::Exact,
            semantic_key_before: None,
            semantic_key_after: None,
            before_ranges: changed_before,
            after_ranges: changed_after,
            evidence: vec![TEXT_DIFF_ALGORITHM_V1.to_owned()],
        });
    }

    let text_sha256 = review_surface_sha256(&builder.text);
    Ok(ReviewSurfaceV1 {
        schema: REVIEW_SURFACE_SCHEMA.to_owned(),
        request_id: request.request_id,
        request_generation: request.request_generation,
        file_pair_id: request.file_pair.id.clone(),
        surface_kind: request.surface_kind,
        algorithm: TEXT_DIFF_ALGORITHM_V1.to_owned(),
        outcome: ReviewSurfaceOutcomeV1::Produced,
        complete: true,
        fallback_kind: None,
        text: builder.text,
        text_sha256,
        mappings: builder.mappings,
        regions: builder.regions,
        correspondence,
        diagnostics: Vec::new(),
    })
}

#[expect(
    clippy::too_many_arguments,
    reason = "context emission advances paired before/after cursors explicitly"
)]
fn emit_common_context(
    request: &ReviewSurfaceRequestV1,
    builder: &mut TextSurfaceBuilder<'_>,
    before_lines: &[SourceLine],
    after_lines: &[SourceLine],
    before_cursor: &mut usize,
    after_cursor: &mut usize,
    before_target: usize,
    after_target: usize,
) -> Result<(), SurfaceLimitError> {
    let before_count = before_target.saturating_sub(*before_cursor);
    let after_count = after_target.saturating_sub(*after_cursor);
    let paired_count = before_count.min(after_count);
    for offset in 0..paired_count {
        let before = before_lines[*before_cursor + offset];
        let after = after_lines[*after_cursor + offset];
        let before_text = source_line_text(request, SnapshotSideV1::Before, before);
        let after_text = source_line_text(request, SnapshotSideV1::After, after);
        if before_text == after_text {
            let sources = vec![
                source_range(&request.file_pair, SnapshotSideV1::Before, before.range()),
                source_range(&request.file_pair, SnapshotSideV1::After, after.range()),
            ];
            let surface_range = builder.push_mapped(
                " ",
                before_text,
                ReviewSurfaceMappingKindV1::Context,
                sources.clone(),
            )?;
            builder.push_region(ReviewSurfaceRegionV1 {
                id: stable_review_surface_id(
                    "text-context",
                    &[
                        &request.file_pair.id,
                        &before.start.to_string(),
                        &after.start.to_string(),
                    ],
                ),
                kind: ReviewSurfaceRegionKindV1::Context,
                label: "Unchanged context".to_owned(),
                surface_range,
                source_ranges: sources,
            })?;
        } else {
            emit_source_line(
                request,
                builder,
                SnapshotSideV1::Before,
                before,
                "-",
                ReviewSurfaceMappingKindV1::Deletion,
                ReviewSurfaceRegionKindV1::Deletion,
            )?;
            emit_source_line(
                request,
                builder,
                SnapshotSideV1::After,
                after,
                "+",
                ReviewSurfaceMappingKindV1::Addition,
                ReviewSurfaceRegionKindV1::Addition,
            )?;
        }
    }
    for line in before_lines
        .iter()
        .copied()
        .skip(*before_cursor + paired_count)
        .take(before_count - paired_count)
    {
        emit_source_line(
            request,
            builder,
            SnapshotSideV1::Before,
            line,
            "-",
            ReviewSurfaceMappingKindV1::Deletion,
            ReviewSurfaceRegionKindV1::Deletion,
        )?;
    }
    for line in after_lines
        .iter()
        .copied()
        .skip(*after_cursor + paired_count)
        .take(after_count - paired_count)
    {
        emit_source_line(
            request,
            builder,
            SnapshotSideV1::After,
            line,
            "+",
            ReviewSurfaceMappingKindV1::Addition,
            ReviewSurfaceRegionKindV1::Addition,
        )?;
    }
    *before_cursor = before_target;
    *after_cursor = after_target;
    Ok(())
}

fn emit_source_line(
    request: &ReviewSurfaceRequestV1,
    builder: &mut TextSurfaceBuilder<'_>,
    side: SnapshotSideV1,
    line: SourceLine,
    prefix: &str,
    mapping_kind: ReviewSurfaceMappingKindV1,
    region_kind: ReviewSurfaceRegionKindV1,
) -> Result<(), SurfaceLimitError> {
    let source = source_range(&request.file_pair, side, line.range());
    let surface_range = builder.push_mapped(
        prefix,
        source_line_text(request, side, line),
        mapping_kind,
        vec![source.clone()],
    )?;
    builder.push_region(ReviewSurfaceRegionV1 {
        id: stable_review_surface_id(
            "text-line",
            &[
                &request.file_pair.id,
                match side {
                    SnapshotSideV1::Before => "before",
                    SnapshotSideV1::After => "after",
                },
                &line.start.to_string(),
                &line.end.to_string(),
            ],
        ),
        kind: region_kind,
        label: match side {
            SnapshotSideV1::Before => "Before source line",
            SnapshotSideV1::After => "After source line",
        }
        .to_owned(),
        surface_range,
        source_ranges: vec![source],
    })
}

fn source_line_text(
    request: &ReviewSurfaceRequestV1,
    side: SnapshotSideV1,
    line: SourceLine,
) -> &str {
    &request
        .file_pair
        .source(side)
        .expect("line side exists")
        .text[line.start..line.end]
}

fn unchanged_surface(request: &ReviewSurfaceRequestV1) -> ReviewSurfaceV1 {
    let text = "No textual differences.\n".to_owned();
    ReviewSurfaceV1 {
        schema: REVIEW_SURFACE_SCHEMA.to_owned(),
        request_id: request.request_id,
        request_generation: request.request_generation,
        file_pair_id: request.file_pair.id.clone(),
        surface_kind: request.surface_kind,
        algorithm: TEXT_DIFF_ALGORITHM_V1.to_owned(),
        outcome: ReviewSurfaceOutcomeV1::Unchanged,
        complete: true,
        fallback_kind: None,
        text_sha256: review_surface_sha256(&text),
        text,
        mappings: Vec::new(),
        regions: Vec::new(),
        correspondence: ReviewCorrespondenceReportV1::empty(request.file_pair.id.clone(), true),
        diagnostics: vec![ReviewSurfaceDiagnosticV1 {
            code: "review.text-diff.unchanged".to_owned(),
            severity: ReviewSurfaceDiagnosticSeverityV1::Info,
            message: "The pinned before and after UTF-8 sources are identical".to_owned(),
            side: None,
            source_range: None,
        }],
    }
}

fn limit_surface(request: &ReviewSurfaceRequestV1, message: String) -> ReviewSurfaceV1 {
    let preferred = "Text diff unavailable within configured bounds.\n";
    let text = if preferred.len() <= request.maximum_output_bytes {
        preferred.to_owned()
    } else {
        String::new()
    };
    ReviewSurfaceV1 {
        schema: REVIEW_SURFACE_SCHEMA.to_owned(),
        request_id: request.request_id,
        request_generation: request.request_generation,
        file_pair_id: request.file_pair.id.clone(),
        surface_kind: request.surface_kind,
        algorithm: TEXT_DIFF_ALGORITHM_V1.to_owned(),
        outcome: ReviewSurfaceOutcomeV1::LimitExceeded,
        complete: false,
        fallback_kind: None,
        text_sha256: review_surface_sha256(&text),
        text,
        mappings: Vec::new(),
        regions: Vec::new(),
        correspondence: ReviewCorrespondenceReportV1::empty(request.file_pair.id.clone(), false),
        diagnostics: vec![ReviewSurfaceDiagnosticV1 {
            code: "review.text-diff.limit-exceeded".to_owned(),
            severity: ReviewSurfaceDiagnosticSeverityV1::Error,
            message,
            side: None,
            source_range: None,
        }],
    }
}

fn histogram_hunks(before: &str, after: &str) -> Vec<RawHunk> {
    let input = gix::diff::blob::InternedInput::new(before, after);
    gix::diff::blob::diff_with_slider_heuristics(gix::diff::blob::Algorithm::Histogram, &input)
        .hunks()
        .map(|hunk| RawHunk {
            before_start: hunk.before.start as usize,
            before_count: hunk.before.len(),
            after_start: hunk.after.start as usize,
            after_count: hunk.after.len(),
        })
        .collect()
}

fn group_hunks(
    hunks: &[RawHunk],
    before_line_count: usize,
    after_line_count: usize,
    context: usize,
) -> Vec<HunkGroup> {
    let mut groups = Vec::<HunkGroup>::new();
    for hunk in hunks {
        let before_start = hunk.before_start.saturating_sub(context);
        let before_end = (hunk.before_start + hunk.before_count + context).min(before_line_count);
        let after_start = hunk.after_start.saturating_sub(context);
        let after_end = (hunk.after_start + hunk.after_count + context).min(after_line_count);
        if let Some(group) = groups.last_mut()
            && before_start <= group.before_end
            && after_start <= group.after_end
        {
            group.before_end = group.before_end.max(before_end);
            group.after_end = group.after_end.max(after_end);
            group.hunks.push(*hunk);
        } else {
            groups.push(HunkGroup {
                before_start,
                before_end,
                after_start,
                after_end,
                hunks: vec![*hunk],
            });
        }
    }
    groups
}

fn source_lines(text: &str) -> Vec<SourceLine> {
    if text.is_empty() {
        return Vec::new();
    }
    let mut lines = Vec::new();
    let mut start = 0;
    for (index, byte) in text.bytes().enumerate() {
        if byte == b'\n' {
            lines.push(SourceLine {
                start,
                end: index + 1,
            });
            start = index + 1;
        }
    }
    if start < text.len() {
        lines.push(SourceLine {
            start,
            end: text.len(),
        });
    }
    lines
}

const fn display_line_start(start: usize, end: usize) -> usize {
    if start == end { start } else { start + 1 }
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
            1,
            1,
            ReviewFilePairV1::new(
                "pair-text",
                "1.19.2",
                ChangeOperationV1::Modified,
                vec!["unit-text".to_owned()],
                Some(ReviewSurfaceSourceV1::new(
                    "before-text",
                    "src/A.java",
                    "java",
                    before,
                )),
                Some(ReviewSurfaceSourceV1::new(
                    "after-text",
                    "src/A.java",
                    "java",
                    after,
                )),
            ),
            ReviewSurfaceKindV1::TextDiff,
            limits,
        )
    }

    #[test]
    fn histogram_diff_preserves_crlf_unicode_and_bidirectional_maps() {
        let request = request(
            "class Caf\u{e9} {\r\n    int x = 1;\r\n}\r\n",
            "class Caf\u{e9} {\r\n    int x = 2;\r\n}\r\n",
        );
        let surface =
            produce_text_diff(&request, ReviewSurfaceLimitsV1::default()).expect("text diff");
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("exact source mappings");
        assert!(surface.text.contains("-    int x = 1;\r\n"));
        assert!(surface.text.contains("+    int x = 2;\r\n"));

        let addition = surface
            .mappings
            .iter()
            .find(|mapping| mapping.kind == ReviewSurfaceMappingKindV1::Addition)
            .expect("addition mapping");
        let projected = surface.source_ranges_for_surface_range(&addition.surface_range);
        assert_eq!(projected.len(), 1);
        assert_eq!(projected[0].side, SnapshotSideV1::After);
        assert_eq!(
            surface.surface_ranges_for_source_range(&projected[0]),
            vec![addition.surface_range.clone()]
        );
    }

    #[test]
    fn added_and_deleted_files_have_explicit_tombstone_headers() {
        let limits = ReviewSurfaceLimitsV1::default();
        let mut added = request("", "hello\n");
        added.file_pair.before = None;
        added.file_pair.operation = ChangeOperationV1::Added;
        let added_surface = produce_text_diff(&added, limits).expect("added diff");
        assert!(added_surface.text.starts_with("--- /dev/null\n"));
        added_surface
            .validate_against(&added, limits)
            .expect("added mappings");

        let mut deleted = request("goodbye\n", "");
        deleted.file_pair.after = None;
        deleted.file_pair.operation = ChangeOperationV1::Deleted;
        let deleted_surface = produce_text_diff(&deleted, limits).expect("deleted diff");
        assert!(deleted_surface.text.contains("+++ /dev/null\n"));
        deleted_surface
            .validate_against(&deleted, limits)
            .expect("deleted mappings");
    }

    #[test]
    fn repeated_generation_is_byte_for_byte_deterministic() {
        let request = request("a\nb\nc\n", "a\nB\nc\n");
        let first =
            produce_text_diff(&request, ReviewSurfaceLimitsV1::default()).expect("first surface");
        let second =
            produce_text_diff(&request, ReviewSurfaceLimitsV1::default()).expect("second surface");
        assert_eq!(first, second);
        let first_json = surface_to_canonical_json(&first).expect("first JSON");
        assert_eq!(
            first_json,
            surface_to_canonical_json(&second).expect("second JSON")
        );
        let decoded = parse_surface(&first_json).expect("surface JSON round trip");
        decoded
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("round-tripped mappings");
        assert_eq!(
            surface_to_canonical_json(&decoded).expect("rewritten JSON"),
            first_json
        );
    }

    #[test]
    fn renamed_file_headers_retain_both_pinned_paths() {
        let mut request = request("class Old {}\n", "class New {}\n");
        request.file_pair.operation = ChangeOperationV1::Renamed;
        request.file_pair.before.as_mut().expect("before").path = "src/Old.java".to_owned();
        request.file_pair.after.as_mut().expect("after").path = "src/New.java".to_owned();
        let surface =
            produce_text_diff(&request, ReviewSurfaceLimitsV1::default()).expect("rename diff");
        assert!(
            surface
                .text
                .starts_with("--- src/Old.java\n+++ src/New.java\n")
        );
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("renamed file mappings");
    }

    #[test]
    fn output_bound_returns_typed_result_without_partial_mappings() {
        let mut request = request("a\n", "b\n");
        request.maximum_output_bytes = 8;
        let surface = produce_text_diff(&request, ReviewSurfaceLimitsV1::default())
            .expect("typed bounded result");
        assert_eq!(surface.outcome, ReviewSurfaceOutcomeV1::LimitExceeded);
        assert!(!surface.complete);
        assert!(surface.mappings.is_empty());
        surface
            .validate_against(&request, ReviewSurfaceLimitsV1::default())
            .expect("bounded terminal result remains valid");
    }
}
