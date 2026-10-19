use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiNamespace {
    #[facet(rename = "Name")]
    pub name: String,
    #[facet(rename = "Host")]
    pub host: bool,
    #[facet(rename = "Path")]
    pub path: String,
}
