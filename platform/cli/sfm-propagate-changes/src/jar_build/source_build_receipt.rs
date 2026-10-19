//! Digest-bound execution receipts for the formerly weak 26.1.2 Mekanism input.
use crate::jar_build::engine::SourceBuildProvenance;
use crate::jar_build::engine::SourceBuildSystem;
use crate::jar_build::engine::SourceGitProvenance;
use crate::jar_build::hash::ContentHash;
use crate::source_projection::candidate_lock::checked_file;
use eyre::ensure;
use facet::Facet;
use sha2::Digest as _;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;

pub(crate) const RECEIPT_KEY: &str = "SFM_PRODUCER_RECEIPT_SHA256";

#[derive(Facet)]
struct Output {
    path: String,
    bytes: u64,
    sha256: String,
    content_hash: ContentHash,
}

#[derive(Facet)]
struct Receipt {
    schema: String,
    repository_url: String,
    commit: String,
    source_tree: String,
    source_archive_sha256: String,
    producer_container: String,
    producer_image: String,
    java_archive_sha256: String,
    gradle_archive_sha256: String,
    download_inventory_sha256: String,
    build_log_sha256: String,
    build_system: String,
    tasks: Vec<String>,
    environment: BTreeMap<String, String>,
    exit_code: i32,
    task_output_cache_reused: bool,
    tracked_source_unchanged: bool,
    outputs: BTreeMap<String, Output>,
}

fn sha256(bytes: &[u8]) -> String {
    format!("{:x}", sha2::Sha256::digest(bytes))
}

fn digest(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
}

pub(crate) fn required(coordinate: Option<&str>, build: Option<&SourceBuildProvenance>) -> bool {
    coordinate.is_some_and(|c| c.starts_with("mekanism:Mekanism:26.1.2-"))
        || build.is_some_and(|b| b.environment.contains_key(RECEIPT_KEY))
}

