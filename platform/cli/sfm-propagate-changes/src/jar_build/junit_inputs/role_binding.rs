use facet::Facet;

/// Source and recipe bytes have separate identities, including template mode.
#[derive(Clone, Debug, Facet)]
pub(crate) struct RoleBinding {
    pub authored_input: String,
    pub selected_output: Option<String>,
    pub declared_template: bool,
    pub authored_sha256: String,
    pub authored_bytes: u64,
    pub projected_sha256: String,
    pub projected_bytes: u64,
    pub recipe_role_sha256: String,
    pub binding_kind: String,
}
