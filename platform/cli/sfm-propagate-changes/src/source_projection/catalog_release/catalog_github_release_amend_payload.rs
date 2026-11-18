use facet::Facet;

/// GitHub title/notes amendment body. The selected release tag is carried
/// separately; this body cannot retarget it or change draft/prerelease flags.
#[derive(Debug, Facet)]
pub struct CatalogGithubReleaseAmendPayload {
    pub(super) name: String,
    pub(super) body: String,
}
