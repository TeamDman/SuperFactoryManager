use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiComponent {
    #[facet(rename = "Name")]
    pub name: String,
}
