//! Collect the ordinary Gradle files a projected project needs to stand alone.
//! The sync layer restores the executable bit on projected Unix `gradlew`.

use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use walkdir::WalkDir;

const ROOT_FILES: &[&str] = &[
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
];

/// These inputs encode the Minecraft/loader baseline and Gradle runtime.
/// Copying 1.19.2 defaults to another target would appear to work at `help`
/// time but produce the wrong dependency graph or artifact identity.
const REQUIRED_TARGET_OVERLAYS: &[&str] = &[
    "gradle.properties",
    "settings.gradle",
    "sfm-toolchain.lock.json",
    "gradle/wrapper/gradle-wrapper.properties",
];

/// Copy only declared build inputs, never caches, run directories or IDE state.
/// The chosen version's Gradle files may later be supplied by a version overlay.
///
/// # Errors
///
/// Fails when a required file is missing, a symlink is encountered or a read fails.
pub fn collect_gradle_project_inputs(
    project_root: &Path,
) -> Result<BTreeMap<String, ProjectedArtifact>> {
    let metadata = fs::symlink_metadata(project_root).wrap_err_with(|| {
        format!(
            "cannot inspect Gradle project root '{}'",
            project_root.display()
        )
    })?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "Gradle project root '{}' must be a real directory",
        project_root.display()
    );
    let root = fs::canonicalize(project_root).wrap_err_with(|| {
        format!(
            "cannot resolve Gradle project root '{}'",
            project_root.display()
        )
    })?;
    ensure!(root.is_dir(), "Gradle project root must be a directory");
    let mut artifacts = BTreeMap::new();
    for name in ROOT_FILES {
        let path = root.join(name);
        let metadata = fs::symlink_metadata(&path)
            .wrap_err_with(|| format!("missing Gradle project input '{name}'"))?;
        ensure!(
            metadata.is_file() && !metadata.file_type().is_symlink(),
            "Gradle project input '{name}' must be a regular file"
        );
        insert(&mut artifacts, name, &path)?;
    }

    let gradle_root = root.join("gradle");
    let metadata = fs::symlink_metadata(&gradle_root)
        .wrap_err("missing Gradle project input directory 'gradle'")?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "Gradle project input 'gradle' must be a real directory"
    );
    for entry in WalkDir::new(&gradle_root)
        .follow_links(false)
        .sort_by_file_name()
    {
        let entry = entry.wrap_err("cannot walk Gradle project input directory")?;
        if entry.path() == gradle_root {
            continue;
        }
        ensure!(
            !entry.file_type().is_symlink(),
            "Gradle project input traverses a symlink: '{}'",
            entry.path().display()
        );
        let resolved = fs::canonicalize(entry.path()).wrap_err_with(|| {
            format!(
                "cannot resolve Gradle project input '{}'",
                entry.path().display()
            )
        })?;
        ensure!(
            resolved.starts_with(&root),
            "Gradle project input '{}' escapes its root",
            entry.path().display()
        );
        if entry.file_type().is_dir() {
            continue;
        }
        ensure!(
            entry.file_type().is_file(),
            "Gradle project input '{}' is not a regular file",
            entry.path().display()
        );
        let relative = entry
            .path()
            .strip_prefix(&root)
            .wrap_err("Gradle walker escaped project root")?;
        let relative = relative
            .components()
            .map(|component| match component {
                Component::Normal(part) => part
                    .to_str()
                    .map(str::to_owned)
                    .ok_or_else(|| eyre::eyre!("Gradle input path is not UTF-8")),
                _ => Err(eyre::eyre!("Gradle input path is not relative")),
            })
            .collect::<Result<Vec<_>>>()?
            .join("/");
        insert(&mut artifacts, &relative, entry.path())?;
    }
    Ok(artifacts)
}

