use super::library_os::LibraryOs;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Clone, Debug, Eq, PartialEq, Facet)]
pub(crate) struct LibraryRule {
    pub action: String,
    #[facet(default)]
    pub os: Option<LibraryOs>,
    #[facet(default)]
    pub features: Option<BTreeMap<String, bool>>,
}
