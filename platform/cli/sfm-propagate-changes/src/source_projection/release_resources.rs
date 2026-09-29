//! Add declared, hash-bound release resources after the pinned source mask.

use super::context::ProjectionContext;
use super::inputs::apply_explicit_inputs;
use super::manifest::BaselineKind;
use super::manifest::ReleaseBaselineBinding;
use super::provenance::sha256;
use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::path::Path;

const RESOURCE_OUTPUT_PREFIX: &str = "src/main/resources/";
const RESOURCE_INPUT_PREFIX: &str = "platform/minecraft/projection-resources/";

/// Apply release-only resources that are deliberately absent from the tagged
/// source tree but present in its published artifact. The binding is part of
/// the preset identity, and each input is verified before any output changes.
///
/// # Errors
///
/// Rejects a development binding, an unsafe or changed input, or any output
/// collision. The caller's artifact map remains unchanged on failure.
pub fn apply_post_baseline_resources(
    repo_root: &Path,
    binding: &ReleaseBaselineBinding,
    context: &ProjectionContext,
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
) -> Result<()> {
    if binding.post_baseline_resources.is_empty() {
        return Ok(());
    }
    ensure!(
        binding.kind == BaselineKind::ReleaseTag,
        "post-baseline resources require a release-tag binding"
    );

    let mut inputs = BTreeMap::new();
    for (output_path, resource) in &binding.post_baseline_resources {
        ensure!(
            output_path.starts_with(RESOURCE_OUTPUT_PREFIX),
            "post-baseline resource output '{output_path}' must be under {RESOURCE_OUTPUT_PREFIX}"
        );
        ensure!(
            resource.source_path.starts_with(RESOURCE_INPUT_PREFIX),
            "post-baseline resource input '{}' must be under {RESOURCE_INPUT_PREFIX}",
            resource.source_path
        );
        inputs.insert(output_path.clone(), resource.source_path.clone());
    }

    // The existing reader rejects symlinks, parent traversal and repository
    // escapes. Build separately so neither read nor hash failure mutates the
    // caller's artifacts.
    let mut selected = BTreeMap::new();
    apply_explicit_inputs(repo_root, &mut selected, &inputs, context)?;
    for (output_path, artifact) in &mut selected {
        let expected = &binding.post_baseline_resources[output_path].sha256;
        ensure!(
            sha256(&artifact.source_bytes) == format!("sha256:{expected}"),
            "post-baseline resource '{output_path}' differs from its declared SHA-256"
        );
        ensure!(
            artifact.output_bytes == artifact.source_bytes,
            "post-baseline resource '{output_path}' must be copied byte-for-byte"
        );
        artifact.overlay = Some("release-resource".to_owned());
    }
    for selected_path in selected.keys() {
        ensure!(
            !artifacts
                .keys()
                .any(|existing_path| output_paths_conflict(existing_path, selected_path)),
            "post-baseline resource '{selected_path}' collides with an existing projected artifact"
        );
    }
    artifacts.extend(selected);
    Ok(())
}

