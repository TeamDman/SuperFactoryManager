use super::super::article::ReleaseReportArticle;
use super::super::article_release_artifact::ArticleReleaseArtifact;
use super::super::article_release_delivery::ArticleReleaseDelivery;
use super::super::document::ReleaseReportDocument;
use super::super::jar_pair::ReleaseReportJarPair;
use super::super::section::ReleaseReportSection;
use base64::Engine as _;
use eyre::Result;
use std::io::Cursor;
use std::io::Read as _;
use std::path::Path;

const IDS: [&str; 10] = [
    "jar-1-19-2",
    "jar-1-19-4",
    "jar-1-20",
    "jar-1-20-1",
    "jar-1-20-2",
    "jar-1-20-3",
    "jar-1-20-4",
    "jar-1-21-0",
    "jar-1-21-1",
    "jar-26-1-2",
];

fn artifact(root: &Path, id: &str) -> ArticleReleaseArtifact {
    let path = |kind| {
        root.join(format!("{kind}.json"))
            .to_string_lossy()
            .into_owned()
    };
    let hash = |kind| {
        crate::source_projection::provenance::sha256(
            &std::fs::read(root.join(format!("{kind}.json"))).unwrap(),
        )
        .trim_start_matches("sha256:")
        .to_owned()
    };
    ArticleReleaseArtifact {
        id: id.into(),
        projection: "unit-test-download".into(),
        baseline_sha256: "a".repeat(64),
        candidate_sha256: "b".repeat(64),
        package_receipt_path: path("package"),
        package_receipt_sha256: hash("package"),
        resource_audit_path: path("resource"),
        resource_audit_sha256: hash("resource"),
        class_audit_path: path("class"),
        class_audit_sha256: hash("class"),
        energy_extraction_verified: true,
        label_gun_success_messages_verified: true,
        tunnel_forwarding_preserved: true,
        tunnel_relay: "not-applicable".into(),
        french_entries: 276,
        portuguese_entries: 282,
        portuguese_added_keys: 23,
        recipe_count: 29,
        recipe_advancements: 23,
        buffer_loot_absent: true,
        buffer_production_item_absent: true,
    }
}

fn fixture(root: &Path) -> Result<(ReleaseReportDocument, [Vec<u8>; 3])> {
    let raw = [
        b"{\"kind\":\"package\"}\r\n".to_vec(),
        "{\"kind\":\"resource\",\"label\":\"Câble\"}\r\n"
            .as_bytes()
            .to_vec(),
        b"{\"kind\":\"class\",\"scope\":\"static\"}\n".to_vec(),
    ];
    for (kind, bytes) in ["package", "resource", "class"].iter().zip(&raw) {
        std::fs::write(root.join(format!("{kind}.json")), bytes)?;
    }
    let delivery = ArticleReleaseDelivery {
        schema: "sfm:release435-artifact-delivery@1".into(),
        artifacts: IDS.iter().map(|id| artifact(root, id)).collect(),
    };
    let doc = ReleaseReportDocument {
        schema: "sfm:release-report-input@1".into(),
        title: "Download unit fixture".into(),
        generated_utc: "fixture".into(),
        summary: "Receipt retention only".into(),
        limitations: vec![],
        cases: vec![],
        diffs: vec![],
        images: vec![],
        correlations: vec![],
        sections: vec![ReleaseReportSection {
            id: "release435-artifact-delivery".into(),
            title: "Delivery".into(),
            body: facet_json::to_string(&delivery)?,
        }],
        jars: IDS
            .iter()
            .map(|id| ReleaseReportJarPair {
                id: (*id).into(),
                baseline_path: "not-read-by-download-test".into(),
                candidate_path: "not-read-by-download-test".into(),
                provenance: "unit fixture".into(),
                class_evidence_path: None,
                class_evidence_sha256: None,
            })
            .collect(),
        article: Some(ReleaseReportArticle {
            profile: "sfm-4.35-energy-candidate@4".into(),
            program_diff_id: "".into(),
            branch_diff_id: "".into(),
            primary_images: vec![],
            supporting_images: vec![],
            historical_images: vec![],
            linux_images: vec![],
        }),
    };
    Ok((doc, raw))
}

