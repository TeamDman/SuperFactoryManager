use super::AppHome;
use super::CacheHomeSource;
use super::SelectedCacheHome;
use directories_next::ProjectDirs;
use eyre::Context;
use eyre::ensure;
use std::env;
use std::fs::read_to_string;
use std::fs::symlink_metadata;
use std::ops;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;
use std::path::Path;
use std::path::PathBuf;

pub const CACHE_ROOT_FILE: &str = "cache_root.txt";
pub const CACHE_ROOT_MARKER: &str = ".sfm-propagate-changes-cache-root";
pub const CACHE_ROOT_MARKER_CONTENT: &str = "sfm-propagate-changes cache root v1\n";

/// Interpret the lockfile's cache marker and either separator independently of
/// the host OS. Reject traversal and drive prefixes before building a path.
pub(crate) fn portable_cache_relative(path: &Path) -> eyre::Result<Option<PathBuf>> {
    let Some(text) = path.to_str() else {
        return Ok(None);
    };
    let Some(suffix) = text.strip_prefix("$sfm-cache") else {
        return Ok(None);
    };
    if suffix.is_empty() {
        return Ok(Some(PathBuf::new()));
    }
    let Some(rest) = suffix.strip_prefix(['/', '\\']) else {
        return Ok(None);
    };
    let mut relative = PathBuf::new();
    for part in rest.split(['/', '\\']) {
        ensure!(
            !part.is_empty()
                && part != "."
                && part != ".."
                && !part.contains(':')
                && !part.contains('\0'),
            "invalid portable cache path `{}`",
            path.display()
        );
        relative.push(part);
    }
    Ok(Some(relative))
}

/// Helper that resolves the application cache directory.
#[derive(Clone, Debug)]
pub struct CacheHome(pub PathBuf);

impl CacheHome {
    /// Resolve the `CacheHome` according to:
    /// * If [`super::APP_CACHE_ENV_VAR`] env var is set, use that directory
    /// * Otherwise use the persisted `cache root set` choice
    /// * Otherwise use the platform `ProjectDirs::cache_dir()`
    ///
    /// # Errors
    ///
    /// This function will return an error if the cache directory cannot be determined.
    pub(crate) fn resolve(app_home: &AppHome) -> eyre::Result<SelectedCacheHome> {
        resolve_selected_cache_root(
            env::var_os(super::APP_CACHE_ENV_VAR).map(PathBuf::from),
            &app_home.file_path(CACHE_ROOT_FILE),
            || {
                ProjectDirs::from("", "teamdman", super::APP_CACHE_DIR_NAME)
                    .map(|dirs| dirs.cache_dir().to_path_buf())
                    .ok_or_else(|| eyre::eyre!("Could not determine cache directory"))
            },
        )
    }
}

fn resolve_selected_cache_root(
    override_dir: Option<PathBuf>,
    config: &Path,
    default: impl FnOnce() -> eyre::Result<PathBuf>,
) -> eyre::Result<SelectedCacheHome> {
    if let Some(path) = override_dir {
        ensure!(
            path.is_absolute(),
            "{} must be an absolute path",
            super::APP_CACHE_ENV_VAR
        );
        return Ok(SelectedCacheHome {
            home: CacheHome(path),
            source: CacheHomeSource::EnvironmentOverride,
        });
    }
    if config.exists() {
        let content = read_to_string(config)
            .wrap_err_with(|| format!("Cannot read configured cache root: {}", config.display()))?;
        let path = PathBuf::from(content.trim());
        ensure!(
            path.is_absolute() && path.is_dir(),
            "Configured cache root is not an existing absolute directory: {}",
            path.display()
        );
        let metadata = symlink_metadata(&path)?;
        ensure!(
            !metadata.file_type().is_symlink(),
            "Configured cache root must not be a link: {}",
            path.display()
        );
        #[cfg(windows)]
        ensure!(
            metadata.file_attributes() & 0x400 == 0,
            "Configured cache root must not be a reparse point: {}",
            path.display()
        );
        ensure!(
            read_to_string(path.join(CACHE_ROOT_MARKER))? == CACHE_ROOT_MARKER_CONTENT,
            "Configured cache root has no valid SFM ownership marker: {}",
            path.display()
        );
        return Ok(SelectedCacheHome {
            home: CacheHome(path),
            source: CacheHomeSource::PersistentSetting,
        });
    }
    Ok(SelectedCacheHome {
        home: CacheHome(default()?),
        source: CacheHomeSource::PlatformDefault,
    })
}

impl ops::Deref for CacheHome {
    type Target = Path;

    fn deref(&self) -> &Self::Target {
        self.0.as_path()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn portable_cache_relative_accepts_both_separators_and_rejects_escapes() {
        for path in [
            "$sfm-cache/maven/example/locked.jar",
            r"$sfm-cache\maven\example\locked.jar",
            r"$sfm-cache\maven/example\locked.jar",
        ] {
            assert_eq!(
                portable_cache_relative(Path::new(path)).unwrap(),
                Some(PathBuf::from("maven").join("example").join("locked.jar"))
            );
        }
        for path in [
            "$sfm-cache/../outside",
            r"$sfm-cache\..\outside",
            r"$sfm-cache\maven/../outside",
            "$sfm-cache//outside",
            r"$sfm-cache\C:\outside",
        ] {
            assert!(portable_cache_relative(Path::new(path)).is_err(), "{path}");
        }
        assert_eq!(
            portable_cache_relative(Path::new("$sfm-cache-other/file")).unwrap(),
            None
        );
    }

    #[test]
    fn environment_overrides_persistent_choice_and_unset_restores_default() {
        let temporary = tempfile::tempdir().unwrap();
        let default = temporary.path().join("platform-default");
        let configured = temporary.path().join("configured");
        let environment = temporary.path().join("environment");
        std::fs::create_dir(&configured).unwrap();
        std::fs::write(
            configured.join(CACHE_ROOT_MARKER),
            CACHE_ROOT_MARKER_CONTENT,
        )
        .unwrap();
        let config = temporary.path().join("cache_root.txt");
        std::fs::write(&config, configured.display().to_string()).unwrap();
        let selected =
            resolve_selected_cache_root(None, &config, || panic!("default should stay lazy"))
                .unwrap();
        assert_eq!(selected.home.0, configured);
        assert_eq!(selected.source, CacheHomeSource::PersistentSetting);
        let selected = resolve_selected_cache_root(Some(environment.clone()), &config, || {
            panic!("default should stay lazy")
        })
        .unwrap();
        assert_eq!(selected.home.0, environment);
        assert_eq!(selected.source, CacheHomeSource::EnvironmentOverride);
        std::fs::remove_file(config).unwrap();
        let selected =
            resolve_selected_cache_root(None, &temporary.path().join("cache_root.txt"), || {
                Ok(default.clone())
            })
            .unwrap();
        assert_eq!(selected.home.0, default);
        assert_eq!(selected.source, CacheHomeSource::PlatformDefault);
    }

    #[test]
    fn invalid_persistent_choice_fails_closed() {
        let temporary = tempfile::tempdir().unwrap();
        let config = temporary.path().join("cache_root.txt");
        std::fs::write(&config, temporary.path().display().to_string()).unwrap();
        assert!(
            resolve_selected_cache_root(None, &config, || Ok(temporary.path().join("default")))
                .is_err()
        );
    }
}
