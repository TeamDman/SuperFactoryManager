use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
pub struct SessionReceipt {
    pub schema: String,
    pub session: String,
    pub container_id: String,
    pub image: String,
    pub running: bool,
    pub exit_code: i64,
    pub oom_killed: bool,
    pub ready: Option<super::SessionReady>,
    pub response: Option<super::SessionResponse>,
    pub artifacts: BTreeMap<String, String>,
    pub removed: bool,
}