/// The reference is a reserved source-build environment key, preserving schema compatibility.
pub(crate) fn verify(
    cache: &Path,
    artifact: &Path,
    coordinate: Option<&str>,
    expected: ContentHash,
    git: Option<&SourceGitProvenance>,
    build: Option<&SourceBuildProvenance>,
) -> eyre::Result<Option<(String, Vec<u8>)>> {
    if !required(coordinate, build) {
        return Ok(None);
    }
    let git = git.ok_or_else(|| eyre::eyre!("producer receipt requires source Git provenance"))?;
    let build =
        build.ok_or_else(|| eyre::eyre!("producer receipt requires source build provenance"))?;
    let pin = build.environment.get(RECEIPT_KEY).ok_or_else(|| eyre::eyre!("missing digest-bound producer receipt; weak metadata cannot admit this source-built input"))?;
    ensure!(digest(pin), "invalid producer receipt SHA256");
    let relative = format!("producer-receipts/{pin}.json");
    let path = checked_file(cache, &relative)?;
    ensure!(
        fs::metadata(&path)?.len() <= 2 * 1024 * 1024,
        "producer receipt byte budget exceeded"
    );
    let raw = fs::read(path)?;
    ensure!(sha256(&raw) == *pin, "producer receipt digest mismatch");
    let receipt: Receipt = facet_json::from_slice(&raw)?;
    ensure!(
        receipt.schema == "sfm:source_producer@1",
        "unknown producer receipt schema"
    );
    ensure!(
        !git.dirty
            && git.remote_url.as_deref() == Some(receipt.repository_url.as_str())
            && git.commit == receipt.commit,
        "producer repository or revision mismatch"
    );
    ensure!(
        receipt.source_tree.len() == 40
            && receipt.source_tree.bytes().all(|b| b.is_ascii_hexdigit()),
        "invalid producer source tree"
    );
    for value in [
        &receipt.source_archive_sha256,
        &receipt.producer_container,
        &receipt.producer_image,
        &receipt.java_archive_sha256,
        &receipt.gradle_archive_sha256,
        &receipt.download_inventory_sha256,
        &receipt.build_log_sha256,
    ] {
        ensure!(digest(value), "invalid producer input digest");
    }
    ensure!(
        receipt.exit_code == 0
            && !receipt.task_output_cache_reused
            && receipt.tracked_source_unchanged,
        "producer did not complete a clean build without task-output cache reuse"
    );
    ensure!(
        matches!(build.build_system, SourceBuildSystem::GradleWrapper)
            && receipt.build_system == "gradle-wrapper"
            && receipt.tasks == build.tasks,
        "producer build recipe mismatch"
    );
    let mut environment = build.environment.clone();
    environment.remove(RECEIPT_KEY);
    ensure!(
        receipt.environment == environment,
        "producer environment mismatch"
    );
    let coordinate = coordinate.ok_or_else(|| eyre::eyre!("producer output has no coordinate"))?;
    let output = receipt
        .outputs
        .get(coordinate)
        .ok_or_else(|| eyre::eyre!("producer does not record this artifact"))?;
    ensure!(
        output.path == build.output_path.to_string_lossy().replace('\\', "/")
            && output.content_hash == expected,
        "producer output path or locked content hash mismatch"
    );
    ensure!(
        digest(&output.sha256) && output.bytes <= 64 * 1024 * 1024,
        "invalid producer output digest or size"
    );
    let metadata = fs::metadata(artifact)?;
    ensure!(
        metadata.is_file() && metadata.len() == output.bytes,
        "producer output size mismatch"
    );
    ensure!(
        sha256(&fs::read(artifact)?) == output.sha256
            && ContentHash::from_path(artifact, expected.algorithm)? == expected,
        "producer output bytes mismatch"
    );
    Ok(Some((relative, raw)))
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use crate::jar_build::hash::ContentHashAlgorithm;
    const COORDINATE: &str = "mekanism:Mekanism:26.1.2-10.8.0.86";

    fn fixture() -> (
        tempfile::TempDir,
        std::path::PathBuf,
        SourceGitProvenance,
        SourceBuildProvenance,
        ContentHash,
        Receipt,
    ) {
        let cache = tempfile::tempdir().unwrap();
        let path = cache.path().join("output.jar");
        fs::write(&path, b"fresh producer output").unwrap();
        let hash = ContentHash::from_path(&path, ContentHashAlgorithm::Blake3).unwrap();
        let git = SourceGitProvenance {
            root: "unused".into(),
            commit: "a".repeat(40),
            branch: "26.1".into(),
            dirty: false,
            remote_url: Some("https://github.com/mekanism/Mekanism/".into()),
        };
        let build = SourceBuildProvenance {
            build_system: SourceBuildSystem::GradleWrapper,
            tasks: vec!["jar".into()],
            environment: BTreeMap::from([("BUILD_NUMBER".into(), "86".into())]),
            output_path: "build/libs/output.jar".into(),
        };
        let receipt = Receipt {
            schema: "sfm:source_producer@1".into(),
            repository_url: git.remote_url.clone().unwrap(),
            commit: git.commit.clone(),
            source_tree: "b".repeat(40),
            source_archive_sha256: "1".repeat(64),
            producer_container: "2".repeat(64),
            producer_image: "3".repeat(64),
            java_archive_sha256: "4".repeat(64),
            gradle_archive_sha256: "5".repeat(64),
            download_inventory_sha256: "6".repeat(64),
            build_log_sha256: "7".repeat(64),
            build_system: "gradle-wrapper".into(),
            tasks: build.tasks.clone(),
            environment: build.environment.clone(),
            exit_code: 0,
            task_output_cache_reused: false,
            tracked_source_unchanged: true,
            outputs: BTreeMap::from([(
                COORDINATE.into(),
                Output {
                    path: "build/libs/output.jar".into(),
                    bytes: fs::metadata(&path).unwrap().len(),
                    sha256: sha256(&fs::read(&path).unwrap()),
                    content_hash: hash,
                },
            )]),
        };
        (cache, path, git, build, hash, receipt)
    }

    pub(crate) fn resolver_fixture() -> (
        tempfile::TempDir,
        std::path::PathBuf,
        crate::jar_build::engine::ArtifactLockEntry,
    ) {
        let (cache, original, git, mut build, hash, receipt) = fixture();
        let path = cache.path().join("producer-outputs/test/output.jar");
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::rename(original, &path).unwrap();
        pin(cache.path(), &mut build, &receipt);
        let entry = crate::jar_build::engine::ArtifactLockEntry {
            coordinate: Some(COORDINATE.into()),
            source: crate::jar_build::engine::ArtifactSource::SourceBuild,
            repository: None,
            url: Some(git.remote_url.clone().unwrap()),
            cache_path: "$sfm-cache/producer-outputs/test/output.jar".into(),
            original_path: None,
            source_relative_path: Some(build.output_path.clone()),
            source_git: Some(git),
            source_build: Some(build),
            hash,
            weak: None,
        };
        (cache, path, entry)
    }

    fn pin(
        cache: &Path,
        build: &mut SourceBuildProvenance,
        receipt: &Receipt,
    ) -> std::path::PathBuf {
        let bytes = facet_json::to_string(receipt).unwrap().into_bytes();
        let digest = sha256(&bytes);
        let path = cache.join(format!("producer-receipts/{digest}.json"));
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, bytes).unwrap();
        build.environment.insert(RECEIPT_KEY.into(), digest);
        path
    }

    #[test]
    fn producer_requires_digest_reference_even_for_an_exact_artifact_hash() {
        let (cache, path, git, build, hash, _) = fixture();
        assert!(
            verify(
                cache.path(),
                &path,
                Some(COORDINATE),
                hash,
                Some(&git),
                Some(&build)
            )
            .is_err()
        );
    }

    #[test]
    fn producer_validates_receipt_and_output_bytes() {
        let (cache, path, git, mut build, hash, receipt) = fixture();
        let proof = pin(cache.path(), &mut build, &receipt);
        assert!(
            verify(
                cache.path(),
                &path,
                Some(COORDINATE),
                hash,
                Some(&git),
                Some(&build)
            )
            .unwrap()
            .is_some()
        );
        fs::write(&path, b"different output").unwrap();
        assert!(
            verify(
                cache.path(),
                &path,
                Some(COORDINATE),
                hash,
                Some(&git),
                Some(&build)
            )
            .is_err()
        );
        fs::remove_file(proof).unwrap();
        assert!(
            verify(
                cache.path(),
                &path,
                Some(COORDINATE),
                hash,
                Some(&git),
                Some(&build)
            )
            .is_err()
        );
    }

    #[test]
    fn producer_rejects_digest_mismatch_and_traversal_reference() {
        let (cache, path, git, mut build, hash, receipt) = fixture();
        let proof = pin(cache.path(), &mut build, &receipt);
        fs::write(proof, b"{}").unwrap();
        assert!(
            verify(
                cache.path(),
                &path,
                Some(COORDINATE),
                hash,
                Some(&git),
                Some(&build)
            )
            .is_err()
        );
        build
            .environment
            .insert(RECEIPT_KEY.into(), "../outside".into());
        assert!(
            verify(
                cache.path(),
                &path,
                Some(COORDINATE),
                hash,
                Some(&git),
                Some(&build)
            )
            .is_err()
        );
    }

    #[test]
    fn producer_rejects_rehashed_but_inconsistent_execution_receipts() {
        for case in 0..9 {
            let (cache, path, git, mut build, hash, mut receipt) = fixture();
            match case {
                0 => receipt.commit = "c".repeat(40),
                1 => receipt.repository_url = "https://example.invalid/other".into(),
                2 => receipt.exit_code = 1,
                3 => receipt.task_output_cache_reused = true,
                4 => receipt.tracked_source_unchanged = false,
                5 => receipt.java_archive_sha256 = "missing SDK pin".into(),
                6 => receipt.tasks = vec!["publish".into()],
                7 => receipt.outputs.get_mut(COORDINATE).unwrap().sha256 = "0".repeat(64),
                _ => {
                    receipt.outputs.get_mut(COORDINATE).unwrap().content_hash =
                        ContentHash::parse(&format!("blake3:{}", "0".repeat(40))).unwrap()
                }
            }
            pin(cache.path(), &mut build, &receipt);
            assert!(
                verify(
                    cache.path(),
                    &path,
                    Some(COORDINATE),
                    hash,
                    Some(&git),
                    Some(&build)
                )
                .is_err(),
                "inconsistent receipt case {case}"
            );
        }
    }
}
