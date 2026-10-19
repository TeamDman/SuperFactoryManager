use super::render::asset_path;
use super::render::escape;
#[test]
fn script_and_attribute_text_cannot_escape_static_source() {
    assert_eq!(
        escape("</script><img onerror='x'>&\""),
        "&lt;/script&gt;&lt;img onerror=&#39;x&#39;&gt;&amp;&quot;"
    );
}
#[test]
fn asset_traversal_and_absolute_paths_are_rejected() {
    let root = tempfile::tempdir().unwrap();
    for path in [
        "../secret.png",
        "C:/secret.png",
        "/secret.png",
        "a\\secret.png",
    ] {
        assert!(asset_path(root.path(), path).is_err());
    }
}
#[test]
fn report_rejects_unknown_schema_and_preserves_existing_output() {
    let root = tempfile::tempdir().unwrap();
    let input = root.path().join("input.json");
    let output = root.path().join("report.html");
    std::fs::write(&input, r#"{"schema":"wrong","title":"x","generated_utc":"x","summary":"x","limitations":[],"sections":[],"cases":[],"diffs":[],"images":[],"jars":[]}"#).unwrap();
    let request = super::request::ReleaseReportRequest {
        manifest: input,
        asset_root: root.path().to_owned(),
        output: output.clone(),
    };
    assert!(super::render(&request, &crate::cancellation::CancellationToken::new()).is_err());
    assert!(!output.exists());
    std::fs::write(&output, "preserved").unwrap();
    assert!(super::render(&request, &crate::cancellation::CancellationToken::new()).is_err());
    assert_eq!(std::fs::read_to_string(output).unwrap(), "preserved");
}

#[test]
fn public_cli_requires_explicit_inputs() {
    assert!(
        figue::from_slice::<crate::cli::Cli>(&["source", "release-report"])
            .into_result()
            .is_err()
    );
    let cli = figue::from_slice::<crate::cli::Cli>(&[
        "source",
        "release-report",
        "--manifest",
        "input.json",
        "--asset-root",
        ".",
        "--output",
        "new.html",
    ])
    .into_result()
    .unwrap()
    .get_silent();
    assert!(matches!(
        cli.command,
        crate::cli::Command::Source(crate::cli::source::SourceArgs {
            command: crate::cli::source::SourceCommand::ReleaseReport(_)
        })
    ));
}

#[test]
fn bad_image_hash_fails_before_creating_output() {
    let root = tempfile::tempdir().unwrap();
    std::fs::write(root.path().join("image.png"), b"\x89PNG\r\n\x1a\n").unwrap();
    let input = root.path().join("input.json");
    let output = root.path().join("new.html");
    std::fs::write(&input, r#"{"schema":"sfm:release-report-input@1","title":"x","generated_utc":"x","summary":"x","limitations":[],"sections":[],"cases":[],"diffs":[],"images":[{"id":"capture","relative_path":"image.png","sha256":"sha256:wrong","caption":"actual capture","alt":"fixture"}],"jars":[]}"#).unwrap();
    let request = super::request::ReleaseReportRequest {
        manifest: input,
        asset_root: root.path().to_owned(),
        output: output.clone(),
    };
    let error =
        super::render(&request, &crate::cancellation::CancellationToken::new()).unwrap_err();
    assert!(error.to_string().contains("SHA256 mismatch"));
    assert!(!output.exists());
}

#[test]
fn timestamp_only_archive_difference_is_not_payload_difference() {
    use std::io::Write as _;
    let root = tempfile::tempdir().unwrap();
    let a = root.path().join("baseline.jar");
    let b = root.path().join("candidate.jar");
    for (path, year) in [(&a, 2024), (&b, 2025)] {
        let mut zip = zip::ZipWriter::new(std::fs::File::create(path).unwrap());
        let options = zip::write::SimpleFileOptions::default()
            .last_modified_time(zip::DateTime::from_date_and_time(year, 1, 1, 0, 0, 0).unwrap());
        zip.start_file("same.class", options).unwrap();
        zip.write_all(b"same compiled payload").unwrap();
        zip.finish().unwrap();
    }
    let comparison = crate::jar_build::engine::release_jar_comparison_json(&a, &b).unwrap();
    assert!(comparison.contains("ZIP metadata differs: true"));
    assert!(comparison.contains("\"matches\": true"));
    assert!(!comparison.contains(&root.path().to_string_lossy().to_string()));
}

#[test]
fn compact_patch_highlights_escaped_changes_without_custom_navigation() {
    let before = (0..40)
        .map(|line| format!("line {line}\n"))
        .collect::<String>();
    let after = before.replace("line 20\n", "<img onerror='unsafe'>\n");
    let (html, ids) = super::static_patch::render("transfer", &before, &after).unwrap();
    assert_eq!(ids, vec!["transfer-hunk-1"]);
    assert!(!html.contains("href=\"#transfer-hunk-1\""));
    assert!(!html.contains("patch-nav"));
    assert!(html.contains("patch-added"));
    assert!(html.contains("patch-removed"));
    assert!(html.contains("&lt;img"));
    assert!(!html.contains("<img"));
    assert!(!html.contains(" line 0\n"));
}

#[test]
fn frozen_source_payloads_remain_serialized_without_duplicate_explorer_views() {
    let doc: super::document::ReleaseReportDocument = facet_json::from_str(r#"{"schema":"sfm:release-report-input@1","title":"test","generated_utc":"test","summary":"test","limitations":[],"sections":[],"cases":[],"diffs":[{"id":"hidden-frozen","title":"Frozen source","language":"java","before":"class Historical {}","after":"class Historical { int n; }","evidence_only":true},{"id":"current","title":"Current source","language":"java","before":"class A {}","after":"class A { int n; }"}],"images":[],"jars":[]}"#).unwrap();
    let (html, _) = super::render::render_diffs(
        &doc,
        &mut std::collections::BTreeSet::new(),
        &crate::cancellation::CancellationToken::new(),
    )
    .unwrap();
    assert!(!html.contains("hidden-frozen"));
    assert!(html.contains("id=\"current\""));
    assert!(
        facet_json::to_string(&doc)
            .unwrap()
            .contains("class Historical { int n; }")
    );
    let (_, supporting) = super::source_explorer::render(&doc, &html, "", "");
    assert!(!supporting.contains("hidden-frozen"));
    assert!(supporting.contains("Current source"));
}

#[test]
fn font_embedding_cannot_publish_an_article_that_crosses_the_final_cap() {
    let root = tempfile::tempdir().unwrap();
    let output = root.path().join("oversized-article.html");
    let limit = 32 * 1024 * 1024;
    let shell = "<html><head></head><body></body></html>";
    let before_fonts = format!(
        "<html><head></head><body>{}</body></html>",
        "x".repeat(limit - shell.len() - 1)
    );
    assert_eq!(before_fonts.len(), limit - 1);
    let html = super::fonts::embed(&before_fonts);
    assert!(html.len() > limit);
    assert!(html.contains("data:font/woff2;base64,"));
    assert!(html.contains("SIL OPEN FONT LICENSE Version 1.1"));
    let error = super::render::write_html(
        &output,
        &html,
        true,
        &crate::cancellation::CancellationToken::new(),
    )
    .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("editorial HTML exceeds 33554432")
    );
    assert!(!output.exists());

    let valid = "<html><head></head><body>bounded</body></html>";
    super::render::write_html(
        &output,
        valid,
        true,
        &crate::cancellation::CancellationToken::new(),
    )
    .unwrap();
    assert_eq!(std::fs::read_to_string(&output).unwrap(), valid);
}

#[test]
fn final_article_and_generic_html_budgets_keep_their_exact_boundaries() {
    let article_cap = 32 * 1024 * 1024;
    let generic_cap = 64 * 1024 * 1024;
    super::render::validate_final_html_size(article_cap, true).unwrap();
    assert!(super::render::validate_final_html_size(article_cap + 1, true).is_err());
    super::render::validate_final_html_size(article_cap + 1, false).unwrap();
    super::render::validate_final_html_size(generic_cap, false).unwrap();
    assert!(super::render::validate_final_html_size(generic_cap + 1, false).is_err());
}
