use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(super) struct RoleInput {
    pub path: String,
    pub sha256: String,
}
