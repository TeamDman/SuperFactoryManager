use crate::branch_targets::BranchQuery;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct CompareOptions {
    pub branch: BranchQuery,
    pub gradle_jar: Option<PathBuf>,
    pub rust_jar: Option<PathBuf>,
    pub report_json: Option<PathBuf>,
    pub strict_manifest: bool,
    pub error_action: ErrorAction,
    pub parallelism: Parallelism,
}
