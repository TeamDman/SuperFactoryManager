use super::SymbolUsageListArgs;
use crate::cli::output::CliOutput;
use facet::Facet;
use figue::{self as args};
use std::path::Path;

#[derive(Facet, Debug)]
pub struct SymbolUsageArgs {
    #[facet(args::subcommand)]
    pub command: SymbolUsageCommand,
}

impl SymbolUsageArgs {
    /// # Errors
    ///
    /// Returns an error when usage analysis fails.
    pub fn invoke(self) -> eyre::Result<CliOutput> {
        let invocation_dir = std::env::current_dir()?;
        self.invoke_in(&invocation_dir)
    }

    /// Run the usage subcommand against an explicit invocation directory.
    ///
    /// # Errors
    ///
    /// Returns an error when the selected usage command fails.
    pub fn invoke_in(self, invocation_dir: &Path) -> eyre::Result<CliOutput> {
        match self.command {
            SymbolUsageCommand::List(args) => args.invoke_in(invocation_dir),
        }
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum SymbolUsageCommand {
    /// List all statically resolved usages of a symbol.
    List(SymbolUsageListArgs),
}
