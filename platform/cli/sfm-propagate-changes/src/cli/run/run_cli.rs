use super::RunClientArgs;
use super::RunCompileArgs;
use super::RunDataArgs;
use super::RunHotswapArgs;
use super::RunServerArgs;
use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use facet::Facet;
use figue as args;

/// Arguments for clean-slate build-backed run commands.
#[derive(Facet, Debug)]
pub struct RunArgs {
    /// Run subcommand.
    #[facet(args::subcommand)]
    pub command: RunCommand,
}

impl RunArgs {
    /// # Errors
    ///
    /// Returns an error if the selected run command fails.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        self.command.invoke(cancellation_token, homes)
    }
}

/// Build and run catalog projections with their native loader.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum RunCommand {
    /// Compile all Java source sets without launching userdev or tests
    Compile(RunCompileArgs),
    /// Launch a development or release projection with its native loader
    Client(RunClientArgs),
    /// Launch the projection's server run config
    Server(RunServerArgs),
    /// Launch the projection's datagen run config
    Data(RunDataArgs),
    /// Explicitly apply a successful compile snapshot to a matching live client session
    Hotswap(RunHotswapArgs),
}

impl RunCommand {
    /// # Errors
    ///
    /// This function will return an error if planning, building, or launching fails.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        match self {
            RunCommand::Compile(args) => args.invoke(cancellation_token, homes),
            RunCommand::Client(args) => args.invoke(cancellation_token, homes),
            RunCommand::Server(args) => args.invoke(cancellation_token, homes),
            RunCommand::Data(args) => args.invoke(cancellation_token, homes),
            RunCommand::Hotswap(args) => args.invoke(cancellation_token, homes),
        }
    }
}
