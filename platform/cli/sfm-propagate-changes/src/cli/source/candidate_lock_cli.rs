//! Local, read-only entry point for a portable source-candidate lock.

use super::source_cli::SourceProjectArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::CandidateVerificationReport;
use crate::source_projection::candidate_lock::SourceCandidateLock;
use crate::source_projection::manifest::SourceProjectionManifest;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

const MAX_LOCK_BYTES: u64 = 1024 * 1024;

#[derive(Debug, Facet)]
pub struct CandidateVerifyArgs {
    /// Git worktree containing the reviewed source-projection definition.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Portable source-candidate lock JSON.
    #[facet(args::named)]
    pub lock: PathBuf,
    /// Local target=absolute-project-root mapping; repeat for all ten targets.
    #[facet(default, args::named)]
    pub candidate_root: Vec<String>,
}

impl CandidateVerifyArgs {
    /// Verify all ten exact candidate JARs and projected outputs without writes.
    ///
    /// # Errors
    ///
    /// Fails on an invalid lock, incomplete local roots or changed artifacts.
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let repo_root = resolve_path(self.repo_root, invocation_dir);
        let lock_path = resolve_path(self.lock, invocation_dir);
        let (lock, lock_sha256) = read_candidate_lock(&lock_path)?;
        let roots = parse_roots(&self.candidate_root)?;
        let report = verify_candidate_in(
            cancellation,
            &repo_root,
            &lock,
            &roots,
            lock_sha256,
            #[cfg(test)]
            None,
        )?;
        Ok(CliOutput::facet(report))
    }
}

/// Read and parse one bounded, immutable byte snapshot. The promotion gate
/// binds this digest to its request and verifies this same parsed snapshot.
pub(super) fn read_candidate_lock(lock_path: &Path) -> Result<(SourceCandidateLock, String)> {
    let file = fs::File::open(lock_path)
        .wrap_err_with(|| format!("cannot open candidate lock '{}'", lock_path.display()))?;
    let metadata = file.metadata()?;
    ensure!(metadata.is_file(), "candidate lock must be a regular file");
    ensure!(
        metadata.len() <= MAX_LOCK_BYTES,
        "candidate lock exceeds the {MAX_LOCK_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_LOCK_BYTES + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_LOCK_BYTES,
        "candidate lock exceeds the {MAX_LOCK_BYTES}-byte limit"
    );
    let lock = SourceCandidateLock::from_json(
        std::str::from_utf8(&bytes).wrap_err("candidate lock is not UTF-8")?,
    )?;
    Ok((lock, sha256(&bytes)))
}

/// Full source verification: the core inventory plus deterministic checks of
/// all ten generated roots. Both CLIs use the same implementation.
pub(super) fn verify_candidate_in(
    cancellation: &CancellationToken,
    repo_root: &Path,
    lock: &SourceCandidateLock,
    roots: &BTreeMap<String, PathBuf>,
    lock_sha256: String,
    #[cfg(test)] test_gradle_overlays: Option<&BTreeMap<String, String>>,
) -> Result<CandidateVerificationReport> {
    cancellation.bail_if_cancelled()?;
    let mut report = lock.verify_in(repo_root, roots, lock_sha256)?;
    let manifest_bytes = fs::read(repo_root.join("platform/minecraft/source-projection.json"))?;
    let manifest = SourceProjectionManifest::from_json(std::str::from_utf8(&manifest_bytes)?)?;
    let preset = manifest.preset(&lock.candidate_preset_id)?;
    for target in &lock.targets {
        cancellation.bail_if_cancelled()?;
        let gradle_overlay = if preset.release_baselines.is_empty() {
            let declared = manifest.target(&target.target_id)?;
            vec![format!("target={}", declared.project_dir)]
        } else {
            Vec::new()
        };
        #[cfg(test)]
        let gradle_overlay = if let Some(overrides) = test_gradle_overlays {
            let relative = overrides.get(&target.target_id).ok_or_else(|| {
                eyre::eyre!(
                    "missing synthetic Gradle overlay for '{}'",
                    target.target_id
                )
            })?;
            vec![format!("synthetic={relative}")]
        } else {
            gradle_overlay
        };
        SourceProjectArgs {
            repo_root: repo_root.to_path_buf(),
            target: target.target_id.clone(),
            preset: lock.candidate_preset_id.clone(),
            manifest: None,
            primary_src_root: None,
            gradle_project_root: None,
            output_root: roots[&target.target_id].clone(),
            overlay: Vec::new(),
            gradle_overlay,
        }
        .check_candidate_in(cancellation, repo_root)?;
    }
    report.schema.clear();
    report
        .schema
        .push_str("sfm:source_candidate_verification@2");
    report.deterministic_source_check = true;
    Ok(report)
}

fn resolve_path(path: PathBuf, invocation_dir: &Path) -> PathBuf {
    if path.is_absolute() {
        path
    } else {
        invocation_dir.join(path)
    }
}

fn parse_roots(values: &[String]) -> Result<BTreeMap<String, PathBuf>> {
    let mut roots = BTreeMap::new();
    for value in values {
        let (target, raw_path) = value
            .split_once('=')
            .ok_or_else(|| eyre::eyre!("candidate root must use target=absolute-project-root"))?;
        ensure!(!target.is_empty(), "candidate root has empty target ID");
        let path = PathBuf::from(raw_path);
        ensure!(
            path.is_absolute(),
            "candidate root for '{target}' must be absolute"
        );
        ensure!(
            roots.insert(target.to_owned(), path).is_none(),
            "duplicate local candidate root for '{target}'"
        );
    }
    Ok(roots)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;

    #[test]
    fn candidate_verify_parses_distinct_portable_lock_and_local_roots() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "candidate-verify",
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
            command: SourceCommand::CandidateVerify(args),
        }) = parsed.command
        else {
            panic!("expected source candidate-verify command");
        };
        assert_eq!(args.lock, PathBuf::from("candidate.json"));
        assert_eq!(args.candidate_root.len(), 1);
    }

    #[test]
    fn local_root_mapping_rejects_duplicates_and_relative_paths() {
        let absolute = std::env::temp_dir().join("candidate");
        let entry = format!("1.19.2={}", absolute.display());
        let _ = parse_roots(&[entry.clone(), entry]).unwrap_err();
        let _ = parse_roots(&["1.19.2=relative/path".to_owned()]).unwrap_err();
        let _ = parse_roots(&["1.19.2".to_owned()]).unwrap_err();
    }
}
