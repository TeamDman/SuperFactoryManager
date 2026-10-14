use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ClassEntry {
    pub path: String,
    pub status: String,
    pub added: usize,
    pub removed: usize,
    pub before: String,
    pub after: String,
    pub debug_before: String,
    pub debug_after: String,
    pub before_sha256: String,
    pub after_sha256: String,
}