/// Layer sparse, ordered version-specific Gradle inputs over the shared Gradle
/// project. Later overlays replace earlier files at the same exact path.
///
/// Each target must explicitly supply its properties, lockfile, wrapper
/// distribution and `settings.gradle`. The tagged settings source is preserved
/// exactly; only the projected output appends a final literal project name for
/// the generated `mc-version/<target>` location.
///
/// # Errors
///
/// Fails for missing target inputs, non-portable or colliding paths, symlinks,
/// a Minecraft version mismatch, or a project-name mismatch.
pub fn collect_gradle_project_inputs_for_target(
    project_root: &Path,
    overlay_roots: &[(String, PathBuf)],
    target_id: &str,
    minecraft_version: &str,
) -> Result<BTreeMap<String, ProjectedArtifact>> {
    validate_overlay_name(target_id)?;
    let mut artifacts = collect_gradle_project_inputs(project_root)?;
    let mut path_case: BTreeMap<String, String> = artifacts
        .keys()
        .map(|path| (path.to_ascii_lowercase(), path.clone()))
        .collect();
    let mut overlay_names = BTreeSet::new();
    let mut overlaid_paths = BTreeSet::new();
    for (name, root) in overlay_roots {
        validate_overlay_name(name)?;
        ensure!(
            overlay_names.insert(name),
            "duplicate Gradle overlay name '{name}'"
        );
        collect_gradle_overlay(
            root,
            name,
            &mut artifacts,
            &mut path_case,
            &mut overlaid_paths,
        )?;
    }
    for required in REQUIRED_TARGET_OVERLAYS {
        ensure!(
            overlaid_paths.contains(*required),
            "target '{target_id}' needs an explicit Gradle overlay for '{required}'"
        );
    }
    append_project_name_override(&mut artifacts, target_id)?;
    validate_target_project(&artifacts, target_id, minecraft_version)?;
    Ok(artifacts)
}

fn append_project_name_override(
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
    target_id: &str,
) -> Result<()> {
    let settings = artifacts
        .get_mut("settings.gradle")
        .ok_or_else(|| eyre::eyre!("missing Gradle input 'settings.gradle'"))?;
    let source = std::str::from_utf8(&settings.source_bytes)
        .wrap_err("Gradle input 'settings.gradle' is not UTF-8")?;
    let newline = if source.contains("\r\n") {
        "\r\n"
    } else if source.contains('\n') {
        "\n"
    } else if source.contains('\r') {
        "\r"
    } else {
        "\n"
    };
    let mut projected = settings.source_bytes.clone();
    if !projected.is_empty() && !projected.ends_with(b"\n") && !projected.ends_with(b"\r") {
        projected.extend_from_slice(newline.as_bytes());
    }
    projected
        .extend_from_slice(b"// GENERATED layout override; tagged settings source is unchanged.");
    projected.extend_from_slice(newline.as_bytes());
    projected.extend_from_slice(format!("rootProject.name = 'sfm-{target_id}'").as_bytes());
    projected.extend_from_slice(newline.as_bytes());
    settings.output_bytes = projected;
    Ok(())
}

fn collect_gradle_overlay(
    overlay_root: &Path,
    overlay_name: &str,
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
    path_case: &mut BTreeMap<String, String>,
    overlaid_paths: &mut BTreeSet<String>,
) -> Result<()> {
    let metadata = fs::symlink_metadata(overlay_root).wrap_err_with(|| {
        format!(
            "cannot inspect Gradle overlay root '{}'",
            overlay_root.display()
        )
    })?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "Gradle overlay root '{}' must be a real directory",
        overlay_root.display()
    );
    let canonical_root = fs::canonicalize(overlay_root)
        .wrap_err_with(|| format!("cannot resolve Gradle overlay '{}'", overlay_root.display()))?;
    for entry in WalkDir::new(overlay_root)
        .follow_links(false)
        .sort_by_file_name()
    {
        let entry = entry.wrap_err("cannot walk Gradle overlay")?;
        if entry.path() == overlay_root {
            continue;
        }
        ensure!(
            !entry.file_type().is_symlink(),
            "Gradle overlay traverses a symlink: '{}'",
            entry.path().display()
        );
        let resolved = fs::canonicalize(entry.path()).wrap_err_with(|| {
            format!(
                "cannot resolve Gradle overlay input '{}'",
                entry.path().display()
            )
        })?;
        ensure!(
            resolved.starts_with(&canonical_root),
            "Gradle overlay input '{}' escapes its root",
            entry.path().display()
        );
        if entry.file_type().is_dir() {
            continue;
        }
        ensure!(
            entry.file_type().is_file(),
            "Gradle overlay input '{}' is not a regular file",
            entry.path().display()
        );
        let relative = entry
            .path()
            .strip_prefix(overlay_root)
            .wrap_err("Gradle overlay walker escaped its root")?;
        let relative = portable_relative_path(relative)?;
        ensure!(
            ROOT_FILES.contains(&relative.as_str()) || relative.starts_with("gradle/"),
            "Gradle overlay input '{relative}' is outside declared Gradle build inputs"
        );
        let case_key = relative.to_ascii_lowercase();
        if let Some(existing) = path_case.get(&case_key) {
            ensure!(
                existing == &relative,
                "case-colliding Gradle inputs '{existing}' and '{relative}'"
            );
        } else {
            path_case.insert(case_key, relative.clone());
        }
        let bytes = fs::read(entry.path()).wrap_err_with(|| {
            format!(
                "cannot read Gradle overlay input '{}'",
                entry.path().display()
            )
        })?;
        artifacts.insert(
            relative.clone(),
            ProjectedArtifact {
                source_path: relative.clone(),
                source_bytes: bytes.clone(),
                output_bytes: bytes,
                overlay: Some(overlay_name.to_owned()),
            },
        );
        overlaid_paths.insert(relative);
    }
    Ok(())
}

