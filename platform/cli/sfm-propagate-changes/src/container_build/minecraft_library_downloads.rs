use super::minecraft_download::MinecraftDownload;
use facet::Facet;
#[derive(Debug, Facet)]
pub(crate) struct MinecraftLibraryDownloads {
    #[facet(default)]
    pub artifact: Option<MinecraftDownload>,
}
