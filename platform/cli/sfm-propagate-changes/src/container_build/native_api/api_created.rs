use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiCreated {
    #[facet(rename = "Id")]
    pub id: String,
}
