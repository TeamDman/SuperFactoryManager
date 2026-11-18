use directories_next::ProjectDirs;
use eyre::bail;
use std::env;
use std::fs::create_dir_all;
use std::ops::Deref;
use std::path::Path;
use std::path::PathBuf;

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
        create_dir_all(&self.0)?;
        Ok(())
    }

    /// Resolve the `AppHome` according to the same rules used previously:
    /// * If [`super::APP_HOME_ENV_VAR`] env var is set, use that directory
    /// * Otherwise use the platform `ProjectDirs::config_dir()`
    ///
    /// # Errors
    ///
    /// This function will return an error if the config directory cannot be determined.
    pub(crate) fn resolve() -> eyre::Result<AppHome> {
        if let Ok(override_dir) = env::var(super::APP_HOME_ENV_VAR) {
            return Ok(AppHome(PathBuf::from(override_dir)));
        }
        if let Some(project_dirs) = ProjectDirs::from("", "teamdman", super::APP_HOME_DIR_NAME) {
            Ok(AppHome(project_dirs.config_dir().to_path_buf()))
        } else {
            bail!("Could not determine config directory")
        }
    }
}

impl Deref for AppHome {
    type Target = Path;

    fn deref(&self) -> &Self::Target {
        self.0.as_path()
    }
}

impl From<&AppHome> for PathBuf {
    fn from(app_home: &AppHome) -> Self {
        app_home.0.clone()
    }
}
