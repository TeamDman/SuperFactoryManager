//! Read-only authoring preview for a tag-independent, commit-frozen release.
//!
//! The caller supplies an explicit, reviewed output-to-physical-input map.
//! Existing `ProjectedArtifact.source_path` values are sometimes logical paths
//! within a tag or import, not paths in the authored repository. Guessing that
//! ownership would make a frozen inventory appear stronger than it is. A later
//! CLI can seed this map from development selection and require review of any
//! ambiguous owners; this engine never promotes a generated project or writes
//! an inventory.

use super::context::ProjectionContext;
use super::frozen_release::FrozenSourceFile;
use super::frozen_release::FrozenSourceInventory;
use super::frozen_release::FrozenTransform;
use super::frozen_release::MAX_INVENTORY_BYTES;
use super::frozen_release::SCHEMA;
use super::frozen_release::validate_git_commit_root;
use super::frozen_release::validate_inventory;
use super::frozen_release::verify_frozen_outputs;
use super::inputs::render_java_artifact;
use super::manifest::FrozenSourceBinding;
use super::project_layout::append_project_name_override;
use super::promotion::validate_relative_path;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::release_version::apply_release_mod_version;
use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Write;
use std::path::Path;
use std::process::Stdio;

const MAX_SOURCE_BLOB_BYTES: usize = 128 * 1024 * 1024;

/// One reviewed project output and its exact authored Git input. This path is
/// physical repository ownership, not the logical `source_path` recorded in a
/// prior generated project's provenance manifest.
#[derive(Clone, Debug)]
pub struct FrozenAuthoringInput {
    pub source_repo_path: String,
    pub overlay: Option<String>,
    pub transform: FrozenTransform,
}

/// Inputs to a pure preview. `context` and `excluded_paths` must reflect the
/// intended candidate preset; the eventual manifest and M6.8 replay enforce
/// that relationship again after the ten inventories are reviewed.
#[derive(Clone, Debug)]
pub struct FrozenAuthoringRequest {
    pub target_id: String,
    pub source_commit: String,
    pub release_mod_version: String,
    pub context: ProjectionContext,
    pub excluded_paths: Vec<String>,
    pub explicit_inputs: BTreeMap<String, String>,
    pub files: BTreeMap<String, FrozenAuthoringInput>,
}

/// Canonical bytes and the SHA binding to copy into a new preset only after
/// human review. Nothing is written by `preview_frozen_inventory`.
#[derive(Debug)]
pub struct FrozenAuthoringPreview {
    pub inventory: FrozenSourceInventory,
    pub canonical_json: String,
    pub binding: FrozenSourceBinding,
}

