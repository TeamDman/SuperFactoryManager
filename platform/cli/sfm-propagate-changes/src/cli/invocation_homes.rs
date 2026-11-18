use crate::paths::AppHome;
#[cfg(test)]
use crate::paths::CACHE_ROOT_FILE;
use crate::paths::CacheHome;
use crate::paths::CacheHomeRequired;
use crate::paths::CacheHomeSource;
use crate::paths::SelectedCacheHome;
use eyre::Report;
use eyre::Result;
use eyre::eyre;

/// Homes selected at the CLI boundary for one invocation.
///
/// A failed cache selection is retained so cache-root repair and commands that
/// only need application settings can still run. Accessors perform no discovery.
#[derive(Debug)]
pub struct InvocationHomes {
    app_home: AppHome,
    cache_home: Result<SelectedCacheHome>,
}

impl InvocationHomes {
    #[must_use]
    pub fn new(app_home: AppHome, cache_home: Result<CacheHome>) -> Self {
        Self::new_selected(
            app_home,
            cache_home.map(|home| SelectedCacheHome {
                home,
                source: CacheHomeSource::Supplied,
            }),
        )
    }

    #[must_use]
    pub fn new_selected(app_home: AppHome, cache_home: Result<SelectedCacheHome>) -> Self {
        Self {
            app_home,
            cache_home,
        }
    }

    /// # Errors
    /// Returns the cache-selection failure captured at startup.
    pub fn selected_cache_home(&self) -> Result<&SelectedCacheHome> {
        self.cache_home
            .as_ref()
            .map_err(|error| eyre!("Cannot resolve SFM cache root: {error:#}"))
    }

    /// Copyable access to a successful startup selection for optional core inputs.
    #[must_use]
    pub fn available_cache_home(&self) -> Option<&CacheHome> {
        self.cache_home
            .as_ref()
            .ok()
            .map(|selection| &selection.home)
    }

    pub(crate) fn explain_cache_error(&self, error: Report) -> Report {
        if error.is::<CacheHomeRequired>()
            && let Err(selection) = &self.cache_home
        {
            return eyre!("Cannot resolve SFM cache root: {selection:#}").wrap_err(error);
        }
        error
    }

    #[must_use]
    pub fn app_home(&self) -> &AppHome {
        &self.app_home
    }

    /// # Errors
    /// Returns the cache-selection failure captured at startup.
    pub fn cache_home(&self) -> Result<&CacheHome> {
        Ok(&self.selected_cache_home()?.home)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cancellation::CancellationToken;
    use crate::cli::Cli;
    use std::fs;
    use std::sync::Arc;
    use std::sync::Barrier;
    use std::thread;

    fn parse(arguments: &[&str]) -> Cli {
        figue::from_slice::<Cli>(arguments)
            .into_result()
            .unwrap()
            .get_silent()
    }

    #[test]
    fn invocations_keep_independent_homes_on_parallel_threads() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let barrier = Arc::new(Barrier::new(2));
        let threads = ["first", "second"].map(|name| {
            let root = temporary.path().join(name);
            let barrier = barrier.clone();
            thread::spawn(move || -> Result<()> {
                let app_home = AppHome(root.join("settings"));
                let cache_home = CacheHome(root.join("cache"));
                let homes = InvocationHomes::new(app_home.clone(), Ok(cache_home.clone()));
                barrier.wait();
                let logging = parse(&["home", "path"]).logging_config(homes.app_home())?;
                assert_eq!(logging.json_log_directory, Some(app_home.file_path("logs")));
                assert_eq!(homes.cache_home()?.0, cache_home.0);
                Ok(())
            })
        });
        for worker in threads {
            worker.join().expect("home fixture thread")?;
        }
        assert!(!temporary.path().join("logs").exists());
        Ok(())
    }

    #[test]
    fn invalid_cache_selection_does_not_block_settings_or_cache_repair() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        let app_home = AppHome(temporary.path().join("settings"));
        app_home.ensure_dir()?;
        let config = app_home.file_path(CACHE_ROOT_FILE);
        fs::write(&config, "invalid cache selection")?;
        let homes = InvocationHomes::new(app_home.clone(), Err(eyre!("fixture cache is invalid")));
        assert!(
            homes
                .cache_home()
                .unwrap_err()
                .to_string()
                .contains("fixture cache is invalid")
        );
        assert_eq!(
            parse(&["home", "path"])
                .logging_config(homes.app_home())?
                .json_log_directory,
            Some(app_home.file_path("logs"))
        );
        parse(&["cache", "root", "unset"]).invoke_in(
            CancellationToken::new(),
            temporary.path(),
            &homes,
        )?;
        assert!(!config.exists());
        // Repair must not perform another selection within the same invocation.
        assert!(homes.cache_home().is_err());
        let error = homes.explain_cache_error(CacheHomeRequired.into());
        assert!(format!("{error:#}").contains("fixture cache is invalid"));
        let unrelated = homes.explain_cache_error(eyre!("unrelated failure"));
        assert_eq!(unrelated.to_string(), "unrelated failure");
        assert!(!temporary.path().join("cache").exists());
        Ok(())
    }
}
