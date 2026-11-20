use super::projection_definition::ProjectionDefinition;
use super::projection_name::ProjectionName;
use super::projection_pattern::ProjectionPattern;
use super::projections_document::ProjectionsDocument;
use super::sfm_projection_feature_registry::SfmProjectionFeatureRegistry;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::iter::once;
use std::path::Path;
use std::path::PathBuf;

pub const CATALOG_PATH: &str = "platform/minecraft/projections.json";
pub const PROJECTIONS_DIR: &str = "platform/minecraft/projections";

/// Template parameters for each named projection in `projections.json`.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(transparent)]
pub struct ProjectionDefinitions(pub BTreeMap<String, ProjectionDefinition>);

impl ProjectionDefinitions {
    pub(super) fn key_for_project(&self, repo: &Path, project_root: &Path) -> Result<&str> {
        for key in self.0.keys() {
            if repo.join(self.project_dir(key)?) == project_root {
                return Ok(key);
            }
        }
        eyre::bail!("project is not an exact root in the containing projections catalog")
    }

    /// Match a native build or run glob against the configured projection names.
    pub(crate) fn matching_glob_names(&self, selector: &str) -> Result<Vec<String>> {
        let pattern = ProjectionPattern::parse(selector)?;
        Ok(self
            .matching_pattern_names(&pattern)?
            .into_iter()
            .map(str::to_owned)
            .collect())
    }

    pub(crate) fn matching_glob_projections(&self, selector: &str) -> Result<Vec<ProjectionName>> {
        let pattern = ProjectionPattern::parse(selector)?;
        self.matching_pattern_names(&pattern)?
            .into_iter()
            .map(ProjectionName::parse)
            .collect()
    }

    pub(crate) fn matching_names(&self, name_or_pattern: &str) -> Result<Vec<ProjectionName>> {
        // A projection's literal name may include brackets.
        if self.0.contains_key(name_or_pattern) {
            return Ok(vec![ProjectionName::parse(name_or_pattern)?]);
        }
        let pattern = ProjectionPattern::parse(name_or_pattern)
            .wrap_err_with(|| format!("invalid projection pattern {name_or_pattern:?}"))?;
        self.matching_pattern_names(&pattern)?
            .into_iter()
            .map(ProjectionName::parse)
            .collect()
    }

    fn matching_pattern_names(&self, pattern: &ProjectionPattern) -> Result<Vec<&str>> {
        pattern.matching_names(self.0.keys().map(String::as_str))
    }

    /// Parse the public JSON shape and validate its paths and feature names.
    ///
    /// # Errors
    ///
    /// Rejects malformed/duplicate JSON keys, unknown entry fields, unsupported
    /// versions or features, nonportable keys, and colliding output roots.
    pub fn from_json(input: &str, registry: &SfmProjectionFeatureRegistry) -> eyre::Result<Self> {
        let ProjectionsDocument(catalog) = ProjectionsDocument::from_json(input)?;
        catalog.validate(registry)?;
        Ok(catalog)
    }

    /// Serialize a valid catalog as the public keyed object, with a newline.
    ///
    /// # Errors
    ///
    /// Returns an error when validation or Facet JSON serialization fails.
    pub fn to_json(&self, registry: &SfmProjectionFeatureRegistry) -> eyre::Result<String> {
        self.validate(registry)?;
        let mut json = facet_json::to_string_pretty(self)
            .wrap_err("could not serialize projections catalog")?;
        json.push('\n');
        Ok(json)
    }

    /// Validate the contract independently of filesystem state and selection
    /// metadata. Callers additionally check each feature's target support and
    /// prerequisites before rendering.
    ///
    /// # Errors
    ///
    /// Rejects empty catalogs, unknown features, unsupported versions, unsafe
    /// paths, case aliases (including shared parent components), or overlapping
    /// ancestor/descendant output roots.
    pub fn validate(&self, registry: &SfmProjectionFeatureRegistry) -> eyre::Result<()> {
        self.validate_entries(|feature| registry.contains(feature))
    }

    pub(super) fn validate_layout(&self) -> eyre::Result<()> {
        self.validate_entries(|_| true)
    }

