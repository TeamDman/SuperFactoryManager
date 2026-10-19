use super::article_release_artifact::ArticleReleaseArtifact;
use super::article_release_delivery::ArticleReleaseDelivery;
use super::article_release_validation::validate;
use super::document::ReleaseReportDocument;
use super::jar_pair::ReleaseReportJarPair;
use super::section::ReleaseReportSection;
use sha2::Digest as _;
use sha2::Sha256;
use std::fs;
use std::path::Path;
use tempfile::TempDir;

const REQUIRED_SECTIONS: [&str; 4] = [
    "release435-artifact-delivery",
    "release435-source-comparison-identities",
    "release435-runtime-acceptance",
    "historical-runtime-boundary",
];

fn digest(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}

fn evidence(directory: &Path, name: &str, bytes: &[u8]) -> (String, String) {
    let path = directory.join(name);
    fs::write(&path, bytes).unwrap();
    (path.to_string_lossy().into_owned(), digest(bytes))
}

fn artifact(directory: &Path, target: &str) -> (ReleaseReportJarPair, ArticleReleaseArtifact) {
    let id = format!("jar-{}", target.replace('.', "-"));
    let projection = format!("sfm-4.35.0/mc-{target}");
    let (baseline_path, baseline_sha256) =
        evidence(directory, &format!("{id}-baseline.jar"), b"baseline");
    let (candidate_path, candidate_sha256) =
        evidence(directory, &format!("{id}-candidate.jar"), b"candidate");
    let (package_receipt_path, package_receipt_sha256) =
        evidence(directory, &format!("{id}-package.json"), b"{}");
    let (resource_audit_path, resource_audit_sha256) =
        evidence(directory, &format!("{id}-resources.json"), b"{}");
    let relay = if matches!(target, "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" | "26.1.2") {
        "present"
    } else {
        "not-applicable"
    };
    let jar_json = facet_json::to_string(&candidate_path).unwrap();
    let class_json = format!(
        r#"{{"schema":"sfm:built-release-class-delivery@1","projection_key":"{projection}","target":"{target}","jar":{jar_json},"sha256":"{candidate_sha256}","bytes":9,"energy_extraction_verified":true,"label_gun_success_messages_verified":true,"tunnel_forwarding_preserved":true,"relay_delivery_status":"{relay}","buffer_production_item_absent":true,"success":true}}"#
    );
    let (class_audit_path, class_audit_sha256) = evidence(
        directory,
        &format!("{id}-classes.json"),
        class_json.as_bytes(),
    );
    let pair = ReleaseReportJarPair {
        id: id.clone(),
        baseline_path,
        candidate_path,
        provenance:
            "Synthetic bytes: validator checks hashes; full rendering checks ZIP/class semantics."
                .to_owned(),
        class_evidence_path: Some("explorer-classes.json".to_owned()),
        class_evidence_sha256: Some(format!("sha256:{}", digest(b"prepared class evidence"))),
    };
    let delivery = ArticleReleaseArtifact {
        id,
        projection,
        baseline_sha256,
        candidate_sha256,
        package_receipt_path,
        package_receipt_sha256,
        resource_audit_path,
        resource_audit_sha256,
        class_audit_path,
        class_audit_sha256,
        energy_extraction_verified: true,
        label_gun_success_messages_verified: true,
        tunnel_forwarding_preserved: true,
        tunnel_relay: relay.to_owned(),
        french_entries: 276,
        portuguese_entries: 282,
        portuguese_added_keys: 23,
        recipe_count: 29,
        recipe_advancements: 23,
        buffer_loot_absent: true,
        buffer_production_item_absent: true,
    };
    (pair, delivery)
}

fn fixture() -> (TempDir, ReleaseReportDocument) {
    let directory = tempfile::tempdir().unwrap();
    let mut document: ReleaseReportDocument = facet_json::from_str(
        r#"{"schema":"sfm:release-report-input@1","title":"fixture","generated_utc":"fixture","summary":"fixture","limitations":[],"sections":[],"cases":[],"diffs":[],"images":[],"jars":[]}"#,
    ).unwrap();
    let mut delivery = ArticleReleaseDelivery {
        schema: "sfm:release435-artifact-delivery@1".to_owned(),
        artifacts: Vec::new(),
    };
    for target in [
        "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
        "26.1.2",
    ] {
        let (pair, record) = artifact(directory.path(), target);
        document.jars.push(pair);
        delivery.artifacts.push(record);
    }
    document.sections = REQUIRED_SECTIONS
        .iter()
        .map(|id| ReleaseReportSection {
            id: (*id).to_owned(),
            title: (*id).to_owned(),
            body: if *id == REQUIRED_SECTIONS[0] {
                facet_json::to_string(&delivery).unwrap()
            } else {
                "Independent acceptance statement; no runtime pass is inferred.".to_owned()
            },
        })
        .collect();
    (directory, document)
}

