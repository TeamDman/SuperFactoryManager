use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::java_analysis::JavaSourceWorkspace;
use crate::java_analysis::JavaSymbolGlob;
use crate::java_analysis::JavaSymbolIndex;
use facet::Facet;
use figue::{self as args};
use std::path::Path;
use std::path::PathBuf;

/// List declarations in a generated source projection without a branch index.
#[derive(Facet, Debug)]
pub struct SymbolProjectListArgs {
    /// Optional case-sensitive glob over canonical symbol selectors (`*` and `?`).
    #[facet(default, args::positional)]
    pub pattern: Option<String>,
    /// Generated standalone Gradle project root.
    #[facet(args::named)]
    pub project_root: PathBuf,
}

impl SymbolProjectListArgs {
    /// # Errors
    ///
    /// Returns an error when the generated root or Java source cannot be read.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        cancellation_token.bail_if_cancelled()?;
        let project_root = if self.project_root.is_absolute() {
            self.project_root
        } else {
            invocation_dir.join(self.project_root)
        };
        let workspace = JavaSourceWorkspace::resolve_generated_project(&project_root)?;
        cancellation_token.bail_if_cancelled()?;
        let index = JavaSymbolIndex::build_definitions(&workspace)?;
        cancellation_token.bail_if_cancelled()?;
        let report = index.list(&JavaSymbolGlob::new(self.pattern));
        let exit_code = report.status();
        Ok(CliOutput::facet_with_csv_and_status(
            report,
            |report| Ok(report.to_csv()),
            exit_code,
        ))
    }
}
