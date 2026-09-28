//! Typed declarations for source-projection targets, features and presets.
//!
//! The manifest describes *possible* file and dependency effects. A later
//! projection step checks the actual source files and records their hashes in
//! a per-output provenance manifest. Neither an effect declaration nor a
//! preset identity by itself proves that a generated JAR matches a release.

use eyre::WrapErr;
use facet::Facet;
use std::collections::{BTreeMap, BTreeSet};

pub const SCHEMA_VERSION: u32 = 1;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct SourceProjectionManifest {
    pub schema_version: u32,
    pub targets: Vec<ProjectionTarget>,
    pub features: Vec<ProjectionFeature>,
    pub presets: Vec<ProjectionPreset>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ProjectionTarget {
    /// Stable matrix ID, such as `1.21.0`; it need not equal the Gradle value.
    pub id: String,
    /// Liquid `targets` map key, such as `mc_1_21_0`. It is intentionally
    /// distinct from the dotted matrix ID, which cannot appear in a directive.
    pub template_key: String,
    /// Exact Gradle Minecraft version, such as `1.21` for the `1.21.0` target.
    pub minecraft_version: String,
    pub loader: String,
    pub java_major: u16,
    /// Repository-relative output project path for the checked-in preset.
    pub project_dir: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ProjectionFeature {
    pub id: String,
    pub supported_targets: Vec<String>,
    pub requires: Vec<String>,
    /// Java or other authored source files whose contents this flag may affect.
    pub source_effects: Vec<PathEffect>,
    /// Assets and other non-source files controlled by this flag.
    pub resource_effects: Vec<PathEffect>,
    /// Dependency components included when the feature is enabled.
    pub dependency_effects: Vec<DependencyEffect>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[repr(u8)]
pub enum PathEffectKind {
    /// An existing canonical file contains a whole-line conditional directive.
    Template,
    /// Copy a conditional file that is absent when this feature is disabled.
    Include,
    /// Select a separate implementation for an existing output path.
    Replace,
    /// Omit an otherwise-present output path.
    Exclude,
}

impl PathEffectKind {
    const fn as_str(self) -> &'static str {
        match self {
            Self::Template => "template",
            Self::Include => "include",
            Self::Replace => "replace",
            Self::Exclude => "exclude",
        }
    }
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct PathEffect {
    /// Repository-relative *generated* path. No globs are allowed.
    pub output_path: String,
    pub kind: PathEffectKind,
    /// Required for Include/Replace, absent for Template/Exclude.
    pub input_path: Option<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyEffect {
    pub dependency_id: String,
    pub component_id: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ProjectionPreset {
    /// A published ID is never edited in place; a changed definition needs a new ID.
    pub id: String,
    pub targets: Vec<String>,
    /// Disabled features are absent from this set.
    pub enabled_features: Vec<String>,
    /// An empty list selects ordinary development sources. Pinned release or
    /// committed-development presets bind each target to an exact-path import.
    #[facet(default)]
    pub release_baselines: Vec<ReleaseBaselineBinding>,
    /// Manifest-definition fingerprint, `blake3:<hex>`. Source/output hashes
    /// belong to the separate provenance manifest.
    pub identity: String,
}

/// Immutable pointer to the exact path mask and divergent/tag-only overlay
/// imported from one release tag. The referenced manifest carries the
/// path-to-blob evidence; it is checked by SHA-256 before projection.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ReleaseBaselineBinding {
    pub target_id: String,
    /// Exact target commit. For release tags this is the resolved tag commit;
    /// for a development import it is the pinned version-branch HEAD.
    pub tag_commit: String,
    /// Repository-relative pinned import manifest.
    pub import_manifest: String,
    pub import_manifest_sha256: String,
    /// Existing release bindings omit this field and retain their old identity.
    #[facet(default)]
    pub kind: BaselineKind,
    /// The committed primary-source snapshot compared with a development HEAD.
    #[facet(default)]
    pub canonical_commit: Option<String>,
    /// Hash of the separately pinned Gradle-input provenance document.
    #[facet(default)]
    pub gradle_provenance_sha256: Option<String>,
}

#[derive(Clone, Copy, Debug, Default, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum BaselineKind {
    #[default]
    ReleaseTag,
    DevelopmentHead,
}

impl SourceProjectionManifest {
    /// Parse and validate a repository-owned projection manifest.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid JSON or any invalid target, feature or
    /// preset declaration.
    pub fn from_json(input: &str) -> eyre::Result<Self> {
        let manifest: Self =
            facet_json::from_str(input).wrap_err("could not parse source-projection manifest")?;
        manifest.validate()?;
        Ok(manifest)
    }

    /// Serialize a validated manifest with a terminal newline.
    ///
    /// # Errors
    ///
    /// Returns an error if validation or JSON serialization fails.
    pub fn to_json(&self) -> eyre::Result<String> {
        self.validate()?;
        let mut json = facet_json::to_string_pretty(self)
            .wrap_err("could not serialize source-projection manifest")?;
        json.push('\n');
        Ok(json)
    }

    /// Check all manifest-local IDs, paths, prerequisites and preset identities.
    ///
    /// # Errors
    ///
    /// Returns an error for an unsupported schema or inconsistent declaration.
    /// Input-file existence and dependency-component existence are checked by
    /// the later projection step against its repository and lockfile inputs.
    pub fn validate(&self) -> eyre::Result<()> {
        if self.schema_version != SCHEMA_VERSION {
            eyre::bail!(
                "source projection schema is {}, expected {SCHEMA_VERSION}",
                self.schema_version
            );
        }

        let target_ids = self.validate_targets()?;

        let feature_ids = self.validate_features(&target_ids)?;

        let mut preset_ids = BTreeSet::new();
        for preset in &self.presets {
            validate_id(&preset.id, "preset")?;
            if !preset_ids.insert(preset.id.as_str()) {
                eyre::bail!("duplicate preset ID `{}`", preset.id);
            }
            if preset.targets.is_empty() {
                eyre::bail!("preset `{}` selects no targets", preset.id);
            }
            unique_known_ids(
                &preset.targets,
                &target_ids,
                &format!("preset `{}` target", preset.id),
            )?;
            unique_known_ids(
                &preset.enabled_features,
                &feature_ids,
                &format!("preset `{}` feature", preset.id),
            )?;
            validate_release_baselines(preset)?;

            let active: BTreeSet<_> = preset.enabled_features.iter().map(String::as_str).collect();
            for target in &preset.targets {
                let mut path_owners: BTreeMap<String, (&str, PathEffectKind)> = BTreeMap::new();
                for feature_id in &active {
                    let feature = self.feature(feature_id)?;
                    if !feature.supported_targets.contains(target) {
                        eyre::bail!(
                            "preset `{}` enables feature `{feature_id}` on unsupported target `{target}`",
                            preset.id
                        );
                    }
                    for required in &feature.requires {
                        if !active.contains(required.as_str()) {
                            eyre::bail!(
                                "preset `{}` enables feature `{feature_id}` without required feature `{required}`",
                                preset.id
                            );
                        }
                    }
                    for effect in feature
                        .source_effects
                        .iter()
                        .chain(&feature.resource_effects)
                    {
                        let path = effect.output_path.to_ascii_lowercase();
                        if let Some((owner, previous_kind)) = path_owners.get(&path) {
                            // Several flags may gate different regions of one template.
                            if *previous_kind != PathEffectKind::Template
                                || effect.kind != PathEffectKind::Template
                            {
                                eyre::bail!(
                                    "preset `{}` has conflicting effects on `{}` from features `{owner}` and `{feature_id}`",
                                    preset.id,
                                    effect.output_path
                                );
                            }
                        } else {
                            path_owners.insert(path, (*feature_id, effect.kind));
                        }
                    }
                }
            }

            let expected = self.compute_preset_identity(preset)?;
            if preset.identity != expected {
                eyre::bail!(
                    "preset `{}` identity mismatch: declared `{}`, expected `{expected}`; publish changed definitions under a new preset ID",
                    preset.id,
                    preset.identity
                );
            }
        }
        Ok(())
    }

    fn validate_targets(&self) -> eyre::Result<BTreeSet<&str>> {
        let mut target_ids = BTreeSet::new();
        let mut target_template_keys = BTreeSet::new();
        let mut project_dirs = BTreeSet::new();
        for target in &self.targets {
            validate_id(&target.id, "target")?;
            validate_template_key(&target.template_key, "target template key")?;
            validate_id(&target.minecraft_version, "Minecraft version")?;
            validate_id(&target.loader, "loader")?;
            if target.java_major == 0 {
                eyre::bail!("target `{}` has Java major 0", target.id);
            }
            validate_path(&target.project_dir, "target project directory")?;
            if !target
                .project_dir
                .starts_with("platform/minecraft/mc-version/")
            {
                eyre::bail!(
                    "target `{}` project directory must be under platform/minecraft/mc-version/",
                    target.id
                );
            }
            if !target_ids.insert(target.id.as_str()) {
                eyre::bail!("duplicate target ID `{}`", target.id);
            }
            if !target_template_keys.insert(target.template_key.as_str()) {
                eyre::bail!("duplicate target template key `{}`", target.template_key);
            }
            if !project_dirs.insert(target.project_dir.to_ascii_lowercase()) {
                eyre::bail!(
                    "duplicate target project directory `{}`",
                    target.project_dir
                );
            }
        }
        Ok(target_ids)
    }

    fn validate_features(&self, target_ids: &BTreeSet<&str>) -> eyre::Result<BTreeSet<&str>> {
        let mut feature_ids = BTreeSet::new();
        for feature in &self.features {
            validate_template_key(&feature.id, "feature")?;
            if !feature_ids.insert(feature.id.as_str()) {
                eyre::bail!("duplicate feature ID `{}`", feature.id);
            }
        }
        for feature in &self.features {
            if feature.supported_targets.is_empty() {
                eyre::bail!("feature `{}` supports no targets", feature.id);
            }
            unique_known_ids(
                &feature.supported_targets,
                target_ids,
                &format!("feature `{}` supported target", feature.id),
            )?;
            unique_known_ids(
                &feature.requires,
                &feature_ids,
                &format!("feature `{}` requirement", feature.id),
            )?;
            if feature
                .requires
                .iter()
                .any(|required| required == &feature.id)
            {
                eyre::bail!("feature `{}` requires itself", feature.id);
            }

            let mut output_paths = BTreeSet::new();
            for effect in feature
                .source_effects
                .iter()
                .chain(&feature.resource_effects)
            {
                validate_effect(effect, &feature.id)?;
                if !output_paths.insert(effect.output_path.to_ascii_lowercase()) {
                    eyre::bail!(
                        "feature `{}` declares output path `{}` more than once",
                        feature.id,
                        effect.output_path
                    );
                }
            }
            let mut components = BTreeSet::new();
            for effect in &feature.dependency_effects {
                validate_id(&effect.dependency_id, "dependency")?;
                validate_id(&effect.component_id, "dependency component")?;
                if !components.insert((&effect.dependency_id, &effect.component_id)) {
                    eyre::bail!(
                        "feature `{}` declares dependency component `{}/{}` more than once",
                        feature.id,
                        effect.dependency_id,
                        effect.component_id
                    );
                }
            }
        }
        Ok(feature_ids)
    }

    /// Return the target with this stable matrix ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the target ID is unknown.
    pub fn target(&self, id: &str) -> eyre::Result<&ProjectionTarget> {
        self.targets
            .iter()
            .find(|target| target.id == id)
            .ok_or_else(|| eyre::eyre!("unknown source-projection target `{id}`"))
    }

    /// Return the feature with this stable flag ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the feature ID is unknown.
    pub fn feature(&self, id: &str) -> eyre::Result<&ProjectionFeature> {
        self.features
            .iter()
            .find(|feature| feature.id == id)
            .ok_or_else(|| eyre::eyre!("unknown source-projection feature `{id}`"))
    }

    /// Return the preset with this stable identity ID.
    ///
    /// # Errors
    ///
    /// Returns an error if the preset ID is unknown.
    pub fn preset(&self, id: &str) -> eyre::Result<&ProjectionPreset> {
        self.presets
            .iter()
            .find(|preset| preset.id == id)
            .ok_or_else(|| eyre::eyre!("unknown source-projection preset `{id}`"))
    }

    /// Hash the *definition* of a preset, including selected target and feature
    /// effects. The projection manifest separately hashes actual file contents.
    ///
    /// # Errors
    ///
    /// Returns an error if the preset names a target or feature missing from
    /// this manifest.
    pub fn compute_preset_identity(&self, preset: &ProjectionPreset) -> eyre::Result<String> {
        let mut hasher = blake3::Hasher::new();
        hash_part(&mut hasher, "sfm:source-projection-preset@1");
        hash_part(&mut hasher, &preset.id);
        let mut target_ids = preset.targets.iter().collect::<Vec<_>>();
        target_ids.sort();
        hash_part(&mut hasher, "targets");
        hash_part(&mut hasher, &target_ids.len().to_string());
        for id in target_ids {
            let target = self.target(id)?;
            hash_part(&mut hasher, "target");
            hash_part(&mut hasher, &target.id);
            hash_part(&mut hasher, &target.template_key);
            hash_part(&mut hasher, &target.minecraft_version);
            hash_part(&mut hasher, &target.loader);
            hash_part(&mut hasher, &target.java_major.to_string());
            hash_part(&mut hasher, &target.project_dir);
        }
        let mut feature_ids = preset.enabled_features.iter().collect::<Vec<_>>();
        feature_ids.sort();
        hash_part(&mut hasher, "features");
        hash_part(&mut hasher, &feature_ids.len().to_string());
        for id in feature_ids {
            let feature = self.feature(id)?;
            hash_part(&mut hasher, "feature");
            hash_part(&mut hasher, &feature.id);
            hash_sorted_strings(&mut hasher, "supported_targets", &feature.supported_targets);
            hash_sorted_strings(&mut hasher, "requires", &feature.requires);
            let mut effects = feature
                .source_effects
                .iter()
                .map(|effect| ("source", effect))
                .chain(
                    feature
                        .resource_effects
                        .iter()
                        .map(|effect| ("resource", effect)),
                )
                .collect::<Vec<_>>();
            effects.sort_by(|(left_category, left), (right_category, right)| {
                (
                    *left_category,
                    &left.output_path,
                    left.kind.as_str(),
                    &left.input_path,
                )
                    .cmp(&(
                        *right_category,
                        &right.output_path,
                        right.kind.as_str(),
                        &right.input_path,
                    ))
            });
            hash_part(&mut hasher, "path_effects");
            hash_part(&mut hasher, &effects.len().to_string());
            for (category, effect) in effects {
                hash_part(&mut hasher, category);
                hash_part(&mut hasher, &effect.output_path);
                hash_part(&mut hasher, effect.kind.as_str());
                hash_part(&mut hasher, effect.input_path.as_deref().unwrap_or(""));
            }
            let mut dependencies = feature.dependency_effects.iter().collect::<Vec<_>>();
            dependencies.sort_by_key(|effect| (&effect.dependency_id, &effect.component_id));
            hash_part(&mut hasher, "dependency_effects");
            hash_part(&mut hasher, &dependencies.len().to_string());
            for dependency in dependencies {
                hash_part(&mut hasher, "dependency");
                hash_part(&mut hasher, &dependency.dependency_id);
                hash_part(&mut hasher, &dependency.component_id);
            }
        }
        if !preset.release_baselines.is_empty() {
            let mut baselines = preset.release_baselines.iter().collect::<Vec<_>>();
            baselines.sort_by_key(|baseline| &baseline.target_id);
            hash_part(&mut hasher, "release_baselines");
            hash_part(&mut hasher, &baselines.len().to_string());
            for baseline in baselines {
                hash_part(&mut hasher, &baseline.target_id);
                hash_part(&mut hasher, &baseline.tag_commit);
                hash_part(&mut hasher, &baseline.import_manifest);
                hash_part(&mut hasher, &baseline.import_manifest_sha256);
                if baseline.kind == BaselineKind::DevelopmentHead {
                    hash_part(&mut hasher, "development_head");
                    hash_part(
                        &mut hasher,
                        baseline.canonical_commit.as_deref().unwrap_or_default(),
                    );
                    hash_part(
                        &mut hasher,
                        baseline
                            .gradle_provenance_sha256
                            .as_deref()
                            .unwrap_or_default(),
                    );
                }
            }
        }
        Ok(format!("blake3:{}", hasher.finalize().to_hex()))
    }
}

impl ProjectionPreset {
    /// Return the pinned release import for one target, or `None` for a
    /// development preset.
    #[must_use]
    pub fn release_baseline_for(&self, target_id: &str) -> Option<&ReleaseBaselineBinding> {
        self.release_baselines
            .iter()
            .find(|baseline| baseline.target_id == target_id)
    }
}

fn validate_release_baselines(preset: &ProjectionPreset) -> eyre::Result<()> {
    if preset.release_baselines.is_empty() {
        return Ok(());
    }
    let mut seen_targets = BTreeSet::new();
    let mut seen_manifests = BTreeSet::new();
    for baseline in &preset.release_baselines {
        if !preset.targets.contains(&baseline.target_id) {
            eyre::bail!(
                "preset `{}` binds release baseline for unselected target `{}`",
                preset.id,
                baseline.target_id
            );
        }
        if !seen_targets.insert(baseline.target_id.as_str()) {
            eyre::bail!(
                "preset `{}` repeats release baseline target `{}`",
                preset.id,
                baseline.target_id
            );
        }
        validate_lower_hex(&baseline.tag_commit, 40, "release tag commit")?;
        validate_path(&baseline.import_manifest, "pinned import manifest")?;
        match baseline.kind {
            BaselineKind::ReleaseTag => {
                if !baseline
                    .import_manifest
                    .starts_with("platform/minecraft/release-baselines/")
                {
                    eyre::bail!(
                        "release import manifest `{}` must be under platform/minecraft/release-baselines/",
                        baseline.import_manifest
                    );
                }
                if baseline.canonical_commit.is_some()
                    || baseline.gradle_provenance_sha256.is_some()
                {
                    eyre::bail!("release import cannot declare development-head identities");
                }
            }
            BaselineKind::DevelopmentHead => {
                if baseline.import_manifest
                    != format!(
                        "platform/minecraft/development-baselines/{}/import.json",
                        baseline.target_id
                    )
                {
                    eyre::bail!(
                        "development import manifest path must match target `{}`",
                        baseline.target_id
                    );
                }
                validate_lower_hex(
                    baseline.canonical_commit.as_deref().unwrap_or_default(),
                    40,
                    "development canonical commit",
                )?;
                validate_lower_hex(
                    baseline
                        .gradle_provenance_sha256
                        .as_deref()
                        .unwrap_or_default(),
                    64,
                    "development Gradle provenance SHA-256",
                )?;
            }
        }
        if !seen_manifests.insert(baseline.import_manifest.to_ascii_lowercase()) {
            eyre::bail!(
                "preset `{}` repeats release import manifest `{}`",
                preset.id,
                baseline.import_manifest
            );
        }
        validate_lower_hex(
            &baseline.import_manifest_sha256,
            64,
            "release import manifest SHA-256",
        )?;
    }
    if seen_targets.len() != preset.targets.len() {
        eyre::bail!(
            "preset `{}` must bind a release baseline for every selected target",
            preset.id
        );
    }
    Ok(())
}

fn validate_lower_hex(value: &str, expected_len: usize, description: &str) -> eyre::Result<()> {
    if value.len() != expected_len
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || matches!(byte, b'a'..=b'f'))
    {
        eyre::bail!("{description} must be {expected_len} lowercase hexadecimal characters");
    }
    Ok(())
}

fn hash_part(hasher: &mut blake3::Hasher, part: &str) {
    hasher.update(&(part.len() as u64).to_le_bytes());
    hasher.update(part.as_bytes());
}

fn hash_sorted_strings(hasher: &mut blake3::Hasher, domain: &str, parts: &[String]) {
    let mut sorted = parts.iter().collect::<Vec<_>>();
    sorted.sort();
    hash_part(hasher, domain);
    hash_part(hasher, &sorted.len().to_string());
    for part in sorted {
        hash_part(hasher, part);
    }
}

fn unique_known_ids(ids: &[String], known: &BTreeSet<&str>, description: &str) -> eyre::Result<()> {
    let mut seen = BTreeSet::new();
    for id in ids {
        if !known.contains(id.as_str()) {
            eyre::bail!("{description} refers to unknown ID `{id}`");
        }
        if !seen.insert(id.as_str()) {
            eyre::bail!("{description} repeats ID `{id}`");
        }
    }
    Ok(())
}

fn validate_id(id: &str, description: &str) -> eyre::Result<()> {
    if id.is_empty()
        || !id.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"._-".contains(&byte)
        })
        || !id.as_bytes()[0].is_ascii_alphanumeric()
    {
        eyre::bail!(
            "{description} ID `{id}` must use lowercase ASCII letters, digits, dot, underscore or hyphen and start with a letter or digit"
        );
    }
    Ok(())
}

