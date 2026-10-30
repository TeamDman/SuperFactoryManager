use super::CacheCleanArgs;
use super::CacheOpenArgs;
use super::CachePathArgs;
use super::CacheReportArgs;
use super::CacheRootArgs;
use facet::Facet;
use figue as args;

/// Arguments for cache directory commands.
#[derive(Facet, Debug)]
pub struct CacheArgs {
    /// Cache subcommand.
    #[facet(args::subcommand)]
    pub command: CacheCommand,
}

impl CacheArgs {
    /// # Errors
    ///
    /// Returns an error if the selected cache command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Cache directory commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CacheCommand {
    /// Show the cache directory path
    Path(CachePathArgs),
    /// Show or persist the shared toolchain cache root
    Root(CacheRootArgs),
    /// Report checkout expectations and possible unused entries without deleting anything
    Report(CacheReportArgs),
    /// Open the cache directory in the file explorer
    Open(CacheOpenArgs),
    /// Clean the cache directory
    Clean(CacheCleanArgs),
}

impl CacheCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            CacheCommand::Path(args) => args.invoke(),
            CacheCommand::Root(args) => args.invoke(),
            CacheCommand::Report(args) => args.invoke(),
            CacheCommand::Open(args) => args.invoke(),
            CacheCommand::Clean(args) => args.invoke(),
        }
    }
}
