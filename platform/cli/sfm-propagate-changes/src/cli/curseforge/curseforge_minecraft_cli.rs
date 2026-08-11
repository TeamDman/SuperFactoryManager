#![allow(clippy::doc_markdown)]

use super::CurseforgeMinecraftVersionArgs;
use facet::Facet;
use figue as args;

/// Arguments for CurseForge Minecraft metadata operations.
#[derive(Facet, Debug)]
pub struct CurseforgeMinecraftArgs {
    /// Minecraft subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeMinecraftCommand,
}

impl CurseforgeMinecraftArgs {
    /// # Errors
    ///
    /// Returns an error if the selected Minecraft metadata command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// `CurseForge` minecraft subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeMinecraftCommand {
    /// Minecraft version operations
    Version(CurseforgeMinecraftVersionArgs),
}

impl CurseforgeMinecraftCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Version(args) => args.invoke(),
        }
    }
}
