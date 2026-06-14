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
    /// Run arbitrary gradle task(s) for each worktree in strict sequence
    Gradle {
        /// Gradle subcommand
        #[facet(args::subcommand)]
        command: super::gradle::GradleCommand,
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
    /// Git operation commands across all worktrees
    Git {
        /// Git subcommand
        #[facet(args::subcommand)]
        command: super::git::GitCommand,
    },
    /// GitHub release commands
    Github {
        /// GitHub subcommand
        #[facet(args::subcommand)]
        command: super::github::GithubCommand,
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
    /// `CurseForge` release and file related commands
    Curseforge {
        /// `CurseForge` subcommand
        #[facet(args::subcommand)]
        command: super::curseforge::CurseforgeCommand,
    },
    /// Modrinth release related commands
    Modrinth {
        /// Modrinth subcommand
        #[facet(args::subcommand)]
        command: super::modrinth::ModrinthCommand,
    },
    /// Jar directory and release artifact related commands
    Jar {
        /// Jar subcommand
        #[facet(args::subcommand)]
        command: super::jar::JarCommand,
    },
    /// Build and launch Forge userdev run configs
    Run {
        /// Run subcommand
        #[facet(args::subcommand)]
        command: super::run::RunCommand,
    },
    /// Repo root related commands
    RepoRoot {
        /// Repo root subcommand
        #[facet(args::subcommand)]
        command: super::repo_root::RepoRootCommand,
    },
}

impl Command {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Command::Gradle { command } => command.invoke(),
            Command::Client { command } => command.invoke(),
            Command::Server { command } => command.invoke(),
            Command::Git { command } => command.invoke(),
            Command::Github { command } => command.invoke(),
            Command::Home { command } => command.invoke(),
            Command::Cache { command } => command.invoke(),
            Command::Curseforge { command } => command.invoke(),
            Command::Modrinth { command } => command.invoke(),
            Command::Jar { command } => command.invoke(),
            Command::Run { command } => command.invoke(),
            Command::RepoRoot { command } => command.invoke(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::Cli;
    use crate::cli::Command;
    use crate::cli::run::RunCommand;

    #[test]
    fn parses_top_level_run_commands() {
        assert_run_command(&["run", "client", "--mc", "1.19.2"]);
        assert_run_command(&["run", "client-smoke", "--mc", "1.19.2"]);
        assert_run_command(&["run", "client-puppet", "--mc", "1.19.2"]);
        assert_run_command(&["run", "server", "--mc", "1.19.2"]);
        assert_run_command(&["run", "data", "--mc", "1.19.2"]);
        assert_run_command(&["run", "game-test-server", "--mc", "1.19.2"]);
    }

    #[test]
    fn jar_run_client_no_longer_parses() {
        assert!(figue::from_slice::<Cli>(&["jar", "run-client", "--mc", "1.19.2"]).is_err());
    }

    fn assert_run_command(args: &[&str]) {
        let cli = figue::from_slice::<Cli>(args)
            .into_result()
            .expect("run command should parse")
            .get_silent();
        match cli.command {
            Command::Run {
                command:
                    RunCommand::Client { .. }
                    | RunCommand::ClientSmoke { .. }
                    | RunCommand::ClientPuppet { .. }
                    | RunCommand::Server { .. }
                    | RunCommand::Data { .. }
                    | RunCommand::GameTestServer { .. },
            } => {}
            command => panic!("expected top-level run command, got {command:?}"),
        }
    }
}
