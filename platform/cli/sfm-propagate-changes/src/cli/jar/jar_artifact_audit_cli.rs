use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::jar_build::ArtifactAuditCommand;
use crate::jar_build::ArtifactAuditOptions;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Options for auditing locked SFM toolchain artifacts.
#[derive(Facet, Debug, Clone)]
pub struct JarArtifactAuditArgs {
    /// Branch selector to audit. Defaults to `core`.
    #[facet(default, args::named)]
    pub branch: BranchSelector,

    /// Optional path to write a structured artifact audit report.
    #[facet(rename = "report-json", default, args::named)]
    pub report_json: Option<PathBuf>,

    /// Fail when any locked artifact depends on local-only or unknown provenance.
    #[facet(rename = "require-portable-artifacts", default = false, args::named)]
    pub require_portable_artifacts: bool,

    /// Failure behavior for multi-target selectors: `bail` or `continue`.
    #[facet(rename = "error-action", default, args::named)]
    pub error_action: ErrorAction,

    /// Run matching targets in parallel. Bare `--parallel` defaults to 10.
    #[facet(default, args::named)]
    #[expect(
        clippy::option_option,
        reason = "figue uses Option<Option<T>> to model absent, bare, and valued optional-value flags."
    )]
    pub parallel: Option<Option<usize>>,
}

impl JarArtifactAuditArgs {
    pub(crate) fn into_options(self) -> eyre::Result<ArtifactAuditOptions> {
        Ok(ArtifactAuditOptions {
            branch: self.branch.into_query()?,
            report_json: self.report_json,
            require_portable_artifacts: self.require_portable_artifacts,
            error_action: self.error_action,
            parallelism: Parallelism::from_cli(self.parallel)?,
        })
    }

    /// # Errors
    ///
    /// Returns an error if locked artifact cache/source provenance fails verification.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        ArtifactAuditCommand::new(self.into_options()?, cancellation_token).invoke()
    }
}
