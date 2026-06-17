use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct CurseforgeProjectFileHash {
    #[facet(default)]
    pub algo: Option<u32>,
    #[facet(default)]
    pub value: Option<String>,
}
