use crate::paths::{APP_CACHE_ENV_VAR, APP_HOME, CACHE_DIR, CACHE_ROOT_FILE};
use eyre::bail;
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
        if std::env::var_os(APP_CACHE_ENV_VAR).is_some()
            || APP_HOME.file_path(CACHE_ROOT_FILE).exists()
        {
            bail!(
                "cache clean refuses a custom cache root; remove only verified entries explicitly"
            );
        }
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
