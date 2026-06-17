use crate::curseforge::CurseforgeGameVersionId;
use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct ResolvedMetadataPlan {
    pub jar_path: PathBuf,
    pub mc_version: String,
    pub metadata_names: Vec<String>,
    pub game_version_ids: Vec<CurseforgeGameVersionId>,
}
