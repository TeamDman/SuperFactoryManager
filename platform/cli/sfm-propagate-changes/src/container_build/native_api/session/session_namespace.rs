use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionNamespace {
    pub nsmode: String,
    #[facet(default)]
    pub value: Option<String>,
}
