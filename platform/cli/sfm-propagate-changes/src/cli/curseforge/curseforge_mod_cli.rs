use super::CurseforgeModFilesArgs;
use super::CurseforgeModSearchArgs;
use facet::Facet;
use figue as args;

/// Read-only `CurseForge` mod discovery commands.
#[derive(Facet, Debug)]
pub struct CurseforgeModArgs {
    /// Mod discovery subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeModCommand,
}

impl CurseforgeModArgs {
    /// # Errors
    ///
    /// Returns an error when the selected `CurseForge` query fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Read-only `CurseForge` mod discovery operations.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeModCommand {
    /// Search projects by name, Minecraft version, and loader.
    Search(CurseforgeModSearchArgs),
    /// List exact-version files for a selected project.
    Files(CurseforgeModFilesArgs),
}

impl CurseforgeModCommand {
    /// # Errors
    ///
    /// Returns an error when the selected `CurseForge` query fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Search(args) => args.invoke(),
            Self::Files(args) => args.invoke(),
        }
    }
}
