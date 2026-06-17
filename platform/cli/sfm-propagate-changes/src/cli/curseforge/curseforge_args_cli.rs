use super::CurseforgeMinecraftArgs;
use super::CurseforgeProjectArgs;
use super::CurseforgeReleaseArgs;
use facet::Facet;
use figue as args;

/// Arguments for `CurseForge` release and file related commands.
#[derive(Facet, Debug)]
pub struct CurseforgeArgs {
    /// `CurseForge` subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeCommand,
}

impl CurseforgeArgs {
    /// # Errors
    ///
    /// Returns an error if the selected `CurseForge` command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// `CurseForge` release and file related commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeCommand {
    /// Project-related operations
    Project(CurseforgeProjectArgs),
    /// Minecraft metadata operations
    Minecraft(CurseforgeMinecraftArgs),
    /// Release metadata validation and upload operations
    Release(CurseforgeReleaseArgs),
}

impl CurseforgeCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Project(args) => args.invoke(),
            Self::Minecraft(args) => args.invoke(),
            Self::Release(args) => args.invoke(),
        }
    }
}