/// Construct a canonical frozen inventory using only blobs from one exact
/// authored commit. No generated project, inventory or manifest is written.
///
/// # Errors
///
/// Rejects an unsafe or incomplete map, non-blob Git input, invalid Liquid
/// output, malformed release-version property or frozen-schema violation.
pub fn preview_frozen_inventory(
    repo_root: &Path,
    request: &FrozenAuthoringRequest,
) -> Result<FrozenAuthoringPreview> {
    ensure!(
        is_lower_hex(&request.source_commit, 40),
        "frozen authoring requires an exact lowercase authored commit SHA"
    );
    ensure!(
        request.context.preset == format!("released-{}", request.release_mod_version)
            && !request.release_mod_version.contains("-dev."),
        "frozen authoring requires a matching released-<version> context"
    );
    let inventory_path = format!(
        "platform/minecraft/frozen-releases/{}/{}/inventory.json",
        request.context.preset, request.target_id
    );
    validate_relative_path(&inventory_path)?;
    validate_request_paths(request)?;

    let root = fs::canonicalize(repo_root).wrap_err("cannot resolve frozen authoring root")?;
    validate_git_commit_root(&root, &request.source_commit)?;
    let tree = read_tree_index(&root, &request.source_commit)?;
    let needed_blobs = request
        .files
        .values()
        .map(|input| {
            tree.get(&input.source_repo_path)
                .map(|entry| entry.oid.clone())
                .ok_or_else(|| {
                    eyre::eyre!(
                        "authored commit lacks exact source '{}'",
                        input.source_repo_path
                    )
                })
        })
        .collect::<Result<BTreeSet<_>>>()?;
    let blobs = read_git_blobs(&root, &needed_blobs)?;
    let mut files = BTreeMap::new();
    let mut artifacts = BTreeMap::new();
    for (output_path, input) in &request.files {
        let entry = &tree[&input.source_repo_path];
        let bytes = blobs[&entry.oid].clone();
        let mut artifact = ProjectedArtifact {
            source_path: input.source_repo_path.clone(),
            source_bytes: bytes.clone(),
            output_bytes: bytes.clone(),
            overlay: input.overlay.clone(),
        };
        if input.transform == FrozenTransform::Java {
            render_java_artifact(output_path, &mut artifact, &request.context)?;
        }
        files.insert(
            output_path.clone(),
            FrozenSourceFile {
                source_repo_path: input.source_repo_path.clone(),
                git_mode: entry.mode.clone(),
                blob_oid: entry.oid.clone(),
                source_sha256: sha256(&bytes),
                output_sha256: String::new(),
                overlay: input.overlay.clone(),
                transform: input.transform,
            },
        );
        artifacts.insert(output_path.clone(), artifact);
    }
    if request
        .files
        .get("settings.gradle")
        .is_some_and(|input| input.transform == FrozenTransform::TargetSettings)
    {
        append_project_name_override(&mut artifacts, &request.target_id)?;
    }
    apply_release_mod_version(&mut artifacts, &request.release_mod_version)?;
    for (path, file) in &mut files {
        file.output_sha256 = sha256(&artifacts[path].output_bytes);
    }

    let inventory = FrozenSourceInventory {
        schema: SCHEMA.to_owned(),
        target_id: request.target_id.clone(),
        source_commit: request.source_commit.clone(),
        context: request.context.clone(),
        excluded_paths: request.excluded_paths.clone(),
        files,
    };
    finish_preview(request, inventory_path, inventory, &artifacts)
}

fn finish_preview(
    request: &FrozenAuthoringRequest,
    inventory_path: String,
    inventory: FrozenSourceInventory,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<FrozenAuthoringPreview> {
    let mut canonical_json = facet_json::to_string_pretty(&inventory)?;
    canonical_json.push('\n');
    ensure!(
        canonical_json.len() as u64 <= MAX_INVENTORY_BYTES,
        "frozen authoring inventory exceeds the replay size limit"
    );
    let digest = sha256(canonical_json.as_bytes());
    let inventory_sha256 = digest
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre::eyre!("invalid frozen authoring SHA-256 digest"))?;
    let binding = FrozenSourceBinding {
        target_id: request.target_id.clone(),
        inventory_path,
        inventory_sha256: inventory_sha256.to_owned(),
    };
    validate_inventory(
        &inventory,
        &binding,
        &request.source_commit,
        &request.context,
    )?;
    verify_frozen_outputs(&inventory, artifacts)?;
    Ok(FrozenAuthoringPreview {
        inventory,
        canonical_json,
        binding,
    })
}

fn validate_request_paths(request: &FrozenAuthoringRequest) -> Result<()> {
    ensure!(!request.files.is_empty(), "frozen authoring map is empty");
    let mut output_casefold = BTreeSet::new();
    for (output, input) in &request.files {
        validate_relative_path(output)?;
        ensure!(
            output_casefold.insert(output.to_ascii_lowercase()),
            "frozen authoring output '{output}' case-collides"
        );
        validate_relative_path(&input.source_repo_path)?;
        let lower_source = input.source_repo_path.to_ascii_lowercase();
        ensure!(
            input.source_repo_path.starts_with("platform/minecraft/")
                && !lower_source.starts_with("platform/minecraft/mc-version/")
                && !lower_source.starts_with("platform/minecraft/frozen-releases/"),
            "frozen authoring source for '{output}' must be authored Minecraft input"
        );
        let java_output = Path::new(output)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"));
        match input.transform {
            FrozenTransform::Java => ensure!(
                output.starts_with("src/") && java_output,
                "Java transform requires a src/... .java output"
            ),
            FrozenTransform::TargetSettings => ensure!(
                output == "settings.gradle",
                "target-settings transform requires settings.gradle"
            ),
            FrozenTransform::Copy => ensure!(
                !java_output,
                "Java output '{output}' requires the Java transform"
            ),
        }
    }
    for excluded in &request.excluded_paths {
        validate_relative_path(excluded)?;
        ensure!(
            !output_casefold.contains(&excluded.to_ascii_lowercase()),
            "disabled feature output '{excluded}' is present in authoring map"
        );
    }
    for (output, source) in &request.explicit_inputs {
        ensure!(
            request
                .files
                .get(output)
                .is_some_and(|input| &input.source_repo_path == source),
            "enabled feature output '{output}' does not use its declared source '{source}'"
        );
    }
    Ok(())
}

