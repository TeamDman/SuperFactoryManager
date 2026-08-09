use super::LoaderListArgs;
use facet::Facet;
use figue as args;

/// Arguments for Prism loader metadata commands.
#[derive(Facet, Debug)]
pub struct LoaderArgs {
    /// Loader subcommand.
    #[facet(args::subcommand)]
    pub command: LoaderCommand,
}

impl LoaderArgs {
    /// # Errors
    ///
    /// Returns an error if the selected loader command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Prism loader metadata commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum LoaderCommand {
    /// List pinned, recommended, and latest Prism loader versions for selected worktrees
    List(LoaderListArgs),
}

impl LoaderCommand {
    /// # Errors
    ///
    /// This function will return an error if loader metadata cannot be listed.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            LoaderCommand::List(args) => args.invoke(),
        }
    }
}
