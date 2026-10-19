use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionIdMapping {
    #[facet(rename = "HostUIDMapping")]
    pub host_uid: bool,
    #[facet(rename = "HostGIDMapping")]
    pub host_gid: bool,
    #[facet(rename = "AutoUserNs")]
    pub auto: bool,
    #[facet(rename = "AutoUserNsOpts")]
    pub options: super::session_auto_userns::SessionAutoUserns,
}
