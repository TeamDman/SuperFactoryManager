use facet::Facet;
use std::collections::BTreeMap;
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct ContextFile {
    pub path: String,
    pub bytes: u64,
    pub sha256: String,
    pub parent_identity: String,
    pub expected_hash: Option<String>,
    pub authenticated_parents: BTreeMap<String, String>,
}
