//! Guarded, ten-target transition from one checked-in projection preset to another.
//!
//! A candidate is an external, already-built standalone project. Its per-target
//! provenance manifest must match an independently reviewed SHA-256 digest. This
//! module does not build JARs, publish releases, or alter Git history.

use super::manifest::ProjectionTarget;
use super::manifest::SourceProjectionManifest;
use super::provenance::ProjectionProvenance;
use super::provenance::sha256;
use super::sync::MANIFEST_FILE;
use eyre::Result;
use eyre::WrapErr;
use eyre::bail;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::ErrorKind;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt as _;
#[cfg(windows)]
use std::os::windows::fs::OpenOptionsExt as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use walkdir::WalkDir;

const MATRIX_SIZE: usize = 10;
const SOURCE_MANIFEST: &str = "platform/minecraft/source-projection.json";
const REPAIR_PRESET_ID: &str = "released-4.34.0";
const REPAIR_REFMAP_PATH: &str = "src/main/resources/sfm.refmap.json";
const REPAIR_TARGETS: [&str; MATRIX_SIZE] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const REPAIR_REFMAP_TARGETS: [&str; 5] = ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1"];

#[derive(Clone, Debug)]
pub struct PromotionCandidate {
    pub project_root: PathBuf,
    /// Exact SHA-256 of the candidate's canonical provenance-manifest bytes.
    pub reviewed_manifest_sha256: String,
    /// Exact production artifact below this candidate's build/libs directory.
    pub production_jar_relative_path: String,
    pub production_jar_sha256: String,
    /// Reviewed Gradle task that produced the production artifact.
    pub production_task: String,
    /// Reviewed JDK major selected for the production build.
    pub jdk_major: u16,
    /// Portable, reviewed build identity of that JDK; not process attestation.
    pub jdk_build_id: String,
}

