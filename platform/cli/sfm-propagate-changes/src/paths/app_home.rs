use directories_next::ProjectDirs;
use eyre::bail;
use std::env;
use std::ops::Deref;
use std::path::Path;
use std::path::PathBuf;
use std::sync::LazyLock;
use tracing::warn;

/// Cached `AppHome` instance
pub static APP_HOME: LazyLock<AppHome> = LazyLock::new(|| match AppHome::resolve() {
    Ok(a) => a,
    Err(e) => {
        warn!("Warning: failed to resolve app home: {}", e);
        AppHome(std::env::current_dir().unwrap_or_else(|_| PathBuf::from(".")))
    }
});

/// Helper that holds the application home directory and provides helper methods
#[derive(Clone, Debug, PartialEq)]
pub struct AppHome(pub PathBuf);

impl AppHome {
    /// Returns a `PathBuf` for a filename under the app config dir
    #[must_use]
    pub fn file_path(&self, name: &str) -> PathBuf {
        self.0.join(name)
    }

    /// Create directories for the app home if needed
    ///
    /// # Errors
    ///
    /// This function will return an error if creating the directories fails.
    pub fn ensure_dir(&self) -> eyre::Result<()> {
        std::fs::create_dir_all(&self.0)?;
        Ok(())
    }

    /// Resolve the `AppHome` according to the same rules used previously:
    /// * If [`super::APP_HOME_ENV_VAR`] env var is set, use that directory
    /// * Otherwise use the platform `ProjectDirs::config_dir()`
    ///
    /// # Errors
    ///
    /// This function will return an error if the config directory cannot be determined.
    pub fn resolve() -> eyre::Result<AppHome> {
        if let Ok(override_dir) = env::var(super::APP_HOME_ENV_VAR) {
            return Ok(AppHome(PathBuf::from(override_dir)));
        }
        if let Some(project_dirs) = ProjectDirs::from("", "teamdman", super::APP_HOME_DIR_NAME) {
            Ok(AppHome(project_dirs.config_dir().to_path_buf()))
        } else {
            bail!("Could not determine config directory")
        }
    }

    /// Returns true if this `AppHome` equals the global `APP_HOME`
    #[must_use]
    pub fn is_default(&self) -> bool {
        // Compare absolute paths
        self.0 == APP_HOME.0
    }
}

impl Deref for AppHome {
    type Target = Path;

    fn deref(&self) -> &Self::Target {
        self.0.as_path()
    }
}
