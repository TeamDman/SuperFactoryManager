use super::super::ContentHash;
use super::super::MavenCoordinate;
use super::super::maven_cache_path_for;
use super::BTreeMap;
use super::CancellationToken;
use super::MAX_ARTIFACT_BYTES;
use super::Path;
use super::PreflightReport;
use super::TestPin;
use super::issue;
use super::read_input;
use super::sha256;
use super::verified_test_artifact::VerifiedTestArtifact;

pub(super) fn verify_all(
    report: &mut PreflightReport,
    cache: &Path,
    cancel: &CancellationToken,
) -> eyre::Result<()> {
    let mut pins: BTreeMap<String, (TestPin, Vec<String>)> = BTreeMap::new();
    for target in &report.targets {
        for pin in target
            .selected_pins
            .iter()
            .chain(&target.production_base_witnesses)
        {
            let entry = pins
                .entry(pin.coordinate.clone())
                .or_insert_with(|| (pin.clone(), Vec::new()));
            eyre::ensure!(
                entry.0 == *pin,
                "contradictory reviewed JUnit pin: {}",
                pin.coordinate
            );
            entry.1.push(target.target_id.clone());
        }
    }
    for (pin, target_ids) in pins.into_values() {
        cancel.bail_if_cancelled()?;
        match verify_one(report, cache, &pin) {
            Ok(artifact) => report.artifacts.push(artifact),
            Err(error) => {
                issue(
                    report,
                    None,
                    if pin.authority == "existing_production_base_classpath" {
                        "production"
                    } else {
                        "test_only"
                    },
                    if pin.authority == "existing_production_base_classpath" {
                        "production_base_artifact_missing_or_corrupt"
                    } else {
                        "test_artifact_missing_or_corrupt"
                    },
                    format!("{error:#}"),
                );
                let issue = report.issues.last_mut().expect("just appended issue");
                issue.target_ids = target_ids;
                issue.coordinate = Some(pin.coordinate.clone());
                issue.path = Some(cache_path(cache, &pin.coordinate)?.display().to_string());
                issue.expected = Some(pin.content_hash.clone());
            }
        }
    }
    Ok(())
}

pub(super) fn cache_path(cache: &Path, coordinate: &str) -> eyre::Result<std::path::PathBuf> {
    eyre::ensure!(
        coordinate
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b".:_-".contains(&b))
            && !coordinate.contains(".."),
        "unsafe JUnit coordinate: {coordinate}"
    );
    Ok(maven_cache_path_for(
        cache,
        &MavenCoordinate::parse(coordinate)?,
    ))
}

pub(super) fn verify_one(
    report: &mut PreflightReport,
    cache: &Path,
    pin: &TestPin,
) -> eyre::Result<VerifiedTestArtifact> {
    let path = cache_path(cache, &pin.coordinate)?;
    let relative = path
        .strip_prefix(cache)?
        .to_string_lossy()
        .replace('\\', "/");
    let bytes = read_input(report, cache, &relative, MAX_ARTIFACT_BYTES)?;
    let expected = ContentHash::parse(&pin.content_hash).map_err(eyre::Report::msg)?;
    let actual = ContentHash::from_bytes(&bytes, expected.algorithm);
    eyre::ensure!(
        expected == actual,
        "JUnit artifact hash mismatch for {}: expected {}, actual {actual}",
        path.display(),
        pin.content_hash
    );
    let full_sha256 = sha256(&bytes);
    eyre::ensure!(
        pin.full_sha256
            .as_ref()
            .is_none_or(|hash| *hash == full_sha256)
            && pin.bytes.is_none_or(|size| size == bytes.len() as u64),
        "JUnit artifact full digest/length mismatch: {}",
        path.display()
    );
    Ok(VerifiedTestArtifact {
        coordinate: pin.coordinate.clone(),
        content_hash: pin.content_hash.clone(),
        sha256: full_sha256,
        bytes: bytes.len() as u64,
        cache_path: path.display().to_string(),
    })
}
