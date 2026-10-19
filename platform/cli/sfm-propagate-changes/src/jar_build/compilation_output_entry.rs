use facet::Facet;

#[derive(Debug, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(super) struct CompilationOutputEntry {
    pub relative_path: String,
    pub bytes: u64,
    pub sha256: String,
}
