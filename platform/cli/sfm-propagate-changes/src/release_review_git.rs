//! Read-only, commit-pinned Git domain production for release review.
//!
//! This module intentionally has no CLI or application-runtime coupling.  It
//! shells out to Git without a command shell, resolves both requested
//! revisions before doing any comparison, and only reads objects.  Callers can
//! therefore build a review corpus for arbitrary commits without checking them
//! out or changing the repository's refs/index/worktree.

use std::error::Error;
use std::ffi::OsStr;
use std::fmt::Display;
use std::fmt::Formatter;
use std::fmt::{self};
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::Output;

pub const GIT_DOMAIN_SCHEMA: &str = "sfm.release-review.git-domain/1";
pub const AMBIENT_REPOSITORY_RELATIONSHIP_SCHEMA: &str =
    "sfm.release-review.ambient-repository-relationship/1";

const RENAME_OPTION: &str = "--find-renames=50%";
const COPY_OPTION: &str = "--find-copies=50%";

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitReviewDomain {
    pub schema: &'static str,
    pub repository_root: PathBuf,
    pub before: ResolvedGitRevision,
    pub candidate: ResolvedGitRevision,
    pub changes: Vec<GitDomainChange>,
    pub units: Vec<GitReviewUnit>,
    pub reconciliation: GitReconciliationReport,
}

impl GitReviewDomain {
    /// Keep complete change records and rebuild all dependent reconciliation evidence.
    pub(crate) fn retain_changes(&mut self, keep: impl FnMut(&GitDomainChange) -> bool) {
        self.changes.retain(keep);
        let retained: std::collections::BTreeSet<_> = self
            .changes
            .iter()
            .map(|change| change.change_id.as_str())
            .collect();
        self.units
            .retain(|unit| retained.contains(unit.change_id.as_str()));
        self.reconciliation = reconcile(&self.changes, &self.units);
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResolvedGitRevision {
    pub requested: String,
    pub commit_id: String,
    pub tree_id: String,
}

#[derive(Clone, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct GitPath {
    bytes: Vec<u8>,
}

impl GitPath {
    #[must_use]
    pub fn from_bytes(bytes: Vec<u8>) -> Self {
        Self { bytes }
    }

    #[must_use]
    pub fn as_bytes(&self) -> &[u8] {
        &self.bytes
    }

    #[must_use]
    pub fn as_utf8(&self) -> Option<&str> {
        std::str::from_utf8(&self.bytes).ok()
    }

