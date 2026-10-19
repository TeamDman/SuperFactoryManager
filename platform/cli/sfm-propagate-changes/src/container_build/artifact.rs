use super::context_file::ContextFile;
use super::context_manifest::ContextManifest;
use super::missing_artifact::MissingArtifact;
use crate::cancellation::CancellationToken;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::candidate_lock::is_reparse;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use eyre::ensure;
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::fs;
use std::io::Read as _;
use std::path::Path;

pub(super) fn missing(
    report: &mut ContextManifest,
    identity: impl Into<String>,
    reason: impl Into<String>,
) {
    report.missing.push(MissingArtifact { identity: identity.into(), reason: reason.into(),
        remediation: "Acquire or approve this exact pinned input separately, then rerun offline; no substitute was downloaded.".into() });
}

pub(super) fn put_bytes(
    stage: &Path,
    relative: &str,
    bytes: &[u8],
    parent: &str,
    report: &mut ContextManifest,
    max_bytes: u64,
) -> eyre::Result<()> {
    super::disk::check(stage, 2 * 1024 * 1024 * 1024)?;
    validate_relative_path(relative)?;
    let digest = sha256(bytes);
    if let Some(existing) = report.files.get(relative) {
        ensure!(
            existing.sha256 == digest,
            "conflicting context input: {relative}"
        );
        return Ok(());
    }
    ensure!(
        report.files.len() < 100_000,
        "context file-count budget exceeded"
    );
    report.total_bytes = report
        .total_bytes
        .checked_add(bytes.len() as u64)
        .ok_or_else(|| eyre::eyre!("context byte count overflow"))?;
    ensure!(
        report.total_bytes <= max_bytes,
        "context byte budget exceeded"
    );
    super::content_store::bytes(stage, relative, bytes)?;
    report.files.insert(
        relative.into(),
        ContextFile {
            path: relative.into(),
            bytes: bytes.len() as u64,
            sha256: digest,
            parent_identity: parent.into(),
            expected_hash: None,
            authenticated_parents: BTreeMap::new(),
        },
    );
    Ok(())
}

/// Hash and stage from the same open handle; corrupt bytes never publish.
#[expect(
    clippy::too_many_arguments,
    clippy::too_many_lines,
    reason = "Explicit source/destination/hash/budget boundary for cache export"
)]
pub(super) fn copy_cached(
    root: &Path,
    relative: &str,
    stage: &Path,
    destination: &str,
    expected: ContentHash,
    expected_bytes: Option<u64>,
    parent: &str,
    report: &mut ContextManifest,
    max_bytes: u64,
    cancellation: &CancellationToken,
) -> eyre::Result<bool> {
    super::disk::check(stage, 2 * 1024 * 1024 * 1024)?;
    validate_relative_path(relative)?;
    validate_relative_path(destination)?;
    if let Some(existing) = report.files.get_mut(destination) {
        cancellation.bail_if_cancelled()?;
        ensure!(
            expected_bytes.is_none_or(|size| size == existing.bytes),
            "conflicting artifact size: {destination}"
        );
        let staged = checked_file(stage, destination)?;
        ensure!(
            ContentHash::from_path(&staged, expected.algorithm)? == expected,
            "conflicting artifact hash: {destination}"
        );
        existing
            .authenticated_parents
            .insert(parent.into(), expected.to_string());
        return Ok(true);
    }
    let result = (|| -> eyre::Result<ContextFile> {
        cancellation.bail_if_cancelled()?;
        let path = checked_file(root, relative)?;
        let mut input = fs::File::open(&path)?;
        let identity = crate::file_identity::file_identity(&input)?;
        let metadata = input.metadata()?;
        ensure!(
            metadata.is_file() && !is_reparse(&metadata),
            "artifact is not a regular file"
        );
        ensure!(
            expected_bytes.is_none_or(|size| size == metadata.len()),
            "artifact size mismatch"
        );
        ensure!(
            metadata.len() <= 2 * 1024 * 1024 * 1024
                && report.total_bytes.saturating_add(metadata.len()) <= max_bytes,
            "artifact/context byte budget exceeded"
        );
        ensure!(
            report.files.len() < 100_000,
            "context file-count budget exceeded"
        );

        let mut sha1 = sha1::Sha1::new();
        let mut blake = blake3::Hasher::new();
        let mut sha256 = Sha256::new();
        let mut total = 0_u64;
        let mut buffer = vec![0_u8; 65_536].into_boxed_slice();
        loop {
            cancellation.bail_if_cancelled()?;
            let read = input.read(&mut buffer)?;
            if read == 0 {
                break;
            }
            total += read as u64;
            ensure!(total <= metadata.len(), "artifact grew during export");
            sha1.update(&buffer[..read]);
            blake.update(&buffer[..read]);
            sha256.update(&buffer[..read]);
        }
        ensure!(total == metadata.len(), "artifact shrank during export");
        let actual = match expected.algorithm {
            ContentHashAlgorithm::Sha1 => format!("sha1:{:x}", sha1.finalize()),
            ContentHashAlgorithm::Blake3 => {
                let mut bytes = [0_u8; 20];
                blake.finalize_xof().fill(&mut bytes);
                ContentHash {
                    algorithm: ContentHashAlgorithm::Blake3,
                    value: bytes,
                }
                .to_string()
            }
        };
        ensure!(
            actual == expected.to_string(),
            "artifact hash mismatch: expected {expected}, got {actual}"
        );
        let rechecked = fs::File::open(checked_file(root, relative)?)?;
        ensure!(
            crate::file_identity::file_identity(&rechecked)? == identity,
            "artifact replaced during export"
        );
        let digest = format!("sha256:{:x}", sha256.finalize());
        super::content_store::file(stage, destination, &digest, &mut input)?;
        Ok(ContextFile {
            path: destination.into(),
            bytes: total,
            sha256: digest,
            parent_identity: parent.into(),
            expected_hash: Some(expected.to_string()),
            authenticated_parents: BTreeMap::from([(parent.into(), expected.to_string())]),
        })
    })();
    match result {
        Ok(file) => {
            report.total_bytes += file.bytes;
            report.files.insert(destination.into(), file);
            Ok(true)
        }
        Err(error) => {
            cancellation.bail_if_cancelled()?;
            missing(report, destination, format!("{error:#}"));
            Ok(false)
        }
    }
}

pub(super) fn portable_cache_path(path: &Path) -> eyre::Result<String> {
    let text = path
        .to_str()
        .ok_or_else(|| eyre::eyre!("non-Unicode cache path"))?
        .replace('\\', "/");
    let relative = text
        .strip_prefix("$sfm-cache/")
        .ok_or_else(|| eyre::eyre!("artifact is not rooted at $sfm-cache"))?;

    validate_relative_path(relative)?;
    ensure!(
        ["maven/", "minecraft/", "source-builds/"]
            .iter()
            .any(|prefix| relative.starts_with(prefix)),
        "unapproved cache family"
    );
    let lowercase = relative.to_ascii_lowercase();
    ensure!(
        !lowercase
            .split('/')
            .any(|part| matches!(part, "run" | "world" | "saves" | "accounts" | "home")),
        "private/runtime cache subtree"
    );
    ensure!(
        ![".exe", ".dll", ".lock", ".log"]
            .iter()
            .any(|suffix| lowercase.ends_with(suffix)),
        "nonportable/runtime artifact"
    );
    Ok(relative.into())
}
