use crate::modrinth::ModrinthProjectVersionFileHashes;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct ModrinthProjectVersionFile {
    pub filename: String,
    pub url: String,
    #[facet(default)]
    pub primary: Option<bool>,
    #[facet(default)]
    pub hashes: ModrinthProjectVersionFileHashes,
}