fn validate_template_key(key: &str, description: &str) -> eyre::Result<()> {
    if key.is_empty()
        || !key.as_bytes()[0].is_ascii_lowercase()
        || !key
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'_')
    {
        eyre::bail!(
            "{description} `{key}` must use lowercase snake_case and start with a letter so it can be used in a Liquid directive"
        );
    }
    Ok(())
}

fn validate_path(path: &str, description: &str) -> eyre::Result<()> {
    if path.is_empty()
        || path
            .chars()
            .any(|character| character.is_control() || "\\<>:\"|?*".contains(character))
        || path.split('/').any(|part| {
            part.is_empty()
                || part == "."
                || part == ".."
                || part.ends_with('.')
                || part.ends_with(' ')
                || is_windows_device_name(part)
        })
    {
        eyre::bail!("{description} `{path}` must be a portable, exact repository-relative path");
    }
    Ok(())
}

fn is_windows_device_name(segment: &str) -> bool {
    let stem = segment.split('.').next().unwrap_or_default();
    let upper = stem.to_ascii_uppercase();
    matches!(upper.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || (upper.len() == 4
            && (upper.starts_with("COM") || upper.starts_with("LPT"))
            && matches!(upper.as_bytes()[3], b'1'..=b'9'))
}

fn validate_effect(effect: &PathEffect, feature_id: &str) -> eyre::Result<()> {
    validate_path(&effect.output_path, "output path")?;
    match (effect.kind, effect.input_path.as_deref()) {
        (PathEffectKind::Template | PathEffectKind::Exclude, None) => {}
        (PathEffectKind::Include | PathEffectKind::Replace, Some(source)) => {
            validate_path(source, "input path")?;
        }
        _ => eyre::bail!(
            "feature `{feature_id}` effect `{}` has an invalid input path for {}",
            effect.output_path,
            effect.kind.as_str()
        ),
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> SourceProjectionManifest {
        let mut manifest = SourceProjectionManifest {
            schema_version: SCHEMA_VERSION,
            targets: vec![ProjectionTarget {
                id: "1.19.2".into(),
                template_key: "mc_1_19_2".into(),
                minecraft_version: "1.19.2".into(),
                loader: "forge".into(),
                java_major: 17,
                project_dir: "platform/minecraft/mc-version/1.19.2".into(),
            }],
            features: vec![ProjectionFeature {
                id: "touch_display".into(),
                supported_targets: vec!["1.19.2".into()],
                requires: vec![],
                source_effects: vec![PathEffect {
                    output_path: "src/main/java/ca/teamdman/sfm/TouchDisplay.java".into(),
                    kind: PathEffectKind::Include,
                    input_path: Some(
                        "platform/minecraft/src/main/java/ca/teamdman/sfm/TouchDisplay.java".into(),
                    ),
                }],
                resource_effects: vec![],
                dependency_effects: vec![],
            }],
            presets: vec![ProjectionPreset {
                id: "current-development".into(),
                targets: vec!["1.19.2".into()],
                enabled_features: vec!["touch_display".into()],
                release_baselines: vec![],
                identity: String::new(),
            }],
        };
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        manifest
    }

    fn release_binding(target_id: &str) -> ReleaseBaselineBinding {
        ReleaseBaselineBinding {
            target_id: target_id.into(),
            tag_commit: "a".repeat(40),
            import_manifest: format!(
                "platform/minecraft/release-baselines/released-4.34.0/{target_id}/import.json"
            ),
            import_manifest_sha256: "b".repeat(64),
            kind: BaselineKind::ReleaseTag,
            canonical_commit: None,
            gradle_provenance_sha256: None,
        }
    }

    #[test]
    fn accepts_well_formed_manifest() {
        sample().validate().unwrap();
    }

    #[test]
    fn facet_json_roundtrip_preserves_manifest() {
        let original = sample();
        let json = original.to_json().unwrap();
        assert!(json.ends_with('\n'));
        assert_eq!(
            SourceProjectionManifest::from_json(&json).unwrap(),
            original
        );
    }

    #[test]
    fn release_binding_roundtrips_and_is_in_preset_identity() {
        let mut manifest = sample();
        let development_identity = manifest.presets[0].identity.clone();
        manifest.presets[0]
            .release_baselines
            .push(release_binding("1.19.2"));
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        assert_ne!(development_identity, manifest.presets[0].identity);
        assert_eq!(
            manifest.presets[0]
                .release_baseline_for("1.19.2")
                .unwrap()
                .tag_commit,
            "a".repeat(40)
        );
        let json = manifest.to_json().unwrap();
        assert_eq!(
            SourceProjectionManifest::from_json(&json).unwrap(),
            manifest
        );

        manifest.presets[0].release_baselines[0].import_manifest_sha256 = "c".repeat(64);
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("identity mismatch")
        );
    }

    #[test]
    fn development_head_binding_requires_both_pinned_commits_and_gradle_hash() {
        let mut manifest = sample();
        let mut binding = release_binding("1.19.2");
        binding.kind = BaselineKind::DevelopmentHead;
        binding.import_manifest =
            "platform/minecraft/development-baselines/1.19.2/import.json".to_owned();
        binding.canonical_commit = Some("c".repeat(40));
        binding.gradle_provenance_sha256 = Some("d".repeat(64));
        manifest.presets[0].release_baselines.push(binding);
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        manifest.validate().unwrap();
        let json = manifest.to_json().unwrap();
        assert_eq!(
            SourceProjectionManifest::from_json(&json).unwrap(),
            manifest
        );

        manifest.presets[0].release_baselines[0].canonical_commit = None;
        assert!(manifest.validate().is_err());
        manifest.presets[0].release_baselines[0].canonical_commit = Some("c".repeat(40));
        manifest.presets[0].release_baselines[0].gradle_provenance_sha256 = None;
        assert!(manifest.validate().is_err());
    }

    #[test]
    fn release_binding_requires_each_selected_target_and_valid_digests() {
        let mut manifest = sample();
        manifest.presets[0]
            .release_baselines
            .push(release_binding("1.19.2"));
        manifest.targets.push(ProjectionTarget {
            id: "26.1.2".into(),
            template_key: "mc_26_1_2".into(),
            minecraft_version: "26.1.2".into(),
            loader: "neoforge".into(),
            java_major: 25,
            project_dir: "platform/minecraft/mc-version/26.1.2".into(),
        });
        manifest.presets[0].targets.push("26.1.2".into());
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("every selected target")
        );

        let mut manifest = sample();
        let mut binding = release_binding("1.19.2");
        binding.import_manifest_sha256 = "not-a-hash".into();
        manifest.presets[0].release_baselines.push(binding);
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("SHA-256")
        );
    }

    #[test]
    fn rejects_unknown_and_unsupported_ids() {
        let mut manifest = sample();
        manifest.presets[0].enabled_features.push("unknown".into());
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("unknown ID")
        );

        let mut manifest = sample();
        manifest.targets.push(ProjectionTarget {
            id: "26.1.2".into(),
            template_key: "mc_26_1_2".into(),
            minecraft_version: "26.1.2".into(),
            loader: "neoforge".into(),
            java_major: 25,
            project_dir: "platform/minecraft/mc-version/26.1.2".into(),
        });
        manifest.presets[0].targets.push("26.1.2".into());
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("unsupported target")
        );
    }

    #[test]
    fn rejects_missing_required_feature() {
        let mut manifest = sample();
        manifest.features.push(ProjectionFeature {
            id: "packet".into(),
            supported_targets: vec!["1.19.2".into()],
            requires: vec![],
            source_effects: vec![],
            resource_effects: vec![],
            dependency_effects: vec![],
        });
        manifest.features[0].requires.push("packet".into());
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("without required")
        );
    }

    #[test]
    fn rejects_duplicate_or_unsafe_paths() {
        let mut manifest = sample();
        manifest.features[0].source_effects[0].output_path = "src/../escape.java".into();
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("portable")
        );

        let mut manifest = sample();
        let mut duplicate = manifest.features[0].source_effects[0].clone();
        duplicate.output_path = duplicate.output_path.to_ascii_lowercase();
        manifest.features[0].resource_effects.push(duplicate);
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("more than once")
        );

        let mut manifest = sample();
        manifest.features[0].source_effects[0].output_path = "src/main/java/CON.java".into();
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("portable")
        );
    }

    #[test]
    fn directive_keys_are_unambiguous_snake_case() {
        let mut manifest = sample();
        manifest.features[0].id = "touch-display".into();
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("snake_case")
        );

        let mut manifest = sample();
        manifest.targets[0].template_key = "1.19.2".into();
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("snake_case")
        );
    }

    #[test]
    fn two_features_may_gate_one_template() {
        let mut manifest = sample();
        manifest.features[0].source_effects[0].kind = PathEffectKind::Template;
        manifest.features[0].source_effects[0].input_path = None;
        let mut other = manifest.features[0].clone();
        other.id = "other".into();
        manifest.features.push(other);
        manifest.presets[0].enabled_features.push("other".into());
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        manifest.validate().unwrap();
    }

    #[test]
    fn rejects_conflicting_feature_file_effects() {
        let mut manifest = sample();
        let mut other = manifest.features[0].clone();
        other.id = "other".into();
        manifest.features.push(other);
        manifest.presets[0].enabled_features.push("other".into());
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("conflicting effects")
        );
    }

    #[test]
    fn detects_mutated_published_preset() {
        let mut manifest = sample();
        manifest.features[0].resource_effects.push(PathEffect {
            output_path: "src/main/resources/assets/sfm/textures/block/touch_display.png".into(),
            kind: PathEffectKind::Include,
            input_path: Some(
                "platform/minecraft/src/main/resources/assets/sfm/textures/block/touch_display.png"
                    .into(),
            ),
        });
        assert!(
            manifest
                .validate()
                .unwrap_err()
                .to_string()
                .contains("identity mismatch")
        );
    }

    #[test]
    fn preset_identity_is_independent_of_declaration_order() {
        let mut manifest = sample();
        manifest.features.push(ProjectionFeature {
            id: "packet".into(),
            supported_targets: vec!["1.19.2".into()],
            requires: vec![],
            source_effects: vec![],
            resource_effects: vec![],
            dependency_effects: vec![],
        });
        manifest.presets[0].enabled_features.push("packet".into());
        let expected = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        manifest.features.reverse();
        manifest.presets[0].enabled_features.reverse();
        assert_eq!(
            expected,
            manifest
                .compute_preset_identity(&manifest.presets[0])
                .unwrap()
        );
    }

    #[test]
    fn preset_identity_distinguishes_feature_set_boundaries() {
        let mut manifest = sample();
        manifest.features[0].supported_targets = vec!["a".into()];
        manifest.features[0].requires = vec!["b".into()];
        let first = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        manifest.features[0].supported_targets = vec!["a".into(), "b".into()];
        manifest.features[0].requires.clear();
        let second = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        assert_ne!(first, second);
    }
}
