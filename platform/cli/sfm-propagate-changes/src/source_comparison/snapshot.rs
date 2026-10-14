use super::file::ComparisonFile;
use super::identity::InputIdentity;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[facet(deny_unknown_fields)]
pub struct ComparisonSnapshot {
    pub identity: InputIdentity,
    pub files: BTreeMap<String, ComparisonFile>,
}