#[derive(Clone, Debug)]
pub struct PromotionRequest {
    pub repository_root: PathBuf,
    /// Reviewed HEAD commit whose checked-in manifests define old ownership.
    pub reviewed_head_commit: String,
    /// Reviewed bytes of the repository's source-projection definition.
    pub reviewed_source_manifest_sha256: String,
    /// One repo-relative compatibility acceptance document for this matrix.
    pub compatibility_evidence_relative_path: String,
    pub reviewed_compatibility_evidence_sha256: String,
    /// Must contain every matrix target exactly once, with no other target.
    pub candidates: BTreeMap<String, PromotionCandidate>,
    pub candidate_preset_id: String,
    pub candidate_definition_identity: String,
    pub transition: PromotionTransition,
    /// Permits an edited owned output only if its bytes already equal the candidate.
    pub accept_identical_edits: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum PromotionTransition {
    /// The normal rule: a released preset definition is immutable, so a new
    /// candidate must have a new preset ID and definition identity.
    NewImmutablePreset,
    /// A conspicuous, reviewed exception for a checked-in projection baseline
    /// that has not been accepted. This does not assert that the numbered mod
    /// release itself was unpublished. The caller must independently verify
    /// pre-acceptance status before choosing this mode.
    PreAcceptanceBaselineRepair {
        expected_old_preset_id: String,
        expected_old_definition_identity: String,
        /// Exactly ten manifest replacements and five refmap creates, with
        /// reviewed before/after hashes for each individual operation.
        reviewed_operations: Vec<ReviewedPromotionOperation>,
    },
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum ReviewedOperationKind {
    Create,
    Manifest,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct ReviewedPromotionOperation {
    pub target_id: String,
    pub relative_path: String,
    pub kind: ReviewedOperationKind,
    pub old_sha256: Option<String>,
    pub new_sha256: String,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PromotionMode {
    DryRun,
    Apply,
}

/// The alternate 1.19.2 bundle task is accepted only on the Apply route
/// after the CLI has verified the reviewed candidate lock and profile inputs.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum ProductionTaskPolicy {
    LegacyOnly,
    VerifiedCandidateLock,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct TargetPromotionReport {
    pub created: Vec<String>,
    pub updated: Vec<String>,
    pub removed: Vec<String>,
    pub unchanged: Vec<String>,
    pub accepted_identical_edits: Vec<String>,
    pub old_manifest_sha256: String,
    pub candidate_manifest_sha256: String,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct PromotionReport {
    pub head_commit: String,
    pub source_manifest_sha256: String,
    pub old_preset_id: String,
    pub old_definition_identity: String,
    pub candidate_preset_id: String,
    pub candidate_definition_identity: String,
    pub targets: BTreeMap<String, TargetPromotionReport>,
    /// Apply retains the complete journal and backups for recovery review.
    pub recovery_stage: Option<PathBuf>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
enum OperationKind {
    Create,
    Update,
    Remove,
    Manifest,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct PlannedOperation {
    target_id: String,
    project_dir: String,
    relative_path: String,
    kind: OperationKind,
    old_sha256: Option<String>,
    new_bytes: Option<Vec<u8>>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct PromotionPlan {
    report: PromotionReport,
    operations: Vec<PlannedOperation>,
}

struct TargetManifests {
    destination_root: PathBuf,
    candidate_root: PathBuf,
    old_bytes: Vec<u8>,
    candidate_bytes: Vec<u8>,
    old: ProjectionProvenance,
    candidate: ProjectionProvenance,
}

struct TargetInspection {
    report: TargetPromotionReport,
    file_operations: Vec<PlannedOperation>,
    manifest_operation: PlannedOperation,
}

#[derive(Facet)]
struct PromotionJournal {
    schema: String,
    head_commit: String,
    source_manifest_sha256: String,
    old_manifest_sha256: BTreeMap<String, String>,
    candidate_manifest_sha256: BTreeMap<String, String>,
    old_preset_id: String,
    old_definition_identity: String,
    candidate_preset_id: String,
    candidate_definition_identity: String,
    operations: Vec<JournalOperation>,
}

#[derive(Facet)]
struct JournalOperation {
    target_id: String,
    project_dir: String,
    relative_path: String,
    kind: String,
    old_sha256: Option<String>,
    new_sha256: Option<String>,
    staged_file: Option<String>,
    backup_file: Option<String>,
    quarantine_file: Option<String>,
}

struct AppliedOperation {
    index: usize,
    destination: PathBuf,
    backup: Option<PathBuf>,
    quarantine: PathBuf,
    installed: Option<InstalledFile>,
}

struct InstalledFile {
    // This keeps the installed inode/file ID allocated even after the staged
    // hard link is removed, so rollback can detect a same-byte replacement.
    handle: fs::File,
    sha256: String,
}

struct ExpectedCandidateFile {
    destination: PathBuf,
    target_id: String,
    relative_path: String,
    sha256: String,
}

/// These handles prevent Windows from renaming or deleting any directory used
/// by path-based installation and rollback. Opening reparse points themselves
/// lets us reject junctions as well as directory symlinks.
/// The repository root is the anchor; its ancestors are outside this guard.
#[derive(Default)]
struct PinnedDirectories {
    #[cfg(windows)]
    handles: BTreeMap<PathBuf, fs::File>,
}

impl PinnedDirectories {
    #[cfg(windows)]
    fn pin_existing(&mut self, path: &Path) -> Result<()> {
        // FILE_SHARE_READ | FILE_SHARE_WRITE intentionally excludes
        // FILE_SHARE_DELETE. BACKUP_SEMANTICS opens directories, and
        // OPEN_REPARSE_POINT makes metadata describe the link itself.
        const SHARE_WITHOUT_DELETE: u32 = 0x0000_0001 | 0x0000_0002;
        const OPEN_DIRECTORY_AND_REPARSE_POINT: u32 = 0x0200_0000 | 0x0020_0000;
        const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0000_0400;
        if self.handles.contains_key(path) {
            return Ok(());
        }
        let handle = fs::OpenOptions::new()
            .read(true)
            .share_mode(SHARE_WITHOUT_DELETE)
            .custom_flags(OPEN_DIRECTORY_AND_REPARSE_POINT)
            .open(path)
            .wrap_err_with(|| format!("could not pin promotion directory '{}'", path.display()))?;
        let metadata = handle.metadata()?;
        ensure!(
            metadata.is_dir() && metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT == 0,
            "promotion parent is a reparse point or non-directory: '{}'",
            path.display()
        );
        self.handles.insert(path.to_path_buf(), handle);
        Ok(())
    }

    #[cfg(not(windows))]
    fn pin_existing(&mut self, path: &Path) -> Result<()> {
        let metadata = fs::symlink_metadata(path)?;
        ensure!(
            metadata.is_dir() && !metadata.file_type().is_symlink(),
            "promotion parent is a symlink or non-directory: '{}'",
            path.display()
        );
        Ok(())
    }

    fn pin_or_create_below(&mut self, root: &Path, parent: &Path) -> Result<()> {
        let relative = parent
            .strip_prefix(root)
            .wrap_err("promotion output parent is outside the repository")?;
        self.pin_existing(root)?;
        let mut current = root.to_path_buf();
        for component in relative.components() {
            ensure!(
                matches!(component, Component::Normal(_)),
                "promotion output parent is not a normal repository path"
            );
            current.push(component.as_os_str());
            match fs::create_dir(&current) {
                Ok(()) => {}
                Err(error) if error.kind() == ErrorKind::AlreadyExists => {}
                Err(error) => {
                    return Err(error).wrap_err("could not create promotion output parent");
                }
            }
            self.pin_existing(&current)?;
        }
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum ApplyHookPoint {
    AfterReadBeforeBackup,
    BeforeInstall,
    AfterRollbackObservedAbsent,
}

type ApplyHook<'a> = dyn Fn(usize, ApplyHookPoint, &Path) -> Result<()> + 'a;

/// Inspect all ten candidate and checked-in roots without changing them.
///
/// Apply is available only through the crate-private, transition-specific
/// entry points that require an independent pre-apply candidate verifier.
///
/// # Errors
///
/// Refuses incomplete matrices, stale or unreviewed candidate manifests,
/// altered checked-in manifests or outputs, unowned collisions, symlinks,
/// and any candidate that reuses the old preset identity.
pub fn promote(request: &PromotionRequest, mode: PromotionMode) -> Result<PromotionReport> {
    ensure!(
        mode == PromotionMode::DryRun,
        "source-promotion Apply requires a verified transition-specific entry point"
    );
    run_promotion(request, PromotionMode::DryRun, None, None)
}

/// Apply only a pre-acceptance baseline repair after re-verifying its external
/// candidate at the last boundary before any checked-in file is installed.
/// The callback receives the retained transaction stage so it can report the
/// exact transaction on failure.
pub(crate) fn promote_repair_with_pre_apply_verification(
    request: &PromotionRequest,
    verify: &dyn Fn(&Path) -> Result<()>,
) -> Result<PromotionReport> {
    ensure!(
        matches!(
            request.transition,
            PromotionTransition::PreAcceptanceBaselineRepair { .. }
        ),
        "pre-apply candidate verification is reserved for baseline repair"
    );
    ensure!(
        !request.accept_identical_edits,
        "baseline repair Apply forbids accept_identical_edits"
    );
    run_promotion_with_pre_apply_verification(
        request,
        PromotionMode::Apply,
        None,
        None,
        Some(verify),
        ProductionTaskPolicy::LegacyOnly,
    )
}

/// Apply a new immutable preset only after an external candidate was fully
/// verified and will be verified again after staging, before destination edits.
/// The CLI owns lock reading, reviewed-hash comparison and acknowledgment.
pub(crate) fn promote_new_immutable_with_pre_apply_verification(
    request: &PromotionRequest,
    verify: &dyn Fn(&Path) -> Result<()>,
) -> Result<PromotionReport> {
    ensure!(
        matches!(request.transition, PromotionTransition::NewImmutablePreset),
        "verified immutable-preset Apply requires a new immutable transition"
    );
    ensure!(
        !request.accept_identical_edits,
        "immutable-preset Apply forbids accept_identical_edits"
    );
    run_promotion_with_pre_apply_verification(
        request,
        PromotionMode::Apply,
        None,
        None,
        Some(verify),
        ProductionTaskPolicy::VerifiedCandidateLock,
    )
}

fn run_promotion(
    request: &PromotionRequest,
    mode: PromotionMode,
    fail_after: Option<usize>,
    hook: Option<&ApplyHook<'_>>,
) -> Result<PromotionReport> {
    run_promotion_with_pre_apply_verification(
        request,
        mode,
        fail_after,
        hook,
        None,
        ProductionTaskPolicy::LegacyOnly,
    )
}

type PreApplyVerification<'a> = &'a dyn Fn(&Path) -> Result<()>;

fn run_promotion_with_pre_apply_verification(
    request: &PromotionRequest,
    mode: PromotionMode,
    fail_after: Option<usize>,
    hook: Option<&ApplyHook<'_>>,
    pre_apply_verify: Option<PreApplyVerification<'_>>,
    production_task_policy: ProductionTaskPolicy,
) -> Result<PromotionReport> {
    ensure!(
        production_task_policy == ProductionTaskPolicy::LegacyOnly
            || (mode == PromotionMode::Apply && pre_apply_verify.is_some()),
        "bundled production task requires verified candidate-lock Apply"
    );
    let first = preflight(request, production_task_policy)?;
    if mode == PromotionMode::DryRun {
        return Ok(first.report);
    }

    let repository_root = checked_existing_root(&request.repository_root)?;
    let mut pinned = PinnedDirectories::default();
    let stage_parent = repository_root.join("platform/minecraft");
    pinned.pin_or_create_below(&repository_root, &stage_parent)?;
    ensure_committed_compatibility_evidence(&repository_root, request)?;
    let stage = tempfile::Builder::new()
        .prefix(".sfm-source-promotion-stage-")
        .tempdir_in(&stage_parent)
        .wrap_err("could not create source-promotion stage")?
        .keep();
    pinned
        .pin_existing(&stage)
        .wrap_err_with(|| format!("promotion stage retained at '{}'", stage.display()))?;
    pinned
        .pin_or_create_below(&stage, &stage.join("files"))
        .wrap_err_with(|| format!("promotion stage retained at '{}'", stage.display()))?;
    pinned
        .pin_or_create_below(&stage, &stage.join("backup"))
        .wrap_err_with(|| format!("promotion stage retained at '{}'", stage.display()))?;
    // Keep the stage even if staging or rollback fails. It contains the
    // transaction journal and, after mutation starts, any remaining backups.
    let staging_result: Result<()> = (|| {
        for (index, operation) in first.operations.iter().enumerate() {
            if let Some(bytes) = &operation.new_bytes {
                let staged = stage.join("files").join(index.to_string());
                fs::write(&staged, bytes).wrap_err("could not stage candidate output")?;
                #[cfg(unix)]
                if operation.relative_path == "gradlew" {
                    use std::os::unix::fs::PermissionsExt as _;
                    fs::set_permissions(&staged, fs::Permissions::from_mode(0o755))?;
                }
            }
        }
        write_journal(&stage, &first)
    })();
    staging_result
        .wrap_err_with(|| format!("promotion stage retained at '{}'", stage.display()))?;

    verify_before_destination_changes(
        request,
        &first,
        &stage,
        pre_apply_verify,
        production_task_policy,
    )?;

    let expected_files = expected_candidate_files(&repository_root, &first)
        .wrap_err_with(|| format!("promotion stage retained at '{}'", stage.display()))?;
    pin_output_parents(&repository_root, &first, &expected_files, &mut pinned)
        .wrap_err_with(|| format!("promotion stage retained at '{}'", stage.display()))?;

    let mut applied = Vec::new();
    let apply_result: Result<()> = (|| {
        for (index, operation) in first.operations.iter().enumerate() {
            if fail_after == Some(index) {
                bail!("injected promotion failure after {index} operations");
            }
            ensure_reviewed_inputs_current(&repository_root, request)?;
            apply_one(
                &repository_root,
                &stage,
                index,
                operation,
                &pinned,
                &mut applied,
                hook,
            )?;
        }
        ensure_reviewed_inputs_current(&repository_root, request)?;
        verify_candidate_files(&expected_files)?;
        verify_promoted_input_closure(&repository_root, &first)?;
        // Other processes can still change files after these final checks;
        // Apply does not provide atomicity against hostile concurrent writers.
        fs::write(stage.join("complete"), b"all operations installed\n")
            .wrap_err("could not mark source promotion complete")?;
        Ok(())
    })();
    if let Err(error) = apply_result {
        let rollback_errors = rollback(&applied, &pinned, hook);
        if rollback_errors.is_empty() {
            let _ = fs::write(
                stage.join("rolled-back"),
                b"all applied operations restored\n",
            );
            return Err(error.wrap_err(format!(
                "promotion failed and was rolled back; journal retained at '{}'",
                stage.display()
            )));
        }
        return Err(error.wrap_err(format!(
            "promotion rollback failed: {}; recover using journal and backups at '{}'",
            rollback_errors.join("; "),
            stage.display()
        )));
    }
    let mut report = first.report;
    report.recovery_stage = Some(stage);
    Ok(report)
}

fn ensure_committed_compatibility_evidence(root: &Path, request: &PromotionRequest) -> Result<()> {
    let evidence_path = &request.compatibility_evidence_relative_path;
    let working_evidence = read_required(&root.join(evidence_path))?;
    ensure!(
        working_evidence == git_head_file(root, evidence_path)?,
        "compatibility evidence for Apply must be committed unchanged in reviewed HEAD"
    );
    Ok(())
}

fn verify_before_destination_changes(
    request: &PromotionRequest,
    first: &PromotionPlan,
    stage: &Path,
    pre_apply_verify: Option<PreApplyVerification<'_>>,
    production_task_policy: ProductionTaskPolicy,
) -> Result<()> {
    // No destination changes precede this second, complete ten-target pass.
    let second = preflight(request, production_task_policy)
        .wrap_err_with(|| format!("promotion stage retained at '{}'", stage.display()))?;
    ensure!(
        second == *first,
        "promotion input changed while staging; no checked-in root was written; stage retained at '{}'",
        stage.display()
    );
    if let Some(verify) = pre_apply_verify {
        verify(stage).wrap_err_with(|| {
            format!(
                "pre-apply candidate verification failed; no checked-in root was written; promotion stage retained at '{}'",
                stage.display()
            )
        })?;
    }
    Ok(())
}

fn ensure_reviewed_inputs_current(root: &Path, request: &PromotionRequest) -> Result<()> {
    ensure!(
        git_head_commit(root)? == request.reviewed_head_commit,
        "repository HEAD differs from reviewed promotion base commit during apply"
    );
    ensure_index_matches_head(root, &request.compatibility_evidence_relative_path)?;
    ensure!(
        sha256(&read_required(&root.join(SOURCE_MANIFEST))?)
            == request.reviewed_source_manifest_sha256,
        "source-projection definition differs from reviewed candidate lock during apply"
    );
    let evidence_path = &request.compatibility_evidence_relative_path;
    let evidence = read_required(&root.join(evidence_path))?;
    ensure!(
        sha256(&evidence) == request.reviewed_compatibility_evidence_sha256
            && evidence == git_head_file(root, evidence_path)?,
        "compatibility evidence differs from reviewed HEAD during apply"
    );
    Ok(())
}

fn expected_candidate_files(
    root: &Path,
    plan: &PromotionPlan,
) -> Result<Vec<ExpectedCandidateFile>> {
    let mut expected = Vec::new();
    let mut manifest_count = 0;
    for operation in &plan.operations {
        if operation.kind != OperationKind::Manifest {
            continue;
        }
        manifest_count += 1;
        let manifest_bytes = operation
            .new_bytes
            .as_deref()
            .ok_or_else(|| eyre::eyre!("candidate manifest operation lacks bytes"))?;
        let manifest = parse_manifest(manifest_bytes)?;
        let project_root = root.join(&operation.project_dir);
        for (relative_path, provenance) in manifest.files {
            expected.push(ExpectedCandidateFile {
                destination: project_root.join(&relative_path),
                target_id: operation.target_id.clone(),
                relative_path,
                sha256: provenance.output_sha256,
            });
        }
        expected.push(ExpectedCandidateFile {
            destination: project_root.join(MANIFEST_FILE),
            target_id: operation.target_id.clone(),
            relative_path: MANIFEST_FILE.to_owned(),
            sha256: sha256(manifest_bytes),
        });
    }
    ensure!(
        manifest_count == MATRIX_SIZE,
        "promotion plan lacks a candidate manifest for every target"
    );
    Ok(expected)
}

fn pin_output_parents(
    root: &Path,
    plan: &PromotionPlan,
    expected: &[ExpectedCandidateFile],
    pinned: &mut PinnedDirectories,
) -> Result<()> {
    // Prepare every output parent before the first file mutation. Each new
    // segment is created beneath an already-pinned parent and pinned at once.
    for operation in &plan.operations {
        let destination = root
            .join(&operation.project_dir)
            .join(&operation.relative_path);
        pinned.pin_or_create_below(root, destination.parent().expect("output has parent"))?;
    }
    for file in expected {
        pinned.pin_or_create_below(
            root,
            file.destination
                .parent()
                .expect("candidate output has parent"),
        )?;
    }
    Ok(())
}

fn verify_candidate_files(expected: &[ExpectedCandidateFile]) -> Result<()> {
    for file in expected {
        ensure!(
            sha256(&read_required(&file.destination)?) == file.sha256,
            "candidate-owned output '{}/{}' changed during apply",
            file.target_id,
            file.relative_path
        );
    }
    Ok(())
}

fn verify_promoted_input_closure(root: &Path, plan: &PromotionPlan) -> Result<()> {
    let mut manifest_count = 0;
    for operation in &plan.operations {
        if operation.kind != OperationKind::Manifest {
            continue;
        }
        manifest_count += 1;
        let manifest_bytes = operation
            .new_bytes
            .as_deref()
            .ok_or_else(|| eyre::eyre!("candidate manifest operation lacks bytes"))?;
        let manifest = parse_manifest(manifest_bytes)?;
        let destination_root = root.join(&operation.project_dir);
        ensure_closed_destination_inputs(&destination_root, &manifest)?;
    }
    ensure!(
        manifest_count == MATRIX_SIZE,
        "promotion plan lacks a candidate manifest for every target"
    );
    Ok(())
}

fn preflight(
    request: &PromotionRequest,
    production_task_policy: ProductionTaskPolicy,
) -> Result<PromotionPlan> {
    let (root, source_manifest, head_commit, source_manifest_sha256) = reviewed_matrix(request)?;

    let mut report = PromotionReport {
        head_commit,
        source_manifest_sha256,
        candidate_preset_id: request.candidate_preset_id.clone(),
        candidate_definition_identity: request.candidate_definition_identity.clone(),
        ..PromotionReport::default()
    };
    let mut file_operations = Vec::new();
    let mut manifest_operations = Vec::new();
    let mut seen_candidate_roots: Vec<(String, PathBuf)> = Vec::new();
    for target in &source_manifest.targets {
        let candidate_root = checked_existing_root(&request.candidates[&target.id].project_root)?;
        for (other_target, other_root) in &seen_candidate_roots {
            ensure!(
                !is_within(&candidate_root, other_root) && !is_within(other_root, &candidate_root),
                "candidate roots for '{other_target}' and '{}' overlap",
                target.id
            );
        }
        seen_candidate_roots.push((target.id.clone(), candidate_root));
        let inspected = inspect_target(
            &root,
            target,
            &request.candidates[&target.id],
            request,
            &mut report,
            production_task_policy,
        )?;
        file_operations.extend(inspected.file_operations);
        manifest_operations.push(inspected.manifest_operation);
        report.targets.insert(target.id.clone(), inspected.report);
    }
    file_operations.extend(manifest_operations);
    validate_transition(request, &report, &file_operations)?;
    Ok(PromotionPlan {
        report,
        operations: file_operations,
    })
}

fn reviewed_matrix(
    request: &PromotionRequest,
) -> Result<(PathBuf, SourceProjectionManifest, String, String)> {
    let root = checked_existing_root(&request.repository_root)?;
    ensure_git_toplevel(&root)?;
    let head_commit = git_head_commit(&root)?;
    ensure!(
        head_commit == request.reviewed_head_commit,
        "repository HEAD differs from reviewed promotion base commit"
    );
    validate_relative_path(&request.compatibility_evidence_relative_path)?;
    validate_digest(&request.reviewed_compatibility_evidence_sha256)?;
    ensure_index_matches_head(&root, &request.compatibility_evidence_relative_path)?;
    let source_manifest_bytes = read_required(&root.join(SOURCE_MANIFEST))?;
    validate_digest(&request.reviewed_source_manifest_sha256)?;
    let source_manifest_sha256 = sha256(&source_manifest_bytes);
    ensure!(
        source_manifest_sha256 == request.reviewed_source_manifest_sha256,
        "source-projection definition differs from reviewed candidate lock"
    );
    inspect_output_parents(&root, &request.compatibility_evidence_relative_path)?;
    let evidence_bytes = read_required(&root.join(&request.compatibility_evidence_relative_path))?;
    ensure!(
        sha256(&evidence_bytes) == request.reviewed_compatibility_evidence_sha256,
        "compatibility evidence differs from reviewed SHA-256 at '{}'",
        request.compatibility_evidence_relative_path
    );
    let source_manifest =
        SourceProjectionManifest::from_json(std::str::from_utf8(&source_manifest_bytes)?)?;
    validate_matrix(&source_manifest, request)?;
    Ok((root, source_manifest, head_commit, source_manifest_sha256))
}

fn validate_matrix(
    source_manifest: &SourceProjectionManifest,
    request: &PromotionRequest,
) -> Result<()> {
    ensure!(
        source_manifest.targets.len() == MATRIX_SIZE,
        "release promotion requires the complete {MATRIX_SIZE}-target matrix"
    );
    let target_ids: BTreeSet<_> = source_manifest
        .targets
        .iter()
        .map(|target| target.id.clone())
        .collect();
    let candidate_ids: BTreeSet<_> = request.candidates.keys().cloned().collect();
    ensure!(
        target_ids == candidate_ids,
        "candidate targets do not exactly match the ten-target projection matrix: missing [{}], extra [{}]",
        target_ids
            .difference(&candidate_ids)
            .cloned()
            .collect::<Vec<_>>()
            .join(", "),
        candidate_ids
            .difference(&target_ids)
            .cloned()
            .collect::<Vec<_>>()
            .join(", ")
    );
    let preset = source_manifest.preset(&request.candidate_preset_id)?;
    ensure!(
        preset.identity == request.candidate_definition_identity,
        "candidate preset definition identity does not match the repository declaration"
    );
    ensure!(
        preset.targets.iter().cloned().collect::<BTreeSet<_>>() == target_ids,
        "candidate preset does not select the entire ten-target matrix"
    );
    Ok(())
}

fn load_target_manifests(
    root: &Path,
    target: &ProjectionTarget,
    candidate: &PromotionCandidate,
    request: &PromotionRequest,
    production_task_policy: ProductionTaskPolicy,
) -> Result<TargetManifests> {
    let target_id = &target.id;
    ensure!(
        target.project_dir == format!("platform/minecraft/mc-version/{target_id}"),
        "target '{target_id}' has a nonstandard checked-in destination"
    );
    let destination_root = root.join(&target.project_dir);
    inspect_directory_chain(&destination_root)?;
    validate_digest(&candidate.reviewed_manifest_sha256)?;
    let candidate_root = checked_existing_root(&candidate.project_root)?;
    ensure!(
        !is_within(&candidate_root, root) && !is_within(root, &candidate_root),
        "candidate root for '{target_id}' must be external to the repository"
    );
    validate_candidate_artifact(&candidate_root, target, candidate, production_task_policy)?;
    let candidate_bytes = read_required(&candidate_root.join(MANIFEST_FILE))?;
    ensure!(
        sha256(&candidate_bytes) == candidate.reviewed_manifest_sha256,
        "candidate manifest hash mismatch for '{target_id}'"
    );
    let candidate_manifest = parse_manifest(&candidate_bytes)?;
    ensure!(
        candidate_manifest.target_id == *target_id
            && candidate_manifest.minecraft_version == target.minecraft_version
            && candidate_manifest.preset_id == request.candidate_preset_id
            && candidate_manifest.preset_definition_identity
                == request.candidate_definition_identity,
        "candidate manifest identity mismatch for '{target_id}'"
    );

    let old_bytes = read_required(&destination_root.join(MANIFEST_FILE))?;
    let committed_bytes = git_head_file(root, &format!("{}/{MANIFEST_FILE}", target.project_dir))?;
    ensure!(
        old_bytes == committed_bytes,
        "checked-in provenance manifest for '{target_id}' differs from HEAD; reconcile contributor edits first"
    );
    let old_manifest = parse_manifest(&old_bytes)?;
    ensure!(
        old_manifest.target_id == *target_id
            && old_manifest.minecraft_version == target.minecraft_version,
        "checked-in manifest identity mismatch for '{target_id}'"
    );
    Ok(TargetManifests {
        destination_root,
        candidate_root,
        old_bytes,
        candidate_bytes,
        old: old_manifest,
        candidate: candidate_manifest,
    })
}

fn validate_candidate_artifact(
    candidate_root: &Path,
    target: &ProjectionTarget,
    candidate: &PromotionCandidate,
    production_task_policy: ProductionTaskPolicy,
) -> Result<()> {
    let expected_task = expected_production_task(&target.id)?;
    let verified_bundle_task = production_task_policy
        == ProductionTaskPolicy::VerifiedCandidateLock
        && target.id == "1.19.2"
        && candidate.production_task == "reobfJarJar";
    ensure!(
        candidate.production_task == expected_task || verified_bundle_task,
        "candidate production task mismatch for '{}': expected '{expected_task}'",
        target.id
    );
    ensure!(
        candidate.jdk_major == target.java_major,
        "candidate JDK major mismatch for '{}': expected {}",
        target.id,
        target.java_major
    );
    let build_id = &candidate.jdk_build_id;
    ensure!(
        !build_id.is_empty()
            && build_id.len() <= 128
            && build_id
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"._+-".contains(&byte)),
        "invalid candidate JDK build ID for '{}'",
        target.id
    );
    let (_, version) = build_id
        .rsplit_once('-')
        .ok_or_else(|| eyre::eyre!("candidate JDK build ID for '{}' lacks a version", target.id))?;
    ensure!(
        version.split('.').next() == Some(target.java_major.to_string().as_str()),
        "candidate JDK build ID major mismatch for '{}': expected {}",
        target.id,
        target.java_major
    );
    let path = &candidate.production_jar_relative_path;
    validate_relative_path(path)?;
    let segments = path.split('/').collect::<Vec<_>>();
    ensure!(
        matches!(segments.as_slice(), ["build", "libs", filename]
            if Path::new(filename)
                .extension()
                .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
                && *filename != ".jar"),
        "candidate production JAR for '{}' must be a direct build/libs/*.jar file",
        target.id
    );
    validate_digest(&candidate.production_jar_sha256)?;
    inspect_output_parents(candidate_root, path)?;
    let jar_bytes = read_required(&candidate_root.join(path))?;
    ensure!(
        sha256(&jar_bytes) == candidate.production_jar_sha256,
        "candidate production JAR hash mismatch for '{}' at '{path}'",
        target.id
    );
    Ok(())
}

fn expected_production_task(target_id: &str) -> Result<&'static str> {
    Ok(match target_id {
        "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => "reobfJar",
        "1.20.2" | "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" => "jar",
        "26.1.2" => "jarJar",
        _ => bail!("unsupported production JAR target '{target_id}'"),
    })
}

fn check_old_identity(report: &mut PromotionReport, old: &ProjectionProvenance) -> Result<()> {
    if report.old_preset_id.is_empty() {
        report.old_preset_id.clone_from(&old.preset_id);
        report
            .old_definition_identity
            .clone_from(&old.preset_definition_identity);
    } else {
        ensure!(
            old.preset_id == report.old_preset_id
                && old.preset_definition_identity == report.old_definition_identity,
            "checked-in roots do not share one old preset and definition identity"
        );
    }
    Ok(())
}

fn read_candidate_files(
    candidate_root: &Path,
    manifest: &ProjectionProvenance,
    target_id: &str,
) -> Result<BTreeMap<String, Vec<u8>>> {
    let mut files = BTreeMap::new();
    for (path, provenance) in &manifest.files {
        inspect_output_parents(candidate_root, path)?;
        let bytes = read_required(&candidate_root.join(path))?;
        ensure!(
            sha256(&bytes) == provenance.output_sha256,
            "candidate output '{target_id}/{path}' differs from its provenance hash"
        );
        files.insert(path.clone(), bytes);
    }
    Ok(files)
}

fn inspect_target(
    root: &Path,
    target: &ProjectionTarget,
    candidate: &PromotionCandidate,
    request: &PromotionRequest,
    report: &mut PromotionReport,
    production_task_policy: ProductionTaskPolicy,
) -> Result<TargetInspection> {
    let target_id = &target.id;
    let TargetManifests {
        destination_root,
        candidate_root,
        old_bytes,
        candidate_bytes,
        old,
        candidate,
    } = load_target_manifests(root, target, candidate, request, production_task_policy)?;
    check_old_identity(report, &old)?;
    ensure_closed_destination_inputs(&destination_root, &old)?;
    let mut target_report = TargetPromotionReport {
        old_manifest_sha256: sha256(&old_bytes),
        candidate_manifest_sha256: sha256(&candidate_bytes),
        ..TargetPromotionReport::default()
    };
    let candidate_files = read_candidate_files(&candidate_root, &candidate, target_id)?;
    // Reject case-only or file/directory changes between generations.
    // They require a separately reviewed migration on Windows.
    validate_path_set(old.files.keys().chain(candidate.files.keys()))?;
    let mut file_operations = Vec::new();
    inspect_old_outputs(
        target,
        &destination_root,
        &old,
        &candidate_files,
        request.accept_identical_edits,
        &mut target_report,
        &mut file_operations,
    )?;
    inspect_candidate_outputs(
        target,
        &destination_root,
        &old,
        candidate_files,
        &mut target_report,
        &mut file_operations,
    )?;
    Ok(TargetInspection {
        report: target_report,
        file_operations,
        manifest_operation: PlannedOperation {
            target_id: target_id.clone(),
            project_dir: target.project_dir.clone(),
            relative_path: MANIFEST_FILE.to_owned(),
            kind: OperationKind::Manifest,
            old_sha256: Some(sha256(&old_bytes)),
            new_bytes: Some(candidate_bytes),
        },
    })
}

fn inspect_old_outputs(
    target: &ProjectionTarget,
    destination_root: &Path,
    old: &ProjectionProvenance,
    candidate_files: &BTreeMap<String, Vec<u8>>,
    accept_identical_edits: bool,
    report: &mut TargetPromotionReport,
    operations: &mut Vec<PlannedOperation>,
) -> Result<()> {
    for (path, provenance) in &old.files {
        inspect_output_parents(destination_root, path)?;
        let actual = read_required(&destination_root.join(path))?;
        if sha256(&actual) != provenance.output_sha256 {
            let identical = candidate_files
                .get(path)
                .is_some_and(|bytes| *bytes == actual);
            ensure!(
                accept_identical_edits && identical,
                "checked-in output '{}/{path}' was edited; backpropagate it or explicitly accept candidate-identical bytes",
                target.id
            );
            report.accepted_identical_edits.push(path.clone());
        }
        if !candidate_files.contains_key(path) {
            report.removed.push(path.clone());
            operations.push(PlannedOperation {
                target_id: target.id.clone(),
                project_dir: target.project_dir.clone(),
                relative_path: path.clone(),
                kind: OperationKind::Remove,
                old_sha256: Some(sha256(&actual)),
                new_bytes: None,
            });
        }
    }
    Ok(())
}

fn inspect_candidate_outputs(
    target: &ProjectionTarget,
    destination_root: &Path,
    old: &ProjectionProvenance,
    candidate_files: BTreeMap<String, Vec<u8>>,
    report: &mut TargetPromotionReport,
    operations: &mut Vec<PlannedOperation>,
) -> Result<()> {
    for (path, bytes) in candidate_files {
        inspect_output_parents(destination_root, &path)?;
        let actual = read_optional(&destination_root.join(&path))?;
        match (old.files.contains_key(&path), actual) {
            (false, Some(_)) => bail!(
                "candidate output '{}/{path}' collides with an unowned checked-in file",
                target.id
            ),
            (false, None) => {
                report.created.push(path.clone());
                operations.push(PlannedOperation {
                    target_id: target.id.clone(),
                    project_dir: target.project_dir.clone(),
                    relative_path: path,
                    kind: OperationKind::Create,
                    old_sha256: None,
                    new_bytes: Some(bytes),
                });
            }
            (true, Some(actual)) if actual == bytes => report.unchanged.push(path),
            (true, Some(actual)) => {
                report.updated.push(path.clone());
                operations.push(PlannedOperation {
                    target_id: target.id.clone(),
                    project_dir: target.project_dir.clone(),
                    relative_path: path,
                    kind: OperationKind::Update,
                    old_sha256: Some(sha256(&actual)),
                    new_bytes: Some(bytes),
                });
            }
            (true, None) => bail!("previously owned output '{}/{path}' is missing", target.id),
        }
    }
    Ok(())
}

/// A destination may retain old-owned files until their planned removal, but
/// must contain no other project inputs. Gradle output directories are skipped
/// by the same exact root-name rule as candidate verification.
pub(crate) fn ensure_closed_destination_inputs(
    root: &Path,
    ownership: &ProjectionProvenance,
) -> Result<()> {
    for entry in fs::read_dir(root)
        .wrap_err_with(|| format!("cannot inspect checked-in project '{}'", root.display()))?
    {
        let entry = entry?;
        let name = entry
            .file_name()
            .into_string()
            .map_err(|bad| eyre::eyre!("checked-in project has a non-UTF-8 root entry: {bad:?}"))?;
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            !is_reparse_input(&metadata),
            "checked-in project input is a reparse point at '{}/{}'",
            ownership.target_id,
            name
        );
        if metadata.is_dir() {
            if matches!(
                name.as_str(),
                "build" | ".gradle" | "run" | "runs" | "logs" | "runGameTest"
            ) {
                continue;
            }
            ensure!(
                name == "src"
                    || name == "gradle"
                    || ownership
                        .files
                        .keys()
                        .any(|path| path.starts_with(&format!("{name}/"))),
                "unowned checked-in project input directory '{}/{}'",
                ownership.target_id,
                name
            );
            for child in WalkDir::new(entry.path()).follow_links(false) {
                let child = child.wrap_err("cannot walk checked-in project inputs")?;
                let metadata = fs::symlink_metadata(child.path())?;
                let relative = child.path().strip_prefix(root)?;
                let relative = relative
                    .to_str()
                    .ok_or_else(|| eyre::eyre!("checked-in project input path is not UTF-8"))?
                    .replace('\\', "/");
                ensure!(
                    !is_reparse_input(&metadata),
                    "checked-in project input is a reparse point at '{}/{}'",
                    ownership.target_id,
                    relative
                );
                if metadata.is_dir() {
                    continue;
                }
                ensure!(
                    metadata.is_file(),
                    "checked-in project input is not regular"
                );
                validate_relative_path(&relative)?;
                ensure!(
                    ownership.files.contains_key(&relative),
                    "unowned checked-in project input '{}/{}'",
                    ownership.target_id,
                    relative
                );
            }
        } else {
            ensure!(
                metadata.is_file(),
                "checked-in project input is not regular"
            );
            ensure!(
                name == MANIFEST_FILE || ownership.files.contains_key(&name),
                "unowned checked-in project input '{}/{}'",
                ownership.target_id,
                name
            );
        }
    }
    Ok(())
}

fn is_reparse_input(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        metadata.file_attributes() & 0x0000_0400 != 0
    }
    #[cfg(not(windows))]
    {
        false
    }
}

fn validate_transition(
    request: &PromotionRequest,
    report: &PromotionReport,
    file_operations: &[PlannedOperation],
) -> Result<()> {
    match &request.transition {
        PromotionTransition::NewImmutablePreset => ensure!(
            report.old_preset_id != request.candidate_preset_id
                && report.old_definition_identity != request.candidate_definition_identity,
            "immutable-preset promotion cannot reuse a checked-in preset ID or definition identity"
        ),
        PromotionTransition::PreAcceptanceBaselineRepair {
            expected_old_preset_id,
            expected_old_definition_identity,
            reviewed_operations,
        } => {
            ensure!(
                report.old_preset_id == REPAIR_PRESET_ID
                    && report.old_preset_id == *expected_old_preset_id
                    && report.old_definition_identity == *expected_old_definition_identity
                    && report.old_preset_id == request.candidate_preset_id
                    && report.old_definition_identity != request.candidate_definition_identity,
                "pre-acceptance baseline repair requires the exact reviewed old 4.34.0 projection identity, the same preset ID, and a different new definition identity"
            );
            validate_repair_operations(report, file_operations, reviewed_operations)?;
        }
    }
    Ok(())
}

fn validate_repair_operations(
    report: &PromotionReport,
    operations: &[PlannedOperation],
    reviewed: &[ReviewedPromotionOperation],
) -> Result<()> {
    let required_targets = REPAIR_TARGETS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    ensure!(
        report.targets.keys().cloned().collect::<BTreeSet<_>>() == required_targets,
        "pre-acceptance repair requires the exact 4.34.0 ten-target matrix"
    );
    ensure!(
        operations.len() == MATRIX_SIZE + REPAIR_REFMAP_TARGETS.len()
            && reviewed.len() == operations.len(),
        "pre-acceptance repair requires exactly ten manifest updates and five refmap creates"
    );
    let required_refmap_targets = REPAIR_REFMAP_TARGETS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let mut manifest_targets = BTreeSet::new();
    let mut refmap_targets = BTreeSet::new();
    let mut actual_reviewed = Vec::new();
    for operation in operations {
        let kind = match operation.kind {
            OperationKind::Manifest if operation.relative_path == MANIFEST_FILE => {
                ensure!(
                    operation.old_sha256.is_some(),
                    "repair manifest update lacks an old hash"
                );
                manifest_targets.insert(operation.target_id.clone());
                ReviewedOperationKind::Manifest
            }
            OperationKind::Create if operation.relative_path == REPAIR_REFMAP_PATH => {
                ensure!(
                    operation.old_sha256.is_none(),
                    "repair refmap create unexpectedly replaces an old output"
                );
                refmap_targets.insert(operation.target_id.clone());
                ReviewedOperationKind::Create
            }
            _ => bail!(
                "pre-acceptance repair contains a non-refmap output delta: '{}/{}'",
                operation.target_id,
                operation.relative_path
            ),
        };
        let new_bytes = operation
            .new_bytes
            .as_ref()
            .ok_or_else(|| eyre::eyre!("repair operation has no candidate bytes"))?;
        actual_reviewed.push(ReviewedPromotionOperation {
            target_id: operation.target_id.clone(),
            relative_path: operation.relative_path.clone(),
            kind,
            old_sha256: operation.old_sha256.clone(),
            new_sha256: sha256(new_bytes),
        });
    }
    ensure!(
        manifest_targets == required_targets && refmap_targets == required_refmap_targets,
        "pre-acceptance repair refmap or manifest target set differs from the reviewed 4.34.0 correction"
    );
    for operation in reviewed {
        validate_relative_path(&operation.relative_path)?;
        if let Some(old) = &operation.old_sha256 {
            validate_digest(old)?;
        }
        validate_digest(&operation.new_sha256)?;
    }
    actual_reviewed.sort();
    let mut reviewed_sorted = reviewed.to_vec();
    reviewed_sorted.sort();
    ensure!(
        actual_reviewed == reviewed_sorted,
        "pre-acceptance repair operations differ from the exact reviewed operation allowlist"
    );
    Ok(())
}

fn write_journal(stage: &Path, plan: &PromotionPlan) -> Result<()> {
    let journal = PromotionJournal {
        schema: "sfm:source_promotion_journal@1".to_owned(),
        head_commit: plan.report.head_commit.clone(),
        source_manifest_sha256: plan.report.source_manifest_sha256.clone(),
        old_manifest_sha256: plan
            .report
            .targets
            .iter()
            .map(|(id, target)| (id.clone(), target.old_manifest_sha256.clone()))
            .collect(),
        candidate_manifest_sha256: plan
            .report
            .targets
            .iter()
            .map(|(id, target)| (id.clone(), target.candidate_manifest_sha256.clone()))
            .collect(),
        old_preset_id: plan.report.old_preset_id.clone(),
        old_definition_identity: plan.report.old_definition_identity.clone(),
        candidate_preset_id: plan.report.candidate_preset_id.clone(),
        candidate_definition_identity: plan.report.candidate_definition_identity.clone(),
        operations: plan
            .operations
            .iter()
            .enumerate()
            .map(|(index, operation)| JournalOperation {
                target_id: operation.target_id.clone(),
                project_dir: operation.project_dir.clone(),
                relative_path: operation.relative_path.clone(),
                kind: format!("{:?}", operation.kind).to_lowercase(),
                old_sha256: operation.old_sha256.clone(),
                new_sha256: operation.new_bytes.as_ref().map(|bytes| sha256(bytes)),
                staged_file: operation
                    .new_bytes
                    .as_ref()
                    .map(|_| format!("files/{index}")),
                backup_file: operation
                    .old_sha256
                    .as_ref()
                    .map(|_| format!("backup/{index}")),
                quarantine_file: operation
                    .new_bytes
                    .as_ref()
                    .map(|_| format!("backup/{index}.quarantine")),
            })
            .collect(),
    };
    let mut json = facet_json::to_string_pretty(&journal)?;
    json.push('\n');
    fs::write(stage.join("journal.json"), json).wrap_err("could not write promotion journal")
}

fn apply_one(
    root: &Path,
    stage: &Path,
    index: usize,
    operation: &PlannedOperation,
    pinned: &PinnedDirectories,
    applied: &mut Vec<AppliedOperation>,
    hook: Option<&ApplyHook<'_>>,
) -> Result<()> {
    let project_root = root.join(&operation.project_dir);
    inspect_directory_chain(&project_root)?;
    inspect_output_parents(&project_root, &operation.relative_path)?;
    let destination = project_root.join(&operation.relative_path);
    #[cfg(windows)]
    ensure!(
        pinned
            .handles
            .contains_key(destination.parent().expect("output has parent")),
        "promotion output parent was not pinned"
    );
    #[cfg(not(windows))]
    let _ = pinned;
    let actual = read_optional(&destination)?;
    ensure!(
        actual.as_ref().map(|bytes| sha256(bytes)) == operation.old_sha256,
        "promoted output '{}/{}' changed during apply",
        operation.target_id,
        operation.relative_path
    );
    if let Some(hook) = hook {
        hook(index, ApplyHookPoint::AfterReadBeforeBackup, &destination)?;
    }
    let backup = if actual.is_some() {
        let path = stage.join("backup").join(index.to_string());
        fs::rename(&destination, &path).wrap_err("could not back up checked-in output")?;
        Some(path)
    } else {
        None
    };
    applied.push(AppliedOperation {
        index,
        destination: destination.clone(),
        backup,
        quarantine: stage.join("backup").join(format!("{index}.quarantine")),
        installed: None,
    });
    if let Some(backup) = applied.last().and_then(|entry| entry.backup.as_deref()) {
        ensure!(
            Some(sha256(&read_required(backup)?)) == operation.old_sha256,
            "promoted output '{}/{}' changed between inspection and backup; preserving the concurrent edit",
            operation.target_id,
            operation.relative_path
        );
    }
    if let Some(bytes) = &operation.new_bytes {
        let staged = stage.join("files").join(index.to_string());
        let new_hash = sha256(bytes);
        ensure!(
            sha256(&read_required(&staged)?) == new_hash,
            "staged candidate output changed before installation"
        );
        let installed_handle = fs::File::open(&staged)?;
        let staged_identity = file_identity(&installed_handle)?;
        if let Some(hook) = hook {
            hook(index, ApplyHookPoint::BeforeInstall, &destination)?;
        }
        // A same-volume hard link creates the destination only if absent.
        // Unlike rename, it cannot replace a path another writer created
        // after the old output was backed up.
        fs::hard_link(&staged, &destination)
            .wrap_err("could not install candidate output without clobbering a concurrent path")?;
        applied.last_mut().expect("just applied").installed = Some(InstalledFile {
            handle: installed_handle,
            sha256: new_hash.clone(),
        });
        ensure!(
            sha256(&read_required(&destination)?) == new_hash,
            "installed candidate output does not match its reviewed bytes"
        );
        ensure!(
            file_identity(&fs::File::open(&destination)?)? == staged_identity,
            "installed candidate output was replaced before verification"
        );
        fs::remove_file(staged).wrap_err("could not release staged candidate link")?;
    }
    Ok(())
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct FileIdentity {
    volume: u64,
    index: u64,
}

#[cfg(unix)]
fn file_identity(file: &fs::File) -> Result<FileIdentity> {
    use std::os::unix::fs::MetadataExt as _;

    let metadata = file.metadata()?;
    Ok(FileIdentity {
        volume: metadata.dev(),
        index: metadata.ino(),
    })
}

#[cfg(windows)]
#[repr(C)]
struct WinFileInformation {
    _attributes: u32,
    _creation_time: [u32; 2],
    _last_access_time: [u32; 2],
    _last_write_time: [u32; 2],
    volume_serial_number: u32,
    _size_high: u32,
    _size_low: u32,
    _number_of_links: u32,
    file_index_high: u32,
    file_index_low: u32,
}

#[cfg(windows)]
#[link(name = "kernel32")]
unsafe extern "system" {
    #[link_name = "GetFileInformationByHandle"]
    fn get_file_information_by_handle(
        file: *mut std::ffi::c_void,
        information: *mut WinFileInformation,
    ) -> i32;
}

#[cfg(windows)]
fn file_identity(file: &fs::File) -> Result<FileIdentity> {
    use std::os::windows::io::AsRawHandle as _;

    let mut information = std::mem::MaybeUninit::<WinFileInformation>::uninit();
    // SAFETY: `file` owns a valid handle, and the output points to a writable
    // buffer with the Win32 BY_HANDLE_FILE_INFORMATION layout.
    let success =
        unsafe { get_file_information_by_handle(file.as_raw_handle(), information.as_mut_ptr()) };
    if success == 0 {
        return Err(std::io::Error::last_os_error()).wrap_err("could not identify promotion file");
    }
    // SAFETY: the successful Win32 call initialized the complete structure.
    let information = unsafe { information.assume_init() };
    Ok(FileIdentity {
        volume: u64::from(information.volume_serial_number),
        index: (u64::from(information.file_index_high) << 32)
            | u64::from(information.file_index_low),
    })
}

#[cfg(not(any(unix, windows)))]
fn file_identity(_file: &fs::File) -> Result<FileIdentity> {
    bail!("source promotion needs file identity support for this platform")
}

fn rollback(
    applied: &[AppliedOperation],
    _pinned: &PinnedDirectories,
    hook: Option<&ApplyHook<'_>>,
) -> Vec<String> {
    let mut errors = Vec::new();
    for operation in applied.iter().rev() {
        match read_optional(&operation.destination) {
            Ok(Some(_)) if operation.installed.is_some() => {
                // Move the path into the retained stage before checking it.
                // A replacement racing with this move is preserved there.
                if let Err(error) = fs::rename(&operation.destination, &operation.quarantine) {
                    errors.push(format!("{}: {error}", operation.destination.display()));
                    continue;
                }
                let installed = operation.installed.as_ref().expect("checked above");
                let moved_is_ours = (|| -> Result<bool> {
                    let bytes = read_required(&operation.quarantine)?;
                    let moved = fs::File::open(&operation.quarantine)?;
                    Ok(file_identity(&moved)? == file_identity(&installed.handle)?
                        && sha256(&bytes) == installed.sha256)
                })();
                if !matches!(moved_is_ours, Ok(true)) {
                    let reason = match moved_is_ours {
                        Ok(false) => "different file identity or bytes".to_owned(),
                        Err(error) => error.to_string(),
                        Ok(true) => unreachable!(),
                    };
                    let relink = match read_optional(&operation.quarantine) {
                        Ok(Some(_)) => fs::hard_link(&operation.quarantine, &operation.destination)
                            .map_or_else(
                                |error| format!("could not restore path: {error}"),
                                |()| "also restored at destination".to_owned(),
                            ),
                        Ok(None) => "quarantined path disappeared".to_owned(),
                        Err(error) => format!("could not inspect quarantined path: {error}"),
                    };
                    errors.push(format!(
                        "{} changed after installation ({reason}); replacement preserved at '{}' ({relink}); old backup retained",
                        operation.destination.display(),
                        operation.quarantine.display()
                    ));
                    continue;
                }
                // Retain even our own installed file for recovery review. Its
                // open handle also keeps the identity stable through rollback.
            }
            Ok(None) => {}
            Ok(Some(_)) | Err(_) => {
                errors.push(format!(
                    "{} changed after installation; preserving output and backup",
                    operation.destination.display()
                ));
                continue;
            }
        }
        if let Some(backup) = &operation.backup {
            if !matches!(read_optional(&operation.destination), Ok(None)) {
                errors.push(format!(
                    "{} changed before backup restoration; preserving output and backup",
                    operation.destination.display()
                ));
                continue;
            }
            if let Some(hook) = hook
                && let Err(error) = hook(
                    operation.index,
                    ApplyHookPoint::AfterRollbackObservedAbsent,
                    &operation.destination,
                )
            {
                errors.push(format!("{}: {error}", operation.destination.display()));
                continue;
            }
            // Hard linking is an atomic create-if-absent operation on the same
            // volume. Keep the backup if a writer wins this final race.
            if let Err(error) = fs::hard_link(backup, &operation.destination) {
                errors.push(format!(
                    "{}: could not restore backup without replacing a concurrent path: {error}",
                    operation.destination.display()
                ));
            }
        } else if !matches!(read_optional(&operation.destination), Ok(None)) {
            errors.push(format!(
                "{} changed during rollback; preserving the concurrent path",
                operation.destination.display()
            ));
        }
    }
    errors
}

fn parse_manifest(bytes: &[u8]) -> Result<ProjectionProvenance> {
    let text = std::str::from_utf8(bytes).wrap_err("projection manifest is not UTF-8")?;
    let manifest = ProjectionProvenance::from_json(text)?;
    ensure!(
        manifest.to_json()?.as_bytes() == bytes,
        "noncanonical projection manifest bytes"
    );
    ensure!(
        !manifest.files.contains_key(MANIFEST_FILE),
        "projection manifest owns itself"
    );
    validate_path_set(manifest.files.keys())?;
    for file in manifest.files.values() {
        validate_relative_path(&file.source_path)?;
        validate_digest(&file.source_sha256)?;
        validate_digest(&file.output_sha256)?;
    }
    Ok(manifest)
}

fn validate_path_set<'a>(paths: impl IntoIterator<Item = &'a String>) -> Result<()> {
    let mut casefold = BTreeMap::new();
    for path in paths {
        validate_relative_path(path)?;
        ensure!(
            !path
                .split('/')
                .next()
                .is_some_and(|segment| segment.eq_ignore_ascii_case(MANIFEST_FILE)),
            "projection manifest cannot own itself"
        );
        if let Some(existing) = casefold.insert(path.to_lowercase(), path) {
            ensure!(
                existing == path,
                "case-only projection path collision between '{existing}' and '{path}'"
            );
        }
    }
    // Old and new lists may deliberately contain the same exact path.
    for path in casefold.keys() {
        for (index, ch) in path.char_indices() {
            if ch == '/' {
                ensure!(
                    !casefold.contains_key(&path[..index]),
                    "projection file/directory collision at '{path}'"
                );
            }
        }
    }
    Ok(())
}

pub(crate) fn validate_relative_path(path: &str) -> Result<()> {
    ensure!(
        !path.is_empty()
            && !Path::new(path).is_absolute()
            && !path.contains(['\\', ':'])
            && !path.starts_with('/')
            && !path.ends_with('/'),
        "unsafe projection path '{path}'"
    );
    for segment in path.split('/') {
        ensure!(
            !segment.is_empty()
                && segment != "."
                && segment != ".."
                && !segment.ends_with([' ', '.'])
                && !segment
                    .chars()
                    .any(|ch| ch.is_control() || "<>\"|?*".contains(ch)),
            "unsafe projection path '{path}'"
        );
        let stem = segment.split('.').next().unwrap_or("").to_ascii_uppercase();
        ensure!(
            !matches!(
                stem.as_str(),
                "CON"
                    | "PRN"
                    | "AUX"
                    | "NUL"
                    | "COM1"
                    | "COM2"
                    | "COM3"
                    | "COM4"
                    | "COM5"
                    | "COM6"
                    | "COM7"
                    | "COM8"
                    | "COM9"
                    | "LPT1"
                    | "LPT2"
                    | "LPT3"
                    | "LPT4"
                    | "LPT5"
                    | "LPT6"
                    | "LPT7"
                    | "LPT8"
                    | "LPT9"
            ),
            "reserved projection path '{path}'"
        );
    }
    Ok(())
}

fn validate_digest(digest: &str) -> Result<()> {
    let hex = digest.strip_prefix("sha256:").unwrap_or("");
    ensure!(
        hex.len() == 64
            && hex
                .bytes()
                .all(|ch| ch.is_ascii_digit() || (b'a'..=b'f').contains(&ch)),
        "invalid SHA-256 digest"
    );
    Ok(())
}

fn checked_existing_root(path: &Path) -> Result<PathBuf> {
    ensure!(path.is_absolute(), "promotion root must be absolute");
    ensure!(
        !path.components().any(|part| part == Component::ParentDir),
        "promotion root cannot contain '..'"
    );
    inspect_directory_chain(path)?;
    fs::canonicalize(path)
        .wrap_err_with(|| format!("could not resolve promotion root '{}'", path.display()))
}

fn inspect_directory_chain(path: &Path) -> Result<()> {
    for ancestor in path.ancestors() {
        match fs::symlink_metadata(ancestor) {
            Ok(metadata) => ensure!(
                metadata.is_dir() && !metadata.file_type().is_symlink(),
                "promotion traverses a symlink or non-directory: '{}'",
                ancestor.display()
            ),
            Err(error) if error.kind() == ErrorKind::NotFound => {}
            Err(error) => return Err(error).wrap_err("could not inspect promotion root"),
        }
    }
    Ok(())
}

fn is_within(child: &Path, parent: &Path) -> bool {
    let child = child.to_string_lossy().to_lowercase();
    let parent = parent.to_string_lossy().to_lowercase();
    child == parent || child.starts_with(&format!("{parent}{}", std::path::MAIN_SEPARATOR))
}

fn inspect_output_parents(root: &Path, path: &str) -> Result<()> {
    let mut parent = root
        .join(path)
        .parent()
        .expect("validated output has parent")
        .to_path_buf();
    while parent.starts_with(root) {
        match fs::symlink_metadata(&parent) {
            Ok(metadata) => ensure!(
                metadata.is_dir() && !metadata.file_type().is_symlink(),
                "promotion output '{path}' traverses a symlink or non-directory"
            ),
            Err(error) if error.kind() == ErrorKind::NotFound => {}
            Err(error) => return Err(error).wrap_err("could not inspect output parent"),
        }
        if parent == root {
            break;
        }
        parent = parent.parent().expect("parent stays in root").to_path_buf();
    }
    Ok(())
}

fn read_optional(path: &Path) -> Result<Option<Vec<u8>>> {
    match fs::symlink_metadata(path) {
        Ok(metadata) => {
            ensure!(
                metadata.is_file() && !metadata.file_type().is_symlink(),
                "promotion path is not a regular file: '{}'",
                path.display()
            );
            #[cfg(windows)]
            ensure!(
                metadata.file_attributes() & 0x0000_0400 == 0,
                "promotion path is a reparse point: '{}'",
                path.display()
            );
            Ok(Some(fs::read(path).wrap_err_with(|| {
                format!("could not read '{}'", path.display())
            })?))
        }
        Err(error) if error.kind() == ErrorKind::NotFound => Ok(None),
        Err(error) => {
            Err(error).wrap_err_with(|| format!("could not inspect '{}'", path.display()))
        }
    }
}

fn read_required(path: &Path) -> Result<Vec<u8>> {
    read_optional(path)?
        .ok_or_else(|| eyre::eyre!("required promotion file is missing: '{}'", path.display()))
}

fn ensure_git_toplevel(root: &Path) -> Result<()> {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(["rev-parse", "--show-toplevel"])
        .output()?;
    ensure!(
        output.status.success(),
        "promotion repository is not a Git worktree"
    );
    let top = PathBuf::from(String::from_utf8(output.stdout)?.trim());
    ensure!(
        fs::canonicalize(top)? == root,
        "promotion root must be the Git worktree root"
    );
    Ok(())
}

fn git_head_commit(root: &Path) -> Result<String> {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(["rev-parse", "HEAD"])
        .output()?;
    ensure!(
        output.status.success(),
        "promotion repository has no HEAD commit"
    );
    let commit = String::from_utf8(output.stdout)?.trim().to_owned();
    ensure!(
        commit.len() == 40 && commit.bytes().all(|ch| ch.is_ascii_hexdigit()),
        "invalid promotion base commit"
    );
    Ok(commit)
}

fn ensure_index_matches_head(root: &Path, evidence_path: &str) -> Result<()> {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args([
            "diff",
            "--cached",
            "--quiet",
            "--exit-code",
            "HEAD",
            "--",
            SOURCE_MANIFEST,
            "platform/minecraft/mc-version/",
        ])
        .arg(evidence_path)
        .output()?;
    match output.status.code() {
        Some(0) => {}
        Some(1) => bail!(
            "Git index differs from HEAD under the source definition, checked-in version roots or compatibility evidence; reconcile staged edits before promotion"
        ),
        _ => bail!(
            "could not verify promotion Git index against HEAD: {}",
            String::from_utf8_lossy(&output.stderr).trim()
        ),
    }
    #[cfg(unix)]
    ensure_unstaged_executable_modes_match_index(root)?;
    Ok(())
}

#[cfg(unix)]
fn ensure_unstaged_executable_modes_match_index(root: &Path) -> Result<()> {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args([
            "-c",
            "core.filemode=true",
            "diff-files",
            "--summary",
            "--",
            "platform/minecraft/mc-version/",
        ])
        .output()?;
    ensure!(
        output.status.success(),
        "could not inspect unstaged modes in checked-in projection roots: {}",
        String::from_utf8_lossy(&output.stderr).trim()
    );
    // The transaction itself may have removed or created files. A mode or
    // file-type change on a path that still exists is relevant here.
    let summary = String::from_utf8(output.stdout)?;
    ensure!(
        !summary
            .lines()
            .any(|line| line.trim_start().starts_with("mode change ")),
        "checked-in projection roots have an unstaged executable mode or file-type change"
    );
    Ok(())
}

fn git_head_file(root: &Path, path: &str) -> Result<Vec<u8>> {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .arg("show")
        .arg(format!("HEAD:{path}"))
        .output()?;
    ensure!(
        output.status.success(),
        "checked-in projection manifest '{path}' is not present at HEAD: {}",
        String::from_utf8_lossy(&output.stderr).trim()
    );
    Ok(output.stdout)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::manifest::ProjectionFeature;
    use crate::source_projection::manifest::ProjectionPreset;
    use crate::source_projection::manifest::SCHEMA_VERSION;
    use crate::source_projection::provenance::ProjectedFileProvenance;
    use tempfile::TempDir;

    const TEST_EVIDENCE_PATH: &str = "docs/compatibility-evidence.md";

    struct Fixture {
        _temporary: TempDir,
        request: PromotionRequest,
    }

    impl Fixture {
        fn new() -> Self {
            Self::new_with(
                REPAIR_TARGETS.into_iter().map(str::to_owned).collect(),
                "released-old",
                "released-new",
            )
        }

        fn new_with(ids: Vec<String>, old_preset_id: &str, new_preset_id: &str) -> Self {
            let temporary = tempfile::tempdir().unwrap();
            let repository_root = temporary.path().join("repo");
            let candidate_parent = temporary.path().join("candidates");
            fs::create_dir_all(repository_root.join("platform/minecraft")).unwrap();
            fs::create_dir_all(repository_root.join("docs")).unwrap();
            let evidence_bytes = b"Synthetic compatibility acceptance evidence.\n";
            fs::write(repository_root.join(TEST_EVIDENCE_PATH), evidence_bytes).unwrap();
            fs::create_dir_all(&candidate_parent).unwrap();
            let targets = ids
                .iter()
                .map(|id| ProjectionTarget {
                    id: id.clone(),
                    template_key: format!("mc_{}", id.replace('.', "_")),
                    minecraft_version: id.clone(),
                    loader: "forge".to_owned(),
                    java_major: match id.as_str() {
                        "1.21.0" | "1.21.1" => 21,
                        "26.1.2" => 25,
                        _ => 17,
                    },
                    project_dir: format!("platform/minecraft/mc-version/{id}"),
                })
                .collect();
            let presets = [old_preset_id, new_preset_id]
                .into_iter()
                .map(|id| ProjectionPreset {
                    id: id.to_owned(),
                    release_mod_version: None,
                    targets: ids.clone(),
                    enabled_features: vec![],
                    target_features: BTreeMap::new(),
                    release_baselines: vec![],
                    frozen_source_commit: None,
                    frozen_sources: vec![],
                    canonical_project_fixture_provenance_sha256: None,
                    identity: String::new(),
                })
                .collect();
            let mut source_manifest = SourceProjectionManifest {
                schema_version: SCHEMA_VERSION,
                targets,
                features: vec![],
                presets,
            };
            for index in 0..source_manifest.presets.len() {
                source_manifest.presets[index].identity = source_manifest
                    .compute_preset_identity(&source_manifest.presets[index])
                    .unwrap();
            }
            fs::write(
                repository_root.join(SOURCE_MANIFEST),
                source_manifest.to_json().unwrap(),
            )
            .unwrap();
            let old = source_manifest.preset(old_preset_id).unwrap();
            let new = source_manifest.preset(new_preset_id).unwrap();
            let mut candidates = BTreeMap::new();
            for id in &ids {
                let destination =
                    repository_root.join(format!("platform/minecraft/mc-version/{id}"));
                let candidate = candidate_parent.join(id);
                fs::create_dir_all(&destination).unwrap();
                fs::create_dir_all(&candidate).unwrap();
                let mut old_manifest = ProjectionProvenance::new(id, id, &old.id, &old.identity);
                let mut new_manifest = ProjectionProvenance::new(id, id, &new.id, &new.identity);
                for (path, bytes) in [
                    ("keep.txt", b"old\n".as_slice()),
                    ("remove.txt", b"remove\n".as_slice()),
                ] {
                    fs::write(destination.join(path), bytes).unwrap();
                    old_manifest
                        .files
                        .insert(path.to_owned(), provenance(path, bytes));
                }
                for (path, bytes) in [
                    ("keep.txt", b"new\n".as_slice()),
                    ("add.txt", b"add\n".as_slice()),
                ] {
                    fs::write(candidate.join(path), bytes).unwrap();
                    new_manifest
                        .files
                        .insert(path.to_owned(), provenance(path, bytes));
                }
                fs::write(
                    destination.join(MANIFEST_FILE),
                    old_manifest.to_json().unwrap(),
                )
                .unwrap();
                let candidate_bytes = new_manifest.to_json().unwrap().into_bytes();
                fs::write(candidate.join(MANIFEST_FILE), &candidate_bytes).unwrap();
                let jar_relative_path = format!("build/libs/sfm-{id}.jar");
                let jar_bytes = format!("synthetic production JAR for {id}\n");
                fs::create_dir_all(candidate.join("build/libs")).unwrap();
                fs::write(candidate.join(&jar_relative_path), jar_bytes.as_bytes()).unwrap();
                candidates.insert(
                    id.clone(),
                    PromotionCandidate {
                        project_root: candidate,
                        reviewed_manifest_sha256: sha256(&candidate_bytes),
                        production_jar_relative_path: jar_relative_path,
                        production_jar_sha256: sha256(jar_bytes.as_bytes()),
                        production_task: expected_production_task(id).unwrap().to_owned(),
                        jdk_major: source_manifest
                            .targets
                            .iter()
                            .find(|target| target.id == *id)
                            .unwrap()
                            .java_major,
                        jdk_build_id: match id.as_str() {
                            "1.21.0" | "1.21.1" => "JBR-21.0.11",
                            "26.1.2" => "JBR-25.0.3",
                            _ => "JBRSDK-17.0.14",
                        }
                        .to_owned(),
                    },
                );
            }
            git(&repository_root, &["init", "-q"]);
            git(&repository_root, &["add", "."]);
            git(
                &repository_root,
                &[
                    "-c",
                    "user.name=Test",
                    "-c",
                    "user.email=test@example.invalid",
                    "commit",
                    "-qm",
                    "fixture",
                ],
            );
            let reviewed_head_commit = git_head_commit(&repository_root).unwrap();
            let reviewed_source_manifest_sha256 =
                sha256(&fs::read(repository_root.join(SOURCE_MANIFEST)).unwrap());
            Self {
                _temporary: temporary,
                request: PromotionRequest {
                    repository_root,
                    reviewed_head_commit,
                    reviewed_source_manifest_sha256,
                    compatibility_evidence_relative_path: TEST_EVIDENCE_PATH.to_owned(),
                    reviewed_compatibility_evidence_sha256: sha256(evidence_bytes),
                    candidates,
                    candidate_preset_id: new.id.clone(),
                    candidate_definition_identity: new.identity.clone(),
                    transition: PromotionTransition::NewImmutablePreset,
                    accept_identical_edits: false,
                },
            }
        }

        fn new_repair() -> Self {
            let ids = REPAIR_TARGETS.into_iter().map(str::to_owned).collect();
            let mut fixture = Self::new_with(ids, REPAIR_PRESET_ID, "released-new");
            let source_path = fixture.request.repository_root.join(SOURCE_MANIFEST);
            let mut source_manifest =
                SourceProjectionManifest::from_json(&fs::read_to_string(&source_path).unwrap())
                    .unwrap();
            let mut candidate_preset = source_manifest.preset("released-new").unwrap().clone();
            candidate_preset.id = REPAIR_PRESET_ID.to_owned();
            candidate_preset
                .enabled_features
                .push("repair_marker".to_owned());
            source_manifest.features.push(ProjectionFeature {
                id: "repair_marker".to_owned(),
                supported_targets: REPAIR_TARGETS.into_iter().map(str::to_owned).collect(),
                requires: vec![],
                source_effects: vec![],
                resource_effects: vec![],
                dependency_effects: vec![],
            });
            candidate_preset.identity = source_manifest
                .compute_preset_identity(&candidate_preset)
                .unwrap();
            source_manifest.presets = vec![candidate_preset.clone()];
            fs::write(&source_path, source_manifest.to_json().unwrap()).unwrap();
            fixture.request.reviewed_source_manifest_sha256 =
                sha256(&fs::read(&source_path).unwrap());

            let mut reviewed_operations = Vec::new();
            for id in REPAIR_TARGETS {
                let old_root = fixture.destination(id);
                let candidate = fixture.request.candidates.get_mut(id).unwrap();
                let candidate_root = &candidate.project_root;
                let manifest_path = candidate_root.join(MANIFEST_FILE);
                let mut manifest = parse_manifest(&fs::read(&manifest_path).unwrap()).unwrap();
                manifest.preset_id = candidate_preset.id.clone();
                manifest.preset_definition_identity = candidate_preset.identity.clone();
                manifest.files.clear();
                fs::remove_file(candidate_root.join("add.txt")).unwrap();
                for path in ["keep.txt", "remove.txt"] {
                    let bytes = fs::read(old_root.join(path)).unwrap();
                    fs::write(candidate_root.join(path), &bytes).unwrap();
                    manifest
                        .files
                        .insert(path.to_owned(), provenance(path, &bytes));
                }
                if REPAIR_REFMAP_TARGETS.contains(&id) {
                    let refmap = candidate_root.join(REPAIR_REFMAP_PATH);
                    fs::create_dir_all(refmap.parent().unwrap()).unwrap();
                    let bytes = format!("{{\"target\":\"{id}\"}}\n").into_bytes();
                    fs::write(&refmap, &bytes).unwrap();
                    manifest.files.insert(
                        REPAIR_REFMAP_PATH.to_owned(),
                        provenance(REPAIR_REFMAP_PATH, &bytes),
                    );
                    reviewed_operations.push(ReviewedPromotionOperation {
                        target_id: id.to_owned(),
                        relative_path: REPAIR_REFMAP_PATH.to_owned(),
                        kind: ReviewedOperationKind::Create,
                        old_sha256: None,
                        new_sha256: sha256(&bytes),
                    });
                }
                let old_manifest_bytes = fs::read(old_root.join(MANIFEST_FILE)).unwrap();
                let candidate_bytes = manifest.to_json().unwrap().into_bytes();
                fs::write(&manifest_path, &candidate_bytes).unwrap();
                candidate.reviewed_manifest_sha256 = sha256(&candidate_bytes);
                reviewed_operations.push(ReviewedPromotionOperation {
                    target_id: id.to_owned(),
                    relative_path: MANIFEST_FILE.to_owned(),
                    kind: ReviewedOperationKind::Manifest,
                    old_sha256: Some(sha256(&old_manifest_bytes)),
                    new_sha256: sha256(&candidate_bytes),
                });
            }
            let old_manifest = parse_manifest(
                &fs::read(fixture.destination("1.19.2").join(MANIFEST_FILE)).unwrap(),
            )
            .unwrap();
            fixture.request.candidate_preset_id = candidate_preset.id;
            fixture.request.candidate_definition_identity = candidate_preset.identity;
            fixture.request.transition = PromotionTransition::PreAcceptanceBaselineRepair {
                expected_old_preset_id: old_manifest.preset_id,
                expected_old_definition_identity: old_manifest.preset_definition_identity,
                reviewed_operations,
            };
            fixture
        }

        fn destination(&self, id: &str) -> PathBuf {
            self.request
                .repository_root
                .join(format!("platform/minecraft/mc-version/{id}"))
        }
    }

    fn provenance(path: &str, bytes: &[u8]) -> ProjectedFileProvenance {
        ProjectedFileProvenance {
            source_path: path.to_owned(),
            source_sha256: sha256(bytes),
            overlay: None,
            output_sha256: sha256(bytes),
        }
    }

    fn git(root: &Path, args: &[&str]) {
        let output = Command::new("git")
            .arg("-C")
            .arg(root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    fn retained_stage(root: &Path) -> PathBuf {
        let stages = fs::read_dir(root.join("platform/minecraft"))
            .unwrap()
            .filter_map(|entry| {
                let path = entry.unwrap().path();
                path.file_name()
                    .unwrap()
                    .to_string_lossy()
                    .starts_with(".sfm-source-promotion-stage-")
                    .then_some(path)
            })
            .collect::<Vec<_>>();
        assert_eq!(stages.len(), 1);
        stages.into_iter().next().unwrap()
    }

    #[test]
    fn dry_run_reports_all_ten_targets_without_writing() {
        let fixture = Fixture::new();
        let report = promote(&fixture.request, PromotionMode::DryRun).unwrap();
        assert_eq!(report.targets.len(), MATRIX_SIZE);
        assert_eq!(report.old_preset_id, "released-old");
        assert!(report.recovery_stage.is_none());
        for target in report.targets.values() {
            assert_eq!(target.created, ["add.txt"]);
            assert_eq!(target.updated, ["keep.txt"]);
            assert_eq!(target.removed, ["remove.txt"]);
        }
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("keep.txt")).unwrap(),
            b"old\n"
        );
    }

    #[test]
    fn public_apply_rejects_both_transitions_without_writing() {
        for fixture in [Fixture::new(), Fixture::new_repair()] {
            let destination = fixture.destination("1.19.2").join(MANIFEST_FILE);
            let before = fs::read(&destination).unwrap();
            let error = promote(&fixture.request, PromotionMode::Apply).unwrap_err();
            assert!(
                error
                    .to_string()
                    .contains("verified transition-specific entry point")
            );
            assert_eq!(fs::read(destination).unwrap(), before);
        }
    }

    #[test]
    fn immutable_apply_requires_its_transition_and_rejects_identical_edits() {
        let repair = Fixture::new_repair();
        assert!(
            promote_new_immutable_with_pre_apply_verification(&repair.request, &|_| Ok(()))
                .unwrap_err()
                .to_string()
                .contains("new immutable transition")
        );
        let mut immutable = Fixture::new();
        immutable.request.accept_identical_edits = true;
        assert!(
            promote_new_immutable_with_pre_apply_verification(&immutable.request, &|_| Ok(()))
                .unwrap_err()
                .to_string()
                .contains("forbids accept_identical_edits")
        );
    }

    #[test]
    fn apply_changes_ten_roots_and_retains_recovery_journal() {
        let fixture = Fixture::new();
        let report =
            promote_new_immutable_with_pre_apply_verification(&fixture.request, &|_| Ok(()))
                .unwrap();
        let stage = report.recovery_stage.unwrap();
        assert!(stage.join("journal.json").is_file());
        assert!(stage.join("complete").is_file());
        for id in REPAIR_TARGETS {
            let destination = fixture.destination(&id);
            assert_eq!(fs::read(destination.join("keep.txt")).unwrap(), b"new\n");
            assert_eq!(fs::read(destination.join("add.txt")).unwrap(), b"add\n");
            assert!(!destination.join("remove.txt").exists());
            let manifest =
                parse_manifest(&fs::read(destination.join(MANIFEST_FILE)).unwrap()).unwrap();
            assert_eq!(manifest.preset_id, "released-new");
        }
    }

    #[test]
    fn clean_index_and_no_concurrent_writer_allow_create_and_update() {
        let fixture = Fixture::new();
        let no_race = |_: usize, _: ApplyHookPoint, _: &Path| -> Result<()> { Ok(()) };
        let report =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&no_race)).unwrap();
        assert_eq!(report.targets.len(), MATRIX_SIZE);
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("add.txt")).unwrap(),
            b"add\n"
        );
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("keep.txt")).unwrap(),
            b"new\n"
        );
    }