fn archive(markup: &str) -> Result<zip::ZipArchive<Cursor<Vec<u8>>>> {
    let encoded = markup
        .split_once("href=\"data:application/zip;base64,")
        .ok_or_else(|| eyre::eyre!("download data missing"))?
        .1
        .split('"')
        .next()
        .unwrap();
    Ok(zip::ZipArchive::new(Cursor::new(
        base64::engine::general_purpose::STANDARD.decode(encoded)?,
    ))?)
}

#[test]
fn new_profile_download_retains_all_thirty_receipts_exactly() -> Result<()> {
    let root = tempfile::tempdir()?;
    let (doc, raw) = fixture(root.path())?;
    let manifest = facet_json::to_string(&doc)?;
    let markup = super::render(manifest.as_bytes(), &doc, root.path())?;
    let mut zip = archive(&markup)?;
    assert_eq!(zip.len(), 31);
    for id in IDS {
        for (kind, expected) in ["package", "resource", "class"].iter().zip(&raw) {
            let mut actual = Vec::new();
            zip.by_name(&format!("delivery/{id}/{kind}.json"))?
                .read_to_end(&mut actual)?;
            assert_eq!(&actual, expected);
        }
    }
    let mut captured = String::new();
    zip.by_name("report-input.json")?
        .read_to_string(&mut captured)?;
    assert_eq!(captured, manifest);
    Ok(())
}

#[test]
fn old_profile_download_ignores_new_delivery_payload() -> Result<()> {
    let root = tempfile::tempdir()?;
    let (mut doc, _) = fixture(root.path())?;
    doc.article.as_mut().unwrap().profile = "sfm-4.35-energy-candidate@3".into();
    doc.sections[0].body = "not parsed by the historical profile".into();
    let markup = super::render(b"historical input bytes", &doc, root.path())?;
    let mut zip = archive(&markup)?;
    assert_eq!(zip.len(), 1);
    let mut captured = Vec::new();
    zip.by_name("report-input.json")?
        .read_to_end(&mut captured)?;
    assert_eq!(captured, b"historical input bytes");
    Ok(())
}

#[test]
fn receipt_download_rejects_changed_raw_bytes() -> Result<()> {
    let root = tempfile::tempdir()?;
    let (doc, _) = fixture(root.path())?;
    std::fs::write(
        root.path().join("package.json"),
        b"changed after identity capture",
    )?;
    let error = super::render(b"input", &doc, root.path()).unwrap_err();
    assert!(error.to_string().contains("receipt hash mismatch"));
    Ok(())
}

#[test]
fn receipt_download_rejects_oversized_file_before_hashing() -> Result<()> {
    let root = tempfile::tempdir()?;
    let (doc, _) = fixture(root.path())?;
    std::fs::File::create(root.path().join("resource.json"))?.set_len(32 * 1024 * 1024 + 1)?;
    let error = super::render(b"input", &doc, root.path()).unwrap_err();
    assert!(error.to_string().contains("exceeds"));
    Ok(())
}

#[test]
fn receipt_download_rejects_unsafe_artifact_zip_path() -> Result<()> {
    let root = tempfile::tempdir()?;
    let (mut doc, _) = fixture(root.path())?;
    let mut delivery: ArticleReleaseDelivery = facet_json::from_str(&doc.sections[0].body)?;
    delivery.artifacts[0].id = "../escape".into();
    doc.jars[0].id = "../escape".into();
    doc.sections[0].body = facet_json::to_string(&delivery)?;
    assert!(super::render(b"input", &doc, root.path()).is_err());
    Ok(())
}
