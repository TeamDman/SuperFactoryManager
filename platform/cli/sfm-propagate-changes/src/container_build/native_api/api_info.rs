use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiInfo {
    pub store: super::api_store::ApiStore,
    pub host: super::api_host::ApiHost,
}
