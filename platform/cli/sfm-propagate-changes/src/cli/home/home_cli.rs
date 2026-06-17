use super::HomeOpenArgs;
use super::HomePathArgs;
use facet::Facet;
use figue as args;

/// Arguments for home directory commands.
#[derive(Facet, Debug)]
pub struct HomeArgs {
    /// Home subcommand.
    #[facet(args::subcommand)]
    pub command: HomeCommand,
}

impl HomeArgs {
    /// # Errors
    ///
    /// Returns an error if the selected home command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Home directory commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum HomeCommand {
    /// Show the home directory path
    Path(HomePathArgs),
    /// Open the home directory in the file explorer
    Open(HomeOpenArgs),
}

impl HomeCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            HomeCommand::Path(args) => args.invoke(),
            HomeCommand::Open(args) => args.invoke(),
        }
    }
}
