use super::RunClientArgs;
use super::RunClientPuppetArgs;
use super::RunClientSmokeArgs;
use super::RunDataArgs;
use super::RunGameTestServerArgs;
use super::RunServerArgs;
use super::RunTestArgs;
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
    /// Launch the Forge client userdev run config
    Client(RunClientArgs),
    /// Launch the Forge client userdev run config and exit when the title screen opens
    #[facet(rename = "client-smoke")]
    ClientSmoke(RunClientSmokeArgs),
    /// Launch the Forge client userdev run config and run SFM game tests in an integrated client
    #[facet(rename = "client-puppet")]
    ClientPuppet(RunClientPuppetArgs),
    /// Launch the Forge server userdev run config
    Server(RunServerArgs),
    /// Launch the Forge datagen userdev run config
    Data(RunDataArgs),
    /// Launch the Forge game test server userdev run config
    #[facet(rename = "game-test-server")]
    GameTestServer(RunGameTestServerArgs),
    /// Compile and run the Java JUnit test source set
    Test(RunTestArgs),
}

impl RunCommand {
    /// # Errors
    ///
    /// This function will return an error if planning, building, or launching fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self {
            RunCommand::Client(args) => args.invoke(cancellation_token),
            RunCommand::ClientSmoke(args) => args.invoke(cancellation_token),
            RunCommand::ClientPuppet(args) => args.invoke(cancellation_token),
            RunCommand::Server(args) => args.invoke(cancellation_token),
            RunCommand::Data(args) => args.invoke(cancellation_token),
            RunCommand::GameTestServer(args) => args.invoke(cancellation_token),
            RunCommand::Test(args) => args.invoke(cancellation_token),
        }
    }
}
