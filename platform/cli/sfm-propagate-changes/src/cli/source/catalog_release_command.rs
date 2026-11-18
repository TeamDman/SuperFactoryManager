use super::catalog_release_plan_args::CatalogReleasePlanArgs;
use facet::Facet;

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum CatalogReleaseCommand {
    /// Inspect selected release metadata without changing source/build/provider state; normal CLI logging applies.
    Plan(CatalogReleasePlanArgs),
}
