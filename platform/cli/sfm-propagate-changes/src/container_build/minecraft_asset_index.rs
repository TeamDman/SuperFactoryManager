use super::minecraft_asset::MinecraftAsset;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
pub(crate) struct MinecraftAssetIndex {
    pub objects: BTreeMap<String, MinecraftAsset>,
}
