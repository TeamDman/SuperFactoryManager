use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
pub struct SessionHost {
    #[facet(rename = "Memory")]
    pub memory: u64,
    #[facet(rename = "MemorySwap")]
    pub swap: u64,
    #[facet(rename = "CpuPeriod")]
    pub cpu_period: u64,
    #[facet(rename = "CpuQuota")]
    pub cpu_quota: u64,
    #[facet(rename = "PidsLimit")]
    pub pids: u64,
    #[facet(rename = "NetworkMode")]
    pub network: String,
    #[facet(rename = "IpcMode")]
    pub ipc: String,
    #[facet(rename = "PidMode")]
    pub pid: String,
    #[facet(rename = "UTSMode")]
    pub uts: String,
    #[facet(rename = "CgroupMode")]
    pub cgroup: String,
    #[facet(rename = "UsernsMode")]
    pub userns: String,
    #[facet(rename = "Privileged")]
    pub privileged: bool,
    #[facet(rename = "SecurityOpt")]
    pub security: Vec<String>,
    #[facet(rename = "CapAdd")]
    pub cap_add: Vec<String>,
    #[facet(rename = "CapDrop")]
    pub cap_drop: Vec<String>,
    #[facet(rename = "Annotations")]
    pub annotations: Option<BTreeMap<String, String>>,
    #[facet(rename = "IDMappings")]
    pub mappings: Option<super::session_inspect_mapping::SessionInspectMapping>,
    #[facet(rename = "Binds")]
    pub binds: Vec<String>,
    #[facet(rename = "PortBindings")]
    pub ports: BTreeMap<String, Vec<BTreeMap<String, String>>>,
}
