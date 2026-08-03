use super::v3::ArtifactLockfileV3;
use super::v3::ArtifactV3;
use super::v3::DependencyV3;
use super::v3::LockfilePolicyV3;
use super::v3::PlatformV3;
use super::v3::RepositoryV3;
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

impl ArtifactLockfileV4 {
    pub(crate) fn from_v3(lockfile: ArtifactLockfileV3) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            platform: lockfile.platform,
            policy: lockfile.policy,
            repositories: lockfile.repositories,
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

#[cfg(test)]
mod tests {
    use super::*;

    const CHECKED_IN_LOCKFILE: &str =
        include_str!("../../../../../minecraft/sfm-toolchain.lock.json");

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
        assert!(migrated.features.is_empty());
        assert_eq!(migrated.profiles.len(), 2);
        assert!(migrated.effective_lockfile("gradle").is_err());
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
}
