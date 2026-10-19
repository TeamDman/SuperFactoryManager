use facet::Facet;

#[derive(Clone, Debug, Facet, PartialEq, Eq)]
pub(crate) struct TestRoot {
    pub configuration: String,
    pub coordinate: String,
    pub content_hash: String,
}
