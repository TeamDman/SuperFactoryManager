use crate::curseforge::CurseforgeProjectFileId;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct CurseforgeUploadResponse {
    pub id: CurseforgeProjectFileId,
}
