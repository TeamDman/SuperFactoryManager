use super::BTreeMap;
use super::PROVENANCE;
use super::PROVENANCE_SHA;
use super::Path;
use super::PreflightReport;
use super::SOURCE;
use super::SOURCE_SHA;
use super::TestPin;
use super::approved_pins::COMMON_PINS;
use super::artifact_catalog::ArtifactCatalog;
use super::provision_receipt::ProvisionReceipt;
use super::read_pinned;
use super::source_lock::SourceLock;

pub(super) fn load(report: &mut PreflightReport, root: &Path) -> eyre::Result<Vec<TestPin>> {
    let source = read_pinned(report, root, SOURCE, SOURCE_SHA)?;
    let proof = read_pinned(report, root, PROVENANCE, PROVENANCE_SHA)?;
    let source: ArtifactCatalog = facet_json::from_slice(&source)?;
    let proof: ProvisionReceipt = facet_json::from_slice(&proof)?;
    validate(&source, &proof)
}

pub(super) fn validate(
    source: &ArtifactCatalog,
    proof: &ProvisionReceipt,
) -> eyre::Result<Vec<TestPin>> {
    eyre::ensure!(
        proof.schema == "sfm:approved_test_dependency_provision@1"
            && proof.source_lock_after_sha256 == format!("sha256:{SOURCE_SHA}")
            && proof.artifacts.len() == 12,
        "reviewed JUnit provision authority mismatch"
    );
    let source_pins = artifact_pin_map(&source.artifacts)?;
    let mut pins = Vec::new();
    for (coordinate, expected) in COMMON_PINS {
        eyre::ensure!(
            source_pins
                .get(coordinate)
                .is_some_and(|hash| hash == expected),
            "reviewed flat JUnit closure missing/conflicting pin: {coordinate}"
        );
        let proof_pin = proof.artifacts.iter().find(|p| p.coordinate == coordinate);
        if let Some(p) = proof_pin {
            eyre::ensure!(
                p.canonical_hash == expected
                    && p.sha256.starts_with("sha256:")
                    && p.sha256.len() == 71
                    && p.bytes > 0,
                "JUnit provision pin mismatch: {coordinate}"
            );
        }
        pins.push(TestPin {
            coordinate: coordinate.to_owned(),
            content_hash: expected.to_owned(),
            full_sha256: proof_pin.map(|p| p.sha256.trim_start_matches("sha256:").to_owned()),
            bytes: proof_pin.map(|p| p.bytes),
            origin: proof_pin.map(|p| p.origin.clone()),
            authority: if proof_pin.is_some() {
                "reviewed_test_provision"
            } else {
                "unchanged_released_test_pin"
            }
            .to_owned(),
        });
    }
    eyre::ensure!(
        proof
            .artifacts
            .iter()
            .all(|p| pins.iter().any(|pin| pin.coordinate == p.coordinate)),
        "provision contains a pin outside the reviewed flat JUnit closure"
    );
    Ok(pins)
}

pub(super) fn pin_map(lock: &SourceLock) -> eyre::Result<BTreeMap<String, String>> {
    artifact_pin_map(&lock.artifacts)
}

fn artifact_pin_map(
    rows: &[super::source_artifact::SourceArtifact],
) -> eyre::Result<BTreeMap<String, String>> {
    let mut pins = BTreeMap::new();
    for row in rows {
        if let Some(coordinate) = &row.coordinate
            && let Some(previous) = pins.insert(coordinate.clone(), row.hash.clone())
        {
            eyre::ensure!(
                previous == row.hash,
                "contradictory artifact pins for {coordinate}"
            );
        }
    }
    Ok(pins)
}