    #[must_use]
    pub fn display_lossy(&self) -> String {
        String::from_utf8_lossy(&self.bytes).into_owned()
    }
}

impl Display for GitPath {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.write_str(&self.display_lossy())
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitDomainChange {
    pub change_id: String,
    pub status: GitChangeStatus,
    pub old_path: Option<GitPath>,
    pub new_path: Option<GitPath>,
    pub old_mode: Option<String>,
    pub new_mode: Option<String>,
    pub old_blob_id: Option<String>,
    pub new_blob_id: Option<String>,
    pub numstat: Option<GitNumstat>,
    pub hunks: Vec<GitHunkRange>,
    pub unit_ids: Vec<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitChangeStatus {
    pub kind: GitChangeKind,
    pub raw: String,
    pub similarity_percent: Option<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum GitChangeKind {
    Added,
    Deleted,
    Modified,
    Renamed,
    Copied,
    TypeChanged,
    Unsupported,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitNumstat {
    pub additions: Option<u64>,
    pub deletions: Option<u64>,
    pub binary: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitHunkRange {
    pub before_start: u64,
    pub before_lines: u64,
    pub candidate_start: u64,
    pub candidate_lines: u64,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitReviewUnit {
    pub unit_id: String,
    pub change_id: String,
    pub old_path: Option<GitPath>,
    pub new_path: Option<GitPath>,
    pub kind: GitReviewUnitKind,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum GitReviewUnitKind {
    TextHunk(GitHunkRange),
    FileChange,
    BinaryFile,
    ExplicitlyUnsupported { reason: String },
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitReconciliationReport {
    pub raw_change_count: usize,
    /// Counts old and candidate paths separately.  An in-place modification
    /// therefore contributes two side-qualified paths, while an add/delete
    /// contributes one and makes its absent side explicit on the change.
    pub raw_side_path_count: usize,
    pub represented_side_path_count: usize,
    pub explicitly_unsupported_side_path_count: usize,
    pub unreconciled_side_path_count: usize,
    pub paths: Vec<GitReconciledPath>,
    pub complete: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GitReconciledPath {
    pub change_id: String,
    pub side: GitRevisionSide,
    pub path: GitPath,
    pub disposition: GitReconciliationDisposition,
    pub unit_ids: Vec<String>,
    pub reason: Option<String>,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum GitRevisionSide {
    Before,
    Candidate,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum GitReconciliationDisposition {
    Represented,
    ExplicitlyUnsupported,
}

/// The relationship between a review document's immutable candidate commit
/// and the repository commit currently named by `HEAD`.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AmbientRepositoryRelationshipKind {
    /// Ambient `HEAD` is exactly the review document's candidate commit.
    Exact,
    /// Ambient `HEAD` descends from the candidate and every path touched by
    /// every intervening commit is an explicitly permitted review-evidence
    /// path.
    ReviewEvidenceOnly,
    /// The candidate cannot be proven to be the ambient ancestor, or at least
    /// one intervening commit touches a path outside the evidence allow-list.
    SourceAffectingDivergence,
}

/// A deterministic path witness emitted by ambient-repository classification.
///
/// Git paths are byte strings.  The hexadecimal form is authoritative even
/// when a path cannot be represented as UTF-8; the display form exists for
/// human diagnostics only.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct AmbientGitPathWitness {
    pub utf8: Option<String>,
    pub display: String,
    pub bytes_hex: String,
}

/// One stable diagnostic explaining the ambient relationship decision.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct AmbientRepositoryDiagnostic {
    pub code: String,
    pub message: String,
}

/// Read-only evidence for classifying an ambient checkout against one
/// repository binding in a portable release-review document.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct AmbientRepositoryRelationship {
    pub schema: &'static str,
    pub repository_root: String,
    pub candidate_commit: String,
    pub ambient_head: Option<String>,
    pub classification: AmbientRepositoryRelationshipKind,
    pub candidate_is_ancestor: Option<bool>,
    pub intervening_commits: Vec<String>,
    pub permitted_review_evidence_paths: Vec<String>,
    pub changed_paths: Vec<AmbientGitPathWitness>,
    pub source_affecting_paths: Vec<AmbientGitPathWitness>,
    pub diagnostics: Vec<AmbientRepositoryDiagnostic>,
}

/// Separate from commit ancestry: equal HEAD does not imply a clean worktree.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct WorkingTreeEvidence {
    pub schema: &'static str,
    pub checked_at_unix_millis: u128,
    pub head: Option<String>,
    /// None means the probe failed or exceeded its publication budget.
    pub source_dirty: Option<bool>,
    pub changes: Vec<WorkingTreeChange>,
    pub diagnostics: Vec<AmbientRepositoryDiagnostic>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct WorkingTreeChange {
    pub index_status: char,
    pub worktree_status: char,
    pub path: AmbientGitPathWitness,
    pub previous_path: Option<AmbientGitPathWitness>,
    pub review_evidence_only: bool,
}

/// Read-only status, preserving byte-addressed paths and rename endpoints.
/// A probe is evidence at a time, not a promise that the filesystem stays frozen.
#[must_use]
pub fn inspect_working_tree(
    repository_root: &Path,
    review_evidence_paths: &[String],
) -> WorkingTreeEvidence {
    let checked_at_unix_millis = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |duration| duration.as_millis());
    let failed = |message: String| WorkingTreeEvidence {
        schema: "sfm.release-review.working-tree-evidence/1",
        checked_at_unix_millis,
        head: None,
        source_dirty: None,
        changes: Vec::new(),
        diagnostics: vec![AmbientRepositoryDiagnostic {
            code: "working-tree.unavailable".to_owned(),
            message,
        }],
    };
    let inspect = || -> Result<WorkingTreeEvidence, GitDomainError> {
        let before = resolve_revision(repository_root, "HEAD")?;
        let output = run_git(
            repository_root,
            [
                "status",
                "--porcelain=v1",
                "-z",
                "--untracked-files=all",
                "--ignore-submodules=none",
                "--renames",
            ],
            None,
            "inspecting staged, unstaged and untracked paths",
        )?;
        let changes = parse_working_tree_status(&output.stdout, review_evidence_paths)?;
        let after = resolve_revision(repository_root, "HEAD")?;
        if before.commit_id != after.commit_id {
            return Err(GitDomainError::Invariant {
                detail: "HEAD changed during the working-tree probe; retry".to_owned(),
            });
        }
        Ok(WorkingTreeEvidence {
            schema: "sfm.release-review.working-tree-evidence/1",
            checked_at_unix_millis,
            head: Some(after.commit_id),
            source_dirty: Some(changes.iter().any(|change| !change.review_evidence_only)),
            changes,
            diagnostics: Vec::new(),
        })
    };
    inspect().unwrap_or_else(|error| failed(error.to_string()))
}

/// Porcelain v1 -z has an XY prefix, an unquoted destination, then the old
/// path for rename/copy entries. Whitespace and newlines belong to the path.
/// # Errors
/// Rejects malformed or truncated porcelain status records instead of reporting a clean tree.
pub fn parse_working_tree_status(
    bytes: &[u8],
    review_evidence_paths: &[String],
) -> Result<Vec<WorkingTreeChange>, GitDomainError> {
    let mut cursor = NulCursor::new(bytes);
    let mut changes = Vec::new();
    let permitted = |path: &[u8]| {
        review_evidence_paths
            .iter()
            .any(|value| value.as_bytes() == path)
    };
    while let Some(record) = cursor.next("working-tree status")? {
        if record.len() < 4
            || record[2] != b' '
            || !b" MTADRCU?!".contains(&record[0])
            || !b" MTADRCU?!".contains(&record[1])
        {
            return Err(GitDomainError::Parse {
                stream: "working-tree status",
                detail: "invalid XY/path record".to_owned(),
            });
        }
        if changes.len() >= 100_000 {
            return Err(GitDomainError::Invariant {
                detail: "working-tree status exceeds 100000 paths".to_owned(),
            });
        }
        let path = &record[3..];
        let previous = if record[..2].iter().any(|value| matches!(value, b'R' | b'C')) {
            Some(
                cursor
                    .next("working-tree rename source")?
                    .filter(|path| !path.is_empty())
                    .ok_or_else(|| GitDomainError::Parse {
                        stream: "working-tree status",
                        detail: "rename source is missing".to_owned(),
                    })?,
            )
        } else {
            None
        };
        changes.push(WorkingTreeChange {
            index_status: char::from(record[0]),
            worktree_status: char::from(record[1]),
            path: path_witness(&GitPath::from_bytes(path.to_vec())),
            previous_path: previous.map(|path| path_witness(&GitPath::from_bytes(path.to_vec()))),
            review_evidence_only: permitted(path) && previous.is_none_or(permitted),
        });
    }
    changes.sort_by(|left, right| left.path.bytes_hex.cmp(&right.path.bytes_hex));
    Ok(changes)
}

#[derive(Debug)]
pub enum GitDomainError {
    Io {
        operation: String,
        source: std::io::Error,
    },
    Git {
        operation: String,
        status_code: Option<i32>,
        stderr: String,
    },
    Parse {
        stream: &'static str,
        detail: String,
    },
    Invariant {
        detail: String,
    },
}

impl Display for GitDomainError {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io { operation, source } => {
                write!(f, "{operation} failed: {source}")
            }
            Self::Git {
                operation,
                status_code,
                stderr,
            } => write!(
                f,
                "{operation} failed with status {status_code:?}: {stderr}"
            ),
            Self::Parse { stream, detail } => {
                write!(f, "could not parse Git {stream}: {detail}")
            }
            Self::Invariant { detail } => f.write_str(detail),
        }
    }
}

impl Error for GitDomainError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        match self {
            Self::Io { source, .. } => Some(source),
            Self::Git { .. } | Self::Parse { .. } | Self::Invariant { .. } => None,
        }
    }
}

/// Classify ambient `HEAD` against a release-review candidate using committed
/// Git history rather than worktree cleanliness or an endpoint-only diff.
///
/// Every commit reachable from ambient `HEAD` but not from the candidate is
/// inspected.  Consequently, changing a source path and reverting it in a
/// later commit still produces `SourceAffectingDivergence` rather than being
/// hidden by an empty endpoint diff.  Git failures are converted into an
/// explicit fail-closed report so callers can render deterministic status
/// evidence instead of losing the classification behind an opaque error.
#[must_use]
#[expect(
    clippy::too_many_lines,
    reason = "the fail-closed classification branches stay together so every Git failure maps visibly to one report"
)]
pub fn classify_ambient_repository_relationship(
    repository_root: &Path,
    candidate_commit: &str,
    review_evidence_paths: &[String],
) -> AmbientRepositoryRelationship {
    let repository_root_text = repository_root.to_string_lossy().into_owned();
    let mut permitted_review_evidence_paths = review_evidence_paths.to_vec();
    permitted_review_evidence_paths.sort();
    permitted_review_evidence_paths.dedup();

    let failed =
        |ambient_head: Option<String>, code: &str, message: String| AmbientRepositoryRelationship {
            schema: AMBIENT_REPOSITORY_RELATIONSHIP_SCHEMA,
            repository_root: repository_root_text.clone(),
            candidate_commit: candidate_commit.to_owned(),
            ambient_head,
            classification: AmbientRepositoryRelationshipKind::SourceAffectingDivergence,
            candidate_is_ancestor: None,
            intervening_commits: Vec::new(),
            permitted_review_evidence_paths: permitted_review_evidence_paths.clone(),
            changed_paths: Vec::new(),
            source_affecting_paths: Vec::new(),
            diagnostics: vec![AmbientRepositoryDiagnostic {
                code: code.to_owned(),
                message,
            }],
        };

    if let Err(error) = run_git(
        repository_root,
        ["rev-parse", "--show-toplevel"],
        None,
        "locating ambient Git repository",
    ) {
        return failed(
            None,
            "ambient.repository-unavailable",
            format!("could not locate ambient Git repository: {error}"),
        );
    }

    let candidate = match resolve_revision(repository_root, candidate_commit) {
        Ok(candidate) => candidate,
        Err(error) => {
            return failed(
                None,
                "ambient.candidate-unavailable",
                format!("candidate commit is unavailable in the ambient repository: {error}"),
            );
        }
    };
    let ambient = match resolve_revision(repository_root, "HEAD") {
        Ok(ambient) => ambient,
        Err(error) => {
            return failed(
                None,
                "ambient.head-unavailable",
                format!("ambient HEAD is unavailable: {error}"),
            );
        }
    };

    if ambient.commit_id == candidate.commit_id {
        return AmbientRepositoryRelationship {
            schema: AMBIENT_REPOSITORY_RELATIONSHIP_SCHEMA,
            repository_root: repository_root_text,
            candidate_commit: candidate.commit_id,
            ambient_head: Some(ambient.commit_id),
            classification: AmbientRepositoryRelationshipKind::Exact,
            candidate_is_ancestor: Some(true),
            intervening_commits: Vec::new(),
            permitted_review_evidence_paths,
            changed_paths: Vec::new(),
            source_affecting_paths: Vec::new(),
            diagnostics: vec![AmbientRepositoryDiagnostic {
                code: "ambient.head-exact".to_owned(),
                message: "ambient HEAD equals the pinned candidate commit".to_owned(),
            }],
        };
    }

    let ancestor = match run_git_allow_status(
        repository_root,
        [
            "merge-base",
            "--is-ancestor",
            candidate.commit_id.as_str(),
            ambient.commit_id.as_str(),
        ],
        None,
        "checking whether candidate is an ancestor of ambient HEAD",
    ) {
        Ok(output) if output.status.success() => true,
        Ok(output) if output.status.code() == Some(1) => false,
        Ok(output) => {
            return failed(
                Some(ambient.commit_id),
                "ambient.ancestor-check-failed",
                format!(
                    "Git ancestor check failed with status {:?}: {}",
                    output.status.code(),
                    String::from_utf8_lossy(&output.stderr).trim()
                ),
            );
        }
        Err(error) => {
            return failed(
                Some(ambient.commit_id),
                "ambient.ancestor-check-failed",
                error.to_string(),
            );
        }
    };
    if !ancestor {
        return AmbientRepositoryRelationship {
            schema: AMBIENT_REPOSITORY_RELATIONSHIP_SCHEMA,
            repository_root: repository_root_text,
            candidate_commit: candidate.commit_id,
            ambient_head: Some(ambient.commit_id),
            classification: AmbientRepositoryRelationshipKind::SourceAffectingDivergence,
            candidate_is_ancestor: Some(false),
            intervening_commits: Vec::new(),
            permitted_review_evidence_paths,
            changed_paths: Vec::new(),
            source_affecting_paths: Vec::new(),
            diagnostics: vec![AmbientRepositoryDiagnostic {
                code: "ambient.non-descendant".to_owned(),
                message: "ambient HEAD does not descend from the pinned candidate commit"
                    .to_owned(),
            }],
        };
    }

    let intervening_commits =
        match intervening_commits(repository_root, &candidate.commit_id, &ambient.commit_id) {
            Ok(commits) => commits,
            Err(error) => {
                return failed(
                    Some(ambient.commit_id),
                    "ambient.history-unavailable",
                    format!("could not enumerate intervening commits: {error}"),
                );
            }
        };
    let changed_git_paths = match paths_touched_by_commits(repository_root, &intervening_commits) {
        Ok(paths) => paths,
        Err(error) => {
            return failed(
                Some(ambient.commit_id),
                "ambient.changed-paths-unavailable",
                format!("could not enumerate intervening changed paths: {error}"),
            );
        }
    };
    let permitted_bytes = permitted_review_evidence_paths
        .iter()
        .map(String::as_bytes)
        .collect::<std::collections::BTreeSet<_>>();
    let source_affecting_git_paths = changed_git_paths
        .iter()
        .filter(|path| !permitted_bytes.contains(path.as_bytes()))
        .cloned()
        .collect::<Vec<_>>();
    let changed_paths = changed_git_paths
        .iter()
        .map(path_witness)
        .collect::<Vec<_>>();
    let source_affecting_paths = source_affecting_git_paths
        .iter()
        .map(path_witness)
        .collect::<Vec<_>>();
    let classification = if source_affecting_paths.is_empty() {
        AmbientRepositoryRelationshipKind::ReviewEvidenceOnly
    } else {
        AmbientRepositoryRelationshipKind::SourceAffectingDivergence
    };
    let diagnostics = if source_affecting_paths.is_empty() {
        vec![AmbientRepositoryDiagnostic {
            code: "ambient.review-evidence-only".to_owned(),
            message: format!(
                "{} intervening commit(s) touched only permitted review-evidence paths",
                intervening_commits.len()
            ),
        }]
    } else {
        vec![AmbientRepositoryDiagnostic {
            code: "ambient.source-affecting-paths".to_owned(),
            message: format!(
                "{} intervening path(s) are outside review_evidence_paths",
                source_affecting_paths.len()
            ),
        }]
    };

    AmbientRepositoryRelationship {
        schema: AMBIENT_REPOSITORY_RELATIONSHIP_SCHEMA,
        repository_root: repository_root_text,
        candidate_commit: candidate.commit_id,
        ambient_head: Some(ambient.commit_id),
        classification,
        candidate_is_ancestor: Some(true),
        intervening_commits,
        permitted_review_evidence_paths,
        changed_paths,
        source_affecting_paths,
        diagnostics,
    }
}

fn intervening_commits(
    repository_root: &Path,
    candidate_commit: &str,
    ambient_head: &str,
) -> Result<Vec<String>, GitDomainError> {
    let range = format!("{candidate_commit}..{ambient_head}");
    let output = run_git(
        repository_root,
        ["rev-list", "--reverse", "--topo-order", range.as_str()],
        None,
        "enumerating intervening commits",
    )?;
    let text = std::str::from_utf8(&output.stdout).map_err(|error| GitDomainError::Parse {
        stream: "rev-list",
        detail: format!("commit list was not UTF-8/ASCII: {error}"),
    })?;
    text.lines()
        .filter(|line| !line.is_empty())
        .map(|line| parse_object_id(line.as_bytes(), "rev-list"))
        .collect()
}

fn paths_touched_by_commits(
    repository_root: &Path,
    commits: &[String],
) -> Result<Vec<GitPath>, GitDomainError> {
    let mut paths = std::collections::BTreeSet::new();
    for commit in commits {
        let output = run_git(
            repository_root,
            [
                "diff-tree",
                "--root",
                "-m",
                "--no-commit-id",
                "--name-only",
                "-z",
                "-r",
                "--no-renames",
                commit.as_str(),
                "--",
            ],
            None,
            &format!("enumerating changed paths for commit {commit}"),
        )?;
        for bytes in output.stdout.split(|byte| *byte == 0) {
            if !bytes.is_empty() {
                paths.insert(GitPath::from_bytes(bytes.to_vec()));
            }
        }
    }
    Ok(paths.into_iter().collect())
}

fn path_witness(path: &GitPath) -> AmbientGitPathWitness {
    AmbientGitPathWitness {
        utf8: path.as_utf8().map(str::to_owned),
        display: path.display_lossy(),
        bytes_hex: hex_bytes(path.as_bytes()),
    }
}

fn hex_bytes(bytes: &[u8]) -> String {
    use std::fmt::Write as _;

    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        write!(output, "{byte:02x}").expect("writing to String cannot fail");
    }
    output
}

