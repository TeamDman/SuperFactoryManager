use crate::logging::LoggingConfig;
use chrono::Local;
use facet::Facet;
use figue::FigueBuiltins;
use figue::{self as args};
use std::path::PathBuf;
use std::str::FromStr;
use tracing::level_filters::LevelFilter;

/// A tool for propagating git changes across Minecraft version worktrees.
///
/// This CLI manages merging changes from older Minecraft version branches
/// to newer ones in a sequential manner.
#[derive(Facet, Debug)]
pub struct Cli {
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

    /// Subcommand to run
    #[facet(args::subcommand)]
    pub command: Command,

    /// Built-in flags (--help, --version, --completions)
    #[facet(flatten)]
    pub builtins: FigueBuiltins,
}

impl Cli {
    /// # Errors
    ///
    /// This function will return an error if the log filter string is invalid.
    pub fn logging_config(&self) -> eyre::Result<LoggingConfig> {
        Ok(LoggingConfig {
            default_directive: match (self.debug, &self.log_filter) {
                (true, _) => LevelFilter::DEBUG,
                (false, Some(filter)) => LevelFilter::from_str(filter)?,
                (false, None) => LevelFilter::INFO,
            }
            .into(),
            json_log_path: match &self.log_file {
                None => None,
                Some(path) if path.is_dir() => {
                    let timestamp = Local::now().format("%Y-%m-%d_%H-%M-%S");
                    let filename = format!("log_{timestamp}.ndjson");
                    Some(path.join(filename))
                }
                Some(path) => Some(path.clone()),
            },
        })
    }

    /// # Errors
    ///
    /// This function will return an error if the command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Available commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum Command {
    /// Propagate changes by merging from older to newer version branches
    Merge {
        /// Merge options
        #[facet(flatten)]
        command: super::merge::MergeCommand,
    },
    /// Run arbitrary gradle task(s) for each worktree in strict sequence
    Gradle {
        /// Gradle options
        #[facet(flatten)]
        command: super::gradle::GradleCommand,
    },
    /// Check workspace files for correctness
    Check {
        /// Check options
        #[facet(flatten)]
        command: super::check::CheckCommand,
    },
    /// Client instance tracking and management commands
    Client {
        /// Client subcommand
        #[facet(args::subcommand)]
        command: super::client::ClientCommand,
    },
    /// Server instance tracking and management commands
    Server {
        /// Server subcommand
        #[facet(args::subcommand)]
        command: super::server::ServerCommand,
    },
    /// Push branches (runs `git push` in each worktree)
    Push {
        /// Push options
        #[facet(flatten)]
        command: super::push::PushCommand,
    },
    /// Home directory related commands
    Home {
        /// Home subcommand
        #[facet(args::subcommand)]
        command: super::home::HomeCommand,
    },
    /// Cache directory related commands
    Cache {
        /// Cache subcommand
        #[facet(args::subcommand)]
        command: super::cache::CacheCommand,
    },
    /// Jar directory and release artifact related commands
    Jar {
        /// Jar subcommand
        #[facet(args::subcommand)]
        command: super::jar::JarCommand,
    },
    /// Repo root related commands
    RepoRoot {
        /// Repo root subcommand
        #[facet(args::subcommand)]
        command: super::repo_root::RepoRootCommand,
    },
    /// Show git status for all worktrees
    Status {
        /// Status subcommand
        #[facet(default, args::subcommand)]
        command: Option<super::status::StatusCommand>,
    },
}

impl Command {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Command::Merge { command } => command.invoke(),
            Command::Gradle { command } => command.invoke(),
            Command::Check { command } => command.invoke(),
            Command::Client { command } => command.invoke(),
            Command::Server { command } => command.invoke(),
            Command::Push { command } => command.invoke(),
            Command::Home { command } => command.invoke(),
            Command::Cache { command } => command.invoke(),
            Command::Jar { command } => command.invoke(),
            Command::RepoRoot { command } => command.invoke(),
            Command::Status { command } => command.unwrap_or_default().invoke(),
        }
    }
}
