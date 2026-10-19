use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionCpu {
    pub period: u64,
    pub quota: u64,
}
