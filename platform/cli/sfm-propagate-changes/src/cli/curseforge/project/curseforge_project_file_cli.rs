#![allow(clippy::doc_markdown)]

use super::CurseforgeProjectFileListArgs;
use facet::Facet;
use figue as args;

/// Arguments for CurseForge project file operations.
#[derive(Facet, Debug)]
pub struct CurseforgeProjectFileArgs {
    /// File subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeProjectFileCommand,
}

impl CurseforgeProjectFileArgs {
    /// # Errors
    ///
    /// Returns an error if the selected file command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// `CurseForge` project file subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeProjectFileCommand {
    /// List files for a project
    List(CurseforgeProjectFileListArgs),
}

impl CurseforgeProjectFileCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::List(args) => args.invoke(),
        }
    }
}
