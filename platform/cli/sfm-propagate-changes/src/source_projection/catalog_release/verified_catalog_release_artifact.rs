use super::CatalogReleasePlan;
use super::CatalogReleaseRequest;
use super::catalog_release_artifact_file::CatalogReleaseArtifactFile;
use super::catalog_release_artifact_receipt::CatalogReleaseArtifactReceipt;
use super::catalog_release_artifact_receipt::MAX_JAR_BYTES;
use super::catalog_release_artifact_receipt::MAX_RECEIPT_BYTES;
use crate::cancellation::CancellationToken;
use crate::source_projection::catalog_owned_project::CatalogOwnedProject;
use crate::source_projection::catalog_owned_project::collect_catalog_project;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;

/// Current selected ownership, native package observation and exact retained
/// artifact bytes. No upload capability or credentials are acquired. The local
/// receipt remains unsigned and is not hostile-writer build attestation.
#[derive(Debug)]
pub(crate) struct VerifiedCatalogReleaseArtifact {
    metadata: CatalogReleasePlan,
    project: CatalogOwnedProject,
    receipt: CatalogReleaseArtifactReceipt,
    receipt_file: CatalogReleaseArtifactFile,
    artifact: CatalogReleaseArtifactFile,
    relative: String,
    receipt_sha256: String,
    cancellation: CancellationToken,
}

impl VerifiedCatalogReleaseArtifact {
    /// # Errors
    /// Refuses missing native receipts, stale selected ownership, changed
    /// recipes/producer, unsafe or replaced files, incomplete archives and
    /// cancellation. This operation never creates logs, caches or build files;
    /// the invoking CLI retains its normal cache-home logging.
    pub(crate) fn verify(
        request: &CatalogReleaseRequest,
        jar_relative: &str,
        cancellation: &CancellationToken,
    ) -> Result<Self> {
        cancellation.bail_if_cancelled()?;
        CatalogReleaseArtifactReceipt::validate_relative(jar_relative)?;
        let metadata = CatalogReleasePlan::prepare(request, cancellation)?;
        let project = collect_catalog_project(
            request.repo_root(),
            request.repo_root(),
            request.projection(),
        )?
        .check_current()?;
        ensure!(
            metadata.ownership_identity() == project.ownership_identity()?,
            "release metadata changed during artifact admission"
        );
        let receipt_file = CatalogReleaseArtifactFile::read(
            project.project_root(),
            &CatalogReleaseArtifactReceipt::sidecar_relative(jar_relative),
            MAX_RECEIPT_BYTES,
        )?;
        let receipt: CatalogReleaseArtifactReceipt = facet_json::from_slice(receipt_file.bytes())?;
        receipt.validate(&metadata, &project, jar_relative)?;
        let artifact =
            CatalogReleaseArtifactFile::read(project.project_root(), jar_relative, MAX_JAR_BYTES)?;
        ensure!(
            receipt.jar_bytes() == u64::try_from(artifact.bytes().len())?
                && receipt.jar_sha256() == sha256(artifact.bytes()),
            "native release receipt does not match exact JAR bytes"
        );
        CatalogReleaseArtifactReceipt::check_archive(
            &metadata,
            &project,
            artifact.bytes(),
            cancellation,
        )?;
        let verified = Self {
            metadata,
            project,
            receipt,
            receipt_sha256: sha256(receipt_file.bytes()),
            receipt_file,
            artifact,
            relative: jar_relative.to_owned(),
            cancellation: cancellation.clone(),
        };
        verified.recheck()?;
        Ok(verified)
    }

    pub(crate) fn metadata(&self) -> &CatalogReleasePlan {
        &self.metadata
    }
    pub(crate) fn relative_path(&self) -> &str {
        &self.relative
    }
    pub(crate) fn sha256(&self) -> &str {
        self.receipt.jar_sha256()
    }
    pub(crate) fn bytes(&self) -> u64 {
        self.receipt.jar_bytes()
    }
    pub(crate) fn receipt_sha256(&self) -> &str {
        &self.receipt_sha256
    }
    pub(crate) fn into_metadata(self) -> CatalogReleasePlan {
        self.metadata
    }

    /// # Errors
    /// Refuses drift in retained objects, current selected inputs or the native
    /// recipe configuration immediately before the caller emits its report.
    pub(crate) fn recheck(&self) -> Result<()> {
        self.cancellation.bail_if_cancelled()?;
        self.project.recheck()?;
        self.receipt
            .validate(&self.metadata, &self.project, &self.relative)?;
        self.receipt_file.recheck()?;
        self.artifact.recheck()?;
        self.cancellation.bail_if_cancelled()?;
        Ok(())
    }
}
