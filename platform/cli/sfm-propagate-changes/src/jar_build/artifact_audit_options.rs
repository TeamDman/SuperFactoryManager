use super::ErrorAction;
use super::Parallelism;
use crate::branch_targets::BranchQuery;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct ArtifactAuditOptions {
    pub branch: BranchQuery,
    pub report_json: Option<PathBuf>,
    pub require_portable_artifacts: bool,
    pub error_action: ErrorAction,
    pub parallelism: Parallelism,
}
