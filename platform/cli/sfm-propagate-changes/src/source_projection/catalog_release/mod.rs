//! Read-only release metadata from one checked catalog projection.
mod catalog_github_release_amend_payload;
mod catalog_github_release_payload;
mod catalog_release_action;
mod catalog_release_amend_targets;
mod catalog_release_artifact_file;
mod catalog_release_artifact_readiness;
mod catalog_release_artifact_receipt;
mod catalog_release_changelog;
mod catalog_release_collection_plan;
mod catalog_release_compatibility;
mod catalog_release_plan;
mod catalog_release_preparation;
mod catalog_release_properties;
mod catalog_release_provider_payloads;
mod catalog_release_provider_readiness;
mod catalog_release_provider_request;
mod catalog_release_request;
mod catalog_release_tag_plan;
mod catalog_release_version;
#[cfg(windows)]
mod pending_catalog_release_receipt;
mod verified_catalog_release_artifact;

pub use catalog_github_release_amend_payload::CatalogGithubReleaseAmendPayload;
pub use catalog_github_release_payload::CatalogGithubReleasePayload;
pub use catalog_release_action::CatalogReleaseAction;
pub use catalog_release_amend_targets::CatalogReleaseAmendTargets;
pub use catalog_release_artifact_readiness::CatalogReleaseArtifactReadiness;
pub(crate) use catalog_release_artifact_receipt::CatalogReleaseArtifactReceipt;
pub use catalog_release_changelog::CatalogReleaseChangelog;
pub use catalog_release_collection_plan::CatalogReleaseCollectionPlan;
pub use catalog_release_plan::CatalogReleasePlan;
pub use catalog_release_preparation::CatalogReleasePreparation;
pub use catalog_release_provider_payloads::CatalogReleaseProviderPayloads;
pub use catalog_release_provider_readiness::CatalogReleaseProviderReadiness;
pub use catalog_release_provider_request::CatalogReleaseProviderRequest;
pub use catalog_release_request::CatalogReleaseRequest;
pub use catalog_release_tag_plan::CatalogReleaseTagPlan;
pub use catalog_release_version::CatalogReleaseVersion;
pub(crate) use verified_catalog_release_artifact::VerifiedCatalogReleaseArtifact;

#[cfg(test)]
mod catalog_release_fixture;
#[cfg(test)]
mod catalog_release_preparation_tests;
#[cfg(test)]
mod tests;

#[cfg(test)]
mod catalog_release_artifact_tests;
