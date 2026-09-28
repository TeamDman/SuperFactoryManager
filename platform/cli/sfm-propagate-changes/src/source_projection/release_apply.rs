//! Apply a pinned source path mask and divergent-file overlays before sync.
//!
//! Development projections also add pinned test fixtures. Gradle inputs and
//! published-JAR parity have separate gates. The whole artifact map is swapped
//! only after sources, overlays, and fixtures pass validation.

use super::context::ProjectionContext;
use super::development_baseline::DEVELOPMENT_SOURCE_SCHEMA;
use super::development_baseline::DevelopmentHeadSpec;
use super::development_fixtures::collect_verified_development_project_fixtures;
use super::development_gradle::verify_development_gradle_inputs;
use super::inputs::apply_explicit_inputs;
use super::inputs::render_java_artifact;
use super::manifest::BaselineKind;
use super::manifest::ReleaseBaselineBinding;
use super::provenance::sha256;
use super::release_baseline::BaselinePathClass;
use super::release_baseline::BaselinePathRecord;
use super::release_baseline::ReleaseBaselineReport;
use super::release_baseline::ReleaseBaselineTargetReport;
use super::release_baseline::read_pinned_blob;
use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

const REPORT_SCHEMA: &str = "sfm:release-baseline-comparison@1";
const SOURCE_ROOT: &str = "platform/minecraft/src";
const RELEASE_IMPORT_PREFIX: &str = "platform/minecraft/release-baselines/";
const DEVELOPMENT_IMPORT_PREFIX: &str = "platform/minecraft/development-baselines/";

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
    let (paths, _) = validate_target_report(target, import_prefix(binding.kind))?;
    validate_post_baseline_test_sources(binding, target)?;
    validate_post_baseline_canonical_sources(binding, target)?;
    Ok(paths)
}

/// Apply one pinned release source mask to already-collected artifacts.
///
/// Only `src/...` entries are filtered. Development fixtures are then added as
/// verified non-source inputs; Gradle inputs use a separate selector. Source
/// Java overlays pass through the same renderer and banner as ordinary inputs.
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
    let (release_paths, explicit_inputs) =
        validate_target_report(target, import_prefix(binding.kind))?;
    validate_post_baseline_test_sources(binding, target)?;
    validate_post_baseline_canonical_sources(binding, target)?;

    let mut pending = artifacts.clone();
    let masked_development_files = pending
        .keys()
        .filter(|path| path.starts_with("src/") && !release_paths.contains(*path))
        .count();
    pending.retain(|path, _| !path.starts_with("src/") || release_paths.contains(path));
    let canonical_sources = capture_post_baseline_canonical_sources(binding, &pending)?;
    let (pinned_tag_fallback_files, verified_template_drift) =
        apply_unchanged_sources(repo_root, binding, context, target, &mut pending)?;
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
            sha256(&artifact.source_bytes) == expected || verified_template_drift.contains(path),
            "release source `{path}` differs from the pinned tag SHA-256"
        );
        if record.release_overlay_path.is_some() {
            artifact.overlay = Some(match binding.kind {
                BaselineKind::ReleaseTag => format!("release-{}", target.release_tag),
                BaselineKind::DevelopmentHead => {
                    format!("development-head-{}", target.target_id)
                }
            });
        }
    }
    apply_post_baseline_canonical_sources(binding, &canonical_sources, &mut pending)?;

    if binding.kind == BaselineKind::DevelopmentHead {
        let spec = DevelopmentHeadSpec {
            target_id: binding.target_id.clone(),
            canonical_commit: binding
                .canonical_commit
                .clone()
                .ok_or_else(|| eyre::eyre!("development canonical commit is missing"))?,
            target_commit: binding.tag_commit.clone(),
        };
        let fixtures = collect_verified_development_project_fixtures(
            repo_root,
            &spec,
            binding
                .project_fixture_provenance_sha256
                .as_deref()
                .ok_or_else(|| eyre::eyre!("development fixture provenance hash is missing"))?,
        )?;
        for (path, artifact) in fixtures {
            ensure!(
                pending.insert(path.clone(), artifact).is_none(),
                "development fixture output '{path}' collides with a source artifact"
            );
        }
    }

    apply_post_baseline_test_sources(repo_root, binding, context, &mut pending)?;

    let result = ReleaseApplyReport {
        retained_source_files: release_paths.len(),
        masked_development_files,
        overlaid_release_files: explicit_inputs.len() - canonical_sources.len(),
        pinned_tag_fallback_files,
    };
    *artifacts = pending;
    Ok(result)
}

