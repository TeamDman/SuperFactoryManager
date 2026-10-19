use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(super) struct ProvisionArtifact {
    pub coordinate: String,
    pub origin: String,
    pub bytes: u64,
    pub sha256: String,
    #[facet(rename = "canonicalHash")]
    pub canonical_hash: String,
}
