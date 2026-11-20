use super::projection_catalog::reject_duplicate_catalog_keys;
use super::projection_definitions::ProjectionDefinitions;
use eyre::Result;
use eyre::WrapErr;
use facet::Facet;

/// V1 of `projections.json`, a flat map keyed by projection name.
#[derive(Facet)]
#[facet(transparent)]
pub(crate) struct ProjectionsDocument(pub(super) ProjectionDefinitions);

impl ProjectionsDocument {
    pub(super) fn from_json(input: &str) -> Result<Self> {
        reject_duplicate_catalog_keys(input)?;
        facet_json::from_str(input).wrap_err("could not parse projections catalog")
    }
}
