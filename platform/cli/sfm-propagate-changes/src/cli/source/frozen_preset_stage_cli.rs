//! Guarded staging of one reviewed frozen preset in an external Git checkout.

use super::source_cli::FROZEN_MATRIX_SCHEMA;
use super::source_cli::FROZEN_MATRIX_SCOPE;
use super::source_cli::FROZEN_MATRIX_TARGETS;
use super::source_cli::FrozenInventoryMatrixPreviewReport;
use super::source_cli::SourceFrozenInventoryMatrixPreviewArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::candidate_lock::is_within;
use crate::source_projection::manifest::FrozenSourceBinding;
use crate::source_projection::manifest::ProjectionPreset;
use crate::source_projection::manifest::SourceProjectionManifest;
use crate::source_projection::manifest::released_preset_id;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use crate::source_projection::release_baseline::frozen_git_command;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::fs::File;
use std::fs::OpenOptions;
use std::io;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::time::SystemTime;

const PREVIEW_MAX_BYTES: u64 = 96 * 1024 * 1024;
const MANIFEST_PATH: &str = "platform/minecraft/source-projection.json";
const STAGE_SCHEMA: &str = "sfm:frozen_preset_stage@1";
const STAGE_SCOPE: &str = "external candidate checkout only; no Git index, generated project, promotion, tag or publication writes";

#[derive(Clone, Debug, Facet)]
pub struct FrozenPresetStageArgs {
    /// Authored Git worktree with the exact selected inputs at commit A.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Separate, clean candidate Git checkout whose HEAD is commit A.
    #[facet(args::named)]
    pub candidate_root: PathBuf,
    /// Saved JSON from `source frozen-inventory-matrix-preview`.
    #[facet(args::named)]
    pub preview: PathBuf,
    /// Caller-reviewed SHA-256 of the exact preview file, including `sha256:`.
    #[facet(args::named)]
    pub preview_sha256: String,
    /// Write the ten inventories and candidate manifest after preflight.
    #[facet(default = false, args::named)]
    pub apply: bool,
}

#[derive(Debug, Facet)]
struct FrozenPresetStageReport {
    schema: String,
    scope: String,
    mode: String,
    outcome: String,
    source_commit: String,
    preview_sha256: String,
    candidate_root: String,
    release_preset_id: String,
    preset_definition_identity: String,
    source_manifest_sha256: String,
    staged_manifest_sha256: String,
    planned_inventory_paths: Vec<String>,
    /// Directories created by this invocation, relative to candidate root.
    created_directories: Vec<String>,
    /// Files successfully created, including a possibly incomplete current file.
    created_inventories: Vec<String>,
    /// Created inventory files whose entire contents were written and synced.
    completed_inventories: Vec<String>,
    /// Present when a manifest temporary file was created but not renamed.
    temporary_manifest_path: Option<String>,
    manifest_replaced: bool,
    failure: Option<String>,
}

struct StagePlan {
    report: FrozenPresetStageReport,
    inventories: Vec<(String, Vec<u8>)>,
    original_manifest_bytes: Vec<u8>,
    manifest_bytes: Vec<u8>,
}

impl FrozenPresetStageArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        self.invoke_with_hook(cancellation, invocation_dir, None)
    }

    fn invoke_with_hook(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        after_inventory: Option<&dyn Fn(usize) -> Result<()>>,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let candidate_arg = if self.candidate_root.is_absolute() {
            self.candidate_root.clone()
        } else {
            invocation_dir.join(&self.candidate_root)
        };
        let candidate = checked_directory(&candidate_arg)?;
        let authored = resolve_authored_root(&self.repo_root, invocation_dir)?;
        ensure!(
            !is_within(&candidate, &authored) && !is_within(&authored, &candidate),
            "candidate root overlaps the authored repository"
        );
        let (saved, digest) =
            read_reviewed_preview(&self.preview, invocation_dir, &self.preview_sha256)?;
        ensure!(
            saved.schema == FROZEN_MATRIX_SCHEMA && saved.scope == FROZEN_MATRIX_SCOPE,
            "saved file is not the exact frozen inventory matrix preview schema"
        );
        ensure!(
            saved.release_preset_id == released_preset_id(&saved.release_mod_version)?,
            "saved release preset ID differs from release version"
        );
        ensure!(
            saved.targets.len() == FROZEN_MATRIX_TARGETS.len(),
            "saved preview must contain exactly ten targets"
        );
        ensure_clean_candidate(&candidate, &saved.source_commit)?;

        let selections = saved
            .targets
            .iter()
            .map(|target| format!("{}={}", target.target_id, target.development_preset_id))
            .collect();
        let fresh = SourceFrozenInventoryMatrixPreviewArgs {
            repo_root: authored.clone(),
            source_commit: saved.source_commit.clone(),
            release_mod_version: saved.release_mod_version.clone(),
            manifest: None,
            selection: selections,
        }
        .preview_report_in(cancellation, invocation_dir)?;
        ensure!(
            saved == fresh,
            "saved frozen matrix preview differs from fresh ten-target preview at authored commit"
        );
        let mut plan = preflight_plan(&candidate, &authored, &fresh, &digest)?;
        if !self.apply {
            return Ok(CliOutput::facet(plan.report));
        }
        "apply".clone_into(&mut plan.report.mode);
        let outcome = apply_plan(&candidate, &mut plan, cancellation, after_inventory);
        match outcome {
            Ok(()) => {
                "staged".clone_into(&mut plan.report.outcome);
                Ok(CliOutput::facet(plan.report))
            }
            Err(error) => {
                // Nothing is rolled back. These fields identify exactly which
                // candidate paths this invocation created before it stopped.
                "partial_failure".clone_into(&mut plan.report.outcome);
                plan.report.failure = Some(format!("{error:#}"));
                Ok(CliOutput::facet_with_status(plan.report, 1))
            }
        }
    }
}

