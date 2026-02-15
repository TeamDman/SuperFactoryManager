use crate::paths::CACHE_DIR;
use facet::Facet;
use tracing::info;

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
            CacheCommand::Path => {
                println!("{}", CACHE_DIR.0.display());
                Ok(())
            }
            CacheCommand::Open => {
                let path = &CACHE_DIR.0;
                if !path.exists() {
                    std::fs::create_dir_all(path)?;
                }
                open::that(path)?;
                Ok(())
            }
            CacheCommand::Clean => {
                let path = &CACHE_DIR.0;
                if path.exists() {
                    std::fs::remove_dir_all(path)?;
                    info!("Cleaned cache directory: {}", path.display());
                } else {
                    info!("Cache directory does not exist: {}", path.display());
                }
                Ok(())
            }
        }
    }
}
