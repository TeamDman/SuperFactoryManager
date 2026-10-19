use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionAutoUserns {
    #[facet(rename = "Size")]
    pub size: u32,
}
