//! Apply a pinned release path mask and divergent-file overlays before sync.
//!
//! This is deliberately source-only. Tagged Gradle inputs and published-JAR
//! parity have separate gates. The whole artifact map is swapped only after
//! the report, every retained source and every overlay pass validation.

use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::Path;

use eyre::{Result, WrapErr, ensure};

use super::context::ProjectionContext;
use super::inputs::{apply_explicit_inputs, render_java_artifact};
use super::manifest::ReleaseBaselineBinding;
use super::provenance::sha256;
use super::release_baseline::{
    BaselinePathClass, BaselinePathRecord, ReleaseBaselineReport, ReleaseBaselineTargetReport,
    read_pinned_blob,
};
use super::sync::ProjectedArtifact;

const REPORT_SCHEMA: &str = "sfm:release-baseline-comparison@1";
const SOURCE_ROOT: &str = "platform/minecraft/src";
const RELEASE_IMPORT_PREFIX: &str = "platform/minecraft/release-baselines/";

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct ReleaseApplyReport {
    pub retained_source_files: usize,
    pub masked_development_files: usize,
    pub overlaid_release_files: usize,
    pub pinned_tag_fallback_files: usize,
}

/// Read the exact release-source membership mask before rendering Java.
///
/// A caller can exclude every primary `src/...` file not in this set, so a
/// development-only file with invalid source for this release never reaches
/// the Java renderer. `apply_release_baseline` still verifies content hashes
/// and applies overlays after collection.
///
/// # Errors
///
/// Returns an error if the pinned report is missing, changed, or malformed.
pub fn release_source_paths(
    repo_root: &Path,
    binding: &ReleaseBaselineBinding,
) -> Result<BTreeSet<String>> {
    let report = read_pinned_report(repo_root, binding)?;
    let target = report
        .targets
        .first()
        .ok_or_else(|| eyre::eyre!("release import manifest contains no target"))?;
    let (paths, _) = validate_target_report(target)?;
    Ok(paths)
}

/// Apply one pinned release source mask to already-collected artifacts.
///
/// Only `src/...` entries are filtered. Non-source project inputs are left
/// alone for the separate Gradle release-input selector. Source Java overlays
/// pass through the same renderer and generated banner as ordinary inputs.
///
/// # Errors
///
/// Returns an error for a changed import manifest, malformed report, missing
/// or changed canonical source relied on by the release, unsafe overlay path,
/// or overlay whose bytes differ from its declared release hash. The caller's
/// artifact map is unchanged on error.
pub fn apply_release_baseline(
    repo_root: &Path,
    binding: &ReleaseBaselineBinding,
    context: &ProjectionContext,
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
) -> Result<ReleaseApplyReport> {
    let report = read_pinned_report(repo_root, binding)?;
    let target = report
        .targets
        .first()
        .ok_or_else(|| eyre::eyre!("release import manifest contains no target"))?;
    let (release_paths, explicit_inputs) = validate_target_report(target)?;

    let mut pending = artifacts.clone();
    let masked_development_files = pending
        .keys()
        .filter(|path| path.starts_with("src/") && !release_paths.contains(*path))
        .count();
    pending.retain(|path, _| !path.starts_with("src/") || release_paths.contains(path));
    let mut pinned_tag_fallback_files = 0;
    for (path, record) in &target.paths {
        if record.classification != BaselinePathClass::Unchanged {
            continue;
        }
        let expected = record
            .release_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("release source `{path}` has no declared SHA-256"))?;
        if pending
            .get(path)
            .is_some_and(|artifact| sha256(&artifact.source_bytes) == expected)
        {
            continue;
        }
        // A common file may evolve on the development branch years after the
        // release. The exact tagged blob remains the immutable source of truth.
        let oid = record
            .release_blob_oid
            .as_deref()
            .ok_or_else(|| eyre::eyre!("release source `{path}` has no pinned Git blob OID"))?;
        let bytes = read_pinned_blob(repo_root, &target.tag_commit, path, oid, expected)?;
        let mut artifact = ProjectedArtifact {
            // Logical provenance path: these bytes come from the pinned Git
            // tree, not a physical file beneath this path in the worktree.
            source_path: format!(
                "platform/minecraft/release-baselines/{}/tag-tree/{path}",
                target.release_tag
            ),
            source_bytes: bytes.clone(),
            output_bytes: bytes,
            overlay: Some(format!("release-{}-pinned-tag", target.release_tag)),
        };
        render_java_artifact(path, &mut artifact, context)?;
        pending.insert(path.clone(), artifact);
        pinned_tag_fallback_files += 1;
    }

    apply_explicit_inputs(repo_root, &mut pending, &explicit_inputs, context)?;
    for path in &release_paths {
        let record = &target.paths[path];
        let artifact = pending
            .get_mut(path)
            .ok_or_else(|| eyre::eyre!("release source `{path}` was not produced"))?;
        let expected = record
            .release_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("release source `{path}` has no declared SHA-256"))?;
        ensure!(
            sha256(&artifact.source_bytes) == expected,
            "release source `{path}` differs from the pinned tag SHA-256"
        );
        if record.release_overlay_path.is_some() {
            artifact.overlay = Some(format!("release-{}", target.release_tag));
        }
    }

    let result = ReleaseApplyReport {
        retained_source_files: release_paths.len(),
        masked_development_files,
        overlaid_release_files: explicit_inputs.len(),
        pinned_tag_fallback_files,
    };
    *artifacts = pending;
    Ok(result)
}

