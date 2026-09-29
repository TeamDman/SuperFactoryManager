//! A portable, read-only inventory of a fully verified projection candidate.
//!
//! This command does not infer release tags from version branches, modify the
//! checked-in projection, or call any publication service. Its output is an
//! exact artifact selection input for a later, separately approved workflow.

use super::candidate_lock_cli::CandidateVerifyArgs;
use super::candidate_lock_cli::parse_roots;
use super::candidate_lock_cli::read_candidate_lock;
use super::candidate_lock_cli::resolve_path;
use super::candidate_lock_cli::verify_candidate_in;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::CandidateVerificationReport;
use crate::source_projection::candidate_lock::SourceCandidateLock;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::path::Path;

pub(super) const INVENTORY_SCHEMA: &str = "sfm:source_release_inventory@1";
pub(super) const INVENTORY_SCOPE: &str =
    "verified-local-candidate-only; no promotion, tag or publication";
const VERIFIED_SCHEMA: &str = "sfm:source_candidate_verification@2";

#[derive(Debug, Facet)]
pub struct ReleaseInventoryArgs {
    /// Reviewed lock and all ten local candidate project roots.
    #[facet(flatten)]
    pub candidate: CandidateVerifyArgs,
}

#[derive(Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(super) struct ReleaseInventory {
    pub(super) schema: String,
    /// Verification of a local candidate is not permission to publish it.
    pub(super) scope: String,
    pub(super) lock_sha256: String,
    pub(super) source_commit: String,
    pub(super) source_manifest_sha256: String,
    pub(super) mod_version: String,
    pub(super) candidate_preset_id: String,
    pub(super) candidate_definition_identity: String,
    pub(super) compatibility_evidence_relative_path: String,
    pub(super) compatibility_evidence_sha256: String,
    pub(super) deterministic_source_check: bool,
    /// Gradle profile and JDK build identity come from the reviewed lock, not process attestation.
    pub(super) build_inputs_are_reviewed_assertions: bool,
    pub(super) targets: Vec<ReleaseInventoryTarget>,
}

#[derive(Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(super) struct ReleaseInventoryTarget {
    pub(super) target_id: String,
    pub(super) minecraft_version: String,
    pub(super) loader: String,
    pub(super) loader_version: String,
    pub(super) gradle_profile: String,
    pub(super) production_task: String,
    pub(super) jdk_major: u16,
    pub(super) jdk_build_id: String,
    pub(super) provenance_manifest_sha256: String,
    pub(super) production_jar_relative_path: String,
    pub(super) production_jar_sha256: String,
}

impl ReleaseInventoryArgs {
    /// Verify exact candidate bytes and emit a portable asset inventory without writes.
    ///
    /// # Errors
    ///
    /// Fails on an invalid lock, changed candidate output, incomplete matrix or
    /// nondeterministic projection.
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let repo_root = resolve_path(self.candidate.repo_root, invocation_dir);
        let lock_path = resolve_path(self.candidate.lock, invocation_dir);
        let (lock, lock_sha256) = read_candidate_lock(&lock_path)?;
        let roots = parse_roots(&self.candidate.candidate_root)?;
        let report = verify_candidate_in(
            cancellation,
            &repo_root,
            &lock,
            &roots,
            lock_sha256,
            #[cfg(test)]
            None,
        )?;
        Ok(CliOutput::facet(ReleaseInventory::from_verified(
            &lock, &report,
        )?))
    }
}

