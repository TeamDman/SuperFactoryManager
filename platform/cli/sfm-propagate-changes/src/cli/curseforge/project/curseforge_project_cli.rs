#![allow(clippy::doc_markdown)]

use super::CurseforgeProjectDefaultArgs;
use super::CurseforgeProjectFileArgs;
use facet::Facet;
use figue as args;

/// Arguments for CurseForge project operations.
#[derive(Facet, Debug)]
pub struct CurseforgeProjectArgs {
    /// Project subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeProjectCommand,
}

impl CurseforgeProjectArgs {
    /// # Errors
    ///
    /// Returns an error if the selected project command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// `CurseForge` project subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeProjectCommand {
    /// Project default configuration commands
    Default(CurseforgeProjectDefaultArgs),
    /// Project file operations
    File(CurseforgeProjectFileArgs),
}

impl CurseforgeProjectCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Default(args) => args.invoke(),
            Self::File(args) => args.invoke(),
        }
    }
}
