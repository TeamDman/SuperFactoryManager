use facet::Facet;

/// Observed HEAD/ref state and a proposed tag name. This carries no eligibility
/// or commit-binding authority, and no Git references are created.
#[derive(Debug, Facet)]
pub struct CatalogReleaseTagPlan {
    pub(super) name: String,
    pub(super) commit: String,
    pub(super) working_tree_clean: bool,
    pub(super) existing_object_id: Option<String>,
    pub(super) created: bool,
}
