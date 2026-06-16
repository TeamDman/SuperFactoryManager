use crate::cli::jar::BranchSelector;
use crate::jar_build::CompareCommand;
use crate::jar_build::CompareOptions;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Options for comparing Gradle and Rust-built jars.
#[derive(Facet, Debug, Clone)]
pub struct JarCompareCommand {
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
}

impl JarCompareCommand {
    fn into_options(self) -> eyre::Result<CompareOptions> {
        Ok(CompareOptions {
            branch: self.branch.into_query()?,
            gradle_jar: self.gradle_jar,
            rust_jar: self.rust_jar,
            report_json: self.report_json,
            strict_manifest: self.strict_manifest,
        })
    }
}

/// Run `jar compare`.
///
/// # Errors
///
/// Returns an error if either jar is missing/unreadable or normalized differences are found.
pub(super) fn invoke(command: JarCompareCommand) -> eyre::Result<()> {
    CompareCommand::new(command.into_options()?).invoke()
}
