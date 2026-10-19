use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionOperation {
    pub op: String,
    #[facet(default,skip_serializing_if=Option::is_none)]
    pub game_test: Option<String>,
    #[facet(default,skip_serializing_if=Option::is_none)]
    pub angle: Option<f64>,
}
