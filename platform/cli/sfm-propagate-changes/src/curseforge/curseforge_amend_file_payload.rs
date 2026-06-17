use crate::curseforge::CurseforgeProjectFileId;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct CurseforgeAmendFilePayload {
    #[facet(rename = "fileID")]
    pub file_id: CurseforgeProjectFileId,
    pub changelog: String,
    #[facet(rename = "changelogType")]
    pub changelog_type: String,
    #[facet(rename = "displayName")]
    pub display_name: String,
}