/// Produce a deterministic, read-only review domain for two explicit Git
/// commit-ish values.
///
/// The comparison enables rename and copy detection at a fixed 50% threshold,
/// with no rename limit, and pins attribute lookup to the candidate commit.
/// Git configuration cannot substitute an external diff or text converter.
///
/// # Errors
///
/// Returns a structured error when Git cannot resolve either revision, emit
/// the required read-only diff streams, or when those streams cannot be
/// reconciled into a complete review domain.
pub fn produce_git_review_domain(
    repository_root: &Path,
    before: &str,
    candidate: &str,
) -> Result<GitReviewDomain, GitDomainError> {
    let repository_root = repository_root.to_path_buf();
    let before = resolve_revision(&repository_root, before)?;
    let candidate = resolve_revision(&repository_root, candidate)?;

    let raw_output = run_diff_tree(
        &repository_root,
        &before.commit_id,
        &candidate.commit_id,
        &candidate.commit_id,
        &["--raw", "-z", "--full-index", "--no-abbrev"],
        "enumerating raw Git changes",
    )?;
    let mut raw_changes = parse_raw_changes(&raw_output.stdout)?;

    let numstat_output = run_diff_tree(
        &repository_root,
        &before.commit_id,
        &candidate.commit_id,
        &candidate.commit_id,
        &["--numstat", "-z"],
        "classifying Git changes with numstat",
    )?;
    let numstats = parse_numstats(&numstat_output.stdout)?;
    attach_numstats(&mut raw_changes, numstats)?;

    let patch_output = run_diff_tree(
        &repository_root,
        &before.commit_id,
        &candidate.commit_id,
        &candidate.commit_id,
        &[
            "--patch",
            "--unified=0",
            "--full-index",
            "--no-color",
            "--no-ext-diff",
            "--no-textconv",
        ],
        "collecting zero-context Git hunks",
    )?;
    attach_patch_hunks(&mut raw_changes, &patch_output.stdout)?;

    let mut changes = build_changes(&before.tree_id, &candidate.tree_id, raw_changes);
    changes.sort_by(compare_changes);
    let units = build_units(&mut changes);
    let reconciliation = reconcile(&changes, &units);
    if !reconciliation.complete {
        return Err(GitDomainError::Invariant {
            detail: format!(
                "Git review reconciliation left {} side-qualified paths without a disposition",
                reconciliation.unreconciled_side_path_count
            ),
        });
    }

    Ok(GitReviewDomain {
        schema: GIT_DOMAIN_SCHEMA,
        repository_root,
        before,
        candidate,
        changes,
        units,
        reconciliation,
    })
}

