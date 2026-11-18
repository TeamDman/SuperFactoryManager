use super::CatalogReleaseAmendTargets;

/// Create and amend have separate inputs and wire contracts.
#[derive(Debug)]
pub enum CatalogReleaseAction {
    Create,
    Amend(CatalogReleaseAmendTargets),
}
