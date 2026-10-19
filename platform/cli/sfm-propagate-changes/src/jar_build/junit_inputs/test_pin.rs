use facet::Facet;

#[derive(Clone, Debug, Facet, PartialEq, Eq)]
pub(crate) struct TestPin {
    pub coordinate: String,
    pub content_hash: String,
    pub full_sha256: Option<String>,
    pub bytes: Option<u64>,
    pub origin: Option<String>,
    pub authority: String,
}
