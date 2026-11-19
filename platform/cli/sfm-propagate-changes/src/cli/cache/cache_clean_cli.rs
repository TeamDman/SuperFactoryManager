use crate::paths::CacheHomeSource;
use crate::paths::SelectedCacheHome;
use eyre::bail;
use eyre::ensure;
use facet::Facet;
use std::fs::read_dir;
use std::fs::remove_dir_all;
use std::fs::remove_file;
use std::fs::symlink_metadata;
use std::io::ErrorKind;
#[cfg(windows)]
use std::os::windows::fs::FileTypeExt as _;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt as _;
use tracing::info;

/// Arguments for cleaning the cache directory.
#[derive(Facet, Debug)]
pub struct CacheCleanArgs;

impl CacheCleanArgs {
    /// # Errors
    ///
    /// Returns an error if the platform cache cannot be inspected or cleaned.
    pub fn invoke(self, selection: &SelectedCacheHome) -> eyre::Result<()> {
        if selection.source != CacheHomeSource::PlatformDefault {
            bail!(
                "cache clean refuses a custom cache root; remove only verified entries explicitly"
            );
        }
        let path = &selection.home.0;
        ensure!(
            path.is_absolute() && path.parent().is_some(),
            "cache clean requires an absolute non-root cache directory"
        );
        let metadata = match symlink_metadata(path) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == ErrorKind::NotFound => {
                info!("Cache directory does not exist: {}", path.display());
                return Ok(());
            }
            Err(error) => return Err(error.into()),
        };
        ensure!(
            metadata.is_dir() && !metadata.file_type().is_symlink(),
            "cache clean requires an ordinary cache directory: {}",
            path.display()
        );
        #[cfg(windows)]
        ensure!(
            metadata.file_attributes() & 0x400 == 0,
            "cache clean refuses a reparse-point cache directory: {}",
            path.display()
        );
        for entry in read_dir(path)? {
            let entry = entry?;
            if entry
                .file_name()
                .to_str()
                .is_some_and(|name| name.eq_ignore_ascii_case("logs"))
            {
                continue;
            }
            let kind = entry.file_type()?;
            let directory = kind.is_dir();
            #[cfg(windows)]
            let directory = directory || kind.is_symlink_dir();
            if directory {
                remove_dir_all(entry.path())?;
            } else {
                remove_file(entry.path())?;
            }
        }
        info!(
            "Cleaned cache entries and retained logs: {}",
            path.display()
        );
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
    fn cleaning_platform_cache_retains_automatic_logs() -> eyre::Result<()> {
        let temporary = tempfile::tempdir()?;
        let cache = temporary.path().join("cache");
        let logs = cache.join("logs");
        let run = logs.join("run.ndjson");
        let artifacts = cache.join("artifacts");
        let loose_cache_file = cache.join("index.dat");
        fs::create_dir_all(&logs)?;
        fs::create_dir_all(&artifacts)?;
        fs::write(&run, "run history")?;
        fs::write(&loose_cache_file, "cached bytes")?;
        let selection = SelectedCacheHome {
            home: CacheHome(cache),
            source: CacheHomeSource::PlatformDefault,
        };
        CacheCleanArgs.invoke(&selection)?;
        assert_eq!(fs::read_to_string(run)?, "run history");
        assert!(!artifacts.exists());
        assert!(!loose_cache_file.exists());
        Ok(())
    }

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
