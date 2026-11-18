//! Read-only release metadata from one checked catalog projection.
mod catalog_release_artifact_readiness;
mod catalog_release_changelog;
mod catalog_release_plan;
mod catalog_release_properties;
mod catalog_release_provider_readiness;
mod catalog_release_request;
mod catalog_release_version;

pub use catalog_release_artifact_readiness::CatalogReleaseArtifactReadiness;
pub use catalog_release_changelog::CatalogReleaseChangelog;
pub use catalog_release_plan::CatalogReleasePlan;
pub use catalog_release_provider_readiness::CatalogReleaseProviderReadiness;
pub use catalog_release_request::CatalogReleaseRequest;
pub use catalog_release_version::CatalogReleaseVersion;

#[cfg(test)]
mod catalog_release_fixture;
#[cfg(test)]
mod tests;
