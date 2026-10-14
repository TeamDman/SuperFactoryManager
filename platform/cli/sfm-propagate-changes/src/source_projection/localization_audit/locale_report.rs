use facet::Facet;
use std::collections::BTreeMap;

#[derive(Debug, Default, Facet)]
pub struct LocaleReport {
    pub missing: Vec<String>,
    /// Keys absent from the observed required inventory, not deletion advice.
    pub extra_observed: Vec<String>,
    pub placeholder_problems: BTreeMap<String, String>,
}
