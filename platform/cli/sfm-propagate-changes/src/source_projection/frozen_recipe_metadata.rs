//! Pure proofs separating project version metadata from frozen dependencies.
//! This module has no filesystem, process, cache, or dependency capabilities.

use std::ops::Range;

/// Read the single literal version value used by authored property inputs.
pub(super) fn mod_version(properties: &[u8]) -> Result<&str, String> {
    let span = mod_version_span(properties)?;
    std::str::from_utf8(&properties[span]).map_err(|error| error.to_string())
}

fn mod_version_span(properties: &[u8]) -> Result<Range<usize>, String> {
    let mut found = None;
    let mut offset = 0;
    for line in properties.split_inclusive(|byte| *byte == b'\n') {
        let body = line.strip_suffix(b"\n").unwrap_or(line);
        let body = body.strip_suffix(b"\r").unwrap_or(body);
        if let Some(value) = body.strip_prefix(b"mod_version=") {
            if found.is_some() {
                return Err("project properties repeat mod_version".to_owned());
            }
            if value.is_empty()
                || !value.iter().all(|byte| {
                    byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'-' | b'_' | b'+')
                })
            {
                return Err("project properties have an invalid literal mod_version".to_owned());
            }
            let start = offset + b"mod_version=".len();
            found = Some(start..start + value.len());
        }
        offset += line.len();
    }
    found.ok_or_else(|| "project properties have no literal mod_version".to_owned())
}

/// Require every byte other than the `mod_version` value to retain its pin.
pub(super) fn prove_mod_version_only(pinned: &[u8], projected: &[u8]) -> Result<(), String> {
    let old = mod_version_span(pinned)?;
    let new = mod_version_span(projected)?;
    if pinned[..old.start] != projected[..new.start] || pinned[old.end..] != projected[new.end..] {
        return Err(
            "selected properties change more than mod_version in the frozen role".to_owned(),
        );
    }
    Ok(())
}

/// Identity of a reviewed dependency recipe; the recipe name is opaque here.
pub(super) struct DependencyRecipeIdentity<'a> {
    pub target: &'a str,
    pub minecraft_version: &'a str,
    pub java_major: u16,
    pub loader: &'a str,
    pub source_lock_path: &'a str,
    pub source_lock_sha256: &'a str,
    pub recipe_id: &'a str,
}

/// Resolve exactly one pinned dependency identity, independently of mod version.
pub(super) fn select_dependency_recipe<'a>(
    reviewed: &[DependencyRecipeIdentity<'a>],
    project: &DependencyRecipeIdentity<'_>,
) -> Result<&'a str, String> {
    let mut matches = reviewed.iter().filter(|candidate| {
        candidate.target == project.target
            && candidate.minecraft_version == project.minecraft_version
            && candidate.java_major == project.java_major
            && candidate.loader == project.loader
            && candidate.source_lock_path == project.source_lock_path
            && candidate.source_lock_sha256 == project.source_lock_sha256
    });
    let selected = matches
        .next()
        .ok_or_else(|| "selected project has no matching reviewed dependency recipe".to_owned())?;
    if matches.next().is_some() {
        return Err("selected project has ambiguous reviewed dependency recipes".to_owned());
    }
    Ok(selected.recipe_id)
}

#[cfg(test)]
mod tests {
    use super::*;

    const ORIGINAL: &[u8] = b"# frozen\nminecraft_version=1.19.2\nneo_version=43.4.0\nmod_version=4.34.0\nmapping_version=2022.11.27-1.19.2\n";

    fn changed(old: &str, new: &str) -> Vec<u8> {
        std::str::from_utf8(ORIGINAL)
            .unwrap()
            .replace(old, new)
            .into_bytes()
    }

    #[test]
    fn original_properties_remain_exactly_admitted() {
        assert!(prove_mod_version_only(ORIGINAL, ORIGINAL).is_ok());
        assert_eq!(mod_version(ORIGINAL).unwrap(), "4.34.0");
    }

