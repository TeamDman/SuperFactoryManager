//! A reviewed JSON boundary for promoting all checked-in Minecraft projects.

use super::candidate_lock_cli::read_candidate_lock;
use super::candidate_lock_cli::verify_candidate_in;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::SourceCandidateLock;
use crate::source_projection::promotion::PromotionCandidate;
use crate::source_projection::promotion::PromotionMode;
use crate::source_projection::promotion::PromotionReport;
use crate::source_projection::promotion::PromotionRequest;
use crate::source_projection::promotion::PromotionTransition;
use crate::source_projection::promotion::ReviewedOperationKind;
use crate::source_projection::promotion::ReviewedPromotionOperation;
use crate::source_projection::promotion::TargetPromotionReport;
use crate::source_projection::promotion::promote;
use crate::source_projection::promotion::promote_new_immutable_with_pre_apply_verification;
use crate::source_projection::promotion::promote_repair_with_pre_apply_verification;
use crate::source_projection::promotion::validate_relative_path;
use eyre::Result;
use eyre::WrapErr;
use eyre::bail;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

const REQUEST_SCHEMA: &str = "sfm:source_promotion_request@2";
const REPORT_SCHEMA: &str = "sfm:source_promotion_report@1";
const MAX_REQUEST_BYTES: u64 = 1024 * 1024;
const REPAIR_PRESET_ID: &str = "released-4.34.0";
const PUBLIC_REPAIR_POLICY: RepairIdentityPolicy = RepairIdentityPolicy {
    old_definition_identity: "blake3:1cd6a9078deb867e527f16b11644b3c07e3d916bffa3edfe4aacb0b40c72f7bd",
    new_definition_identity: "blake3:c72d2eb42418abd568df22ed60a4c233cbd8a3a6c06eaf2448c94a25f84e02b6",
    refmap_sha256: "sha256:2c94879b9e943b34c562f6966f9e0aa88c1a30b39bc8e17bd18772b66af24523",
    #[cfg(test)]
    synthetic_gradle_overlays: None,
    #[cfg(test)]
    synthetic_before_promotion: None,
};

struct RepairIdentityPolicy<'a> {
    old_definition_identity: &'a str,
    new_definition_identity: &'a str,
    refmap_sha256: &'a str,
    /// Only synthetic tests may redirect development-style Gradle inputs.
    /// Real repair verification always follows the release-baseline binding.
    #[cfg(test)]
    synthetic_gradle_overlays: Option<&'a BTreeMap<String, String>>,
    /// A deterministic test hook between the initial verification and staging.
    #[cfg(test)]
    synthetic_before_promotion: Option<&'a dyn Fn() -> Result<()>>,
}

