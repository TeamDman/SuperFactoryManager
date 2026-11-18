use super::ErrorAction;
use super::Parallelism;
use crate::branch_targets::BranchQuery;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct ArtifactAuditOptions {
    pub app_home: AppHome,
    pub cache_home: CacheHome,
    pub branch: BranchQuery,
    pub report_json: Option<PathBuf>,
    pub require_portable_artifacts: bool,
    pub error_action: ErrorAction,
    pub parallelism: Parallelism,
}
