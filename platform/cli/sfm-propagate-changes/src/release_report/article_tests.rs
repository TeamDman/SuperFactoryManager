use super::document::ReleaseReportDocument;

fn fixture() -> ReleaseReportDocument {
    facet_json::from_str(include_str!("templates/article_test_input.json")).unwrap()
}

#[test]
fn reviewed_profile_accepts_explicit_fixture() {
    let doc = fixture();
    super::article_render::validate(doc.article.as_ref().unwrap(), &doc).unwrap();
}

#[test]
fn ordinary_report_input_keeps_article_optional() {
    let doc: ReleaseReportDocument = facet_json::from_str(r#"{"schema":"sfm:release-report-input@1","title":"x","generated_utc":"x","summary":"x","limitations":[],"sections":[],"cases":[],"diffs":[],"images":[],"jars":[]}"#).unwrap();
    assert!(doc.article.is_none());
}

#[test]
fn article_rejects_duplicate_unknown_and_unassigned_captures() {
    for mutation in 0..3 {
        let mut doc = fixture();
        let article = doc.article.as_mut().unwrap();
        match mutation {
            0 => article.supporting_images[0] = article.supporting_images[1].clone(),
            1 => article.supporting_images[0] = "issue610-unknown-final".to_owned(),
            _ => {
                article.supporting_images.pop();
            }
        }
        assert!(super::article_render::validate(doc.article.as_ref().unwrap(), &doc).is_err());
    }
}

#[test]
fn article_rejects_reinterpreted_source_or_acceptance() {
    for mutation in 0..5 {
        let mut doc = fixture();
        match mutation {
            0 => doc.article.as_mut().unwrap().profile = "unreviewed".to_owned(),
            1 => doc
                .diffs
                .iter_mut()
                .find(|d| d.id == "energy-asset-ordered-dropper-first-sfml")
                .unwrap()
                .after
                .push_str("\nOUTPUT fe:: TO Other"),
            2 => {
                doc.cases
                    .iter_mut()
                    .find(|c| c.id == "issue610-gui-red")
                    .unwrap()
                    .status = "failed".to_owned()
            }
            3 => {
                doc.article.as_mut().unwrap().primary_images[0].tooltip =
                    "<script>invented precision</script>".to_owned()
            }
            _ => {
                doc.jars.pop();
            }
        }
        assert!(super::article_render::validate(doc.article.as_ref().unwrap(), &doc).is_err());
    }
}

#[test]
fn article_liquid_preserves_fragments_and_keeps_records_subordinate() {
    let doc = fixture();
    let images = doc
        .images
        .iter()
        .map(|i| super::rendered_image::RenderedReportImage {
            id: i.id.clone(),
            html: format!("<figure id=\"{}\">fixture PNG</figure>", i.id),
            data: "fixture".to_owned(),
        })
        .collect::<Vec<_>>();
    let values = [
        ("title", "fixture"),
        ("summary", "fixture"),
        ("generated", "fixture"),
        ("manifest_hash", "sha256:fixture"),
        ("limitations", ""),
        (
            "sections",
            "<section id=\"record\"><pre>{JSON fixture}</pre></section>",
        ),
        ("cases", ""),
        ("diffs", ""),
        ("images", ""),
        ("jars", ""),
        ("style", ""),
        ("script", ""),
        ("evidence_download", "download fixture"),
    ];
    let html = super::article_render::render(
        doc.article.as_ref().unwrap(),
        &doc,
        &values,
        "<section id=\"class-explorer\">compressed evidence fixture</section>",
        "JAR record fixture",
        &images,
    )
    .unwrap();
    let story = html
        .split("<article id=\"release-story\"")
        .nth(1)
        .unwrap()
        .split("</article>")
        .next()
        .unwrap();
    assert!(!story.contains("{JSON fixture}"));
    assert_eq!(story.matches("<figure ").count(), 4);
    for id in [
        "issue610-red-source-after-hover-final",
        "issue610-green-source-after-hover-final",
    ] {
        assert!(story.contains(id));
        assert_eq!(html.matches(&format!("id=\"{id}\"")).count(), 1);
    }
    assert!(html.contains("<details id=\"technical-appendix\" class=\"appendix\">"));
    assert!(!html.contains("{JSON fixture}"));
    assert!(html.contains("download fixture"));
    assert!(html.contains("compressed evidence fixture"));
    assert!(!html.contains("JAR record fixture"));
    assert_eq!(html.matches("fixture PNG").count(), 4);
}

#[test]
fn article_class_loading_is_optional_and_lazy() {
    let script = include_str!("templates/class_tree.js");
    assert!(script.contains("if(panel&&!panel.open)await new Promise"));
    assert!(
        script.find("if(panel&&!panel.open)").unwrap()
            < script.find("pipeThrough(new DecompressionStream").unwrap()
    );
}

#[test]
fn presentation_labels_distinguish_transfer_states_and_mutation() {
    let body = include_str!("templates/article_body.liquid");
    assert!(body.contains("Before the second transfer"));
    assert!(body.contains("99,975 FE"));
    assert!(body.contains("24 FE"));
    assert!(body.contains("a <strong>state mutation</strong>, not a check"));
    assert!(!body.contains("The original report involved"));
    assert!(
        body.contains("issues/610")
            && body.contains("issues/528")
            && body.contains("issues/621")
            && body.contains("issues/527")
    );
    assert!(body.contains("byte-for-byte unchanged"));
}

#[test]
fn initial_gap_profile_requires_its_additional_assigned_capture() {
    let mut doc = fixture();
    doc.images.retain(|image| image.id.ends_with("-final"));
    let article = doc.article.as_mut().unwrap();
    article.profile = "sfm-4.35-energy-candidate@3".to_owned();
    article.historical_images.clear();
    article.linux_images.clear();
    assert!(super::article_render::validate(doc.article.as_ref().unwrap(), &doc).is_err());
    let id = "issue610-red-dropper-initial-final".to_owned();
    doc.article
        .as_mut()
        .unwrap()
        .supporting_images
        .push(id.clone());
    doc.images.push(super::image::ReleaseReportImage {
        id,
        relative_path: "initial.png".to_owned(),
        sha256: "sha256:fixture".to_owned(),
        caption: "Initial dropper before transfer".to_owned(),
        alt: "Actual initial capture".to_owned(),
    });
    super::article_render::validate(doc.article.as_ref().unwrap(), &doc).unwrap();
}
