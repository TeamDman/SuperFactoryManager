//! Current consumption evidence kept separate from historical fresh production.

use super::local_dependency_key::LocalDependencyKey;
use super::local_dependency_manifest::LocalDependencyManifest;
use super::nfrt_launch_contract::PreparedNfrtInvocationInput;
use eyre::Result;
use facet::Facet;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(super) struct LocalDependencyConsumption {
    pub schema: String,
    pub key: String,
    pub production_contract_sha256: String,
    pub current_contract_sha256: String,
    pub current_preparation_identity: String,
    pub authority: String,
    pub classes_bytes: u64,
    pub classes_sha256: String,
    pub sources_bytes: u64,
    pub sources_sha256: String,
    pub historical_reuse: bool,
}

impl LocalDependencyConsumption {
    /// Storage must retain the verified read-only files through last use.
    pub(super) fn reused(
        key: &LocalDependencyKey,
        prepared: &PreparedNfrtInvocationInput,
        manifest: &LocalDependencyManifest,
    ) -> Result<Self> {
        key.matches_prepared(prepared)?;
        manifest.validate(key)?;
        Ok(Self {
            schema: "sfm:local_dependency_consumption@1".to_owned(),
            key: key.digest(),
            production_contract_sha256: manifest.production_contract_sha256.clone(),
            current_contract_sha256: prepared.contract_identity(),
            current_preparation_identity: prepared.receipt().preparation_identity.clone(),
            authority: "trusted-local-developer-cache".to_owned(),
            classes_bytes: manifest.classes_bytes,
            classes_sha256: manifest.classes_sha256.clone(),
            sources_bytes: manifest.sources_bytes,
            sources_sha256: manifest.sources_sha256.clone(),
            historical_reuse: true,
        })
    }
}
