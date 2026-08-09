mod app_home;
mod cache;

pub use app_home::*;
pub use cache::*;

pub const APP_HOME_ENV_VAR: &str = "SFM_PROPAGATE_CHANGES_HOME";
pub const APP_HOME_DIR_NAME: &str = "sfm-propagate-changes";

pub const APP_CACHE_ENV_VAR: &str = "SFM_PROPAGATE_CHANGES_CACHE";
pub const APP_CACHE_DIR_NAME: &str = "sfm-propagate-changes";