fn read_pinned_report(
    repo_root: &Path,
    binding: &ReleaseBaselineBinding,
) -> Result<ReleaseBaselineReport> {
    ensure!(
        is_lower_hex(&binding.import_manifest_sha256, 64),
        "release import manifest SHA-256 must be 64 lowercase hexadecimal characters"
    );
    ensure!(
        is_lower_hex(&binding.tag_commit, 40),
        "release tag commit must be 40 lowercase hexadecimal characters"
    );
    validate_repo_path(&binding.import_manifest, RELEASE_IMPORT_PREFIX)?;
    let root = fs::canonicalize(repo_root)
        .wrap_err_with(|| format!("cannot resolve repository root '{}'", repo_root.display()))?;
    ensure!(root.is_dir(), "repository root must be a directory");
    let mut manifest_path = root.clone();
    for segment in binding.import_manifest.split('/') {
        manifest_path.push(segment);
        let metadata = fs::symlink_metadata(&manifest_path).wrap_err_with(|| {
            format!(
                "cannot inspect release import path '{}'",
                manifest_path.display()
            )
        })?;
        ensure!(
            !metadata.file_type().is_symlink(),
            "release import path '{}' traverses a symlink",
            binding.import_manifest
        );
    }
    ensure!(
        manifest_path.is_file(),
        "release import manifest must be a regular file"
    );
    let bytes = fs::read(&manifest_path).wrap_err_with(|| {
        format!(
            "cannot read release import manifest '{}'",
            manifest_path.display()
        )
    })?;
    ensure!(
        sha256(&bytes) == format!("sha256:{}", binding.import_manifest_sha256),
        "release import manifest '{}' differs from the pinned SHA-256",
        binding.import_manifest
    );
    let json = std::str::from_utf8(&bytes).wrap_err("release import manifest is not UTF-8")?;
    let report: ReleaseBaselineReport =
        facet_json::from_str(json).wrap_err("cannot parse release import manifest")?;
    ensure!(
        report.schema == REPORT_SCHEMA,
        "unsupported release import schema '{}': expected {REPORT_SCHEMA}",
        report.schema
    );
    ensure!(
        report.canonical_source_root == SOURCE_ROOT,
        "release import must describe {SOURCE_ROOT}"
    );
    ensure!(
        is_lower_hex(&report.canonical_head, 40) && is_sha256(&report.canonical_source_sha256),
        "release import has invalid canonical source identity"
    );
    ensure!(
        !report.jar_parity_proven,
        "source comparison cannot claim JAR parity"
    );
    ensure!(
        report.targets.len() == 1,
        "a pinned release import must contain exactly one target"
    );
    let target = &report.targets[0];
    ensure!(
        target.target_id == binding.target_id,
        "release import target '{}' does not match pinned target '{}'",
        target.target_id,
        binding.target_id
    );
    ensure!(
        target.tag_commit == binding.tag_commit,
        "release import tag commit does not match pinned commit"
    );
    ensure!(
        is_release_label(&target.release_tag),
        "release import has invalid tag label '{}'",
        target.release_tag
    );
    ensure!(
        binding.import_manifest
            == format!("{RELEASE_IMPORT_PREFIX}{}/import.json", target.release_tag),
        "release import manifest path does not match its tag label"
    );
    Ok(report)
}

