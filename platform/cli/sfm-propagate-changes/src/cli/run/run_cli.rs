use super::RunClientArgs;
use super::RunCompileArgs;
use super::RunDataArgs;
use super::RunHotswapArgs;
use super::RunServerArgs;
use crate::cancellation::CancellationToken;
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
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        self.command.invoke(cancellation_token)
    }
}

/// Build Rust-owned project outputs and launch a Forge userdev run config.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum RunCommand {
    /// Compile all Java source sets without launching userdev or tests
    Compile(RunCompileArgs),
    /// Launch the Forge client userdev run config
    Client(RunClientArgs),
    /// Launch the Forge server userdev run config
    Server(RunServerArgs),
    /// Launch the Forge datagen userdev run config
    Data(RunDataArgs),
    /// Compile changed classes and hotswap them into a running JDWP-enabled client
    Hotswap(RunHotswapArgs),
}

impl RunCommand {
    /// # Errors
    ///
    /// This function will return an error if planning, building, or launching fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self {
            RunCommand::Compile(args) => args.invoke(cancellation_token),
            RunCommand::Client(args) => args.invoke(cancellation_token),
            RunCommand::Server(args) => args.invoke(cancellation_token),
            RunCommand::Data(args) => args.invoke(cancellation_token),
            RunCommand::Hotswap(args) => args.invoke(cancellation_token),
        }
    }
}