    fn validate_entries(&self, is_registered: impl Fn(&str) -> bool) -> eyre::Result<()> {
        ensure!(
            !self.0.is_empty(),
            "projections catalog must contain at least one entry"
        );
        let mut roots = BTreeMap::new();
        let mut prefixes = BTreeMap::new();
        for (key, entry) in &self.0 {
            ProjectionName::validate(key)?;
            entry
                .target_id()
                .wrap_err_with(|| format!("projection `{key}`"))?;
            entry
                .validated_features(&is_registered)
                .wrap_err_with(|| format!("projection `{key}`"))?;
            let folded = key.to_ascii_lowercase();
            if let Some(other) = roots.insert(folded, key.as_str()) {
                eyre::bail!("projection keys `{other}` and `{key}` collide by case");
            }
            for prefix in key_prefixes(key) {
                let folded = prefix.to_ascii_lowercase();
                if let Some(other) = prefixes.insert(folded, prefix) {
                    ensure!(
                        other == prefix,
                        "projection path components `{other}` and `{prefix}` collide by case"
                    );
                }
            }
        }
        for key in self.0.keys() {
            for ancestor in key_prefixes(key)
                .into_iter()
                .filter(|prefix| *prefix != key)
            {
                if let Some(other) = roots.get(&ancestor.to_ascii_lowercase()) {
                    eyre::bail!(
                        "projection output roots `{other}` and `{key}` overlap as ancestor and descendant"
                    );
                }
            }
        }
        Ok(())
    }

    /// Resolve an exact catalog identity; no aliases or case folding apply.
    ///
    /// # Errors
    ///
    /// Returns an error if the key is unsafe or absent from the catalog.
    pub fn entry(&self, key: &str) -> eyre::Result<&ProjectionDefinition> {
        ProjectionName::validate(key)?;
        self.0
            .get(key)
            .ok_or_else(|| eyre::eyre!("unknown projection key `{key}`"))
    }

    /// Repository-relative project directory under the single projections root.
    /// This does not authorize writing to the path or prove symlink safety.
    ///
    /// # Errors
    ///
    /// Returns an error when the key is unsafe or absent from this catalog.
    pub fn project_dir(&self, key: &str) -> eyre::Result<PathBuf> {
        self.entry(key)?;
        let mut output = PathBuf::from(PROJECTIONS_DIR);
        for component in key.split('/') {
            output.push(component);
        }
        Ok(output)
    }

    /// Fingerprint the exact key and declared context, independent of JSON and
    /// feature declaration order. Generation additionally binds authored inputs,
    /// registry semantics, and build metadata in its provenance/cache identity.
    ///
    /// # Errors
    ///
    /// Returns an error when the key/version is invalid or features repeat.
    pub fn context_identity(&self, key: &str) -> eyre::Result<String> {
        let entry = self.entry(key)?;
        entry.target_id()?;
        let features = entry.unique_features()?;
        let mut hash = blake3::Hasher::new();
        hash.update(b"sfm:projection_context@1\0");
        hash_field(&mut hash, key);
        hash_field(&mut hash, &entry.minecraft_version);
        hash_field(&mut hash, entry.environment.as_str());
        for feature in features {
            hash_field(&mut hash, feature);
        }
        Ok(format!("blake3:{}", hash.finalize().to_hex()))
    }
}

fn key_prefixes(key: &str) -> Vec<&str> {
    key.match_indices('/')
        .map(|(index, _)| &key[..index])
        .chain(once(key))
        .collect()
}

