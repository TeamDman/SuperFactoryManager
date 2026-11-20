use super::CatalogReleaseVersion;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::projection_name::ProjectionName;
use eyre::Result;
use std::path::Path;
use std::path::PathBuf;

/// Exact local selection; it carries no provider, credential or upload intent.
#[derive(Debug)]
pub struct CatalogReleaseRequest {
    repo_root: PathBuf,
    projection: String,
    expected_mod_version: CatalogReleaseVersion,
}

impl CatalogReleaseRequest {
    /// # Errors
    /// Rejects unsafe roots and nonportable keys, including wildcard selectors.
    pub fn new(
        repo_root: &Path,
        projection: String,
        expected_mod_version: CatalogReleaseVersion,
    ) -> Result<Self> {
        ProjectionName::validate(&projection)?;
        Ok(Self {
            repo_root: checked_directory(repo_root)?,
            projection,
            expected_mod_version,
        })
    }

    pub(super) fn repo_root(&self) -> &Path {
        &self.repo_root
    }

    pub(super) fn projection(&self) -> &str {
        &self.projection
    }

    pub(super) fn expected_mod_version(&self) -> &CatalogReleaseVersion {
        &self.expected_mod_version
    }
}
