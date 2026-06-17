use crate::curseforge::CurseforgeProjectFileItem;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct CurseforgeProjectFileListEnvelope {
    #[facet(default)]
    pub data: Vec<CurseforgeProjectFileItem>,
}
