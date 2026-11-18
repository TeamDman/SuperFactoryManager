use super::CatalogGithubReleaseAmendPayload;
use super::CatalogGithubReleasePayload;
use crate::curseforge::CurseforgeAmendFilePayload;
use crate::curseforge::UploadMetadata;
use crate::modrinth::ModrinthAmendVersionPayload;
use crate::modrinth::ModrinthCreateVersionPayload;
use facet::Facet;

/// Distinct create/update payload sets. No endpoint is selected by remote discovery.
#[derive(Debug, Facet)]
#[repr(u8)]
#[facet(rename_all = "snake_case")]
pub enum CatalogReleaseProviderPayloads {
    Create {
        github: CatalogGithubReleasePayload,
        modrinth: Box<ModrinthCreateVersionPayload>,
        curseforge: UploadMetadata,
    },
    Amend {
        github_tag_name: String,
        github: CatalogGithubReleaseAmendPayload,
        modrinth_version_id: String,
        modrinth: ModrinthAmendVersionPayload,
        curseforge: CurseforgeAmendFilePayload,
    },
}