fn apply_unchanged_sources(
    repo_root: &Path,
    binding: &ReleaseBaselineBinding,
    context: &ProjectionContext,
    target: &ReleaseBaselineTargetReport,
    pending: &mut BTreeMap<String, ProjectedArtifact>,
) -> Result<(usize, BTreeSet<String>)> {
    let mut pinned_tag_fallback_files = 0;
    let mut verified_template_drift = BTreeSet::new();
    for (path, record) in &target.paths {
        if record.classification != BaselinePathClass::Unchanged {
            continue;
        }
        let expected = record
            .release_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("release source `{path}` has no declared SHA-256"))?;
        if binding.post_baseline_test_sources.contains_key(path) {
            ensure!(
                pending.contains_key(path),
                "post-baseline test source '{path}' is missing from canonical inputs"
            );
            let oid = record.release_blob_oid.as_deref().ok_or_else(|| {
                eyre::eyre!("post-baseline test source '{path}' has no pinned Git blob ID")
            })?;
            let bytes = read_pinned_blob(repo_root, &target.tag_commit, path, oid, expected)?;
            let mut pinned = ProjectedArtifact {
                source_path: format!(
                    "platform/minecraft/development-baselines/{}/committed-tree/{path}",
                    target.target_id
                ),
                source_bytes: bytes.clone(),
                output_bytes: bytes,
                overlay: Some("development-head-pinned-test".to_owned()),
            };
            render_java_artifact(path, &mut pinned, context)?;
            pending.insert(path.clone(), pinned);
            continue;
        }
        if pending
            .get(path)
            .is_some_and(|artifact| sha256(&artifact.source_bytes) == expected)
        {
            continue;
        }
        if binding.kind == BaselineKind::DevelopmentHead {
            // Canonical Java may gain Liquid-only guards after a development
            // head was imported. Accept that drift only when the selected
            // output remains byte-identical to the pinned committed source.
            // This does not let a semantic canonical edit silently change a
            // version snapshot; it must be imported/reviewed explicitly.
            let oid = record.release_blob_oid.as_deref().ok_or_else(|| {
                eyre::eyre!("development source `{path}` has no pinned Git blob OID")
            })?;
            let bytes = read_pinned_blob(repo_root, &target.tag_commit, path, oid, expected)?;
            ensure!(
                pending
                    .get(path)
                    .map(|artifact| rendered_java_matches_pinned(path, artifact, &bytes, context))
                    .transpose()?
                    .unwrap_or(false),
                "committed-development source '{path}' changed from its pinned canonical snapshot"
            );
            verified_template_drift.insert(path.clone());
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
            // For an Unchanged release record, `src/...` is the logical source
            // path within the preset's pinned tag snapshot, not necessarily
            // today's primary worktree. The Git blob and source SHA-256 above
            // verify those historical bytes. Keeping that logical provenance
            // also leaves checked-in release manifests stable as source evolves.
            source_path: path.clone(),
            source_bytes: bytes.clone(),
            output_bytes: bytes,
            overlay: None,
        };
        render_java_artifact(path, &mut artifact, context)?;
        pending.insert(path.clone(), artifact);
        pinned_tag_fallback_files += 1;
    }

    Ok((pinned_tag_fallback_files, verified_template_drift))
}

