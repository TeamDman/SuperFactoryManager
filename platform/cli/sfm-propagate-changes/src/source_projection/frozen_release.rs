//! Tag-independent, commit-frozen inputs for a future released preset.
//!
//! The inventory is a reviewed path contract, not a claim that a candidate
//! passed gameplay or publication acceptance. Each source blob is read from
//! its exact authored Git commit, never from a later working tree.

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::inputs::render_java_artifact;
use super::manifest::FrozenSourceBinding;
use super::project_layout::append_project_name_override;
use super::promotion::validate_relative_path;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::release_baseline::read_pinned_blob_with_mode_hardened;
use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

pub(crate) const SCHEMA: &str = "sfm:frozen_release_sources@1";
pub(crate) const MAX_INVENTORY_BYTES: u64 = 8 * 1024 * 1024;
const REQUIRED_BUILD_INPUTS: &[&str] = &[
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
];

#[derive(Clone, Debug, Facet)]
pub struct FrozenSourceInventory {
    pub schema: String,
    pub target_id: String,
    pub source_commit: String,
    /// Exact Liquid environment from the frozen preset selection. New feature
    /// definitions in a later manifest must not alter replay semantics.
    pub context: ProjectionContext,
    /// Excluded outputs derived when this snapshot was reviewed. Later disabled
    /// feature declarations cannot silently widen or relax this set.
    pub excluded_paths: Vec<String>,
    /// The complete set of files owned by this target's released projection.
    pub files: BTreeMap<String, FrozenSourceFile>,
}

