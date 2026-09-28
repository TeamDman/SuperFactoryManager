use super::v3::ArtifactLockfileV3;
use super::v3::ArtifactV3;
use super::v3::DependencyV3;
use super::v3::LockfilePolicyV3;
use super::v3::PlatformV3;
use super::v3::RepositoryV3;
use crate::jar_build::ArtifactLockfile;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;

pub(crate) const SCHEMA_VERSION: u32 = 4;

/// The v4 lockfile adds declarative feature ownership and entry-point profiles
/// while keeping the dependency and artifact records shared with v3.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ArtifactLockfileV4 {
    pub(crate) schema_version: u32,
    pub(crate) platform: PlatformV3,
    pub(crate) policy: LockfilePolicyV3,
    pub(crate) repositories: Vec<RepositoryV3>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub(crate) jdk_pins: Option<Vec<JdkPinV4>>,
    pub(crate) features: Vec<FeatureV4>,
    pub(crate) profiles: Vec<ProfileV4>,
    pub(crate) dependencies: Vec<DependencyV3>,
    pub(crate) artifacts: Vec<ArtifactV3>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct FeatureV4 {
    pub(crate) id: String,
    #[facet(default)]
    pub(crate) requires: Vec<String>,
    #[facet(default)]
    pub(crate) components: Vec<FeatureComponentV4>,
    #[facet(default)]
    pub(crate) source_sets: Vec<String>,
    #[facet(default)]
    pub(crate) source_excludes: Vec<SourceExcludeV4>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct FeatureComponentV4 {
    pub(crate) dependency_id: String,
    pub(crate) component_id: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct SourceExcludeV4 {
    pub(crate) source_set: String,
    pub(crate) path: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ProfileV4 {
    pub(crate) id: String,
    pub(crate) features: Vec<String>,
}

/// Exact SDK releases are independent of Maven and Minecraft artifact records.
/// A missing catalog preserves the legacy installed-JDK selection policy.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct JdkPinV4 {
    pub(crate) major: u32,
    pub(crate) vendor: String,
    pub(crate) version: String,
    pub(crate) build: String,
    pub(crate) flavor: String,
    pub(crate) artifacts: Vec<JdkArtifactV4>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct JdkArtifactV4 {
    pub(crate) platform: String,
    pub(crate) url: String,
    pub(crate) sha512: String,
}

impl ArtifactLockfileV4 {
    pub(crate) fn from_v3(lockfile: ArtifactLockfileV3) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            platform: lockfile.platform,
            policy: lockfile.policy,
            repositories: lockfile.repositories,
            jdk_pins: None,
            features: Vec::new(),
            profiles: vec![
                ProfileV4 {
                    id: "gradle".to_owned(),
                    features: Vec::new(),
                },
                ProfileV4 {
                    id: "rust-toolchain".to_owned(),
                    features: Vec::new(),
                },
            ],
            dependencies: lockfile.dependencies,
            artifacts: lockfile.artifacts,
        }
    }

    pub(crate) fn to_canonical_json(&self) -> eyre::Result<String> {
        let mut canonical = self.clone();
        canonical.canonicalize();
        canonical.validate()?;
        let mut output = facet_json::to_string_pretty(&canonical)?;
        output.push('\n');
        Ok(output)
    }

    pub(crate) fn validate(&self) -> eyre::Result<()> {
        if self.schema_version != SCHEMA_VERSION {
            eyre::bail!(
                "v4 lockfile declares schema_version {}, expected {SCHEMA_VERSION}",
                self.schema_version
            );
        }

        let base = self.as_v3(self.dependencies.clone(), self.artifacts.clone());
        base.validate()?;
        validate_jdk_pins(self.jdk_pins.as_deref())?;

        let feature_ids = unique_ids(
            self.features.iter().map(|feature| feature.id.as_str()),
            "feature",
        )?;
        let _profile_ids = unique_ids(
            self.profiles.iter().map(|profile| profile.id.as_str()),
            "profile",
        )?;
        let component_ids = self
            .dependencies
            .iter()
            .flat_map(|dependency| {
                dependency
                    .components
                    .iter()
                    .map(move |component| (dependency.id.as_str(), component.id.as_str()))
            })
            .collect::<BTreeSet<_>>();

        for feature in &self.features {
            for required in &feature.requires {
                require_reference(&feature_ids, required, "feature")?;
            }
            for component in &feature.components {
                if !component_ids.contains(&(
                    component.dependency_id.as_str(),
                    component.component_id.as_str(),
                )) {
                    eyre::bail!(
                        "feature `{}` references unknown component `{}/{}`",
                        feature.id,
                        component.dependency_id,
                        component.component_id
                    );
                }
            }
            validate_strings(&feature.source_sets, "source set")?;
            for source_exclude in &feature.source_excludes {
                let path = Path::new(&source_exclude.path);
                if source_exclude.source_set.trim().is_empty()
                    || source_exclude.path.trim().is_empty()
                    || path.is_absolute()
                    || path.starts_with("..")
                {
                    eyre::bail!(
                        "feature `{}` contains a non-portable source exclusion `{}/{}`",
                        feature.id,
                        source_exclude.source_set,
                        source_exclude.path
                    );
                }
            }
        }

        for profile in &self.profiles {
            for feature in &profile.features {
                require_reference(&feature_ids, feature, "feature")?;
            }
        }

        Ok(())
    }

    /// Projects the declarative v4 document into the v3-shaped lockfile used
    /// by the existing resolver and build engine.
    pub(crate) fn effective_lockfile(&self, profile_id: &str) -> eyre::Result<ArtifactLockfileV3> {
        self.validate()?;
        let active_features = self.active_features(profile_id)?;
        let mut ownership = BTreeMap::<(&str, &str), bool>::new();
        for feature in &self.features {
            for component in &feature.components {
                let key = (
                    component.dependency_id.as_str(),
                    component.component_id.as_str(),
                );
                let enabled = active_features.contains(feature.id.as_str());
                ownership
                    .entry(key)
                    .and_modify(|value| *value |= enabled)
                    .or_insert(enabled);
            }
        }

        let mut dependencies = Vec::new();
        for dependency in &self.dependencies {
            let mut projected = dependency.clone();
            projected.components.retain(|component| {
                ownership
                    .get(&(dependency.id.as_str(), component.id.as_str()))
                    .copied()
                    .unwrap_or(true)
            });
            if projected.components.is_empty() {
                if dependency.role == super::v3::DependencyRoleV3::Platform {
                    eyre::bail!(
                        "profile `{profile_id}` disables all components of platform dependency `{}`",
                        dependency.id
                    );
                }
                continue;
            }
            dependencies.push(projected);
        }

        let dependency_components = dependencies
            .iter()
            .flat_map(|dependency| {
                dependency
                    .components
                    .iter()
                    .map(move |component| (dependency.id.as_str(), component.id.as_str()))
            })
            .collect::<BTreeSet<_>>();
        let artifacts = self
            .artifacts
            .iter()
            .filter(|artifact| {
                artifact.owner.as_ref().is_none_or(|owner| {
                    dependency_components
                        .contains(&(owner.dependency_id.as_str(), owner.component_id.as_str()))
                })
            })
            .cloned()
            .collect();

        let projected = self.as_v3(dependencies, artifacts);
        projected.validate()?;
        Ok(projected)
    }

    /// Refreshes resolver-owned evidence for the Rust-toolchain projection
    /// without discarding feature/profile declarations or components owned by
    /// inactive features.
    pub(crate) fn refresh_resolved_artifacts(
        &self,
        resolved: &ArtifactLockfile,
    ) -> eyre::Result<Self> {
        self.validate()?;
        let effective = self.effective_lockfile("rust-toolchain")?;
        let refreshed_effective = effective.refresh_resolved_artifacts(resolved)?;
        let mut refreshed = self.clone();

        for dependency in refreshed_effective.dependencies {
            let maintained_dependency = refreshed
                .dependencies
                .iter_mut()
                .find(|candidate| candidate.id == dependency.id)
                .expect("effective dependencies originate in the maintained v4 document");
            for component in dependency.components {
                let maintained_component = maintained_dependency
                    .components
                    .iter_mut()
                    .find(|candidate| candidate.id == component.id)
                    .expect("effective components originate in the maintained v4 document");
                maintained_component
                    .derived_checks
                    .clone_from(&component.derived_checks);
            }
        }

        for artifact in refreshed_effective.artifacts {
            if let Some(maintained) = refreshed
                .artifacts
                .iter_mut()
                .find(|candidate| candidate.id == artifact.id)
            {
                *maintained = artifact;
            } else {
                refreshed.artifacts.push(artifact);
            }
        }

        refreshed.canonicalize();
        refreshed.validate()?;
        Ok(refreshed)
    }

    fn active_features(&self, profile_id: &str) -> eyre::Result<BTreeSet<&str>> {
        let profile = self
            .profiles
            .iter()
            .find(|profile| profile.id == profile_id)
            .ok_or_else(|| eyre::eyre!("unknown lockfile profile `{profile_id}`"))?;
        let features = self
            .features
            .iter()
            .map(|feature| (feature.id.as_str(), feature))
            .collect::<BTreeMap<_, _>>();
        let mut active = BTreeSet::new();
        let mut visiting = BTreeSet::new();
        for feature in &profile.features {
            visit_feature(feature, &features, &mut active, &mut visiting)?;
        }
        Ok(active)
    }

    /// Return source exclusions owned by features that are inactive for a
    /// profile. These are the source-side counterpart of filtering feature-
    /// owned dependency components in [`Self::effective_lockfile`].
    pub(crate) fn effective_source_excludes(
        &self,
        profile_id: &str,
    ) -> eyre::Result<Vec<SourceExcludeV4>> {
        self.validate()?;
        let active = self.active_features(profile_id)?;
        let mut excludes = self
            .features
            .iter()
            .filter(|feature| !active.contains(feature.id.as_str()))
            .flat_map(|feature| feature.source_excludes.iter().cloned())
            .collect::<Vec<_>>();
        excludes.sort_by(|left, right| {
            left.source_set
                .cmp(&right.source_set)
                .then(left.path.cmp(&right.path))
        });
        excludes
            .dedup_by(|left, right| left.source_set == right.source_set && left.path == right.path);
        Ok(excludes)
    }

    fn as_v3(
        &self,
        dependencies: Vec<DependencyV3>,
        artifacts: Vec<ArtifactV3>,
    ) -> ArtifactLockfileV3 {
        ArtifactLockfileV3 {
            schema_version: super::v3::SCHEMA_VERSION,
            platform: self.platform.clone(),
            policy: self.policy.clone(),
            repositories: self.repositories.clone(),
            dependencies,
            artifacts,
        }
    }

    fn canonicalize(&mut self) {
        let dependencies = std::mem::take(&mut self.dependencies);
        let artifacts = std::mem::take(&mut self.artifacts);
        let mut base = self.as_v3(dependencies, artifacts);
        base.canonicalize();
        self.dependencies = base.dependencies;
        self.artifacts = base.artifacts;
        self.repositories = base.repositories;
        if let Some(pins) = &mut self.jdk_pins {
            pins.sort_by_key(|pin| pin.major);
            for pin in pins {
                pin.artifacts
                    .sort_by(|left, right| left.platform.cmp(&right.platform));
                for artifact in &mut pin.artifacts {
                    artifact.sha512.make_ascii_lowercase();
                }
            }
        }
        self.features.sort_by(|left, right| left.id.cmp(&right.id));
        for feature in &mut self.features {
            feature.requires.sort();
            feature.requires.dedup();
            feature.components.sort_by(|left, right| {
                left.dependency_id
                    .cmp(&right.dependency_id)
                    .then(left.component_id.cmp(&right.component_id))
            });
            feature.components.dedup_by(|left, right| {
                left.dependency_id == right.dependency_id && left.component_id == right.component_id
            });
            feature.source_sets.sort();
            feature.source_sets.dedup();
            feature.source_excludes.sort_by(|left, right| {
                left.source_set
                    .cmp(&right.source_set)
                    .then(left.path.cmp(&right.path))
            });
            feature.source_excludes.dedup_by(|left, right| {
                left.source_set == right.source_set && left.path == right.path
            });
        }
        self.profiles.sort_by(|left, right| left.id.cmp(&right.id));
        for profile in &mut self.profiles {
            profile.features.sort();
            profile.features.dedup();
        }
    }
}

fn visit_feature<'a>(
    id: &'a str,
    features: &BTreeMap<&str, &'a FeatureV4>,
    active: &mut BTreeSet<&'a str>,
    visiting: &mut BTreeSet<&'a str>,
) -> eyre::Result<()> {
    if active.contains(id) {
        return Ok(());
    }
    if !visiting.insert(id) {
        eyre::bail!("cyclic lockfile feature requirement involving `{id}`");
    }
    let feature = features
        .get(id)
        .ok_or_else(|| eyre::eyre!("unknown lockfile feature `{id}`"))?;
    for required in &feature.requires {
        visit_feature(required, features, active, visiting)?;
    }
    visiting.remove(id);
    active.insert(id);
    Ok(())
}