#[derive(Clone)]
struct GitTreeEntry {
    mode: String,
    oid: String,
}

fn read_tree_index(root: &Path, commit: &str) -> Result<BTreeMap<String, GitTreeEntry>> {
    let output = frozen_git_command(root)
        .args(["ls-tree", "-r", "-z", commit, "--", "platform/minecraft"])
        .output()
        .wrap_err("cannot list authored Minecraft Git tree")?;
    ensure!(
        output.status.success(),
        "cannot list authored Minecraft Git tree"
    );
    let mut entries = BTreeMap::new();
    for record in output
        .stdout
        .split(|byte| *byte == 0)
        .filter(|r| !r.is_empty())
    {
        let tab = record
            .iter()
            .position(|byte| *byte == b'\t')
            .ok_or_else(|| eyre::eyre!("malformed authored Git tree entry"))?;
        let (header, path_with_tab) = record.split_at(tab);
        let path = &path_with_tab[1..];
        let header = std::str::from_utf8(header)?;
        let path = std::str::from_utf8(path)?;
        let mut fields = header.split_whitespace();
        let (Some(mode), Some(kind), Some(oid), None) =
            (fields.next(), fields.next(), fields.next(), fields.next())
        else {
            eyre::bail!("malformed authored Git tree identity");
        };
        if kind != "blob" || !matches!(mode, "100644" | "100755") {
            continue;
        }
        ensure!(is_lower_hex(oid, 40), "invalid authored Git blob ID");
        ensure!(
            entries
                .insert(
                    path.to_owned(),
                    GitTreeEntry {
                        mode: mode.to_owned(),
                        oid: oid.to_owned(),
                    },
                )
                .is_none(),
            "duplicate authored Git path '{path}'"
        );
    }
    Ok(entries)
}

fn read_git_blobs(root: &Path, oids: &BTreeSet<String>) -> Result<BTreeMap<String, Vec<u8>>> {
    let mut child = frozen_git_command(root)
        .args(["cat-file", "--batch"])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .wrap_err("cannot start authored Git blob reader")?;
    let mut stdin = child.stdin.take().expect("piped Git stdin");
    let mut stdout = BufReader::new(child.stdout.take().expect("piped Git stdout"));
    let result = read_git_blob_batch(&mut stdin, &mut stdout, oids);
    drop(stdin);
    if result.is_err() {
        let _ = child.kill();
    }
    let status = child.wait()?;
    ensure!(
        status.success() || result.is_err(),
        "authored Git blob reader failed"
    );
    result
}

