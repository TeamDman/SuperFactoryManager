use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiHost {
    #[facet(rename = "memTotal")]
    pub mem_total: u64,
    #[facet(rename = "memFree")]
    pub mem_free: u64,
    pub security: super::api_security::ApiSecurity,
}
