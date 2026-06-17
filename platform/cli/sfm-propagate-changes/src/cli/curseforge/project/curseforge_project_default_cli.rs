#![allow(clippy::doc_markdown)]

use super::CurseforgeProjectDefaultSetArgs;
use super::CurseforgeProjectDefaultShowArgs;
use facet::Facet;
use figue as args;

/// Arguments for CurseForge default-project operations.
#[derive(Facet, Debug)]
pub struct CurseforgeProjectDefaultArgs {
    /// Default-project subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeProjectDefaultCommand,
}

impl CurseforgeProjectDefaultArgs {
    /// # Errors
    ///
    /// Returns an error if the selected default-project command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// `CurseForge` project default subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeProjectDefaultCommand {
    /// Set default project ID
    Set(CurseforgeProjectDefaultSetArgs),
    /// Show default project ID (falls back to built-in default)
    Show(CurseforgeProjectDefaultShowArgs),
}

impl CurseforgeProjectDefaultCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Set(args) => args.invoke(),
            Self::Show(args) => args.invoke(),
        }
    }
}