fn resolve_revision(
    repository_root: &Path,
    requested: &str,
) -> Result<ResolvedGitRevision, GitDomainError> {
    if requested.is_empty() {
        return Err(GitDomainError::Parse {
            stream: "revision",
            detail: "commit-ish must not be empty".to_owned(),
        });
    }
    let commit_expression = format!("{requested}^{{commit}}");
    let commit_output = run_git(
        repository_root,
        [
            "rev-parse",
            "--verify",
            "--end-of-options",
            &commit_expression,
        ],
        None,
        &format!("resolving commit-ish '{requested}'"),
    )?;
    let commit_id = parse_object_id(&commit_output.stdout, "revision")?;
    let tree_expression = format!("{commit_id}^{{tree}}");
    let tree_output = run_git(
        repository_root,
        [
            "rev-parse",
            "--verify",
            "--end-of-options",
            &tree_expression,
        ],
        None,
        &format!("resolving tree for '{requested}'"),
    )?;
    let tree_id = parse_object_id(&tree_output.stdout, "revision tree")?;
    Ok(ResolvedGitRevision {
        requested: requested.to_owned(),
        commit_id,
        tree_id,
    })
}

fn parse_object_id(bytes: &[u8], stream: &'static str) -> Result<String, GitDomainError> {
    let value = std::str::from_utf8(bytes)
        .map_err(|error| GitDomainError::Parse {
            stream,
            detail: format!("object ID was not UTF-8: {error}"),
        })?
        .trim();
    if value.len() < 40 || !value.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err(GitDomainError::Parse {
            stream,
            detail: format!("expected a full hexadecimal object ID, got '{value}'"),
        });
    }
    Ok(value.to_ascii_lowercase())
}

fn run_diff_tree(
    repository_root: &Path,
    before_commit: &str,
    candidate_commit: &str,
    attribute_source: &str,
    output_options: &[&str],
    operation: &str,
) -> Result<Output, GitDomainError> {
    let mut arguments = vec![
        "diff-tree",
        "-r",
        "--no-commit-id",
        RENAME_OPTION,
        COPY_OPTION,
        "--find-copies-harder",
        "-l0",
    ];
    arguments.extend_from_slice(output_options);
    arguments.push(before_commit);
    arguments.push(candidate_commit);
    arguments.push("--");
    run_git(
        repository_root,
        arguments,
        Some(attribute_source),
        operation,
    )
}

