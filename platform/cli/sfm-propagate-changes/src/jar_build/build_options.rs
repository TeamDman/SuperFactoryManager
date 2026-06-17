use super::BuildMode;
use super::ErrorAction;
use super::Parallelism;
use crate::branch_targets::BranchQuery;
use std::path::PathBuf;

#[derive(Clone, Debug)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "This type carries normalized CLI flags into the build engine."
)]
pub struct BuildOptions {
    pub branch: BranchQuery,
    pub refresh: bool,
    pub explain_rebuild: bool,
    pub plan_json: Option<PathBuf>,
    pub java_home: Option<PathBuf>,
    pub dry_run: bool,
    pub allow_local_artifact_cache: bool,
    pub artifact_sources: Vec<PathBuf>,
    pub require_portable_artifacts: bool,
    pub error_action: ErrorAction,
    pub parallelism: Parallelism,
    pub mode: BuildMode,
}
