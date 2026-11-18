use super::ModrinthReleaseArgs;
use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for Modrinth release-related commands.
#[derive(Facet, Debug)]
pub struct ModrinthArgs {
    /// Modrinth subcommand.
    #[facet(args::subcommand)]
    pub command: ModrinthCommand,
}

impl ModrinthArgs {
    /// # Errors
    ///
    /// Returns an error if the selected Modrinth command fails.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        self.command.invoke(app_home)
    }
}

/// Modrinth release-related commands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ModrinthCommand {
    /// Release metadata validation and upload operations
    Release(ModrinthReleaseArgs),
}

impl ModrinthCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        match self {
            Self::Release(args) => args.invoke(app_home),
        }
    }
}
