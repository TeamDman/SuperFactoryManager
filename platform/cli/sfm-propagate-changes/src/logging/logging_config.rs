use std::path::PathBuf;
use tracing_subscriber::filter::Directive;

#[derive(Debug)]
pub struct LoggingConfig {
    pub default_directive: Directive,
    pub read_env_filter: bool,
    pub json_log_path: Option<PathBuf>,
    pub json_log_directory: Option<PathBuf>,
    pub private_log_directory: bool,
    pub json_default_directive: Directive,
    pub json_read_env_filter: bool,
    pub stop_after: Option<String>,
}

impl LoggingConfig {
    pub fn new(level: impl Into<Directive>, json_log_path: Option<impl Into<PathBuf>>) -> Self {
        let directive = level.into();
        Self {
            default_directive: directive.clone(),
            json_default_directive: directive,
            json_read_env_filter: true,
            json_log_directory: None,
            private_log_directory: false,
            read_env_filter: true,
            json_log_path: json_log_path.map(Into::into),
            stop_after: None,
        }
    }
}
