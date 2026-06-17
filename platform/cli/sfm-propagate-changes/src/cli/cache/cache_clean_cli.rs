use crate::paths::CACHE_DIR;
use facet::Facet;
use tracing::info;

/// Arguments for cleaning the cache directory.
#[derive(Facet, Debug)]
pub struct CacheCleanArgs;

impl CacheCleanArgs {
    /// # Errors
    ///
    /// Returns an error if the cache directory cannot be removed.
    pub fn invoke(self) -> eyre::Result<()> {
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
