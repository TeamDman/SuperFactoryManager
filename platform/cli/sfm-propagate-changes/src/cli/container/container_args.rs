use super::ContainerCommand;
use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::output::CliOutput;
use crate::container_build::execute;
use crate::container_build::execute_session;
use crate::container_build::execute_test;
use facet::Facet;
use figue as args;
use std::path::Path;

#[derive(Debug, Facet)]
pub struct ContainerArgs {
    /// Local container commands.
    #[facet(args::subcommand)]
    pub command: ContainerCommand,
}

impl ContainerArgs {
    /// Execute the selected local container request.
    /// # Errors
    /// Rejects invalid inputs, unsafe paths, failed preparation or engine failures.
    pub fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation: &Path,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        match self.command {
            ContainerCommand::Session(args) => {
                let request = args.into_request(invocation)?;
                Ok(CliOutput::facet(execute_session(&request, cancellation)?))
            }
            ContainerCommand::Test(args) => {
                let report = execute_test(&args.into_request(invocation)?, cancellation)?;
                let status = u8::from(!report.accepted);
                Ok(CliOutput::facet_with_status(report, status))
            }
            ContainerCommand::Build(args) => {
                let report = execute(&args.into_request(invocation, homes)?, cancellation)?;
                let status = u8::from(!report.missing.is_empty()) * 2;
                Ok(CliOutput::facet_with_status(report, status))
            }
        }
    }
}
