use facet::Facet;

/// Cache directory commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum CacheCommand {
    /// Show the cache directory path
    Path,
    /// Open the cache directory in the file explorer
    Open,
    /// Clean the cache directory
    Clean,
}

impl CacheCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            CacheCommand::Path => super::cache_path_command::invoke(),
            CacheCommand::Open => super::cache_open_command::invoke(),
            CacheCommand::Clean => super::cache_clean_command::invoke(),
        }
    }
}
