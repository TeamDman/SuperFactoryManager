use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionInspectMapping {
    #[facet(rename = "UidMap")]
    pub uid: Vec<String>,
    #[facet(rename = "GidMap")]
    pub gid: Vec<String>,
}
