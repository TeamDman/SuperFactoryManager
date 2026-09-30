//! Read-only authoring preview for a tag-independent, commit-frozen release.
//!
//! The caller may supply a reviewed output-to-physical-input map or seed one
//! from a current-development selection. `ProjectedArtifact.source_path` may
//! be logical provenance within an import, so it is never used as an authored
//! Git path. Neither route writes a project or inventory.

use super::candidate_lock::checked_file;
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
use super::manifest::BaselineKind;
use super::manifest::FrozenSourceBinding;
use super::manifest::released_preset_id;
use super::project_layout::append_project_name_override;
use super::project_layout::validate_target_project;
use super::promotion::validate_relative_path;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::release_version::apply_release_mod_version;
use super::selection::ProjectionSelection;
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
use std::path::PathBuf;
use std::process::Stdio;

const MAX_SOURCE_BLOB_BYTES: usize = 128 * 1024 * 1024;

/// One reviewed project output and its exact authored Git input. This path is
/// physical repository ownership, not the logical `source_path` recorded in a
/// prior generated project's provenance manifest.
#[derive(Clone, Debug, Eq, PartialEq)]
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

/// Resolved, ordered input roots used for a current-development projection.
/// Each root must be inside the same authored Git worktree as `repo_root`.
#[derive(Debug)]
pub struct FrozenSelectionRoots {
    pub primary_src_root: PathBuf,
    pub source_overlays: Vec<(String, PathBuf)>,
    pub gradle_project_root: PathBuf,
    pub gradle_overlays: Vec<(String, PathBuf)>,
}

/// Require the selected manifest's working bytes to equal the blob at the
/// authored source commit. A working-tree-only selection must not certify a
/// frozen inventory whose inputs are attributed to that commit.
///
/// # Errors
///
/// Rejects an unsafe path, absent or nonregular committed manifest, or any
/// uncommitted selection-definition change.
pub fn verify_committed_selection_manifest(
    repo_root: &Path,
    source_commit: &str,
    manifest_repo_path: &str,
    working_bytes: &[u8],
) -> Result<()> {
    ensure!(
        is_lower_hex(source_commit, 40),
        "selection manifest verification requires an exact authored commit SHA"
    );
    validate_relative_path(manifest_repo_path)?;
    ensure!(
        manifest_repo_path.starts_with("platform/minecraft/"),
        "selection manifest must be an authored Minecraft file"
    );
    let root = fs::canonicalize(repo_root).wrap_err("cannot resolve authored repository root")?;
    validate_git_commit_root(&root, source_commit)?;
    let tree = read_tree_index(&root, source_commit)?;
    let entry = tree.get(manifest_repo_path).ok_or_else(|| {
        eyre::eyre!("authored commit lacks exact selection manifest '{manifest_repo_path}'")
    })?;
    let blobs = read_git_blobs(&root, &BTreeSet::from([entry.oid.clone()]))?;
    ensure!(
        blobs[&entry.oid] == working_bytes,
        "selection manifest working bytes differ from exact authored commit"
    );
    Ok(())
}