#[derive(Clone, Debug, Facet)]
pub struct FrozenSourceFile {
    /// Exact repository-relative path of the authored input at `source_commit`.
    pub source_repo_path: String,
    pub git_mode: String,
    pub blob_oid: String,
    pub source_sha256: String,
    pub output_sha256: String,
    pub overlay: Option<String>,
    pub transform: FrozenTransform,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum FrozenTransform {
    Copy,
    Java,
    TargetSettings,
}

/// Recreate a complete release project from exact Git blobs and a reviewed
/// inventory. The caller applies the preset's release version to
/// `gradle.properties`, then calls `verify_frozen_outputs` before syncing.
///
/// # Errors
///
/// Rejects a changed inventory, unsafe path, wrong Git blob/mode, malformed
/// source or a missing required Gradle input without writing to the project.
pub fn project_frozen_artifacts(
    repo_root: &Path,
    binding: &FrozenSourceBinding,
    source_commit: &str,
    context: &ProjectionContext,
) -> Result<(BTreeMap<String, ProjectedArtifact>, FrozenSourceInventory)> {
    let inventory = read_inventory(repo_root, binding, source_commit, context)?;
    let mut artifacts = BTreeMap::new();
    for (output_path, file) in &inventory.files {
        let relative = file
            .source_repo_path
            .strip_prefix("platform/minecraft/")
            .ok_or_else(|| {
                eyre::eyre!("frozen input '{output_path}' is outside Minecraft sources")
            })?;
        let bytes = read_pinned_blob_with_mode_hardened(
            repo_root,
            source_commit,
            relative,
            &file.blob_oid,
            &file.source_sha256,
            Some(&file.git_mode),
        )?;
        let mut artifact = ProjectedArtifact {
            source_path: file.source_repo_path.clone(),
            source_bytes: bytes.clone(),
            output_bytes: bytes,
            overlay: file.overlay.clone(),
        };
        if file.transform == FrozenTransform::Java {
            render_java_artifact(output_path, &mut artifact, &inventory.context)?;
        }
        ensure!(
            artifacts.insert(output_path.clone(), artifact).is_none(),
            "frozen output '{output_path}' is repeated"
        );
    }
    if inventory
        .files
        .get("settings.gradle")
        .is_some_and(|file| file.transform == FrozenTransform::TargetSettings)
    {
        append_project_name_override(&mut artifacts, &binding.target_id)?;
    }
    Ok((artifacts, inventory))
}

/// Compare the full rendered path set and every output hash after the optional
/// release-version rewrite. No extra Gradle input can join the project here.
///
/// # Errors
///
/// Rejects a missing/extra output or a changed rendered file.
pub fn verify_frozen_outputs(
    inventory: &FrozenSourceInventory,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<()> {
    ensure!(
        artifacts.len() == inventory.files.len() && artifacts.keys().eq(inventory.files.keys()),
        "frozen release output path set differs from its reviewed inventory"
    );
    for (path, file) in &inventory.files {
        ensure!(
            sha256(&artifacts[path].output_bytes) == file.output_sha256,
            "frozen release output '{path}' differs from its reviewed SHA-256"
        );
    }
    Ok(())
}

fn read_inventory(
    repo_root: &Path,
    binding: &FrozenSourceBinding,
    source_commit: &str,
    current_context: &ProjectionContext,
) -> Result<FrozenSourceInventory> {
    validate_relative_path(&binding.inventory_path)?;
    let root = fs::canonicalize(repo_root).wrap_err("cannot resolve frozen repository root")?;
    ensure!(root.is_dir(), "frozen repository root must be a directory");
    validate_git_commit_root(&root, source_commit)?;
    // The shared checked-file guard also rejects Windows junctions and other
    // reparse points; `file_type().is_symlink()` alone does not catch them.
    let path = checked_file(&root, &binding.inventory_path)?;
    let metadata = fs::metadata(&path)?;
    ensure!(
        metadata.is_file() && metadata.len() <= MAX_INVENTORY_BYTES,
        "frozen inventory must be a bounded regular file"
    );
    let bytes = fs::read(&path)?;
    ensure!(
        sha256(&bytes) == format!("sha256:{}", binding.inventory_sha256),
        "frozen source inventory differs from its pinned SHA-256"
    );
    let text = std::str::from_utf8(&bytes).wrap_err("frozen source inventory is not UTF-8")?;
    let inventory: FrozenSourceInventory =
        facet_json::from_str(text).wrap_err("cannot parse frozen source inventory")?;
    // A map parser may collapse duplicate exact keys. New inventories have a
    // canonical encoding so the pinned bytes must round-trip exactly.
    let canonical = format!("{}\n", facet_json::to_string_pretty(&inventory)?);
    ensure!(
        text == canonical,
        "frozen source inventory is not canonical JSON (or repeats a key)"
    );
    validate_inventory(&inventory, binding, source_commit, current_context)?;
    Ok(inventory)
}

pub(crate) fn validate_git_commit_root(root: &Path, source_commit: &str) -> Result<()> {
    let top = frozen_git_command(root)
        .args(["rev-parse", "--show-toplevel"])
        .output()
        .wrap_err("cannot inspect frozen source Git repository")?;
    ensure!(
        top.status.success(),
        "frozen source root is not a Git worktree"
    );
    let top = String::from_utf8(top.stdout).wrap_err("Git root is not UTF-8")?;
    ensure!(
        fs::canonicalize(top.trim())? == root,
        "frozen source repository root must be the Git worktree root"
    );
    let kind = frozen_git_command(root)
        .args(["cat-file", "-t", source_commit])
        .output()
        .wrap_err("cannot inspect frozen source commit")?;
    ensure!(
        kind.status.success() && kind.stdout == b"commit\n",
        "frozen source must name an available Git commit"
    );
    Ok(())
}

pub(crate) fn validate_inventory(
    inventory: &FrozenSourceInventory,
    binding: &FrozenSourceBinding,
    source_commit: &str,
    current_context: &ProjectionContext,
) -> Result<()> {
    ensure!(
        inventory.schema == SCHEMA
            && inventory.target_id == binding.target_id
            && inventory.source_commit == source_commit,
        "frozen source inventory identity does not match its preset binding"
    );
    validate_frozen_context_and_exclusions(inventory, current_context)?;
    ensure!(
        !inventory.files.is_empty(),
        "frozen source inventory is empty"
    );
    let mut casefold = BTreeSet::new();
    for (path, file) in &inventory.files {
        validate_relative_path(path)?;
        ensure!(
            casefold.insert(path.to_ascii_lowercase()),
            "frozen source output '{path}' case-collides with another output"
        );
        let top = path
            .split('/')
            .next()
            .unwrap_or_default()
            .to_ascii_lowercase();
        ensure!(
            !matches!(
                top.as_str(),
                "build" | ".gradle" | ".git" | "run" | "runclient" | "runserver" | ".idea"
            ) && !path
                .split('/')
                .any(|segment| segment.eq_ignore_ascii_case(".git")),
            "frozen source output '{path}' is a build or runtime path"
        );
        validate_relative_path(&file.source_repo_path)?;
        let source_folded = file.source_repo_path.to_ascii_lowercase();
        ensure!(
            file.source_repo_path.starts_with("platform/minecraft/")
                && !source_folded.starts_with("platform/minecraft/mc-version/")
                && !source_folded.starts_with("platform/minecraft/frozen-releases/"),
            "frozen source for '{path}' must be authored input, not generated output"
        );
        ensure!(
            matches!(file.git_mode.as_str(), "100644" | "100755")
                && is_lower_hex(&file.blob_oid, 40)
                && is_sha256(&file.source_sha256)
                && is_sha256(&file.output_sha256),
            "frozen source record for '{path}' has invalid Git or hash identity"
        );
        let java_output = Path::new(path)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"));
        match file.transform {
            FrozenTransform::Java => ensure!(
                path.starts_with("src/") && java_output,
                "Java frozen transform requires a src/... .java output"
            ),
            FrozenTransform::TargetSettings => ensure!(
                path == "settings.gradle",
                "target-settings transform is permitted only for settings.gradle"
            ),
            FrozenTransform::Copy => ensure!(
                !(path.starts_with("src/") && java_output),
                "source Java frozen output '{path}' requires the Java renderer"
            ),
        }
    }
    for path in REQUIRED_BUILD_INPUTS {
        ensure!(
            inventory.files.contains_key(*path),
            "frozen release target '{}' lacks required Gradle input '{path}'",
            inventory.target_id
        );
    }
    Ok(())
}

fn validate_frozen_context_and_exclusions(
    inventory: &FrozenSourceInventory,
    current_context: &ProjectionContext,
) -> Result<()> {
    ensure!(
        inventory.context.minecraft_version == current_context.minecraft_version
            && inventory.context.preset == current_context.preset,
        "frozen projection context targets the wrong Minecraft version or preset"
    );
    let enabled_keys = |values: &BTreeMap<String, bool>| {
        values
            .iter()
            .filter_map(|(key, active)| active.then_some(key.clone()))
            .collect::<BTreeSet<_>>()
    };
    ensure!(
        enabled_keys(&inventory.context.features) == enabled_keys(&current_context.features)
            && enabled_keys(&inventory.context.targets) == enabled_keys(&current_context.targets),
        "frozen projection context enables features or targets not selected by the preset"
    );
    let mut excluded_casefold = BTreeSet::new();
    for excluded in &inventory.excluded_paths {
        validate_relative_path(excluded)?;
        ensure!(
            excluded_casefold.insert(excluded.to_ascii_lowercase()),
            "frozen excluded output '{excluded}' is repeated or case-colliding"
        );
    }
    ensure!(
        inventory
            .excluded_paths
            .windows(2)
            .all(|pair| pair[0] < pair[1]),
        "frozen excluded outputs must be sorted"
    );
    Ok(())
}

fn is_lower_hex(value: &str, length: usize) -> bool {
    value.len() == length
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || matches!(byte, b'a'..=b'f'))
}

fn is_sha256(value: &str) -> bool {
    value
        .strip_prefix("sha256:")
        .is_some_and(|digest| is_lower_hex(digest, 64))
}

#[cfg(test)]
mod tests {
    use super::super::sync::ProjectionIdentity;
    use super::super::sync::SyncMode;
    use super::super::sync::sync_projection;
    use super::*;
    use crate::cancellation::CancellationToken;
    use crate::cli::source::LegacySourceArgs;
    use crate::cli::source::LegacySourceCommand;
    use crate::cli::source::SourceProjectArgs;
    use crate::source_projection::manifest::PathEffect;
    use crate::source_projection::manifest::PathEffectKind;
    use crate::source_projection::manifest::ProjectionFeature;
    use crate::source_projection::manifest::ProjectionPreset;
    use crate::source_projection::manifest::ProjectionTarget;
    use crate::source_projection::manifest::SCHEMA_VERSION;
    use crate::source_projection::manifest::SourceProjectionManifest;
    use std::path::PathBuf;
    use std::process::Command;

