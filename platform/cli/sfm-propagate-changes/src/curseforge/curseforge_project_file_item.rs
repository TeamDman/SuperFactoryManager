use crate::curseforge::CurseforgeProjectFileHash;
use crate::curseforge::CurseforgeProjectFileId;
use crate::curseforge::CurseforgeProjectId;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct CurseforgeProjectFileItem {
    pub id: CurseforgeProjectFileId,
    #[facet(default, rename = "modId")]
    pub mod_id: Option<CurseforgeProjectId>,
    #[facet(default, rename = "fileName")]
    pub file_name: Option<String>,
    #[facet(default, rename = "displayName")]
    pub display_name: Option<String>,
    #[facet(default, rename = "releaseType")]
    pub release_type: Option<u32>,
    #[facet(default, rename = "fileStatus")]
    pub file_status: Option<u32>,
    #[facet(default, rename = "gameVersions")]
    pub game_versions: Vec<String>,
    #[facet(default, rename = "sortableGameVersions")]
    pub sortable_game_versions: Vec<CurseforgeSortableGameVersion>,
    #[facet(default, rename = "downloadUrl")]
    pub download_url: Option<String>,
    #[facet(default, rename = "downloadCount")]
    pub download_count: u64,
    #[facet(default, rename = "fileDate")]
    pub file_date: Option<String>,
    #[facet(default)]
    pub hashes: Vec<CurseforgeProjectFileHash>,
}

#[derive(Facet, Debug, Clone)]
pub struct CurseforgeSortableGameVersion {
    #[facet(default, rename = "gameVersion")]
    pub game_version: Option<String>,
    #[facet(default, rename = "modLoader")]
    pub mod_loader: Option<u8>,
}
