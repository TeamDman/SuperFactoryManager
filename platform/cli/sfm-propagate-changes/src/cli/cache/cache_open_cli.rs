use crate::paths::CacheHome;
use facet::Facet;
use std::fs::create_dir_all;

/// Arguments for opening the cache directory.
#[derive(Facet, Debug)]
pub struct CacheOpenArgs;

impl CacheOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the cache directory cannot be created or opened.
    pub fn invoke(self, cache_home: &CacheHome) -> eyre::Result<()> {
        let path = &cache_home.0;
        if !path.exists() {
            create_dir_all(path)?;
        }
        open::that(path)?;
        Ok(())
    }
}
