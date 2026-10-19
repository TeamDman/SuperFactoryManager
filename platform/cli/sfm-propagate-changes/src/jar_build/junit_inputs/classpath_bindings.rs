use facet::Facet;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct ClasspathBindings {
    pub compile: Vec<String>,
    pub runtime: Vec<String>,
    pub annotation_processor_roots: Vec<String>,
    pub console: String,
}
