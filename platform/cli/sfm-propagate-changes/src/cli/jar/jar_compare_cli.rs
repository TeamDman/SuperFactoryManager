use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::jar_build::CompareCommand;
use crate::jar_build::CompareOptions;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Options for comparing Gradle and Rust-built jars.
#[derive(Facet, Debug, Clone)]
pub struct JarCompareArgs {
    /// Branch selector to compare. Defaults to `core`.
    #[facet(default, args::named)]
    pub branch: BranchSelector,

    /// Override the expected Gradle-built jar path.
    #[facet(rename = "gradle-jar", default, args::named)]
    pub gradle_jar: Option<PathBuf>,

    /// Override the expected Rust-built `-rust.jar` path.
    #[facet(rename = "rust-jar", default, args::named)]
    pub rust_jar: Option<PathBuf>,

    /// Optional path to write a structured comparison report.
    #[facet(rename = "report-json", default, args::named)]
    pub report_json: Option<PathBuf>,

    /// Compare manifest timestamp-style values instead of ignoring them.
    #[facet(rename = "strict-manifest", default = false, args::named)]
    pub strict_manifest: bool,

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

impl JarCompareArgs {
    pub(crate) fn into_options(self) -> eyre::Result<CompareOptions> {
        Ok(CompareOptions {
            branch: self.branch.into_query()?,
            gradle_jar: self.gradle_jar,
            rust_jar: self.rust_jar,
            report_json: self.report_json,
            strict_manifest: self.strict_manifest,
            error_action: self.error_action,
            parallelism: Parallelism::from_cli(self.parallel)?,
        })
    }

    /// # Errors
    ///
    /// Returns an error if either jar is missing/unreadable or normalized differences are found.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        CompareCommand::new(self.into_options()?, cancellation_token).invoke()
    }
}
