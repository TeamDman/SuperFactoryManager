use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionMount {
    #[facet(rename = "Type")]
    pub kind: String,
}
