use crate::modrinth::ModrinthDependencyPayload;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct ModrinthCreateVersionPayload {
    pub name: String,
    #[facet(rename = "version_number")]
    pub version_number: String,
    pub changelog: String,
    pub dependencies: Vec<ModrinthDependencyPayload>,
    #[facet(rename = "game_versions")]
    pub game_versions: Vec<String>,
    #[facet(rename = "version_type")]
    pub version_type: String,
    pub loaders: Vec<String>,
    pub featured: bool,
    #[facet(rename = "project_id")]
    pub project_id: String,
    #[facet(rename = "file_parts")]
    pub file_parts: Vec<String>,
}
