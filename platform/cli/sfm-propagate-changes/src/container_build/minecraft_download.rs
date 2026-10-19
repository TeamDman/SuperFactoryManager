use facet::Facet;
#[derive(Clone, Debug, Facet)]
pub(crate) struct MinecraftDownload {
    #[facet(default)]
    pub path: Option<String>,
    #[facet(default)]
    pub id: Option<String>,
    pub sha1: String,
    pub size: u64,
}
