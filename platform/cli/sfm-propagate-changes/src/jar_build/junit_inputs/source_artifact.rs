use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(super) struct SourceArtifact {
    pub coordinate: Option<String>,
    pub hash: String,
}
