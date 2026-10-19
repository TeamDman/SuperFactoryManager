use facet::Facet;
#[derive(Clone, Debug, Eq, PartialEq, Facet)]
pub(crate) struct LibraryOs {
    #[facet(default)]
    pub name: Option<String>,
    #[facet(default)]
    pub arch: Option<String>,
    #[facet(default)]
    pub version: Option<String>,
}