fn resolve_authored_root(path: &Path, invocation_dir: &Path) -> Result<PathBuf> {
    let path = if path.is_absolute() {
        path.to_path_buf()
    } else {
        invocation_dir.join(path)
    };
    checked_directory(&path)
        .wrap_err_with(|| format!("cannot resolve authored root '{}'", path.display()))
}

fn read_reviewed_preview(
    path: &Path,
    invocation_dir: &Path,
    reviewed_sha256: &str,
) -> Result<(FrozenInventoryMatrixPreviewReport, String)> {
    let path = if path.is_absolute() {
        path.to_path_buf()
    } else {
        invocation_dir.join(path)
    };
    let metadata = fs::symlink_metadata(&path)
        .wrap_err_with(|| format!("cannot inspect frozen matrix preview '{}'", path.display()))?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "frozen matrix preview must be a regular, non-reparse file"
    );
    ensure!(
        metadata.len() <= PREVIEW_MAX_BYTES,
        "frozen matrix preview exceeds the {PREVIEW_MAX_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    File::open(&path)?
        .take(PREVIEW_MAX_BYTES + 1)
        .read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= PREVIEW_MAX_BYTES,
        "frozen matrix preview exceeds the {PREVIEW_MAX_BYTES}-byte limit"
    );
    let digest = sha256(&bytes);
    ensure!(
        digest == reviewed_sha256,
        "frozen matrix preview SHA-256 differs from caller-reviewed digest"
    );
    let text = std::str::from_utf8(&bytes).wrap_err("frozen matrix preview is not UTF-8")?;
    let report: FrozenInventoryMatrixPreviewReport =
        facet_json::from_str(text).wrap_err("cannot parse frozen matrix preview")?;
    let canonical = facet_json::to_string_pretty(&report)?;
    ensure!(
        text == canonical || text == format!("{canonical}\n"),
        "saved frozen matrix preview is not exact canonical CLI JSON"
    );
    Ok((report, digest))
}

