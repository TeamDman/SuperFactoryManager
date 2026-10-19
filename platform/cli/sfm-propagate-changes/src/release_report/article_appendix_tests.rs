use super::super::document::ReleaseReportDocument;
use super::super::section::ReleaseReportSection;
use super::super::source_correlation::SourceCorrelation;

const RECORDS: [&str; 3] = [
    "release435-label-gun-chat",
    "release435-artifact-delivery",
    "recipe-example-provenance",
];

fn fixture() -> ReleaseReportDocument {
    facet_json::from_str(include_str!("templates/article_test_input.json")).unwrap()
}

fn reviewed_records(doc: &mut ReleaseReportDocument) {
    doc.sections
        .retain(|section| !RECORDS.contains(&section.id.as_str()));
    doc.sections.extend(RECORDS.map(|id| ReleaseReportSection {
        id: id.into(),
        title: if id == "recipe-example-provenance" {
            "Historical evidence · recipe example <retained>".into()
        } else {
            format!("Reviewed record {id} <original>")
        },
        body: format!("Exact original body for {id}\r\n<script>inert & untrusted</script>"),
    }));
}

#[test]
fn article_restores_reviewed_section_and_archive_targets_without_raw_technical_dump() {
    let mut doc = fixture();
    reviewed_records(&mut doc);
    doc.sections.push(ReleaseReportSection {
        id: "unreviewed-appendix-record".into(),
        title: "Must stay omitted".into(),
        body: "OMITTED SENTINEL".into(),
    });
    let jar = doc
        .jars
        .iter_mut()
        .find(|pair| pair.id == "jar-1-21-1")
        .unwrap();
    jar.baseline_path = "baseline<&>.jar".into();
    jar.candidate_path = "candidate\"<&>.jar".into();
    jar.provenance = "OMITTED TECHNICAL JSON SENTINEL".into();
    jar.class_evidence_path = Some("classes/exact<&>.json".into());
    jar.class_evidence_sha256 = Some(format!("sha256:{}", "a".repeat(64)));
    let images = doc
        .images
        .iter()
        .map(|image| super::super::rendered_image::RenderedReportImage {
            id: image.id.clone(),
            html: format!("<figure id=\"{}\">fixture PNG</figure>", image.id),
            data: "fixture".into(),
        })
        .collect::<Vec<_>>();
    let html = super::super::article_render::render(
        doc.article.as_ref().unwrap(),
        &doc,
        &[("diffs", ""), ("evidence_download", "download fixture")],
        "<section id=\"class-explorer\">fixture</section>",
        "OMITTED FULL TECHNICAL HTML SENTINEL",
        &images,
    )
    .unwrap();
    for id in RECORDS
        .into_iter()
        .chain(doc.jars.iter().map(|pair| pair.id.as_str()))
    {
        assert_eq!(
            html.matches(&format!("id=\"{id}\"")).count(),
            1,
            "target {id}"
        );
    }
    assert!(html.contains("Historical evidence · recipe example &lt;retained&gt;"));
    for section in doc
        .sections
        .iter()
        .filter(|section| RECORDS.contains(&section.id.as_str()))
    {
        assert!(html.contains(&super::super::render::escape(&section.title)));
        assert!(html.contains(&super::super::render::escape(&section.body)));
    }
    assert!(html.contains("baseline&lt;&amp;&gt;.jar"));
    assert!(html.contains("candidate&quot;&lt;&amp;&gt;.jar"));
    assert!(html.contains("classes/exact&lt;&amp;&gt;.json"));
    assert!(html.contains("Paired class evidence SHA256"));
    assert!(html.contains(&format!("sha256:{}", "a".repeat(64))));
    for sentinel in [
        "<script>inert",
        "OMITTED SENTINEL",
        "OMITTED TECHNICAL JSON SENTINEL",
        "OMITTED FULL TECHNICAL HTML SENTINEL",
    ] {
        assert!(!html.contains(sentinel));
    }
}

#[test]
fn appendix_rejects_duplicate_or_oversized_selected_records_and_identity_fields() {
    for mutation in 0..5 {
        let mut doc = fixture();
        reviewed_records(&mut doc);
        let section = doc
            .sections
            .iter_mut()
            .find(|section| section.id == RECORDS[0])
            .unwrap();
        match mutation {
            0 => section.body = "x".repeat(64 * 1024 + 1),
            1 => section.title = "x".repeat(1025),
            2 => doc.sections.push(ReleaseReportSection {
                id: RECORDS[0].into(),
                title: "duplicate".into(),
                body: "duplicate".into(),
            }),
            3 => doc.jars[0].baseline_path = "x".repeat(4097),
            _ => doc.jars[0].id = "unsafe\" onclick=\"inert".into(),
        }
        assert!(super::render(&doc).is_err(), "mutation {mutation}");
    }
}

#[test]
fn appendix_total_output_budget_is_applied_after_escaping() {
    let mut doc = fixture();
    reviewed_records(&mut doc);
    for section in doc
        .sections
        .iter_mut()
        .filter(|section| RECORDS.contains(&section.id.as_str()))
    {
        section.body = "<".repeat(64 * 1024);
    }
    assert!(super::render(&doc).is_err());
}

fn correlation() -> SourceCorrelation {
    SourceCorrelation {
        id: "resource-quantity-cause".into(),
        source_diff_id: "resource-quantity-current-reference".into(),
        class_path: "ca/teamdman/sfml/ast/ResourceQuantity.class".into(),
        before_javap: "  public void example();\n    LineNumberTable:\n      line 1: 0\n".into(),
        after_javap: "  public void example();\n    LineNumberTable:\n      line 2: 0\n".into(),
    }
}

#[test]
fn empty_correlations_and_equal_current_source_do_not_fabricate_a_causal_target() {
    let mut doc = fixture();
    doc.correlations.clear();
    doc.diffs.clear();
    doc.diffs.push(facet_json::from_str(r#"{"id":"resource-quantity-current-reference","title":"Current source reference · unchanged","language":"java","before":"package example;\n","after":"package example;\n"}"#).unwrap());
    let raw_diff = "<section id=\"resource-quantity-current-reference\"><h3>Current source reference · unchanged</h3></section>";
    let verified = super::super::correlation_render::render(&doc).unwrap();
    assert!(verified.is_empty());
    let (changes, supporting) =
        super::super::source_explorer::render(&doc, raw_diff, "", &verified);
    assert!(!changes.contains("resource-quantity-cause"));
    assert!(supporting.contains("id=\"resource-quantity-current-reference\""));

    doc.correlations.push(correlation());
    assert!(super::super::correlation_render::render(&doc).is_err());
    let (changes, _) = super::super::source_explorer::render(&doc, raw_diff, "", "");
    assert!(!changes.contains("resource-quantity-cause"));
}

#[test]
fn causal_link_is_present_when_the_verified_correlation_target_was_actually_rendered() {
    let mut doc = fixture();
    doc.correlations = vec![correlation()];
    doc.diffs.clear();
    doc.diffs.push(facet_json::from_str(r#"{"id":"resource-quantity-current-reference","title":"Synthetic exact one-comment source correspondence","language":"java","before":"package example;\n","after":"// GENERATED by sfm-propagate-changes; edit the primary source or reconcile this file.\npackage example;\n"}"#).unwrap());
    let verified = super::super::correlation_render::render(&doc).unwrap();
    assert!(verified.contains("<section id=\"resource-quantity-cause\">"));
    let (changes, _) = super::super::source_explorer::render(&doc, "", "", &verified);
    assert_eq!(
        changes.matches("href=\"#resource-quantity-cause\"").count(),
        1
    );
}
