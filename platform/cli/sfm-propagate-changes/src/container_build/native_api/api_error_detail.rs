use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiErrorDetail {
    #[facet(default)]
    pub message: Option<String>,
}
