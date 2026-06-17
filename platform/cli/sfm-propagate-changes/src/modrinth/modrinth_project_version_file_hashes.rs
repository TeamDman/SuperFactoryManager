use facet::Facet;

#[derive(Facet, Debug, Clone, Default)]
pub struct ModrinthProjectVersionFileHashes {
    #[facet(default)]
    pub sha1: Option<String>,
}
