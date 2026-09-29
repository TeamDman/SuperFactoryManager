//! The one reviewed release-version rewrite used by both projection and
//! frozen-inventory authoring. It changes only a canonical Gradle property.

use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
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
    let text = std::str::from_utf8(&properties.output_bytes)
        .wrap_err("release Gradle properties are not UTF-8")?;
    let mut version_range = None;
    let mut offset = 0;
    for line in text.split_inclusive('\n') {
        let line_without_lf = line.strip_suffix('\n').unwrap_or(line);
        let body = line_without_lf
            .strip_suffix('\r')
            .unwrap_or(line_without_lf);
        let trimmed = body.trim_start();
        if !trimmed.starts_with('#') && !trimmed.starts_with('!') {
            ensure!(
                !has_continuation(body),
                "release Gradle properties cannot contain continued lines"
            );
        }
        if parsed_property_key(body)?.as_deref() == Some("mod_version") {
            let old_version = body.strip_prefix("mod_version=").ok_or_else(|| {
                eyre::eyre!(
                    "release Gradle mod_version must use one canonical mod_version=<value> line"
                )
            })?;
            ensure!(
                old_version == old_version.trim(),
                "release Gradle mod_version must use one canonical mod_version=<value> line"
            );
            ensure!(
                version_range.is_none(),
                "release Gradle properties contain multiple mod_version entries"
            );
            ensure!(
                !old_version.trim().is_empty(),
                "release Gradle properties contain an empty mod_version"
            );
            version_range = Some((offset + "mod_version=".len(), offset + body.len()));
        }
        offset += line.len();
    }
    let (start, end) = version_range
        .ok_or_else(|| eyre::eyre!("release Gradle properties need one mod_version entry"))?;
    let mut projected = Vec::with_capacity(properties.output_bytes.len() + version.len());
    projected.extend_from_slice(&properties.output_bytes[..start]);
    projected.extend_from_slice(version.as_bytes());
    projected.extend_from_slice(&properties.output_bytes[end..]);
    properties.output_bytes = projected;
    Ok(())
}

fn has_continuation(line: &str) -> bool {
    line.as_bytes()
        .iter()
        .rev()
        .take_while(|byte| **byte == b'\\')
        .count()
        % 2
        == 1
}

fn parsed_property_key(line: &str) -> Result<Option<String>> {
    let trimmed = line.trim_start();
    if trimmed.is_empty() || trimmed.starts_with('#') || trimmed.starts_with('!') {
        return Ok(None);
    }
    let mut chars = trimmed.chars();
    let mut key = String::new();
    while let Some(next) = chars.next() {
        match next {
            '=' | ':' => break,
            whitespace if whitespace.is_whitespace() => break,
            '\\' => {
                let escaped = chars
                    .next()
                    .ok_or_else(|| eyre::eyre!("unterminated Gradle property key escape"))?;
                if escaped == 'u' {
                    let digits = (0..4)
                        .map(|_| {
                            chars.next().ok_or_else(|| {
                                eyre::eyre!("incomplete Gradle property Unicode escape")
                            })
                        })
                        .collect::<Result<String>>()?;
                    let scalar = u32::from_str_radix(&digits, 16)
                        .wrap_err("invalid Gradle property Unicode escape")?;
                    key.push(
                        char::from_u32(scalar)
                            .ok_or_else(|| eyre::eyre!("invalid Gradle property Unicode scalar"))?,
                    );
                } else {
                    key.push(escaped);
                }
            }
            ordinary => key.push(ordinary),
        }
    }
    Ok(Some(key))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn artifact(bytes: &[u8]) -> BTreeMap<String, ProjectedArtifact> {
        BTreeMap::from([(
            "gradle.properties".to_owned(),
            ProjectedArtifact {
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