fn preflight_plan(
    candidate: &Path,
    authored: &Path,
    preview: &FrozenInventoryMatrixPreviewReport,
    digest: &str,
) -> Result<StagePlan> {
    let candidate_manifest = checked_file(candidate, MANIFEST_PATH)?;
    let candidate_bytes = fs::read(&candidate_manifest)?;
    ensure!(
        sha256(&candidate_bytes) == preview.source_manifest_sha256,
        "candidate manifest differs from preview's authored manifest"
    );
    let authored_bytes = fs::read(checked_file(authored, MANIFEST_PATH)?)?;
    ensure!(
        candidate_bytes == authored_bytes,
        "candidate and authored selection manifests differ"
    );
    // Existing checked-in manifests mix compact and pretty JSON. Their exact
    // reviewed bytes matter here; only the newly staged output is canonicalized.
    let mut manifest = SourceProjectionManifest::from_json(std::str::from_utf8(&candidate_bytes)?)?;
    let id = &preview.release_preset_id;
    ensure!(
        manifest
            .presets
            .iter()
            .all(|preset| !preset.id.eq_ignore_ascii_case(id)),
        "release preset ID is already used or case-collides with an existing preset"
    );
    let preset_tree = format!("platform/minecraft/frozen-releases/{id}");
    ensure_plan_path(candidate, &preset_tree, false)?;
    let mut target_features = BTreeMap::new();
    let mut bindings = Vec::with_capacity(FROZEN_MATRIX_TARGETS.len());
    let mut inventories = Vec::with_capacity(FROZEN_MATRIX_TARGETS.len());
    for (expected, target) in FROZEN_MATRIX_TARGETS.into_iter().zip(&preview.targets) {
        ensure!(
            target.target_id == expected,
            "frozen matrix target order or membership differs from canonical ten targets"
        );
        ensure!(
            target.inventory_path == format!("{preset_tree}/{expected}/inventory.json"),
            "frozen inventory path differs from canonical preset tree"
        );
        validate_relative_path(&target.inventory_path)?;
        ensure_plan_path(candidate, &target.inventory_path, false)?;
        let bytes = target.canonical_inventory_json.as_bytes();
        ensure!(
            sha256(bytes) == format!("sha256:{}", target.inventory_sha256),
            "frozen inventory bytes differ from its SHA-256 binding"
        );
        if !target.enabled_features.is_empty() {
            target_features.insert(expected.to_owned(), target.enabled_features.clone());
        }
        bindings.push(FrozenSourceBinding {
            target_id: expected.to_owned(),
            inventory_path: target.inventory_path.clone(),
            inventory_sha256: target.inventory_sha256.clone(),
        });
        inventories.push((target.inventory_path.clone(), bytes.to_vec()));
    }
    let mut preset = ProjectionPreset {
        id: id.clone(),
        release_mod_version: Some(preview.release_mod_version.clone()),
        targets: FROZEN_MATRIX_TARGETS
            .iter()
            .map(|id| (*id).to_owned())
            .collect(),
        enabled_features: Vec::new(),
        target_features,
        release_baselines: Vec::new(),
        frozen_source_commit: Some(preview.source_commit.clone()),
        frozen_sources: bindings,
        canonical_project_fixture_provenance_sha256: None,
        identity: String::new(),
    };
    preset.identity = manifest.compute_preset_identity(&preset)?;
    let definition_identity = preset.identity.clone();
    manifest.presets.push(preset);
    let manifest_bytes = manifest.to_json()?.into_bytes();
    let report = FrozenPresetStageReport {
        schema: STAGE_SCHEMA.to_owned(),
        scope: STAGE_SCOPE.to_owned(),
        mode: "preflight".to_owned(),
        outcome: "ready".to_owned(),
        source_commit: preview.source_commit.clone(),
        preview_sha256: digest.to_owned(),
        candidate_root: candidate.display().to_string(),
        release_preset_id: id.clone(),
        preset_definition_identity: definition_identity,
        source_manifest_sha256: preview.source_manifest_sha256.clone(),
        staged_manifest_sha256: sha256(&manifest_bytes),
        planned_inventory_paths: inventories.iter().map(|(path, _)| path.clone()).collect(),
        created_directories: Vec::new(),
        created_inventories: Vec::new(),
        completed_inventories: Vec::new(),
        temporary_manifest_path: None,
        manifest_replaced: false,
        failure: None,
    };
    Ok(StagePlan {
        report,
        inventories,
        original_manifest_bytes: candidate_bytes,
        manifest_bytes,
    })
}

fn apply_plan(
    root: &Path,
    plan: &mut StagePlan,
    cancellation: &CancellationToken,
    after_inventory: Option<&dyn Fn(usize) -> Result<()>>,
) -> Result<()> {
    for index in 0..plan.inventories.len() {
        cancellation.bail_if_cancelled()?;
        let (relative, bytes) = &plan.inventories[index];
        ensure_parent_directories(root, relative, &mut plan.report.created_directories)?;
        ensure_plan_path(root, relative, false)?;
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(root.join(relative))
            .wrap_err_with(|| format!("cannot create candidate inventory '{relative}'"))?;
        plan.report.created_inventories.push(relative.clone());
        file.write_all(bytes)
            .wrap_err_with(|| format!("cannot write candidate inventory '{relative}'"))?;
        file.sync_all()
            .wrap_err_with(|| format!("cannot sync candidate inventory '{relative}'"))?;
        plan.report.completed_inventories.push(relative.clone());
        if let Some(hook) = after_inventory {
            hook(index + 1)?;
        }
        ensure_candidate_quiescent(root, plan)?;
    }
    cancellation.bail_if_cancelled()?;
    ensure_candidate_quiescent(root, plan)?;
    ensure_plan_path(root, MANIFEST_PATH, true)?;
    let temp_relative = format!(
        "platform/minecraft/.source-projection.json.frozen-stage-{}-{}",
        std::process::id(),
        SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)?
            .as_nanos()
    );
    ensure_plan_path(root, &temp_relative, false)?;
    let temp = root.join(&temp_relative);
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&temp)
        .wrap_err("cannot create temporary candidate manifest")?;
    plan.report.temporary_manifest_path = Some(temp_relative);
    file.write_all(&plan.manifest_bytes)
        .wrap_err("cannot write temporary candidate manifest")?;
    file.sync_all()
        .wrap_err("cannot sync temporary candidate manifest")?;
    drop(file);
    ensure_candidate_quiescent(root, plan)?;
    fs::rename(&temp, root.join(MANIFEST_PATH))
        .wrap_err("cannot replace candidate manifest from temporary file")?;
    plan.report.temporary_manifest_path = None;
    plan.report.manifest_replaced = true;
    ensure!(
        fs::read(checked_file(root, MANIFEST_PATH)?)? == plan.manifest_bytes,
        "staged candidate manifest bytes differ from plan"
    );
    for (relative, bytes) in &plan.inventories {
        ensure!(
            fs::read(checked_file(root, relative)?)? == *bytes,
            "staged candidate inventory bytes differ at '{relative}'"
        );
    }
    ensure!(
        git_stdout(root, &["rev-parse", "HEAD"])?
            == format!("{}\n", plan.report.source_commit).as_bytes(),
        "candidate HEAD changed during staging"
    );
    ensure!(
        git_stdout(root, &["diff", "--cached", "--name-only"])? == b"",
        "candidate Git index changed during staging"
    );
    Ok(())
}

