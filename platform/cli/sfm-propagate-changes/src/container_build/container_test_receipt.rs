use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
pub struct ContainerTestReceipt {
    pub schema: String,
    pub image: String,
    pub context_identity: String,
    pub projection: String,
    pub worker: String,
    pub exit_code: Option<i64>,
    pub seconds: f64,
    pub summary: Option<crate::jar_build::JunitSummary>,
    pub accepted: bool,
    pub removed: bool,
    pub error: Option<String>,
    pub artifacts: BTreeMap<String, String>,
}
