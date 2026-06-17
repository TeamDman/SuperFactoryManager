use super::RunClientArgs;
use super::RunClientPuppetArgs;
use super::RunClientSmokeArgs;
use super::RunDataArgs;
use super::RunGameTestServerArgs;
use super::RunServerArgs;
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
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
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
}

impl RunCommand {
    /// # Errors
    ///
    /// This function will return an error if planning, building, or launching fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            RunCommand::Client(args) => args.invoke(),
            RunCommand::ClientSmoke(args) => args.invoke(),
            RunCommand::ClientPuppet(args) => args.invoke(),
            RunCommand::Server(args) => args.invoke(),
            RunCommand::Data(args) => args.invoke(),
            RunCommand::GameTestServer(args) => args.invoke(),
        }
    }
}
