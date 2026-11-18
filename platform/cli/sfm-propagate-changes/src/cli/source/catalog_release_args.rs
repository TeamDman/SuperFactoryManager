use super::catalog_release_command::CatalogReleaseCommand;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use eyre::Result;
use facet::Facet;
use figue as args;
use std::path::Path;

#[derive(Debug, Facet)]
pub struct CatalogReleaseArgs {
    #[facet(args::subcommand)]
    pub command: CatalogReleaseCommand,
}

impl CatalogReleaseArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        match self.command {
            CatalogReleaseCommand::Plan(args) => args.invoke(invocation_dir, cancellation),
        }
    }
}