fn output_paths_conflict(left: &str, right: &str) -> bool {
    let left = left.to_ascii_lowercase();
    let right = right.to_ascii_lowercase();
    left == right
        || left
            .strip_prefix(&right)
            .is_some_and(|remainder| remainder.starts_with('/'))
        || right
            .strip_prefix(&left)
            .is_some_and(|remainder| remainder.starts_with('/'))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::manifest::SourceProjectionManifest;
    use std::fs;

    const OFFICIAL_EMPTY_REFMAP: &[u8] = include_bytes!(
        "../../../../minecraft/projection-resources/released-4.34.0/sfm.refmap.json"
    );
    const MANIFEST: &str = include_str!("../../../../minecraft/source-projection.json");
    const REFMAP_OUTPUT: &str = "src/main/resources/sfm.refmap.json";
    const REFMAP_SHA256: &str =
        "sha256:2c94879b9e943b34c562f6966f9e0aa88c1a30b39bc8e17bd18772b66af24523";

    fn release_binding(target: &str) -> ReleaseBaselineBinding {
        let manifest = SourceProjectionManifest::from_json(MANIFEST).unwrap();
        manifest
            .preset("released-4.34.0")
            .unwrap()
            .release_baseline_for(target)
            .unwrap()
            .clone()
    }

    fn context() -> ProjectionContext {
        ProjectionContext {
            minecraft_version: "1.20.2".to_owned(),
            preset: "released-4.34.0".to_owned(),
            features: BTreeMap::new(),
            targets: BTreeMap::new(),
        }
    }

    fn fixture_root(binding: &ReleaseBaselineBinding, bytes: &[u8]) -> tempfile::TempDir {
        let root = tempfile::tempdir().unwrap();
        let source = root
            .path()
            .join(&binding.post_baseline_resources[REFMAP_OUTPUT].source_path);
        fs::create_dir_all(source.parent().unwrap()).unwrap();
        fs::write(source, bytes).unwrap();
        root
    }

    fn existing_artifact() -> ProjectedArtifact {
        ProjectedArtifact {
            source_path: "src/main/resources/existing.json".to_owned(),
            source_bytes: b"existing".to_vec(),
            output_bytes: b"existing".to_vec(),
            overlay: None,
        }
    }

    #[test]
    fn released_4_34_0_empty_refmap_fixture_matches_published_entry() {
        assert_eq!(OFFICIAL_EMPTY_REFMAP.len(), 34);
        assert_eq!(sha256(OFFICIAL_EMPTY_REFMAP), REFMAP_SHA256);
        assert_eq!(
            OFFICIAL_EMPTY_REFMAP,
            b"{\n  \"mappings\": {},\n  \"data\": {}\n}"
        );
    }

    #[test]
    fn released_4_34_0_declares_only_five_empty_refmap_targets() {
        let manifest = SourceProjectionManifest::from_json(MANIFEST).unwrap();
        let preset = manifest.preset("released-4.34.0").unwrap();
        for target in ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1"] {
            let resources = &preset
                .release_baseline_for(target)
                .unwrap()
                .post_baseline_resources;
            assert_eq!(resources.len(), 1, "{target}");
            assert_eq!(
                resources[REFMAP_OUTPUT].sha256,
                REFMAP_SHA256.trim_start_matches("sha256:")
            );
        }
        for target in ["1.19.2", "1.19.4", "1.20", "1.20.1", "26.1.2"] {
            assert!(
                preset
                    .release_baseline_for(target)
                    .unwrap()
                    .post_baseline_resources
                    .is_empty(),
                "{target}"
            );
        }
    }

    #[test]
    fn declared_resource_changes_preset_definition_identity() {
        let manifest = SourceProjectionManifest::from_json(MANIFEST).unwrap();
        let mut preset = manifest.preset("released-4.34.0").unwrap().clone();
        let before = manifest.compute_preset_identity(&preset).unwrap();
        let binding = preset
            .release_baselines
            .iter_mut()
            .find(|binding| binding.target_id == "1.20.2")
            .unwrap();
        binding
            .post_baseline_resources
            .get_mut(REFMAP_OUTPUT)
            .unwrap()
            .sha256 = "0".repeat(64);
        assert_ne!(before, manifest.compute_preset_identity(&preset).unwrap());
    }

    #[test]
    fn copies_exact_resource_after_baseline() {
        let binding = release_binding("1.20.2");
        let root = fixture_root(&binding, OFFICIAL_EMPTY_REFMAP);
        let mut artifacts = BTreeMap::new();
        apply_post_baseline_resources(root.path(), &binding, &context(), &mut artifacts).unwrap();
        let artifact = &artifacts[REFMAP_OUTPUT];
        assert_eq!(artifact.source_bytes, OFFICIAL_EMPTY_REFMAP);
        assert_eq!(artifact.output_bytes, OFFICIAL_EMPTY_REFMAP);
        assert_eq!(artifact.overlay.as_deref(), Some("release-resource"));
        assert_eq!(
            artifact.source_path,
            binding.post_baseline_resources[REFMAP_OUTPUT].source_path
        );
    }

    #[test]
    fn changed_fixture_fails_without_mutating_artifacts() {
        let binding = release_binding("1.20.2");
        let root = fixture_root(&binding, b"{}\n");
        let mut artifacts = BTreeMap::from([(
            "src/main/resources/existing.json".to_owned(),
            existing_artifact(),
        )]);
        let before = artifacts.clone();
        let error =
            apply_post_baseline_resources(root.path(), &binding, &context(), &mut artifacts)
                .unwrap_err();
        assert!(error.to_string().contains("declared SHA-256"));
        assert_eq!(artifacts, before);
    }

    #[test]
    fn output_collision_fails_without_mutating_artifacts() {
        let binding = release_binding("1.20.2");
        let root = fixture_root(&binding, OFFICIAL_EMPTY_REFMAP);
        let mut artifacts = BTreeMap::from([(REFMAP_OUTPUT.to_owned(), existing_artifact())]);
        let before = artifacts.clone();
        let error =
            apply_post_baseline_resources(root.path(), &binding, &context(), &mut artifacts)
                .unwrap_err();
        assert!(error.to_string().contains("collides"));
        assert_eq!(artifacts, before);
    }

    #[test]
    fn older_and_26_1_2_bindings_do_not_add_a_stub() {
        for target in ["1.19.2", "1.19.4", "1.20", "1.20.1", "26.1.2"] {
            let binding = release_binding(target);
            let mut artifacts = BTreeMap::new();
            apply_post_baseline_resources(
                Path::new("unused-for-empty-binding"),
                &binding,
                &context(),
                &mut artifacts,
            )
            .unwrap();
            assert!(artifacts.is_empty(), "{target}");
        }
    }
}
