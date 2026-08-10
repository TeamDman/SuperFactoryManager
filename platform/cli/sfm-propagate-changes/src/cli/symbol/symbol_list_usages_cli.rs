use super::SymbolWorkspaceArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::java_analysis::JavaSymbolSelector;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Facet, Debug)]
pub struct SymbolListUsagesArgs {
    /// Class, field, or method selector; class owners may be qualified suffixes.
    #[facet(args::positional)]
    pub selector: Vec<String>,
    #[facet(flatten)]
    pub workspace: SymbolWorkspaceArgs,
}

impl SymbolListUsagesArgs {
    /// # Errors
    ///
    /// Returns an error when workspace resolution, parsing, or indexing fails.
    pub fn invoke(self) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(&CancellationToken::new(), &invocation_dir)
    }

    /// Run usage analysis against an explicit invocation directory.
    ///
    /// # Errors
    ///
    /// Returns an error when selector parsing, workspace resolution, parsing,
    /// or indexing fails.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        let selector = JavaSymbolSelector::parse_terms(&self.selector)?;
        let branch = self.workspace.branch.clone();
        let workspace = self.workspace.resolve(invocation_dir)?;
        let (index, dependency_index) = super::build_query_index(
            &workspace,
            &branch,
            true,
            super::DependencySymbolQuery::Usages(&selector),
            cancellation_token,
        )?;
        let mut report = index.usages(&selector);
        if let Some(dependency_index) = dependency_index {
            report = report.with_dependency_index(dependency_index);
        }
        let exit_code = report.status();
        Ok(CliOutput::facet_with_csv_and_status(
            report,
            |report| Ok(report.to_csv()),
            exit_code,
        ))
    }
}