fn update_delivery(
    document: &mut ReleaseReportDocument,
    change: impl FnOnce(&mut ArticleReleaseDelivery),
) {
    let section = document
        .sections
        .iter_mut()
        .find(|section| section.id == REQUIRED_SECTIONS[0])
        .unwrap();
    let mut delivery: ArticleReleaseDelivery = facet_json::from_str(&section.body).unwrap();
    change(&mut delivery);
    section.body = facet_json::to_string(&delivery).unwrap();
}

#[test]
fn accepts_ten_hash_bound_records_without_inferring_runtime_success() {
    let (_directory, document) = fixture();
    validate(&document).unwrap();
    assert!(document.cases.is_empty());
}

#[test]
fn rejects_changed_jar_or_receipt_bytes() {
    for mutation in 0..5 {
        let (_directory, document) = fixture();
        let delivery: ArticleReleaseDelivery =
            facet_json::from_str(&document.sections[0].body).unwrap();
        let record = &delivery.artifacts[0];
        let path = match mutation {
            0 => &document.jars[0].baseline_path,
            1 => &document.jars[0].candidate_path,
            2 => &record.package_receipt_path,
            3 => &record.resource_audit_path,
            _ => &record.class_audit_path,
        };
        fs::write(path, b"changed evidence").unwrap();
        assert!(
            validate(&document)
                .unwrap_err()
                .to_string()
                .contains("SHA256 mismatch")
        );
    }
}

#[test]
fn rejects_stale_or_noncanonical_declared_hashes() {
    for mutation in 0..4 {
        let (_directory, mut document) = fixture();
        update_delivery(&mut document, |delivery| {
            let record = &mut delivery.artifacts[0];
            match mutation {
                0 => record.candidate_sha256 = "0".repeat(64),
                1 => record.package_receipt_sha256 = "0".repeat(64),
                2 => {
                    record.resource_audit_sha256 =
                        format!("sha256:{}", record.resource_audit_sha256)
                }
                _ => record.class_audit_sha256 = "A".repeat(64),
            }
        });
        assert!(validate(&document).is_err());
    }
}

#[test]
fn rejects_reordered_missing_or_mismatched_target_records() {
    for mutation in 0..5 {
        let (_directory, mut document) = fixture();
        match mutation {
            0 => update_delivery(&mut document, |delivery| delivery.artifacts.swap(0, 1)),
            1 => update_delivery(&mut document, |delivery| {
                delivery.artifacts.pop();
            }),
            2 => document.jars.swap(0, 1),
            3 => {
                document.jars.pop();
            }
            _ => update_delivery(&mut document, |delivery| {
                delivery.artifacts[0].projection = "sfm-4.35.0/mc-1.21.1".to_owned()
            }),
        }
        assert!(validate(&document).is_err());
    }
}

#[test]
fn rejects_wrong_counts_incomplete_checks_and_relay_states() {
    for mutation in 0..12 {
        let (_directory, mut document) = fixture();
        update_delivery(&mut document, |delivery| {
            if mutation == 10 {
                delivery.artifacts[5].tunnel_relay = "not-applicable".to_owned();
                return;
            }
            let record = &mut delivery.artifacts[0];
            match mutation {
                0 => record.french_entries = 275,
                1 => record.portuguese_entries = 259,
                2 => record.portuguese_added_keys = 22,
                3 => record.recipe_count = 28,
                4 => record.recipe_advancements = 22,
                5 => record.energy_extraction_verified = false,
                6 => record.tunnel_forwarding_preserved = false,
                7 => record.buffer_loot_absent = false,
                8 => record.buffer_production_item_absent = false,
                9 => record.tunnel_relay = "present".to_owned(),
                _ => record.label_gun_success_messages_verified = false,
            }
        });
        assert!(validate(&document).is_err());
    }
}

#[test]
fn rejects_each_missing_empty_or_duplicate_required_section() {
    for required in REQUIRED_SECTIONS {
        for mutation in 0..3 {
            let (_directory, mut document) = fixture();
            let index = document
                .sections
                .iter()
                .position(|section| section.id == required)
                .unwrap();
            match mutation {
                0 => {
                    document.sections.remove(index);
                }
                1 => document.sections[index].body = " \n\t".to_owned(),
                _ => document.sections.push(ReleaseReportSection {
                    id: required.to_owned(),
                    title: "duplicate".to_owned(),
                    body: "duplicate".to_owned(),
                }),
            }
            assert!(validate(&document).is_err());
        }
    }
}

