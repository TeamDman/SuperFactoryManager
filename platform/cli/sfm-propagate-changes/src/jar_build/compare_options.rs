use crate::branch_targets::BranchQuery;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct CompareOptions {
    pub branch: BranchQuery,
    pub gradle_jar: Option<PathBuf>,
    pub rust_jar: Option<PathBuf>,
    pub report_json: Option<PathBuf>,
    pub strict_manifest: bool,
}
