use crate::curseforge::CurseforgeProjectFileId;
use eyre::Result;
use eyre::ensure;

/// Explicit existing remote objects. Parsing never discovers or modifies them.
#[derive(Debug)]
pub struct CatalogReleaseAmendTargets {
    modrinth_version_id: String,
    curseforge_file_id: CurseforgeProjectFileId,
}

impl CatalogReleaseAmendTargets {
    /// # Errors
    /// Rejects empty/nonportable version IDs and the absent `CurseForge` file ID.
    pub fn new(modrinth_version_id: String, curseforge_file_id: u64) -> Result<Self> {
        ensure!(
            (1..=64).contains(&modrinth_version_id.len())
                && modrinth_version_id
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric()),
            "amend requires a portable explicit Modrinth version ID"
        );
        ensure!(
            curseforge_file_id != 0,
            "amend requires a positive CurseForge file ID"
        );
        Ok(Self {
            modrinth_version_id,
            curseforge_file_id: CurseforgeProjectFileId(curseforge_file_id),
        })
    }

    pub(super) fn modrinth_version_id(&self) -> &str {
        &self.modrinth_version_id
    }
    pub(super) fn curseforge_file_id(&self) -> CurseforgeProjectFileId {
        self.curseforge_file_id
    }
}
