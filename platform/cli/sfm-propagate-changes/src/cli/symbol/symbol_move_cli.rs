use super::SymbolWorkspaceArgs;
use super::parse_mutation_request;
use crate::cli::output::CliOutput;
use crate::java_analysis::SymbolCommandOutcome;
use crate::java_analysis::SymbolMutationUnsupportedOutput;
use facet::Facet;
use figue::{self as args};
use std::path::PathBuf;

#[derive(Facet, Debug)]
pub struct SymbolMoveArgs {
    /// Access Transformer style selector followed by the qualified destination.
    #[facet(args::positional)]
    pub terms: Vec<String>,
    #[facet(flatten)]
    pub workspace: SymbolWorkspaceArgs,
    #[facet(default, args::named)]
    pub dry_run: bool,
    #[facet(default, args::named)]
    pub apply: bool,
    #[facet(default, args::named)]
    pub output_root: Option<PathBuf>,
}

impl SymbolMoveArgs {
    /// # Errors
    ///
    /// Returns an error when the frozen mutation contract is malformed.
    pub fn invoke(self) -> eyre::Result<CliOutput> {
        self.workspace.validate()?;
        let request = parse_mutation_request(
            &self.terms,
            self.dry_run,
            self.apply,
            self.output_root.as_ref(),
        )?;
        Ok(CliOutput::facet_with_status(
            SymbolMutationUnsupportedOutput::new(
                "move",
                &request.selector.canonical(),
                &request.destination,
            ),
            SymbolCommandOutcome::Unsupported.exit_code(),
        ))
    }
}
