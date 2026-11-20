use super::projection_catalog::reject_duplicate_catalog_keys;
use super::sfm_projection_feature_registry::SfmProjectionFeatureRegistry;
use eyre::Result;
use eyre::WrapErr;
use facet::Facet;

/// V1 of `feature-definitions.json`, a flat map keyed by feature name.
#[derive(Facet)]
#[facet(transparent)]
pub(crate) struct FeatureDefinitionsDocument(pub(super) SfmProjectionFeatureRegistry);

impl FeatureDefinitionsDocument {
    pub(super) fn from_json(input: &str) -> Result<Self> {
        reject_duplicate_catalog_keys(input).wrap_err("duplicate core feature-definition key")?;
        facet_json::from_str(input).wrap_err("could not parse core feature definitions")
    }
}
