use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Default, Facet)]
pub(super) struct ApiImageConfig {
    #[facet(rename = "Labels", default)]
    pub labels: Option<BTreeMap<String, String>>,
    #[facet(rename = "Entrypoint", default)]
    pub entrypoint: Option<Vec<String>>,
    #[facet(rename = "Cmd", default)]
    pub command: Option<Vec<String>>,
    #[facet(rename = "User", default)]
    pub user: Option<String>,
}
