mod app_home;
mod cache;
mod cache_home_required;
mod cache_home_source;
mod invocation_directory;
mod selected_cache_home;

pub use app_home::*;
pub use cache::*;
pub use cache_home_required::CacheHomeRequired;
pub use cache_home_source::CacheHomeSource;
pub use invocation_directory::InvocationDirectory;
pub use selected_cache_home::SelectedCacheHome;

pub const APP_HOME_ENV_VAR: &str = "SFM_PROPAGATE_CHANGES_HOME";
pub const APP_HOME_DIR_NAME: &str = "sfm-propagate-changes";

pub const APP_CACHE_ENV_VAR: &str = "SFM_PROPAGATE_CHANGES_CACHE";
pub const APP_CACHE_DIR_NAME: &str = "sfm-propagate-changes";
