use facet::Facet;
#[derive(Debug, Facet)]
pub struct ControllerReceipt {
    #[facet(default)]
    pub sequence: Option<u64>,
    #[facet(default)]
    pub error: Option<String>,
}
