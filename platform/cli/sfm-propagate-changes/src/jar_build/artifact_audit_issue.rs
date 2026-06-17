use super::artifact_audit_issue_kind::ArtifactAuditIssueKind;
use super::artifact_audit_severity::ArtifactAuditSeverity;
use super::engine::ArtifactSource;
use super::json_path::JsonPath;
use facet::Facet;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub(super) struct ArtifactAuditIssue {
    pub(super) severity: ArtifactAuditSeverity,
    pub(super) kind: ArtifactAuditIssueKind,
    pub(super) coordinate: Option<String>,
    pub(super) source: Option<ArtifactSource>,
    #[facet(proxy = JsonPath)]
    pub(super) path: PathBuf,
    pub(super) message: String,
}
