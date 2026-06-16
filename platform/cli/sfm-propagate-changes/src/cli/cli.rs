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
    /// JDK discovery and selection commands
    Jdk {
        /// JDK subcommand
        #[facet(args::subcommand)]
        command: super::jdk::JdkCommand,
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
            Command::Jdk { command } => command.invoke(),
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
    use crate::cli::jar::JarCommand;
    use crate::cli::run::RunCommand;
    use crate::cli_arg_normalization::normalize_parallel_args;
    use crate::jar_build::BuildMode;
    use crate::jar_build::ErrorAction;
    use crate::jar_build::Parallelism;
    use tracing::level_filters::LevelFilter;

    #[test]
    fn parses_top_level_run_commands() {
        assert_run_command(&["run", "client"]);
        assert_run_command(&["run", "client", "--branch", "1.19.2"]);
        assert_run_command(&["run", "client-smoke", "--branch", "1.19.2"]);
        assert_run_command(&["run", "client-puppet", "--branch", "1.19.2"]);
        assert_run_command(&["run", "server", "--branch", "1.19.2"]);
        assert_run_command(&["run", "data", "--branch", "1.19.2"]);
        assert_run_command(&["run", "game-test-server", "--branch", "1.19.2"]);
        assert_run_command(&["run", "game-test-server", "--branch", "1.19.2", "--dry-run"]);
        assert_run_command(&[
            "run",
            "game-test-server",
            "--branch",
            "core",
            "--error-action",
            "continue",
            "--dry-run",
        ]);
    }

    #[test]
    fn parses_jar_build_dry_run() {
        let cli = figue::from_slice::<Cli>(&["jar", "build", "--branch", "1.19.2", "--dry-run"])
            .into_result()
            .expect("jar build dry-run should parse")
            .get_silent();
        match cli.command {
            Command::Jar {
                command: JarCommand::Build { command },
            } => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("branch query should parse");
                assert!(options.dry_run);
                assert_eq!(options.branch.to_string(), "1.19.2");
                assert_eq!(options.error_action, ErrorAction::Bail);
            }
            command => panic!("expected jar build command, got {command:?}"),
        }
    }

    #[test]
    fn parses_jar_build_parallel_value() {
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "build",
            "--branch",
            "core",
            "--parallel",
            "4",
            "--dry-run",
        ])
        .into_result()
        .expect("jar build parallel should parse")
        .get_silent();
        match cli.command {
            Command::Jar {
                command: JarCommand::Build { command },
            } => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("parallel value should parse");
                assert_eq!(options.parallelism, Parallelism::Parallel { limit: 4 });
            }
            command => panic!("expected jar build command, got {command:?}"),
        }
    }

    #[test]
    fn parses_bare_parallel_after_arg_normalization() {
        let args = normalize_parallel_args(["run", "game-test-server", "--parallel", "--dry-run"]);
        let arg_refs = args.iter().map(String::as_str).collect::<Vec<_>>();
        let cli = figue::from_slice::<Cli>(&arg_refs)
            .into_result()
            .expect("bare parallel should normalize and parse")
            .get_silent();
        match cli.command {
            Command::Run {
                command: RunCommand::GameTestServer { command },
            } => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("normalized parallel should parse");
                assert_eq!(
                    options.parallelism,
                    Parallelism::Parallel {
                        limit: Parallelism::DEFAULT_LIMIT
                    }
                );
            }
            command => panic!("expected game-test-server run command, got {command:?}"),
        }
    }

    #[test]
    fn rejects_zero_parallelism() {
        let cli = figue::from_slice::<Cli>(&["jar", "build", "--parallel", "0"])
            .into_result()
            .expect("zero parallel syntax should parse before domain validation")
            .get_silent();
        match cli.command {
            Command::Jar {
                command: JarCommand::Build { command },
            } => {
                let error = command
                    .into_options(BuildMode::Build)
                    .expect_err("zero parallelism should be rejected");
                assert!(error.to_string().contains("--parallel"));
            }
            command => panic!("expected jar build command, got {command:?}"),
        }
    }

    #[test]
    fn parses_error_action_continue() {
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "build",
            "--branch",
            "core",
            "--error-action",
            "continue",
        ])
        .into_result()
        .expect("jar build should parse")
        .get_silent();
        match cli.command {
            Command::Jar {
                command: JarCommand::Build { command },
            } => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("error action should parse");
                assert_eq!(options.error_action, ErrorAction::Continue);
            }
            command => panic!("expected jar build command, got {command:?}"),
        }
    }

    #[test]
    fn rejects_invalid_error_action() {
        assert!(
            figue::from_slice::<Cli>(&[
                "jar",
                "build",
                "--branch",
                "core",
                "--error-action",
                "explode",
            ])
            .is_err()
        );
    }

    #[test]
    fn omitted_branch_defaults_to_core() {
        let cli = figue::from_slice::<Cli>(&["jar", "plan"])
            .into_result()
            .expect("jar plan should parse without explicit branch")
            .get_silent();
        match cli.command {
            Command::Jar {
                command: JarCommand::Plan { command },
            } => {
                let options = command
                    .into_options(BuildMode::Plan)
                    .expect("default branch query should parse");
                assert_eq!(options.branch.to_string(), "core");
            }
            command => panic!("expected jar plan command, got {command:?}"),
        }
    }

    #[test]
    fn migrated_jar_and_run_commands_reject_mc() {
        assert!(figue::from_slice::<Cli>(&["run", "client", "--mc", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["jar", "plan", "--mc", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["jar", "build", "--mc", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["jar", "compare", "--mc", "1.19.2"]).is_err());
    }

    #[test]
    fn jar_run_client_no_longer_parses() {
        assert!(figue::from_slice::<Cli>(&["jar", "run-client", "--branch", "1.19.2"]).is_err());
    }

    #[test]
    fn parses_jdk_list() {
        let cli = figue::from_slice::<Cli>(&["jdk", "list"])
            .into_result()
            .expect("jdk list should parse")
            .get_silent();
        assert!(matches!(cli.command, Command::Jdk { .. }));
    }

    #[test]
    fn default_logging_config_reads_env_filter() {
        let cli = figue::from_slice::<Cli>(&["jdk", "list"])
            .into_result()
            .expect("jdk list should parse")
            .get_silent();
        let logging = cli.logging_config().expect("logging config should build");
        assert!(logging.read_env_filter);
    }

    #[test]
    fn explicit_logging_config_ignores_env_filter() {
        let cli = figue::from_slice::<Cli>(&["--log-filter", "info", "jdk", "list"])
            .into_result()
            .expect("jdk list should parse")
            .get_silent();
        let logging = cli.logging_config().expect("logging config should build");
        assert!(!logging.read_env_filter);
        assert_eq!(logging.default_directive, LevelFilter::INFO.into());
    }

    #[test]
    fn debug_logging_config_ignores_env_filter() {
        let cli = figue::from_slice::<Cli>(&["--debug", "jdk", "list"])
            .into_result()
            .expect("jdk list should parse")
            .get_silent();
        let logging = cli.logging_config().expect("logging config should build");
        assert!(!logging.read_env_filter);
        assert_eq!(logging.default_directive, LevelFilter::DEBUG.into());
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
