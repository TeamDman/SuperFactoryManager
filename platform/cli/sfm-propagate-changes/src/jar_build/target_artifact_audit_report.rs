use super::artifact_audit_report::ArtifactAuditReport;
use super::json_branch_name::JsonBranchName;
use super::json_path::JsonPath;
use crate::branch_targets::BranchName;
use facet::Facet;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub(super) struct TargetArtifactAuditReport {
    #[facet(proxy = JsonBranchName)]
    pub(super) branch_name: BranchName,
    #[facet(proxy = JsonPath)]
    pub(super) worktree_path: PathBuf,
    pub(super) report: ArtifactAuditReport,
}
