use super::minecraft_download::MinecraftDownload;
use super::minecraft_library::MinecraftLibrary;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
pub(crate) struct MinecraftVersion {
    pub id: String,
    #[facet(rename = "assetIndex")]
    pub asset_index: MinecraftDownload,
    pub downloads: BTreeMap<String, MinecraftDownload>,
    pub libraries: Vec<MinecraftLibrary>,
}
