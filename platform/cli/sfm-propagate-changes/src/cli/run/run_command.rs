use crate::cli::jar::jar_build_command::JarBuildCommand;
use facet::Facet;

/// Build Rust-owned project outputs and launch a Forge userdev run config.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum RunCommand {
    /// Launch the Forge client userdev run config
    Client {
        /// Build and launch options
        #[facet(flatten)]
        command: JarBuildCommand,
    },
    /// Launch the Forge server userdev run config
    Server {
        /// Build and launch options
        #[facet(flatten)]
        command: JarBuildCommand,
    },
    /// Launch the Forge datagen userdev run config
    Data {
        /// Build and launch options
        #[facet(flatten)]
        command: JarBuildCommand,
    },
    /// Launch the Forge game test server userdev run config
    #[facet(rename = "game-test-server")]
    GameTestServer {
        /// Build and launch options
        #[facet(flatten)]
        command: JarBuildCommand,
    },
}

impl RunCommand {
    /// # Errors
    ///
    /// This function will return an error if planning, building, or launching fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            RunCommand::Client { command } => {
                crate::cli::jar::jar_build_command::invoke_run_client(command)
            }
            RunCommand::Server { command } => {
                crate::cli::jar::jar_build_command::invoke_run_server(command)
            }
            RunCommand::Data { command } => {
                crate::cli::jar::jar_build_command::invoke_run_data(command)
            }
            RunCommand::GameTestServer { command } => {
                crate::cli::jar::jar_build_command::invoke_run_game_test_server(command)
            }
        }
    }
}
