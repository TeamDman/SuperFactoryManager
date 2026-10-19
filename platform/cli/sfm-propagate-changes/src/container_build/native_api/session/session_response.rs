use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionResponse {
    pub schema: String,
    pub sequence: u64,
    pub op: String,
    pub status: String,
    pub duration_ms: u64,
    #[facet(default)]
    pub error: Option<String>,
}
