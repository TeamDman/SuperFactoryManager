//! Reproducible provenance for a generated Minecraft source root.

use super::projection_catalog::ProjectionEnvironment;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::validate_projection_key;
use eyre::ensure;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

pub const LEGACY_MANIFEST_SCHEMA: &str = "sfm:source_projection_manifest@1";
pub const CATALOG_MANIFEST_SCHEMA: &str = "sfm:source_projection_manifest@2";
pub const MAX_MANIFEST_BYTES: usize = 16 * 1024 * 1024;

/// The manifest travels with one target/preset output, so two feature sets
/// cannot accidentally claim the same generated files as their own.
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ProjectionProvenance {
    pub schema: String,
    pub target_id: String,
    pub minecraft_version: String,
    #[facet(default, skip_serializing_if = String::is_empty)]
    pub preset_id: String,
    /// BLAKE3 fingerprint of the published preset definition, not just its ID.
    #[facet(default, skip_serializing_if = String::is_empty)]
    pub preset_definition_identity: String,
    /// Named projections have their own explicit owner, never a flattened
    /// alias pretending to be a legacy preset. Absent in exact legacy bytes.
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub catalog: Option<CatalogProjectionOwner>,
    pub tool_version: String,
    pub files: BTreeMap<String, ProjectedFileProvenance>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CatalogProjectionOwner {
    pub projection_key: String,
    pub environment: ProjectionEnvironment,
    pub context_identity: String,
}

impl CatalogProjectionOwner {
    /// Validate a named owner and the exact stable-target/upstream-MC pair.
    ///
    /// # Errors
    /// Rejects unsafe keys, unsupported pairs and noncanonical fingerprints.
    pub fn validate(&self, target_id: &str, minecraft_version: &str) -> eyre::Result<()> {
        validate_projection_key(&self.projection_key)?;
        ensure!(
            SUPPORTED_TARGETS
                .iter()
                .any(|(id, version)| *id == target_id && *version == minecraft_version),
            "catalog provenance has an unsupported target/Minecraft pair"
        );
        let digest = self.context_identity.strip_prefix("blake3:").unwrap_or("");
        ensure!(
            digest.len() == 64
                && digest
                    .bytes()
                    .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
            "catalog provenance has an invalid context fingerprint"
        );
        Ok(())
    }
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ProjectedFileProvenance {
    /// Logical path in the selected source snapshot. For a release-tag fallback,
    /// `src/...` belongs to the verified pinned tag, not necessarily the current
    /// primary worktree.
    pub source_path: String,
    pub source_sha256: String,
    /// Name of the selected overlay, if this file overrides a shared source.
    pub overlay: Option<String>,
    pub output_sha256: String,
}

impl ProjectionProvenance {
    #[must_use]
    pub fn new(
        target_id: &str,
        minecraft_version: &str,
        preset_id: &str,
        preset_definition_identity: &str,
    ) -> Self {
        Self {
            schema: LEGACY_MANIFEST_SCHEMA.to_owned(),
            target_id: target_id.to_owned(),
            minecraft_version: minecraft_version.to_owned(),
            preset_id: preset_id.to_owned(),
            preset_definition_identity: preset_definition_identity.to_owned(),
            catalog: None,
            tool_version: env!("CARGO_PKG_VERSION").to_owned(),
            files: BTreeMap::new(),
        }
    }

    #[must_use]
    pub fn new_catalog(
        target_id: &str,
        minecraft_version: &str,
        owner: CatalogProjectionOwner,
    ) -> Self {
        Self {
            schema: CATALOG_MANIFEST_SCHEMA.to_owned(),
            target_id: target_id.to_owned(),
            minecraft_version: minecraft_version.to_owned(),
            preset_id: String::new(),
            preset_definition_identity: String::new(),
            catalog: Some(owner),
            tool_version: env!("CARGO_PKG_VERSION").to_owned(),
            files: BTreeMap::new(),
        }
    }

    /// An old preset-only consumer must opt in before interpreting provenance.
    ///
    /// # Errors
    /// Refuses named ownership instead of inventing a flat preset alias.
    pub fn require_legacy_owner(&self) -> eyre::Result<()> {
        self.validate_owner()?;
        ensure!(
            self.schema == LEGACY_MANIFEST_SCHEMA && self.catalog.is_none(),
            "this legacy preset workflow does not accept catalog-owned projections"
        );
        Ok(())
    }

    fn validate_owner(&self) -> eyre::Result<()> {
        match self.schema.as_str() {
            LEGACY_MANIFEST_SCHEMA => {
                ensure!(
                    self.catalog.is_none(),
                    "legacy provenance must not contain catalog ownership"
                );
                ensure!(
                    !self.preset_id.is_empty() && !self.preset_definition_identity.is_empty(),
                    "legacy provenance requires its original preset identity"
                );
            }
            CATALOG_MANIFEST_SCHEMA => {
                ensure!(
                    self.preset_id.is_empty() && self.preset_definition_identity.is_empty(),
                    "catalog provenance must not contain legacy preset identities"
                );
                let owner = self.catalog.as_ref().ok_or_else(|| {
                    eyre::eyre!("catalog provenance requires a complete catalog owner")
                })?;
                owner.validate(&self.target_id, &self.minecraft_version)?;
            }
            _ => eyre::bail!(
                "unsupported source projection manifest schema '{}'",
                self.schema
            ),
        }
        Ok(())
    }

    /// Serialize stable key order without embedding a timestamp or machine path.
    ///
    /// # Errors
    ///
    /// Returns an error if the typed manifest cannot be serialized.
    pub fn to_json(&self) -> eyre::Result<String> {
        self.validate_owner()?;
        let mut json = facet_json::to_string_pretty(self)?;
        json.push('\n');
        ensure!(
            json.len() <= MAX_MANIFEST_BYTES,
            "source projection manifest exceeds the {MAX_MANIFEST_BYTES}-byte limit"
        );
        Ok(json)
    }

    /// # Errors
    ///
    /// Returns an error for malformed or unsupported provenance schemas.
    pub fn from_json(json: &str) -> eyre::Result<Self> {
        ensure!(
            json.len() <= MAX_MANIFEST_BYTES,
            "source projection manifest exceeds the {MAX_MANIFEST_BYTES}-byte limit"
        );
        let fields = reject_duplicate_manifest_keys(json)?;
        let value: Self = facet_json::from_str(json)?;
        value.validate_owner()?;
        if value.schema == CATALOG_MANIFEST_SCHEMA {
            ensure!(
                !fields.contains("preset_id") && !fields.contains("preset_definition_identity"),
                "catalog provenance must not encode legacy preset fields, even empty ones"
            );
        } else {
            ensure!(
                !fields.contains("catalog"),
                "legacy provenance must not encode catalog ownership, even null"
            );
        }
        Ok(value)
    }
}

// Facet map parsing must not silently replace duplicate owned output paths.
// Decode string keys first so escaped aliases are duplicates as well.
fn reject_duplicate_manifest_keys(input: &str) -> eyre::Result<BTreeSet<String>> {
    let bytes = input.as_bytes();
    let mut position = 0;
    let mut objects: Vec<Option<BTreeSet<String>>> = Vec::new();
    let mut root_fields = BTreeSet::new();
    while position < bytes.len() {
        match bytes[position] {
            b'{' => {
                objects.push(Some(BTreeSet::new()));
                position += 1;
            }
            b'[' => {
                objects.push(None);
                position += 1;
            }
            b'}' | b']' => {
                if let Some(Some(keys)) = objects.pop()
                    && objects.is_empty()
                {
                    root_fields = keys;
                }
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
                if closed
                    && bytes.get(next) == Some(&b':')
                    && let Some(Some(keys)) = objects.last_mut()
                {
                    let key: String = facet_json::from_str(&input[start..position])?;
                    ensure!(
                        keys.insert(key.clone()),
                        "duplicate source projection manifest key `{key}`"
                    );
                }
            }
            _ => position += 1,
        }
    }
    Ok(root_fields)
}

#[must_use]
pub fn sha256(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    format!("sha256:{digest:x}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn manifest_roundtrip_is_stable_and_machine_independent() {
        let mut manifest =
            ProjectionProvenance::new("mc_1_19_2", "1.19.2", "released-4.34.0", "blake3:example");
        manifest.files.insert(
            "src/main/java/Example.java".to_owned(),
            ProjectedFileProvenance {
                source_path: "src/main/java/Example.java".to_owned(),
                source_sha256: sha256(b"class Example {}\n"),
                overlay: None,
                output_sha256: sha256(b"// generated\nclass Example {}\n"),
            },
        );
        let first = manifest.to_json().unwrap();
        let second = ProjectionProvenance::from_json(&first)
            .unwrap()
            .to_json()
            .unwrap();
        assert_eq!(first, second);
        assert!(!first.contains("D:\\"));
    }

    fn named() -> ProjectionProvenance {
        ProjectionProvenance::new_catalog(
            "1.21.0",
            "1.21",
            CatalogProjectionOwner {
                projection_key: "any/nested/key".to_owned(),
                environment: ProjectionEnvironment::Dev,
                context_identity: format!("blake3:{}", "a".repeat(64)),
            },
        )
    }

    #[test]
    fn exact_legacy_wire_bytes_are_preserved() {
        let fixture = "{\n  \"schema\": \"sfm:source_projection_manifest@1\",\n  \"target_id\": \"1.19.2\",\n  \"minecraft_version\": \"1.19.2\",\n  \"preset_id\": \"released-4.34.0\",\n  \"preset_definition_identity\": \"blake3:example\",\n  \"tool_version\": \"fixture\",\n  \"files\": {}\n}\n";
        let parsed = ProjectionProvenance::from_json(fixture).unwrap();
        parsed.require_legacy_owner().unwrap();
        assert_eq!(parsed.to_json().unwrap(), fixture);
        let mut constructed =
            ProjectionProvenance::new("1.19.2", "1.19.2", "released-4.34.0", "blake3:example");
        constructed.tool_version = "fixture".to_owned();
        assert_eq!(constructed.to_json().unwrap(), fixture);
    }

    #[test]
    fn named_wire_roundtrip_preserves_explicit_owner_without_a_flat_alias() {
        let manifest = named();
        let wire = manifest.to_json().unwrap();
        assert!(wire.contains(CATALOG_MANIFEST_SCHEMA));
        assert!(wire.contains("any/nested/key"));
        assert!(!wire.contains("preset_id") && !wire.contains("preset_definition_identity"));
        let parsed = ProjectionProvenance::from_json(&wire).unwrap();
        assert_eq!(parsed.to_json().unwrap(), wire);
        assert_eq!(parsed.catalog.unwrap(), manifest.catalog.unwrap());
        assert!(named().require_legacy_owner().is_err());
    }

    #[test]
    fn schemas_cannot_mix_partial_or_contradictory_ownership() {
        for invalid in [
            "legacy_with_named",
            "named_with_legacy",
            "missing_owner",
            "unknown_schema",
            "wrong_mc",
            "unsafe_key",
            "fingerprint",
        ] {
            let mut manifest = named();
            match invalid {
                "legacy_with_named" => {
                    manifest.schema = LEGACY_MANIFEST_SCHEMA.to_owned();
                    manifest.preset_id = "flat".to_owned();
                    manifest.preset_definition_identity = "definition".to_owned();
                }
                "named_with_legacy" => manifest.preset_id = "flat".to_owned(),
                "missing_owner" => manifest.catalog = None,
                "unknown_schema" => manifest.schema = "unknown".to_owned(),
                "wrong_mc" => manifest.minecraft_version = "1.21.0".to_owned(),
                "unsafe_key" => {
                    manifest.catalog.as_mut().unwrap().projection_key = "../escape".to_owned()
                }
                "fingerprint" => {
                    manifest.catalog.as_mut().unwrap().context_identity = "blake3:bad".to_owned()
                }
                _ => unreachable!(),
            }
            assert!(manifest.to_json().is_err(), "accepted {invalid}");
        }
        let wire = named().to_json().unwrap();
        let partial = wire.replace("\"environment\": \"dev\",", "");
        assert!(ProjectionProvenance::from_json(&partial).is_err());
        let empty_legacy_field =
            wire.replace("\"files\": {}", "\"preset_id\": \"\", \"files\": {}");
        assert!(ProjectionProvenance::from_json(&empty_legacy_field).is_err());
    }

    #[test]
    fn duplicate_owner_and_escaped_output_keys_are_rejected() {
        let wire = named().to_json().unwrap();
        let duplicate_owner = wire.replace(
            "\"projection_key\": \"any/nested/key\",",
            "\"projection_key\": \"any/nested/key\", \"projection_key\": \"other\",",
        );
        assert!(ProjectionProvenance::from_json(&duplicate_owner).is_err());
        let file = "{\"source_path\":\"src/A.java\",\"source_sha256\":\"sha256:x\",\"overlay\":null,\"output_sha256\":\"sha256:y\"}";
        let duplicate_output = wire.replace(
            "\"files\": {}",
            &format!("\"files\": {{\"src/A.java\":{file},\"src/\\u0041.java\":{file}}}"),
        );
        assert!(ProjectionProvenance::from_json(&duplicate_output).is_err());
        assert!(ProjectionProvenance::from_json(&" ".repeat(MAX_MANIFEST_BYTES + 1)).is_err());
    }
}
