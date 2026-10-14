//! Compact changed-line navigation reuses the existing bounded Histogram producer.
use crate::release_review_surface_v1::ReviewFilePairV1;
use crate::release_review_surface_v1::ReviewSurfaceKindV1;
use crate::release_review_surface_v1::ReviewSurfaceLimitsV1;
use crate::release_review_surface_v1::ReviewSurfaceRequestV1;
use crate::release_review_surface_v1::ReviewSurfaceSourceV1;
use crate::release_review_v1::ChangeOperationV1;
use std::fmt::Write as _;

pub(super) fn render(id: &str, before: &str, after: &str) -> eyre::Result<(String, Vec<String>)> {
    let limits = ReviewSurfaceLimitsV1 {
        max_output_bytes: 128 * 1024,
        ..ReviewSurfaceLimitsV1::default()
    };
    let request = ReviewSurfaceRequestV1::new(
        1,
        1,
        ReviewFilePairV1::new(
            id,
            "release-report",
            ChangeOperationV1::Modified,
            vec![id.to_owned()],
            Some(ReviewSurfaceSourceV1::new(
                "before",
                "before",
                "plaintext",
                before,
            )),
            Some(ReviewSurfaceSourceV1::new(
                "after",
                "after",
                "plaintext",
                after,
            )),
        ),
        ReviewSurfaceKindV1::TextPatch,
        limits,
    );
    let surface = crate::release_review_text_diff::produce_text_diff(&request, limits)?;
    let mut body = String::new();
    let mut ids = Vec::new();
    for line in surface.text.split_inclusive('\n') {
        let escaped = super::render::escape(line);
        if line.starts_with("@@") {
            let anchor = format!("{id}-hunk-{}", ids.len() + 1);
            let (before_line, after_line) = hunk_lines(line);
            write!(
                body,
                "<span class=\"patch-hunk\" id=\"{anchor}\" data-before-line=\"{before_line}\" data-after-line=\"{after_line}\">{escaped}</span>"
            )?;
            ids.push(anchor);
        } else {
            let class = if line.starts_with('-') && !line.starts_with("---") {
                "patch-removed"
            } else if line.starts_with('+') && !line.starts_with("+++") {
                "patch-added"
            } else {
                "patch-context"
            };
            write!(body, "<span class=\"{class}\">{escaped}</span>")?;
        }
    }
    Ok((format!("<pre class=\"compact-patch\">{body}</pre>"), ids))
}

fn hunk_lines(line: &str) -> (usize, usize) {
    let mut fields = line.split_whitespace().skip(1);
    let number = |field: Option<&str>| {
        field
            .and_then(|f| f.trim_start_matches(['-', '+']).split(',').next())
            .and_then(|f| f.parse::<usize>().ok())
            .unwrap_or(1)
            .max(1)
    };
    (number(fields.next()), number(fields.next()))
}

#[cfg(test)]
mod tests {
    #[test]
    fn hunk_headers_appear_only_in_patch_not_navigation() {
        let (html, ids) = super::render(
            "line-shift",
            "line 3: 0\nline 15: 4\n",
            "line 4: 0\nline 16: 4\n",
        )
        .unwrap();
        assert!(!html.contains("patch-nav"));
        assert!(!html.contains("Change 1"));
        assert!(html.contains("@@"));
        assert_eq!(html.matches("class=\"patch-hunk\"").count(), ids.len());
    }
}

/// Adjacent changed-line excerpts from the already escaped exact patch producer.
pub(super) fn split_view(patch: &str) -> String {
    let body = patch
        .split("<pre class=\"compact-patch\">")
        .nth(1)
        .unwrap_or("")
        .trim_end_matches("</pre>");
    let mut before = String::new();
    let mut after = String::new();
    for original in body.split_inclusive("</span>") {
        let span = if let Some((head, rest)) = original.split_once(" id=\"") {
            format!(
                "{head}{}",
                rest.split_once('"').map_or(rest, |(_, tail)| tail)
            )
        } else {
            original.to_owned()
        };
        if !span.contains("class=\"patch-added\"") {
            before.push_str(&span);
        }
        if !span.contains("class=\"patch-removed\"") {
            after.push_str(&span);
        }
    }
    if before.is_empty() {
        before.push_str("No textual changes; see complete payload and path move.");
        after.clone_from(&before);
    }
    format!(
        "<div class=\"text-preview\"><div><h3>4.34 baseline</h3><pre>{before}</pre></div><div><h3>4.35 candidate</h3><pre>{after}</pre></div></div>"
    )
}

pub(super) fn equal_view(escaped_code: &str) -> String {
    format!(
        "<p class=\"payload-note\">No textual changes; the complete unchanged payload is shown below. Resource moves retain their before/after paths in the title.</p><div class=\"text-preview\"><div><h3>4.34 baseline</h3><pre><code>{escaped_code}</code></pre></div><div><h3>4.35 candidate</h3><pre><code>{escaped_code}</code></pre></div></div>"
    )
}
