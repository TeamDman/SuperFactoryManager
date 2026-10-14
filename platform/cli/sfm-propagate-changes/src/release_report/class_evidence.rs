use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ClassEvidence {
    pub schema: String,
    pub projection: String,
    pub baseline_sha256: String,
    pub candidate_sha256: String,
    pub tool: String,
    pub classes: Vec<super::class_entry::ClassEntry>,
}
