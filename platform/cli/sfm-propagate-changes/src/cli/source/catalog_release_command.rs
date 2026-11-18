use super::catalog_release_plan_args::CatalogReleasePlanArgs;
use super::catalog_release_prepare_args::CatalogReleasePrepareArgs;
use facet::Facet;

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum CatalogReleaseCommand {
    /// Inspect selected release metadata without changing source/build/provider state; normal CLI logging applies.
    Plan(CatalogReleasePlanArgs),
    /// Verify one native JAR and prepare offline provider payloads and copy/tag plans; performs no publication.
    Prepare(CatalogReleasePrepareArgs),
}
