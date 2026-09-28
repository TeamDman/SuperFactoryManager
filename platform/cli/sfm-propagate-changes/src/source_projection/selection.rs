//! Resolve a validated target and preset to concrete source-selection rules.

use std::collections::{BTreeMap, BTreeSet};

use eyre::{Result, ensure};

use super::context::ProjectionContext;
use super::manifest::{PathEffectKind, ReleaseBaselineBinding, SourceProjectionManifest};

/// Paths here are canonical generated paths with the `src/` prefix. Explicit
/// file inputs are repository-relative paths declared in the feature manifest.
#[derive(Debug)]
pub struct ProjectionSelection {
    pub context: ProjectionContext,
    pub excluded_paths: BTreeSet<String>,
    pub explicit_inputs: BTreeMap<String, String>,
    pub release_baseline: Option<ReleaseBaselineBinding>,
}

/// Resolve all feature effects for one target without reading any files.
///
/// # Errors
///
/// Fails on a malformed manifest or an unsupported target/preset combination.
pub fn select(
    manifest: &SourceProjectionManifest,
    target_id: &str,
    preset_id: &str,
) -> Result<ProjectionSelection> {
    manifest.validate()?;
    let target = manifest.target(target_id)?;
    let preset = manifest.preset(preset_id)?;
    ensure!(
        preset
            .targets
            .iter()
            .any(|candidate| candidate == target_id),
        "preset '{preset_id}' does not support target '{target_id}'"
    );

    let active: BTreeSet<&str> = preset.enabled_features.iter().map(String::as_str).collect();
    let mut features = BTreeMap::new();
    let mut excluded_paths = BTreeSet::new();
    let mut explicit_inputs = BTreeMap::new();
    for feature in &manifest.features {
        let enabled = active.contains(feature.id.as_str());
        features.insert(feature.id.clone(), enabled);
        if !feature.supported_targets.iter().any(|id| id == target_id) {
            continue;
        }
        for effect in feature
            .source_effects
            .iter()
            .chain(&feature.resource_effects)
        {
            match (enabled, effect.kind) {
                (false, PathEffectKind::Include) | (true, PathEffectKind::Exclude) => {
                    excluded_paths.insert(effect.output_path.clone());
                }
                (true, PathEffectKind::Include | PathEffectKind::Replace) => {
                    let input = effect.input_path.as_ref().ok_or_else(|| {
                        eyre::eyre!(
                            "feature '{}' has no input for '{}'",
                            feature.id,
                            effect.output_path
                        )
                    })?;
                    explicit_inputs.insert(effect.output_path.clone(), input.clone());
                }
                _ => {}
            }
        }
    }

    let mut targets = BTreeMap::new();
    for candidate in &manifest.targets {
        let key = candidate.template_key.clone();
        ensure!(
            targets
                .insert(key.clone(), candidate.id == target_id)
                .is_none(),
            "target condition key '{key}' is ambiguous"
        );
    }
    let mut loaders = BTreeSet::new();
    for candidate in &manifest.targets {
        if !loaders.insert(candidate.loader.as_str()) {
            continue;
        }
        ensure!(
            !targets.contains_key(&candidate.loader),
            "loader condition key '{}' collides with a target condition",
            candidate.loader
        );
        targets.insert(candidate.loader.clone(), candidate.loader == target.loader);
    }

    Ok(ProjectionSelection {
        context: ProjectionContext {
            minecraft_version: target.minecraft_version.clone(),
            preset: preset.id.clone(),
            features,
            targets,
        },
        excluded_paths,
        explicit_inputs,
        release_baseline: preset.release_baseline_for(target_id).cloned(),
    })
}

#[cfg(test)]
mod tests {
    use super::super::manifest::{
        PathEffect, ProjectionFeature, ProjectionPreset, ProjectionTarget,
    };
    use super::*;

