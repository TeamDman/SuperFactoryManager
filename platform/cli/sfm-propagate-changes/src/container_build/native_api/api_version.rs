use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiVersion {
    #[facet(rename = "Version")]
    pub version: String,
    #[facet(rename = "Os")]
    pub os: String,
    #[facet(rename = "Arch")]
    pub arch: String,
    #[facet(rename = "Components")]
    pub components: Vec<super::api_component::ApiComponent>,
}
