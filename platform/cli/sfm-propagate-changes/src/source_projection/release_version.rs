//! The one reviewed release-version rewrite used by both projection and
//! frozen-inventory authoring. It changes only a canonical Gradle property.

use super::sync::ProjectedArtifact;
use crate::gradle_properties_version_edit::GradlePropertiesVersionEdit;
use eyre::Result;
use std::collections::BTreeMap;

/// Replace exactly one canonical `mod_version=<value>` property.
///
/// # Errors
///
/// Rejects a missing, malformed, duplicate, or non-UTF-8 version property.
pub fn apply_release_mod_version(
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
    version: &str,
) -> Result<()> {
    let properties = artifacts
        .get_mut("gradle.properties")
        .ok_or_else(|| eyre::eyre!("release projection has no gradle.properties"))?;
    let content = GradlePropertiesVersionEdit::from_bytes(&properties.output_bytes)?;
    properties.output_bytes = content.with_mod_version(version)?.into_bytes();
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn artifact(bytes: &[u8]) -> BTreeMap<String, ProjectedArtifact> {
        BTreeMap::from([(
            "gradle.properties".to_owned(),
            ProjectedArtifact {
                related_references: None,
                source_path: "platform/minecraft/gradle.properties".to_owned(),
                source_bytes: bytes.to_vec(),
                output_bytes: bytes.to_vec(),
                overlay: None,
            },
        )])
    }

    #[test]
    fn rewrites_only_one_canonical_property_without_changing_source() {
        let mut artifacts =
            artifact(b"# mod_version=ignored\r\nmod_version=4.34.0\r\nother=ok\r\n");
        apply_release_mod_version(&mut artifacts, "9.99.99").unwrap();
        let properties = &artifacts["gradle.properties"];
        assert_eq!(
            properties.output_bytes,
            b"# mod_version=ignored\r\nmod_version=9.99.99\r\nother=ok\r\n"
        );
        assert_eq!(
            properties.source_bytes,
            b"# mod_version=ignored\r\nmod_version=4.34.0\r\nother=ok\r\n"
        );
    }

    #[test]
    fn rejected_rewrites_preserve_source_and_projected_bytes() {
        let original = b"mod_version=4.34.0\r\nother=ok\r\n";
        for version in [
            "",
            " 4.35.0",
            "4.35.0\nmod_id=other",
            "4.35.0\rmod_id=other",
        ] {
            let mut artifacts = artifact(original);
            assert!(apply_release_mod_version(&mut artifacts, version).is_err());
            assert_eq!(artifacts["gradle.properties"].output_bytes, original);
            assert_eq!(artifacts["gradle.properties"].source_bytes, original);
        }
        let bare_cr = b"other=hidden\rmod_version=4.34.0\n";
        let mut artifacts = artifact(bare_cr);
        assert!(apply_release_mod_version(&mut artifacts, "4.35.0").is_err());
        assert_eq!(artifacts["gradle.properties"].output_bytes, bare_cr);
    }

    #[test]
    fn missing_duplicate_and_noncanonical_properties_fail() {
        for bytes in [
            b"other=ok\n".as_slice(),
            b"mod_version=4.34.0\nmod_version=4.35.0\n".as_slice(),
            b"mod_version =4.34.0\n".as_slice(),
            b"mod_version= 4.34.0\n".as_slice(),
            b"mod_version=4.34.0\nmod_version:4.35.0\n".as_slice(),
            b"mod_version=4.34.0\nmod_version 4.35.0\n".as_slice(),
            b"mod_version=4.34.0\nmod\\_version=4.35.0\n".as_slice(),
            b"mod_version=4.34.0\nmod_\\u0076ersion=4.35.0\n".as_slice(),
            b"mod_version=4.34.0\nmod_\\\nversion=4.35.0\n".as_slice(),
        ] {
            assert!(apply_release_mod_version(&mut artifact(bytes), "9.99.99").is_err());
        }
    }
}
