use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiSecurity {
    pub rootless: bool,
    #[facet(rename = "seccompEnabled")]
    pub seccomp_enabled: bool,
}
