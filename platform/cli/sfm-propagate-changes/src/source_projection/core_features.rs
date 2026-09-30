//! Functional compilation features owned by the shared authored tree.
//!
//! This registry describes support and prerequisites, not projection selectors.
//! A development environment never enables a feature implicitly.

use super::projection_catalog::ProjectionEntry;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::reject_duplicate_catalog_keys;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

pub const FEATURE_DEFINITIONS_PATH: &str =
    "platform/minecraft/core-liquid-template/feature-definitions.json";

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CoreFeatureDefinition {
    pub supported_targets: Vec<String>,
    pub requires: Vec<String>,
}

#[derive(Debug, Facet)]
#[facet(transparent)]
pub struct CoreFeatureDefinitions(pub BTreeMap<String, CoreFeatureDefinition>);

impl CoreFeatureDefinitions {
    /// Parse and validate the core registry without reading any source files.
    ///
    /// # Errors
    /// Rejects duplicate JSON keys, malformed entries, unknown targets or
    /// prerequisites, incompatible dependency support and cyclic dependencies.
    pub fn from_json(input: &str) -> Result<Self> {
        reject_duplicate_catalog_keys(input).wrap_err("duplicate core feature-definition key")?;
        let definitions: Self =
            facet_json::from_str(input).wrap_err("could not parse core feature definitions")?;
        definitions.validate()?;
        Ok(definitions)
    }

    #[must_use]
    pub fn registered_names(&self) -> BTreeSet<String> {
        self.0.keys().cloned().collect()
    }

    /// Check functional feature names, support and the prerequisite graph.
    ///
    /// # Errors
    /// Rejects invalid IDs, repeated or unsupported targets, missing/repeated
    /// prerequisites, unsupported dependency targets and dependency cycles.
    pub fn validate(&self) -> Result<()> {
        let supported = SUPPORTED_TARGETS
            .iter()
            .map(|(id, _)| *id)
            .collect::<BTreeSet<_>>();
        for (id, definition) in &self.0 {
            ensure!(
                id.as_bytes().first().is_some_and(u8::is_ascii_lowercase)
                    && id.bytes().all(|byte| byte.is_ascii_lowercase()
                        || byte.is_ascii_digit()
                        || byte == b'_'),
                "feature `{id}` must use lowercase snake_case and start with a letter"
            );
            ensure!(
                !definition.supported_targets.is_empty(),
                "feature `{id}` supports no targets"
            );
            let mut declared_targets = BTreeSet::new();
            for target in &definition.supported_targets {
                ensure!(
                    supported.contains(target.as_str()),
                    "feature `{id}` declares unsupported target `{target}`"
                );
                ensure!(
                    declared_targets.insert(target),
                    "feature `{id}` repeats target `{target}`"
                );
            }
            let mut prerequisites = BTreeSet::new();
            for required in &definition.requires {
                let dependency = self.0.get(required).ok_or_else(|| {
                    eyre::eyre!("feature `{id}` requires unknown feature `{required}`")
                })?;
                ensure!(
                    prerequisites.insert(required),
                    "feature `{id}` repeats prerequisite `{required}`"
                );
                for target in &definition.supported_targets {
                    ensure!(
                        dependency.supported_targets.contains(target),
                        "feature `{id}` requires `{required}` which does not support target `{target}`"
                    );
                }
            }
        }
        self.validate_acyclic()
    }

    fn validate_acyclic(&self) -> Result<()> {
        let mut counts = BTreeMap::new();
        let mut dependants: BTreeMap<&str, Vec<&str>> = BTreeMap::new();
        let mut ready = BTreeSet::new();
        for (id, definition) in &self.0 {
            counts.insert(id.as_str(), definition.requires.len());
            if definition.requires.is_empty() {
                ready.insert(id.as_str());
            }
            for required in &definition.requires {
                dependants.entry(required.as_str()).or_default().push(id);
            }
        }
        let mut visited = 0;
        while let Some(id) = ready.pop_first() {
            visited += 1;
            for dependant in dependants.get(id).into_iter().flatten() {
                let count = counts
                    .get_mut(dependant)
                    .expect("validated prerequisite graph contains every feature");
                *count -= 1;
                if *count == 0 {
                    ready.insert(*dependant);
                }
            }
        }
        ensure!(
            visited == self.0.len(),
            "core feature prerequisites contain a cycle"
        );
        Ok(())
    }

    /// Validate one catalog entry's exact explicitly enabled feature set.
    ///
    /// # Errors
    /// Rejects an unknown/unsupported enabled feature or a missing prerequisite.
    pub fn validate_entry(&self, entry: &ProjectionEntry) -> Result<()> {
        let target = entry.target_id()?;
        let enabled = entry
            .features
            .iter()
            .map(String::as_str)
            .collect::<BTreeSet<_>>();
        for id in &entry.features {
            let definition = self
                .0
                .get(id)
                .ok_or_else(|| eyre::eyre!("unknown enabled feature `{id}`"))?;
            ensure!(
                definition
                    .supported_targets
                    .iter()
                    .any(|supported| supported == target),
                "enabled feature `{id}` does not support target `{target}`"
            );
            for required in &definition.requires {
                ensure!(
                    enabled.contains(required.as_str()),
                    "enabled feature `{id}` requires enabled feature `{required}`"
                );
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::projection_catalog::ProjectionEnvironment;

    #[test]
    fn explicit_features_need_support_and_dependencies_even_in_dev() {
        let registry = CoreFeatureDefinitions::from_json(
            r#"{"packet":{"supported_targets":["1.19.2"],"requires":[]},"display":{"supported_targets":["1.19.2"],"requires":["packet"]}}"#,
        )
        .unwrap();
        let mut entry = ProjectionEntry {
            minecraft_version: "1.19.2".to_owned(),
            environment: ProjectionEnvironment::Dev,
            features: vec![],
        };
        registry.validate_entry(&entry).unwrap();
        entry.features = vec!["display".to_owned()];
        assert!(registry.validate_entry(&entry).is_err());
        entry.features.push("packet".to_owned());
        registry.validate_entry(&entry).unwrap();
        entry.minecraft_version = "1.19.4".to_owned();
        assert!(registry.validate_entry(&entry).is_err());
        assert_eq!(registry.registered_names().len(), 2);
    }

    #[test]
    fn registry_cannot_silently_overwrite_duplicate_or_escaped_keys() {
        for input in [
            r#"{"a":{"supported_targets":["1.19.2"],"requires":[]},"a":{"supported_targets":["1.19.4"],"requires":[]}}"#,
            r#"{"a":{"supported_targets":["1.19.2"],"requires":[]},"\u0061":{"supported_targets":["1.19.4"],"requires":[]}}"#,
            r#"{"a":{"supported_targets":["1.19.2"],"requires":[],"environment":"dev"}}"#,
        ] {
            assert!(CoreFeatureDefinitions::from_json(input).is_err(), "{input}");
        }
    }
}
