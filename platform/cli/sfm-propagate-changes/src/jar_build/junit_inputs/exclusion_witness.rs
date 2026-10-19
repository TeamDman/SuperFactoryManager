use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(super) struct ExclusionWitness {
    pub output: String,
    pub source_path: String,
    pub sha256: String,
    pub patterns: Vec<String>,
}
