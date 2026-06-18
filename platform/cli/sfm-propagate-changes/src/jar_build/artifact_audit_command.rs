use super::ArtifactAuditOptions;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct ArtifactAuditCommand {
    options: ArtifactAuditOptions,
    cancellation_token: CancellationToken,
}

impl ArtifactAuditCommand {
    #[must_use]
    pub fn new(options: ArtifactAuditOptions, cancellation_token: CancellationToken) -> Self {
        Self {
            options,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error when locked artifact cache/source provenance fails verification.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_artifact_audit(&self.options, &self.cancellation_token)
    }
}
// todo(2026-06-16) cli args struct
