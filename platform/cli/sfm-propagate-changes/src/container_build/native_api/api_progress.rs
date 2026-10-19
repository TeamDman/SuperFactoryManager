use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiProgress {
    #[facet(default)]
    pub stream: Option<String>,
    #[facet(default)]
    pub error: Option<String>,
    #[facet(default, rename = "errorDetail")]
    pub error_detail: Option<super::api_error_detail::ApiErrorDetail>,
}
