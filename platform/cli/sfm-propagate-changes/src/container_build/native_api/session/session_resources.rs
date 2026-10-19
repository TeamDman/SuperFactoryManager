use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionResources {
    pub memory: super::session_memory::SessionMemory,
    pub cpu: super::session_cpu::SessionCpu,
    pub pids: super::session_pids::SessionPids,
}
