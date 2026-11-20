use super::projection_environment::ProjectionEnvironment;
use super::sfm_projection_feature_registry::SfmProjectionFeatureRegistry;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

/// Stable matrix IDs and their exact upstream Minecraft version values.
/// In particular, `1.21.0` is a matrix ID, not an upstream release version.
pub const SUPPORTED_TARGETS: [(&str, &str); 10] = [
    ("1.19.2", "1.19.2"),
    ("1.19.4", "1.19.4"),
    ("1.20", "1.20"),
    ("1.20.1", "1.20.1"),
    ("1.20.2", "1.20.2"),
    ("1.20.3", "1.20.3"),
    ("1.20.4", "1.20.4"),
    ("1.21.0", "1.21"),
    ("1.21.1", "1.21.1"),
    ("26.1.2", "26.1.2"),
];

/// The template inputs declared for one named projection.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ProjectionDefinition {
    pub minecraft_version: String,
    pub environment: ProjectionEnvironment,
    /// Exactly the enabled features. Known features omitted here are disabled.
    pub features: Vec<String>,
}

impl ProjectionDefinition {
    /// Derive the stable build target solely from the authoritative context.
    /// Projection directory names do not influence this selection.
    ///
    /// # Errors
    ///
    /// Returns an error for a Minecraft version outside the supported matrix.
    pub fn target_id(&self) -> eyre::Result<&'static str> {
        SUPPORTED_TARGETS
            .iter()
            .find(|(_, minecraft_version)| *minecraft_version == self.minecraft_version)
            .map(|(target_id, _)| *target_id)
            .ok_or_else(|| {
                eyre::eyre!(
                    "unsupported Minecraft version `{}`; use exact upstream versions (target `1.21.0` uses `1.21`)",
                    self.minecraft_version
                )
            })
    }

    /// Resolve every registered flag explicitly. The environment cannot enable
    /// omitted flags, and misspelled names cannot silently behave as false.
    ///
    /// # Errors
    ///
    /// Returns an error for duplicate or unregistered enabled feature names.
    pub fn feature_flags(
        &self,
        registry: &SfmProjectionFeatureRegistry,
    ) -> eyre::Result<BTreeMap<String, bool>> {
        let enabled = self.validated_features(|feature| registry.contains(feature))?;
        Ok(registry
            .names()
            .map(|feature| (feature.to_owned(), enabled.contains(feature)))
            .collect())
    }

    pub(super) fn validated_features(
        &self,
        is_registered: impl Fn(&str) -> bool,
    ) -> eyre::Result<BTreeSet<&str>> {
        let enabled = self.unique_features()?;
        for feature in &enabled {
            ensure!(
                is_registered(feature),
                "unknown enabled feature `{feature}`"
            );
        }
        Ok(enabled)
    }

    pub(super) fn unique_features(&self) -> eyre::Result<BTreeSet<&str>> {
        let mut enabled = BTreeSet::new();
        for feature in &self.features {
            ensure!(
                enabled.insert(feature.as_str()),
                "enabled feature `{feature}` is declared more than once"
            );
        }
        Ok(enabled)
    }
}
