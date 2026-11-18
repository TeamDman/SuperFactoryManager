use facet::Facet;

/// Reviewable GitHub release fields; no HTTP client or upload authority.
#[derive(Debug, Facet)]
pub struct CatalogGithubReleasePayload {
    pub(super) tag_name: String,
    pub(super) target_commitish: String,
    pub(super) name: String,
    pub(super) body: String,
    pub(super) draft: bool,
    pub(super) prerelease: bool,
}
