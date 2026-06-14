use super::BuildMode;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct BuildOptions {
    pub mc: String,
    pub refresh: bool,
    pub explain_rebuild: bool,
    pub plan_json: Option<PathBuf>,
    pub java_home: Option<PathBuf>,
    pub allow_local_artifact_cache: bool,
    pub mode: BuildMode,
}