fn validate_target_project(
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    target_id: &str,
    minecraft_version: &str,
) -> Result<()> {
    let properties = artifact_text(artifacts, "gradle.properties")?;
    let versions: Vec<_> = properties
        .lines()
        .filter_map(|line| {
            let line = line.trim();
            if line.starts_with('#') || line.starts_with('!') {
                return None;
            }
            let (key, value) = line.split_once('=')?;
            (key.trim() == "minecraft_version").then(|| value.trim())
        })
        .collect();
    ensure!(
        versions.len() == 1 && versions[0] == minecraft_version,
        "target '{target_id}' needs exactly one minecraft_version={minecraft_version} in gradle.properties"
    );

    let settings = artifact_text(artifacts, "settings.gradle")?;
    let actual_name = settings.lines().rev().find_map(|line| {
        let (key, value) = line.trim().split_once('=')?;
        (key.trim() == "rootProject.name").then(|| value.trim())
    });
    let expected_name = format!("sfm-{target_id}");
    let expected_double = format!("\"{expected_name}\"");
    let expected_single = format!("'{expected_name}'");
    ensure!(
        actual_name == Some(expected_double.as_str())
            || actual_name == Some(expected_single.as_str()),
        "target '{target_id}' needs a final literal rootProject.name = \"{expected_name}\" in its settings.gradle overlay"
    );

    let wrapper = artifact_text(artifacts, "gradle/wrapper/gradle-wrapper.properties")?;
    let distribution = wrapper.lines().find_map(|line| {
        let (key, value) = line.split_once('=')?;
        (key.trim() == "distributionUrl").then(|| value.trim())
    });
    ensure!(
        distribution.is_some_and(|value| {
            value.contains("gradle-")
                && value
                    .rsplit_once('.')
                    .is_some_and(|(_, extension)| extension.eq_ignore_ascii_case("zip"))
        }),
        "target '{target_id}' needs a Gradle wrapper distributionUrl ending in .zip"
    );
    Ok(())
}

fn artifact_text<'a>(
    artifacts: &'a BTreeMap<String, ProjectedArtifact>,
    path: &str,
) -> Result<&'a str> {
    let artifact = artifacts
        .get(path)
        .ok_or_else(|| eyre::eyre!("missing Gradle input '{path}'"))?;
    std::str::from_utf8(&artifact.output_bytes)
        .wrap_err_with(|| format!("Gradle input '{path}' is not UTF-8"))
}

fn portable_relative_path(path: &Path) -> Result<String> {
    let mut parts = Vec::new();
    for component in path.components() {
        let Component::Normal(part) = component else {
            eyre::bail!("Gradle overlay path '{}' is not relative", path.display());
        };
        parts.push(
            part.to_str().ok_or_else(|| {
                eyre::eyre!("Gradle overlay path '{}' is not UTF-8", path.display())
            })?,
        );
    }
    let normalized = parts.join("/");
    ensure!(
        !normalized.is_empty()
            && !normalized
                .chars()
                .any(|character| { character.is_control() || "\\<>:\"|?*".contains(character) })
            && normalized.split('/').all(|part| {
                !part.is_empty()
                    && part != "."
                    && part != ".."
                    && !part.ends_with('.')
                    && !part.ends_with(' ')
                    && !is_windows_device_name(part)
                    && !matches!(part, ".git" | ".gradle" | "build" | "run" | "target")
            }),
        "Gradle overlay path '{normalized}' is not portable or is generated state"
    );
    Ok(normalized)
}

