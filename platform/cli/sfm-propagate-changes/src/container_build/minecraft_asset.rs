use facet::Facet;
#[derive(Debug, Facet)]
pub(crate) struct MinecraftAsset {
    pub hash: String,
    pub size: u64,
}
