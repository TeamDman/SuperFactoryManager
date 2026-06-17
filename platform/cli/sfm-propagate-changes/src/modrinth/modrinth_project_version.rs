use crate::modrinth::ModrinthProjectVersionFile;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct ModrinthProjectVersion {
    pub id: String,
    #[facet(default)]
    pub name: Option<String>,
    #[facet(default, rename = "version_number")]
    pub version_number: Option<String>,
    #[facet(default, rename = "game_versions")]
    pub game_versions: Vec<String>,
    #[facet(default)]
    pub loaders: Vec<String>,
    #[facet(default, rename = "date_published")]
    pub date_published: Option<String>,
    #[facet(default)]
    pub files: Vec<ModrinthProjectVersionFile>,
}