fn ensure_candidate_quiescent(root: &Path, plan: &StagePlan) -> Result<()> {
    ensure!(
        git_stdout(root, &["rev-parse", "HEAD"])?
            == format!("{}\n", plan.report.source_commit).as_bytes(),
        "candidate HEAD changed during staging"
    );
    ensure!(
        fs::read(checked_file(root, MANIFEST_PATH)?)? == plan.original_manifest_bytes,
        "candidate manifest changed during staging"
    );
    ensure_unconcealed_index(root)?;
    for (relative, expected_bytes) in plan
        .inventories
        .iter()
        .take(plan.report.completed_inventories.len())
    {
        ensure!(
            fs::read(checked_file(root, relative)?)? == *expected_bytes,
            "completed candidate inventory changed during staging at '{relative}'"
        );
    }
    if let Some(relative) = &plan.report.temporary_manifest_path {
        ensure!(
            fs::read(checked_file(root, relative)?)? == plan.manifest_bytes,
            "temporary candidate manifest changed before replacement"
        );
    }
    let expected = plan
        .report
        .created_inventories
        .iter()
        .cloned()
        .chain(plan.report.temporary_manifest_path.iter().cloned())
        .collect::<BTreeSet<_>>();
    let status = git_stdout(
        root,
        &[
            "-c",
            "core.fsmonitor=false",
            "status",
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
            "--ignore-submodules=none",
        ],
    )?;
    for entry in status
        .split(|byte| *byte == 0)
        .filter(|entry| !entry.is_empty())
    {
        ensure!(
            entry.starts_with(b"?? ")
                && std::str::from_utf8(&entry[3..]).is_ok_and(|path| expected.contains(path)),
            "candidate checkout gained an unexpected staged, unstaged or untracked edit during staging"
        );
    }
    Ok(())
}

fn ensure_clean_candidate(root: &Path, commit: &str) -> Result<()> {
    let top = git_stdout(root, &["rev-parse", "--show-toplevel"])?;
    let top = String::from_utf8(top)?;
    ensure!(
        fs::canonicalize(top.trim())? == root,
        "candidate root must be the Git worktree top level"
    );
    ensure!(
        git_stdout(root, &["rev-parse", "HEAD"])? == format!("{commit}\n").as_bytes(),
        "candidate HEAD differs from reviewed authored commit A"
    );
    ensure_unconcealed_index(root)?;
    ensure!(
        git_stdout(
            root,
            &[
                "-c",
                "core.fsmonitor=false",
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
                "--ignore-submodules=none"
            ]
        )?
        .is_empty(),
        "candidate Git checkout has staged, unstaged or untracked edits"
    );
    Ok(())
}

fn ensure_unconcealed_index(root: &Path) -> Result<()> {
    let index = git_stdout(root, &["ls-files", "--cached", "-v", "-z"])?;
    ensure!(
        index.is_empty() || index.last() == Some(&0),
        "candidate Git index is malformed"
    );
    for entry in index
        .split(|byte| *byte == 0)
        .filter(|entry| !entry.is_empty())
    {
        ensure!(
            entry.len() >= 3 && entry[0] == b'H' && entry[1] == b' ',
            "candidate Git index has a concealed or non-normal tracked entry"
        );
    }
    Ok(())
}

fn git_stdout(root: &Path, args: &[&str]) -> Result<Vec<u8>> {
    let output = frozen_git_command(root).args(args).output()?;
    ensure!(
        output.status.success(),
        "candidate Git command failed: {}",
        String::from_utf8_lossy(&output.stderr).trim()
    );
    Ok(output.stdout)
}