#[derive(Clone, Debug, Facet)]
pub struct PromotionArgs {
    /// Reviewed local JSON request containing the ten candidate roots and hashes.
    #[facet(args::named)]
    pub request: PathBuf,
    /// Apply one locked, reviewed transition after explicit acknowledgment.
    #[facet(default = false, args::named)]
    pub apply: bool,
    /// Extra confirmation reserved for a pre-acceptance baseline repair.
    #[facet(default = false, args::named)]
    pub ack_pre_acceptance_baseline_repair: bool,
    /// Explicitly acknowledge advancing all ten roots to a new immutable preset.
    #[facet(default = false, args::named)]
    pub ack_new_immutable_preset: bool,
    /// External, portable lock for the exact ten-target release candidate.
    #[facet(default, args::named)]
    pub candidate_lock: Option<PathBuf>,
    /// Reviewed SHA-256 of the lock file, including its `sha256:` prefix.
    #[facet(default, args::named)]
    pub candidate_lock_sha256: Option<String>,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct PromotionRequestFile {
    schema: String,
    repository_root: String,
    reviewed_head_commit: String,
    reviewed_source_manifest_sha256: String,
    compatibility_evidence_relative_path: String,
    reviewed_compatibility_evidence_sha256: String,
    candidate_preset_id: String,
    candidate_definition_identity: String,
    transition: PromotionTransitionFile,
    accept_identical_edits: bool,
    candidates: Vec<PromotionCandidateFile>,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct PromotionCandidateFile {
    target_id: String,
    project_root: String,
    reviewed_manifest_sha256: String,
    production_jar_relative_path: String,
    production_jar_sha256: String,
    production_task: String,
    jdk_major: u16,
    jdk_build_id: String,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct PromotionTransitionFile {
    kind: String,
    #[facet(default)]
    expected_old_preset_id: Option<String>,
    #[facet(default)]
    expected_old_definition_identity: Option<String>,
    #[facet(default)]
    reviewed_operations: Option<Vec<ReviewedPromotionOperationFile>>,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct ReviewedPromotionOperationFile {
    target_id: String,
    relative_path: String,
    kind: String,
    old_sha256: Option<String>,
    new_sha256: String,
}

#[derive(Debug, Facet)]
struct PromotionCliReport {
    schema: String,
    mode: String,
    head_commit: String,
    source_manifest_sha256: String,
    old_preset_id: String,
    old_definition_identity: String,
    candidate_preset_id: String,
    candidate_definition_identity: String,
    targets: BTreeMap<String, PromotionTargetCliReport>,
    recovery_stage: Option<String>,
}

#[derive(Debug, Facet)]
struct PromotionTargetCliReport {
    counts: PromotionTargetCounts,
    changes: PromotionTargetChanges,
    old_manifest_sha256: String,
    candidate_manifest_sha256: String,
}

#[derive(Debug, Facet)]
struct PromotionTargetCounts {
    created: usize,
    updated: usize,
    removed: usize,
    unchanged: usize,
    accepted_identical_edits: usize,
}

#[derive(Debug, Facet)]
struct PromotionTargetChanges {
    created: Vec<String>,
    updated: Vec<String>,
    removed: Vec<String>,
    accepted_identical_edits: Vec<String>,
}

impl PromotionArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        self.invoke_in_with_policy(cancellation, invocation_dir, &PUBLIC_REPAIR_POLICY)
    }

    /// The policy is never selected by command-line input. Tests alone pass a
    /// synthetic identity while exercising this same read/verify/apply flow.
    fn invoke_in_with_policy(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        policy: &RepairIdentityPolicy<'_>,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let request_path = if self.request.is_absolute() {
            self.request.clone()
        } else {
            invocation_dir.join(&self.request)
        };
        let file = fs::File::open(&request_path).wrap_err_with(|| {
            format!("cannot open promotion request '{}'", request_path.display())
        })?;
        let metadata = file.metadata().wrap_err_with(|| {
            format!(
                "cannot inspect promotion request '{}'",
                request_path.display()
            )
        })?;
        ensure!(
            metadata.is_file(),
            "promotion request must be a regular file"
        );
        ensure!(
            metadata.len() <= MAX_REQUEST_BYTES,
            "promotion request exceeds the {MAX_REQUEST_BYTES}-byte limit"
        );
        let mut bytes = Vec::new();
        file.take(MAX_REQUEST_BYTES + 1)
            .read_to_end(&mut bytes)
            .wrap_err_with(|| {
                format!("cannot read promotion request '{}'", request_path.display())
            })?;
        ensure!(
            bytes.len() as u64 <= MAX_REQUEST_BYTES,
            "promotion request exceeds the {MAX_REQUEST_BYTES}-byte limit"
        );
        let text = String::from_utf8(bytes).wrap_err("promotion request is not UTF-8")?;
        let request = parse_request(&text)?;
        let mode = self.validate_mode(&request.transition)?;
        if mode == PromotionMode::Apply {
            let report =
                self.apply_in_with_policy(&request, cancellation, invocation_dir, policy)?;
            return Ok(CliOutput::facet(PromotionCliReport::from_core(
                report, mode,
            )));
        }
        cancellation.bail_if_cancelled()?;
        let report = promote(&request, mode)?;
        Ok(CliOutput::facet(PromotionCliReport::from_core(
            report, mode,
        )))
    }

    fn apply_in_with_policy(
        &self,
        request: &PromotionRequest,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        policy: &RepairIdentityPolicy<'_>,
    ) -> Result<PromotionReport> {
        let lock_path = self
            .candidate_lock
            .as_ref()
            .expect("validated Apply requires a candidate lock");
        let lock_path = if lock_path.is_absolute() {
            lock_path.clone()
        } else {
            invocation_dir.join(lock_path)
        };
        let (lock, actual_sha256) = read_candidate_lock(&lock_path)?;
        ensure!(
            Some(&actual_sha256) == self.candidate_lock_sha256.as_ref(),
            "candidate lock SHA-256 differs from --candidate-lock-sha256"
        );
        let roots = bind_candidate_lock(request, &lock)?;
        if matches!(
            request.transition,
            PromotionTransition::PreAcceptanceBaselineRepair { .. }
        ) {
            ensure_repair_policy(request, policy)?;
        }
        ensure_external_candidate_lock(&lock_path, &request.repository_root, &roots)?;
        cancellation.bail_if_cancelled()?;
        require_full_candidate_verification(
            cancellation,
            &request.repository_root,
            &lock,
            &roots,
            actual_sha256.clone(),
            #[cfg(test)]
            policy.synthetic_gradle_overlays,
        )?;
        #[cfg(test)]
        if let Some(before_promotion) = policy.synthetic_before_promotion {
            before_promotion()?;
        }
        let verify_again = |_stage: &Path| {
            require_full_candidate_verification(
                cancellation,
                &request.repository_root,
                &lock,
                &roots,
                actual_sha256.clone(),
                #[cfg(test)]
                policy.synthetic_gradle_overlays,
            )
        };
        cancellation.bail_if_cancelled()?;
        match request.transition {
            PromotionTransition::NewImmutablePreset => {
                promote_new_immutable_with_pre_apply_verification(request, &verify_again)
            }
            PromotionTransition::PreAcceptanceBaselineRepair { .. } => {
                promote_repair_with_pre_apply_verification(request, &verify_again)
            }
        }
    }

    fn validate_mode(&self, transition: &PromotionTransition) -> Result<PromotionMode> {
        if !self.apply {
            ensure!(
                !self.ack_pre_acceptance_baseline_repair
                    && !self.ack_new_immutable_preset
                    && self.candidate_lock.is_none()
                    && self.candidate_lock_sha256.is_none(),
                "promotion acknowledgements and candidate-lock flags require --apply"
            );
            return Ok(PromotionMode::DryRun);
        }
        ensure!(
            self.candidate_lock.is_some() && self.candidate_lock_sha256.is_some(),
            "promotion Apply requires --candidate-lock and --candidate-lock-sha256"
        );
        match transition {
            PromotionTransition::NewImmutablePreset => {
                ensure!(
                    self.ack_new_immutable_preset && !self.ack_pre_acceptance_baseline_repair,
                    "immutable-preset Apply requires only --ack-new-immutable-preset"
                );
                Ok(PromotionMode::Apply)
            }
            PromotionTransition::PreAcceptanceBaselineRepair { .. } => {
                ensure!(
                    self.ack_pre_acceptance_baseline_repair && !self.ack_new_immutable_preset,
                    "repair Apply requires only --ack-pre-acceptance-baseline-repair"
                );
                Ok(PromotionMode::Apply)
            }
        }
    }
}

fn require_full_candidate_verification(
    cancellation: &CancellationToken,
    repository_root: &Path,
    lock: &SourceCandidateLock,
    roots: &BTreeMap<String, PathBuf>,
    lock_sha256: String,
    #[cfg(test)] synthetic_gradle_overlays: Option<&BTreeMap<String, String>>,
) -> Result<()> {
    let report = verify_candidate_in(
        cancellation,
        repository_root,
        lock,
        roots,
        lock_sha256,
        #[cfg(test)]
        synthetic_gradle_overlays,
    )?;
    ensure!(
        report.deterministic_source_check && report.verified_targets.len() == 10,
        "promotion Apply requires full ten-target deterministic candidate verification"
    );
    Ok(())
}

fn ensure_repair_policy(
    request: &PromotionRequest,
    policy: &RepairIdentityPolicy<'_>,
) -> Result<()> {
    let PromotionTransition::PreAcceptanceBaselineRepair {
        expected_old_preset_id,
        expected_old_definition_identity,
        reviewed_operations,
    } = &request.transition
    else {
        bail!("repair Apply requires a pre-acceptance repair transition")
    };
    ensure!(
        request.candidate_preset_id == REPAIR_PRESET_ID
            && expected_old_preset_id == REPAIR_PRESET_ID,
        "repair Apply is pinned to the 4.34.0 preset"
    );
    ensure!(
        expected_old_definition_identity == policy.old_definition_identity
            && request.candidate_definition_identity == policy.new_definition_identity,
        "repair Apply definition identities differ from the reviewed old/new pair"
    );
    let refmaps = reviewed_operations
        .iter()
        .filter(|operation| operation.relative_path == "src/main/resources/sfm.refmap.json")
        .collect::<Vec<_>>();
    ensure!(
        refmaps.len() == 5
            && refmaps.iter().all(|operation| {
                operation.kind == ReviewedOperationKind::Create
                    && operation.old_sha256.is_none()
                    && operation.new_sha256 == policy.refmap_sha256
            }),
        "repair Apply requires the reviewed published refmap bytes on five create operations"
    );
    Ok(())
}

/// Make every overlapping request field an exact assertion about the same
/// reviewed lock. The returned roots are the only roots passed to verification.
fn bind_candidate_lock(
    request: &PromotionRequest,
    lock: &SourceCandidateLock,
) -> Result<BTreeMap<String, PathBuf>> {
    ensure!(
        !request.accept_identical_edits,
        "promotion Apply forbids accept_identical_edits"
    );
    ensure!(
        request.reviewed_head_commit == lock.source_commit,
        "candidate lock source commit differs from promotion request"
    );
    ensure!(
        request.reviewed_source_manifest_sha256 == lock.source_manifest_sha256,
        "candidate lock source manifest differs from promotion request"
    );
    ensure!(
        request.compatibility_evidence_relative_path == lock.compatibility_evidence_relative_path
            && request.reviewed_compatibility_evidence_sha256 == lock.compatibility_evidence_sha256,
        "candidate lock compatibility evidence differs from promotion request"
    );
    ensure!(
        request.candidate_preset_id == lock.candidate_preset_id
            && request.candidate_definition_identity == lock.candidate_definition_identity,
        "candidate lock preset differs from promotion request"
    );
    ensure!(
        request.candidates.len() == 10 && lock.targets.len() == 10,
        "promotion Apply requires ten request and lock targets"
    );
    let mut roots = BTreeMap::new();
    for locked in &lock.targets {
        let requested = request.candidates.get(&locked.target_id).ok_or_else(|| {
            eyre::eyre!(
                "candidate lock target '{}' is absent from promotion request",
                locked.target_id
            )
        })?;
        ensure!(
            requested.reviewed_manifest_sha256 == locked.provenance_manifest_sha256,
            "candidate provenance manifest differs for '{}'",
            locked.target_id
        );
        ensure!(
            requested.production_jar_relative_path == locked.production_jar_relative_path
                && requested.production_jar_sha256 == locked.production_jar_sha256,
            "candidate production JAR differs for '{}'",
            locked.target_id
        );
        ensure!(
            requested.production_task == locked.production_task
                && requested.jdk_major == locked.jdk_major
                && requested.jdk_build_id == locked.jdk_build_id,
            "candidate build task or JDK differs for '{}'",
            locked.target_id
        );
        ensure!(
            roots
                .insert(locked.target_id.clone(), requested.project_root.clone())
                .is_none(),
            "duplicate candidate lock target '{}'",
            locked.target_id
        );
    }
    ensure!(
        roots.keys().eq(request.candidates.keys()),
        "candidate lock target set differs from promotion request"
    );
    Ok(roots)
}

fn ensure_external_candidate_lock(
    lock_path: &Path,
    repository_root: &Path,
    candidate_roots: &BTreeMap<String, PathBuf>,
) -> Result<()> {
    let lock_path = fs::canonicalize(lock_path)
        .wrap_err_with(|| format!("cannot resolve candidate lock '{}'", lock_path.display()))?;
    let repository_root = fs::canonicalize(repository_root).wrap_err_with(|| {
        format!(
            "cannot resolve promotion repository '{}'",
            repository_root.display()
        )
    })?;
    ensure!(
        !path_is_within(&lock_path, &repository_root),
        "promotion Apply requires candidate lock outside the source repository"
    );
    for (target, root) in candidate_roots {
        let root = fs::canonicalize(root).wrap_err_with(|| {
            format!(
                "cannot resolve candidate root for '{target}' at '{}'",
                root.display()
            )
        })?;
        ensure!(
            !path_is_within(&lock_path, &root),
            "promotion Apply requires candidate lock outside candidate root '{target}'"
        );
    }
    Ok(())
}

#[cfg(windows)]
fn path_is_within(path: &Path, parent: &Path) -> bool {
    let mut parts = path.components();
    parent.components().all(|parent_part| {
        parts.next().is_some_and(|part| {
            part.as_os_str().to_string_lossy().to_lowercase()
                == parent_part.as_os_str().to_string_lossy().to_lowercase()
        })
    })
}

#[cfg(not(windows))]
fn path_is_within(path: &Path, parent: &Path) -> bool {
    path.starts_with(parent)
}

fn parse_request(text: &str) -> Result<PromotionRequest> {
    let input: PromotionRequestFile =
        facet_json::from_str(text).wrap_err("cannot parse strict source-promotion request JSON")?;
    ensure!(
        input.schema == REQUEST_SCHEMA,
        "unsupported source-promotion request schema '{}' (expected '{REQUEST_SCHEMA}')",
        input.schema
    );
    let repository_root = PathBuf::from(&input.repository_root);
    ensure!(
        repository_root.is_absolute(),
        "promotion repository_root must be absolute"
    );
    validate_relative_path(&input.compatibility_evidence_relative_path)
        .wrap_err("unsafe compatibility evidence path")?;
    ensure!(
        input.candidates.len() == 10,
        "source promotion requires exactly ten candidate targets"
    );
    let mut candidates = BTreeMap::new();
    for candidate in input.candidates {
        let target = candidate.target_id;
        let project_root = PathBuf::from(&candidate.project_root);
        ensure!(
            project_root.is_absolute(),
            "candidate project_root for '{target}' must be absolute"
        );
        validate_relative_path(&candidate.production_jar_relative_path)
            .wrap_err_with(|| format!("unsafe production JAR path for '{target}'"))?;
        ensure!(
            candidates
                .insert(
                    target.clone(),
                    PromotionCandidate {
                        project_root,
                        reviewed_manifest_sha256: candidate.reviewed_manifest_sha256,
                        production_jar_relative_path: candidate.production_jar_relative_path,
                        production_jar_sha256: candidate.production_jar_sha256,
                        production_task: candidate.production_task,
                        jdk_major: candidate.jdk_major,
                        jdk_build_id: candidate.jdk_build_id,
                    },
                )
                .is_none(),
            "duplicate source-promotion candidate target '{target}'"
        );
    }
    let transition = match (
        input.transition.kind.as_str(),
        input.transition.expected_old_preset_id,
        input.transition.expected_old_definition_identity,
        input.transition.reviewed_operations,
    ) {
        ("new_immutable_preset", None, None, None) => PromotionTransition::NewImmutablePreset,
        (
            "pre_acceptance_baseline_repair",
            Some(expected_old_preset_id),
            Some(expected_old_definition_identity),
            Some(reviewed_operations),
        ) => PromotionTransition::PreAcceptanceBaselineRepair {
            expected_old_preset_id,
            expected_old_definition_identity,
            reviewed_operations: reviewed_operations
                .into_iter()
                .map(ReviewedPromotionOperationFile::into_core)
                .collect::<Result<Vec<_>>>()?,
        },
        ("new_immutable_preset", _, _, _) => {
            bail!("immutable-preset transition cannot contain repair-only fields")
        }
        ("pre_acceptance_baseline_repair", _, _, _) => {
            bail!(
                "pre-acceptance repair requires both expected-old identity fields and reviewed operations"
            )
        }
        (kind, _, _, _) => bail!("unknown source-promotion transition kind '{kind}'"),
    };
    Ok(PromotionRequest {
        repository_root,
        reviewed_head_commit: input.reviewed_head_commit,
        reviewed_source_manifest_sha256: input.reviewed_source_manifest_sha256,
        compatibility_evidence_relative_path: input.compatibility_evidence_relative_path,
        reviewed_compatibility_evidence_sha256: input.reviewed_compatibility_evidence_sha256,
        candidates,
        candidate_preset_id: input.candidate_preset_id,
        candidate_definition_identity: input.candidate_definition_identity,
        transition,
        accept_identical_edits: input.accept_identical_edits,
    })
}

impl ReviewedPromotionOperationFile {
    fn into_core(self) -> Result<ReviewedPromotionOperation> {
        let kind = match self.kind.as_str() {
            "create" => ReviewedOperationKind::Create,
            "manifest" => ReviewedOperationKind::Manifest,
            other => bail!("unknown reviewed promotion operation kind '{other}'"),
        };
        Ok(ReviewedPromotionOperation {
            target_id: self.target_id,
            relative_path: self.relative_path,
            kind,
            old_sha256: self.old_sha256,
            new_sha256: self.new_sha256,
        })
    }
}

impl PromotionCliReport {
    fn from_core(report: PromotionReport, mode: PromotionMode) -> Self {
        Self {
            schema: REPORT_SCHEMA.to_owned(),
            mode: match mode {
                PromotionMode::DryRun => "dry_run",
                PromotionMode::Apply => "apply",
            }
            .to_owned(),
            head_commit: report.head_commit,
            source_manifest_sha256: report.source_manifest_sha256,
            old_preset_id: report.old_preset_id,
            old_definition_identity: report.old_definition_identity,
            candidate_preset_id: report.candidate_preset_id,
            candidate_definition_identity: report.candidate_definition_identity,
            targets: report
                .targets
                .into_iter()
                .map(|(target, report)| (target, PromotionTargetCliReport::from_core(report)))
                .collect(),
            recovery_stage: report.recovery_stage.map(|path| path.display().to_string()),
        }
    }
}

impl PromotionTargetCliReport {
    fn from_core(report: TargetPromotionReport) -> Self {
        Self {
            counts: PromotionTargetCounts {
                created: report.created.len(),
                updated: report.updated.len(),
                removed: report.removed.len(),
                unchanged: report.unchanged.len(),
                accepted_identical_edits: report.accepted_identical_edits.len(),
            },
            changes: PromotionTargetChanges {
                created: report.created,
                updated: report.updated,
                removed: report.removed,
                accepted_identical_edits: report.accepted_identical_edits,
            },
            old_manifest_sha256: report.old_manifest_sha256,
            candidate_manifest_sha256: report.candidate_manifest_sha256,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::CandidateVerifyArgs;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::cli::source::SourceProjectArgs;
    use crate::source_projection::candidate_lock::CandidateTargetLock;
    use crate::source_projection::candidate_lock::tests::Fixture as CandidateFixture;
    use crate::source_projection::manifest::SourceProjectionManifest;
    use crate::source_projection::provenance::ProjectionProvenance;
    use crate::source_projection::provenance::sha256;
    use crate::source_projection::sync::MANIFEST_FILE;
    use walkdir::WalkDir;

    fn destination_snapshot(request: &PromotionRequest) -> BTreeMap<String, String> {
        let mut snapshot = BTreeMap::new();
        for target in request.candidates.keys() {
            let root = request
                .repository_root
                .join(format!("platform/minecraft/mc-version/{target}"));
            for entry in WalkDir::new(&root).follow_links(false) {
                let entry = entry.unwrap();
                let relative = entry.path().strip_prefix(&root).unwrap();
                let key = format!("{target}/{}", relative.display());
                let value = if entry.file_type().is_dir() {
                    "directory".to_owned()
                } else {
                    sha256(&fs::read(entry.path()).unwrap())
                };
                snapshot.insert(key, value);
            }
        }
        snapshot
    }

    fn request_json(transition: &str) -> String {
        let candidates = (0..10)
            .map(|index| {
                let root = std::env::temp_dir().join(format!("candidate-v{index}"));
                format!(
                    "{{\"target_id\":\"v{index}\",\"project_root\":{},\"reviewed_manifest_sha256\":\"{}\",\"production_jar_relative_path\":\"build/libs/sfm-v{index}.jar\",\"production_jar_sha256\":\"{}\",\"production_task\":\"jar\",\"jdk_major\":17,\"jdk_build_id\":\"JBRSDK-17.0.14\"}}",
                    facet_json::to_string(&root.display().to_string()).unwrap(),
                    format!("sha256:{}", "a".repeat(64)),
                    format!("sha256:{}", "d".repeat(64))
                )
            })
            .collect::<Vec<_>>()
            .join(",");
        format!(
            "{{\"schema\":\"{REQUEST_SCHEMA}\",\"repository_root\":{},\"reviewed_head_commit\":\"{}\",\"reviewed_source_manifest_sha256\":\"{}\",\"compatibility_evidence_relative_path\":\"docs/acceptance.md\",\"reviewed_compatibility_evidence_sha256\":\"{}\",\"candidate_preset_id\":\"released-new\",\"candidate_definition_identity\":\"blake3:new\",\"transition\":{transition},\"accept_identical_edits\":false,\"candidates\":[{candidates}]}}",
            facet_json::to_string(&std::env::temp_dir().display().to_string()).unwrap(),
            "b".repeat(40),
            format!("sha256:{}", "c".repeat(64)),
            format!("sha256:{}", "e".repeat(64)),
        )
    }

    fn request_file_json(request: &PromotionRequest) -> String {
        let transition = match &request.transition {
            PromotionTransition::NewImmutablePreset => PromotionTransitionFile {
                kind: "new_immutable_preset".to_owned(),
                expected_old_preset_id: None,
                expected_old_definition_identity: None,
                reviewed_operations: None,
            },
            PromotionTransition::PreAcceptanceBaselineRepair {
                expected_old_preset_id,
                expected_old_definition_identity,
                reviewed_operations,
            } => PromotionTransitionFile {
                kind: "pre_acceptance_baseline_repair".to_owned(),
                expected_old_preset_id: Some(expected_old_preset_id.clone()),
                expected_old_definition_identity: Some(expected_old_definition_identity.clone()),
                reviewed_operations: Some(
                    reviewed_operations
                        .iter()
                        .map(|operation| ReviewedPromotionOperationFile {
                            target_id: operation.target_id.clone(),
                            relative_path: operation.relative_path.clone(),
                            kind: match operation.kind {
                                ReviewedOperationKind::Create => "create",
                                ReviewedOperationKind::Manifest => "manifest",
                            }
                            .to_owned(),
                            old_sha256: operation.old_sha256.clone(),
                            new_sha256: operation.new_sha256.clone(),
                        })
                        .collect(),
                ),
            },
        };
        facet_json::to_string_pretty(&PromotionRequestFile {
            schema: REQUEST_SCHEMA.to_owned(),
            repository_root: request.repository_root.display().to_string(),
            reviewed_head_commit: request.reviewed_head_commit.clone(),
            reviewed_source_manifest_sha256: request.reviewed_source_manifest_sha256.clone(),
            compatibility_evidence_relative_path: request
                .compatibility_evidence_relative_path
                .clone(),
            reviewed_compatibility_evidence_sha256: request
                .reviewed_compatibility_evidence_sha256
                .clone(),
            candidate_preset_id: request.candidate_preset_id.clone(),
            candidate_definition_identity: request.candidate_definition_identity.clone(),
            transition,
            accept_identical_edits: request.accept_identical_edits,
            candidates: request
                .candidates
                .iter()
                .map(|(target_id, candidate)| PromotionCandidateFile {
                    target_id: target_id.clone(),
                    project_root: candidate.project_root.display().to_string(),
                    reviewed_manifest_sha256: candidate.reviewed_manifest_sha256.clone(),
                    production_jar_relative_path: candidate.production_jar_relative_path.clone(),
                    production_jar_sha256: candidate.production_jar_sha256.clone(),
                    production_task: candidate.production_task.clone(),
                    jdk_major: candidate.jdk_major,
                    jdk_build_id: candidate.jdk_build_id.clone(),
                })
                .collect(),
        })
        .unwrap()
    }

    /// Turn the synthetic repair fixture into a true old-ID -> new-ID
    /// transition, while preserving its independent ten candidate JAR roots.
    fn new_immutable_fixture() -> (
        CandidateFixture,
        PromotionRequest,
        SourceCandidateLock,
        BTreeMap<String, String>,
    ) {
        new_immutable_fixture_with_bundled_1_19_2_profile(false)
    }

    fn new_immutable_fixture_with_bundled_1_19_2_profile(
        bundled_1_19_2: bool,
    ) -> (
        CandidateFixture,
        PromotionRequest,
        SourceCandidateLock,
        BTreeMap<String, String>,
    ) {
        let (fixture, mut request, _, gradle_overlays) = if bundled_1_19_2 {
            CandidateFixture::new_bundled_1_19_2_for_cli()
        } else {
            CandidateFixture::new_repair_for_cli()
        };
        let manifest_path = fixture
            .repo()
            .join("platform/minecraft/source-projection.json");
        let mut source_manifest =
            SourceProjectionManifest::from_json(&fs::read_to_string(&manifest_path).unwrap())
                .unwrap();
        let mut old_preset = source_manifest.presets[0].clone();
        old_preset.id = "released-4.33.0".to_owned();
        old_preset.identity = source_manifest
            .compute_preset_identity(&old_preset)
            .unwrap();
        let old_identity = old_preset.identity.clone();
        source_manifest.presets.push(old_preset);
        let source_manifest_bytes = source_manifest.to_json().unwrap();
        fs::write(&manifest_path, &source_manifest_bytes).unwrap();
        for target in request.candidates.keys() {
            let destination_manifest = fixture.repo().join(format!(
                "platform/minecraft/mc-version/{target}/{MANIFEST_FILE}"
            ));
            let mut old = ProjectionProvenance::from_json(
                &fs::read_to_string(&destination_manifest).unwrap(),
            )
            .unwrap();
            old.preset_id = "released-4.33.0".to_owned();
            old.preset_definition_identity.clone_from(&old_identity);
            fs::write(destination_manifest, old.to_json().unwrap()).unwrap();
        }
        let git = |args: &[&str]| {
            let output = std::process::Command::new("git")
                .arg("-C")
                .arg(fixture.repo())
                .args(args)
                .output()
                .unwrap();
            assert!(
                output.status.success(),
                "git {:?}: {}",
                args,
                String::from_utf8_lossy(&output.stderr)
            );
            String::from_utf8(output.stdout).unwrap()
        };
        git(&["add", "."]);
        git(&["commit", "-qm", "reviewed immutable baseline"]);
        let mut lock = fixture.lock().clone();
        lock.source_commit = git(&["rev-parse", "HEAD"]).trim().to_owned();
        lock.source_manifest_sha256 = sha256(source_manifest_bytes.as_bytes());
        request.reviewed_head_commit.clone_from(&lock.source_commit);
        request
            .reviewed_source_manifest_sha256
            .clone_from(&lock.source_manifest_sha256);
        request.transition = PromotionTransition::NewImmutablePreset;
        (fixture, request, lock, gradle_overlays)
    }

    #[test]
    fn source_promote_parses_as_dry_run_by_default() {
        let parsed = figue::from_slice::<Cli>(&[
            "--output-format",
            "json",
            "source",
            "promote",
            "--request",
            "reviewed.json",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        assert_eq!(parsed.global_args.output_format, Some(OutputFormat::Json));
        let Command::Source(SourceArgs {
            command: SourceCommand::Promote(args),
        }) = parsed.command
        else {
            panic!("expected source promote command");
        };
        assert_eq!(args.request, PathBuf::from("reviewed.json"));
        assert_eq!(
            args.validate_mode(&PromotionTransition::NewImmutablePreset)
                .unwrap(),
            PromotionMode::DryRun
        );
        assert!(!args.apply);
    }

    #[test]
    fn apply_and_repair_acknowledgement_are_explicit_flags() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "promote",
            "--request",
            "reviewed.json",
            "--apply",
            "--ack-pre-acceptance-baseline-repair",
            "--candidate-lock",
            "candidate.json",
            "--candidate-lock-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::Promote(args),
        }) = parsed.command
        else {
            panic!("expected source promote command");
        };
        assert!(args.apply);
        assert!(args.ack_pre_acceptance_baseline_repair);
        assert_eq!(args.candidate_lock, Some(PathBuf::from("candidate.json")));
        assert!(args.candidate_lock_sha256.unwrap().starts_with("sha256:"));
    }

    #[test]
    fn new_immutable_acknowledgement_is_an_explicit_flag() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "promote",
            "--request",
            "reviewed.json",
            "--apply",
            "--ack-new-immutable-preset",
            "--candidate-lock",
            "candidate.json",
            "--candidate-lock-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::Promote(args),
        }) = parsed.command
        else {
            panic!("expected source promote command");
        };
        assert_eq!(
            args.validate_mode(&PromotionTransition::NewImmutablePreset)
                .unwrap(),
            PromotionMode::Apply
        );
        assert!(!args.ack_pre_acceptance_baseline_repair);
    }

    #[test]
    fn request_rejects_unknown_schema_fields_and_incomplete_matrix() {
        let json = request_json("{\"kind\":\"new_immutable_preset\"}");
        let parsed = parse_request(&json).unwrap();
        assert!(
            parsed
                .reviewed_source_manifest_sha256
                .starts_with("sha256:")
        );
        assert!(
            parsed.candidates["v0"]
                .reviewed_manifest_sha256
                .starts_with("sha256:")
        );
        let unknown = json.replacen(
            "\"accept_identical_edits\":false",
            "\"surprise\":true,\"accept_identical_edits\":false",
            1,
        );
        assert!(parse_request(&unknown).is_err());
        let unknown_candidate = json.replacen(
            "\"reviewed_manifest_sha256\"",
            "\"surprise\":true,\"reviewed_manifest_sha256\"",
            1,
        );
        assert!(parse_request(&unknown_candidate).is_err());
        let missing_jdk_build_id = json.replacen(",\"jdk_build_id\":\"JBRSDK-17.0.14\"", "", 1);
        assert!(parse_request(&missing_jdk_build_id).is_err());
        let wrong_schema = json.replacen(REQUEST_SCHEMA, "sfm:source_promotion_request@1", 1);
        assert!(
            parse_request(&wrong_schema)
                .unwrap_err()
                .to_string()
                .contains("unsupported")
        );
        let last_entry = json.find(",{\"target_id\":\"v9\"").unwrap();
        let incomplete = format!("{}]}}", &json[..last_entry]);
        assert!(
            parse_request(&incomplete)
                .unwrap_err()
                .to_string()
                .contains("exactly ten")
        );
        let duplicate = json.replacen("\"target_id\":\"v9\"", "\"target_id\":\"v8\"", 1);
        assert!(
            parse_request(&duplicate)
                .unwrap_err()
                .to_string()
                .contains("duplicate")
        );
    }

    #[test]
    fn request_rejects_unknown_transition_fields_and_relative_roots() {
        let repair = request_json(
            "{\"kind\":\"pre_acceptance_baseline_repair\",\"expected_old_preset_id\":\"released-old\",\"expected_old_definition_identity\":\"blake3:old\",\"reviewed_operations\":[]}",
        );
        assert!(matches!(
            parse_request(&repair).unwrap().transition,
            PromotionTransition::PreAcceptanceBaselineRepair { .. }
        ));
        let extra = repair.replacen(
            "\"expected_old_preset_id\"",
            "\"surprise\":true,\"expected_old_preset_id\"",
            1,
        );
        assert!(parse_request(&extra).is_err());
        let relative = repair.replacen(
            &facet_json::to_string(&std::env::temp_dir().display().to_string()).unwrap(),
            "\"relative/repo\"",
            1,
        );
        assert!(
            parse_request(&relative)
                .unwrap_err()
                .to_string()
                .contains("absolute")
        );
        let relative_candidate = repair.replacen(
            &facet_json::to_string(
                &std::env::temp_dir()
                    .join("candidate-v0")
                    .display()
                    .to_string(),
            )
            .unwrap(),
            "\"relative/candidate\"",
            1,
        );
        assert!(
            parse_request(&relative_candidate)
                .unwrap_err()
                .to_string()
                .contains("candidate project_root")
        );
        let escaping_evidence = repair.replacen(
            "\"compatibility_evidence_relative_path\":\"docs/acceptance.md\"",
            "\"compatibility_evidence_relative_path\":\"../acceptance.md\"",
            1,
        );
        assert!(
            parse_request(&escaping_evidence)
                .unwrap_err()
                .to_string()
                .contains("unsafe compatibility evidence path")
        );
        let escaping_jar = repair.replacen(
            "\"production_jar_relative_path\":\"build/libs/sfm-v0.jar\"",
            "\"production_jar_relative_path\":\"build/libs/../../outside.jar\"",
            1,
        );
        assert!(
            parse_request(&escaping_jar)
                .unwrap_err()
                .to_string()
                .contains("unsafe production JAR path")
        );
        let immutable_with_repair_field = request_json(
            "{\"kind\":\"new_immutable_preset\",\"expected_old_preset_id\":\"released-old\"}",
        );
        assert!(parse_request(&immutable_with_repair_field).is_err());
    }

    #[test]
    fn repair_operation_lock_has_strict_typed_fields() {
        let transition = format!(
            "{{\"kind\":\"pre_acceptance_baseline_repair\",\"expected_old_preset_id\":\"released-old\",\"expected_old_definition_identity\":\"blake3:old\",\"reviewed_operations\":[{{\"target_id\":\"1.20.2\",\"relative_path\":\"src/main/resources/sfm.refmap.json\",\"kind\":\"create\",\"old_sha256\":null,\"new_sha256\":\"sha256:{}\"}}]}}",
            "d".repeat(64)
        );
        let json = request_json(&transition);
        let parsed = parse_request(&json).unwrap();
        let PromotionTransition::PreAcceptanceBaselineRepair {
            reviewed_operations,
            ..
        } = parsed.transition
        else {
            panic!("expected repair transition");
        };
        assert_eq!(reviewed_operations.len(), 1);
        assert_eq!(reviewed_operations[0].kind, ReviewedOperationKind::Create);
        assert!(reviewed_operations[0].old_sha256.is_none());
        let unknown = json.replacen("\"new_sha256\"", "\"surprise\":true,\"new_sha256\"", 1);
        assert!(parse_request(&unknown).is_err());
        let bad_kind = json.replacen("\"kind\":\"create\"", "\"kind\":\"update\"", 1);
        assert!(parse_request(&bad_kind).is_err());
    }

    #[test]
    fn repair_apply_requires_acknowledgement_and_exact_lock_inputs() {
        let repair = PromotionTransition::PreAcceptanceBaselineRepair {
            expected_old_preset_id: "released-old".to_owned(),
            expected_old_definition_identity: "blake3:old".to_owned(),
            reviewed_operations: vec![],
        };
        for acknowledged in [false, true] {
            let args = PromotionArgs {
                request: PathBuf::from("reviewed.json"),
                apply: true,
                ack_pre_acceptance_baseline_repair: acknowledged,
                ack_new_immutable_preset: false,
                candidate_lock: None,
                candidate_lock_sha256: None,
            };
            assert!(args.validate_mode(&repair).is_err());
        }
        let dry_run = PromotionArgs {
            request: PathBuf::from("reviewed.json"),
            apply: false,
            ack_pre_acceptance_baseline_repair: false,
            ack_new_immutable_preset: false,
            candidate_lock: None,
            candidate_lock_sha256: None,
        };
        assert_eq!(
            dry_run.validate_mode(&repair).unwrap(),
            PromotionMode::DryRun
        );
        let invalid_ack = PromotionArgs {
            ack_pre_acceptance_baseline_repair: true,
            ..dry_run
        };
        assert!(invalid_ack.validate_mode(&repair).is_err());

        let normal_apply = PromotionArgs {
            request: PathBuf::from("reviewed.json"),
            apply: true,
            ack_pre_acceptance_baseline_repair: false,
            ack_new_immutable_preset: false,
            candidate_lock: None,
            candidate_lock_sha256: None,
        };
        assert!(
            normal_apply
                .validate_mode(&PromotionTransition::NewImmutablePreset)
                .is_err()
        );
        let wrongly_acknowledged = PromotionArgs {
            ack_pre_acceptance_baseline_repair: true,
            ..normal_apply
        };
        assert!(
            wrongly_acknowledged
                .validate_mode(&PromotionTransition::NewImmutablePreset)
                .is_err()
        );
        let complete_repair = PromotionArgs {
            request: PathBuf::from("reviewed.json"),
            apply: true,
            ack_pre_acceptance_baseline_repair: true,
            ack_new_immutable_preset: false,
            candidate_lock: Some(PathBuf::from("candidate.json")),
            candidate_lock_sha256: Some(format!("sha256:{}", "a".repeat(64))),
        };
        assert_eq!(
            complete_repair.validate_mode(&repair).unwrap(),
            PromotionMode::Apply
        );
        let incomplete = PromotionArgs {
            candidate_lock_sha256: None,
            ..complete_repair
        };
        assert!(incomplete.validate_mode(&repair).is_err());
    }

    #[test]
    fn immutable_apply_requires_exactly_its_acknowledgement_and_complete_lock_flags() {
        let complete = PromotionArgs {
            request: PathBuf::from("reviewed.json"),
            apply: true,
            ack_pre_acceptance_baseline_repair: false,
            ack_new_immutable_preset: true,
            candidate_lock: Some(PathBuf::from("candidate.json")),
            candidate_lock_sha256: Some(format!("sha256:{}", "a".repeat(64))),
        };
        assert_eq!(
            complete
                .validate_mode(&PromotionTransition::NewImmutablePreset)
                .unwrap(),
            PromotionMode::Apply
        );
        assert!(
            PromotionArgs {
                ack_pre_acceptance_baseline_repair: true,
                ..complete.clone()
            }
            .validate_mode(&PromotionTransition::NewImmutablePreset)
            .is_err()
        );
        assert!(
            PromotionArgs {
                ack_new_immutable_preset: false,
                ..complete.clone()
            }
            .validate_mode(&PromotionTransition::NewImmutablePreset)
            .is_err()
        );
        assert!(
            PromotionArgs {
                candidate_lock_sha256: None,
                ..complete.clone()
            }
            .validate_mode(&PromotionTransition::NewImmutablePreset)
            .is_err()
        );
        assert!(
            PromotionArgs {
                apply: false,
                ..complete.clone()
            }
            .validate_mode(&PromotionTransition::NewImmutablePreset)
            .is_err()
        );
        assert!(
            complete
                .validate_mode(&PromotionTransition::PreAcceptanceBaselineRepair {
                    expected_old_preset_id: "released-4.34.0".to_owned(),
                    expected_old_definition_identity: "blake3:old".to_owned(),
                    reviewed_operations: vec![],
                })
                .is_err()
        );
    }

    #[test]
    fn public_repair_policy_pins_published_identities_and_refmap_bytes() {
        let (_, mut request, synthetic_refmap_sha256, _) = CandidateFixture::new_repair_for_cli();
        assert_ne!(
            request.candidate_definition_identity,
            PUBLIC_REPAIR_POLICY.new_definition_identity
        );
        assert_ne!(synthetic_refmap_sha256, PUBLIC_REPAIR_POLICY.refmap_sha256);
        assert!(ensure_repair_policy(&request, &PUBLIC_REPAIR_POLICY).is_err());
        let synthetic_new_identity = request.candidate_definition_identity.clone();
        let policy = RepairIdentityPolicy {
            old_definition_identity: PUBLIC_REPAIR_POLICY.old_definition_identity,
            new_definition_identity: &synthetic_new_identity,
            refmap_sha256: &synthetic_refmap_sha256,
            synthetic_gradle_overlays: None,
            synthetic_before_promotion: None,
        };
        ensure_repair_policy(&request, &policy).unwrap();
        request.candidate_definition_identity = PUBLIC_REPAIR_POLICY.new_definition_identity.into();
        assert!(ensure_repair_policy(&request, &policy).is_err());
        request.candidate_definition_identity = synthetic_new_identity.clone();
        let PromotionTransition::PreAcceptanceBaselineRepair {
            reviewed_operations,
            ..
        } = &mut request.transition
        else {
            unreachable!()
        };
        reviewed_operations
            .iter_mut()
            .find(|operation| operation.kind == ReviewedOperationKind::Create)
            .unwrap()
            .new_sha256 = PUBLIC_REPAIR_POLICY.refmap_sha256.into();
        assert!(ensure_repair_policy(&request, &policy).is_err());
    }

    #[test]
    fn synthetic_repair_apply_verifies_exact_lock_and_promotes_ten_temp_roots() {
        let (fixture, request, refmap_sha256, gradle_overlays) =
            CandidateFixture::new_repair_for_cli();
        let scratch = request.repository_root.parent().unwrap();
        let lock_path = scratch.join("reviewed-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(fixture.lock()).unwrap();
        fs::write(&lock_path, &lock_bytes).unwrap();
        let lock_sha256 = sha256(lock_bytes.as_bytes());
        let request_path = scratch.join("reviewed-promotion-request.json");
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let synthetic_new_identity = request.candidate_definition_identity.clone();
        let policy = RepairIdentityPolicy {
            old_definition_identity: PUBLIC_REPAIR_POLICY.old_definition_identity,
            new_definition_identity: &synthetic_new_identity,
            refmap_sha256: &refmap_sha256,
            synthetic_gradle_overlays: Some(&gradle_overlays),
            synthetic_before_promotion: None,
        };
        let make_args = |digest: String| PromotionArgs {
            request: request_path.clone(),
            apply: true,
            ack_pre_acceptance_baseline_repair: true,
            ack_new_immutable_preset: false,
            candidate_lock: Some(lock_path.clone()),
            candidate_lock_sha256: Some(digest),
        };
        assert!(
            make_args(format!("sha256:{}", "0".repeat(64)))
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("candidate lock SHA-256 differs")
        );
        assert!(
            make_args(lock_sha256.clone())
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &PUBLIC_REPAIR_POLICY)
                .unwrap_err()
                .to_string()
                .contains("definition identities differ")
        );
        let mut mismatched = request.clone();
        mismatched
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .jdk_build_id = "JBRSDK-17.0.99".to_owned();
        fs::write(&request_path, request_file_json(&mismatched)).unwrap();
        assert!(
            make_args(lock_sha256.clone())
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("build task or JDK differs")
        );
        mismatched = request.clone();
        mismatched
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .production_task = "reobfJarJar".to_owned();
        fs::write(&request_path, request_file_json(&mismatched)).unwrap();
        assert!(
            make_args(lock_sha256.clone())
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("build task or JDK differs")
        );
        mismatched = request.clone();
        mismatched.accept_identical_edits = true;
        fs::write(&request_path, request_file_json(&mismatched)).unwrap();
        assert!(
            make_args(lock_sha256.clone())
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("forbids accept_identical_edits")
        );
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let internal_lock = request.repository_root.join("internal-candidate-lock.json");
        fs::write(&internal_lock, &lock_bytes).unwrap();
        let internal_args = PromotionArgs {
            candidate_lock: Some(internal_lock.clone()),
            ..make_args(lock_sha256.clone())
        };
        assert!(
            internal_args
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("outside the source repository")
        );
        fs::remove_file(internal_lock).unwrap();
        let refmap = request
            .repository_root
            .join("platform/minecraft/mc-version/1.20.2/src/main/resources/sfm.refmap.json");
        assert!(!refmap.exists());
        let output = make_args(lock_sha256)
            .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
            .unwrap();
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(json.contains("\"mode\": \"apply\""));
        assert!(json.contains("\"candidate_preset_id\": \"released-4.34.0\""));
        assert!(refmap.exists());
        assert_eq!(sha256(&fs::read(refmap).unwrap()), refmap_sha256);
        for (target, candidate) in &request.candidates {
            let promoted = request
                .repository_root
                .join(format!("platform/minecraft/mc-version/{target}"))
                .join(MANIFEST_FILE);
            assert_eq!(
                sha256(&fs::read(promoted).unwrap()),
                candidate.reviewed_manifest_sha256,
                "candidate manifest was not installed for '{target}'"
            );
        }
    }

    #[test]
    fn synthetic_immutable_apply_verifies_exact_lock_and_promotes_ten_temp_roots() {
        let (fixture, request, lock, gradle_overlays) = new_immutable_fixture();
        let scratch = request.repository_root.parent().unwrap();
        let lock_path = scratch.join("reviewed-immutable-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(&lock).unwrap();
        fs::write(&lock_path, &lock_bytes).unwrap();
        let lock_sha256 = sha256(lock_bytes.as_bytes());
        let request_path = scratch.join("reviewed-immutable-promotion-request.json");
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let policy = RepairIdentityPolicy {
            old_definition_identity: PUBLIC_REPAIR_POLICY.old_definition_identity,
            new_definition_identity: PUBLIC_REPAIR_POLICY.new_definition_identity,
            refmap_sha256: PUBLIC_REPAIR_POLICY.refmap_sha256,
            synthetic_gradle_overlays: Some(&gradle_overlays),
            synthetic_before_promotion: None,
        };
        let make_args = |digest: String| PromotionArgs {
            request: request_path.clone(),
            apply: true,
            ack_pre_acceptance_baseline_repair: false,
            ack_new_immutable_preset: true,
            candidate_lock: Some(lock_path.clone()),
            candidate_lock_sha256: Some(digest),
        };
        let before_destinations = destination_snapshot(&request);
        assert!(
            make_args(format!("sha256:{}", "0".repeat(64)))
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("candidate lock SHA-256 differs")
        );
        let mut mismatched = request.clone();
        mismatched
            .candidates
            .get_mut("1.19.2")
            .unwrap()
            .jdk_build_id = "JBRSDK-17.0.99".to_owned();
        fs::write(&request_path, request_file_json(&mismatched)).unwrap();
        assert!(
            make_args(lock_sha256.clone())
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("build task or JDK differs")
        );
        mismatched = request.clone();
        mismatched.accept_identical_edits = true;
        fs::write(&request_path, request_file_json(&mismatched)).unwrap();
        assert!(
            make_args(lock_sha256.clone())
                .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
                .unwrap_err()
                .to_string()
                .contains("forbids accept_identical_edits")
        );
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let internal_lock = fixture
            .repo()
            .join("internal-immutable-candidate-lock.json");
        fs::write(&internal_lock, &lock_bytes).unwrap();
        assert!(
            PromotionArgs {
                candidate_lock: Some(internal_lock.clone()),
                ..make_args(lock_sha256.clone())
            }
            .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
            .unwrap_err()
            .to_string()
            .contains("outside the source repository")
        );
        fs::remove_file(internal_lock).unwrap();
        assert_eq!(destination_snapshot(&request), before_destinations);

        let output = make_args(lock_sha256)
            .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
            .unwrap();
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(json.contains("\"mode\": \"apply\""));
        assert!(json.contains("\"candidate_preset_id\": \"released-4.34.0\""));
        let stages = fs::read_dir(request.repository_root.join("platform/minecraft"))
            .unwrap()
            .map(|entry| entry.unwrap().path())
            .filter(|path| {
                path.file_name().is_some_and(|name| {
                    name.to_string_lossy()
                        .starts_with(".sfm-source-promotion-stage-")
                })
            })
            .collect::<Vec<_>>();
        assert_eq!(stages.len(), 1);
        assert!(stages[0].join("journal.json").is_file());
        assert!(stages[0].join("complete").is_file());
        for (target, candidate) in &request.candidates {
            let destination_manifest = request.repository_root.join(format!(
                "platform/minecraft/mc-version/{target}/{MANIFEST_FILE}"
            ));
            assert_eq!(
                sha256(&fs::read(destination_manifest).unwrap()),
                candidate.reviewed_manifest_sha256,
                "candidate manifest was not installed for '{target}'"
            );
        }
    }

    #[test]
    fn synthetic_bundled_immutable_apply_verifies_exact_lock_and_promotes_ten_temp_roots() {
        let (fixture, request, lock, gradle_overlays) =
            new_immutable_fixture_with_bundled_1_19_2_profile(true);
        assert_eq!(lock.targets.len(), 10);
        assert_eq!(request.candidates.len(), 10);
        let bundled = lock
            .targets
            .iter()
            .find(|target| target.target_id == "1.19.2")
            .unwrap();
        assert_eq!(bundled.gradle_profile, "rust-toolchain");
        assert_eq!(bundled.production_task, "reobfJarJar");
        assert_eq!(
            request.candidates["1.19.2"].production_task,
            bundled.production_task
        );

        let scratch = request.repository_root.parent().unwrap();
        let lock_path = scratch.join("reviewed-bundled-immutable-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(&lock).unwrap();
        fs::write(&lock_path, &lock_bytes).unwrap();
        let request_path = scratch.join("reviewed-bundled-immutable-promotion-request.json");
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let policy = RepairIdentityPolicy {
            old_definition_identity: PUBLIC_REPAIR_POLICY.old_definition_identity,
            new_definition_identity: PUBLIC_REPAIR_POLICY.new_definition_identity,
            refmap_sha256: PUBLIC_REPAIR_POLICY.refmap_sha256,
            synthetic_gradle_overlays: Some(&gradle_overlays),
            synthetic_before_promotion: None,
        };
        let output = PromotionArgs {
            request: request_path,
            apply: true,
            ack_pre_acceptance_baseline_repair: false,
            ack_new_immutable_preset: true,
            candidate_lock: Some(lock_path),
            candidate_lock_sha256: Some(sha256(lock_bytes.as_bytes())),
        }
        .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
        .unwrap()
        .render(Some(OutputFormat::Json), false)
        .unwrap()
        .unwrap();
        let report: PromotionCliReport = facet_json::from_str(&output).unwrap();
        assert_eq!(report.mode, "apply");
        assert_eq!(report.candidate_preset_id, lock.candidate_preset_id);
        assert_eq!(report.targets.len(), 10);
        assert!(report.targets.keys().eq(request.candidates.keys()));
        let stage = PathBuf::from(report.recovery_stage.as_deref().unwrap());
        assert!(stage.join("journal.json").is_file());
        assert!(stage.join("complete").is_file());
        for (target, candidate) in &request.candidates {
            let destination_manifest = fixture.repo().join(format!(
                "platform/minecraft/mc-version/{target}/{MANIFEST_FILE}"
            ));
            assert_eq!(
                sha256(&fs::read(destination_manifest).unwrap()),
                candidate.reviewed_manifest_sha256,
                "candidate manifest was not installed for '{target}'"
            );
            assert_eq!(
                report.targets[target].candidate_manifest_sha256,
                candidate.reviewed_manifest_sha256
            );
        }
    }

    #[test]
    fn real_pinned_tag_inputs_flow_through_fictional_ten_target_immutable_apply() {
        const VERSION: &str = "9.99.99-fixture";
        const PRESET: &str = "released-9.99.99-fixture";
        const EVIDENCE: &str = "synthetic-compatibility-evidence.md";
        let source_repo = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../..")
            .canonicalize()
            .unwrap();
        let git_source_repo = source_repo.to_string_lossy();
        let git_source_repo = git_source_repo
            .strip_prefix(r"\\?\")
            .unwrap_or(&git_source_repo)
            .replace('\\', "/");
        let scratch = tempfile::tempdir().unwrap();
        let repo = scratch.path().join("repo");
        let clone = std::process::Command::new("git")
            .args(["clone", "--quiet", "--shared", "--sparse"])
            .arg(&git_source_repo)
            .arg(&repo)
            .output()
            .unwrap();
        assert!(
            clone.status.success(),
            "temporary fixture clone failed: {}",
            String::from_utf8_lossy(&clone.stderr)
        );
        let git = |args: &[&str]| {
            let output = std::process::Command::new("git")
                .arg("-C")
                .arg(&repo)
                .args(args)
                .output()
                .unwrap();
            assert!(
                output.status.success(),
                "temporary fixture git {args:?} failed: {}",
                String::from_utf8_lossy(&output.stderr)
            );
            String::from_utf8(output.stdout).unwrap().trim().to_owned()
        };
        git(&["config", "core.longpaths", "true"]);
        git(&[
            "sparse-checkout",
            "set",
            "--cone",
            "platform/minecraft/src",
            "platform/minecraft/gradle",
            "platform/minecraft/release-baselines",
            "platform/minecraft/projection-resources",
            "platform/minecraft/mc-version",
        ]);
        git(&["config", "user.name", "SFM Fixture"]);
        git(&["config", "user.email", "sfm-fixture@example.invalid"]);
        let manifest_path = repo.join("platform/minecraft/source-projection.json");
        let mut manifest =
            SourceProjectionManifest::from_json(&fs::read_to_string(&manifest_path).unwrap())
                .unwrap();
        let mut future = manifest.preset("released-4.34.0").unwrap().clone();
        future.id = PRESET.to_owned();
        future.release_mod_version = Some(VERSION.to_owned());
        future.identity = manifest.compute_preset_identity(&future).unwrap();
        manifest.presets.push(future.clone());
        let manifest_bytes = manifest.to_json().unwrap();
        fs::write(&manifest_path, &manifest_bytes).unwrap();
        let evidence = b"Synthetic fixture only; no build or public compatibility claim.\n";
        fs::write(repo.join(EVIDENCE), evidence).unwrap();
        git(&[
            "add",
            "--",
            "platform/minecraft/source-projection.json",
            EVIDENCE,
        ]);
        git(&["commit", "-qm", "fictional future preset fixture"]);
        let source_commit = git(&["rev-parse", "HEAD"]);
        assert_eq!(manifest.targets.len(), 10);

        let mut roots = BTreeMap::new();
        let mut targets = Vec::new();
        let mut candidates = BTreeMap::new();
        for target in &manifest.targets {
            let root = scratch.path().join("candidates").join(&target.id);
            SourceArgs {
                command: SourceCommand::Sync(SourceProjectArgs {
                    repo_root: repo.clone(),
                    target: target.id.clone(),
                    preset: PRESET.to_owned(),
                    manifest: None,
                    primary_src_root: None,
                    gradle_project_root: None,
                    output_root: root.clone(),
                    overlay: Vec::new(),
                    gradle_overlay: Vec::new(),
                }),
            }
            .invoke_in(&CancellationToken::new(), &repo)
            .unwrap();
            let properties = fs::read_to_string(root.join("gradle.properties")).unwrap();
            assert_eq!(
                properties
                    .matches(&format!("mod_version={VERSION}"))
                    .count(),
                1
            );
            let loader_version = properties
                .lines()
                .find_map(|line| line.strip_prefix("neo_version="))
                .unwrap()
                .to_owned();
            let old_properties =
                fs::read_to_string(repo.join(&target.project_dir).join("gradle.properties"))
                    .unwrap();
            assert!(old_properties.contains("mod_version=4.34.0"));
            let provenance_bytes = fs::read(root.join(MANIFEST_FILE)).unwrap();
            let provenance =
                ProjectionProvenance::from_json(std::str::from_utf8(&provenance_bytes).unwrap())
                    .unwrap();
            let version_file = &provenance.files["gradle.properties"];
            assert_ne!(version_file.source_sha256, version_file.output_sha256);
            let jar_name = format!("SFM-MC{}-{VERSION}.jar", target.minecraft_version);
            let jar_relative = format!("build/libs/{jar_name}");
            let jar_bytes = format!("synthetic fixture JAR, not Gradle-built: {}\n", target.id);
            fs::create_dir_all(root.join("build/libs")).unwrap();
            fs::write(root.join(&jar_relative), &jar_bytes).unwrap();
            let production_task = match target.id.as_str() {
                "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => "reobfJar",
                "26.1.2" => "jarJar",
                _ => "jar",
            };
            let jdk_build_id = format!("JBRSDK-{}.0.1", target.java_major);
            let locked = CandidateTargetLock {
                target_id: target.id.clone(),
                minecraft_version: target.minecraft_version.clone(),
                loader: target.loader.clone(),
                loader_version,
                gradle_profile: "default".to_owned(),
                production_task: production_task.to_owned(),
                jdk_major: target.java_major,
                jdk_build_id: jdk_build_id.clone(),
                provenance_manifest_sha256: sha256(&provenance_bytes),
                production_jar_relative_path: jar_relative.clone(),
                production_jar_sha256: sha256(jar_bytes.as_bytes()),
            };
            candidates.insert(
                target.id.clone(),
                PromotionCandidate {
                    project_root: root.clone(),
                    reviewed_manifest_sha256: locked.provenance_manifest_sha256.clone(),
                    production_jar_relative_path: jar_relative,
                    production_jar_sha256: locked.production_jar_sha256.clone(),
                    production_task: production_task.to_owned(),
                    jdk_major: target.java_major,
                    jdk_build_id,
                },
            );
            roots.insert(target.id.clone(), root);
            targets.push(locked);
        }
        let lock = SourceCandidateLock {
            schema: "sfm:source_candidate_lock@1".to_owned(),
            source_commit: source_commit.clone(),
            source_manifest_sha256: sha256(manifest_bytes.as_bytes()),
            mod_version: VERSION.to_owned(),
            candidate_preset_id: PRESET.to_owned(),
            candidate_definition_identity: future.identity.clone(),
            compatibility_evidence_relative_path: EVIDENCE.to_owned(),
            compatibility_evidence_sha256: sha256(evidence),
            targets,
        };
        let lock_path = scratch.path().join("fictional-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(&lock).unwrap();
        fs::write(&lock_path, &lock_bytes).unwrap();
        let lock_sha256 = sha256(lock_bytes.as_bytes());
        let candidate_roots = roots
            .iter()
            .map(|(target, root)| format!("{target}={}", root.display()))
            .collect::<Vec<_>>();
        let mut incomplete_roots = candidate_roots.clone();
        incomplete_roots.pop();
        assert!(
            SourceArgs {
                command: SourceCommand::CandidateVerify(CandidateVerifyArgs {
                    repo_root: repo.clone(),
                    lock: lock_path.clone(),
                    candidate_root: incomplete_roots,
                }),
            }
            .invoke_in(&CancellationToken::new(), &repo)
            .unwrap_err()
            .to_string()
            .contains("local candidate roots do not match complete locked target matrix")
        );
        let request = PromotionRequest {
            repository_root: repo.clone(),
            reviewed_head_commit: source_commit,
            reviewed_source_manifest_sha256: lock.source_manifest_sha256.clone(),
            compatibility_evidence_relative_path: EVIDENCE.to_owned(),
            reviewed_compatibility_evidence_sha256: lock.compatibility_evidence_sha256.clone(),
            candidates,
            candidate_preset_id: PRESET.to_owned(),
            candidate_definition_identity: future.identity,
            transition: PromotionTransition::NewImmutablePreset,
            accept_identical_edits: false,
        };
        let request_path = scratch.path().join("fictional-promotion-request.json");
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let output = PromotionArgs {
            request: request_path,
            apply: true,
            ack_pre_acceptance_baseline_repair: false,
            ack_new_immutable_preset: true,
            candidate_lock: Some(lock_path),
            candidate_lock_sha256: Some(lock_sha256),
        }
        .invoke_in(&CancellationToken::new(), scratch.path())
        .unwrap()
        .render(Some(OutputFormat::Json), false)
        .unwrap()
        .unwrap();
        assert!(output.contains("\"mode\": \"apply\""));
        assert!(output.contains(&format!("\"candidate_preset_id\": \"{PRESET}\"")));
        let stages = fs::read_dir(repo.join("platform/minecraft"))
            .unwrap()
            .map(|entry| entry.unwrap().path())
            .filter(|path| {
                path.file_name().is_some_and(|name| {
                    name.to_string_lossy()
                        .starts_with(".sfm-source-promotion-stage-")
                })
            })
            .collect::<Vec<_>>();
        assert_eq!(stages.len(), 1);
        assert!(stages[0].join("journal.json").is_file());
        assert!(stages[0].join("complete").is_file());
        for (target, candidate) in &request.candidates {
            let promoted = repo.join("platform/minecraft/mc-version").join(target);
            assert_eq!(
                sha256(&fs::read(promoted.join(MANIFEST_FILE)).unwrap()),
                candidate.reviewed_manifest_sha256,
                "{target}"
            );
            assert!(
                fs::read_to_string(promoted.join("gradle.properties"))
                    .unwrap()
                    .contains(&format!("mod_version={VERSION}")),
                "{target}"
            );
        }
    }

    #[test]
    fn immutable_apply_rechecks_candidate_inputs_before_destination_changes() {
        let (_fixture, request, lock, gradle_overlays) = new_immutable_fixture();
        let scratch = request.repository_root.parent().unwrap();
        let lock_path = scratch.join("reviewed-immutable-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(&lock).unwrap();
        fs::write(&lock_path, &lock_bytes).unwrap();
        let request_path = scratch.join("reviewed-immutable-promotion-request.json");
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let candidate_extra = request.candidates["1.19.2"]
            .project_root
            .join("unowned-after-initial-verification.txt");
        let before_destinations = destination_snapshot(&request);
        let inserted = std::cell::Cell::new(false);
        let insert_unowned = || -> Result<()> {
            fs::write(&candidate_extra, b"unowned candidate input\n")?;
            inserted.set(true);
            Ok(())
        };
        let policy = RepairIdentityPolicy {
            old_definition_identity: PUBLIC_REPAIR_POLICY.old_definition_identity,
            new_definition_identity: PUBLIC_REPAIR_POLICY.new_definition_identity,
            refmap_sha256: PUBLIC_REPAIR_POLICY.refmap_sha256,
            synthetic_gradle_overlays: Some(&gradle_overlays),
            synthetic_before_promotion: Some(&insert_unowned),
        };
        let error = PromotionArgs {
            request: request_path,
            apply: true,
            ack_pre_acceptance_baseline_repair: false,
            ack_new_immutable_preset: true,
            candidate_lock: Some(lock_path),
            candidate_lock_sha256: Some(sha256(lock_bytes.as_bytes())),
        }
        .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
        .unwrap_err();
        let diagnostics = format!("{error:?}");
        assert!(
            inserted.get(),
            "test mutation was not reached: {diagnostics}"
        );
        assert!(
            diagnostics.contains("pre-apply candidate verification failed"),
            "{diagnostics}"
        );
        assert!(
            diagnostics.contains("unowned candidate project input"),
            "{diagnostics}"
        );
        assert_eq!(destination_snapshot(&request), before_destinations);
    }

    #[test]
    fn repair_apply_rechecks_closed_candidate_inputs_before_destination_changes() {
        let (fixture, request, refmap_sha256, gradle_overlays) =
            CandidateFixture::new_repair_for_cli();
        let scratch = request.repository_root.parent().unwrap();
        let lock_path = scratch.join("reviewed-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(fixture.lock()).unwrap();
        fs::write(&lock_path, &lock_bytes).unwrap();
        let request_path = scratch.join("reviewed-promotion-request.json");
        fs::write(&request_path, request_file_json(&request)).unwrap();
        let candidate_extra = request.candidates["1.19.2"]
            .project_root
            .join("unowned-after-initial-verification.txt");
        let before_destinations = destination_snapshot(&request);
        let inserted = std::cell::Cell::new(false);
        let insert_unowned = || -> Result<()> {
            fs::write(&candidate_extra, b"unowned candidate input\n")?;
            inserted.set(true);
            Ok(())
        };
        let policy = RepairIdentityPolicy {
            old_definition_identity: PUBLIC_REPAIR_POLICY.old_definition_identity,
            new_definition_identity: &request.candidate_definition_identity,
            refmap_sha256: &refmap_sha256,
            synthetic_gradle_overlays: Some(&gradle_overlays),
            synthetic_before_promotion: Some(&insert_unowned),
        };
        let args = PromotionArgs {
            request: request_path,
            apply: true,
            ack_pre_acceptance_baseline_repair: true,
            ack_new_immutable_preset: false,
            candidate_lock: Some(lock_path),
            candidate_lock_sha256: Some(sha256(lock_bytes.as_bytes())),
        };
        let error = args
            .invoke_in_with_policy(&CancellationToken::new(), scratch, &policy)
            .unwrap_err();
        let diagnostics = format!("{error:?}");
        assert!(
            inserted.get(),
            "test mutation was not reached: {diagnostics}"
        );
        assert!(
            diagnostics.contains("pre-apply candidate verification failed"),
            "{diagnostics}"
        );
        assert!(
            diagnostics.contains("unowned candidate project input"),
            "{diagnostics}"
        );
        assert_eq!(destination_snapshot(&request), before_destinations);
    }

    #[cfg(windows)]
    #[test]
    fn external_lock_containment_is_case_insensitive_and_component_bounded() {
        assert!(path_is_within(
            Path::new(r"C:\Reviewed\Repo\locks\candidate.json"),
            Path::new(r"c:\reviewed\repo")
        ));
        assert!(!path_is_within(
            Path::new(r"C:\Reviewed\Repository\candidate.json"),
            Path::new(r"c:\reviewed\repo")
        ));
        assert!(path_is_within(
            Path::new(r"C:\locks\candidate.json"),
            Path::new("c:\\")
        ));
        assert!(!path_is_within(
            Path::new(r"D:\locks\candidate.json"),
            Path::new("c:\\")
        ));
    }

    #[test]
    fn review_report_is_machine_readable_without_running_apply() {
        let output = CliOutput::facet(PromotionCliReport::from_core(
            PromotionReport {
                head_commit: "a".repeat(40),
                candidate_preset_id: "released-next".to_owned(),
                ..PromotionReport::default()
            },
            PromotionMode::DryRun,
        ));
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(json.contains(REPORT_SCHEMA));
        assert!(json.contains("\"mode\": \"dry_run\""));
        assert!(json.contains("\"candidate_preset_id\": \"released-next\""));
    }

    #[test]
    fn report_counts_unchanged_files_without_listing_their_paths() {
        let report = PromotionReport {
            targets: BTreeMap::from([(
                "1.19.2".to_owned(),
                TargetPromotionReport {
                    created: vec!["src/main/resources/new.json".to_owned()],
                    unchanged: (0..1_000)
                        .map(|index| format!("unchanged_path_that_must_not_render_{index}.java"))
                        .collect(),
                    ..TargetPromotionReport::default()
                },
            )]),
            ..PromotionReport::default()
        };
        let output = CliOutput::facet(PromotionCliReport::from_core(report, PromotionMode::DryRun));
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(json.contains("\"unchanged\": 1000"));
        assert!(json.contains("src/main/resources/new.json"));
        assert!(!json.contains("unchanged_path_that_must_not_render"));
        assert!(
            json.len() < 2_000,
            "compact report grew to {} bytes",
            json.len()
        );
    }
}
