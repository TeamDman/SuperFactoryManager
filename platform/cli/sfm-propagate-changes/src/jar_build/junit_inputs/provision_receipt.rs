use super::provision_artifact::ProvisionArtifact;
use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ProvisionReceipt {
    pub schema: String,
    #[facet(rename = "sourceLockAfterSha256")]
    pub source_lock_after_sha256: String,
    pub artifacts: Vec<ProvisionArtifact>,
}
