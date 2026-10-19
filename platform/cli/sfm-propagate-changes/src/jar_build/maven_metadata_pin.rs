use facet::Facet;
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(super) struct MavenMetadataPin {
    pub coordinate: String,
    pub url: String,
    pub sha256: String,
    pub bytes: u64,
    pub path: String,
    #[facet(rename = "retrievedUtc")]
    pub retrieved_utc: String,
}
