use super::minecraft_library_downloads::MinecraftLibraryDownloads;
use crate::jar_build::library_rule::LibraryRule;
use facet::Facet;
#[derive(Debug, Facet)]
pub(crate) struct MinecraftLibrary {
    pub name: String,
    #[facet(default)]
    pub downloads: Option<MinecraftLibraryDownloads>,
    #[facet(default)]
    pub rules: Vec<LibraryRule>,
}
impl MinecraftLibrary {
    pub(crate) fn linux_allowed(&self) -> eyre::Result<bool> {
        let path = self
            .downloads
            .as_ref()
            .and_then(|value| value.artifact.as_ref())
            .and_then(|value| value.path.as_deref())
            .unwrap_or("");
        crate::jar_build::library_platform::allows(path, &self.rules, "linux", "x86_64")
            .map_err(|message| eyre::eyre!("{}: {message}", self.name))
    }
}
