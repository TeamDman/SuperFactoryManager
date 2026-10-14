use facet::Facet;

/// Explicit raw printed class evidence and its source comparison; no inferred global shift.
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct SourceCorrelation {
    pub id: String,
    pub source_diff_id: String,
    pub class_path: String,
    pub before_javap: String,
    pub after_javap: String,
}