fn is_windows_device_name(part: &str) -> bool {
    let stem = part.split('.').next().unwrap_or_default();
    let upper = stem.to_ascii_uppercase();
    matches!(upper.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || (upper.len() == 4
            && (upper.starts_with("COM") || upper.starts_with("LPT"))
            && matches!(upper.as_bytes()[3], b'1'..=b'9'))
}

fn validate_overlay_name(name: &str) -> Result<()> {
    ensure!(
        !name.is_empty()
            && name.as_bytes()[0].is_ascii_alphanumeric()
            && name.bytes().all(|byte| {
                byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"._-".contains(&byte)
            }),
        "Gradle overlay/target ID '{name}' must be a lowercase portable ID"
    );
    Ok(())
}

fn insert(
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
    relative: &str,
    path: &Path,
) -> Result<()> {
    let bytes = fs::read(path)
        .wrap_err_with(|| format!("cannot read Gradle project input '{}'", path.display()))?;
    ensure!(
        artifacts
            .insert(
                relative.to_owned(),
                ProjectedArtifact {
                    source_path: relative.to_owned(),
                    source_bytes: bytes.clone(),
                    output_bytes: bytes,
                    overlay: Some("gradle_project".to_owned()),
                }
            )
            .is_none(),
        "duplicate Gradle project input '{relative}'"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::context::ProjectionContext;
    use crate::source_projection::inputs::collect_projected_inputs;

    #[test]
    fn only_build_inputs_are_copied() {
        let root = tempfile::tempdir().unwrap();
        for name in ROOT_FILES {
            fs::write(root.path().join(name), name.as_bytes()).unwrap();
        }
        fs::create_dir(root.path().join("gradle")).unwrap();
        fs::write(root.path().join("gradle/wrapper.gradle"), b"wrapper\n").unwrap();
        fs::create_dir(root.path().join("build")).unwrap();
        fs::write(root.path().join("build/output.jar"), b"ignored").unwrap();
        let files = collect_gradle_project_inputs(root.path()).unwrap();
        assert!(files.contains_key("gradle/wrapper.gradle"));
        assert!(files.contains_key("gradlew"));
        assert!(!files.contains_key("build/output.jar"));
        assert_eq!(files["gradle/wrapper.gradle"].output_bytes, b"wrapper\n");
    }

    fn write_base(root: &Path) {
        for name in ROOT_FILES {
            fs::write(root.join(name), name.as_bytes()).unwrap();
        }
        fs::create_dir(root.join("gradle")).unwrap();
        fs::write(root.join("gradle/shared.gradle"), b"shared\n").unwrap();
    }

    fn write_required_target_overlay(root: &Path, target: &str, minecraft: &str) {
        fs::create_dir_all(root.join("gradle/wrapper")).unwrap();
        fs::write(
            root.join("gradle.properties"),
            format!("minecraft_version={minecraft}\n"),
        )
        .unwrap();
        fs::write(
            root.join("settings.gradle"),
            format!(
                "// tagged {target} project\r\nrootProject.name = \"sfm-${{settingsDir.parentFile.parentFile.name}}\"\r\n"
            ),
        )
        .unwrap();
        fs::write(root.join("sfm-toolchain.lock.json"), b"{}\n").unwrap();
        fs::write(
            root.join("gradle/wrapper/gradle-wrapper.properties"),
            b"distributionUrl=https\\://services.gradle.org/distributions/gradle-8.8-bin.zip\n",
        )
        .unwrap();
    }

    #[test]
    fn target_overlay_replaces_baseline_and_later_overlay_wins() {
        let base = tempfile::tempdir().unwrap();
        let version = tempfile::tempdir().unwrap();
        let later = tempfile::tempdir().unwrap();
        write_base(base.path());
        write_required_target_overlay(version.path(), "1.21.0", "1.21");
        fs::create_dir(later.path().join("gradle")).unwrap();
        fs::write(version.path().join("gradle/shared.gradle"), b"version\n").unwrap();
        fs::write(later.path().join("gradle/shared.gradle"), b"later\n").unwrap();

        let artifacts = collect_gradle_project_inputs_for_target(
            base.path(),
            &[
                ("version".to_owned(), version.path().to_path_buf()),
                ("later".to_owned(), later.path().to_path_buf()),
            ],
            "1.21.0",
            "1.21",
        )
        .unwrap();
        assert_eq!(artifacts["gradle/shared.gradle"].output_bytes, b"later\n");
        assert_eq!(
            artifacts["gradle/shared.gradle"].overlay.as_deref(),
            Some("later")
        );
        assert_eq!(
            artifacts["gradle.properties"].overlay.as_deref(),
            Some("version")
        );
        let tagged_settings = fs::read(version.path().join("settings.gradle")).unwrap();
        assert_eq!(artifacts["settings.gradle"].source_bytes, tagged_settings);
        assert_eq!(
            super::super::provenance::sha256(&artifacts["settings.gradle"].source_bytes),
            super::super::provenance::sha256(&tagged_settings)
        );
        assert!(
            artifacts["settings.gradle"]
                .output_bytes
                .ends_with(b"rootProject.name = 'sfm-1.21.0'\r\n")
        );
    }

    #[test]
    fn target_overlay_requires_version_inputs_and_matching_minecraft_property() {
        let base = tempfile::tempdir().unwrap();
        let version = tempfile::tempdir().unwrap();
        write_base(base.path());
        write_required_target_overlay(version.path(), "1.21.1", "1.21.1");
        fs::remove_file(version.path().join("sfm-toolchain.lock.json")).unwrap();
        let error = collect_gradle_project_inputs_for_target(
            base.path(),
            &[("version".to_owned(), version.path().to_path_buf())],
            "1.21.1",
            "1.21.1",
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("sfm-toolchain.lock.json"));

        fs::write(version.path().join("sfm-toolchain.lock.json"), b"{}\n").unwrap();
        fs::write(
            version.path().join("gradle.properties"),
            b"minecraft_version=1.20.4\n",
        )
        .unwrap();
        let error = collect_gradle_project_inputs_for_target(
            base.path(),
            &[("version".to_owned(), version.path().to_path_buf())],
            "1.21.1",
            "1.21.1",
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("minecraft_version=1.21.1"));
    }

    #[test]
    fn target_overlay_rejects_case_collision_and_non_build_input() {
        let base = tempfile::tempdir().unwrap();
        let version = tempfile::tempdir().unwrap();
        write_base(base.path());
        write_required_target_overlay(version.path(), "1.21.1", "1.21.1");
        fs::write(version.path().join("gradle/Shared.gradle"), b"wrong case\n").unwrap();
        let error = collect_gradle_project_inputs_for_target(
            base.path(),
            &[("version".to_owned(), version.path().to_path_buf())],
            "1.21.1",
            "1.21.1",
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("case-colliding"));

        fs::remove_file(version.path().join("gradle/Shared.gradle")).unwrap();
        fs::create_dir(version.path().join("src")).unwrap();
        fs::write(version.path().join("src/NotGradle.java"), b"wrong root\n").unwrap();
        let error = collect_gradle_project_inputs_for_target(
            base.path(),
            &[("version".to_owned(), version.path().to_path_buf())],
            "1.21.1",
            "1.21.1",
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("outside declared Gradle build inputs"));
    }

    #[test]
    #[ignore = "repository-scale smoke test; run explicitly before promoting a projection"]
    fn current_1192_sources_and_gradle_inputs_can_be_collected_together() {
        let crate_root = Path::new(env!("CARGO_MANIFEST_DIR"));
        let repository = crate_root.ancestors().nth(3).unwrap();
        let minecraft = repository.join("platform/minecraft");
        let context = ProjectionContext {
            minecraft_version: "1.19.2".to_owned(),
            preset: "current-development".to_owned(),
            features: BTreeMap::new(),
            targets: BTreeMap::from([("mc_1_19_2".to_owned(), true), ("forge".to_owned(), true)]),
        };
        let mut artifacts =
            collect_projected_inputs(&minecraft.join("src"), &[], &context, &BTreeSet::new())
                .unwrap();
        assert!(artifacts.len() > 500);
        for (path, artifact) in collect_gradle_project_inputs(&minecraft).unwrap() {
            assert!(artifacts.insert(path, artifact).is_none());
        }
        assert!(artifacts.contains_key("build.gradle"));
        assert!(artifacts.contains_key("gradle/wrapper/gradle-wrapper.jar"));
    }
}
