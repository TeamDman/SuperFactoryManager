use super::ArtifactAuditOptions;

#[derive(Debug)]
pub struct ArtifactAuditCommand {
    options: ArtifactAuditOptions,
}

impl ArtifactAuditCommand {
    #[must_use]
    pub fn new(options: ArtifactAuditOptions) -> Self {
        Self { options }
    }

    /// # Errors
    ///
    /// Returns an error when locked artifact cache/source provenance fails verification.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_artifact_audit(&self.options)
    }
}
// todo(2026-06-16) cli args struct
