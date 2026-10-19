use super::maven_metadata_pin::MavenMetadataPin;
use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct MavenMetadataCatalog {
    pub schema: String,
    pub repository: String,
    pub artifacts: Vec<MavenMetadataPin>,
}
