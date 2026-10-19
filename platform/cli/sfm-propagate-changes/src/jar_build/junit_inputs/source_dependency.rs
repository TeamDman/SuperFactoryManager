use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(super) struct SourceDependency {
    pub configuration: String,
    pub resolved_notation: String,
}
