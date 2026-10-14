use facet::Facet;
#[derive(Clone, Debug, Facet)]
pub(crate) struct ReviewFile {
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub old_related_references:
        Option<crate::source_projection::reference_associations::ReferenceAssociations>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub new_related_references:
        Option<crate::source_projection::reference_associations::ReferenceAssociations>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub whitespace_basis: Option<String>,
    pub id: String,
    pub identity: String,
    pub scope: String,
    pub old_path: Option<String>,
    pub new_path: Option<String>,
    pub before_sha256: Option<String>,
    pub after_sha256: Option<String>,
    pub status: String,
    pub extension: String,
}
