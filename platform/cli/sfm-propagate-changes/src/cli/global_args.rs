use super::InvocationHomes;
use crate::cli::output::OutputFormat;
use crate::logging::LoggingConfig;
use crate::paths::InvocationDirectory;
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

    /// Write structured ndjson logs to this file or directory. Relative paths resolve
    /// from the invocation directory. If a directory is provided,
    /// a unique filename will be generated there. Defaults to additive DEBUG NDJSON
    /// run files in the selected SFM cache's logs directory (latest.json identifies the latest).
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
    /// Returns an error when the log filter or destination cannot be resolved.
    pub fn logging_config(
        &self,
        invocation_dir: &InvocationDirectory<'_>,
        homes: &InvocationHomes,
    ) -> eyre::Result<LoggingConfig> {
        self.logging_config_with_default_file(invocation_dir, homes, true)
    }

    pub(crate) fn logging_config_with_default_file(
        &self,
        invocation_dir: &InvocationDirectory<'_>,
        homes: &InvocationHomes,
        default_file: bool,
    ) -> eyre::Result<LoggingConfig> {
        let explicit_filter = self.debug || self.log_filter.is_some();
        let level = match (self.debug, &self.log_filter) {
            (true, _) => LevelFilter::DEBUG,
            (false, Some(filter)) => LevelFilter::from_str(filter)?,
            (false, None) => LevelFilter::INFO,
        };
        let log_file = self
            .log_file
            .as_deref()
            .map(|path| invocation_dir.resolve(path))
            .transpose()?;
        let (json_log_path, json_log_directory) = match log_file {
            None if default_file => {
                // A failed cache selection must leave automatic logging and cache repair available.
                let directory = homes.available_cache_home().map_or_else(
                    || homes.app_home().file_path("logs"),
                    |cache| cache.join("logs"),
                );
                (None, Some(invocation_dir.resolve(&directory)?))
            }
            None => (None, None),
            Some(path) if path.is_dir() => (None, Some(path)),
            Some(path) => (Some(path), None),
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