    const PRESET: &str = "released-9.99.99-fixture";
    const JAVA: &str = "src/main/java/example/Proof.java";
    const EXTRA: &str = "src/main/java/example/Extra.java";
    const RESOURCE: &str = "src/main/resources/assets/sfm/frozen.txt";
    const TEST_FIXTURE: &str = "src/test/resources/frozen.json";
    const EXAMPLE: &str = "examples/frozen.sfml";

    fn git(root: &Path, args: &[&str]) -> String {
        let output = Command::new("git")
            .arg("-C")
            .arg(root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {args:?} failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn write(root: &Path, relative: &str, bytes: &[u8]) {
        let path = root.join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, bytes).unwrap();
    }

    fn setup() -> (tempfile::TempDir, String) {
        let root = tempfile::tempdir().unwrap();
        git(root.path(), &["init", "--quiet"]);
        git(root.path(), &["config", "user.name", "SFM fixture"]);
        git(
            root.path(),
            &["config", "user.email", "sfm-fixture@example.invalid"],
        );
        write(
            root.path(),
            "platform/minecraft/src/main/java/example/Proof.java",
            b"class Proof {\n{% if targets.mc_1_20 %}\n int target = 120;\n{% else %}\n int target = 1194;\n{% endif %}\n}\n",
        );
        write(
            root.path(),
            "platform/minecraft/version-sources/1.20/src/main/java/example/Extra.java",
            b"class Extra {}\n",
        );
        write(
            root.path(),
            "platform/minecraft/src/main/resources/assets/sfm/frozen.txt",
            b"frozen resource\n",
        );
        write(
            root.path(),
            "platform/minecraft/src/test/resources/frozen.json",
            b"{\"fixture\":true}\n",
        );
        for (target, minecraft_version) in [("1.19.4", "1.19.4"), ("1.20", "1.20")] {
            let prefix = format!("platform/minecraft/freeze-fixture/{target}");
            write(
                root.path(),
                &format!("{prefix}/{EXAMPLE}"),
                b"EVERY 20 TICKS DO END\n",
            );
            write(
                root.path(),
                &format!("{prefix}/build.gradle"),
                b"plugins {}\n",
            );
            let settings = if target == "1.20" {
                "rootProject.name = 'old-name'\n"
            } else {
                "rootProject.name = 'sfm-1.19.4'\n"
            };
            write(
                root.path(),
                &format!("{prefix}/settings.gradle"),
                settings.as_bytes(),
            );
            write(
                root.path(),
                &format!("{prefix}/gradle.properties"),
                format!("minecraft_version={minecraft_version}\nmod_version=4.34.0\n").as_bytes(),
            );
            write(root.path(), &format!("{prefix}/gradlew"), b"#!/bin/sh\n");
            write(
                root.path(),
                &format!("{prefix}/gradlew.bat"),
                b"@echo off\r\n",
            );
            write(
                root.path(),
                &format!("{prefix}/sfm-toolchain.lock.json"),
                b"{}\n",
            );
            write(
                root.path(),
                &format!("{prefix}/gradle/wrapper/gradle-wrapper.jar"),
                b"synthetic wrapper bytes",
            );
            write(
                root.path(),
                &format!("{prefix}/gradle/wrapper/gradle-wrapper.properties"),
                b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n",
            );
        }
        git(root.path(), &["add", "--", "platform/minecraft"]);
        git(root.path(), &["commit", "--quiet", "-m", "source A"]);
        let commit = git(root.path(), &["rev-parse", "HEAD"]);
        (root, commit)
    }

    fn context(target: &str) -> ProjectionContext {
        ProjectionContext {
            minecraft_version: target.to_owned(),
            preset: PRESET.to_owned(),
            environment: "release".to_owned(),
            projection_key: format!("sfm-fixture/mc-{target}"),
            features: BTreeMap::new(),
            targets: BTreeMap::from([
                ("mc_1_19_4".to_owned(), target == "1.19.4"),
                ("mc_1_20".to_owned(), target == "1.20"),
                ("forge".to_owned(), true),
            ]),
        }
    }

    fn source_for(target: &str, output: &str) -> String {
        if target == "1.20" && output == EXTRA {
            format!("platform/minecraft/version-sources/{target}/{output}")
        } else if output.starts_with("src/") {
            format!("platform/minecraft/{output}")
        } else {
            format!("platform/minecraft/freeze-fixture/{target}/{output}")
        }
    }

    fn git_identity(root: &Path, commit: &str, source: &str) -> (String, String) {
        let line = git(root, &["ls-tree", commit, "--", source]);
        let (object, exact_path) = line.split_once('\t').unwrap();
        assert_eq!(exact_path, source);
        let fields = object.split_whitespace().collect::<Vec<_>>();
        assert_eq!(fields[1], "blob");
        (fields[0].to_owned(), fields[2].to_owned())
    }

    fn inventory(root: &Path, commit: &str, target: &str) -> FrozenSourceInventory {
        let mut paths = REQUIRED_BUILD_INPUTS.to_vec();
        paths.push(JAVA);
        paths.extend([RESOURCE, TEST_FIXTURE, EXAMPLE]);
        if target == "1.20" {
            paths.push(EXTRA);
        }
        let mut files = BTreeMap::new();
        let mut artifacts = BTreeMap::new();
        for path in paths {
            let source = source_for(target, path);
            let bytes = fs::read(root.join(&source)).unwrap();
            let (git_mode, blob_oid) = git_identity(root, commit, &source);
            let transform = if path.ends_with(".java") {
                FrozenTransform::Java
            } else if target == "1.20" && path == "settings.gradle" {
                FrozenTransform::TargetSettings
            } else {
                FrozenTransform::Copy
            };
            let mut artifact = ProjectedArtifact {
                source_path: source.clone(),
                source_bytes: bytes.clone(),
                output_bytes: bytes.clone(),
                overlay: None,
            };
            if transform == FrozenTransform::Java {
                render_java_artifact(path, &mut artifact, &context(target)).unwrap();
            }
            if path == "gradle.properties" {
                release_version(&mut artifact);
            }
            artifacts.insert(path.to_owned(), artifact);
            files.insert(
                path.to_owned(),
                FrozenSourceFile {
                    source_repo_path: source,
                    git_mode,
                    blob_oid,
                    source_sha256: sha256(&bytes),
                    output_sha256: String::new(),
                    overlay: None,
                    transform,
                },
            );
        }
        if target == "1.20" {
            append_project_name_override(&mut artifacts, target).unwrap();
        }
        for (path, file) in &mut files {
            file.output_sha256 = sha256(&artifacts[path].output_bytes);
        }
        FrozenSourceInventory {
            schema: SCHEMA.to_owned(),
            target_id: target.to_owned(),
            source_commit: commit.to_owned(),
            context: context(target),
            excluded_paths: vec![],
            files,
        }
    }

    fn release_version(artifact: &mut ProjectedArtifact) {
        artifact.output_bytes = String::from_utf8(artifact.output_bytes.clone())
            .unwrap()
            .replace("mod_version=4.34.0", "mod_version=9.99.99-fixture")
            .into_bytes();
    }

    fn release_version_in_projected(artifacts: &mut BTreeMap<String, ProjectedArtifact>) {
        release_version(artifacts.get_mut("gradle.properties").unwrap());
    }

    fn project_args(root: &Path, target: &str) -> SourceProjectArgs {
        SourceProjectArgs {
            repo_root: root.to_path_buf(),
            target: target.to_owned(),
            preset: PRESET.to_owned(),
            manifest: None,
            primary_src_root: None,
            gradle_project_root: None,
            output_root: PathBuf::from(format!("generated/{target}")),
            overlay: vec![],
            gradle_overlay: vec![],
        }
    }

    #[test]
    fn source_sync_and_check_use_two_frozen_targets_and_reject_edited_output() {
        let (root, commit) = setup();
        let bindings = ["1.19.4", "1.20"]
            .into_iter()
            .map(|target| {
                let inventory = inventory(root.path(), &commit, target);
                bind_inventory(root.path(), target, &inventory)
            })
            .collect();
        let mut manifest = SourceProjectionManifest {
            schema_version: SCHEMA_VERSION,
            targets: ["1.19.4", "1.20"]
                .into_iter()
                .map(|id| ProjectionTarget {
                    id: id.to_owned(),
                    template_key: format!("mc_{}", id.replace('.', "_")),
                    minecraft_version: id.to_owned(),
                    loader: "forge".to_owned(),
                    java_major: 17,
                    project_dir: format!("platform/minecraft/mc-version/{id}"),
                })
                .collect(),
            features: vec![],
            presets: vec![ProjectionPreset {
                id: PRESET.to_owned(),
                release_mod_version: Some("9.99.99-fixture".to_owned()),
                targets: vec!["1.19.4".to_owned(), "1.20".to_owned()],
                enabled_features: vec![],
                target_features: BTreeMap::new(),
                release_baselines: vec![],
                frozen_source_commit: Some(commit.clone()),
                frozen_sources: bindings,
                canonical_project_fixture_provenance_sha256: None,
                identity: String::new(),
            }],
        };
        manifest.presets[0].identity = manifest
            .compute_preset_identity(&manifest.presets[0])
            .unwrap();
        write(
            root.path(),
            "platform/minecraft/source-projection.json",
            manifest.to_json().unwrap().as_bytes(),
        );
        let cancellation = CancellationToken::new();
        for target in ["1.19.4", "1.20"] {
            LegacySourceArgs {
                command: LegacySourceCommand::Sync(project_args(root.path(), target)),
            }
            .invoke_in(&cancellation, root.path())
            .unwrap();
            let projected = root.path().join(format!("generated/{target}"));
            assert!(
                fs::read_to_string(projected.join("gradle.properties"))
                    .unwrap()
                    .contains("mod_version=9.99.99-fixture")
            );
            assert!(
                projected
                    .join("gradle/wrapper/gradle-wrapper.jar")
                    .is_file()
            );
            for path in [RESOURCE, TEST_FIXTURE, EXAMPLE] {
                assert!(projected.join(path).is_file());
            }
            assert_eq!(projected.join(EXTRA).is_file(), target == "1.20");
        }

        #[cfg(windows)]
        {
            let probe = root.path().join("platform/minecraft/SRC/frozen-probe");
            let mut under_source = project_args(root.path(), "1.19.4");
            under_source.output_root = PathBuf::from("platform/minecraft/SRC/frozen-probe");
            assert!(
                LegacySourceArgs {
                    command: LegacySourceCommand::Sync(under_source),
                }
                .invoke_in(&cancellation, root.path())
                .is_err()
            );
            assert!(!probe.exists());

            let mut root_alias = project_args(root.path(), "1.19.4");
            root_alias.output_root =
                PathBuf::from(root.path().to_string_lossy().to_ascii_uppercase());
            assert!(
                LegacySourceArgs {
                    command: LegacySourceCommand::Sync(root_alias),
                }
                .invoke_in(&cancellation, root.path())
                .is_err()
            );
            assert!(!root.path().join(super::super::sync::MANIFEST_FILE).exists());
        }

        // A later source commit cannot silently redefine this release preset.
        write(
            root.path(),
            "platform/minecraft/src/main/java/example/Proof.java",
            b"class Proof { int laterEdit = 1; }\n",
        );
        git(root.path(), &["add", "--", "platform/minecraft"]);
        git(root.path(), &["commit", "--quiet", "-m", "source B"]);
        manifest.features.push(ProjectionFeature {
            id: "later_disabled".to_owned(),
            supported_targets: vec!["1.19.4".to_owned(), "1.20".to_owned()],
            requires: vec![],
            source_effects: vec![],
            resource_effects: vec![PathEffect {
                output_path: RESOURCE.to_owned(),
                kind: PathEffectKind::Include,
                input_path: Some(format!("platform/minecraft/{RESOURCE}")),
            }],
            dependency_effects: vec![],
        });
        assert_eq!(
            manifest
                .compute_preset_identity(&manifest.presets[0])
                .unwrap(),
            manifest.presets[0].identity
        );
        write(
            root.path(),
            "platform/minecraft/source-projection.json",
            manifest.to_json().unwrap().as_bytes(),
        );
        // A frozen release must remain projectable even if the current source
        // directory moves away; no working-tree source bytes are consumed.
        fs::rename(
            root.path().join("platform/minecraft/src"),
            root.path().join("platform/minecraft/archived-src"),
        )
        .unwrap();
        for target in ["1.19.4", "1.20"] {
            LegacySourceArgs {
                command: LegacySourceCommand::Check(project_args(root.path(), target)),
            }
            .invoke_in(&cancellation, root.path())
            .unwrap();
        }

        let edited = root.path().join("generated/1.20").join(EXTRA);
        fs::write(&edited, b"contributor edit\n").unwrap();
        assert!(
            LegacySourceArgs {
                command: LegacySourceCommand::Sync(project_args(root.path(), "1.20")),
            }
            .invoke_in(&cancellation, root.path())
            .is_err()
        );
        assert_eq!(fs::read(&edited).unwrap(), b"contributor edit\n");
    }

    fn bind_inventory(
        root: &Path,
        target: &str,
        inventory: &FrozenSourceInventory,
    ) -> FrozenSourceBinding {
        let relative =
            format!("platform/minecraft/frozen-releases/{PRESET}/{target}/inventory.json");
        let mut json = facet_json::to_string_pretty(inventory).unwrap();
        json.push('\n');
        write(root, &relative, json.as_bytes());
        FrozenSourceBinding {
            target_id: target.to_owned(),
            inventory_path: relative,
            inventory_sha256: sha256(json.as_bytes())
                .strip_prefix("sha256:")
                .unwrap()
                .to_owned(),
        }
    }

    #[test]
    fn two_targets_reproject_from_commit_after_primary_changes() {
        let (root, commit) = setup();
        let mut bindings = Vec::new();
        let outputs = tempfile::tempdir().unwrap();
        for target in ["1.19.4", "1.20"] {
            let inventory = inventory(root.path(), &commit, target);
            let binding = bind_inventory(root.path(), target, &inventory);
            let (mut artifacts, parsed) =
                project_frozen_artifacts(root.path(), &binding, &commit, &context(target)).unwrap();
            release_version_in_projected(&mut artifacts);
            verify_frozen_outputs(&parsed, &artifacts).unwrap();
            assert_eq!(artifacts.contains_key(EXTRA), target == "1.20");
            let identity = ProjectionIdentity {
                target_id: target.to_owned(),
                minecraft_version: target.to_owned(),
                preset_id: PRESET.to_owned(),
                preset_definition_identity: format!("blake3:{}", "a".repeat(64)),
            };
            let output = outputs.path().join(target);
            sync_projection(&output, &identity, &artifacts, SyncMode::Apply).unwrap();
            bindings.push((target, binding, identity, output, artifacts));
        }
        write(
            root.path(),
            "platform/minecraft/src/main/java/example/Proof.java",
            b"class Proof { int changedAfterFreeze = 1; }\n",
        );
        git(root.path(), &["add", "--", "platform/minecraft"]);
        git(root.path(), &["commit", "--quiet", "-m", "source B"]);
        for (target, binding, identity, output, before) in &bindings {
            let (mut after, parsed) =
                project_frozen_artifacts(root.path(), binding, &commit, &context(target)).unwrap();
            release_version_in_projected(&mut after);
            verify_frozen_outputs(&parsed, &after).unwrap();
            assert_eq!(&after, before);
            let report = sync_projection(output, identity, &after, SyncMode::Check).unwrap();
            assert!(!report.needs_write());
        }
        let (_, _, identity, output, artifacts) = &bindings[0];
        fs::write(output.join(JAVA), b"contributor edit").unwrap();
        assert!(sync_projection(output, identity, artifacts, SyncMode::Apply).is_err());
    }

    #[test]
    fn changed_inventory_and_git_mode_fail_closed() {
        let (root, commit) = setup();
        let base = inventory(root.path(), &commit, "1.19.4");
        let binding = bind_inventory(root.path(), "1.19.4", &base);
        write(root.path(), &binding.inventory_path, b"changed");
        assert!(
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).is_err()
        );

        let mut wrong_mode = base.clone();
        wrong_mode.files.get_mut(JAVA).unwrap().git_mode = "100755".to_owned();
        let binding = bind_inventory(root.path(), "1.19.4", &wrong_mode);
        let error = project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4"))
            .unwrap_err();
        assert!(format!("{error:?}").contains("Git mode"));

        let mut false_preset_feature = base.clone();
        false_preset_feature
            .context
            .features
            .insert("unselected".to_owned(), true);
        let binding = bind_inventory(root.path(), "1.19.4", &false_preset_feature);
        assert!(
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).is_err()
        );