fn read_git_blob_batch(
    stdin: &mut impl Write,
    stdout: &mut impl BufRead,
    oids: &BTreeSet<String>,
) -> Result<BTreeMap<String, Vec<u8>>> {
    let mut blobs = BTreeMap::new();
    for oid in oids {
        stdin.write_all(oid.as_bytes())?;
        stdin.write_all(b"\n")?;
        stdin.flush()?;
        let mut header = String::new();
        ensure!(
            stdout.read_line(&mut header)? != 0,
            "authored Git blob reader ended early"
        );
        let mut fields = header.split_whitespace();
        let (Some(found_oid), Some("blob"), Some(size), None) =
            (fields.next(), fields.next(), fields.next(), fields.next())
        else {
            eyre::bail!("authored Git object is not a blob");
        };
        ensure!(
            found_oid == oid,
            "authored Git blob ID changed while reading"
        );
        let size = size.parse::<usize>()?;
        ensure!(
            size <= MAX_SOURCE_BLOB_BYTES,
            "authored Git blob is too large"
        );
        let mut bytes = vec![0; size];
        stdout.read_exact(&mut bytes)?;
        let mut separator = [0];
        stdout.read_exact(&mut separator)?;
        ensure!(
            separator == [b'\n'],
            "malformed authored Git blob separator"
        );
        blobs.insert(oid.clone(), bytes);
    }
    Ok(blobs)
}

fn is_lower_hex(value: &str, length: usize) -> bool {
    value.len() == length
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || matches!(byte, b'a'..=b'f'))
}

#[cfg(test)]
mod tests {
    use super::super::frozen_release::project_frozen_artifacts;
    use super::super::sync::ProjectionIdentity;
    use super::super::sync::SyncMode;
    use super::super::sync::sync_projection;
    use super::*;
    use std::env;
    use std::process::Command;

