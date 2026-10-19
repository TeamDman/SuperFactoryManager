use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(super) struct Supplement {
    pub configuration: String,
    pub coordinate: String,
    pub artifact_hash: String,
}