fn unique_ids<'a>(
    values: impl IntoIterator<Item = &'a str>,
    kind: &str,
) -> eyre::Result<BTreeSet<&'a str>> {
    let mut ids = BTreeSet::new();
    for value in values {
        if value.trim().is_empty() {
            eyre::bail!("{kind} id must not be empty");
        }
        if !ids.insert(value) {
            eyre::bail!("duplicate {kind} id `{value}`");
        }
    }
    Ok(ids)
}

fn require_reference(values: &BTreeSet<&str>, value: &str, kind: &str) -> eyre::Result<()> {
    if !values.contains(value) {
        eyre::bail!("unknown {kind} reference `{value}`");
    }
    Ok(())
}

fn validate_strings(values: &[String], kind: &str) -> eyre::Result<()> {
    for value in values {
        if value.trim().is_empty() {
            eyre::bail!("{kind} must not be empty");
        }
    }
    Ok(())
}

fn validate_jdk_pins(pins: Option<&[JdkPinV4]>) -> eyre::Result<()> {
    let Some(pins) = pins else {
        return Ok(());
    };
    if pins.is_empty() {
        eyre::bail!("jdk_pins must contain at least one SDK pin when present");
    }

    let mut majors = BTreeSet::new();
    for pin in pins {
        if !majors.insert(pin.major) {
            eyre::bail!("duplicate JDK pin for Java {}", pin.major);
        }
        if pin.major == 0
            || pin.version.split('.').count() < 3
            || !pin
                .version
                .split('.')
                .all(|part| !part.is_empty() && part.bytes().all(|byte| byte.is_ascii_digit()))
            || pin
                .version
                .split('.')
                .next()
                .and_then(|part| part.parse::<u32>().ok())
                != Some(pin.major)
        {
            eyre::bail!(
                "JDK pin for Java {} has an invalid version `{}`",
                pin.major,
                pin.version
            );
        }
        let Some((family, revision)) = pin
            .build
            .strip_prefix('b')
            .and_then(|build| build.split_once('.'))
        else {
            eyre::bail!(
                "JDK pin for Java {} has an invalid build `{}`",
                pin.major,
                pin.build
            );
        };
        if family.is_empty()
            || revision.is_empty()
            || !family.bytes().all(|byte| byte.is_ascii_digit())
            || !revision.bytes().all(|byte| byte.is_ascii_digit())
        {
            eyre::bail!(
                "JDK pin for Java {} has an invalid build `{}`",
                pin.major,
                pin.build
            );
        }
        if pin.vendor != "JetBrains" || pin.flavor != "jbrsdk" {
            eyre::bail!(
                "JDK pin for Java {} must select the JetBrains jbrsdk flavor",
                pin.major
            );
        }
        if pin.artifacts.is_empty() {
            eyre::bail!("JDK pin for Java {} has no platform artifacts", pin.major);
        }

        let mut platforms = BTreeSet::new();
        for artifact in &pin.artifacts {
            if artifact.platform.is_empty()
                || !artifact
                    .platform
                    .bytes()
                    .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
                || !platforms.insert(artifact.platform.as_str())
            {
                eyre::bail!(
                    "JDK pin for Java {} has an invalid or duplicate platform `{}`",
                    pin.major,
                    artifact.platform
                );
            }
            validate_jdk_artifact_url(pin, artifact)?;
            if artifact.sha512.len() != 128
                || !artifact.sha512.bytes().all(|byte| byte.is_ascii_hexdigit())
            {
                eyre::bail!(
                    "JDK pin for Java {} platform `{}` needs a 128-character SHA-512 digest",
                    pin.major,
                    artifact.platform
                );
            }
        }
    }
    Ok(())
}

