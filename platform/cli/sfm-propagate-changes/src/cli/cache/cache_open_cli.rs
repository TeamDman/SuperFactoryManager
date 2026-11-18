use crate::paths::CacheHome;
use facet::Facet;

/// Arguments for opening the cache directory.
#[derive(Facet, Debug)]
pub struct CacheOpenArgs;

impl CacheOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the cache directory cannot be created or opened.
    pub fn invoke(self) -> eyre::Result<()> {
        let path = &CacheHome::current()?.0;
        if !path.exists() {
            std::fs::create_dir_all(path)?;
        }
        open::that(path)?;
        Ok(())
    }
}