    #[test]
    fn empty_commit_during_apply_rolls_back_installed_outputs() {
        let fixture = Fixture::new();
        let root = fixture.request.repository_root.clone();
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("1.19.2").join("add.txt"))
            {
                git(
                    &root,
                    &[
                        "-c",
                        "user.name=Test",
                        "-c",
                        "user.email=test@example.invalid",
                        "commit",
                        "--allow-empty",
                        "-qm",
                        "concurrent empty commit",
                    ],
                );
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(format!("{error:?}").contains("HEAD differs"));
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("remove.txt")).unwrap(),
            b"remove\n"
        );
        assert!(!fixture.destination("1.19.2").join("add.txt").exists());
        assert_ne!(
            git_head_commit(&root).unwrap(),
            fixture.request.reviewed_head_commit
        );
    }

    #[test]
    fn staged_only_index_edit_during_apply_rolls_back_installed_outputs() {
        let fixture = Fixture::new();
        let root = fixture.request.repository_root.clone();
        let evidence = root.join(TEST_EVIDENCE_PATH);
        let original_evidence = fs::read(&evidence).unwrap();
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("1.19.2").join("add.txt"))
            {
                fs::write(&evidence, b"concurrent staged evidence\n")?;
                git(&root, &["add", "--", TEST_EVIDENCE_PATH]);
                fs::write(&evidence, &original_evidence)?;
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(format!("{error:?}").contains("Git index differs from HEAD"));
        assert_eq!(fs::read(&evidence).unwrap(), original_evidence);
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("remove.txt")).unwrap(),
            b"remove\n"
        );
        assert!(!fixture.destination("1.19.2").join("add.txt").exists());
    }

    #[test]
    fn changed_unchanged_owned_output_blocks_complete_and_survives_rollback() {
        let fixture = Fixture::new_repair();
        let unchanged = fixture.destination("1.19.2").join("keep.txt");
        let old_manifest = fs::read(fixture.destination("1.19.2").join(MANIFEST_FILE)).unwrap();
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("26.1.2").join(MANIFEST_FILE))
            {
                fs::write(&unchanged, b"concurrent owned edit\n")?;
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(format!("{error:?}").contains("candidate-owned output"));
        assert_eq!(fs::read(&unchanged).unwrap(), b"concurrent owned edit\n");
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join(MANIFEST_FILE)).unwrap(),
            old_manifest
        );
        assert!(
            !fixture
                .destination("1.20.2")
                .join(REPAIR_REFMAP_PATH)
                .exists()
        );
    }

    #[test]
    fn unowned_input_added_during_apply_blocks_completion_and_survives_rollback() {
        let fixture = Fixture::new();
        let destination = fixture.destination("1.19.2");
        let old_manifest = fs::read(destination.join(MANIFEST_FILE)).unwrap();
        let extra = destination.join("src/main/java/Injected.java");
        let hook = |_: usize, point: ApplyHookPoint, installed: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && installed.ends_with(Path::new("26.1.2").join(MANIFEST_FILE))
            {
                fs::create_dir_all(extra.parent().expect("extra input has parent"))?;
                fs::write(&extra, b"class Injected {}\n")?;
            }
            Ok(())
        };

        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(format!("{error:?}").contains("unowned checked-in project input"));
        assert_eq!(fs::read(destination.join("keep.txt")).unwrap(), b"old\n");
        assert_eq!(
            fs::read(destination.join(MANIFEST_FILE)).unwrap(),
            old_manifest
        );
        assert_eq!(fs::read(extra).unwrap(), b"class Injected {}\n");
    }

    #[cfg(any(unix, windows))]
    #[test]
    fn symlinked_unchanged_owned_output_blocks_complete_and_survives_rollback() {
        let fixture = Fixture::new_repair();
        let unchanged = fixture.destination("1.19.2").join("keep.txt");
        let outside = fixture._temporary.path().join("same-bytes.txt");
        fs::write(&outside, b"old\n").unwrap();
        #[cfg(windows)]
        {
            let probe = fixture._temporary.path().join("symlink-probe.txt");
            if std::os::windows::fs::symlink_file(&outside, &probe).is_err() {
                return; // Symlink privilege is optional on Windows test hosts.
            }
            fs::remove_file(probe).unwrap();
        }
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("26.1.2").join(MANIFEST_FILE))
            {
                fs::remove_file(&unchanged)?;
                #[cfg(unix)]
                std::os::unix::fs::symlink(&outside, &unchanged)?;
                #[cfg(windows)]
                std::os::windows::fs::symlink_file(&outside, &unchanged)?;
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(format!("{error:?}").contains("promotion path is not a regular file"));
        assert!(
            fs::symlink_metadata(&unchanged)
                .unwrap()
                .file_type()
                .is_symlink()
        );
        assert!(
            !fixture
                .destination("1.20.2")
                .join(REPAIR_REFMAP_PATH)
                .exists()
        );
    }

    #[cfg(unix)]
    #[test]
    fn unstaged_executable_mode_change_in_checked_in_root_is_rejected() {
        use std::os::unix::fs::PermissionsExt as _;

        let fixture = Fixture::new();
        let path = fixture.destination("1.19.2").join("keep.txt");
        let mut permissions = fs::metadata(&path).unwrap().permissions();
        permissions.set_mode(permissions.mode() ^ 0o111);
        fs::set_permissions(&path, permissions).unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("executable mode")
        );
    }

    #[cfg(windows)]
    #[test]
    fn pinned_destination_parent_cannot_be_renamed_before_install() {
        let fixture = Fixture::new();
        let repository_root = fixture.request.repository_root.clone();
        let renamed_root = repository_root.with_file_name("repo-renamed");
        let parent = fixture.destination("1.19.2");
        let renamed = parent.with_file_name("1.19.2-renamed");
        let attempted = std::cell::Cell::new(false);
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("1.19.2").join("add.txt"))
            {
                attempted.set(true);
                let error = fs::rename(&parent, &renamed).unwrap_err();
                assert!(
                    matches!(error.raw_os_error(), Some(5 | 32)),
                    "expected access denial or sharing violation, got {error}"
                );
                let root_error = fs::rename(&repository_root, &renamed_root).unwrap_err();
                assert!(
                    matches!(root_error.raw_os_error(), Some(5 | 32)),
                    "expected repository-root rename denial, got {root_error}"
                );
            }
            Ok(())
        };
        let report =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap();
        assert!(attempted.get());
        assert!(report.recovery_stage.unwrap().join("complete").is_file());
        assert!(!renamed.exists());
        assert!(!renamed_root.exists());
        assert_eq!(fs::read(parent.join("add.txt")).unwrap(), b"add\n");
    }

    #[cfg(windows)]
    #[test]
    fn junction_in_destination_parent_is_rejected_by_pinned_handle() {
        let temporary = tempfile::tempdir().unwrap();
        let root = temporary.path().join("repo");
        let outside = temporary.path().join("outside");
        let junction = root.join("src");
        fs::create_dir(&root).unwrap();
        fs::create_dir(&outside).unwrap();
        let output = Command::new("cmd")
            .args(["/C", "mklink", "/J"])
            .arg(&junction)
            .arg(&outside)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "could not create test junction: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        assert_ne!(
            fs::symlink_metadata(&junction).unwrap().file_attributes() & 0x0000_0400,
            0
        );
        let mut pinned = PinnedDirectories::default();
        pinned.pin_existing(&root).unwrap();
        let error = pinned
            .pin_or_create_below(&root, &junction.join("main"))
            .unwrap_err();
        assert!(error.to_string().contains("reparse point"));
        assert!(!outside.join("main").exists());
    }

    #[test]
    fn injected_mid_transaction_failure_restores_every_root() {
        let fixture = Fixture::new();
        let before = (0..MATRIX_SIZE)
            .map(|index| {
                let destination = fixture.destination(REPAIR_TARGETS[index]);
                fs::read(destination.join(MANIFEST_FILE)).unwrap()
            })
            .collect::<Vec<_>>();
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, Some(4), None).unwrap_err();
        assert!(error.to_string().contains("rolled back"));
        for (index, bytes) in before.iter().enumerate() {
            let destination = fixture.destination(REPAIR_TARGETS[index]);
            assert_eq!(&fs::read(destination.join(MANIFEST_FILE)).unwrap(), bytes);
            assert_eq!(fs::read(destination.join("keep.txt")).unwrap(), b"old\n");
            assert_eq!(
                fs::read(destination.join("remove.txt")).unwrap(),
                b"remove\n"
            );
            assert!(!destination.join("add.txt").exists());
        }
    }

    #[test]
    fn staged_only_contributor_edit_is_not_safe_to_promote() {
        let fixture = Fixture::new();
        let relative = "platform/minecraft/mc-version/1.19.2/keep.txt";
        let destination = fixture.request.repository_root.join(relative);
        fs::write(&destination, b"staged-only\n").unwrap();
        git(&fixture.request.repository_root, &["add", "--", relative]);
        fs::write(&destination, b"old\n").unwrap();
        assert_eq!(fs::read(&destination).unwrap(), b"old\n");
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("index")
        );
    }

    #[test]
    fn staged_only_compatibility_evidence_edit_is_not_safe_to_promote() {
        let fixture = Fixture::new();
        let relative = &fixture.request.compatibility_evidence_relative_path;
        let evidence = fixture.request.repository_root.join(relative);
        let original = fs::read(&evidence).unwrap();
        fs::write(&evidence, b"unreviewed staged evidence\n").unwrap();
        git(&fixture.request.repository_root, &["add", "--", relative]);
        fs::write(&evidence, &original).unwrap();
        assert_eq!(fs::read(&evidence).unwrap(), original);
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("index")
        );
    }

    #[test]
    fn staged_only_deletion_and_mode_change_are_not_safe_to_promote() {
        for args in [
            vec![
                "rm",
                "--cached",
                "--",
                "platform/minecraft/mc-version/1.19.2/keep.txt",
            ],
            vec![
                "update-index",
                "--chmod=+x",
                "--",
                "platform/minecraft/mc-version/1.19.2/keep.txt",
            ],
        ] {
            let fixture = Fixture::new();
            git(&fixture.request.repository_root, &args);
            assert_eq!(
                fs::read(fixture.destination("1.19.2").join("keep.txt")).unwrap(),
                b"old\n"
            );
            assert!(
                promote(&fixture.request, PromotionMode::DryRun)
                    .unwrap_err()
                    .to_string()
                    .contains("index")
            );
        }
    }

    #[test]
    fn create_race_preserves_newly_appeared_unowned_file() {
        let fixture = Fixture::new();
        let raced = fixture.destination("1.19.2").join("add.txt");
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("1.19.2").join("add.txt"))
            {
                fs::write(destination, b"racing owner\n")?;
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(error.to_string().contains("rollback"));
        assert_eq!(fs::read(&raced).unwrap(), b"racing owner\n");
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("remove.txt")).unwrap(),
            b"remove\n"
        );
    }

    #[test]
    fn update_race_before_backup_preserves_the_concurrent_edit() {
        let fixture = Fixture::new();
        let raced = fixture.destination("1.19.2").join("keep.txt");
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::AfterReadBeforeBackup
                && destination.ends_with(Path::new("1.19.2").join("keep.txt"))
            {
                fs::write(destination, b"racing edit\n")?;
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(error.to_string().contains("rolled back"));
        assert_eq!(fs::read(&raced).unwrap(), b"racing edit\n");
        assert!(!fixture.destination("1.19.2").join("add.txt").exists());
    }

    #[test]
    fn update_race_after_backup_preserves_new_file_and_old_backup() {
        let fixture = Fixture::new();
        let raced = fixture.destination("1.19.2").join("keep.txt");
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("1.19.2").join("keep.txt"))
            {
                fs::write(destination, b"new racing owner\n")?;
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(error.to_string().contains("rollback failed"));
        assert_eq!(fs::read(&raced).unwrap(), b"new racing owner\n");
        let stage_parent = fixture.request.repository_root.join("platform/minecraft");
        let stages = fs::read_dir(stage_parent)
            .unwrap()
            .filter_map(|entry| {
                let path = entry.unwrap().path();
                path.file_name()
                    .unwrap()
                    .to_string_lossy()
                    .starts_with(".sfm-source-promotion-stage-")
                    .then_some(path)
            })
            .collect::<Vec<_>>();
        assert_eq!(stages.len(), 1);
        assert!(
            fs::read_dir(stages[0].join("backup"))
                .unwrap()
                .any(|entry| {
                    fs::read(entry.unwrap().path()).is_ok_and(|bytes| bytes == b"old\n")
                })
        );
    }

    #[test]
    fn rollback_restore_race_does_not_replace_a_new_writer_file() {
        let fixture = Fixture::new();
        let raced = fixture.destination("1.19.2").join("remove.txt");
        let injected = std::cell::Cell::new(false);
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("1.19.2").join("add.txt"))
            {
                bail!("force rollback after the old file was backed up");
            }
            if point == ApplyHookPoint::AfterRollbackObservedAbsent
                && destination.ends_with(Path::new("1.19.2").join("remove.txt"))
            {
                injected.set(true);
                fs::write(destination, b"writer during rollback\n")?;
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(injected.get());
        assert!(format!("{error:?}").contains("rollback failed"));
        assert_eq!(fs::read(&raced).unwrap(), b"writer during rollback\n");
        let stage = retained_stage(&fixture.request.repository_root);
        let journal: PromotionJournal =
            facet_json::from_str(&fs::read_to_string(stage.join("journal.json")).unwrap()).unwrap();
        assert_eq!(
            journal.operations[0].backup_file.as_deref(),
            Some("backup/0")
        );
        assert_eq!(fs::read(stage.join("backup/0")).unwrap(), b"remove\n");
        assert!(!stage.join("rolled-back").exists());
    }

    #[test]
    fn rollback_preserves_a_distinct_same_byte_writer_file() {
        let fixture = Fixture::new();
        let writer_path = fixture.destination("1.19.2").join("add.txt");
        let replaced = std::cell::Cell::new(false);
        let hook = |_: usize, point: ApplyHookPoint, destination: &Path| -> Result<()> {
            if point == ApplyHookPoint::BeforeInstall
                && destination.ends_with(Path::new("1.19.2").join("keep.txt"))
            {
                let original = fs::File::open(&writer_path)?;
                let original_id = file_identity(&original)?;
                fs::remove_file(&writer_path)?;
                fs::write(&writer_path, b"add\n")?;
                let replacement_id = file_identity(&fs::File::open(&writer_path)?)?;
                ensure!(
                    original_id != replacement_id,
                    "test replacement reused file ID"
                );
                replaced.set(true);
                bail!("force rollback after same-byte writer replacement");
            }
            Ok(())
        };
        let error =
            run_promotion(&fixture.request, PromotionMode::Apply, None, Some(&hook)).unwrap_err();
        assert!(replaced.get());
        assert!(format!("{error:?}").contains("rollback failed"));
        assert_eq!(fs::read(&writer_path).unwrap(), b"add\n");
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("remove.txt")).unwrap(),
            b"remove\n"
        );
        assert_eq!(
            fs::read(fixture.destination("1.19.2").join("keep.txt")).unwrap(),
            b"old\n"
        );
        let stage = retained_stage(&fixture.request.repository_root);
        let quarantined = stage.join("backup/1.quarantine");
        let journal: PromotionJournal =
            facet_json::from_str(&fs::read_to_string(stage.join("journal.json")).unwrap()).unwrap();
        assert_eq!(
            journal.operations[1].quarantine_file.as_deref(),
            Some("backup/1.quarantine")
        );
        assert_eq!(fs::read(&quarantined).unwrap(), b"add\n");
        assert_eq!(
            file_identity(&fs::File::open(&writer_path).unwrap()).unwrap(),
            file_identity(&fs::File::open(&quarantined).unwrap()).unwrap()
        );
        assert_eq!(fs::read(stage.join("backup/0")).unwrap(), b"remove\n");
        assert!(!stage.join("rolled-back").exists());
    }

    #[test]
    fn rejects_missing_extra_or_unreviewed_candidates() {
        let mut fixture = Fixture::new();
        let missing = fixture.request.candidates.remove("26.1.2").unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("missing [26.1.2]")
        );
        fixture.request.candidates.insert(
            "extra".to_owned(),
            fixture.request.candidates["1.19.2"].clone(),
        );
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("extra [extra]")
        );
        fixture.request.candidates.remove("extra");
        fixture
            .request
            .candidates
            .insert("26.1.2".to_owned(), missing);
        let mut candidate = fixture.request.candidates["1.19.2"].clone();
        candidate.reviewed_manifest_sha256 = sha256(b"wrong");
        fixture
            .request
            .candidates
            .insert("1.19.2".to_owned(), candidate);
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("hash mismatch")
        );
    }

    #[test]
    fn rejects_changed_reviewed_head_or_source_definition() {
        let mut fixture = Fixture::new();
        fixture.request.reviewed_head_commit = "0".repeat(40);
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("HEAD differs")
        );
        fixture.request.reviewed_head_commit =
            git_head_commit(&fixture.request.repository_root).unwrap();
        fixture.request.reviewed_source_manifest_sha256 = sha256(b"wrong definition");
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("definition differs")
        );
    }

    #[test]
    fn rejects_stale_or_misplaced_production_jar() {
        let mut fixture = Fixture::new();
        let candidate = &fixture.request.candidates["1.19.2"];
        fs::write(
            candidate
                .project_root
                .join(&candidate.production_jar_relative_path),
            b"stale JAR bytes\n",
        )
        .unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("production JAR hash mismatch")
        );

        let candidate = fixture.request.candidates.get_mut("1.19.2").unwrap();
        candidate.production_jar_sha256 = sha256(b"stale JAR bytes\n");
        candidate.production_jar_relative_path = "other/sfm.jar".to_owned();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("direct build/libs")
        );
    }

    #[test]
    fn rejects_wrong_production_task_or_jdk_major() {
        let mut fixture = Fixture::new();
        fixture
            .request
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .production_task = "jar".to_owned();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("production task mismatch")
        );

        fixture
            .request
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .production_task = "reobfJar".to_owned();
        fixture
            .request
            .candidates
            .get_mut("26.1.2")
            .unwrap()
            .jdk_major = 21;
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("JDK major mismatch")
        );

        let mut fixture = Fixture::new();
        fixture
            .request
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .jdk_build_id = "JBR-21.0.11".to_owned();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("JDK build ID major mismatch")
        );
        fixture
            .request
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .jdk_build_id
            .clear();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("invalid candidate JDK build ID")
        );
    }

    #[test]
    fn bundled_1_19_2_task_requires_verified_apply_preflight() {
        let mut fixture = Fixture::new();
        fixture
            .request
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .production_task = "reobfJarJar".to_owned();

        // Dry-run has no candidate lock and cannot make the bundle assertion.
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("production task mismatch")
        );

        // The CLI selects this policy only after it verifies the exact lock,
        // profile, projected lockfile, JAR and request fields for all ten roots.
        preflight(
            &fixture.request,
            ProductionTaskPolicy::VerifiedCandidateLock,
        )
        .unwrap();
    }

    #[test]
    fn verified_apply_policy_does_not_extend_bundled_task_to_other_targets() {
        let mut fixture = Fixture::new();
        fixture
            .request
            .candidates
            .get_mut("1.19.4")
            .unwrap()
            .production_task = "reobfJarJar".to_owned();
        assert!(
            preflight(
                &fixture.request,
                ProductionTaskPolicy::VerifiedCandidateLock
            )
            .unwrap_err()
            .to_string()
            .contains("production task mismatch")
        );
    }

    #[test]
    fn rejects_jar_escape_and_duplicate_candidate_roots() {
        let mut fixture = Fixture::new();
        fixture
            .request
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .production_jar_relative_path = "build/libs/../../outside.jar".to_owned();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("unsafe projection path")
        );

        let mut fixture = Fixture::new();
        let duplicate_root = fixture.request.candidates["1.19.2"].project_root.clone();
        fixture
            .request
            .candidates
            .get_mut("1.19.4")
            .unwrap()
            .project_root = duplicate_root;
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("candidate roots")
        );
    }

    #[test]
    fn rejects_edited_or_escaping_compatibility_evidence() {
        let mut fixture = Fixture::new();
        let path = fixture
            .request
            .repository_root
            .join(&fixture.request.compatibility_evidence_relative_path);
        fs::write(&path, b"changed compatibility evidence\n").unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("compatibility evidence differs")
        );
        fixture.request.compatibility_evidence_relative_path = "../evidence.md".to_owned();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("unsafe projection path")
        );
    }

    #[test]
    fn apply_requires_compatibility_evidence_in_reviewed_head() {
        let mut fixture = Fixture::new();
        let path = fixture
            .request
            .repository_root
            .join(&fixture.request.compatibility_evidence_relative_path);
        let changed = b"new reviewed draft evidence\n";
        fs::write(path, changed).unwrap();
        fixture.request.reviewed_compatibility_evidence_sha256 = sha256(changed);
        promote(&fixture.request, PromotionMode::DryRun).unwrap();
        assert!(
            promote_new_immutable_with_pre_apply_verification(&fixture.request, &|_| Ok(()))
                .unwrap_err()
                .to_string()
                .contains("committed unchanged in reviewed HEAD")
        );
    }

    #[test]
    fn rejects_candidate_jar_symlink() {
        let fixture = Fixture::new();
        let candidate = &fixture.request.candidates["1.19.2"];
        let jar = candidate
            .project_root
            .join(&candidate.production_jar_relative_path);
        fs::remove_file(&jar).unwrap();
        #[cfg(unix)]
        std::os::unix::fs::symlink("../../add.txt", &jar).unwrap();
        #[cfg(windows)]
        if std::os::windows::fs::symlink_file("../../add.txt", &jar).is_err() {
            return;
        }
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("not a regular file")
        );
    }

    #[test]
    fn rejects_edited_old_manifest_and_unowned_collisions() {
        let fixture = Fixture::new();
        fs::write(fixture.destination("1.19.2").join(MANIFEST_FILE), b"{}\n").unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("differs from HEAD")
        );
        let fixture = Fixture::new();
        fs::write(fixture.destination("1.19.2").join("add.txt"), b"unowned\n").unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("unowned")
        );
    }

    #[test]
    fn rejects_surviving_unowned_java_input() {
        let fixture = Fixture::new();
        let extra = fixture
            .destination("1.19.2")
            .join("src/main/java/Injected.java");
        fs::create_dir_all(extra.parent().unwrap()).unwrap();
        fs::write(&extra, b"class Injected {}\n").unwrap();

        let error = promote(&fixture.request, PromotionMode::DryRun).unwrap_err();
        assert!(format!("{error:?}").contains("unowned checked-in project input"));
    }

    #[test]
    fn rejects_surviving_unowned_resource_input() {
        let fixture = Fixture::new();
        let extra = fixture
            .destination("1.19.2")
            .join("src/main/resources/assets/sfm/injected.json");
        fs::create_dir_all(extra.parent().unwrap()).unwrap();
        fs::write(&extra, b"{}\n").unwrap();

        let error = promote(&fixture.request, PromotionMode::DryRun).unwrap_err();
        assert!(format!("{error:?}").contains("unowned checked-in project input"));
    }

    #[test]
    fn rejects_surviving_unowned_gradle_input() {
        let fixture = Fixture::new();
        let extra = fixture.destination("1.19.2").join("gradle/injected.gradle");
        fs::create_dir_all(extra.parent().unwrap()).unwrap();
        fs::write(&extra, b"println('injected')\n").unwrap();

        let error = promote(&fixture.request, PromotionMode::DryRun).unwrap_err();
        assert!(format!("{error:?}").contains("unowned checked-in project input"));
    }

    #[test]
    fn rejects_surviving_unowned_root_gradle_script() {
        let fixture = Fixture::new();
        let extra = fixture.destination("1.19.2").join("injected.gradle.kts");
        fs::write(&extra, b"println(\"injected\")\n").unwrap();

        let error = promote(&fixture.request, PromotionMode::DryRun).unwrap_err();
        assert!(format!("{error:?}").contains("unowned checked-in project input"));
    }

    #[cfg(any(unix, windows))]
    #[test]
    fn rejects_unowned_reparse_input() {
        let fixture = Fixture::new();
        let external = fixture._temporary.path().join("external.java");
        fs::write(&external, b"class External {}\n").unwrap();
        let extra = fixture
            .destination("1.19.2")
            .join("src/main/java/Injected.java");
        fs::create_dir_all(extra.parent().unwrap()).unwrap();
        #[cfg(unix)]
        std::os::unix::fs::symlink(&external, &extra).unwrap();
        #[cfg(windows)]
        if std::os::windows::fs::symlink_file(&external, &extra).is_err() {
            return;
        }

        let error = promote(&fixture.request, PromotionMode::DryRun).unwrap_err();
        assert!(format!("{error:?}").contains("reparse point"));
    }

    #[test]
    fn allows_runtime_output_directory_during_input_closure_check() {
        let fixture = Fixture::new();
        let output = fixture
            .destination("1.19.2")
            .join("build/classes/Injected.class");
        fs::create_dir_all(output.parent().unwrap()).unwrap();
        fs::write(output, b"test build output\n").unwrap();

        promote(&fixture.request, PromotionMode::DryRun).unwrap();
    }

    #[test]
    fn accepts_only_explicit_candidate_identical_contributor_edits() {
        let mut fixture = Fixture::new();
        fs::write(fixture.destination("1.19.2").join("keep.txt"), b"new\n").unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("was edited")
        );
        fixture.request.accept_identical_edits = true;
        let report = promote(&fixture.request, PromotionMode::DryRun).unwrap();
        assert_eq!(
            report.targets["1.19.2"].accepted_identical_edits,
            ["keep.txt"]
        );
        assert_eq!(report.targets["1.19.2"].unchanged, ["keep.txt"]);
        fs::write(
            fixture.destination("1.19.2").join("keep.txt"),
            b"different\n",
        )
        .unwrap();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("was edited")
        );
    }

    #[test]
    fn rejects_reused_preset_identity() {
        let mut fixture = Fixture::new();
        let old_bytes = fs::read(fixture.destination("1.19.2").join(MANIFEST_FILE)).unwrap();
        let old = parse_manifest(&old_bytes).unwrap();
        fixture.request.candidate_preset_id = old.preset_id;
        fixture.request.candidate_definition_identity = old.preset_definition_identity;
        assert!(promote(&fixture.request, PromotionMode::DryRun).is_err());
    }

    #[test]
    fn case_only_aliases_are_not_a_safe_transition() {
        let paths = ["Foo.txt".to_owned(), "foo.txt".to_owned()];
        assert!(
            validate_path_set(paths.iter())
                .unwrap_err()
                .to_string()
                .contains("case-only")
        );
        let same_path_in_old_and_new = ["Foo.txt".to_owned(), "Foo.txt".to_owned()];
        validate_path_set(same_path_in_old_and_new.iter()).unwrap();
    }

    #[test]
    fn broad_same_id_repair_is_rejected_even_with_explicit_transition() {
        let mut fixture = Fixture::new();
        let old =
            parse_manifest(&fs::read(fixture.destination("1.19.2").join(MANIFEST_FILE)).unwrap())
                .unwrap();
        let manifest_path = fixture.request.repository_root.join(SOURCE_MANIFEST);
        let mut source_manifest =
            SourceProjectionManifest::from_json(&fs::read_to_string(&manifest_path).unwrap())
                .unwrap();
        let mut candidate_preset = source_manifest.preset("released-new").unwrap().clone();
        candidate_preset.id = old.preset_id.clone();
        candidate_preset
            .enabled_features
            .push("repair_marker".to_owned());
        source_manifest.features.push(ProjectionFeature {
            id: "repair_marker".to_owned(),
            supported_targets: source_manifest
                .targets
                .iter()
                .map(|t| t.id.clone())
                .collect(),
            requires: vec![],
            source_effects: vec![],
            resource_effects: vec![],
            dependency_effects: vec![],
        });
        candidate_preset.identity = source_manifest
            .compute_preset_identity(&candidate_preset)
            .unwrap();
        source_manifest.presets = vec![candidate_preset.clone()];
        fs::write(&manifest_path, source_manifest.to_json().unwrap()).unwrap();
        fixture.request.reviewed_source_manifest_sha256 =
            sha256(&fs::read(&manifest_path).unwrap());
        for candidate in fixture.request.candidates.values_mut() {
            let path = candidate.project_root.join(MANIFEST_FILE);
            let mut manifest = parse_manifest(&fs::read(&path).unwrap()).unwrap();
            manifest.preset_id = candidate_preset.id.clone();
            manifest.preset_definition_identity = candidate_preset.identity.clone();
            let bytes = manifest.to_json().unwrap().into_bytes();
            fs::write(path, &bytes).unwrap();
            candidate.reviewed_manifest_sha256 = sha256(&bytes);
        }
        fixture.request.candidate_preset_id = candidate_preset.id;
        fixture.request.candidate_definition_identity = candidate_preset.identity;
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("immutable-preset")
        );
        fixture.request.transition = PromotionTransition::PreAcceptanceBaselineRepair {
            expected_old_preset_id: old.preset_id,
            expected_old_definition_identity: "blake3:wrong".to_owned(),
            reviewed_operations: vec![],
        };
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("exact reviewed old")
        );
        fixture.request.transition = PromotionTransition::PreAcceptanceBaselineRepair {
            expected_old_preset_id: fixture.request.candidate_preset_id.clone(),
            expected_old_definition_identity: old.preset_definition_identity,
            reviewed_operations: vec![],
        };
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("exact reviewed old 4.34.0")
        );
    }

    #[test]
    fn exact_refmap_repair_dry_run_matches_all_fifteen_reviewed_operations() {
        let fixture = Fixture::new_repair();
        let report = promote(&fixture.request, PromotionMode::DryRun).unwrap();
        assert_eq!(report.targets.len(), MATRIX_SIZE);
        assert_eq!(report.old_preset_id, REPAIR_PRESET_ID);
        assert_eq!(report.candidate_preset_id, REPAIR_PRESET_ID);
        assert_eq!(
            report
                .targets
                .values()
                .map(|target| target.created.len())
                .sum::<usize>(),
            REPAIR_REFMAP_TARGETS.len()
        );
        for (id, target) in &report.targets {
            let expected = if REPAIR_REFMAP_TARGETS.contains(&id.as_str()) {
                vec![REPAIR_REFMAP_PATH.to_owned()]
            } else {
                vec![]
            };
            assert_eq!(target.created, expected);
            assert!(target.updated.is_empty());
            assert!(target.removed.is_empty());
        }
    }

    #[test]
    fn exact_refmap_repair_applies_only_to_synthetic_ten_root_fixture() {
        let fixture = Fixture::new_repair();
        let report =
            promote_repair_with_pre_apply_verification(&fixture.request, &|_| Ok(())).unwrap();
        assert!(report.recovery_stage.unwrap().join("complete").is_file());
        for id in REPAIR_TARGETS {
            let root = fixture.destination(id);
            let manifest = parse_manifest(&fs::read(root.join(MANIFEST_FILE)).unwrap()).unwrap();
            assert_eq!(manifest.preset_id, REPAIR_PRESET_ID);
            assert_eq!(
                root.join(REPAIR_REFMAP_PATH).exists(),
                REPAIR_REFMAP_TARGETS.contains(&id)
            );
            assert_eq!(fs::read(root.join("keep.txt")).unwrap(), b"old\n");
        }
    }

    #[test]
    fn legacy_4_34_repair_apply_rejects_bundled_1_19_2_task() {
        let mut fixture = Fixture::new_repair();
        fixture
            .request
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .production_task = "reobfJarJar".to_owned();
        assert!(
            promote_repair_with_pre_apply_verification(&fixture.request, &|_| Ok(()))
                .unwrap_err()
                .to_string()
                .contains("production task mismatch")
        );
    }

    #[test]
    fn refmap_repair_rejects_wrong_reviewed_new_or_old_hash() {
        for change_old in [false, true] {
            let mut fixture = Fixture::new_repair();
            let PromotionTransition::PreAcceptanceBaselineRepair {
                reviewed_operations,
                ..
            } = &mut fixture.request.transition
            else {
                unreachable!();
            };
            if change_old {
                let manifest = reviewed_operations
                    .iter_mut()
                    .find(|entry| entry.kind == ReviewedOperationKind::Manifest)
                    .unwrap();
                manifest.old_sha256 = Some(sha256(b"wrong old"));
            } else {
                reviewed_operations[0].new_sha256 = sha256(b"wrong new");
            }
            assert!(
                promote(&fixture.request, PromotionMode::DryRun)
                    .unwrap_err()
                    .to_string()
                    .contains("reviewed operation allowlist")
            );
        }
    }

    #[test]
    fn refmap_repair_rejects_missing_reviewed_entry_and_extra_candidate_file() {
        let mut fixture = Fixture::new_repair();
        let PromotionTransition::PreAcceptanceBaselineRepair {
            reviewed_operations,
            ..
        } = &mut fixture.request.transition
        else {
            unreachable!();
        };
        reviewed_operations.pop();
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("exactly ten manifest updates")
        );

        let mut fixture = Fixture::new_repair();
        let candidate = fixture.request.candidates.get_mut("1.19.2").unwrap();
        let path = candidate.project_root.join(MANIFEST_FILE);
        let mut manifest = parse_manifest(&fs::read(&path).unwrap()).unwrap();
        fs::write(candidate.project_root.join("unreviewed.txt"), b"extra\n").unwrap();
        manifest.files.insert(
            "unreviewed.txt".to_owned(),
            provenance("unreviewed.txt", b"extra\n"),
        );
        let bytes = manifest.to_json().unwrap().into_bytes();
        fs::write(path, &bytes).unwrap();
        candidate.reviewed_manifest_sha256 = sha256(&bytes);
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("exactly ten manifest updates")
        );
    }

    #[test]
    fn rejects_candidate_output_symlink() {
        let fixture = Fixture::new();
        let candidate_root = &fixture.request.candidates["1.19.2"].project_root;
        fs::remove_file(candidate_root.join("keep.txt")).unwrap();
        #[cfg(unix)]
        std::os::unix::fs::symlink("add.txt", candidate_root.join("keep.txt")).unwrap();
        #[cfg(windows)]
        if std::os::windows::fs::symlink_file("add.txt", candidate_root.join("keep.txt")).is_err() {
            return; // Symlink privilege is optional on Windows test hosts.
        }
        assert!(
            promote(&fixture.request, PromotionMode::DryRun)
                .unwrap_err()
                .to_string()
                .contains("regular file")
        );
    }
}
