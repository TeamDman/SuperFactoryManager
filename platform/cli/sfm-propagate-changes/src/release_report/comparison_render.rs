//! Generic comparison reusing the report's tree, diff and optional editor.
use super::code_diff::ReleaseReportCodeDiff;
use super::document::ReleaseReportDocument;
use crate::cancellation::CancellationToken;
use crate::source_comparison::report::ComparisonReport;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;
use std::fmt::Write as _;

pub(crate) fn render(
    report: &ComparisonReport,
    cancellation: &CancellationToken,
) -> Result<String> {
    let escape = super::render::escape;
    let (diffs, rows) = diff_records(report);
    let doc = ReleaseReportDocument {
        schema: "sfm:release-report-input@1".into(),
        title: "Source comparison".into(),
        generated_utc: String::new(),
        summary: String::new(),
        limitations: vec![],
        sections: vec![],
        cases: vec![],
        diffs,
        images: vec![],
        article: None,
        correlations: vec![],
        jars: vec![],
    };
    let (diffs, originals) = super::render::render_diffs(&doc, &mut BTreeSet::new(), cancellation)?;
    let diffs = diffs
        .replace("4.34 baseline", "Left input")
        .replace("4.35 candidate", "Right input");
    let mut limits = String::new();
    for limit in &report.limitations {
        write!(limits, "<li>{}</li>", escape(limit))?;
    }
    let identities = escape(&facet_json::to_string_pretty(&(
        &report.left.identity,
        &report.right.identity,
    ))?);
    let review = super::review_controls::embed(&super::review_controls::comparison(report)?)?;
    let html = format!(
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Source comparison</title><style>{}\n{}\n{}\n.source-workbench{{display:grid;grid-template-columns:minmax(220px,30%) 1fr;gap:24px}}.source-sidebar{{max-height:80vh;overflow:auto}}.source-preview{{min-width:0}}.source-link[aria-current=true]{{font-weight:bold}}.code-diff[hidden]{{display:none}}@media(max-width:800px){{.source-workbench{{display:block}}}}</style></head><body><header><h1>Source comparison</h1><p>Captured source evidence · explicit left → right byte policy</p><p><a href=\"comparison.json\">Raw evidence and identities</a> · Compare <code>left/</code> and <code>right/</code> independently in IntelliJ.</p><details><summary>Input identities</summary><pre>{identities}</pre></details><details><summary>Limits</summary><ul>{limits}</ul></details></header><main><p id=\"comparison-status\" role=\"status\">Select a file. All static diffs remain readable without JavaScript.</p><div class=\"source-workbench\"><aside class=\"source-sidebar\" aria-label=\"Comparison files\">{}</aside><div class=\"source-preview\">{diffs}</div></div></main>{originals}{review}<script>{}</script><script>{}</script><script>{}</script></body></html>",
        include_str!("templates/style.css"),
        include_str!("templates/sfml.css"),
        include_str!("templates/review.css"),
        super::source_explorer::tree(&rows),
        include_str!("templates/monaco.js"),
        include_str!("templates/comparison.js"),
        include_str!("templates/review.js"),
    );
    let html = super::fonts::embed(&html);
    ensure!(
        html.len() <= 64 * 1024 * 1024,
        "comparison HTML exceeds budget"
    );
    Ok(html)
}

fn diff_records(report: &ComparisonReport) -> (Vec<ReleaseReportCodeDiff>, Vec<(String, String)>) {
    let mut rows = Vec::new();
    let diffs: Vec<ReleaseReportCodeDiff> = report
        .differences
        .iter()
        .enumerate()
        .map(|(index, diff)| {
            let id = format!("comparison-{index}");
            let label = match (&diff.old_path, &diff.new_path) {
                (Some(old), Some(new)) if old != new => format!("{old} → {new}"),
                _ => diff.path.clone(),
            };
            rows.push((format!("{label} [{}]", diff.status), id.clone()));
            let evidence = |side: &str, text: &Option<String>| {
                if let Some(text) = text {
                    return text.clone();
                }
                let snapshot = if side == "left" {
                    &report.left
                } else {
                    &report.right
                };
                if side == "left" {
                    diff.old_path.as_ref()
                } else {
                    diff.new_path.as_ref()
                }
                .and_then(|path| snapshot.files.get(path))
                .map_or_else(
                    || "(absent)".into(),
                    |file| {
                        format!(
                            "Binary/non-text evidence: {} bytes, {}",
                            file.bytes.len(),
                            file.sha256
                        )
                    },
                )
            };
            ReleaseReportCodeDiff {
                evidence_only: false,
                source_paths: None,
                old_reference_evidence: None,
                new_reference_evidence: None,
                id,
                title: format!(
                    "{} · {} · mode equality {:?}",
                    diff.path, diff.status, diff.mode_equal
                ),
                language: match std::path::Path::new(&diff.path)
                    .extension()
                    .and_then(|value| value.to_str())
                {
                    Some("java") => "java",
                    Some("json") => "json",
                    Some("sfml") => "sfml",
                    Some("rs") => "rust",
                    Some("gradle") => "groovy",
                    _ => "plaintext",
                }
                .into(),
                before: evidence("left", &diff.before),
                after: evidence("right", &diff.after),
            }
        })
        .collect();
    (diffs, rows)
}
