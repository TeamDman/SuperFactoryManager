use super::target_report::TargetReport;
use super::verified_test_artifact::VerifiedTestArtifact;
use facet::Facet;
#[derive(Clone, Debug, Facet)]
pub(crate) struct PreparedJunitInputs {
    pub schema: String,
    pub identity: String,
    pub target: TargetReport,
    pub source_snapshot_identity: String,
    pub java_identity: String,
    pub cli_sha256: String,
    pub test_source_lock_sha256: String,
    pub test_provenance_sha256: String,
    pub configuration_sha256: String,
    pub artifacts: Vec<VerifiedTestArtifact>,
    pub annotation_processing_enabled: bool,
}