        let mut repeated_exclusion = base.clone();
        repeated_exclusion.excluded_paths = vec![RESOURCE.to_owned(), RESOURCE.to_uppercase()];
        let binding = bind_inventory(root.path(), "1.19.4", &repeated_exclusion);
        assert!(
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).is_err()
        );

        let mut wrong_output = base.clone();
        wrong_output.files.get_mut(JAVA).unwrap().output_sha256 =
            format!("sha256:{}", "0".repeat(64));
        let binding = bind_inventory(root.path(), "1.19.4", &wrong_output);
        let (artifacts, parsed) =
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).unwrap();
        assert!(verify_frozen_outputs(&parsed, &artifacts).is_err());
    }

    #[test]
    fn incomplete_or_unsafe_inventory_is_rejected() {
        let (root, commit) = setup();
        let complete = inventory(root.path(), &commit, "1.19.4");
        let binding = bind_inventory(root.path(), "1.19.4", &complete);
        let tree_ref = format!("{commit}^{{tree}}");
        let tree_oid = git(root.path(), &["rev-parse", &tree_ref]);
        assert!(
            project_frozen_artifacts(root.path(), &binding, &tree_oid, &context("1.19.4")).is_err()
        );
        let (mut extra_output, parsed) =
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).unwrap();
        release_version_in_projected(&mut extra_output);
        let copied = extra_output[RESOURCE].clone();
        extra_output.insert("src/main/resources/unreviewed.txt".to_owned(), copied);
        assert!(verify_frozen_outputs(&parsed, &extra_output).is_err());

        let mut missing = inventory(root.path(), &commit, "1.19.4");
        missing.files.remove("gradle/wrapper/gradle-wrapper.jar");
        let binding = bind_inventory(root.path(), "1.19.4", &missing);
        assert!(
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).is_err()
        );

        let mut unowned = inventory(root.path(), &commit, "1.19.4");
        let copied = unowned.files[JAVA].clone();
        unowned.files.insert("Build/hidden.java".to_owned(), copied);
        let binding = bind_inventory(root.path(), "1.19.4", &unowned);
        assert!(
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).is_err()
        );

        let mut generated_input = inventory(root.path(), &commit, "1.19.4");
        generated_input
            .files
            .get_mut(JAVA)
            .unwrap()
            .source_repo_path =
            "platform/minecraft/Mc-Version/1.19.4/src/main/java/example/Proof.java".to_owned();
        let binding = bind_inventory(root.path(), "1.19.4", &generated_input);
        assert!(
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).is_err()
        );
    }

    #[test]
    fn duplicate_json_key_is_rejected_even_if_a_map_parser_would_collapse_it() {
        let (root, commit) = setup();
        let base = inventory(root.path(), &commit, "1.19.4");
        let binding = bind_inventory(root.path(), "1.19.4", &base);
        let path = root.path().join(&binding.inventory_path);
        let text = fs::read_to_string(&path).unwrap();
        let duplicate = text.replacen(
            "\"schema\": \"sfm:frozen_release_sources@1\",",
            "\"schema\": \"sfm:frozen_release_sources@1\",\n  \"schema\": \"sfm:frozen_release_sources@1\",",
            1,
        );
        assert_ne!(duplicate, text);
        fs::write(&path, duplicate.as_bytes()).unwrap();
        let binding = FrozenSourceBinding {
            inventory_sha256: sha256(duplicate.as_bytes())
                .strip_prefix("sha256:")
                .unwrap()
                .to_owned(),
            ..binding
        };
        assert!(
            project_frozen_artifacts(root.path(), &binding, &commit, &context("1.19.4")).is_err()
        );
    }
}