/// Seed a complete physical-owner map from one selected development target,
/// then preview it against an exact authored commit. Development output bytes
/// are a membership witness; the released context, target-settings override,
/// and release-version rewrite intentionally render a new candidate. A proposed map, when
/// supplied, must agree on every output and every owner. This lets a reviewer
/// reject an incomplete or hand-edited inventory before accepting its digest.
///
/// # Errors
///
/// Rejects missing or ambiguous physical routes, changed working-tree bytes,
/// uncommitted source drift, incomplete proposed membership, or any ordinary
/// frozen-preview validation failure.
#[expect(
    clippy::too_many_arguments,
    reason = "the explicit commit, target, selection and proposed map are independent review inputs"
)]
pub fn preview_frozen_inventory_from_selection(
    repo_root: &Path,
    target_id: &str,
    selection: &ProjectionSelection,
    roots: &FrozenSelectionRoots,
    selected_artifacts: &BTreeMap<String, ProjectedArtifact>,
    source_commit: &str,
    release_mod_version: &str,
    proposed_files: Option<&BTreeMap<String, FrozenAuthoringInput>>,
) -> Result<FrozenAuthoringPreview> {
    let release_preset_id = released_preset_id(release_mod_version)?;
    ensure!(
        selection.frozen_source.is_none() && selection.frozen_source_commit.is_none(),
        "frozen authoring requires a current-development selection"
    );
    ensure!(
        selection.release_baseline.as_ref().is_none_or(|binding| {
            binding.kind == BaselineKind::DevelopmentHead && binding.target_id == target_id
        }),
        "frozen authoring cannot seed from a release-tag or foreign-target baseline"
    );
    let files = derive_physical_owners(repo_root, target_id, selection, roots, selected_artifacts)?;
    if let Some(proposed) = proposed_files {
        ensure!(
            proposed.len() == files.len() && proposed.keys().eq(files.keys()),
            "proposed frozen inventory output membership differs from selected development output"
        );
        ensure!(
            proposed == &files,
            "proposed frozen inventory physical owners differ from selected development owners"
        );
    }
    let mut context = selection.context.clone();
    context.preset = release_preset_id;
    let request = FrozenAuthoringRequest {
        target_id: target_id.to_owned(),
        source_commit: source_commit.to_owned(),
        release_mod_version: release_mod_version.to_owned(),
        context,
        excluded_paths: selection.excluded_paths.iter().cloned().collect(),
        explicit_inputs: selection.explicit_inputs.clone(),
        files,
    };
    let (preview, rendered) = preview_frozen_inventory_and_artifacts(repo_root, &request)?;
    validate_target_project(&rendered, target_id, &selection.context.minecraft_version)?;
    ensure!(
        preview.inventory.files.len() == selected_artifacts.len()
            && preview.inventory.files.keys().eq(selected_artifacts.keys()),
        "frozen preview omitted selected development output membership"
    );
    for (output, artifact) in selected_artifacts {
        ensure!(
            preview.inventory.files[output].source_sha256 == sha256(&artifact.source_bytes),
            "selected source '{output}' differs from its exact authored commit blob"
        );
    }
    Ok(preview)
}

