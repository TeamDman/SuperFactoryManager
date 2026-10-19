use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
pub struct SessionConfig {
    #[facet(rename = "User")]
    pub user: String,
    #[facet(rename = "Entrypoint")]
    pub entrypoint: Vec<String>,
    #[facet(rename = "Cmd")]
    pub command: Vec<String>,
    #[facet(rename = "Labels")]
    pub labels: BTreeMap<String, String>,
    #[facet(rename = "Env")]
    pub env: Vec<String>,
}
