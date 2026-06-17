use crate::curseforge::UploadMetadata;
use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct UploadPlan {
    pub jar_path: PathBuf,
    pub mc_version: String,
    pub metadata_names: Vec<String>,
    pub metadata: UploadMetadata,
}