fn validate_target_report(
    target: &ReleaseBaselineTargetReport,
) -> Result<(BTreeSet<String>, BTreeMap<String, String>)> {
    let mut membership = BTreeSet::new();
    let mut overlays = BTreeMap::new();
    let mut casefold = BTreeSet::new();
    let mut counts = [0_u64; 4];
    for (path, record) in &target.paths {
        validate_repo_path(path, "src/")?;
        ensure!(
            casefold.insert(path.to_ascii_lowercase()),
            "case-colliding release path `{path}`"
        );
        let expected_category = match record.classification {
            BaselinePathClass::Unchanged => {
                counts[0] += 1;
                0
            }
            BaselinePathClass::Changed => {
                counts[1] += 1;
                1
            }
            BaselinePathClass::ReleaseOnly => {
                counts[2] += 1;
                2
            }
            BaselinePathClass::CanonicalOnly => {
                counts[3] += 1;
                3
            }
        };
        validate_record(path, record, expected_category, &target.release_tag)?;
        if expected_category == 3 {
            continue;
        }
        membership.insert(path.clone());
        if let Some(overlay) = &record.release_overlay_path {
            overlays.insert(path.clone(), overlay.clone());
        }
    }
    ensure!(
        counts
            == [
                target.unchanged,
                target.changed,
                target.release_only,
                target.canonical_only
            ],
        "release import path counts do not match the path records"
    );
    Ok((membership, overlays))
}

fn validate_record(
    path: &str,
    record: &BaselinePathRecord,
    category: usize,
    release_tag: &str,
) -> Result<()> {
    if category == 3 {
        ensure!(
            record.release_git_mode.is_none()
                && record.release_blob_oid.is_none()
                && record.release_sha256.is_none()
                && record.release_overlay_path.is_none(),
            "canonical-only path `{path}` unexpectedly declares release content"
        );
        ensure!(
            record.canonical_sha256.as_deref().is_some_and(is_sha256),
            "canonical-only path `{path}` has no valid canonical SHA-256"
        );
        return Ok(());
    }
    ensure!(
        matches!(
            record.release_git_mode.as_deref(),
            Some("100644" | "100755")
        ),
        "release path `{path}` has unsupported Git mode"
    );
    ensure!(
        record
            .release_blob_oid
            .as_deref()
            .is_some_and(|oid| is_lower_hex(oid, 40)),
        "release path `{path}` has no valid Git blob OID"
    );
    ensure!(
        record.release_sha256.as_deref().is_some_and(is_sha256),
        "release path `{path}` has no valid SHA-256"
    );
    match category {
        0 => {
            ensure!(
                record.release_overlay_path.is_none(),
                "unchanged release path `{path}` unexpectedly has an overlay"
            );
            ensure!(
                record.canonical_sha256 == record.release_sha256,
                "unchanged release path `{path}` has unequal canonical and release hashes"
            );
        }
        1 | 2 => {
            let overlay = record.release_overlay_path.as_deref().ok_or_else(|| {
                eyre::eyre!("divergent release path `{path}` has no overlay path")
            })?;
            validate_repo_path(overlay, RELEASE_IMPORT_PREFIX)?;
            ensure!(
                overlay == format!("{RELEASE_IMPORT_PREFIX}{release_tag}/overlays/{path}"),
                "release overlay `{overlay}` does not mirror `{path}` under its tag"
            );
            if category == 1 {
                ensure!(
                    record.canonical_sha256.as_deref().is_some_and(is_sha256),
                    "changed release path `{path}` has no valid canonical SHA-256"
                );
                ensure!(
                    record.canonical_sha256 != record.release_sha256,
                    "changed release path `{path}` has equal canonical and release hashes"
                );
            } else {
                ensure!(
                    record.canonical_sha256.is_none(),
                    "release-only path `{path}` unexpectedly has a canonical hash"
                );
            }
        }
        _ => unreachable!("category is a BaselinePathClass variant"),
    }
    Ok(())
}

