use super::input::ComparisonInput;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[facet(deny_unknown_fields)]
pub struct InputIdentity {
    pub selected: ComparisonInput,
    pub evidence: BTreeMap<String, String>,
    /// Sorted paths, byte hashes and sizes, independent of adapter provenance.
    pub content_sha256: String,
    /// Also binds modes and per-file provenance.
    pub snapshot_sha256: String,
    pub exclusions: Vec<String>,
}