    fn manifest() -> SourceProjectionManifest {
        let mut manifest = SourceProjectionManifest {
            schema_version: 1,
            targets: vec![
                ProjectionTarget {
                    id: "1.19.2".to_owned(),
                    template_key: "mc_1_19_2".to_owned(),
                    minecraft_version: "1.19.2".to_owned(),
                    loader: "forge".to_owned(),
                    java_major: 17,
                    project_dir: "platform/minecraft/mc-version/1.19.2".to_owned(),
                },
                ProjectionTarget {
                    id: "26.1.2".to_owned(),
                    template_key: "mc_26_1_2".to_owned(),
                    minecraft_version: "26.1.2".to_owned(),
                    loader: "neoforge".to_owned(),
                    java_major: 25,
                    project_dir: "platform/minecraft/mc-version/26.1.2".to_owned(),
                },
            ],
            features: vec![ProjectionFeature {
                id: "touch_display".to_owned(),
                supported_targets: vec!["1.19.2".to_owned(), "26.1.2".to_owned()],
                requires: vec![],
                source_effects: vec![PathEffect {
                    output_path: "src/main/java/TouchDisplay.java".to_owned(),
                    kind: PathEffectKind::Include,
                    input_path: Some(
                        "platform/minecraft/src/main/java/TouchDisplay.java".to_owned(),
                    ),
                }],
                resource_effects: vec![],
                dependency_effects: vec![],
            }],
            presets: vec![ProjectionPreset {
                id: "released-4.34.0".to_owned(),
                targets: vec!["1.19.2".to_owned(), "26.1.2".to_owned()],
                enabled_features: vec![],
                release_baselines: vec![],
                identity: String::new(),
            }],
        };
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        manifest
    }

    #[test]
    fn released_preset_omits_unreleased_file_and_exposes_false_flag() {
        let selected = select(&manifest(), "1.19.2", "released-4.34.0").unwrap();
        assert!(
            selected
                .excluded_paths
                .contains("src/main/java/TouchDisplay.java")
        );
        assert!(!selected.context.features["touch_display"]);
        assert!(selected.context.targets["mc_1_19_2"]);
        assert!(!selected.context.targets["mc_26_1_2"]);
        assert!(selected.context.targets["forge"]);
        assert!(!selected.context.targets["neoforge"]);
    }

    #[test]
    fn selection_carries_only_the_matching_release_binding() {
        let mut manifest = manifest();
        manifest.presets[0].targets = vec!["1.19.2".to_owned()];
        manifest.presets[0]
            .release_baselines
            .push(ReleaseBaselineBinding {
                target_id: "1.19.2".to_owned(),
                tag_commit: "a".repeat(40),
                import_manifest:
                    "platform/minecraft/release-baselines/released-4.34.0/1.19.2/import.json"
                        .to_owned(),
                import_manifest_sha256: "b".repeat(64),
            });
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        let selected = select(&manifest, "1.19.2", "released-4.34.0").unwrap();
        assert_eq!(
            selected.release_baseline.unwrap().tag_commit,
            "a".repeat(40)
        );
    }

    #[test]
    fn enabled_feature_selects_declared_input() {
        let mut manifest = manifest();
        manifest.presets[0]
            .enabled_features
            .push("touch_display".to_owned());
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        let selected = select(&manifest, "26.1.2", "released-4.34.0").unwrap();
        assert_eq!(
            selected.explicit_inputs["src/main/java/TouchDisplay.java"],
            "platform/minecraft/src/main/java/TouchDisplay.java"
        );
    }

    #[test]
    fn disabled_feature_does_not_exclude_paths_on_unsupported_targets() {
        let mut manifest = manifest();
        manifest.features[0].supported_targets = vec!["1.19.2".to_owned()];
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        let selected = select(&manifest, "26.1.2", "released-4.34.0").unwrap();
        assert!(selected.excluded_paths.is_empty());
        assert!(!selected.context.features["touch_display"]);
    }
}