fn validate_post_baseline_canonical_sources(
    binding: &ReleaseBaselineBinding,
    target: &ReleaseBaselineTargetReport,
) -> Result<()> {
    ensure!(
        binding.kind == BaselineKind::DevelopmentHead
            || binding.post_baseline_canonical_sources.is_empty(),
        "release-tag binding cannot select post-baseline canonical sources"
    );
    let mut casefold = BTreeSet::new();
    for (path, selected) in &binding.post_baseline_canonical_sources {
        validate_repo_path(path, "src/main/java/")?;
        ensure!(
            has_exact_extension(path, "java"),
            "post-baseline canonical source '{path}' must be Java"
        );
        ensure!(
            casefold.insert(path.to_ascii_lowercase()),
            "case-colliding post-baseline canonical source '{path}'"
        );
        ensure!(
            is_lower_hex(&selected.source_sha256, 64) && is_lower_hex(&selected.output_sha256, 64),
            "post-baseline canonical source '{path}' needs source and output SHA-256"
        );
        let record = target.paths.get(path).ok_or_else(|| {
            eyre::eyre!("post-baseline canonical source '{path}' is absent from pinned membership")
        })?;
        ensure!(
            record.classification == BaselinePathClass::Changed,
            "post-baseline canonical source '{path}' must replace a changed pinned source"
        );
    }
    Ok(())
}