    #[test]
    fn new_mod_version_reuses_unchanged_dependency_evidence() {
        let new = changed("mod_version=4.34.0", "mod_version=4.35.0");
        assert!(prove_mod_version_only(ORIGINAL, &new).is_ok());
        assert_eq!(mod_version(&new).unwrap(), "4.35.0");
        assert_ne!(new, ORIGINAL);
    }

    #[test]
    fn different_length_version_preserves_crlf_and_no_final_newline() {
        let original = b"neo_version=43.4.0\r\nmod_version=4.34.0";
        let new = b"neo_version=43.4.0\r\nmod_version=4.350.0-rc.1+proof";
        assert!(prove_mod_version_only(original, new).is_ok());
        assert!(
            prove_mod_version_only(original, b"neo_version=43.4.0\nmod_version=4.350.0").is_err()
        );
    }

    #[test]
    fn dependency_and_unrelated_property_changes_are_rejected() {
        for (old, new) in [
            ("1.19.2", "1.19.4"),
            ("43.4.0", "43.4.1"),
            ("2022.11.27", "2023.01.01"),
            ("# frozen", "# another input"),
        ] {
            assert!(
                prove_mod_version_only(ORIGINAL, &changed(old, new)).is_err(),
                "{old}"
            );
        }
        let mut appended = ORIGINAL.to_vec();
        appended.extend_from_slice(b"extra_property=true\n");
        assert!(prove_mod_version_only(ORIGINAL, &appended).is_err());
    }

    #[test]
    fn missing_duplicate_empty_escaped_or_multiline_versions_are_rejected() {
        for value in [
            b"neo_version=43.4.0\n".as_slice(),
            b"mod_version=\n",
            b"mod_version=4.35.0\nmod_version=4.36.0\n",
            b"mod_version=4.35.0\\\ncontinued\n",
            b"mod_version=4.35.0 value\n",
        ] {
            assert!(prove_mod_version_only(ORIGINAL, value).is_err());
        }
    }

    fn identity(recipe_id: &str) -> DependencyRecipeIdentity<'_> {
        DependencyRecipeIdentity {
            target: "1.21.0",
            minecraft_version: "1.21",
            java_major: 21,
            loader: "neoforge",
            source_lock_path: "core/pinned-lock.json",
            source_lock_sha256: "sha256:original-pin",
            recipe_id,
        }
    }

    #[test]
    fn recipe_identity_is_opaque_and_independent_of_project_family() {
        let reviewed = [identity("reviewed:unchanged-dependencies@1")];
        for family in ["sfm-4.34.0", "sfm-4.35.0", "another-release"] {
            assert_eq!(
                select_dependency_recipe(&reviewed, &identity(family)).unwrap(),
                reviewed[0].recipe_id
            );
        }
    }

    #[test]
    fn recipe_selection_preserves_exact_target_and_lock_boundaries() {
        let reviewed = [identity("reviewed:unchanged-dependencies@1")];
        for field in ["target", "minecraft", "java", "loader", "path", "hash"] {
            let mut project = identity("project");
            match field {
                "target" => project.target = "1.21.1",
                "minecraft" => project.minecraft_version = "1.21.0",
                "java" => project.java_major = 17,
                "loader" => project.loader = "forge",
                "path" => project.source_lock_path = "unreviewed-lock.json",
                "hash" => project.source_lock_sha256 = "sha256:changed",
                _ => unreachable!(),
            }
            assert!(
                select_dependency_recipe(&reviewed, &project).is_err(),
                "{field}"
            );
        }
    }

    #[test]
    fn recipe_selection_refuses_missing_and_ambiguous_admission() {
        assert!(select_dependency_recipe(&[], &identity("project")).is_err());
        let reviewed = [identity("first@1"), identity("second@1")];
        assert!(select_dependency_recipe(&reviewed, &identity("project")).is_err());
    }
}
