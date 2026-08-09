use super::SymbolWorkspaceArgs;
use crate::cli::output::CliOutput;
use crate::java_analysis::JavaSymbolIndex;
use crate::java_analysis::JavaSymbolSelector;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Facet, Debug)]
pub struct SymbolUsageListArgs {
    /// Access Transformer style class, field, or method selector.
    #[facet(args::positional)]
    pub selector: Vec<String>,
    #[facet(flatten)]
    pub workspace: SymbolWorkspaceArgs,
}

impl SymbolUsageListArgs {
    /// # Errors
    ///
    /// Returns an error until the Phase 0 source index is connected.
    pub fn invoke(self) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(&invocation_dir)
    }

    /// Run usage analysis against an explicit invocation directory.
    ///
    /// # Errors
    ///
    /// Returns an error when selector parsing, workspace resolution, parsing,
    /// or indexing fails.
    pub fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        let selector = JavaSymbolSelector::parse_terms(&self.selector)?;
        let workspace = self.workspace.resolve(invocation_dir)?;
        let index = JavaSymbolIndex::build(&workspace)?;
        let report = index.usages(&selector);
        let exit_code = report.outcome.exit_code();
        Ok(CliOutput::facet_with_csv_and_status(
            report,
            |report| Ok(report.to_csv()),
            exit_code,
        ))
    }
}