fn hash_field(hash: &mut blake3::Hasher, value: &str) {
    hash.update(&(value.len() as u64).to_le_bytes());
    hash.update(value.as_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::projection_definition::SUPPORTED_TARGETS;
    use crate::source_projection::projection_environment::ProjectionEnvironment;
    use crate::source_projection::projections_document::reject_duplicate_definition_keys;

    fn registry() -> SfmProjectionFeatureRegistry {
        SfmProjectionFeatureRegistry::test_registry(["alpha", "beta"])
    }

    fn entry() -> ProjectionDefinition {
        ProjectionDefinition {
            minecraft_version: "1.19.2".to_owned(),
            environment: ProjectionEnvironment::Release,
            features: vec![],
        }
    }

    fn catalog(keys: &[&str]) -> ProjectionDefinitions {
        ProjectionDefinitions(
            keys.iter()
                .map(|key| ((*key).to_owned(), entry()))
                .collect(),
        )
    }

    #[test]
    fn public_object_round_trip_resolves_all_twenty_contexts() {
        let mut entries = BTreeMap::new();
        for (target, minecraft_version) in SUPPORTED_TARGETS {
            for (prefix, environment) in [
                ("sfm-4.34.0", ProjectionEnvironment::Release),
                ("sfm-dev", ProjectionEnvironment::Dev),
            ] {
                entries.insert(
                    format!("{prefix}/mc-{target}"),
                    ProjectionDefinition {
                        minecraft_version: minecraft_version.to_owned(),
                        environment,
                        features: vec![],
                    },
                );
            }
        }
        let original = ProjectionDefinitions(entries);
        let json = original.to_json(&registry()).unwrap();
        assert!(json.contains("\"sfm-dev/mc-26.1.2\""));
        assert!(json.contains("\"environment\": \"dev\""));
        assert!(!json.contains("\"entries\""));
        let parsed = ProjectionDefinitions::from_json(&json, &registry()).unwrap();
        assert_eq!(parsed, original);
        assert_eq!(parsed.0.len(), 20);
        assert_eq!(
            parsed
                .entry("sfm-dev/mc-1.21.0")
                .unwrap()
                .target_id()
                .unwrap(),
            "1.21.0"
        );
    }

    #[test]
    fn arbitrary_safe_nested_keys_use_values_not_filename_inference() {
        let original = catalog(&["experiments/touch-panel/with alpha"]);
        original.validate(&registry()).unwrap();
        assert_eq!(
            original
                .project_dir("experiments/touch-panel/with alpha")
                .unwrap(),
            PathBuf::from(PROJECTIONS_DIR)
                .join("experiments")
                .join("touch-panel")
                .join("with alpha")
        );
        assert_eq!(
            original
                .entry("experiments/touch-panel/with alpha")
                .unwrap()
                .target_id()
                .unwrap(),
            "1.19.2"
        );
        assert!(
            original
                .entry("Experiments/touch-panel/with alpha")
                .is_err()
        );
        assert!(original.project_dir("missing").is_err());
    }

    #[test]
    fn flags_are_explicit_and_environment_never_enables_missing_features() {
        let mut configured = entry();
        configured.environment = ProjectionEnvironment::Dev;
        configured.features = vec!["beta".to_owned()];
        assert_eq!(
            configured.feature_flags(&registry()).unwrap(),
            BTreeMap::from([("alpha".to_owned(), false), ("beta".to_owned(), true)])
        );
    }

    #[test]
    fn rejects_unknown_and_duplicate_enabled_features() {
        let mut configured = catalog(&["valid"]);
        configured.0.get_mut("valid").unwrap().features = vec!["typo".to_owned()];
        assert!(
            configured
                .validate(&registry())
                .unwrap_err()
                .to_string()
                .contains("projection `valid`")
        );
        configured.0.get_mut("valid").unwrap().features =
            vec!["alpha".to_owned(), "alpha".to_owned()];
        assert!(configured.validate(&registry()).is_err());
        assert!(configured.context_identity("valid").is_err());
    }

    #[test]
    fn rejects_unsupported_versions_and_matrix_id_as_actual_version() {
        let mut configured = entry();
        for invalid in ["1.21.0", "6.1.2", "1.22", " 1.19.2", "1.19.2 "] {
            configured.minecraft_version = invalid.to_owned();
            assert!(configured.target_id().is_err(), "accepted {invalid}");
        }
        configured.minecraft_version = "1.21".to_owned();
        assert_eq!(configured.target_id().unwrap(), "1.21.0");
        configured.minecraft_version = "26.1.2".to_owned();
        assert_eq!(configured.target_id().unwrap(), "26.1.2");
    }

    #[test]
    fn rejects_unknown_fields_missing_features_and_invalid_environment() {
        let valid =
            r#"{"example":{"minecraft_version":"1.19.2","environment":"release","features":[]}}"#;
        ProjectionDefinitions::from_json(valid, &registry()).unwrap();
        for invalid in [
            valid.replace(
                "\"features\":[]",
                "\"features\":[],\"preset\":\"hidden-snapshot\"",
            ),
            valid.replace(",\"features\":[]", ""),
            valid.replace("\"release\"", "\"development\""),
            valid.replace("\"features\":[]", "\"features\":true"),
            valid.replace("\"features\":[]", "\"features\":[1]"),
        ] {
            assert!(
                ProjectionDefinitions::from_json(&invalid, &registry()).is_err(),
                "accepted {invalid}"
            );
        }
        assert!(ProjectionDefinitions::from_json("{}", &registry()).is_err());
        assert!(ProjectionDefinitions::from_json("[]", &registry()).is_err());
    }

    #[test]
    fn rejects_duplicate_json_keys_even_when_escaped_or_nonadjacent() {
        let value = r#"{"minecraft_version":"1.19.2","environment":"release","features":[]}"#;
        for duplicate in [
            format!(r#"{{"same":{value},"same":{value}}}"#),
            format!(r#"{{"same":{value},"other":{value},"\u0073ame":{value}}}"#),
        ] {
            assert!(
                ProjectionDefinitions::from_json(&duplicate, &registry())
                    .unwrap_err()
                    .to_string()
                    .contains("duplicate projection key")
            );
        }
    }

    #[test]
    fn duplicate_guard_respects_nested_arrays_and_escaped_string_contents() {
        let valid = r#"{"first":{"minecraft_version":"1.19.2","environment":"release","features":["alpha"]},"second":{"minecraft_version":"1.19.2","environment":"release","features":["alpha"]}}"#;
        ProjectionDefinitions::from_json(valid, &registry()).unwrap();
        reject_duplicate_definition_keys(
            r#"{"one":{"nested":[{"one":"\"one\":{[}]"}]},"two":{"one":1}}"#,
        )
        .unwrap();
    }

    #[test]
    fn rejects_duplicate_entry_fields_in_typed_parser() {
        let duplicate = r#"{"example":{"minecraft_version":"1.19.2","environment":"release","environment":"dev","features":[]}}"#;
        assert!(ProjectionDefinitions::from_json(duplicate, &registry()).is_err());
    }

    #[test]
    fn rejects_full_key_and_shared_parent_case_collisions() {
        for keys in [["one/child", "ONE/CHILD"], ["one/a", "ONE/b"]] {
            assert!(
                catalog(&keys).validate(&registry()).is_err(),
                "accepted {keys:?}"
            );
        }
        catalog(&["one/a", "one/b"]).validate(&registry()).unwrap();
    }

    #[test]
    fn rejects_ancestor_roots_but_accepts_siblings_and_similar_prefixes() {
        for keys in [["one", "one/two"], ["one/two", "one/two/three"]] {
            assert!(
                catalog(&keys).validate(&registry()).is_err(),
                "accepted {keys:?}"
            );
        }
        catalog(&["one/two", "one/two-extra", "one/three"])
            .validate(&registry())
            .unwrap();
    }

    #[test]
    fn context_identity_is_order_independent_and_binds_every_declared_value() {
        let mut original = catalog(&["named/project"]);
        original.0.get_mut("named/project").unwrap().features =
            vec!["alpha".to_owned(), "beta".to_owned()];
        let expected = original.context_identity("named/project").unwrap();
        original
            .0
            .get_mut("named/project")
            .unwrap()
            .features
            .reverse();
        assert_eq!(
            original.context_identity("named/project").unwrap(),
            expected
        );
        for mutation in ["key", "version", "environment", "features"] {
            let mut changed = original.clone();
            let mut key = "named/project";
            match mutation {
                "key" => {
                    let entry = changed.0.remove(key).unwrap();
                    key = "other/project";
                    changed.0.insert(key.to_owned(), entry);
                }
                "version" => {
                    changed.0.get_mut(key).unwrap().minecraft_version = "26.1.2".to_owned()
                }
                "environment" => {
                    changed.0.get_mut(key).unwrap().environment = ProjectionEnvironment::Dev
                }
                "features" => {
                    changed.0.get_mut(key).unwrap().features.pop();
                }
                _ => unreachable!(),
            }
            assert_ne!(
                changed.context_identity(key).unwrap(),
                expected,
                "did not bind {mutation}"
            );
        }
    }

    #[test]
    fn native_globs_and_exact_checks_keep_their_distinct_bracket_meanings() {
        let definitions = ProjectionDefinitions(
            ["sfm-dev/mc-1.19.2", "sfm-dev/mc-1.19.[2]"]
                .into_iter()
                .map(|name| {
                    (
                        name.to_owned(),
                        ProjectionDefinition {
                            minecraft_version: "1.19.2".to_owned(),
                            environment: ProjectionEnvironment::Dev,
                            features: Vec::new(),
                        },
                    )
                })
                .collect(),
        );

        let bracket_name = "sfm-dev/mc-1.19.[2]";
        assert_eq!(
            definitions.matching_glob_names(bracket_name).unwrap(),
            ["sfm-dev/mc-1.19.2"]
        );
        assert_eq!(
            definitions.matching_glob_projections(bracket_name).unwrap()[0].as_str(),
            "sfm-dev/mc-1.19.2"
        );
        assert_eq!(
            definitions.matching_names(bracket_name).unwrap()[0].as_str(),
            bracket_name
        );
    }
}
