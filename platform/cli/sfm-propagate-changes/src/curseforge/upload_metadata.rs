use crate::curseforge::CurseforgeGameVersionId;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct UploadMetadata {
    pub changelog: String,
    #[facet(rename = "changelogType")]
    pub changelog_type: String,
    #[facet(rename = "displayName")]
    pub display_name: String,
    #[facet(rename = "gameVersions")]
    pub game_versions: Vec<CurseforgeGameVersionId>,
    #[facet(rename = "releaseType")]
    pub release_type: String,
}