    const JAVA: &str = "src/main/java/example/Proof.java";
    const EXTRA: &str = "src/main/java/example/Extra.java";
    const RESOURCE: &str = "src/main/resources/assets/sfm/frozen.txt";
    const TEST_FIXTURE: &str = "src/test/resources/frozen.json";
    const EXAMPLE: &str = "examples/frozen.sfml";
    const PRESET: &str = "released-9.99.99-fixture";

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
        for target in ["1.19.4", "1.20"] {
            let prefix = format!("platform/minecraft/freeze-fixture/{target}");
            for (path, bytes) in [
                (EXAMPLE, b"EVERY 20 TICKS DO END\n".as_slice()),
                ("build.gradle", b"plugins {}\n".as_slice()),
                (
                    "settings.gradle",
                    b"rootProject.name = 'old-name'\n".as_slice(),
                ),
                (
                    "gradle.properties",
                    b"minecraft_version=fixture\nmod_version=4.34.0\n".as_slice(),
                ),
                ("gradlew", b"#!/bin/sh\n".as_slice()),
                ("gradlew.bat", b"@echo off\r\n".as_slice()),
                ("sfm-toolchain.lock.json", b"{}\n".as_slice()),
                (
                    "gradle/wrapper/gradle-wrapper.jar",
                    b"synthetic wrapper bytes".as_slice(),
                ),
                (
                    "gradle/wrapper/gradle-wrapper.properties",
                    b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n".as_slice(),
                ),
            ] {
                write(root.path(), &format!("{prefix}/{path}"), bytes);
            }
        }
        git(root.path(), &["add", "--", "platform/minecraft"]);
        git(
            root.path(),
            &["commit", "--quiet", "-m", "authored source A"],
        );
        let commit = git(root.path(), &["rev-parse", "HEAD"]);
        (root, commit)
    }

    fn request(commit: &str, target: &str) -> FrozenAuthoringRequest {
        let mut files = BTreeMap::new();
        let mut add = |output: &str, source: String, transform: FrozenTransform| {
            files.insert(
                output.to_owned(),
                FrozenAuthoringInput {
                    source_repo_path: source,
                    overlay: None,
                    transform,
                },
            );
        };
        add(
            JAVA,
            format!("platform/minecraft/{JAVA}"),
            FrozenTransform::Java,
        );
        if target == "1.20" {
            add(
                EXTRA,
                format!("platform/minecraft/version-sources/{target}/{EXTRA}"),
                FrozenTransform::Java,
            );
        }
        for path in [RESOURCE, TEST_FIXTURE] {
            add(
                path,
                format!("platform/minecraft/{path}"),
                FrozenTransform::Copy,
            );
        }
        for path in [
            EXAMPLE,
            "build.gradle",
            "settings.gradle",
            "gradle.properties",
            "gradlew",
            "gradlew.bat",
            "sfm-toolchain.lock.json",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
        ] {
            let transform = if path == "settings.gradle" && target == "1.20" {
                FrozenTransform::TargetSettings
            } else {
                FrozenTransform::Copy
            };
            add(
                path,
                format!("platform/minecraft/freeze-fixture/{target}/{path}"),
                transform,
            );
        }
        FrozenAuthoringRequest {
            target_id: target.to_owned(),
            source_commit: commit.to_owned(),
            release_mod_version: "9.99.99-fixture".to_owned(),
            context: ProjectionContext {
                minecraft_version: target.to_owned(),
                preset: PRESET.to_owned(),
                features: BTreeMap::new(),
                targets: BTreeMap::from([
                    ("mc_1_19_4".to_owned(), target == "1.19.4"),
                    ("mc_1_20".to_owned(), target == "1.20"),
                    ("forge".to_owned(), true),
                ]),
            },
            excluded_paths: if target == "1.19.4" {
                vec![EXTRA.to_owned()]
            } else {
                vec![]
            },
            explicit_inputs: if target == "1.20" {
                BTreeMap::from([(
                    EXTRA.to_owned(),
                    format!("platform/minecraft/version-sources/{target}/{EXTRA}"),
                )])
            } else {
                BTreeMap::new()
            },
            files,
        }
    }

    #[test]
    fn two_target_preview_replays_from_commit_after_later_authored_edit() {
        let (root, commit) = setup();
        let projected = tempfile::tempdir().unwrap();
        let mut previews = Vec::new();
        for target in ["1.19.4", "1.20"] {
            let request = request(&commit, target);
            let preview = preview_frozen_inventory(root.path(), &request).unwrap();
            let again = preview_frozen_inventory(root.path(), &request).unwrap();
            assert_eq!(preview.canonical_json, again.canonical_json);
            assert_eq!(
                preview.binding.inventory_sha256,
                again.binding.inventory_sha256
            );
            if previews.is_empty() {
                assert!(
                    !root
                        .path()
                        .join("platform/minecraft/frozen-releases")
                        .exists()
                );
            }
            assert_eq!(
                preview.inventory.files.contains_key(EXTRA),
                target == "1.20"
            );
            assert_eq!(
                preview.inventory.files[JAVA].source_repo_path,
                format!("platform/minecraft/{JAVA}")
            );
            write(
                root.path(),
                &preview.binding.inventory_path,
                preview.canonical_json.as_bytes(),
            );
            let (mut artifacts, parsed) =
                project_frozen_artifacts(root.path(), &preview.binding, &commit, &request.context)
                    .unwrap();
            apply_release_mod_version(&mut artifacts, &request.release_mod_version).unwrap();
            verify_frozen_outputs(&parsed, &artifacts).unwrap();
            let identity = ProjectionIdentity {
                target_id: target.to_owned(),
                minecraft_version: target.to_owned(),
                preset_id: PRESET.to_owned(),
                preset_definition_identity: format!("blake3:{}", "a".repeat(64)),
            };
            let output = projected.path().join(target);
            sync_projection(&output, &identity, &artifacts, SyncMode::Apply).unwrap();
            assert!(
                fs::read_to_string(output.join("gradle.properties"))
                    .unwrap()
                    .contains("mod_version=9.99.99-fixture")
            );
            previews.push((request, preview, artifacts, identity, output));
        }

        write(
            root.path(),
            "platform/minecraft/src/main/java/example/Proof.java",
            b"class Proof { int later = 1; }\n",
        );
        git(root.path(), &["add", "--", "platform/minecraft"]);
        git(
            root.path(),
            &["commit", "--quiet", "-m", "authored source B"],
        );
        for (request, preview, before, identity, output) in previews {
            let (mut after, parsed) =
                project_frozen_artifacts(root.path(), &preview.binding, &commit, &request.context)
                    .unwrap();
            apply_release_mod_version(&mut after, &request.release_mod_version).unwrap();
            verify_frozen_outputs(&parsed, &after).unwrap();
            assert_eq!(after, before);
            assert!(
                !sync_projection(&output, &identity, &after, SyncMode::Check)
                    .unwrap()
                    .needs_write()
            );
        }
    }

    #[test]
    fn preview_rejects_generated_ambiguous_and_excluded_inputs_without_writes() {
        let (root, commit) = setup();
        let mut candidate = request(&commit, "1.20");
        candidate.files.get_mut(JAVA).unwrap().source_repo_path =
            format!("platform/minecraft/mc-version/1.20/{JAVA}");
        assert!(preview_frozen_inventory(root.path(), &candidate).is_err());
        candidate = request(&commit, "1.20");
        candidate.excluded_paths = vec![JAVA.to_owned()];
        assert!(preview_frozen_inventory(root.path(), &candidate).is_err());
        candidate = request(&commit, "1.20");
        candidate.explicit_inputs.insert(
            JAVA.to_owned(),
            format!("platform/minecraft/version-sources/1.20/{JAVA}"),
        );
        assert!(preview_frozen_inventory(root.path(), &candidate).is_err());
        assert!(
            !root
                .path()
                .join("platform/minecraft/frozen-releases")
                .exists()
        );
        assert!(git(root.path(), &["status", "--porcelain"]).is_empty());
    }

    #[test]
    fn local_replace_ref_cannot_redirect_frozen_authoring_or_replay() {
        let (root, commit_a) = setup();
        let original = fs::read(root.path().join(format!("platform/minecraft/{JAVA}"))).unwrap();
        write(
            root.path(),
            &format!("platform/minecraft/{JAVA}"),
            b"class Proof { int substituted = 1; }\n",
        );
        git(root.path(), &["add", "--", "platform/minecraft"]);
        git(root.path(), &["commit", "--quiet", "-m", "source B"]);
        let commit_b = git(root.path(), &["rev-parse", "HEAD"]);
        git(root.path(), &["replace", &commit_a, &commit_b]);

        let request = request(&commit_a, "1.19.4");
        let preview = preview_frozen_inventory(root.path(), &request).unwrap();
        assert_eq!(
            preview.inventory.files[JAVA].source_sha256,
            sha256(&original)
        );
        write(
            root.path(),
            &preview.binding.inventory_path,
            preview.canonical_json.as_bytes(),
        );
        let (mut replay, parsed) =
            project_frozen_artifacts(root.path(), &preview.binding, &commit_a, &request.context)
                .unwrap();
        apply_release_mod_version(&mut replay, &request.release_mod_version).unwrap();
        verify_frozen_outputs(&parsed, &replay).unwrap();
        assert_eq!(replay[JAVA].source_bytes, original);
    }

    #[test]
    fn inherited_git_repository_overrides_cannot_redirect_preview() {
        let (root, commit) = setup();
        let bogus = tempfile::tempdir().unwrap();
        let output = Command::new(env::current_exe().unwrap())
            .args([
                "--exact",
                "source_projection::frozen_authoring::tests::frozen_child_probe_from_env",
                "--nocapture",
            ])
            .env("SFM_FROZEN_TEST_REPO_ROOT", root.path())
            .env("SFM_FROZEN_TEST_COMMIT", commit)
            .env("GIT_DIR", bogus.path())
            .env("GIT_WORK_TREE", bogus.path())
            .env("GIT_CONFIG_COUNT", "1")
            .env("GIT_CONFIG_KEY_0", "core.repositoryformatversion")
            .env("GIT_CONFIG_VALUE_0", "900")
            .output()
            .unwrap();
        assert!(
            output.status.success()
                && String::from_utf8_lossy(&output.stdout).contains("FROZEN_PROBE_OK"),
            "hostile-env child failed:\nstdout: {}\nstderr: {}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }

    #[test]
    fn frozen_child_probe_from_env() {
        let Ok(root) = env::var("SFM_FROZEN_TEST_REPO_ROOT") else {
            return;
        };
        let commit = env::var("SFM_FROZEN_TEST_COMMIT").unwrap();
        let candidate = request(&commit, "1.19.4");
        let preview = preview_frozen_inventory(Path::new(&root), &candidate).unwrap();
        assert_eq!(preview.inventory.source_commit, commit);
        println!("FROZEN_PROBE_OK");
    }
}
