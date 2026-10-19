use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Podman create wire format defines independent sandbox flags"
)]
pub struct SessionCreate {
    pub name: String,
    pub image: String,
    pub command: Vec<String>,
    pub entrypoint: Option<Vec<String>>,
    pub user: String,
    pub labels: BTreeMap<String, String>,
    pub cap_drop: Vec<String>,
    pub no_new_privileges: bool,
    pub seccomp_policy: String,
    pub privileged: bool,
    pub http_proxy: bool,
    pub env_host: bool,
    pub annotations: BTreeMap<String, String>,
    pub idmappings: super::session_id_mapping::SessionIdMapping,
    pub userns: super::session_namespace::SessionNamespace,
    pub netns: super::session_namespace::SessionNamespace,
    pub ipcns: super::session_namespace::SessionNamespace,
    pub pidns: super::session_namespace::SessionNamespace,
    pub utsns: super::session_namespace::SessionNamespace,
    pub cgroupns: super::session_namespace::SessionNamespace,
    pub resource_limits: super::session_resources::SessionResources,
    pub shm_size: u64,
}
