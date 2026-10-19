use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionMemory {
    pub limit: u64,
    pub swap: u64,
}
