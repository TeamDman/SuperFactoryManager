use crate::paths::CacheHomeSource;
use crate::paths::SelectedCacheHome;
use eyre::bail;
use facet::Facet;
use std::fs::remove_dir_all;
use tracing::info;

/// Arguments for cleaning the cache directory.
#[derive(Facet, Debug)]
pub struct CacheCleanArgs;

impl CacheCleanArgs {
    /// # Errors
    ///
    /// Returns an error if the cache directory cannot be removed.
    pub fn invoke(self, selection: &SelectedCacheHome) -> eyre::Result<()> {
        if selection.source != CacheHomeSource::PlatformDefault {
            bail!(
                "cache clean refuses a custom cache root; remove only verified entries explicitly"
            );
        }
        let path = &selection.home.0;
        if path.exists() {
            remove_dir_all(path)?;
            info!("Cleaned cache directory: {}", path.display());
        } else {
            info!("Cache directory does not exist: {}", path.display());
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::paths::AppHome;
    use crate::paths::CACHE_ROOT_FILE;
    use crate::paths::CacheHome;
    use std::fs;

    #[test]
    fn deleting_a_setting_never_authorizes_cleaning_its_selected_custom_cache() {
        let temporary = tempfile::tempdir().unwrap();
        let home = AppHome(temporary.path().join("settings"));
        home.ensure_dir().unwrap();
        let cache = temporary.path().join("custom-cache");
        fs::create_dir(&cache).unwrap();
        fs::write(cache.join("preserve.txt"), "cached bytes").unwrap();
        let setting = home.file_path(CACHE_ROOT_FILE);
        fs::write(&setting, cache.display().to_string()).unwrap();
        let selection = SelectedCacheHome {
            home: CacheHome(cache.clone()),
            source: CacheHomeSource::PersistentSetting,
        };
        fs::remove_file(setting).unwrap();
        let error = CacheCleanArgs.invoke(&selection).unwrap_err();
        assert!(error.to_string().contains("refuses a custom cache root"));
        assert_eq!(
            fs::read_to_string(cache.join("preserve.txt")).unwrap(),
            "cached bytes"
        );
    }
}
