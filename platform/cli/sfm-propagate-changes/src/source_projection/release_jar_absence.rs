//! Check that a built JAR omits artifacts from disabled `Include` effects.
//!
//! This is an absence check, not JAR parity or a proof that conditional
//! `Template` code, dependencies, or classes inside nested JARs are absent.
//! Only `src/main/java/*.java` and `src/main/resources/*` effects have a
//! predictable direct entry in the outer JAR without a Gradle relocation.
//! Test and `GameTest` source effects are deliberately excluded because they
//! are not release-JAR inputs. ZIP inspection reads central-directory entry
//! names, not compressed payloads or their integrity checks.

use super::manifest::PathEffectKind;
use super::manifest::SourceProjectionManifest;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeSet;
use std::io::Read;
use std::io::Seek;
use std::path::Path;
use zip::ZipArchive;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct JarAbsenceReport {
    pub target_id: String,
    pub preset_id: String,
    pub checked_features: Vec<String>,
    pub forbidden_entry_rules: usize,
    pub inspected_entries: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
enum ForbiddenEntry {
    Class { feature: String, stem: String },
    Resource { feature: String, path: String },
}

impl ForbiddenEntry {
    fn feature(&self) -> &str {
        match self {
            Self::Class { feature, .. } | Self::Resource { feature, .. } => feature,
        }
    }

    fn matches(&self, entry: &str) -> bool {
        let entry = strip_multi_release_prefix(entry);
        match self {
            Self::Class { stem, .. } => {
                entry == format!("{stem}.class")
                    || entry
                        .strip_prefix(stem)
                        .and_then(|suffix| suffix.strip_prefix('$'))
                        .is_some_and(|suffix| {
                            Path::new(suffix)
                                .extension()
                                .is_some_and(|extension| extension.eq_ignore_ascii_case("class"))
                        })
            }
            Self::Resource { path, .. } => entry == path,
        }
    }
}

fn strip_multi_release_prefix(entry: &str) -> &str {
    let Some(rest) = entry.strip_prefix("META-INF/versions/") else {
        return entry;
    };
    let Some((version, path)) = rest.split_once('/') else {
        return entry;
    };
    if !version.is_empty() && version.bytes().all(|byte| byte.is_ascii_digit()) {
        path
    } else {
        entry
    }
}

fn forbidden_entries(
    manifest: &SourceProjectionManifest,
    target_id: &str,
    preset_id: &str,
) -> eyre::Result<(Vec<String>, Vec<ForbiddenEntry>)> {
    manifest.validate()?;
    manifest.target(target_id)?;
    let preset = manifest.preset(preset_id)?;
    ensure!(
        preset.targets.iter().any(|target| target == target_id),
        "preset `{preset_id}` does not select target `{target_id}`"
    );
    let active = preset.effective_features(target_id);
    let mut checked_features = Vec::new();
    let mut rules = Vec::new();

    for feature in &manifest.features {
        if active.contains(feature.id.as_str())
            || !feature.supported_targets.iter().any(|id| id == target_id)
        {
            continue;
        }
        // A Template-only flag changes existing source bytes, but has no
        // conditional class or resource whose absence a JAR can prove.
        if feature.dependency_effects.is_empty()
            && (!feature.source_effects.is_empty() || !feature.resource_effects.is_empty())
            && feature
                .source_effects
                .iter()
                .chain(&feature.resource_effects)
                .all(|effect| effect.kind == PathEffectKind::Template)
        {
            continue;
        }
        checked_features.push(feature.id.clone());
        let before = rules.len();
        for effect in feature
            .source_effects
            .iter()
            .chain(&feature.resource_effects)
            .filter(|effect| effect.kind == PathEffectKind::Include)
        {
            let output = effect.output_path.as_str();
            if let Some(path) = output.strip_prefix("src/main/java/") {
                let stem = path.strip_suffix(".java").ok_or_else(|| {
                    eyre::eyre!(
                        "disabled feature `{}` has non-Java main-source Include `{output}`",
                        feature.id
                    )
                })?;
                ensure!(
                    !stem.is_empty(),
                    "disabled feature `{}` has an empty Java Include path",
                    feature.id
                );
                rules.push(ForbiddenEntry::Class {
                    feature: feature.id.clone(),
                    stem: stem.to_owned(),
                });
            } else if let Some(path) = output.strip_prefix("src/main/resources/") {
                ensure!(
                    !path.is_empty(),
                    "disabled feature `{}` has an empty resource Include path",
                    feature.id
                );
                rules.push(ForbiddenEntry::Resource {
                    feature: feature.id.clone(),
                    path: path.to_owned(),
                });
            } else if output.starts_with("src/main/") {
                eyre::bail!(
                    "disabled feature `{}` has unsupported main-source Include `{output}`",
                    feature.id
                );
            }
        }
        ensure!(
            rules.len() > before,
            "disabled feature `{}` has no derivable outer-JAR Include entry; this absence check cannot cover it",
            feature.id
        );
    }

    Ok((checked_features, rules))
}

/// Verify direct outer-JAR entry absence for disabled features with runtime
/// Include effects. Template-only flags have no absence entry to inspect.
/// The manifest is validated before examining the ZIP.
///
/// # Errors
///
/// Fails for a malformed selection or ZIP central directory, a disabled
/// non-template-only feature without a derivable runtime Include, or any
/// forbidden top-level, nested, or
/// multi-release class/resource entry. The caller owns the reader; no JAR
/// content is extracted or written. It does not validate compressed payloads.
pub fn check_jar_reader<R: Read + Seek>(
    manifest: &SourceProjectionManifest,
    target_id: &str,
    preset_id: &str,
    reader: R,
) -> eyre::Result<JarAbsenceReport> {
    let (checked_features, rules) = forbidden_entries(manifest, target_id, preset_id)?;
    let mut archive = ZipArchive::new(reader).wrap_err("could not open release JAR as ZIP")?;
    let mut violations = BTreeSet::new();
    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("could not inspect release JAR entry {index}"))?;
        if entry.is_dir() {
            continue;
        }
        for rule in &rules {
            if rule.matches(entry.name()) {
                violations.insert(format!("{}: {}", rule.feature(), entry.name()));
            }
        }
    }
    ensure!(
        violations.is_empty(),
        "disabled-feature artifacts found in release JAR: {}",
        violations.into_iter().collect::<Vec<_>>().join(", ")
    );
    Ok(JarAbsenceReport {
        target_id: target_id.to_owned(),
        preset_id: preset_id.to_owned(),
        checked_features,
        forbidden_entry_rules: rules.len(),
        inspected_entries: archive.len(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::manifest::PathEffect;
    use crate::source_projection::manifest::ProjectionFeature;
    use crate::source_projection::manifest::ProjectionPreset;
    use crate::source_projection::manifest::ProjectionTarget;
    use crate::source_projection::manifest::SCHEMA_VERSION;
    use std::collections::BTreeMap;
    use std::io::Cursor;
    use std::io::Write;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    fn manifest(enabled_features: Vec<String>) -> SourceProjectionManifest {
        let mut manifest = SourceProjectionManifest {
            schema_version: SCHEMA_VERSION,
            targets: vec![ProjectionTarget {
                id: "1.19.2".to_owned(),
                template_key: "mc_1_19_2".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                loader: "forge".to_owned(),
                java_major: 17,
                project_dir: "platform/minecraft/mc-version/1.19.2".to_owned(),
            }],
            features: vec![ProjectionFeature {
                id: "echo_action".to_owned(),
                supported_targets: vec!["1.19.2".to_owned()],
                requires: Vec::new(),
                source_effects: vec![
                    PathEffect {
                        output_path: "src/main/java/example/EchoAction.java".to_owned(),
                        kind: PathEffectKind::Include,
                        input_path: Some(
                            "platform/minecraft/src/main/java/example/EchoAction.java".to_owned(),
                        ),
                    },
                    PathEffect {
                        output_path: "src/test/java/example/EchoActionTests.java".to_owned(),
                        kind: PathEffectKind::Include,
                        input_path: Some(
                            "platform/minecraft/src/test/java/example/EchoActionTests.java"
                                .to_owned(),
                        ),
                    },
                    PathEffect {
                        output_path: "src/main/java/example/Host.java".to_owned(),
                        kind: PathEffectKind::Template,
                        input_path: None,
                    },
                ],
                resource_effects: vec![PathEffect {
                    output_path: "src/main/resources/assets/sfm/echo.png".to_owned(),
                    kind: PathEffectKind::Include,
                    input_path: Some(
                        "platform/minecraft/src/main/resources/assets/sfm/echo.png".to_owned(),
                    ),
                }],
                dependency_effects: Vec::new(),
            }],
            presets: vec![ProjectionPreset {
                id: "released-test".to_owned(),
                release_mod_version: None,
                targets: vec!["1.19.2".to_owned()],
                enabled_features,
                target_features: BTreeMap::new(),
                release_baselines: Vec::new(),
                frozen_source_commit: None,
                frozen_sources: vec![],
                canonical_project_fixture_provenance_sha256: None,
                identity: String::new(),
            }],
        };
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        manifest
    }

    fn jar(entries: &[&str]) -> Cursor<Vec<u8>> {
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        for entry in entries {
            writer
                .start_file(*entry, SimpleFileOptions::default())
                .unwrap();
            writer.write_all(b"fixture").unwrap();
        }
        writer.finish().unwrap()
    }

    #[test]
    fn absent_disabled_include_entries_pass_even_if_test_and_template_names_exist() {
        let manifest = manifest(Vec::new());
        let report = check_jar_reader(
            &manifest,
            "1.19.2",
            "released-test",
            jar(&["example/Host.class", "example/EchoActionTests.class"]),
        )
        .unwrap();
        assert_eq!(report.checked_features, vec!["echo_action"]);
        assert_eq!(report.forbidden_entry_rules, 2);
        assert_eq!(report.inspected_entries, 2);
    }

    #[test]
    fn top_level_and_nested_disabled_classes_are_rejected() {
        let manifest = manifest(Vec::new());
        for entry in ["example/EchoAction.class", "example/EchoAction$Inner.class"] {
            let error =
                check_jar_reader(&manifest, "1.19.2", "released-test", jar(&[entry])).unwrap_err();
            assert!(error.to_string().contains(entry), "{error}");
        }
        check_jar_reader(
            &manifest,
            "1.19.2",
            "released-test",
            jar(&["example/EchoActionOther.class"]),
        )
        .unwrap();
    }

    #[test]
    fn disabled_resource_and_multi_release_class_are_rejected() {
        let manifest = manifest(Vec::new());
        for entry in [
            "assets/sfm/echo.png",
            "META-INF/versions/17/example/EchoAction.class",
        ] {
            let error =
                check_jar_reader(&manifest, "1.19.2", "released-test", jar(&[entry])).unwrap_err();
            assert!(error.to_string().contains(entry), "{error}");
        }
    }

    #[test]
    fn enabled_feature_does_not_forbid_its_entries() {
        let manifest = manifest(vec!["echo_action".to_owned()]);
        let report = check_jar_reader(
            &manifest,
            "1.19.2",
            "released-test",
            jar(&["example/EchoAction.class", "assets/sfm/echo.png"]),
        )
        .unwrap();
        assert!(report.checked_features.is_empty());
        assert_eq!(report.forbidden_entry_rules, 0);
    }

    #[test]
    fn target_scoped_feature_does_not_forbid_its_entries() {
        let mut manifest = manifest(Vec::new());
        manifest.presets[0]
            .target_features
            .insert("1.19.2".to_owned(), vec!["echo_action".to_owned()]);
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        let report = check_jar_reader(
            &manifest,
            "1.19.2",
            "released-test",
            jar(&["example/EchoAction.class", "assets/sfm/echo.png"]),
        )
        .unwrap();
        assert!(report.checked_features.is_empty());
    }

    #[test]
    fn disabled_template_only_feature_has_no_jar_absence_target() {
        let mut manifest = manifest(Vec::new());
        manifest.features.push(ProjectionFeature {
            id: "regex_overlap_fix".to_owned(),
            supported_targets: vec!["1.19.2".to_owned()],
            requires: Vec::new(),
            source_effects: vec![PathEffect {
                output_path: "src/main/java/example/Host.java".to_owned(),
                kind: PathEffectKind::Template,
                input_path: None,
            }],
            resource_effects: Vec::new(),
            dependency_effects: Vec::new(),
        });
        let report = check_jar_reader(
            &manifest,
            "1.19.2",
            "released-test",
            jar(&["example/Host.class"]),
        )
        .unwrap();
        assert_eq!(report.checked_features, vec!["echo_action"]);
        assert_eq!(report.forbidden_entry_rules, 2);
    }

    #[test]
    fn unsupported_or_uncovered_runtime_include_is_not_silent_success() {
        let mut uncovered = manifest(Vec::new());
        uncovered.features[0]
            .source_effects
            .retain(|effect| !effect.output_path.starts_with("src/main/java/"));
        uncovered.features[0].resource_effects.clear();
        uncovered.presets[0].identity = uncovered
            .compute_preset_identity(&uncovered.presets[0])
            .unwrap();
        let error = check_jar_reader(&uncovered, "1.19.2", "released-test", jar(&[])).unwrap_err();
        assert!(error.to_string().contains("no derivable outer-JAR Include"));

        let mut manifest = manifest(Vec::new());
        manifest.features[0].source_effects[0].output_path =
            "src/main/kotlin/example/EchoAction.kt".to_owned();
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        let error = check_jar_reader(&manifest, "1.19.2", "released-test", jar(&[])).unwrap_err();
        assert!(
            error
                .to_string()
                .contains("unsupported main-source Include")
        );
    }

    #[test]
    fn malformed_zip_and_unsupported_selection_fail_closed() {
        let manifest = manifest(Vec::new());
        assert!(
            check_jar_reader(
                &manifest,
                "1.19.2",
                "released-test",
                Cursor::new(b"not zip")
            )
            .unwrap_err()
            .to_string()
            .contains("could not open release JAR")
        );
        assert!(
            check_jar_reader(&manifest, "1.19.4", "released-test", jar(&[]))
                .unwrap_err()
                .to_string()
                .contains("unknown source-projection target")
        );
    }

    #[test]
    fn checked_in_release_manifest_derives_echo_and_touch_rules() {
        let manifest = SourceProjectionManifest::from_json(include_str!(
            "../../tests/fixtures/source_projection/legacy-source-projection.json"
        ))
        .unwrap();
        let (features, rules) = forbidden_entries(&manifest, "1.19.2", "released-4.34.0").unwrap();
        assert_eq!(
            features,
            vec!["echo_action", "touch_display_terminal_mount"]
        );
        assert_eq!(rules.len(), 14);
        assert!(
            rules
                .iter()
                .any(|rule| rule.matches("ca/teamdman/sfm/client/action/EchoAction.class"))
        );
        assert!(rules.iter().any(|rule| {
            rule.matches("ca/teamdman/sfm/client/terminal/TouchDisplayTerminalRuntime$Worker.class")
        }));
    }
}
