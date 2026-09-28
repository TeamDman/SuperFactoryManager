//! Reproducible provenance for a generated Minecraft source root.

use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;

/// The manifest travels with one target/preset output, so two feature sets
/// cannot accidentally claim the same generated files as their own.
#[derive(Debug, Facet)]
pub struct ProjectionProvenance {
    pub schema: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub preset_id: String,
    /// BLAKE3 fingerprint of the published preset definition, not just its ID.
    pub preset_definition_identity: String,
    pub tool_version: String,
    pub files: BTreeMap<String, ProjectedFileProvenance>,
}

#[derive(Debug, Facet)]
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
            schema: "sfm:source_projection_manifest@1".to_owned(),
            target_id: target_id.to_owned(),
            minecraft_version: minecraft_version.to_owned(),
            preset_id: preset_id.to_owned(),
            preset_definition_identity: preset_definition_identity.to_owned(),
            tool_version: env!("CARGO_PKG_VERSION").to_owned(),
            files: BTreeMap::new(),
        }
    }

    /// Serialize stable key order without embedding a timestamp or machine path.
    ///
    /// # Errors
    ///
    /// Returns an error if the typed manifest cannot be serialized.
    pub fn to_json(&self) -> eyre::Result<String> {
        let mut json = facet_json::to_string_pretty(self)?;
        json.push('\n');
        Ok(json)
    }

    /// # Errors
    ///
    /// Returns an error for malformed or unsupported provenance schemas.
    pub fn from_json(json: &str) -> eyre::Result<Self> {
        let value: Self = facet_json::from_str(json)?;
        eyre::ensure!(
            value.schema == "sfm:source_projection_manifest@1",
            "unsupported source projection manifest schema '{}'",
            value.schema
        );
        Ok(value)
    }
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
}
