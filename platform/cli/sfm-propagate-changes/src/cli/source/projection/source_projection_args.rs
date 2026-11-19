use super::SourceProjectionCommand;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use eyre::Result;
use facet::Facet;
use figue as args;
use std::path::Path;

/// Work with projections generated from the core Liquid templates.
#[derive(Debug, Facet)]
pub struct SourceProjectionArgs {
    #[facet(args::subcommand)]
    pub command: SourceProjectionCommand,
}

impl SourceProjectionArgs {
    pub(in crate::cli::source) fn invoke_in(
        self,
        invocation_dir: &Path,
        cancellation: &CancellationToken,
    ) -> Result<CliOutput> {
        match self.command {
            SourceProjectionCommand::Check(args) => args.invoke_in(invocation_dir, cancellation),
        }
    }
}
