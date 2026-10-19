use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Check every approved 4.35 `JUnit` closure without writing or launching Java.
#[derive(Debug, Facet)]
pub struct TestPreflightArgs {
    /// Repository containing the released projection catalog.
    #[facet(args::named)]
    pub repo_root: PathBuf,
}

impl TestPreflightArgs {
    pub(crate) fn invoke(self, cancel: &CancellationToken) -> eyre::Result<CliOutput> {
        let report = crate::jar_build::engine::junit_inputs::preflight(&self.repo_root, cancel)?;
        let status = u8::from(!report.closure_complete);
        Ok(CliOutput::facet_with_status(report, status))
    }
}