fn capture_post_baseline_canonical_sources(
    binding: &ReleaseBaselineBinding,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<BTreeMap<String, ProjectedArtifact>> {
    let mut selected = BTreeMap::new();
    for path in binding.post_baseline_canonical_sources.keys() {
        let artifact = artifacts.get(path).ok_or_else(|| {
            eyre::eyre!("post-baseline canonical source '{path}' is missing from primary inputs")
        })?;
        ensure!(
            artifact.source_path == *path && artifact.overlay.is_none(),
            "post-baseline canonical source '{path}' is not a primary source input"
        );
        selected.insert(path.clone(), artifact.clone());
    }
    Ok(selected)
}

fn apply_post_baseline_canonical_sources(
    binding: &ReleaseBaselineBinding,
    canonical_sources: &BTreeMap<String, ProjectedArtifact>,
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
) -> Result<()> {
    let mut replacements = Vec::new();
    for (path, selected) in &binding.post_baseline_canonical_sources {
        let canonical = canonical_sources.get(path).ok_or_else(|| {
            eyre::eyre!("post-baseline canonical source '{path}' was not captured")
        })?;
        ensure!(
            sha256(&canonical.source_bytes) == format!("sha256:{}", selected.source_sha256),
            "post-baseline canonical source '{path}' differs from its pinned SHA-256"
        );
        ensure!(
            sha256(&canonical.output_bytes) == format!("sha256:{}", selected.output_sha256),
            "post-baseline canonical output '{path}' differs from its pinned SHA-256"
        );
        let imported = artifacts.get(path).ok_or_else(|| {
            eyre::eyre!("post-baseline canonical source '{path}' has no verified imported overlay")
        })?;
        ensure!(
            canonical.output_bytes == imported.output_bytes,
            "post-baseline canonical output '{path}' differs from verified imported overlay"
        );
        replacements.push((path.clone(), canonical.clone()));
    }
    artifacts.extend(replacements);
    Ok(())
}

fn has_exact_extension(path: &str, expected: &str) -> bool {
    Path::new(path)
        .extension()
        .and_then(|extension| extension.to_str())
        == Some(expected)
}

fn rendered_java_matches_pinned(
    path: &str,
    current: &ProjectedArtifact,
    pinned_source: &[u8],
    context: &ProjectionContext,
) -> Result<bool> {
    if !has_exact_extension(path, "java") {
        return Ok(false);
    }
    let mut pinned = ProjectedArtifact {
        source_path: format!("pinned-committed-tree/{path}"),
        source_bytes: pinned_source.to_vec(),
        output_bytes: pinned_source.to_vec(),
        overlay: None,
    };
    render_java_artifact(path, &mut pinned, context)?;
    Ok(current.output_bytes == pinned.output_bytes)
}

fn validate_post_baseline_test_sources(
    binding: &ReleaseBaselineBinding,
    target: &ReleaseBaselineTargetReport,
) -> Result<()> {
    ensure!(
        binding.kind == BaselineKind::DevelopmentHead
            || binding.post_baseline_test_sources.is_empty(),
        "release-tag binding cannot declare post-baseline test sources"
    );
    let mut casefold = BTreeSet::new();
    for (path, digest) in &binding.post_baseline_test_sources {
        validate_repo_path(path, "src/test/java/")?;
        ensure!(
            has_exact_extension(path, "java"),
            "post-baseline test source '{path}' must be Java"
        );
        ensure!(
            is_lower_hex(digest, 64),
            "post-baseline test source '{path}' needs a bare lowercase SHA-256"
        );
        ensure!(
            casefold.insert(path.to_ascii_lowercase()),
            "case-colliding post-baseline test source '{path}'"
        );
        let record = target.paths.get(path).ok_or_else(|| {
            eyre::eyre!("post-baseline test source '{path}' is absent from pinned membership")
        })?;
        ensure!(
            record.classification == BaselinePathClass::Unchanged,
            "post-baseline test source '{path}' must have an unchanged pinned baseline"
        );
    }
    Ok(())
}

fn apply_post_baseline_test_sources(
    repo_root: &Path,
    binding: &ReleaseBaselineBinding,
    context: &ProjectionContext,
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
) -> Result<()> {
    if binding.post_baseline_test_sources.is_empty() {
        return Ok(());
    }
    ensure!(
        binding.kind == BaselineKind::DevelopmentHead,
        "release-tag binding cannot apply post-baseline test sources"
    );
    let inputs = binding
        .post_baseline_test_sources
        .keys()
        .map(|path| (path.clone(), format!("platform/minecraft/{path}")))
        .collect::<BTreeMap<_, _>>();
    let mut pending = artifacts.clone();
    apply_explicit_inputs(repo_root, &mut pending, &inputs, context)?;
    for (path, expected_hash) in &binding.post_baseline_test_sources {
        let artifact = pending
            .get_mut(path)
            .ok_or_else(|| eyre::eyre!("missing post-baseline test source '{path}'"))?;
        ensure!(
            sha256(&artifact.source_bytes) == format!("sha256:{expected_hash}"),
            "post-baseline test source '{path}' differs from its pinned SHA-256"
        );
        artifact.overlay = Some("development-test-portability".to_owned());
    }
    *artifacts = pending;
    Ok(())
}

fn import_prefix(kind: BaselineKind) -> &'static str {
    match kind {
        BaselineKind::ReleaseTag => RELEASE_IMPORT_PREFIX,
        BaselineKind::DevelopmentHead => DEVELOPMENT_IMPORT_PREFIX,
    }
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
    let prefix = import_prefix(binding.kind);
    validate_repo_path(&binding.import_manifest, prefix)?;
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
    validate_report_schema(binding.kind, &report)?;
    ensure!(
        report.canonical_source_root == SOURCE_ROOT,
        "release import must describe {SOURCE_ROOT}"
    );
    ensure!(
        is_lower_hex(&report.canonical_head, 40) && is_sha256(&report.canonical_source_sha256),
        "release import has invalid canonical source identity"
    );
    if binding.kind == BaselineKind::DevelopmentHead {
        ensure!(
            binding.canonical_commit.as_deref() == Some(report.canonical_head.as_str()),
            "development import canonical commit does not match its pinned binding"
        );
    }
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
        binding.import_manifest == format!("{prefix}{}/import.json", target.release_tag),
        "pinned import manifest path does not match its target label"
    );
    if binding.kind == BaselineKind::DevelopmentHead {
        verify_development_gradle_binding(&root, &manifest_path, binding, target)?;
    }
    Ok(report)
}