#[test]
fn requires_prepared_explorer_class_evidence_declarations() {
    for mutation in 0..4 {
        let (_directory, mut document) = fixture();
        match mutation {
            0 => document.jars[0].class_evidence_path = None,
            1 => document.jars[0].class_evidence_path = Some(" ".to_owned()),
            2 => document.jars[0].class_evidence_sha256 = None,
            _ => document.jars[0].class_evidence_sha256 = Some("sha256:not-a-digest".to_owned()),
        }
        assert!(validate(&document).is_err());
    }
}

#[test]
fn rejects_unknown_delivery_and_artifact_fields() {
    for mutation in 0..2 {
        let (_directory, mut document) = fixture();
        let body = &mut document.sections[0].body;
        *body = if mutation == 0 {
            body.replacen('{', r#"{"unreviewed":true,"#, 1)
        } else {
            body.replacen(r#""id":"#, r#""unreviewed":true,"id":"#, 1)
        };
        assert!(validate(&document).is_err());
    }
}

#[test]
fn rejects_relative_nonregular_and_oversized_receipts() {
    for mutation in 0..3 {
        let (directory, mut document) = fixture();
        update_delivery(&mut document, |delivery| {
            let record = &mut delivery.artifacts[0];
            match mutation {
                0 => record.package_receipt_path = "relative-receipt.json".to_owned(),
                1 => record.package_receipt_path = directory.path().to_string_lossy().into_owned(),
                _ => fs::OpenOptions::new()
                    .write(true)
                    .open(&record.package_receipt_path)
                    .unwrap()
                    .set_len(32 * 1024 * 1024 + 1)
                    .unwrap(),
            }
        });
        assert!(validate(&document).is_err());
    }
}

#[cfg(unix)]
#[test]
fn rejects_receipt_symlinks_even_with_matching_bytes() {
    let (directory, mut document) = fixture();
    update_delivery(&mut document, |delivery| {
        let record = &mut delivery.artifacts[0];
        let link = directory.path().join("receipt-link.json");
        std::os::unix::fs::symlink(&record.package_receipt_path, &link).unwrap();
        record.package_receipt_path = link.to_string_lossy().into_owned();
    });
    assert!(validate(&document).is_err());
}

#[test]
fn rejects_hash_bound_class_receipts_that_contradict_delivery() {
    for mutation in 0..12 {
        let (_directory, mut document) = fixture();
        let wrong_jar = facet_json::to_string(&document.jars[0].baseline_path).unwrap();
        let candidate_json = facet_json::to_string(&document.jars[0].candidate_path).unwrap();
        update_delivery(&mut document, |delivery| {
            let record = &mut delivery.artifacts[0];
            let original = fs::read_to_string(&record.class_audit_path).unwrap();
            let changed = match mutation {
                0 => original.replace("sfm:built-release-class-delivery@1", "unreviewed"),
                1 => original.replace("sfm-4.35.0/mc-1.19.2", "sfm-4.35.0/mc-1.19.4"),
                2 => original.replace(r#""target":"1.19.2""#, r#""target":"1.19.4""#),
                3 => original.replace(&record.candidate_sha256, &"0".repeat(64)),
                4 => original.replace(r#""bytes":9"#, r#""bytes":8"#),
                5 => original.replace(&candidate_json, &wrong_jar),
                6 => original.replace(
                    r#""energy_extraction_verified":true"#,
                    r#""energy_extraction_verified":false"#,
                ),
                7 => original.replace(
                    r#""tunnel_forwarding_preserved":true"#,
                    r#""tunnel_forwarding_preserved":false"#,
                ),
                8 => original.replace(
                    r#""relay_delivery_status":"not-applicable""#,
                    r#""relay_delivery_status":"present""#,
                ),
                9 => original.replace(
                    r#""buffer_production_item_absent":true"#,
                    r#""buffer_production_item_absent":false"#,
                ),
                10 => original.replace(r#""success":true"#, r#""success":false"#),
                _ => original.replace(
                    r#""label_gun_success_messages_verified":true"#,
                    r#""label_gun_success_messages_verified":false"#,
                ),
            };
            assert_ne!(changed, original);
            fs::write(&record.class_audit_path, &changed).unwrap();
            record.class_audit_sha256 = digest(changed.as_bytes());
        });
        assert!(validate(&document).is_err());
    }
}

#[test]
fn accepts_extensible_class_receipt_proof_details() {
    let (_directory, mut document) = fixture();
    update_delivery(&mut document, |delivery| {
        let record = &mut delivery.artifacts[0];
        let original = fs::read_to_string(&record.class_audit_path).unwrap();
        let changed = original.replacen('{', r#"{"proof_details":{"static_only":true},"#, 1);
        fs::write(&record.class_audit_path, &changed).unwrap();
        record.class_audit_sha256 = digest(changed.as_bytes());
    });
    validate(&document).unwrap();
}
