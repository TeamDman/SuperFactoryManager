use crate::branch_targets::BranchQuery;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct DependencyAddOptions {
    pub branch: BranchQuery,
    pub coordinate: String,
    pub weak_mod_metadata: bool,
    pub metadata_path: Option<PathBuf>,
    pub mod_id: Option<String>,
    pub version: Option<String>,
}
