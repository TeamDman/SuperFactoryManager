use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionInspect {
    #[facet(rename = "Id")]
    pub id: String,
    #[facet(rename = "Image")]
    pub image: String,
    #[facet(rename = "Config")]
    pub config: super::session_config::SessionConfig,
    #[facet(rename = "HostConfig")]
    pub host: super::session_host::SessionHost,
    #[facet(rename = "State")]
    pub state: super::session_state::SessionState,
    #[facet(rename = "Mounts")]
    pub mounts: Vec<super::session_mount::SessionMount>,
    #[facet(rename = "EffectiveCaps")]
    pub effective_caps: Option<Vec<String>>,
    #[facet(rename = "BoundingCaps")]
    pub bounding_caps: Option<Vec<String>>,
}