fn ensure_plan_path(root: &Path, relative: &str, allow_file: bool) -> Result<()> {
    validate_relative_path(relative)?;
    let mut cursor = root.to_path_buf();
    let parts = relative.split('/').collect::<Vec<_>>();
    let mut missing_parent = false;
    for (index, part) in parts.iter().enumerate() {
        if missing_parent {
            break;
        }
        let colliding = fs::read_dir(&cursor)?
            .map(|entry| entry.map(|entry| entry.file_name()))
            .collect::<io::Result<Vec<_>>>()?
            .into_iter()
            .any(|name| {
                name.to_string_lossy().eq_ignore_ascii_case(part)
                    && name != std::ffi::OsStr::new(part)
            });
        ensure!(
            !colliding,
            "candidate destination has a case-colliding path component in '{relative}'"
        );
        cursor.push(part);
        match fs::symlink_metadata(&cursor) {
            Ok(metadata) => {
                ensure!(
                    !is_reparse(&metadata),
                    "candidate destination traverses a reparse point at '{relative}'"
                );
                if index + 1 == parts.len() {
                    ensure!(
                        allow_file && metadata.is_file(),
                        "candidate destination or preset tree already exists at '{relative}'"
                    );
                } else {
                    ensure!(
                        metadata.is_dir(),
                        "candidate parent is not a directory at '{relative}'"
                    );
                }
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => missing_parent = true,
            Err(error) => return Err(error.into()),
        }
    }
    ensure!(
        !allow_file || !missing_parent,
        "required candidate manifest is missing"
    );
    Ok(())
}

fn ensure_parent_directories(root: &Path, relative: &str, created: &mut Vec<String>) -> Result<()> {
    let parent = Path::new(relative)
        .parent()
        .ok_or_else(|| eyre::eyre!("inventory has no parent"))?;
    let mut cursor = root.to_path_buf();
    let mut relative_cursor = PathBuf::new();
    for part in parent.components() {
        let Component::Normal(name) = part else {
            eyre::bail!("inventory parent is not a normal relative path");
        };
        cursor.push(name);
        relative_cursor.push(name);
        match fs::symlink_metadata(&cursor) {
            Ok(metadata) => ensure!(
                metadata.is_dir() && !is_reparse(&metadata),
                "candidate inventory parent is not a safe directory"
            ),
            Err(error) if error.kind() == io::ErrorKind::NotFound => {
                fs::create_dir(&cursor)?;
                created.push(relative_cursor.to_string_lossy().replace('\\', "/"));
            }
            Err(error) => return Err(error.into()),
        }
    }
    Ok(())
}

fn is_reparse(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.file_attributes() & 0x0000_0400 != 0
    }
    #[cfg(not(windows))]
    {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::cli::source::source_cli::tests::frozen_matrix_fixture;
    use tempfile::TempDir;

    struct Fixture {
        _authored: TempDir,
        _candidate_parent: TempDir,
        _review: TempDir,
        args: FrozenPresetStageArgs,
        preview: FrozenInventoryMatrixPreviewReport,
        original_manifest: Vec<u8>,
    }

    fn git(root: &Path, args: &[&str]) -> String {
        let output = frozen_git_command(root).args(args).output().unwrap();
        assert!(
            output.status.success(),
            "git {args:?}: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn fixture() -> Fixture {
        let (authored, commit, preview_args) = frozen_matrix_fixture();
        let preview = preview_args
            .preview_report_in(&CancellationToken::new(), authored.path())
            .unwrap();
        let candidate_parent = tempfile::tempdir().unwrap();
        let candidate_root = candidate_parent.path().join("candidate");
        git(
            authored.path(),
            &[
                "worktree",
                "add",
                "--quiet",
                "--detach",
                candidate_root.to_str().unwrap(),
                &commit,
            ],
        );
        let review = tempfile::tempdir().unwrap();
        let preview_path = review.path().join("frozen-preview.json");
        let bytes = format!("{}\n", facet_json::to_string_pretty(&preview).unwrap()).into_bytes();
        fs::write(&preview_path, &bytes).unwrap();
        let original_manifest = fs::read(candidate_root.join(MANIFEST_PATH)).unwrap();
        let args = FrozenPresetStageArgs {
            repo_root: authored.path().to_path_buf(),
            candidate_root,
            preview: preview_path,
            preview_sha256: sha256(&bytes),
            apply: false,
        };
        Fixture {
            _authored: authored,
            _candidate_parent: candidate_parent,
            _review: review,
            args,
            preview,
            original_manifest,
        }
    }

    fn saved_preview(fixture: &mut Fixture) {
        let bytes = format!(
            "{}\n",
            facet_json::to_string_pretty(&fixture.preview).unwrap()
        )
        .into_bytes();
        fs::write(&fixture.args.preview, &bytes).unwrap();
        fixture.args.preview_sha256 = sha256(&bytes);
    }

    fn stage_report(output: &CliOutput) -> FrozenPresetStageReport {
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        facet_json::from_str(&json).unwrap()
    }

    fn assert_no_candidate_stage(fixture: &Fixture) {
        assert_eq!(
            fs::read(fixture.args.candidate_root.join(MANIFEST_PATH)).unwrap(),
            fixture.original_manifest
        );
        assert!(
            !fixture
                .args
                .candidate_root
                .join("platform/minecraft/frozen-releases")
                .exists()
        );
    }

    #[test]
    fn parses_explicit_candidate_and_review_digest() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "frozen-preset-stage",
            "--repo-root",
            "C:/authored",
            "--candidate-root",
            "C:/candidate",
            "--preview",
            "C:/review/preview.json",
            "--preview-sha256",
            "sha256:reviewed",
            "--apply",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::FrozenPresetStage(args),
        }) = parsed.command
        else {
            panic!("expected frozen-preset-stage command");
        };
        assert!(args.apply);
        assert_eq!(args.preview_sha256, "sha256:reviewed");
    }

    #[test]
    fn preflight_is_read_only_and_apply_stages_exact_eleven_paths_without_mutating_hardlink() {
        let mut fixture = fixture();
        let preflight = fixture
            .args
            .clone()
            .invoke_in(&CancellationToken::new(), fixture._authored.path())
            .unwrap();
        let preflight = stage_report(&preflight);
        assert_eq!(preflight.mode, "preflight");
        assert_eq!(preflight.outcome, "ready");
        assert_eq!(preflight.planned_inventory_paths.len(), 10);
        assert!(preflight.created_inventories.is_empty());
        assert_no_candidate_stage(&fixture);
        assert!(git(&fixture.args.candidate_root, &["status", "--porcelain"]).is_empty());

        let hardlink = fixture
            ._review
            .path()
            .join("original-manifest-hardlink.json");
        fs::hard_link(fixture.args.candidate_root.join(MANIFEST_PATH), &hardlink).unwrap();
        fixture.args.apply = true;
        let applied = fixture
            .args
            .clone()
            .invoke_in(&CancellationToken::new(), fixture._authored.path())
            .unwrap();
        assert_eq!(applied.exit_code(), 0);
        let report = stage_report(&applied);
        assert_eq!(report.outcome, "staged");
        assert_eq!(report.created_inventories, report.planned_inventory_paths);
        assert_eq!(report.completed_inventories, report.planned_inventory_paths);
        assert!(report.manifest_replaced);
        assert!(report.temporary_manifest_path.is_none());
        assert_eq!(fs::read(&hardlink).unwrap(), fixture.original_manifest);
        let staged = fs::read(fixture.args.candidate_root.join(MANIFEST_PATH)).unwrap();
        assert_eq!(sha256(&staged), report.staged_manifest_sha256);
        let manifest =
            SourceProjectionManifest::from_json(std::str::from_utf8(&staged).unwrap()).unwrap();
        let preset = manifest.preset(&report.release_preset_id).unwrap();
        assert_eq!(preset.identity, report.preset_definition_identity);
        assert_eq!(preset.frozen_sources.len(), 10);
        for target in &fixture.preview.targets {
            assert_eq!(
                fs::read(fixture.args.candidate_root.join(&target.inventory_path)).unwrap(),
                target.canonical_inventory_json.as_bytes()
            );
        }
        assert_eq!(
            git(&fixture.args.candidate_root, &["rev-parse", "HEAD"]),
            fixture.preview.source_commit
        );
        assert!(
            git(
                &fixture.args.candidate_root,
                &["diff", "--cached", "--name-only"]
            )
            .is_empty()
        );
        assert_eq!(
            fs::read(fixture._authored.path().join(MANIFEST_PATH)).unwrap(),
            fixture.original_manifest
        );
        assert!(
            !fixture
                .args
                .candidate_root
                .join("platform/minecraft/mc-version")
                .exists()
        );
    }

    #[test]
    fn preflight_accepts_valid_noncanonical_checked_in_manifest() {
        let fixture = fixture();
        let noncanonical = [b"\n".as_slice(), fixture.original_manifest.as_slice()].concat();
        fs::write(
            fixture.args.candidate_root.join(MANIFEST_PATH),
            &noncanonical,
        )
        .unwrap();
        fs::write(fixture._authored.path().join(MANIFEST_PATH), &noncanonical).unwrap();
        let mut preview = fixture.preview;
        preview.source_manifest_sha256 = sha256(&noncanonical);

        let plan = preflight_plan(
            &fixture.args.candidate_root,
            fixture._authored.path(),
            &preview,
            &fixture.args.preview_sha256,
        )
        .unwrap();
        assert_eq!(plan.original_manifest_bytes, noncanonical);
        assert_eq!(plan.report.planned_inventory_paths.len(), 10);
        assert_ne!(plan.manifest_bytes, noncanonical);
    }

    #[test]
    fn rejects_tampered_preview_and_changed_version_feature_or_membership() {
        let mut fixture = fixture();
        fs::write(&fixture.args.preview, b"{}").unwrap();
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .unwrap_err()
                .to_string()
                .contains("SHA-256")
        );
        assert_no_candidate_stage(&fixture);

        fixture.preview.release_mod_version = "9.99.98-fixture".to_owned();
        saved_preview(&mut fixture);
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fixture.preview.release_mod_version = "9.99.99-fixture".to_owned();
        fixture.preview.targets[0].enabled_features.clear();
        saved_preview(&mut fixture);
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fixture.preview.targets[0].enabled_features = vec!["matrix_probe".to_owned()];
        fixture.preview.targets.pop();
        saved_preview(&mut fixture);
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        assert_no_candidate_stage(&fixture);
    }

    #[test]
    fn rejects_wrong_commit_dirty_or_overlapping_candidate_root() {
        let mut fixture = fixture();
        fixture.preview.source_commit = "a".repeat(40);
        saved_preview(&mut fixture);
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fixture.preview.source_commit = git(fixture._authored.path(), &["rev-parse", "HEAD"]);
        saved_preview(&mut fixture);
        fs::write(fixture.args.candidate_root.join("contributor.txt"), b"edit").unwrap();
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fs::remove_file(fixture.args.candidate_root.join("contributor.txt")).unwrap();
        let candidate = fixture.args.candidate_root.clone();
        fixture.args.candidate_root = fixture._authored.path().to_path_buf();
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fixture.args.candidate_root = fixture._authored.path().join("platform/minecraft");
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fixture.args.candidate_root = candidate;
        assert_no_candidate_stage(&fixture);
    }

    #[test]
    fn rejects_existing_preset_tree_case_collision_and_reparse_parent() {
        let fixture = fixture();
        let preset_tree = fixture.args.candidate_root.join(format!(
            "platform/minecraft/frozen-releases/{}",
            fixture.preview.release_preset_id
        ));
        fs::create_dir_all(&preset_tree).unwrap();
        assert!(
            ensure_plan_path(
                &fixture.args.candidate_root,
                &format!(
                    "platform/minecraft/frozen-releases/{}",
                    fixture.preview.release_preset_id
                ),
                false
            )
            .is_err()
        );
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fs::remove_dir(&preset_tree).unwrap();
        let colliding =
            preset_tree.with_file_name(fixture.preview.release_preset_id.to_uppercase());
        fs::create_dir_all(&colliding).unwrap();
        assert!(
            ensure_plan_path(
                &fixture.args.candidate_root,
                &format!(
                    "platform/minecraft/frozen-releases/{}",
                    fixture.preview.release_preset_id
                ),
                false
            )
            .is_err()
        );
        assert!(
            fixture
                .args
                .clone()
                .invoke_in(&CancellationToken::new(), fixture._authored.path())
                .is_err()
        );
        fs::remove_dir(&colliding).unwrap();

        let parent = fixture
            .args
            .candidate_root
            .join("platform/minecraft/frozen-releases");
        fs::remove_dir(&parent).unwrap();
        let real = fixture._review.path().join("real-directory");
        fs::create_dir(&real).unwrap();
        #[cfg(windows)]
        let linked = std::os::windows::fs::symlink_dir(&real, &parent).is_ok();
        #[cfg(unix)]
        let linked = std::os::unix::fs::symlink(&real, &parent).is_ok();
        if linked {
            assert!(
                ensure_plan_path(
                    &fixture.args.candidate_root,
                    &format!(
                        "platform/minecraft/frozen-releases/{}",
                        fixture.preview.release_preset_id
                    ),
                    false
                )
                .is_err()
            );
            assert!(
                fixture
                    .args
                    .clone()
                    .invoke_in(&CancellationToken::new(), fixture._authored.path())
                    .is_err()
            );
        }
        assert_eq!(
            fs::read(fixture.args.candidate_root.join(MANIFEST_PATH)).unwrap(),
            fixture.original_manifest
        );
        assert!(ensure_plan_path(&fixture.args.candidate_root, "../escape", false).is_err());
    }

    #[test]
    fn refuses_reused_release_preset_id_even_with_new_reviewed_manifest_hash() {
        let fixture = fixture();
        let original_plan = preflight_plan(
            &fixture.args.candidate_root,
            fixture._authored.path(),
            &fixture.preview,
            &fixture.args.preview_sha256,
        )
        .unwrap();
        fs::write(
            fixture.args.candidate_root.join(MANIFEST_PATH),
            &original_plan.manifest_bytes,
        )
        .unwrap();
        fs::write(
            fixture._authored.path().join(MANIFEST_PATH),
            &original_plan.manifest_bytes,
        )
        .unwrap();
        let mut preview = fixture.preview;
        preview.source_manifest_sha256 = sha256(&original_plan.manifest_bytes);
        let error = preflight_plan(
            &fixture.args.candidate_root,
            fixture._authored.path(),
            &preview,
            &fixture.args.preview_sha256,
        )
        .err()
        .unwrap()
        .to_string();
        assert!(error.contains("already used"), "{error}");
    }

    #[test]
    fn contributor_edit_during_apply_stops_before_manifest_replacement() {
        let mut fixture = fixture();
        fixture.args.apply = true;
        let candidate = fixture.args.candidate_root.clone();
        let edit_after_ten = |count: usize| -> Result<()> {
            if count == 10 {
                fs::write(candidate.join("contributor.txt"), b"new contributor edit")?;
            }
            Ok(())
        };
        let output = fixture
            .args
            .clone()
            .invoke_with_hook(
                &CancellationToken::new(),
                fixture._authored.path(),
                Some(&edit_after_ten),
            )
            .unwrap();
        assert_eq!(output.exit_code(), 1);
        let report = stage_report(&output);
        assert_eq!(report.completed_inventories.len(), 10);
        assert!(!report.manifest_replaced);
        assert!(report.failure.unwrap().contains("unexpected"));
        assert_eq!(
            fs::read(fixture.args.candidate_root.join(MANIFEST_PATH)).unwrap(),
            fixture.original_manifest
        );
        assert_eq!(
            fs::read(fixture.args.candidate_root.join("contributor.txt")).unwrap(),
            b"new contributor edit"
        );
    }

    #[test]
    fn changed_earlier_inventory_stops_before_manifest_replacement() {
        let mut fixture = fixture();
        fixture.args.apply = true;
        let candidate = fixture.args.candidate_root.clone();
        let first_inventory = fixture.preview.targets[0].inventory_path.clone();
        let edit_after_ten = |count: usize| -> Result<()> {
            if count == 10 {
                fs::write(candidate.join(&first_inventory), b"changed inventory")?;
            }
            Ok(())
        };
        let output = fixture
            .args
            .clone()
            .invoke_with_hook(
                &CancellationToken::new(),
                fixture._authored.path(),
                Some(&edit_after_ten),
            )
            .unwrap();
        assert_eq!(output.exit_code(), 1);
        let report = stage_report(&output);
        assert_eq!(report.completed_inventories.len(), 10);
        assert!(!report.manifest_replaced);
        assert!(report.failure.unwrap().contains("inventory changed"));
        assert_eq!(
            fs::read(fixture.args.candidate_root.join(MANIFEST_PATH)).unwrap(),
            fixture.original_manifest
        );
        assert_eq!(
            fs::read(fixture.args.candidate_root.join(first_inventory)).unwrap(),
            b"changed inventory"
        );
    }

    #[test]
    fn interrupted_apply_reports_exact_created_candidate_paths_without_cleanup() {
        let mut fixture = fixture();
        fixture.args.apply = true;
        let stop_after_one = |count: usize| -> Result<()> {
            ensure!(count != 1, "synthetic interruption after one inventory");
            Ok(())
        };
        let output = fixture
            .args
            .clone()
            .invoke_with_hook(
                &CancellationToken::new(),
                fixture._authored.path(),
                Some(&stop_after_one),
            )
            .unwrap();
        assert_eq!(output.exit_code(), 1);
        let report = stage_report(&output);
        assert_eq!(report.outcome, "partial_failure");
        assert_eq!(report.created_inventories.len(), 1);
        assert_eq!(report.completed_inventories, report.created_inventories);
        assert!(!report.manifest_replaced);
        assert!(report.temporary_manifest_path.is_none());
        assert!(report.failure.unwrap().contains("synthetic interruption"));
        assert_eq!(
            fs::read(
                fixture
                    .args
                    .candidate_root
                    .join(&report.created_inventories[0])
            )
            .unwrap(),
            fixture.preview.targets[0]
                .canonical_inventory_json
                .as_bytes()
        );
        assert_eq!(
            fs::read(fixture.args.candidate_root.join(MANIFEST_PATH)).unwrap(),
            fixture.original_manifest
        );
        for target in &fixture.preview.targets[1..] {
            assert!(
                !fixture
                    .args
                    .candidate_root
                    .join(&target.inventory_path)
                    .exists()
            );
        }
    }
}