fn is_lower_hex(value: &str, expected_len: usize) -> bool {
    value.len() == expected_len
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || matches!(byte, b'a'..=b'f'))
}

fn is_release_label(value: &str) -> bool {
    !value.is_empty()
        && value.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"._-".contains(&byte)
        })
}

fn is_sha256(value: &str) -> bool {
    value
        .strip_prefix("sha256:")
        .is_some_and(|hex| is_lower_hex(hex, 64))
}

fn validate_repo_path(path: &str, required_prefix: &str) -> Result<()> {
    ensure!(
        path.starts_with(required_prefix),
        "repository path `{path}` must start with `{required_prefix}`"
    );
    ensure!(
        !path
            .chars()
            .any(|character| character.is_control() || "\\<>:\"|?*".contains(character))
            && path.split('/').all(|part| !part.is_empty()
                && part != "."
                && part != ".."
                && !part.ends_with('.')
                && !part.ends_with(' ')),
        "repository path `{path}` is not a portable exact relative path"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::process::Command;

    use tempfile::TempDir;

    use super::*;
    use crate::source_projection::inputs::collect_projected_inputs_with_allowlist;

    fn artifact(path: &str, bytes: &[u8]) -> ProjectedArtifact {
        ProjectedArtifact {
            source_path: path.to_owned(),
            source_bytes: bytes.to_vec(),
            output_bytes: bytes.to_vec(),
            overlay: None,
        }
    }

    fn context() -> ProjectionContext {
        ProjectionContext {
            minecraft_version: "1.19.2".to_owned(),
            preset: "released-4.34.0".to_owned(),
            features: BTreeMap::new(),
            targets: BTreeMap::from([("mc_1_19_2".to_owned(), true)]),
        }
    }

    fn record(
        classification: BaselinePathClass,
        release: Option<&[u8]>,
        canonical: Option<&[u8]>,
        overlay: Option<&str>,
    ) -> BaselinePathRecord {
        BaselinePathRecord {
            classification,
            release_git_mode: release.map(|_| "100644".to_owned()),
            release_blob_oid: release.map(|_| "a".repeat(40)),
            release_sha256: release.map(sha256),
            canonical_sha256: canonical.map(sha256),
            release_overlay_path: overlay.map(str::to_owned),
        }
    }

    fn report(commit: &str, paths: BTreeMap<String, BaselinePathRecord>) -> ReleaseBaselineReport {
        let mut counts = [0_u64; 4];
        for record in paths.values() {
            match record.classification {
                BaselinePathClass::Unchanged => counts[0] += 1,
                BaselinePathClass::Changed => counts[1] += 1,
                BaselinePathClass::ReleaseOnly => counts[2] += 1,
                BaselinePathClass::CanonicalOnly => counts[3] += 1,
            }
        }
        ReleaseBaselineReport {
            schema: REPORT_SCHEMA.to_owned(),
            canonical_source_root: SOURCE_ROOT.to_owned(),
            canonical_head: "c".repeat(40),
            canonical_source_sha256: sha256(b"test canonical source inventory"),
            jar_parity_proven: false,
            targets: vec![ReleaseBaselineTargetReport {
                target_id: "1.19.2".to_owned(),
                release_tag: "4.34.0-1.19.2".to_owned(),
                tag_commit: commit.to_owned(),
                unchanged: counts[0],
                changed: counts[1],
                release_only: counts[2],
                canonical_only: counts[3],
                paths,
            }],
        }
    }

    fn write_report(root: &Path, report: &ReleaseBaselineReport) -> ReleaseBaselineBinding {
        let relative = "platform/minecraft/release-baselines/4.34.0-1.19.2/import.json";
        let path = root.join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        let bytes = report.to_json().unwrap().into_bytes();
        fs::write(path, &bytes).unwrap();
        ReleaseBaselineBinding {
            target_id: "1.19.2".to_owned(),
            tag_commit: report.targets[0].tag_commit.clone(),
            import_manifest: relative.to_owned(),
            import_manifest_sha256: sha256(&bytes).trim_start_matches("sha256:").to_owned(),
        }
    }

    fn mixed_fixture() -> (
        TempDir,
        ReleaseBaselineBinding,
        BTreeMap<String, ProjectedArtifact>,
    ) {
        let root = tempfile::tempdir().unwrap();
        let prefix = "platform/minecraft/release-baselines/4.34.0-1.19.2/overlays/";
        let changed_overlay = format!("{prefix}src/changed.txt");
        let old_overlay = format!("{prefix}src/old.txt");
        for (path, bytes) in [
            (&changed_overlay, b"old".as_slice()),
            (&old_overlay, b"old-only".as_slice()),
        ] {
            let path = root.path().join(path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, bytes).unwrap();
        }
        let report = report(
            &"a".repeat(40),
            BTreeMap::from([
                (
                    "src/same.txt".to_owned(),
                    record(
                        BaselinePathClass::Unchanged,
                        Some(b"same"),
                        Some(b"same"),
                        None,
                    ),
                ),
                (
                    "src/changed.txt".to_owned(),
                    record(
                        BaselinePathClass::Changed,
                        Some(b"old"),
                        Some(b"new"),
                        Some(&changed_overlay),
                    ),
                ),
                (
                    "src/old.txt".to_owned(),
                    record(
                        BaselinePathClass::ReleaseOnly,
                        Some(b"old-only"),
                        None,
                        Some(&old_overlay),
                    ),
                ),
                (
                    "src/dev-only.txt".to_owned(),
                    record(
                        BaselinePathClass::CanonicalOnly,
                        None,
                        Some(b"dev-only"),
                        None,
                    ),
                ),
            ]),
        );
        let binding = write_report(root.path(), &report);
        let artifacts = BTreeMap::from([
            ("src/same.txt".to_owned(), artifact("src/same.txt", b"same")),
            (
                "src/changed.txt".to_owned(),
                artifact("src/changed.txt", b"new"),
            ),
            (
                "src/dev-only.txt".to_owned(),
                artifact("src/dev-only.txt", b"dev-only"),
            ),
            (
                "build.gradle".to_owned(),
                artifact("build.gradle", b"build"),
            ),
        ]);
        (root, binding, artifacts)
    }

    #[test]
    fn exact_mask_keeps_release_sources_and_replaces_divergent_files() {
        let (root, binding, mut artifacts) = mixed_fixture();
        let result =
            apply_release_baseline(root.path(), &binding, &context(), &mut artifacts).unwrap();
        assert_eq!(result.retained_source_files, 3);
        assert_eq!(result.masked_development_files, 1);
        assert_eq!(result.overlaid_release_files, 2);
        assert_eq!(result.pinned_tag_fallback_files, 0);
        assert_eq!(artifacts["src/same.txt"].source_bytes, b"same");
        assert_eq!(artifacts["src/changed.txt"].source_bytes, b"old");
        assert_eq!(artifacts["src/old.txt"].source_bytes, b"old-only");
        assert!(!artifacts.contains_key("src/dev-only.txt"));
        assert_eq!(artifacts["build.gradle"].source_bytes, b"build");
    }

    #[test]
    fn release_mask_excludes_invalid_development_java_before_rendering() {
        let (root, binding, _) = mixed_fixture();
        let source_root = root.path().join("platform/minecraft/src");
        fs::create_dir_all(&source_root).unwrap();
        for (name, bytes) in [
            ("same.txt", b"same".as_slice()),
            ("changed.txt", b"new".as_slice()),
            (
                "dev-only.java",
                b"{% if features.unknown %}\nclass DevelopmentOnly {}\n{% endif %}\n".as_slice(),
            ),
        ] {
            fs::write(source_root.join(name), bytes).unwrap();
        }
        let allowlist = release_source_paths(root.path(), &binding).unwrap();
        assert!(!allowlist.contains("src/dev-only.java"));
        let mut artifacts = collect_projected_inputs_with_allowlist(
            &source_root,
            &[],
            &context(),
            &BTreeSet::new(),
            Some(&allowlist),
        )
        .unwrap();
        apply_release_baseline(root.path(), &binding, &context(), &mut artifacts).unwrap();
        assert_eq!(artifacts["src/changed.txt"].source_bytes, b"old");
        assert!(!artifacts.contains_key("src/dev-only.java"));
    }

    #[test]
    fn changed_report_or_overlay_fails_without_mutating_artifacts() {
        let (root, binding, mut artifacts) = mixed_fixture();
        let original = artifacts.clone();
        fs::write(root.path().join(&binding.import_manifest), b"{}\n").unwrap();
        assert!(apply_release_baseline(root.path(), &binding, &context(), &mut artifacts).is_err());
        assert_eq!(artifacts, original);

        let (root, binding, mut artifacts) = mixed_fixture();
        let original = artifacts.clone();
        let overlay = root
            .path()
            .join("platform/minecraft/release-baselines/4.34.0-1.19.2/overlays/src/changed.txt");
        fs::write(overlay, b"tampered").unwrap();
        assert!(apply_release_baseline(root.path(), &binding, &context(), &mut artifacts).is_err());
        assert_eq!(artifacts, original);
    }

    #[test]
    fn unsafe_overlay_path_fails_without_mutating_artifacts() {
        let (root, binding, mut artifacts) = mixed_fixture();
        let original = artifacts.clone();
        let json = fs::read_to_string(root.path().join(&binding.import_manifest)).unwrap();
        let mut report = ReleaseBaselineReport::from_json(&json).unwrap();
        report.targets[0]
            .paths
            .get_mut("src/changed.txt")
            .unwrap()
            .release_overlay_path = Some(
            "platform/minecraft/release-baselines/4.34.0-1.19.2/overlays/../secret.txt".to_owned(),
        );
        let binding = write_report(root.path(), &report);
        assert!(apply_release_baseline(root.path(), &binding, &context(), &mut artifacts).is_err());
        assert_eq!(artifacts, original);
    }

    fn git(root: &Path, args: &[&str]) -> String {
        let output = Command::new("git")
            .arg("-C")
            .arg(root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    #[test]
    fn canonical_drift_recovers_exact_pinned_git_blob() {
        let root = tempfile::tempdir().unwrap();
        git(root.path(), &["init", "-q"]);
        let source = root.path().join("platform/minecraft/src/Same.java");
        fs::create_dir_all(source.parent().unwrap()).unwrap();
        fs::write(&source, b"class Same {}\n").unwrap();
        git(root.path(), &["add", "platform/minecraft/src/Same.java"]);
        git(
            root.path(),
            &[
                "-c",
                "user.name=SFM Test",
                "-c",
                "user.email=sfm@example.invalid",
                "commit",
                "-qm",
                "release",
            ],
        );
        let commit = git(root.path(), &["rev-parse", "HEAD"]);
        let oid = git(
            root.path(),
            &["rev-parse", "HEAD:platform/minecraft/src/Same.java"],
        );
        let mut pinned = record(
            BaselinePathClass::Unchanged,
            Some(b"class Same {}\n"),
            Some(b"class Same {}\n"),
            None,
        );
        pinned.release_blob_oid = Some(oid);
        let report = report(
            &commit,
            BTreeMap::from([("src/Same.java".to_owned(), pinned)]),
        );
        let binding = write_report(root.path(), &report);
        let mut artifacts = BTreeMap::from([(
            "src/Same.java".to_owned(),
            artifact("src/Same.java", b"class Development {}\n"),
        )]);
        let result =
            apply_release_baseline(root.path(), &binding, &context(), &mut artifacts).unwrap();
        assert_eq!(result.pinned_tag_fallback_files, 1);
        assert_eq!(artifacts["src/Same.java"].source_bytes, b"class Same {}\n");
        assert!(artifacts["src/Same.java"].source_path.contains("tag-tree"));
        assert!(
            artifacts["src/Same.java"]
                .output_bytes
                .starts_with(b"// GENERATED")
        );
    }
}
