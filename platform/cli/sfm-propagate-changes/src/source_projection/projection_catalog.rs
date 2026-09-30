//! The public, key-addressed catalog of Minecraft source projections.
//!
//! A catalog entry supplies its own context. Its path is an output identity,
//! never an implicit selector of release snapshots, source trees, or features.
//! Filesystem ownership, symlink checks, and feature support/dependencies are
//! checked by the generation boundary; this module validates the pure contract.

use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::PathBuf;

pub const CATALOG_PATH: &str = "platform/minecraft/projections.json";
pub const PROJECTIONS_DIR: &str = "platform/minecraft/projections";

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

/// JSON is the user's object keyed by nested projection paths, without an
/// additional schema or entries wrapper.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(transparent)]
pub struct ProjectionCatalog(pub BTreeMap<String, ProjectionEntry>);

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ProjectionEntry {
    pub minecraft_version: String,
    pub environment: ProjectionEnvironment,
    /// Exactly the enabled features. Known features omitted here are disabled.
    pub features: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum ProjectionEnvironment {
    Release,
    Dev,
}

impl ProjectionEnvironment {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Release => "release",
            Self::Dev => "dev",
        }
    }
}

impl ProjectionCatalog {
    /// Parse the public JSON shape and validate its paths and feature names.
    ///
    /// # Errors
    ///
    /// Rejects malformed/duplicate JSON keys, unknown entry fields, unsupported
    /// versions or features, nonportable keys, and colliding output roots.
    pub fn from_json(input: &str, registered_features: &BTreeSet<String>) -> eyre::Result<Self> {
        reject_duplicate_catalog_keys(input)?;
        let catalog: Self =
            facet_json::from_str(input).wrap_err("could not parse projections catalog")?;
        catalog.validate(registered_features)?;
        Ok(catalog)
    }

