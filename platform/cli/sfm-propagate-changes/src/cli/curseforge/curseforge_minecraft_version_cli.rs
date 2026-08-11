#![allow(clippy::doc_markdown)]

use super::CurseforgeMinecraftVersionListArgs;
use facet::Facet;
use figue as args;

/// Arguments for CurseForge Minecraft version operations.
#[derive(Facet, Debug)]
pub struct CurseforgeMinecraftVersionArgs {
    /// Version subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeMinecraftVersionCommand,
}

impl CurseforgeMinecraftVersionArgs {
    /// # Errors
    ///
    /// Returns an error if the selected Minecraft version command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// `CurseForge` minecraft version subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeMinecraftVersionCommand {
    /// List Minecraft game versions from `CurseForge`
    List(CurseforgeMinecraftVersionListArgs),
}

impl CurseforgeMinecraftVersionCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::List(args) => args.invoke(),
        }
    }
}
