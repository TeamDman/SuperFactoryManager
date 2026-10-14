use super::review_file::ReviewFile;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Clone, Debug, Facet)]
pub(crate) struct ReviewPayload {
    pub schema: String,
    pub comparison_identity: String,
    pub provenance: BTreeMap<String, String>,
    pub files: Vec<ReviewFile>,
}