    /// Serialize a valid catalog as the public keyed object, with a newline.
    ///
    /// # Errors
    ///
    /// Returns an error when validation or Facet JSON serialization fails.
    pub fn to_json(&self, registered_features: &BTreeSet<String>) -> eyre::Result<String> {
        self.validate(registered_features)?;
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
    pub fn validate(&self, registered_features: &BTreeSet<String>) -> eyre::Result<()> {
        ensure!(
            !self.0.is_empty(),
            "projections catalog must contain at least one entry"
        );
        let mut roots = BTreeMap::new();
        let mut prefixes = BTreeMap::new();
        for (key, entry) in &self.0 {
            validate_projection_key(key)?;
            entry
                .target_id()
                .wrap_err_with(|| format!("projection `{key}`"))?;
            entry
                .feature_flags(registered_features)
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
    pub fn entry(&self, key: &str) -> eyre::Result<&ProjectionEntry> {
        validate_projection_key(key)?;
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

impl ProjectionEntry {
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
        registered_features: &BTreeSet<String>,
    ) -> eyre::Result<BTreeMap<String, bool>> {
        let enabled = self.unique_features()?;
        for feature in &enabled {
            ensure!(
                registered_features.contains(*feature),
                "unknown enabled feature `{feature}`"
            );
        }
        Ok(registered_features
            .iter()
            .map(|feature| (feature.clone(), enabled.contains(feature.as_str())))
            .collect())
    }

    fn unique_features(&self) -> eyre::Result<BTreeSet<&str>> {
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

/// Accept only a portable relative path, irrespective of the host OS.
/// ASCII avoids locale-dependent case and Unicode-normalization aliases.
///
/// # Errors
///
/// Rejects absolute/device/traversal paths, invalid Windows filename characters,
/// backslashes, controls/non-ASCII, empty segments, and trailing dots/spaces.
pub fn validate_projection_key(key: &str) -> eyre::Result<()> {
    ensure!(
        !key.is_empty()
            && key.is_ascii()
            && !key
                .chars()
                .any(|character| character.is_control() || "\\<>:\"|?*".contains(character))
            && key.split('/').all(|component| {
                !component.is_empty()
                    && component != "."
                    && component != ".."
                    && component.len() <= 255
                    && !component.ends_with('.')
                    && !component.ends_with(' ')
                    && !is_windows_device_name(component)
            }),
        "projection key `{key}` must be a portable, exact, ASCII relative path without traversal, device names, backslashes or trailing dots/spaces"
    );
    Ok(())
}

fn is_windows_device_name(component: &str) -> bool {
    let stem = component
        .split('.')
        .next()
        .unwrap_or_default()
        .trim_end_matches(' ');
    let upper = stem.to_ascii_uppercase();
    matches!(
        upper.as_str(),
        "CON" | "PRN" | "AUX" | "NUL" | "CONIN$" | "CONOUT$" | "CLOCK$"
    ) || (upper.len() == 4
        && (upper.starts_with("COM") || upper.starts_with("LPT"))
        && matches!(upper.as_bytes()[3], b'1'..=b'9'))
}

fn key_prefixes(key: &str) -> Vec<&str> {
    key.match_indices('/')
        .map(|(index, _)| &key[..index])
        .chain(std::iter::once(key))
        .collect()
}

fn hash_field(hash: &mut blake3::Hasher, value: &str) {
    hash.update(&(value.len() as u64).to_le_bytes());
    hash.update(value.as_bytes());
}

/// Facet's map parser can replace a repeated map key. Guard the public root map
/// before that conversion. Facet still owns string decoding and full JSON/type
/// validation; this lexical pass only identifies strings followed by a colon
/// at root-object depth, respecting escaped quotes and nested containers.
pub(crate) fn reject_duplicate_catalog_keys(input: &str) -> eyre::Result<()> {
    let bytes = input.as_bytes();
    let mut position = 0;
    let mut depth = 0_usize;
    let mut keys = BTreeSet::new();
    while position < bytes.len() {
        match bytes[position] {
            b'{' | b'[' => {
                depth = depth.saturating_add(1);
                position += 1;
            }
            b'}' | b']' => {
                depth = depth.saturating_sub(1);
                position += 1;
            }
            b'"' => {
                let start = position;
                position += 1;
                let mut closed = false;
                while position < bytes.len() {
                    match bytes[position] {
                        b'\\' => position = (position + 2).min(bytes.len()),
                        b'"' => {
                            position += 1;
                            closed = true;
                            break;
                        }
                        _ => position += 1,
                    }
                }
                let mut next = position;
                while next < bytes.len() && bytes[next].is_ascii_whitespace() {
                    next += 1;
                }
                if closed && depth == 1 && bytes.get(next) == Some(&b':') {
                    let key: String = facet_json::from_str(&input[start..position])
                        .wrap_err("invalid projections catalog key")?;
                    ensure!(
                        keys.insert(key.clone()),
                        "duplicate projection key `{key}` in JSON"
                    );
                }
            }
            _ => position += 1,
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn registry() -> BTreeSet<String> {
        BTreeSet::from(["alpha".to_owned(), "beta".to_owned()])
    }

    fn entry() -> ProjectionEntry {
        ProjectionEntry {
            minecraft_version: "1.19.2".to_owned(),
            environment: ProjectionEnvironment::Release,
            features: vec![],
        }
    }

    fn catalog(keys: &[&str]) -> ProjectionCatalog {
        ProjectionCatalog(
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
                    ProjectionEntry {
                        minecraft_version: minecraft_version.to_owned(),
                        environment,
                        features: vec![],
                    },
                );
            }
        }
        let original = ProjectionCatalog(entries);
        let json = original.to_json(&registry()).unwrap();
        assert!(json.contains("\"sfm-dev/mc-26.1.2\""));
        assert!(json.contains("\"environment\": \"dev\""));
        assert!(!json.contains("\"entries\""));
        let parsed = ProjectionCatalog::from_json(&json, &registry()).unwrap();
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
        ProjectionCatalog::from_json(valid, &registry()).unwrap();
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
                ProjectionCatalog::from_json(&invalid, &registry()).is_err(),
                "accepted {invalid}"
            );
        }
        assert!(ProjectionCatalog::from_json("{}", &registry()).is_err());
        assert!(ProjectionCatalog::from_json("[]", &registry()).is_err());
    }

    #[test]
    fn rejects_duplicate_json_keys_even_when_escaped_or_nonadjacent() {
        let value = r#"{"minecraft_version":"1.19.2","environment":"release","features":[]}"#;
        for duplicate in [
            format!(r#"{{"same":{value},"same":{value}}}"#),
            format!(r#"{{"same":{value},"other":{value},"\u0073ame":{value}}}"#),
        ] {
            assert!(
                ProjectionCatalog::from_json(&duplicate, &registry())
                    .unwrap_err()
                    .to_string()
                    .contains("duplicate projection key")
            );
        }
    }

    #[test]
    fn duplicate_guard_respects_nested_arrays_and_escaped_string_contents() {
        let valid = r#"{"first":{"minecraft_version":"1.19.2","environment":"release","features":["alpha"]},"second":{"minecraft_version":"1.19.2","environment":"release","features":["alpha"]}}"#;
        ProjectionCatalog::from_json(valid, &registry()).unwrap();
        reject_duplicate_catalog_keys(
            r#"{"one":{"nested":[{"one":"\"one\":{[}]"}]},"two":{"one":1}}"#,
        )
        .unwrap();
    }

    #[test]
    fn rejects_duplicate_entry_fields_in_typed_parser() {
        let duplicate = r#"{"example":{"minecraft_version":"1.19.2","environment":"release","environment":"dev","features":[]}}"#;
        assert!(ProjectionCatalog::from_json(duplicate, &registry()).is_err());
    }

    #[test]
    fn path_validation_is_host_independent() {
        for invalid in [
            "",
            "/absolute",
            "//server/share",
            "C:/drive",
            "C:drive",
            "../escape",
            "a/../b",
            "a/./b",
            "a//b",
            "a/",
            "a\\b",
            "\\\\server\\share",
            "a\0b",
            "a\nb",
            "a\tb",
            "a/ends.",
            "a/ends ",
            "a/<b",
            "a/quo\"te",
            "a/pipe|",
            "a/quest?",
            "a/star*",
            "a/col:on",
            "a/caf\u{e9}",
        ] {
            assert!(
                validate_projection_key(invalid).is_err(),
                "accepted {invalid:?}"
            );
        }
        for valid in [
            "a",
            "sfm-4.34.0/mc-1.19.2",
            "custom/one more/alpha_beta",
            ".local/example",
            "one/two/three",
        ] {
            validate_projection_key(valid).unwrap();
        }
        assert!(validate_projection_key(&"a".repeat(256)).is_err());
    }

    #[test]
    fn rejects_windows_device_names_with_extensions_and_case_variants() {
        for device in [
            "CON",
            "con.txt",
            "PRN",
            "Aux.java",
            "nul",
            "COM1",
            "com9.gradle",
            "LPT1",
            "lpt9",
            "CON .txt",
            "CONIN$",
            "conout$",
            "CLOCK$",
        ] {
            assert!(
                validate_projection_key(&format!("safe/{device}/project")).is_err(),
                "accepted {device}"
            );
        }
        for ordinary in ["COM0", "COM10", "LPT0", "console", "auxiliary"] {
            validate_projection_key(&format!("safe/{ordinary}/project")).unwrap();
        }
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
}