#[expect(
    clippy::too_many_lines,
    reason = "all route candidates and the single-owner byte check are audited together"
)]
fn derive_physical_owners(
    repo_root: &Path,
    target_id: &str,
    selection: &ProjectionSelection,
    roots: &FrozenSelectionRoots,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<BTreeMap<String, FrozenAuthoringInput>> {
    ensure!(
        !artifacts.is_empty(),
        "selected development output is empty"
    );
    let root = fs::canonicalize(repo_root).wrap_err("cannot resolve authored repository root")?;
    let roots = FrozenSelectionRoots {
        primary_src_root: fs::canonicalize(&roots.primary_src_root)
            .wrap_err("cannot resolve selected primary source root")?,
        source_overlays: roots
            .source_overlays
            .iter()
            .map(|(name, path)| Ok((name.clone(), fs::canonicalize(path)?)))
            .collect::<Result<_>>()?,
        gradle_project_root: fs::canonicalize(&roots.gradle_project_root)
            .wrap_err("cannot resolve selected Gradle project root")?,
        gradle_overlays: roots
            .gradle_overlays
            .iter()
            .map(|(name, path)| Ok((name.clone(), fs::canonicalize(path)?)))
            .collect::<Result<_>>()?,
    };
    let mut files = BTreeMap::new();
    for (output, artifact) in artifacts {
        validate_relative_path(output)?;
        let mut candidates = Vec::new();
        let transform = if output.starts_with("src/")
            && Path::new(output)
                .extension()
                .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            FrozenTransform::Java
        } else if output == "settings.gradle" {
            // The canonical pilot has a dynamic name in its raw settings.
            // Every frozen candidate needs one final literal target name.
            FrozenTransform::TargetSettings
        } else {
            FrozenTransform::Copy
        };
        if let Some(relative) = output.strip_prefix("src/") {
            candidates.push((None, root_child(&root, &roots.primary_src_root, relative)?));
            for (name, overlay_root) in &roots.source_overlays {
                candidates.push((
                    Some(name.clone()),
                    root_child(&root, overlay_root, relative)?,
                ));
            }
            if let Some(input) = selection.explicit_inputs.get(output) {
                candidates.push((Some("feature".to_owned()), input.clone()));
            }
            if let Some(binding) = &selection.release_baseline {
                let target = &binding.target_id;
                candidates.push((
                    Some(format!("development-head-{target}")),
                    format!("platform/minecraft/development-baselines/{target}/overlays/{output}"),
                ));
                if let Some(selected) = binding.post_baseline_version_sources.get(output) {
                    candidates.push((
                        Some(format!("version-source-{target}")),
                        selected.source_path.clone(),
                    ));
                }
                if binding.post_baseline_test_sources.contains_key(output) {
                    candidates.push((
                        Some("development-test-portability".to_owned()),
                        format!("platform/minecraft/{output}"),
                    ));
                }
            }
        } else if output.starts_with("examples/")
            || output.starts_with("docs/architecture/fixtures/")
        {
            candidates.push((
                Some("development-fixtures".to_owned()),
                format!(
                    "platform/minecraft/development-baselines/{target_id}/project-fixtures/{output}"
                ),
            ));
        } else {
            candidates.push((
                Some("gradle_project".to_owned()),
                root_child(&root, &roots.gradle_project_root, output)?,
            ));
            for (name, overlay_root) in &roots.gradle_overlays {
                candidates.push((Some(name.clone()), root_child(&root, overlay_root, output)?));
            }
            if let Some(binding) = &selection.release_baseline
                && binding.post_baseline_gradle_sources.contains_key(output)
            {
                candidates.push((
                    Some("development-gradle-portability".to_owned()),
                    format!("platform/minecraft/development-overlays/{target_id}/{output}"),
                ));
            }
        }
        let matching = candidates
            .into_iter()
            .filter(|(label, _)| label.as_deref() == artifact.overlay.as_deref())
            .map(|(_, path)| path)
            .collect::<Vec<_>>();
        ensure!(
            matching.len() == 1,
            "selected output '{output}' has {} physical owner routes; expected exactly one",
            matching.len()
        );
        let source_repo_path = matching.into_iter().next().unwrap();
        validate_relative_path(&source_repo_path)?;
        let source = checked_file(&root, &source_repo_path)?;
        let bytes = fs::read(&source).wrap_err_with(|| {
            format!("cannot read physical owner '{source_repo_path}' for '{output}'")
        })?;
        ensure!(
            bytes == artifact.source_bytes,
            "physical owner '{source_repo_path}' differs from final source bytes for '{output}'"
        );
        files.insert(
            output.clone(),
            FrozenAuthoringInput {
                source_repo_path,
                overlay: artifact.overlay.clone(),
                transform,
            },
        );
    }
    Ok(files)
}

fn root_child(repo_root: &Path, input_root: &Path, relative: &str) -> Result<String> {
    let suffix = input_root.strip_prefix(repo_root).wrap_err_with(|| {
        format!(
            "selected input root '{}' escapes authored repository",
            input_root.display()
        )
    })?;
    let suffix = suffix
        .components()
        .map(|part| match part {
            std::path::Component::Normal(name) => name
                .to_str()
                .map(str::to_owned)
                .ok_or_else(|| eyre::eyre!("selected input root is not UTF-8")),
            _ => Err(eyre::eyre!(
                "selected input root is not a regular relative path"
            )),
        })
        .collect::<Result<Vec<_>>>()?
        .join("/");
    Ok(format!("{suffix}/{relative}"))
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
    preview_frozen_inventory_and_artifacts(repo_root, request).map(|(preview, _)| preview)
}

