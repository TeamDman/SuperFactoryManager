use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ToolingFile {
    pub sha256: String,
    pub bytes: u64,
    pub parent: String,
}
