#![allow(clippy::doc_markdown)]

use super::CurseforgeReleaseAmendArgs;
use super::CurseforgeReleaseCheckArgs;
use super::CurseforgeReleaseNowArgs;
use super::CurseforgeReleaseValidateArgs;
use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for CurseForge release operations.
#[derive(Facet, Debug)]
pub struct CurseforgeReleaseArgs {
    /// Release subcommand.
    #[facet(args::subcommand)]
    pub command: CurseforgeReleaseCommand,
}

impl CurseforgeReleaseArgs {
    /// # Errors
    ///
    /// Returns an error if the selected CurseForge release operation fails.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        self.command.invoke(app_home)
    }
}

/// `CurseForge` release subcommands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CurseforgeReleaseCommand {
    /// Verify computed release metadata against historical project uploads
    Check(CurseforgeReleaseCheckArgs),
    /// Validate remote downloadable files against local release jars by hash
    Validate(CurseforgeReleaseValidateArgs),
    /// Upload each release jar to `CurseForge` according to release-process rules
    Now(CurseforgeReleaseNowArgs),
    /// Amend changelog for latest file per MC version in current release jars
    Amend(CurseforgeReleaseAmendArgs),
}

impl CurseforgeReleaseCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        match self {
            Self::Check(args) => args.invoke(app_home),
            Self::Validate(args) => args.invoke(app_home),
            Self::Now(args) => args.invoke(app_home),
            Self::Amend(args) => args.invoke(app_home),
        }
    }
}