fn validate_report_schema(kind: BaselineKind, report: &ReleaseBaselineReport) -> Result<()> {
    let expected_schema = match kind {
        BaselineKind::ReleaseTag => REPORT_SCHEMA,
        BaselineKind::DevelopmentHead => DEVELOPMENT_SOURCE_SCHEMA,
    };
    ensure!(
        report.schema == expected_schema,
        "unsupported pinned import schema '{}': expected {expected_schema}",
        report.schema
    );
    Ok(())
}

fn verify_development_gradle_binding(
    root: &Path,
    manifest_path: &Path,
    binding: &ReleaseBaselineBinding,
    target: &ReleaseBaselineTargetReport,
) -> Result<()> {
    ensure!(
        target.release_tag == binding.target_id,
        "development import target label does not match its pinned target"
    );
    let import_root = manifest_path
        .parent()
        .ok_or_else(|| eyre::eyre!("development import manifest has no parent"))?
        .join("gradle-project");
    verify_development_gradle_inputs(
        root,
        binding
            .canonical_commit
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development canonical commit is missing"))?,
        &binding.tag_commit,
        &import_root,
        binding
            .gradle_provenance_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development Gradle provenance hash is missing"))?,
        &BTreeMap::new(),
    )?;
    Ok(())
}

fn validate_target_report(
    target: &ReleaseBaselineTargetReport,
    import_prefix: &str,
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
        validate_record(
            path,
            record,
            expected_category,
            &target.release_tag,
            import_prefix,
        )?;
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
    import_prefix: &str,
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
            validate_repo_path(overlay, import_prefix)?;
            ensure!(
                overlay == format!("{import_prefix}{release_tag}/overlays/{path}"),
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
    use super::*;
    use crate::source_projection::inputs::collect_projected_inputs_with_allowlist;
    use crate::source_projection::manifest::CanonicalSourceSelection;
    use crate::source_projection::provenance::ProjectedFileProvenance;
    use crate::source_projection::provenance::ProjectionProvenance;
    use std::process::Command;
    use tempfile::TempDir;

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

    #[test]
    fn committed_java_accepts_only_template_drift_with_identical_output() {
        let mut context = context();
        context
            .features
            .insert("touch_display_terminal_mount".to_owned(), true);
        let path = "src/main/java/Same.java";
        let pinned = b"class Same {\n    void mount() {}\n}\n";
        let templated = b"class Same {\n{% if features.touch_display_terminal_mount %}\n    void mount() {}\n{% endif %}\n}\n";
        let mut current = artifact(path, templated);
        render_java_artifact(path, &mut current, &context).unwrap();
        assert!(rendered_java_matches_pinned(path, &current, pinned, &context).unwrap());

        context
            .features
            .insert("touch_display_terminal_mount".to_owned(), false);
        render_java_artifact(path, &mut current, &context).unwrap();
        assert!(!rendered_java_matches_pinned(path, &current, pinned, &context).unwrap());
        assert!(!rendered_java_matches_pinned("src/same.txt", &current, pinned, &context).unwrap());
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
            kind: super::super::manifest::BaselineKind::ReleaseTag,
            canonical_commit: None,
            gradle_provenance_sha256: None,
            project_fixture_provenance_sha256: None,
            post_baseline_test_sources: BTreeMap::new(),
            post_baseline_gradle_sources: BTreeMap::new(),
            post_baseline_canonical_sources: BTreeMap::new(),
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

    #[test]
    fn post_baseline_test_binding_requires_development_and_unchanged_membership() {
        let path = "src/test/java/example/PortableTest.java";
        let mut target = report(
            &"a".repeat(40),
            BTreeMap::from([(
                path.to_owned(),
                record(
                    BaselinePathClass::Unchanged,
                    Some(b"class PortableTest {}\n"),
                    Some(b"class PortableTest {}\n"),
                    None,
                ),
            )]),
        )
        .targets
        .remove(0);
        let root = tempfile::tempdir().unwrap();
        let mut binding = write_report(root.path(), &report(&"a".repeat(40), target.paths.clone()));
        binding
            .post_baseline_test_sources
            .insert(path.to_owned(), "b".repeat(64));
        assert!(validate_post_baseline_test_sources(&binding, &target).is_err());
        binding.kind = BaselineKind::DevelopmentHead;
        validate_post_baseline_test_sources(&binding, &target).unwrap();

        target.paths.get_mut(path).unwrap().classification = BaselinePathClass::Changed;
        assert!(validate_post_baseline_test_sources(&binding, &target).is_err());
        target.paths.get_mut(path).unwrap().classification = BaselinePathClass::Unchanged;
        binding.post_baseline_test_sources = BTreeMap::from([(
            "src/main/java/example/PortableTest.java".to_owned(),
            "b".repeat(64),
        )]);
        assert!(validate_post_baseline_test_sources(&binding, &target).is_err());
    }

    #[test]
    fn canonical_main_source_selection_requires_changed_development_membership() {
        let path = "src/main/java/example/VersionAdapter.java";
        let mut target = report(
            &"a".repeat(40),
            BTreeMap::from([(
                path.to_owned(),
                record(
                    BaselinePathClass::Changed,
                    Some(b"class Old {}\n"),
                    Some(b"class Canonical {}\n"),
                    Some("platform/minecraft/release-baselines/4.34.0-1.19.2/overlays/src/main/java/example/VersionAdapter.java"),
                ),
            )]),
        )
        .targets
        .remove(0);
        let root = tempfile::tempdir().unwrap();
        let mut binding = write_report(root.path(), &report(&"a".repeat(40), target.paths.clone()));
        binding.post_baseline_canonical_sources.insert(
            path.to_owned(),
            CanonicalSourceSelection {
                source_sha256: "a".repeat(64),
                output_sha256: "b".repeat(64),
            },
        );
        assert!(validate_post_baseline_canonical_sources(&binding, &target).is_err());
        binding.kind = BaselineKind::DevelopmentHead;
        validate_post_baseline_canonical_sources(&binding, &target).unwrap();

        target.paths.get_mut(path).unwrap().classification = BaselinePathClass::Unchanged;
        assert!(validate_post_baseline_canonical_sources(&binding, &target).is_err());
        target.paths.remove(path);
        assert!(validate_post_baseline_canonical_sources(&binding, &target).is_err());
    }

    #[test]
    fn canonical_main_source_selection_is_primary_hash_bound_and_keeps_provenance() {
        let path = "src/main/java/example/VersionAdapter.java";
        let mut rendering_context = context();
        rendering_context
            .features
            .insert("disabled".to_owned(), false);
        let mut canonical = artifact(
            path,
            b"class VersionAdapter {}\n{% if features.disabled %}\nclass NeverRendered {}\n{% endif %}\n",
        );
        render_java_artifact(path, &mut canonical, &rendering_context).unwrap();
        let mut imported = artifact(path, b"class VersionAdapter {}\n");
        render_java_artifact(path, &mut imported, &rendering_context).unwrap();
        imported.source_path = "platform/minecraft/development-baselines/1.19.2/overlays/src/main/java/example/VersionAdapter.java".to_owned();
        imported.overlay = Some("development-head-1.19.2".to_owned());
        let mut binding = write_report(
            tempfile::tempdir().unwrap().path(),
            &report(&"a".repeat(40), BTreeMap::new()),
        );
        binding.kind = BaselineKind::DevelopmentHead;
        binding.post_baseline_canonical_sources.insert(
            path.to_owned(),
            CanonicalSourceSelection {
                source_sha256: sha256(&canonical.source_bytes)
                    .trim_start_matches("sha256:")
                    .to_owned(),
                output_sha256: sha256(&canonical.output_bytes)
                    .trim_start_matches("sha256:")
                    .to_owned(),
            },
        );
        let primary = BTreeMap::from([(path.to_owned(), canonical.clone())]);
        let captured = capture_post_baseline_canonical_sources(&binding, &primary).unwrap();
        let mut artifacts = BTreeMap::from([(path.to_owned(), imported)]);
        apply_post_baseline_canonical_sources(&binding, &captured, &mut artifacts).unwrap();
        assert_eq!(artifacts[path], canonical);
        assert_eq!(artifacts[path].source_path, path);
        assert_eq!(artifacts[path].overlay, None);

        let mut drifted = artifact(path, b"class ChangedVersionAdapter {}\n");
        render_java_artifact(path, &mut drifted, &rendering_context).unwrap();
        let drifted_source_sha256 = sha256(&drifted.source_bytes)
            .trim_start_matches("sha256:")
            .to_owned();
        let drifted_output_sha256 = sha256(&drifted.output_bytes)
            .trim_start_matches("sha256:")
            .to_owned();
        let selection = binding
            .post_baseline_canonical_sources
            .get_mut(path)
            .unwrap();
        selection.source_sha256 = drifted_source_sha256;
        selection.output_sha256 = drifted_output_sha256;
        let previous = artifacts.clone();
        let error = apply_post_baseline_canonical_sources(
            &binding,
            &BTreeMap::from([(path.to_owned(), drifted)]),
            &mut artifacts,
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("differs from verified imported overlay"));
        assert_eq!(artifacts, previous);

        let selection = binding
            .post_baseline_canonical_sources
            .get_mut(path)
            .unwrap();
        selection.source_sha256 = sha256(&canonical.source_bytes)
            .trim_start_matches("sha256:")
            .to_owned();
        selection.output_sha256 = sha256(&canonical.output_bytes)
            .trim_start_matches("sha256:")
            .to_owned();

        binding
            .post_baseline_canonical_sources
            .get_mut(path)
            .unwrap()
            .output_sha256 = "f".repeat(64);
        assert!(
            apply_post_baseline_canonical_sources(&binding, &captured, &mut artifacts).is_err()
        );
        binding
            .post_baseline_canonical_sources
            .get_mut(path)
            .unwrap()
            .output_sha256 = sha256(&canonical.output_bytes)
            .trim_start_matches("sha256:")
            .to_owned();
        binding
            .post_baseline_canonical_sources
            .get_mut(path)
            .unwrap()
            .source_sha256 = "f".repeat(64);
        assert!(
            apply_post_baseline_canonical_sources(&binding, &captured, &mut artifacts).is_err()
        );

        let mut non_primary = primary;
        non_primary.get_mut(path).unwrap().overlay = Some("feature".to_owned());
        assert!(capture_post_baseline_canonical_sources(&binding, &non_primary).is_err());
    }

    #[test]
    fn canonical_source_replacements_are_atomic_when_later_output_drifts() {
        let first_path = "src/main/java/example/First.java";
        let second_path = "src/main/java/example/Second.java";
        let mut first = artifact(first_path, b"class First {}\n");
        let mut second = artifact(second_path, b"class ChangedSecond {}\n");
        let mut imported_first = artifact(first_path, b"class First {}\n");
        let mut imported_second = artifact(second_path, b"class Second {}\n");
        for (path, artifact) in [
            (first_path, &mut first),
            (second_path, &mut second),
            (first_path, &mut imported_first),
            (second_path, &mut imported_second),
        ] {
            render_java_artifact(path, artifact, &context()).unwrap();
        }
        let mut binding = write_report(
            tempfile::tempdir().unwrap().path(),
            &report(&"a".repeat(40), BTreeMap::new()),
        );
        binding.kind = BaselineKind::DevelopmentHead;
        for (path, artifact) in [(first_path, &first), (second_path, &second)] {
            binding.post_baseline_canonical_sources.insert(
                path.to_owned(),
                CanonicalSourceSelection {
                    source_sha256: sha256(&artifact.source_bytes)
                        .trim_start_matches("sha256:")
                        .to_owned(),
                    output_sha256: sha256(&artifact.output_bytes)
                        .trim_start_matches("sha256:")
                        .to_owned(),
                },
            );
        }
        let canonical_sources = BTreeMap::from([
            (first_path.to_owned(), first),
            (second_path.to_owned(), second),
        ]);
        let mut artifacts = BTreeMap::from([
            (first_path.to_owned(), imported_first),
            (second_path.to_owned(), imported_second),
        ]);
        let previous = artifacts.clone();
        let error =
            apply_post_baseline_canonical_sources(&binding, &canonical_sources, &mut artifacts)
                .unwrap_err();
        assert!(format!("{error:?}").contains("differs from verified imported overlay"));
        assert_eq!(artifacts, previous);
    }

    #[test]
    fn post_baseline_test_patch_is_content_bound_and_atomic_on_drift() {
        let root = tempfile::tempdir().unwrap();
        let path = "src/test/java/example/PortableTest.java";
        let source = root.path().join(format!("platform/minecraft/{path}"));
        fs::create_dir_all(source.parent().unwrap()).unwrap();
        let patched = b"class PortableTest { void portable() {} }\n";
        fs::write(&source, patched).unwrap();
        let mut binding = write_report(
            root.path(),
            &report(
                &"a".repeat(40),
                BTreeMap::from([(
                    path.to_owned(),
                    record(
                        BaselinePathClass::Unchanged,
                        Some(b"class PortableTest {}\n"),
                        Some(b"class PortableTest {}\n"),
                        None,
                    ),
                )]),
            ),
        );
        binding.kind = BaselineKind::DevelopmentHead;
        binding.post_baseline_test_sources = BTreeMap::from([(
            path.to_owned(),
            sha256(patched).trim_start_matches("sha256:").to_owned(),
        )]);
        let mut artifacts =
            BTreeMap::from([(path.to_owned(), artifact(path, b"class PortableTest {}\n"))]);
        apply_post_baseline_test_sources(root.path(), &binding, &context(), &mut artifacts)
            .unwrap();
        assert_eq!(artifacts[path].source_bytes, patched);
        assert!(artifacts[path].output_bytes.starts_with(b"// GENERATED"));
        assert_eq!(
            artifacts[path].overlay.as_deref(),
            Some("development-test-portability")
        );
        let original = artifacts.clone();
        fs::write(&source, b"class PortableTest { void changed() {} }\n").unwrap();
        assert!(
            apply_post_baseline_test_sources(root.path(), &binding, &context(), &mut artifacts)
                .is_err()
        );
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
        let path = "src/Same.java";
        let mut before = ProjectionProvenance::new(
            "1.19.2",
            "1.19.2",
            "released-4.34.0",
            "blake3:historical-preset",
        );
        let mut old_artifact = artifact(path, b"class Same {}\n");
        render_java_artifact(path, &mut old_artifact, &context()).unwrap();
        before.files.insert(
            path.to_owned(),
            ProjectedFileProvenance {
                source_path: path.to_owned(),
                source_sha256: sha256(&old_artifact.source_bytes),
                overlay: None,
                output_sha256: sha256(&old_artifact.output_bytes),
            },
        );
        let old_manifest_bytes = before.to_json().unwrap();
        let mut artifacts =
            BTreeMap::from([(path.to_owned(), artifact(path, b"class Development {}\n"))]);
        let result =
            apply_release_baseline(root.path(), &binding, &context(), &mut artifacts).unwrap();
        assert_eq!(result.pinned_tag_fallback_files, 1);
        assert_eq!(artifacts[path].source_bytes, b"class Same {}\n");
        assert_eq!(artifacts[path].source_path, path);
        assert_eq!(artifacts[path].overlay, None);
        assert!(artifacts[path].output_bytes.starts_with(b"// GENERATED"));
        let mut after = ProjectionProvenance::new(
            "1.19.2",
            "1.19.2",
            "released-4.34.0",
            "blake3:historical-preset",
        );
        after.files.insert(
            path.to_owned(),
            ProjectedFileProvenance {
                source_path: artifacts[path].source_path.clone(),
                source_sha256: sha256(&artifacts[path].source_bytes),
                overlay: artifacts[path].overlay.clone(),
                output_sha256: sha256(&artifacts[path].output_bytes),
            },
        );
        assert_eq!(after.to_json().unwrap(), old_manifest_bytes);
    }
}
