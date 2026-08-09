use crate::curseforge::CurseforgeGameVersionId;
use crate::curseforge::CurseforgeGameVersionTypeId;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct CurseforgeGameVersion {
    pub id: CurseforgeGameVersionId,
    #[facet(default, rename = "gameVersionTypeID")]
    pub game_version_type_id: Option<CurseforgeGameVersionTypeId>,
    pub name: String,
    #[facet(default)]
    pub slug: Option<String>,
}
