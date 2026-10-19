use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(super) struct SourceLockReference {
    pub path: String,
    pub sha256: String,
}