fn validate_jdk_artifact_url(pin: &JdkPinV4, artifact: &JdkArtifactV4) -> eyre::Result<()> {
    let root = "https://cache-redirector.jetbrains.com/intellij-jbr/";
    let name = format!("jbrsdk-{}-{}-{}", pin.version, artifact.platform, pin.build);
    let tar_url = format!("{root}{name}.tar.gz");
    eyre::ensure!(
        artifact.url != tar_url,
        "only ZIP artifacts are supported for JDK pins"
    );
    if artifact.url != format!("{root}{name}.zip") {
        eyre::bail!(
            "JDK pin for Java {} platform `{}` has a URL that does not match its exact SDK identity",
            pin.major,
            artifact.platform
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const CHECKED_IN_LOCKFILE: &str =
        include_str!("../../../../../minecraft/sfm-toolchain.lock.json");

    fn fixture_pin(major: u32, version: &str, build: &str) -> JdkPinV4 {
        JdkPinV4 {
            major,
            vendor: "JetBrains".to_owned(),
            version: version.to_owned(),
            build: build.to_owned(),
            flavor: "jbrsdk".to_owned(),
            artifacts: vec![JdkArtifactV4 {
                platform: "windows-x64".to_owned(),
                url: format!(
                    "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-{version}-windows-x64-{build}.zip"
                ),
                sha512: "a".repeat(128),
            }],
        }
    }

    #[test]
    fn v3_migration_creates_compatible_entry_point_profiles() {
        let v3 = ArtifactLockfileV3 {
            schema_version: super::super::v3::SCHEMA_VERSION,
            platform: PlatformV3 {
                minecraft_dependency: "minecraft".to_owned(),
                loader_dependency: "loader".to_owned(),
            },
            policy: LockfilePolicyV3 {
                allow_local_artifact_cache: false,
            },
            repositories: Vec::new(),
            dependencies: Vec::new(),
            artifacts: Vec::new(),
        };
        let migrated = ArtifactLockfileV4::from_v3(v3);
        assert_eq!(migrated.schema_version, SCHEMA_VERSION);
        assert!(migrated.jdk_pins.is_none());
        assert!(migrated.features.is_empty());
        assert_eq!(migrated.profiles.len(), 2);
        let _ = migrated.effective_lockfile("gradle").unwrap_err();
    }

    #[test]
    fn profiles_project_feature_owned_components_without_dependency_names() {
        let lockfile: ArtifactLockfileV4 =
            facet_json::from_str(CHECKED_IN_LOCKFILE).expect("fixture should parse");
        let gradle = lockfile
            .effective_lockfile("gradle")
            .expect("Gradle profile should project");
        assert!(
            !gradle
                .dependencies
                .iter()
                .any(|dependency| dependency.id == "vox-java")
        );
        for retained in ["cc-tweaked", "mekanism"] {
            assert!(
                gradle
                    .dependencies
                    .iter()
                    .any(|dependency| dependency.id == retained),
                "ordinary integration {retained} must not be feature-gated"
            );
        }

        let rust = lockfile
            .effective_lockfile("rust-toolchain")
            .expect("Rust profile should project");
        assert!(
            rust.dependencies
                .iter()
                .any(|dependency| dependency.id == "vox-java")
        );
    }

    #[test]
    fn feature_requirements_are_transitive() {
        let mut lockfile: ArtifactLockfileV4 =
            facet_json::from_str(CHECKED_IN_LOCKFILE).expect("fixture should parse");
        lockfile.features.push(FeatureV4 {
            id: "terminal-wrapper".to_owned(),
            requires: vec!["rust".to_owned()],
            components: Vec::new(),
            source_sets: Vec::new(),
            source_excludes: Vec::new(),
        });
        lockfile.profiles[0]
            .features
            .push("terminal-wrapper".to_owned());
        let projected = lockfile
            .effective_lockfile("gradle")
            .expect("transitive feature profile should project");
        assert!(
            projected
                .dependencies
                .iter()
                .any(|dependency| dependency.id == "vox-java")
        );
    }

    #[test]
    fn inactive_features_contribute_source_excludes() {
        let lockfile: ArtifactLockfileV4 =
            facet_json::from_str(CHECKED_IN_LOCKFILE).expect("fixture should parse");

        let gradle = lockfile
            .effective_source_excludes("gradle")
            .expect("Gradle exclusions should project");
        assert!(gradle.iter().any(|exclude| {
            exclude.source_set == "main-java"
                && exclude
                    .path
                    .ends_with("client/terminal/SFMVoxTerminalService.java")
        }));

        assert!(
            lockfile
                .effective_source_excludes("rust-toolchain")
                .expect("Rust exclusions should project")
                .is_empty()
        );
    }

    #[test]
    fn old_v4_without_jdk_pins_round_trips_without_adding_a_catalog() {
        let mut lockfile: ArtifactLockfileV4 =
            facet_json::from_str(CHECKED_IN_LOCKFILE).expect("checked-in v4 lockfile should parse");
        lockfile.jdk_pins = None;
        assert!(lockfile.jdk_pins.is_none());
        let output = lockfile.to_canonical_json().expect("canonical v4 JSON");
        assert!(!output.contains("\"jdk_pins\""));
        let reparsed: ArtifactLockfileV4 = facet_json::from_str(&output).unwrap();
        assert!(reparsed.jdk_pins.is_none());
        assert_eq!(reparsed.dependencies, lockfile.dependencies);
        assert_eq!(reparsed.artifacts, lockfile.artifacts);
    }

    #[test]
    fn jdk_pins_round_trip_with_canonical_order_and_untouched_dependencies() {
        let mut lockfile: ArtifactLockfileV4 = facet_json::from_str(CHECKED_IN_LOCKFILE).unwrap();
        let original_dependencies = lockfile.dependencies.clone();
        let original_artifacts = lockfile.artifacts.clone();
        let mut java17 = fixture_pin(17, "17.0.14", "b1367.22");
        java17.artifacts.push(JdkArtifactV4 {
            platform: "linux-x64".to_owned(),
            url: "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.14-linux-x64-b1367.22.zip".to_owned(),
            sha512: "B".repeat(128),
        });
        lockfile.jdk_pins = Some(vec![fixture_pin(21, "21.0.11", "b1163.116"), java17]);

        let output = lockfile.to_canonical_json().expect("valid pinned v4 JSON");
        let reparsed: ArtifactLockfileV4 = facet_json::from_str(&output).unwrap();
        reparsed.validate().unwrap();
        let pins = reparsed.jdk_pins.unwrap();
        assert_eq!(
            pins.iter().map(|pin| pin.major).collect::<Vec<_>>(),
            vec![17, 21]
        );
        assert_eq!(pins[0].artifacts[0].platform, "linux-x64");
        assert_eq!(pins[0].artifacts[0].sha512, "b".repeat(128));
        assert_eq!(reparsed.dependencies, original_dependencies);
        assert_eq!(reparsed.artifacts, original_artifacts);
    }

    #[test]
    fn jdk_pin_validation_rejects_ambiguous_and_incomplete_catalogs() {
        let mut lockfile: ArtifactLockfileV4 = facet_json::from_str(CHECKED_IN_LOCKFILE).unwrap();
        assert!(lockfile.validate().is_ok());

        lockfile.jdk_pins = Some(Vec::new());
        assert!(
            lockfile
                .validate()
                .unwrap_err()
                .to_string()
                .contains("at least one")
        );

        let pin = fixture_pin(17, "17.0.14", "b1367.22");
        lockfile.jdk_pins = Some(vec![pin.clone(), pin.clone()]);
        assert!(
            lockfile
                .validate()
                .unwrap_err()
                .to_string()
                .contains("duplicate JDK pin")
        );

        let mut invalid = pin.clone();
        invalid.artifacts.push(invalid.artifacts[0].clone());
        lockfile.jdk_pins = Some(vec![invalid]);
        assert!(
            lockfile
                .validate()
                .unwrap_err()
                .to_string()
                .contains("duplicate platform")
        );

        let mut invalid = pin.clone();
        invalid.artifacts[0].sha512 = "a".repeat(127);
        lockfile.jdk_pins = Some(vec![invalid]);
        assert!(
            lockfile
                .validate()
                .unwrap_err()
                .to_string()
                .contains("SHA-512")
        );

        let mut invalid = pin.clone();
        invalid.artifacts[0].url = invalid.artifacts[0].url.replace(".zip", ".tar.gz");
        lockfile.jdk_pins = Some(vec![invalid]);
        assert!(
            lockfile
                .validate()
                .unwrap_err()
                .to_string()
                .contains("only ZIP artifacts are supported")
        );

        let mut invalid = pin.clone();
        invalid.artifacts[0].url = invalid.artifacts[0].url.replace("jbrsdk-", "jbr-");
        lockfile.jdk_pins = Some(vec![invalid]);
        assert!(
            lockfile
                .validate()
                .unwrap_err()
                .to_string()
                .contains("exact SDK identity")
        );

        let mut invalid = pin;
        invalid.version = "21.0.11".to_owned();
        lockfile.jdk_pins = Some(vec![invalid]);
        assert!(
            lockfile
                .validate()
                .unwrap_err()
                .to_string()
                .contains("invalid version")
        );
    }
}
