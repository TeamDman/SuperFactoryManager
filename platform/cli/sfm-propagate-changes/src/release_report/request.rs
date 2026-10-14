use std::path::PathBuf;
/// Explicit local inputs; this request never acquires, builds or publishes evidence.
#[derive(Debug)]
pub struct ReleaseReportRequest {
    pub manifest: PathBuf,
    pub asset_root: PathBuf,
    pub output: PathBuf,
}
