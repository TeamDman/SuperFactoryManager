use crate::cli::output::OutputFormat;
use crate::logging::LoggingConfig;
use facet::Facet;
use figue::{self as args};
use std::path::PathBuf;
use std::str::FromStr;
use tracing::level_filters::LevelFilter;

/// Global arguments that apply to all commands.
#[derive(Facet, Debug, Default, Clone)]
pub struct GlobalArgs {
    /// Enable debug logging, including backtraces on panics.
    #[facet(args::named)]
    pub debug: bool,

    /// Log level filter directive.
    #[facet(default, args::named)]
    pub log_filter: Option<String>,

    /// Write structured ndjson logs to this file or directory. If a directory is provided,
    /// a unique filename will be generated there. Defaults to additive DEBUG NDJSON
    /// run files under the SFM application home logs directory (latest.json identifies the latest).
    #[facet(default, args::named)]
    pub log_file: Option<PathBuf>,

    /// Request graceful cancellation after the named tracing/Tracy zone closes.
    #[facet(default, args::named)]
    pub stop_after: Option<String>,

    /// Render typed command output as text, JSON, or CSV.
    ///
    /// When omitted, typed output is text for an interactive terminal and JSON
    /// when stdout is redirected.
    #[facet(default, args::named)]
    pub output_format: Option<OutputFormat>,
}

impl GlobalArgs {
    /// # Errors
    ///
    /// This function will return an error if the log filter string is invalid.
    pub fn logging_config(&self) -> eyre::Result<LoggingConfig> {
        self.logging_config_with_default_file(true)
    }

    pub(crate) fn logging_config_with_default_file(
        &self,
        default_file: bool,
    ) -> eyre::Result<LoggingConfig> {
        let explicit_filter = self.debug || self.log_filter.is_some();
        let level = match (self.debug, &self.log_filter) {
            (true, _) => LevelFilter::DEBUG,
            (false, Some(filter)) => LevelFilter::from_str(filter)?,
            (false, None) => LevelFilter::INFO,
        };
        let (json_log_path, json_log_directory) = match &self.log_file {
            None if default_file => (
                None,
                Some(std::path::absolute(
                    crate::paths::AppHome::resolve()?.file_path("logs"),
                )?),
            ),
            None => (None, None),
            Some(path) if path.is_dir() => (None, Some(path.clone())),
            Some(path) => (Some(path.clone()), None),
        };
        Ok(LoggingConfig {
            default_directive: level.into(),
            read_env_filter: !explicit_filter,
            json_log_path,
            json_log_directory,
            private_log_directory: default_file && self.log_file.is_none(),
            json_default_directive: if self.log_file.is_none() && !explicit_filter {
                LevelFilter::DEBUG
            } else {
                level
            }
            .into(),
            json_read_env_filter: !explicit_filter,
            stop_after: self.stop_after.clone(),
        })
    }
}
