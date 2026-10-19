use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionReady {
    pub schema: String,
    pub world: String,
    pub max_requests: u64,
    pub lifetime_seconds: u64,
}