fn run_git<I, S>(
    repository_root: &Path,
    arguments: I,
    attribute_source: Option<&str>,
    operation: &str,
) -> Result<Output, GitDomainError>
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let output = run_git_allow_status(repository_root, arguments, attribute_source, operation)?;
    if output.status.success() {
        Ok(output)
    } else {
        Err(GitDomainError::Git {
            operation: operation.to_owned(),
            status_code: output.status.code(),
            stderr: String::from_utf8_lossy(&output.stderr).trim().to_owned(),
        })
    }
}

fn run_git_allow_status<I, S>(
    repository_root: &Path,
    arguments: I,
    attribute_source: Option<&str>,
    operation: &str,
) -> Result<Output, GitDomainError>
where
    I: IntoIterator<Item = S>,
    S: AsRef<OsStr>,
{
    let mut command = Command::new("git");
    command
        .arg("-C")
        .arg(repository_root)
        .arg("-c")
        .arg("diff.algorithm=myers")
        .arg("-c")
        .arg("core.quotePath=false")
        .args(arguments)
        .env("GIT_OPTIONAL_LOCKS", "0")
        .env("LC_ALL", "C")
        .env("LANG", "C");
    if let Some(attribute_source) = attribute_source {
        command.env("GIT_ATTR_SOURCE", attribute_source);
    }
    let output = command.output().map_err(|source| GitDomainError::Io {
        operation: operation.to_owned(),
        source,
    })?;
    Ok(output)
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct RawChange {
    status: GitChangeStatus,
    old_path: Option<GitPath>,
    new_path: Option<GitPath>,
    old_mode: Option<String>,
    new_mode: Option<String>,
    old_blob_id: Option<String>,
    new_blob_id: Option<String>,
    numstat: Option<GitNumstat>,
    hunks: Vec<GitHunkRange>,
}

fn parse_raw_changes(bytes: &[u8]) -> Result<Vec<RawChange>, GitDomainError> {
    let mut cursor = NulCursor::new(bytes);
    let mut changes = Vec::new();
    while let Some(header) = cursor.next("raw diff header")? {
        if header.is_empty() {
            return Err(GitDomainError::Parse {
                stream: "raw diff",
                detail: "encountered an empty raw header".to_owned(),
            });
        }
        let header = std::str::from_utf8(header).map_err(|error| GitDomainError::Parse {
            stream: "raw diff",
            detail: format!("raw header was not ASCII/UTF-8: {error}"),
        })?;
        let fields = header.split_ascii_whitespace().collect::<Vec<_>>();
        if fields.len() != 5 || !fields[0].starts_with(':') {
            return Err(GitDomainError::Parse {
                stream: "raw diff",
                detail: format!("expected five raw-header fields, got '{header}'"),
            });
        }
        let status = parse_status(fields[4])?;
        let first_path = GitPath::from_bytes(
            cursor
                .next("raw diff path")?
                .ok_or_else(|| GitDomainError::Parse {
                    stream: "raw diff",
                    detail: format!("status '{}' had no path", status.raw),
                })?
                .to_vec(),
        );
        let (old_path, new_path) = match status.kind {
            GitChangeKind::Added => (None, Some(first_path)),
            GitChangeKind::Deleted => (Some(first_path), None),
            GitChangeKind::Renamed | GitChangeKind::Copied => {
                let second_path = GitPath::from_bytes(
                    cursor
                        .next("raw diff destination path")?
                        .ok_or_else(|| GitDomainError::Parse {
                            stream: "raw diff",
                            detail: format!("status '{}' had no destination path", status.raw),
                        })?
                        .to_vec(),
                );
                (Some(first_path), Some(second_path))
            }
            GitChangeKind::Modified | GitChangeKind::TypeChanged | GitChangeKind::Unsupported => {
                (Some(first_path.clone()), Some(first_path))
            }
        };
        changes.push(RawChange {
            status,
            old_path,
            new_path,
            old_mode: parse_optional_mode(fields[0].trim_start_matches(':'))?,
            new_mode: parse_optional_mode(fields[1])?,
            old_blob_id: parse_optional_raw_object_id(fields[2])?,
            new_blob_id: parse_optional_raw_object_id(fields[3])?,
            numstat: None,
            hunks: Vec::new(),
        });
    }
    Ok(changes)
}

fn parse_status(raw: &str) -> Result<GitChangeStatus, GitDomainError> {
    let mut bytes = raw.bytes();
    let Some(code) = bytes.next() else {
        return Err(GitDomainError::Parse {
            stream: "raw diff",
            detail: "empty change status".to_owned(),
        });
    };
    if !code.is_ascii_uppercase() {
        return Err(GitDomainError::Parse {
            stream: "raw diff",
            detail: format!("invalid change status '{raw}'"),
        });
    }
    let suffix = bytes.collect::<Vec<_>>();
    let similarity_percent = if suffix.is_empty() {
        None
    } else {
        let suffix = std::str::from_utf8(&suffix).map_err(|error| GitDomainError::Parse {
            stream: "raw diff",
            detail: format!("invalid similarity in status '{raw}': {error}"),
        })?;
        let value = suffix
            .parse::<u8>()
            .map_err(|error| GitDomainError::Parse {
                stream: "raw diff",
                detail: format!("invalid similarity in status '{raw}': {error}"),
            })?;
        if value > 100 {
            return Err(GitDomainError::Parse {
                stream: "raw diff",
                detail: format!("similarity exceeded 100 in status '{raw}'"),
            });
        }
        Some(value)
    };
    let kind = match code {
        b'A' => GitChangeKind::Added,
        b'D' => GitChangeKind::Deleted,
        b'M' => GitChangeKind::Modified,
        b'R' => GitChangeKind::Renamed,
        b'C' => GitChangeKind::Copied,
        b'T' => GitChangeKind::TypeChanged,
        _ => GitChangeKind::Unsupported,
    };
    if matches!(kind, GitChangeKind::Renamed | GitChangeKind::Copied)
        && similarity_percent.is_none()
    {
        return Err(GitDomainError::Parse {
            stream: "raw diff",
            detail: format!("rename/copy status '{raw}' omitted its similarity"),
        });
    }
    Ok(GitChangeStatus {
        kind,
        raw: raw.to_owned(),
        similarity_percent,
    })
}

fn parse_optional_mode(raw: &str) -> Result<Option<String>, GitDomainError> {
    if raw.len() != 6 || !raw.bytes().all(|byte| matches!(byte, b'0'..=b'7')) {
        return Err(GitDomainError::Parse {
            stream: "raw diff",
            detail: format!("invalid Git mode '{raw}'"),
        });
    }
    Ok((raw != "000000").then(|| raw.to_owned()))
}

fn parse_optional_raw_object_id(raw: &str) -> Result<Option<String>, GitDomainError> {
    if raw.len() < 40 || !raw.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err(GitDomainError::Parse {
            stream: "raw diff",
            detail: format!("invalid object ID '{raw}'"),
        });
    }
    Ok((!raw.bytes().all(|byte| byte == b'0')).then(|| raw.to_ascii_lowercase()))
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct NumstatRecord {
    old_path: Option<GitPath>,
    new_path: GitPath,
    value: GitNumstat,
}

fn parse_numstats(bytes: &[u8]) -> Result<Vec<NumstatRecord>, GitDomainError> {
    let mut cursor = NulCursor::new(bytes);
    let mut records = Vec::new();
    while let Some(prefix) = cursor.next("numstat record")? {
        let first_tab = prefix
            .iter()
            .position(|byte| *byte == b'\t')
            .ok_or_else(|| GitDomainError::Parse {
                stream: "numstat",
                detail: "record omitted additions separator".to_owned(),
            })?;
        let second_tab = prefix[first_tab + 1..]
            .iter()
            .position(|byte| *byte == b'\t')
            .map(|position| position + first_tab + 1)
            .ok_or_else(|| GitDomainError::Parse {
                stream: "numstat",
                detail: "record omitted deletions separator".to_owned(),
            })?;
        let additions = &prefix[..first_tab];
        let deletions = &prefix[first_tab + 1..second_tab];
        let first_path = &prefix[second_tab + 1..];
        let value = parse_numstat_value(additions, deletions)?;
        let (old_path, new_path) = if first_path.is_empty() {
            let old_path = cursor.next("numstat rename/copy source")?.ok_or_else(|| {
                GitDomainError::Parse {
                    stream: "numstat",
                    detail: "rename/copy omitted source path".to_owned(),
                }
            })?;
            let new_path = cursor
                .next("numstat rename/copy destination")?
                .ok_or_else(|| GitDomainError::Parse {
                    stream: "numstat",
                    detail: "rename/copy omitted destination path".to_owned(),
                })?;
            (
                Some(GitPath::from_bytes(old_path.to_vec())),
                GitPath::from_bytes(new_path.to_vec()),
            )
        } else {
            (None, GitPath::from_bytes(first_path.to_vec()))
        };
        records.push(NumstatRecord {
            old_path,
            new_path,
            value,
        });
    }
    Ok(records)
}

fn parse_numstat_value(additions: &[u8], deletions: &[u8]) -> Result<GitNumstat, GitDomainError> {
    if additions == b"-" || deletions == b"-" {
        if additions != b"-" || deletions != b"-" {
            return Err(GitDomainError::Parse {
                stream: "numstat",
                detail: "binary marker appeared on only one side".to_owned(),
            });
        }
        return Ok(GitNumstat {
            additions: None,
            deletions: None,
            binary: true,
        });
    }
    let additions = parse_ascii_u64(additions, "numstat additions")?;
    let deletions = parse_ascii_u64(deletions, "numstat deletions")?;
    Ok(GitNumstat {
        additions: Some(additions),
        deletions: Some(deletions),
        binary: false,
    })
}

fn parse_ascii_u64(bytes: &[u8], field: &'static str) -> Result<u64, GitDomainError> {
    let value = std::str::from_utf8(bytes).map_err(|error| GitDomainError::Parse {
        stream: "numstat",
        detail: format!("{field} was not ASCII: {error}"),
    })?;
    value.parse::<u64>().map_err(|error| GitDomainError::Parse {
        stream: "numstat",
        detail: format!("invalid {field} '{value}': {error}"),
    })
}

fn attach_numstats(
    changes: &mut [RawChange],
    mut records: Vec<NumstatRecord>,
) -> Result<(), GitDomainError> {
    for change in changes {
        let Some(index) = records
            .iter()
            .position(|record| numstat_matches(change, record))
        else {
            continue;
        };
        change.numstat = Some(records.remove(index).value);
    }
    if !records.is_empty() {
        let paths = records
            .iter()
            .map(|record| record.new_path.display_lossy())
            .collect::<Vec<_>>()
            .join(", ");
        return Err(GitDomainError::Invariant {
            detail: format!(
                "Git numstat contained {} entries absent from the raw diff: {paths}",
                records.len()
            ),
        });
    }
    Ok(())
}

fn numstat_matches(change: &RawChange, record: &NumstatRecord) -> bool {
    match change.status.kind {
        GitChangeKind::Renamed | GitChangeKind::Copied => {
            record.old_path.as_ref() == change.old_path.as_ref()
                && Some(&record.new_path) == change.new_path.as_ref()
        }
        GitChangeKind::Added
        | GitChangeKind::Deleted
        | GitChangeKind::Modified
        | GitChangeKind::TypeChanged
        | GitChangeKind::Unsupported => {
            record.old_path.is_none()
                && change
                    .new_path
                    .as_ref()
                    .or(change.old_path.as_ref())
                    .is_some_and(|path| path == &record.new_path)
        }
    }
}

fn attach_patch_hunks(changes: &mut [RawChange], patch: &[u8]) -> Result<(), GitDomainError> {
    let sections = parse_patch_sections(patch)?;
    if sections.len() != changes.len() {
        return Err(GitDomainError::Invariant {
            detail: format!(
                "Git patch/raw cardinality mismatch: {} patch sections for {} raw changes",
                sections.len(),
                changes.len()
            ),
        });
    }
    for (change, section) in changes.iter_mut().zip(sections) {
        change.hunks = section.hunks;
    }
    Ok(())
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct PatchSection {
    hunks: Vec<GitHunkRange>,
}

fn parse_patch_sections(bytes: &[u8]) -> Result<Vec<PatchSection>, GitDomainError> {
    let mut sections = Vec::new();
    let mut current: Option<PatchSection> = None;
    for line in bytes.split(|byte| *byte == b'\n') {
        let line = line.strip_suffix(b"\r").unwrap_or(line);
        if line.starts_with(b"diff --git ") {
            if let Some(section) = current.take() {
                sections.push(section);
            }
            current = Some(PatchSection { hunks: Vec::new() });
        } else if line.starts_with(b"@@ -") {
            let Some(section) = current.as_mut() else {
                return Err(GitDomainError::Parse {
                    stream: "patch",
                    detail: "hunk appeared before a diff section".to_owned(),
                });
            };
            section.hunks.push(parse_hunk_header(line)?);
        } else if current.is_none() && !line.is_empty() {
            return Err(GitDomainError::Parse {
                stream: "patch",
                detail: format!(
                    "content appeared before the first diff section: '{}'",
                    String::from_utf8_lossy(line)
                ),
            });
        }
    }
    if let Some(section) = current {
        sections.push(section);
    }
    Ok(sections)
}

fn parse_hunk_header(line: &[u8]) -> Result<GitHunkRange, GitDomainError> {
    let line = std::str::from_utf8(line).map_err(|error| GitDomainError::Parse {
        stream: "patch",
        detail: format!("hunk header was not UTF-8/ASCII: {error}"),
    })?;
    let Some(rest) = line.strip_prefix("@@ ") else {
        return Err(GitDomainError::Parse {
            stream: "patch",
            detail: format!("invalid hunk header '{line}'"),
        });
    };
    let Some((ranges, _context)) = rest.split_once(" @@") else {
        return Err(GitDomainError::Parse {
            stream: "patch",
            detail: format!("hunk header omitted closing marker: '{line}'"),
        });
    };
    let mut ranges = ranges.split_ascii_whitespace();
    let old = ranges.next().ok_or_else(|| GitDomainError::Parse {
        stream: "patch",
        detail: format!("hunk header omitted old range: '{line}'"),
    })?;
    let new = ranges.next().ok_or_else(|| GitDomainError::Parse {
        stream: "patch",
        detail: format!("hunk header omitted new range: '{line}'"),
    })?;
    if ranges.next().is_some() {
        return Err(GitDomainError::Parse {
            stream: "patch",
            detail: format!("hunk header had extra ranges: '{line}'"),
        });
    }
    let (before_start, before_lines) = parse_hunk_range(old, '-')?;
    let (candidate_start, candidate_lines) = parse_hunk_range(new, '+')?;
    Ok(GitHunkRange {
        before_start,
        before_lines,
        candidate_start,
        candidate_lines,
    })
}

fn parse_hunk_range(raw: &str, prefix: char) -> Result<(u64, u64), GitDomainError> {
    let Some(raw) = raw.strip_prefix(prefix) else {
        return Err(GitDomainError::Parse {
            stream: "patch",
            detail: format!("range '{raw}' omitted '{prefix}'"),
        });
    };
    let (start, lines) = raw.split_once(',').unwrap_or((raw, "1"));
    let start = start
        .parse::<u64>()
        .map_err(|error| GitDomainError::Parse {
            stream: "patch",
            detail: format!("invalid range start '{start}': {error}"),
        })?;
    let lines = lines
        .parse::<u64>()
        .map_err(|error| GitDomainError::Parse {
            stream: "patch",
            detail: format!("invalid range length '{lines}': {error}"),
        })?;
    Ok((start, lines))
}

fn build_changes(
    before_tree_id: &str,
    candidate_tree_id: &str,
    raw_changes: Vec<RawChange>,
) -> Vec<GitDomainChange> {
    raw_changes
        .into_iter()
        .map(|change| {
            let change_id = stable_change_id(before_tree_id, candidate_tree_id, &change);
            GitDomainChange {
                change_id,
                status: change.status,
                old_path: change.old_path,
                new_path: change.new_path,
                old_mode: change.old_mode,
                new_mode: change.new_mode,
                old_blob_id: change.old_blob_id,
                new_blob_id: change.new_blob_id,
                numstat: change.numstat,
                hunks: change.hunks,
                unit_ids: Vec::new(),
            }
        })
        .collect()
}

fn compare_changes(left: &GitDomainChange, right: &GitDomainChange) -> std::cmp::Ordering {
    let left_path = left.new_path.as_ref().or(left.old_path.as_ref());
    let right_path = right.new_path.as_ref().or(right.old_path.as_ref());
    left_path
        .cmp(&right_path)
        .then_with(|| left.old_path.cmp(&right.old_path))
        .then_with(|| left.status.raw.cmp(&right.status.raw))
        .then_with(|| left.change_id.cmp(&right.change_id))
}

fn build_units(changes: &mut [GitDomainChange]) -> Vec<GitReviewUnit> {
    let mut units = Vec::new();
    for change in changes {
        let kinds = unit_kinds(change);
        for (ordinal, kind) in kinds.into_iter().enumerate() {
            let unit_id = stable_unit_id(&change.change_id, ordinal, &kind);
            change.unit_ids.push(unit_id.clone());
            units.push(GitReviewUnit {
                unit_id,
                change_id: change.change_id.clone(),
                old_path: change.old_path.clone(),
                new_path: change.new_path.clone(),
                kind,
            });
        }
    }
    units.sort_by(|left, right| left.unit_id.cmp(&right.unit_id));
    units
}

fn unit_kinds(change: &GitDomainChange) -> Vec<GitReviewUnitKind> {
    if matches!(change.status.kind, GitChangeKind::Unsupported) {
        return vec![GitReviewUnitKind::ExplicitlyUnsupported {
            reason: format!("unsupported raw Git status '{}'", change.status.raw),
        }];
    }
    let Some(numstat) = change.numstat.as_ref() else {
        return vec![GitReviewUnitKind::ExplicitlyUnsupported {
            reason: "raw change had no matching numstat record".to_owned(),
        }];
    };
    if numstat.binary {
        return vec![GitReviewUnitKind::BinaryFile];
    }
    if change.hunks.is_empty() {
        return vec![GitReviewUnitKind::FileChange];
    }
    change
        .hunks
        .iter()
        .cloned()
        .map(GitReviewUnitKind::TextHunk)
        .collect()
}

fn reconcile(changes: &[GitDomainChange], units: &[GitReviewUnit]) -> GitReconciliationReport {
    let mut paths = Vec::new();
    for change in changes {
        let change_units = units
            .iter()
            .filter(|unit| unit.change_id == change.change_id)
            .collect::<Vec<_>>();
        let unsupported_reason = change_units.iter().find_map(|unit| match &unit.kind {
            GitReviewUnitKind::ExplicitlyUnsupported { reason } => Some(reason.clone()),
            GitReviewUnitKind::TextHunk(_)
            | GitReviewUnitKind::FileChange
            | GitReviewUnitKind::BinaryFile => None,
        });
        let disposition = if unsupported_reason.is_some() {
            GitReconciliationDisposition::ExplicitlyUnsupported
        } else {
            GitReconciliationDisposition::Represented
        };
        let unit_ids = change_units
            .iter()
            .map(|unit| unit.unit_id.clone())
            .collect::<Vec<_>>();
        if let Some(path) = change.old_path.as_ref() {
            paths.push(GitReconciledPath {
                change_id: change.change_id.clone(),
                side: GitRevisionSide::Before,
                path: path.clone(),
                disposition: disposition.clone(),
                unit_ids: unit_ids.clone(),
                reason: unsupported_reason.clone(),
            });
        }
        if let Some(path) = change.new_path.as_ref() {
            paths.push(GitReconciledPath {
                change_id: change.change_id.clone(),
                side: GitRevisionSide::Candidate,
                path: path.clone(),
                disposition,
                unit_ids,
                reason: unsupported_reason,
            });
        }
    }
    paths.sort_by(|left, right| {
        left.side
            .cmp(&right.side)
            .then_with(|| left.path.cmp(&right.path))
            .then_with(|| left.change_id.cmp(&right.change_id))
    });
    let represented_side_path_count = paths
        .iter()
        .filter(|path| matches!(path.disposition, GitReconciliationDisposition::Represented))
        .count();
    let explicitly_unsupported_side_path_count = paths
        .iter()
        .filter(|path| {
            matches!(
                path.disposition,
                GitReconciliationDisposition::ExplicitlyUnsupported
            )
        })
        .count();
    let raw_side_path_count: usize = changes
        .iter()
        .map(|change| {
            usize::from(change.old_path.is_some()) + usize::from(change.new_path.is_some())
        })
        .sum();
    let reconciled = represented_side_path_count + explicitly_unsupported_side_path_count;
    let unreconciled_side_path_count = raw_side_path_count.saturating_sub(reconciled);
    GitReconciliationReport {
        raw_change_count: changes.len(),
        raw_side_path_count,
        represented_side_path_count,
        explicitly_unsupported_side_path_count,
        unreconciled_side_path_count,
        paths,
        complete: unreconciled_side_path_count == 0
            && changes.iter().all(|change| !change.unit_ids.is_empty()),
    }
}

fn stable_change_id(before_tree_id: &str, candidate_tree_id: &str, change: &RawChange) -> String {
    let mut hasher = StableHasher::new(b"sfm.release-review.git-change/1");
    hasher.field(before_tree_id.as_bytes());
    hasher.field(candidate_tree_id.as_bytes());
    hasher.field(change.status.raw.as_bytes());
    hasher.optional_text(change.old_mode.as_deref());
    hasher.optional_text(change.new_mode.as_deref());
    hasher.optional_text(change.old_blob_id.as_deref());
    hasher.optional_text(change.new_blob_id.as_deref());
    hasher.optional_path(change.old_path.as_ref());
    hasher.optional_path(change.new_path.as_ref());
    format!("git-change:blake3:{}", hasher.finish())
}

fn stable_unit_id(change_id: &str, ordinal: usize, kind: &GitReviewUnitKind) -> String {
    let mut hasher = StableHasher::new(b"sfm.release-review.git-unit/1");
    hasher.field(change_id.as_bytes());
    hasher.field(&ordinal.to_be_bytes());
    match kind {
        GitReviewUnitKind::TextHunk(range) => {
            hasher.field(b"text-hunk");
            hasher.field(&range.before_start.to_be_bytes());
            hasher.field(&range.before_lines.to_be_bytes());
            hasher.field(&range.candidate_start.to_be_bytes());
            hasher.field(&range.candidate_lines.to_be_bytes());
        }
        GitReviewUnitKind::FileChange => hasher.field(b"file-change"),
        GitReviewUnitKind::BinaryFile => hasher.field(b"binary-file"),
        GitReviewUnitKind::ExplicitlyUnsupported { reason } => {
            hasher.field(b"explicitly-unsupported");
            hasher.field(reason.as_bytes());
        }
    }
    format!("git-unit:blake3:{}", hasher.finish())
}

struct StableHasher {
    inner: blake3::Hasher,
}

impl StableHasher {
    fn new(domain: &[u8]) -> Self {
        let mut this = Self {
            inner: blake3::Hasher::new(),
        };
        this.field(domain);
        this
    }

    fn field(&mut self, bytes: &[u8]) {
        self.inner.update(&(bytes.len() as u64).to_be_bytes());
        self.inner.update(bytes);
    }

    fn optional_text(&mut self, value: Option<&str>) {
        match value {
            Some(value) => {
                self.field(b"some");
                self.field(value.as_bytes());
            }
            None => self.field(b"none"),
        }
    }

    fn optional_path(&mut self, value: Option<&GitPath>) {
        match value {
            Some(value) => {
                self.field(b"some");
                self.field(value.as_bytes());
            }
            None => self.field(b"none"),
        }
    }

    fn finish(self) -> String {
        self.inner.finalize().to_hex().to_string()
    }
}

struct NulCursor<'a> {
    bytes: &'a [u8],
    position: usize,
}

impl<'a> NulCursor<'a> {
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, position: 0 }
    }

    fn next(&mut self, description: &'static str) -> Result<Option<&'a [u8]>, GitDomainError> {
        if self.position == self.bytes.len() {
            return Ok(None);
        }
        let Some(relative_end) = self.bytes[self.position..]
            .iter()
            .position(|byte| *byte == 0)
        else {
            return Err(GitDomainError::Parse {
                stream: "NUL-delimited output",
                detail: format!("unterminated {description}"),
            });
        };
        let start = self.position;
        let end = start + relative_end;
        self.position = end + 1;
        Ok(Some(&self.bytes[start..end]))
    }
}

