use super::artifact_audit_issue::ArtifactAuditIssue;
use super::artifact_audit_issue_kind::ArtifactAuditIssueKind;
use super::artifact_audit_severity::ArtifactAuditSeverity;
use super::engine::ArtifactSource;
use super::json_path::JsonPath;
use facet::Facet;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub(super) struct ArtifactAuditReport {
    #[facet(proxy = JsonPath)]
    pub(super) lockfile_path: PathBuf,
    pub(super) passed: bool,
    pub(super) fresh_slate_portable: bool,
    pub(super) total_artifacts: usize,
    pub(super) verified_artifacts: usize,
    pub(super) error_count: usize,
    pub(super) warning_count: usize,
    pub(super) missing_artifacts: usize,
    pub(super) hash_mismatches: usize,
    pub(super) provenance_mismatches: usize,
    pub(super) original_source_mismatches: usize,
    pub(super) source_git_mismatches: usize,
    pub(super) non_portable_artifacts: usize,
    pub(super) issues: Vec<ArtifactAuditIssue>,
}

impl ArtifactAuditReport {
    pub(super) fn new(lockfile_path: PathBuf) -> Self {
        Self {
            lockfile_path,
            passed: false,
            fresh_slate_portable: false,
            total_artifacts: 0,
            verified_artifacts: 0,
            error_count: 0,
            warning_count: 0,
            missing_artifacts: 0,
            hash_mismatches: 0,
            provenance_mismatches: 0,
            original_source_mismatches: 0,
            source_git_mismatches: 0,
            non_portable_artifacts: 0,
            issues: Vec::new(),
        }
    }
    // todo(2026-06-16) dubious helper
    pub(super) fn push_error(
        &mut self,
        kind: ArtifactAuditIssueKind,
        coordinate: Option<String>,
        source: Option<ArtifactSource>,
        path: &Path,
        message: String,
    ) {
        self.push_issue(
            ArtifactAuditSeverity::Error,
            kind,
            coordinate,
            source,
            path,
            message,
        );
    }
    // todo(2026-06-16) dubious helper

    pub(super) fn push_warning(
        &mut self,
        kind: ArtifactAuditIssueKind,
        coordinate: Option<String>,
        source: Option<ArtifactSource>,
        path: &Path,
        message: String,
    ) {
        self.push_issue(
            ArtifactAuditSeverity::Warning,
            kind,
            coordinate,
            source,
            path,
            message,
        );
    }
    // todo(2026-06-16) dubious helper

    fn push_issue(
        &mut self,
        severity: ArtifactAuditSeverity,
        kind: ArtifactAuditIssueKind,
        coordinate: Option<String>,
        source: Option<ArtifactSource>,
        path: &Path,
        message: String,
    ) {
        self.issues.push(ArtifactAuditIssue {
            severity,
            kind,
            coordinate,
            source,
            path: path.to_path_buf(),
            message,
        });
    }

    pub(super) fn finalize(&mut self, require_portable_artifacts: bool) {
        self.error_count = self
            .issues
            .iter()
            .filter(|issue| issue.severity == ArtifactAuditSeverity::Error)
            .count();
        self.warning_count = self
            .issues
            .iter()
            .filter(|issue| issue.severity == ArtifactAuditSeverity::Warning)
            .count();
        self.missing_artifacts = self
            .issues
            .iter()
            .filter(|issue| {
                matches!(
                    issue.kind,
                    ArtifactAuditIssueKind::LockfileMissing
                        | ArtifactAuditIssueKind::ArtifactMissing
                        | ArtifactAuditIssueKind::DependencyArtifactMissing
                )
            })
            .count();
        self.hash_mismatches = self
            .issues
            .iter()
            .filter(|issue| {
                matches!(
                    issue.kind,
                    ArtifactAuditIssueKind::ArtifactHashMismatch
                        | ArtifactAuditIssueKind::ProvenanceHashMismatch
                )
            })
            .count();
        self.provenance_mismatches = self
            .issues
            .iter()
            .filter(|issue| issue.kind == ArtifactAuditIssueKind::ProvenanceMismatch)
            .count();
        self.original_source_mismatches = self
            .issues
            .iter()
            .filter(|issue| {
                matches!(
                    issue.kind,
                    ArtifactAuditIssueKind::OriginalSourceMissing
                        | ArtifactAuditIssueKind::OriginalSourceHashMismatch
                )
            })
            .count();
        self.source_git_mismatches = self
            .issues
            .iter()
            .filter(|issue| {
                matches!(
                    issue.kind,
                    ArtifactAuditIssueKind::SourceGitMissing
                        | ArtifactAuditIssueKind::SourceGitMismatch
                )
            })
            .count();
        self.non_portable_artifacts = self
            .issues
            .iter()
            .filter(|issue| issue.kind == ArtifactAuditIssueKind::NonPortableProvenance)
            .count();
        self.fresh_slate_portable = self.non_portable_artifacts == 0;
        self.passed =
            self.error_count == 0 && (!require_portable_artifacts || self.fresh_slate_portable);
    }
}
