use super::preflight_issue::PreflightIssue;
use super::target_report::TargetReport;
use super::verified_test_artifact::VerifiedTestArtifact;
use facet::Facet;
#[derive(Clone, Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Explicit read-only receipt capabilities."
)]
pub(crate) struct PreflightReport {
    pub schema: String,
    pub targets: Vec<TargetReport>,
    pub issues: Vec<PreflightIssue>,
    pub artifacts: Vec<VerifiedTestArtifact>,
    pub closure_complete: bool,
    pub writes_performed: bool,
    pub java_launched: bool,
    pub live_pom_discovery: bool,
    pub test_source_lock_sha256: String,
    pub test_provenance_sha256: String,
    pub configuration_sha256: String,
    pub files_checked: u64,
    pub bytes_read: u64,
    pub elapsed_ms: u64,
}