impl ReleaseInventory {
    pub(super) fn from_verified(
        lock: &SourceCandidateLock,
        report: &CandidateVerificationReport,
    ) -> Result<Self> {
        ensure!(
            lock.targets.len() == 10,
            "release inventory requires the complete ten-target matrix"
        );
        ensure!(
            report.schema == VERIFIED_SCHEMA && report.deterministic_source_check,
            "release inventory requires complete deterministic candidate verification"
        );
        ensure!(
            report.source_commit == lock.source_commit
                && report.mod_version == lock.mod_version
                && report.candidate_preset_id == lock.candidate_preset_id,
            "candidate verification does not match the reviewed lock"
        );
        let expected = lock
            .targets
            .iter()
            .map(|target| {
                (
                    target.target_id.clone(),
                    target.production_jar_sha256.clone(),
                )
            })
            .collect::<BTreeMap<_, _>>();
        ensure!(
            report.verified_targets == expected,
            "candidate verification does not cover the exact ten locked JARs"
        );
        let mut targets = lock
            .targets
            .iter()
            .map(|target| ReleaseInventoryTarget {
                target_id: target.target_id.clone(),
                minecraft_version: target.minecraft_version.clone(),
                loader: target.loader.clone(),
                loader_version: target.loader_version.clone(),
                gradle_profile: target.gradle_profile.clone(),
                production_task: target.production_task.clone(),
                jdk_major: target.jdk_major,
                jdk_build_id: target.jdk_build_id.clone(),
                provenance_manifest_sha256: target.provenance_manifest_sha256.clone(),
                production_jar_relative_path: target.production_jar_relative_path.clone(),
                production_jar_sha256: target.production_jar_sha256.clone(),
            })
            .collect::<Vec<_>>();
        targets.sort_by(|a, b| a.target_id.cmp(&b.target_id));
        Ok(Self {
            schema: INVENTORY_SCHEMA.to_owned(),
            scope: INVENTORY_SCOPE.to_owned(),
            lock_sha256: report.lock_sha256.clone(),
            source_commit: lock.source_commit.clone(),
            source_manifest_sha256: lock.source_manifest_sha256.clone(),
            mod_version: lock.mod_version.clone(),
            candidate_preset_id: lock.candidate_preset_id.clone(),
            candidate_definition_identity: lock.candidate_definition_identity.clone(),
            compatibility_evidence_relative_path: lock.compatibility_evidence_relative_path.clone(),
            compatibility_evidence_sha256: lock.compatibility_evidence_sha256.clone(),
            deterministic_source_check: true,
            build_inputs_are_reviewed_assertions: report.toolchain_fields_are_reviewed_assertions,
            targets,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::source_projection::candidate_lock::CandidateTargetLock;

    #[test]
    fn release_inventory_parses_explicit_lock_and_local_roots() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "release-inventory",
            "--repo-root",
            "C:/reviewed/repo",
            "--lock",
            "candidate.json",
            "--candidate-root",
            "1.19.2=C:/candidate/1.19.2",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ReleaseInventory(args),
        }) = parsed.command
        else {
            panic!("expected source release-inventory command");
        };
        assert_eq!(args.candidate.candidate_root.len(), 1);
    }

    #[test]
    fn release_inventory_requires_exact_verified_artifacts_and_sorts_targets() {
        let targets = [
            "26.1.2", "1.21.1", "1.21.0", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20", "1.19.4",
            "1.19.2",
        ]
        .into_iter()
        .map(|id| CandidateTargetLock {
            target_id: id.to_owned(),
            minecraft_version: id.to_owned(),
            loader: "forge".to_owned(),
            loader_version: "test".to_owned(),
            gradle_profile: "default".to_owned(),
            production_task: "jar".to_owned(),
            jdk_major: 17,
            jdk_build_id: "test-17.0.1".to_owned(),
            provenance_manifest_sha256: "sha256:manifest".to_owned(),
            production_jar_relative_path: format!("build/libs/sfm-MC{id}-4.34.0.jar"),
            production_jar_sha256: format!("sha256:{id}"),
        })
        .collect::<Vec<_>>();
        let lock = SourceCandidateLock {
            schema: "sfm:source_candidate_lock@1".to_owned(),
            source_commit: "commit".to_owned(),
            source_manifest_sha256: "sha256:manifest".to_owned(),
            mod_version: "4.34.0".to_owned(),
            candidate_preset_id: "released-4.34.0".to_owned(),
            candidate_definition_identity: "blake3:definition".to_owned(),
            compatibility_evidence_relative_path: "docs/matrix.md".to_owned(),
            compatibility_evidence_sha256: "sha256:evidence".to_owned(),
            targets: targets.clone(),
        };
        let mut report = CandidateVerificationReport {
            schema: VERIFIED_SCHEMA.to_owned(),
            lock_sha256: "sha256:lock".to_owned(),
            source_commit: lock.source_commit.clone(),
            mod_version: lock.mod_version.clone(),
            candidate_preset_id: lock.candidate_preset_id.clone(),
            verified_targets: targets
                .iter()
                .map(|target| {
                    (
                        target.target_id.clone(),
                        target.production_jar_sha256.clone(),
                    )
                })
                .collect(),
            deterministic_source_check: true,
            toolchain_fields_are_reviewed_assertions: true,
        };
        let inventory = ReleaseInventory::from_verified(&lock, &report).unwrap();
        assert_eq!(inventory.targets[0].target_id, "1.19.2");
        assert_eq!(inventory.targets[9].target_id, "26.1.2");
        assert_eq!(inventory.targets.len(), 10);
        assert!(inventory.scope.contains("no promotion"));

        report.verified_targets.remove("1.21.1");
        assert!(ReleaseInventory::from_verified(&lock, &report).is_err());
        report.deterministic_source_check = false;
        assert!(ReleaseInventory::from_verified(&lock, &report).is_err());
    }
}