#[cfg(test)]
mod parser_tests {
    use super::*;

    #[test]
    fn raw_parser_recognizes_copy_and_type_change() {
        let old = "1".repeat(40);
        let new = "2".repeat(40);
        let input = format!(
            ":100644 100644 {old} {new} C087\0old name.txt\0new name.txt\0\
             :100644 120000 {old} {new} T\0kind.txt\0"
        );
        let changes = parse_raw_changes(input.as_bytes()).expect("raw diff should parse");
        assert_eq!(changes.len(), 2);
        assert_eq!(changes[0].status.kind, GitChangeKind::Copied);
        assert_eq!(changes[0].status.similarity_percent, Some(87));
        assert_eq!(
            changes[0].old_path.as_ref().unwrap().as_utf8(),
            Some("old name.txt")
        );
        assert_eq!(
            changes[0].new_path.as_ref().unwrap().as_utf8(),
            Some("new name.txt")
        );
        assert_eq!(changes[1].status.kind, GitChangeKind::TypeChanged);
        assert_eq!(changes[1].old_mode.as_deref(), Some("100644"));
        assert_eq!(changes[1].new_mode.as_deref(), Some("120000"));
    }

    #[test]
    fn hunk_parser_preserves_zero_length_sides() {
        assert_eq!(
            parse_hunk_header(b"@@ -0,0 +1,2 @@").expect("hunk should parse"),
            GitHunkRange {
                before_start: 0,
                before_lines: 0,
                candidate_start: 1,
                candidate_lines: 2,
            }
        );
        assert_eq!(
            parse_hunk_header(b"@@ -7 +8,0 @@ method").expect("hunk should parse"),
            GitHunkRange {
                before_start: 7,
                before_lines: 1,
                candidate_start: 8,
                candidate_lines: 0,
            }
        );
    }

    #[test]
    fn numstat_parser_preserves_whitespace_paths_and_rename_pairs() {
        let records = parse_numstats(b"1\t2\twhite space.txt\x000\t0\t\0old path\0new path\0")
            .expect("numstat should parse");
        assert_eq!(records.len(), 2);
        assert_eq!(records[0].new_path.as_utf8(), Some("white space.txt"));
        assert_eq!(
            records[1].old_path.as_ref().unwrap().as_utf8(),
            Some("old path")
        );
        assert_eq!(records[1].new_path.as_utf8(), Some("new path"));
    }
}
