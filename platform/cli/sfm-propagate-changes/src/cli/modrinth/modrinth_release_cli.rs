use super::ModrinthReleaseAmendArgs;
use super::ModrinthReleaseCheckArgs;
use super::ModrinthReleaseNowArgs;
use super::ModrinthReleaseValidateArgs;
use facet::Facet;
use figue as args;

/// Arguments for Modrinth release operations.
#[derive(Facet, Debug)]
pub struct ModrinthReleaseArgs {
    /// Release subcommand.
    #[facet(args::subcommand)]
    pub command: ModrinthReleaseCommand,
}

impl ModrinthReleaseArgs {
    /// # Errors
    ///
    /// Returns an error if the selected release operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Modrinth release subcommands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum ModrinthReleaseCommand {
    /// Verify computed release metadata against historical project versions
    Check(ModrinthReleaseCheckArgs),
    /// Validate remote downloadable jars against local release jars by hash
    Validate(ModrinthReleaseValidateArgs),
    /// Create new Modrinth versions for each release jar
    Now(ModrinthReleaseNowArgs),
    /// Amend changelog for latest version per MC in current release jars
    Amend(ModrinthReleaseAmendArgs),
}

impl ModrinthReleaseCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Check(args) => args.invoke(),
            Self::Validate(args) => args.invoke(),
            Self::Now(args) => args.invoke(),
            Self::Amend(args) => args.invoke(),
        }
    }
}
