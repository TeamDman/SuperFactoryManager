use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(crate) struct VerifiedTestArtifact {
    pub coordinate: String,
    pub content_hash: String,
    pub sha256: String,
    pub bytes: u64,
    pub cache_path: String,
}
