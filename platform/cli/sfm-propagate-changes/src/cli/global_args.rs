use crate::cli::output::OutputFormat;
use crate::logging::LoggingConfig;
use chrono::Local;
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
    /// a filename will be generated there. If omitted, no JSON log file will be written.
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
        let explicit_filter = self.debug || self.log_filter.is_some();
        Ok(LoggingConfig {
            default_directive: match (self.debug, &self.log_filter) {
                (true, _) => LevelFilter::DEBUG,
                (false, Some(filter)) => LevelFilter::from_str(filter)?,
                (false, None) => LevelFilter::INFO,
            }
            .into(),
            read_env_filter: !explicit_filter,
            json_log_path: match &self.log_file {
                None => None,
                Some(path) if path.is_dir() => {
                    let timestamp = Local::now().format("%Y-%m-%d_%H-%M-%S");
                    let filename = format!("log_{timestamp}.ndjson");
                    Some(path.join(filename))
                }
                Some(path) => Some(path.clone()),
            },
            stop_after: self.stop_after.clone(),
        })
    }
}