fn preview_frozen_inventory_and_artifacts(
    repo_root: &Path,
    request: &FrozenAuthoringRequest,
) -> Result<(FrozenAuthoringPreview, BTreeMap<String, ProjectedArtifact>)> {
    let release_preset_id = released_preset_id(&request.release_mod_version)?;
    ensure!(
        is_lower_hex(&request.source_commit, 40),
        "frozen authoring requires an exact lowercase authored commit SHA"
    );
    ensure!(
        request.context.preset == release_preset_id,
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
    let preview = finish_preview(request, inventory_path, inventory, &artifacts)?;
    Ok((preview, artifacts))
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
                !(output.starts_with("src/") && java_output),
                "source Java output '{output}' requires the Java transform"
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
    use super::super::manifest::SourceProjectionManifest;
    use super::super::manifest::VersionSourceSelection;
    use super::super::selection::select;
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
        for target in ["1.19.2", "1.19.4", "1.20", "1.21.0"] {
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
            let minecraft_version = if target == "1.21.0" { "1.21" } else { target };
            write(
                root.path(),
                &format!("{prefix}/gradle.properties"),
                format!("minecraft_version={minecraft_version}\nmod_version=4.34.0\n").as_bytes(),
            );
            if target == "1.19.2" {
                write(
                    root.path(),
                    &format!("{prefix}/settings.gradle"),
                    b"rootProject.name = \"sfm-${sfmVersionLabel}\"\n",
                );
            }
            write(
                root.path(),
                &format!(
                    "platform/minecraft/development-baselines/{target}/project-fixtures/{EXAMPLE}"
                ),
                b"EVERY 20 TICKS DO END\n",
            );
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
                minecraft_version: if target == "1.21.0" {
                    "1.21".to_owned()
                } else {
                    target.to_owned()
                },
                preset: PRESET.to_owned(),
                environment: "dev".to_owned(),
                projection_key: format!("sfm-fixture/mc-{target}"),
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

    fn selected_fixture(
        root: &Path,
        commit: &str,
        target: &str,
    ) -> (
        ProjectionSelection,
        FrozenSelectionRoots,
        BTreeMap<String, ProjectedArtifact>,
    ) {
        let manifest = SourceProjectionManifest::from_json(include_str!(
            "../../../../minecraft/source-projection.json"
        ))
        .unwrap();
        let preset = if target == "1.19.2" {
            "current-development-pilot".to_owned()
        } else {
            format!("current-development-head-{target}")
        };
        let mut selection = select(&manifest, target, &preset).unwrap();
        // This fixture has only the miniature file set below, while the real
        // preset includes unrelated feature-selected paths.
        selection.explicit_inputs.clear();
        selection.excluded_paths.clear();
        let roots = FrozenSelectionRoots {
            primary_src_root: root.join("platform/minecraft/src"),
            source_overlays: vec![],
            gradle_project_root: root.join(format!("platform/minecraft/freeze-fixture/{target}")),
            gradle_overlays: vec![],
        };
        let mut artifacts = BTreeMap::new();
        for (output, input) in request(commit, target).files {
            let (source, overlay) = if output == EXAMPLE {
                (
                    format!(
                        "platform/minecraft/development-baselines/{target}/project-fixtures/{EXAMPLE}"
                    ),
                    Some("development-fixtures".to_owned()),
                )
            } else if output == EXTRA {
                selection
                    .explicit_inputs
                    .insert(output.clone(), input.source_repo_path.clone());
                (input.source_repo_path, Some("feature".to_owned()))
            } else if output.starts_with("src/") {
                (input.source_repo_path, None)
            } else {
                (input.source_repo_path, Some("gradle_project".to_owned()))
            };
            let bytes = fs::read(root.join(source)).unwrap();
            artifacts.insert(
                output,
                ProjectedArtifact {
                    // Logical provenance intentionally cannot locate the file.
                    source_path: "logical/import/path".to_owned(),
                    source_bytes: bytes.clone(),
                    output_bytes: bytes,
                    overlay,
                },
            );
        }
        (selection, roots, artifacts)
    }

    #[test]
    fn selection_seed_rejects_uncommitted_manifest_drift() {
        let (root, _) = setup();
        let path = "platform/minecraft/source-projection.json";
        write(root.path(), path, b"{\"selection\":1}\n");
        git(root.path(), &["add", "--", path]);
        git(
            root.path(),
            &["commit", "--quiet", "-m", "selection definition"],
        );
        let commit = git(root.path(), &["rev-parse", "HEAD"]);
        verify_committed_selection_manifest(root.path(), &commit, path, b"{\"selection\":1}\n")
            .unwrap();
        write(root.path(), path, b"{\"selection\":2}\n");
        let error = verify_committed_selection_manifest(
            root.path(),
            &commit,
            path,
            &fs::read(root.path().join(path)).unwrap(),
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("working bytes differ"), "{error}");
    }

    #[test]
    fn selection_seed_uses_physical_primary_fixture_and_gradle_owners() {
        let (root, commit) = setup();
        for target in ["1.19.2", "1.19.4", "1.20", "1.21.0"] {
            let (selection, mut roots, mut artifacts) =
                selected_fixture(root.path(), &commit, target);
            if target == "1.19.4" {
                roots
                    .gradle_overlays
                    .push(("release-tag".to_owned(), roots.gradle_project_root.clone()));
                for (output, artifact) in &mut artifacts {
                    if !output.starts_with("src/") && output != EXAMPLE {
                        artifact.overlay = Some("release-tag".to_owned());
                    }
                }
            }
            let preview = preview_frozen_inventory_from_selection(
                root.path(),
                target,
                &selection,
                &roots,
                &artifacts,
                &commit,
                "9.99.99-fixture",
                None,
            )
            .unwrap();
            assert_eq!(preview.inventory.files.len(), artifacts.len());
            assert_eq!(
                preview.inventory.files[JAVA].source_repo_path,
                format!("platform/minecraft/{JAVA}")
            );
            assert_eq!(
                preview.inventory.files[EXAMPLE].source_repo_path,
                format!(
                    "platform/minecraft/development-baselines/{target}/project-fixtures/{EXAMPLE}"
                )
            );
            assert_eq!(
                preview.inventory.files["build.gradle"].source_repo_path,
                format!("platform/minecraft/freeze-fixture/{target}/build.gradle")
            );
            if target == "1.19.4" {
                assert_eq!(
                    preview.inventory.files["build.gradle"].overlay.as_deref(),
                    Some("release-tag")
                );
            }
            assert_eq!(
                preview.inventory.files["settings.gradle"].transform,
                FrozenTransform::TargetSettings
            );
            if target == "1.19.2" {
                let mut rendered_settings = BTreeMap::from([(
                    "settings.gradle".to_owned(),
                    artifacts["settings.gradle"].clone(),
                )]);
                append_project_name_override(&mut rendered_settings, target).unwrap();
                assert_ne!(
                    artifacts["settings.gradle"].source_bytes,
                    rendered_settings["settings.gradle"].output_bytes
                );
                assert_eq!(
                    preview.inventory.files["settings.gradle"].output_sha256,
                    sha256(&rendered_settings["settings.gradle"].output_bytes)
                );
            }
            if target == "1.20" {
                assert_eq!(
                    preview.inventory.files[EXTRA].source_repo_path,
                    format!("platform/minecraft/version-sources/{target}/{EXTRA}")
                );
                assert_eq!(
                    preview.inventory.files[EXTRA].overlay.as_deref(),
                    Some("feature")
                );
            }
            if target == "1.21.0" {
                assert_eq!(selection.context.minecraft_version, "1.21");
                assert_eq!(preview.inventory.context.minecraft_version, "1.21");
                assert_eq!(preview.inventory.target_id, "1.21.0");
            }
        }
    }

    #[test]
    fn selection_seed_covers_overlay_feature_and_version_source_routes() {
        let (root, _) = setup();
        let root_path = root.path();
        let overlay_source = format!("platform/minecraft/selection-overlay/{RESOURCE}");
        write(root_path, &overlay_source, b"selected overlay resource\n");
        let imported_java =
            format!("platform/minecraft/development-baselines/1.20/overlays/{JAVA}");
        write(
            root_path,
            &imported_java,
            b"class Proof { int imported = 1; }\n",
        );
        write(
            root_path,
            "platform/minecraft/selection-gradle/gradle.properties",
            b"minecraft_version=1.20\nmod_version=4.34.0\n",
        );
        git(root_path, &["add", "--", "platform/minecraft"]);
        git(root_path, &["commit", "--quiet", "-m", "selected routes"]);
        let commit = git(root_path, &["rev-parse", "HEAD"]);
        let (mut selection, mut roots, mut artifacts) =
            selected_fixture(root_path, &commit, "1.20");
        roots.source_overlays.push((
            "chosen".to_owned(),
            root_path.join("platform/minecraft/selection-overlay/src"),
        ));
        roots.gradle_overlays.push((
            "chosen-gradle".to_owned(),
            root_path.join("platform/minecraft/selection-gradle"),
        ));
        let resource = artifacts.get_mut(RESOURCE).unwrap();
        resource.source_bytes = fs::read(root_path.join(&overlay_source)).unwrap();
        resource.overlay = Some("chosen".to_owned());
        let java = artifacts.get_mut(JAVA).unwrap();
        java.source_bytes = fs::read(root_path.join(&imported_java)).unwrap();
        java.overlay = Some("development-head-1.20".to_owned());
        artifacts.get_mut("gradle.properties").unwrap().overlay = Some("chosen-gradle".to_owned());
        selection.explicit_inputs.remove(EXTRA);
        selection
            .release_baseline
            .as_mut()
            .unwrap()
            .post_baseline_version_sources
            .insert(
                EXTRA.to_owned(),
                VersionSourceSelection {
                    source_path: format!("platform/minecraft/version-sources/1.20/{EXTRA}"),
                    source_sha256: String::new(),
                    output_sha256: String::new(),
                    imported_output_sha256: None,
                    required_feature: None,
                },
            );
        artifacts.get_mut(EXTRA).unwrap().overlay = Some("version-source-1.20".to_owned());
        let preview = preview_frozen_inventory_from_selection(
            root_path,
            "1.20",
            &selection,
            &roots,
            &artifacts,
            &commit,
            "9.99.99-fixture",
            None,
        )
        .unwrap();
        assert_eq!(
            preview.inventory.files[JAVA].source_repo_path,
            imported_java
        );
        assert_eq!(
            preview.inventory.files[RESOURCE].source_repo_path,
            overlay_source
        );
        assert_eq!(
            preview.inventory.files[EXTRA].source_repo_path,
            format!("platform/minecraft/version-sources/1.20/{EXTRA}")
        );
        assert_eq!(
            preview.inventory.files["settings.gradle"].transform,
            FrozenTransform::TargetSettings
        );
    }

    #[test]
    fn selection_seed_rejects_incomplete_wrong_ambiguous_and_stale_owners() {
        let (root, commit) = setup();
        let (mut selection, mut roots, mut artifacts) =
            selected_fixture(root.path(), &commit, "1.19.4");
        let mut proposed =
            derive_physical_owners(root.path(), "1.19.4", &selection, &roots, &artifacts).unwrap();
        proposed.remove(RESOURCE);
        let error = preview_frozen_inventory_from_selection(
            root.path(),
            "1.19.4",
            &selection,
            &roots,
            &artifacts,
            &commit,
            "9.99.99-fixture",
            Some(&proposed),
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("membership"), "{error}");
        proposed =
            derive_physical_owners(root.path(), "1.19.4", &selection, &roots, &artifacts).unwrap();
        proposed.get_mut(RESOURCE).unwrap().source_repo_path =
            format!("platform/minecraft/{TEST_FIXTURE}");
        let error = preview_frozen_inventory_from_selection(
            root.path(),
            "1.19.4",
            &selection,
            &roots,
            &artifacts,
            &commit,
            "9.99.99-fixture",
            Some(&proposed),
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("physical owners"), "{error}");

        artifacts.get_mut(RESOURCE).unwrap().overlay = Some("unknown".to_owned());
        let error = derive_physical_owners(root.path(), "1.19.4", &selection, &roots, &artifacts)
            .unwrap_err()
            .to_string();
        assert!(error.contains("0 physical owner routes"), "{error}");
        artifacts.get_mut(RESOURCE).unwrap().overlay = Some("feature".to_owned());
        selection.explicit_inputs.insert(
            RESOURCE.to_owned(),
            format!("platform/minecraft/{RESOURCE}"),
        );
        roots
            .source_overlays
            .push(("feature".to_owned(), roots.primary_src_root.clone()));
        let error = derive_physical_owners(root.path(), "1.19.4", &selection, &roots, &artifacts)
            .unwrap_err()
            .to_string();
        assert!(error.contains("2 physical owner routes"), "{error}");
        roots.source_overlays.clear();
        artifacts.get_mut(RESOURCE).unwrap().source_bytes = b"wrong raw bytes".to_vec();
        let error = derive_physical_owners(root.path(), "1.19.4", &selection, &roots, &artifacts)
            .unwrap_err()
            .to_string();
        assert!(error.contains("differs from final source bytes"), "{error}");

        artifacts.get_mut(RESOURCE).unwrap().source_bytes = b"uncommitted drift\n".to_vec();
        write(
            root.path(),
            &format!("platform/minecraft/{RESOURCE}"),
            b"uncommitted drift\n",
        );
        let error = preview_frozen_inventory_from_selection(
            root.path(),
            "1.19.4",
            &selection,
            &roots,
            &artifacts,
            &commit,
            "9.99.99-fixture",
            None,
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("exact authored commit blob"), "{error}");
        selection.release_baseline.as_mut().unwrap().kind = BaselineKind::ReleaseTag;
        assert!(
            preview_frozen_inventory_from_selection(
                root.path(),
                "1.19.4",
                &selection,
                &roots,
                &artifacts,
                &commit,
                "9.99.99-fixture",
                None,
            )
            .is_err()
        );
    }

    #[test]
    fn frozen_fixture_java_is_copied_while_source_java_requires_rendering() {
        let (root, _) = setup();
        let output = "docs/architecture/fixtures/review/Example.java";
        let source = format!("platform/minecraft/freeze-fixture/1.19.4/{output}");
        let bytes = b"class FixtureExample {}\n";
        write(root.path(), &source, bytes);
        git(root.path(), &["add", "--", "platform/minecraft"]);
        git(root.path(), &["commit", "--quiet", "-m", "Java fixture"]);
        let commit = git(root.path(), &["rev-parse", "HEAD"]);
        let mut candidate = request(&commit, "1.19.4");
        candidate.files.insert(
            output.to_owned(),
            FrozenAuthoringInput {
                source_repo_path: source,
                overlay: Some("development-fixtures".to_owned()),
                transform: FrozenTransform::Copy,
            },
        );
        let preview = preview_frozen_inventory(root.path(), &candidate).unwrap();
        assert_eq!(preview.inventory.files[output].source_sha256, sha256(bytes));
        assert_eq!(preview.inventory.files[output].output_sha256, sha256(bytes));
        write(
            root.path(),
            &preview.binding.inventory_path,
            preview.canonical_json.as_bytes(),
        );
        let (artifacts, _) =
            project_frozen_artifacts(root.path(), &preview.binding, &commit, &candidate.context)
                .unwrap();
        assert_eq!(artifacts[output].output_bytes, bytes);
        candidate.files.get_mut(JAVA).unwrap().transform = FrozenTransform::Copy;
        assert!(preview_frozen_inventory(root.path(), &candidate).is_err());
        candidate.files.get_mut(JAVA).unwrap().transform = FrozenTransform::Java;
        candidate.files.get_mut(output).unwrap().transform = FrozenTransform::Java;
        assert!(preview_frozen_inventory(root.path(), &candidate).is_err());
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
    fn frozen_authoring_rejects_unsafe_release_version_before_inventory_path() {
        let (root, commit) = setup();
        for invalid in ["", "foo/bar", "Foo", "9.99.99-dev.1"] {
            let mut candidate = request(&commit, "1.20");
            candidate.release_mod_version = invalid.to_owned();
            candidate.context.preset = format!("released-{invalid}");
            let error = preview_frozen_inventory(root.path(), &candidate)
                .unwrap_err()
                .to_string();
            assert!(
                error.contains("release mod version"),
                "{invalid:?}: {error}"
            );
        }
        assert!(
            !root
                .path()
                .join("platform/minecraft/frozen-releases")
                .exists()
        );
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
