use crate::cancellation::CancellationToken;
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
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        self.command.invoke(cancellation_token)
    }
}

/// Available commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum Command {
    /// Run arbitrary gradle task(s) for each worktree in strict sequence
    Gradle(super::gradle::GradleArgs),
    /// Client instance tracking and management commands
    Client(super::client::ClientArgs),
    /// Server instance tracking and management commands
    Server(super::server::ServerArgs),
    /// Git operation commands across all worktrees
    Git(super::git::GitArgs),
    /// GitHub release commands
    Github(super::github::GithubArgs),
    /// Home directory related commands
    Home(super::home::HomeArgs),
    /// Cache directory related commands
    Cache(super::cache::CacheArgs),
    /// `CurseForge` release and file related commands
    Curseforge(super::curseforge::CurseforgeArgs),
    /// JDK discovery and selection commands
    Jdk(super::jdk::JdkArgs),
    /// Modrinth release related commands
    Modrinth(super::modrinth::ModrinthArgs),
    /// Jar directory and release artifact related commands
    Jar(super::jar::JarArgs),
    /// Build and launch Forge userdev run configs
    Run(super::run::RunArgs),
    /// Repo root related commands
    RepoRoot(super::repo_root::RepoRootArgs),
}

impl Command {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self {
            Command::Gradle(args) => args.invoke(),
            Command::Client(args) => args.invoke(),
            Command::Server(args) => args.invoke(),
            Command::Git(args) => args.invoke(),
            Command::Github(args) => args.invoke(),
            Command::Home(args) => args.invoke(),
            Command::Cache(args) => args.invoke(),
            Command::Curseforge(args) => args.invoke(),
            Command::Jdk(args) => args.invoke(),
            Command::Modrinth(args) => args.invoke(),
            Command::Jar(args) => args.invoke(cancellation_token),
            Command::Run(args) => args.invoke(cancellation_token),
            Command::RepoRoot(args) => args.invoke(),
        }
    }
}

#[cfg(test)] // todo(2026-06-16) these tests have gotten long, can we create a cli_test.rs or something so they are still close to this file
mod tests {
    use super::Cli;
    use crate::cli::Command;
    use crate::cli::gradle::GradleCommand;
    use crate::cli::jar::JarCommand;
    use crate::cli::run::RunCommand;
    use crate::cli_arg_normalization::normalize_parallel_args;
    use crate::jar_build::BuildMode;
    use crate::jar_build::ErrorAction;
    use crate::jar_build::Parallelism;
    use tracing::level_filters::LevelFilter;

    #[test]
    fn parses_top_level_run_clis() {
        assert_run_cli(&["run", "client"]);
        assert_run_cli(&["run", "client", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "client-smoke", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "client-puppet", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "server", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "data", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "game-test-server", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "game-test-server", "--branch", "1.19.2", "--dry-run"]);
        assert_run_cli(&[
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
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "build",
            "--branch",
            "1.19.2",
            "--dry-run",
            "--artifact-source",
            "G:/Programming/Repos/Mekanism",
            "--artifact-source",
            "G:/Programming/Repos/OtherMavenRepo",
            "--require-portable-artifacts",
        ])
        .into_result()
        .expect("jar build dry-run should parse")
        .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Build(command),
            }) => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("branch query should parse");
                assert!(options.dry_run);
                assert_eq!(options.branch.to_string(), "1.19.2");
                assert_eq!(options.error_action, ErrorAction::Bail);
                assert_eq!(options.artifact_sources.len(), 2);
                assert_eq!(
                    options.artifact_sources[0],
                    std::path::PathBuf::from("G:/Programming/Repos/Mekanism")
                );
                assert_eq!(
                    options.artifact_sources[1],
                    std::path::PathBuf::from("G:/Programming/Repos/OtherMavenRepo")
                );
                assert!(options.require_portable_artifacts);
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
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Build(command),
            }) => {
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
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::GameTestServer(command),
            }) => {
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
    fn parses_jar_compare_parallel_value() {
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "compare",
            "--branch",
            "core",
            "--parallel",
            "3",
            "--error-action",
            "continue",
        ])
        .into_result()
        .expect("jar compare parallel should parse")
        .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Compare(command),
            }) => {
                let options = command
                    .into_options()
                    .expect("compare parallel value should parse");
                assert_eq!(options.parallelism, Parallelism::Parallel { limit: 3 });
                assert_eq!(options.error_action, ErrorAction::Continue);
            }
            command => panic!("expected jar compare command, got {command:?}"),
        }
    }

    #[test]
    fn parses_jar_artifact_audit_parallel_value() {
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "audit-artifacts",
            "--branch",
            "core",
            "--parallel",
            "5",
            "--error-action",
            "continue",
            "--require-portable-artifacts",
        ])
        .into_result()
        .expect("jar audit-artifacts parallel should parse")
        .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::AuditArtifacts(command),
            }) => {
                let options = command
                    .into_options()
                    .expect("artifact audit options should parse");
                assert_eq!(options.branch.to_string(), "core");
                assert_eq!(options.parallelism, Parallelism::Parallel { limit: 5 });
                assert_eq!(options.error_action, ErrorAction::Continue);
                assert!(options.require_portable_artifacts);
            }
            command => panic!("expected jar audit-artifacts command, got {command:?}"),
        }
    }

    #[test]
    fn parses_jar_compare_bare_parallel_after_arg_normalization() {
        let args = normalize_parallel_args(["jar", "compare", "--branch", "core", "--parallel"]);
        let arg_refs = args.iter().map(String::as_str).collect::<Vec<_>>();
        let cli = figue::from_slice::<Cli>(&arg_refs)
            .into_result()
            .expect("bare compare parallel should normalize and parse")
            .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Compare(command),
            }) => {
                let options = command
                    .into_options()
                    .expect("normalized compare parallel should parse");
                assert_eq!(
                    options.parallelism,
                    Parallelism::Parallel {
                        limit: Parallelism::DEFAULT_LIMIT
                    }
                );
            }
            command => panic!("expected jar compare command, got {command:?}"),
        }
    }

    #[test]
    fn parses_jar_artifact_audit_bare_parallel_after_arg_normalization() {
        let args = normalize_parallel_args(["jar", "audit-artifacts", "--parallel"]);
        let arg_refs = args.iter().map(String::as_str).collect::<Vec<_>>();
        let cli = figue::from_slice::<Cli>(&arg_refs)
            .into_result()
            .expect("bare artifact audit parallel should normalize and parse")
            .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::AuditArtifacts(command),
            }) => {
                let options = command
                    .into_options()
                    .expect("normalized artifact audit parallel should parse");
                assert_eq!(
                    options.parallelism,
                    Parallelism::Parallel {
                        limit: Parallelism::DEFAULT_LIMIT
                    }
                );
            }
            command => panic!("expected jar audit-artifacts command, got {command:?}"),
        }
    }

    #[test]
    fn rejects_zero_parallelism() {
        let cli = figue::from_slice::<Cli>(&["jar", "build", "--parallel", "0"])
            .into_result()
            .expect("zero parallel syntax should parse before domain validation")
            .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Build(command),
            }) => {
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
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Build(command),
            }) => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("error action should parse");
                assert_eq!(options.error_action, ErrorAction::Continue);
            }
            command => panic!("expected jar build command, got {command:?}"),
        }
    }

    #[test]
    fn parses_jar_compare_error_action_continue() {
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "compare",
            "--branch",
            "core",
            "--error-action",
            "continue",
        ])
        .into_result()
        .expect("jar compare should parse")
        .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Compare(command),
            }) => {
                let options = command.into_options().expect("branch query should parse");
                assert_eq!(options.branch.to_string(), "core");
                assert_eq!(options.error_action, ErrorAction::Continue);
            }
            command => panic!("expected jar compare command, got {command:?}"),
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
    fn parses_gradle_branch_selector() {
        let cli =
            figue::from_slice::<Cli>(&["gradle", "run", "runData", "--branch", "core>=1.21.0"])
                .into_result()
                .expect("gradle branch selector should parse")
                .get_silent();
        match cli.command {
            Command::Gradle(crate::cli::gradle::GradleArgs {
                command: GradleCommand::Run(command),
            }) => {
                let query = command
                    .branch
                    .expect("branch selector should be present")
                    .into_query()
                    .expect("branch query should parse");
                assert_eq!(query.to_string(), "core>=1.21.0");
                assert_eq!(command.tasks, ["runData"]);
            }
            command => panic!("expected gradle run command, got {command:?}"),
        }
    }

    #[test]
    fn omitted_branch_defaults_to_core() {
        let cli = figue::from_slice::<Cli>(&["jar", "plan"])
            .into_result()
            .expect("jar plan should parse without explicit branch")
            .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Plan(command),
            }) => {
                let options = command
                    .into_options(BuildMode::Plan)
                    .expect("default branch query should parse");
                assert_eq!(options.branch.to_string(), "core");
            }
            command => panic!("expected jar plan command, got {command:?}"),
        }
    }

    #[test]
    fn migrated_jar_and_run_clis_reject_mc() {
        assert!(figue::from_slice::<Cli>(&["run", "client", "--mc", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["jar", "plan", "--mc", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["jar", "build", "--mc", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["jar", "compare", "--mc", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["gradle", "run", "runData", "--mc", "1.19.2"]).is_err());
    }

    #[test]
    fn migrated_release_and_server_clis_parse_branch() {
        let commands = [
            &["server", "list", "--branch", "core"][..],
            &["server", "launch", "--branch", "1.19.2"],
            &[
                "github",
                "release",
                "now",
                "--branch",
                "1.19.2",
                "--dry-run",
                "--yes",
            ],
            &[
                "github",
                "release",
                "amend",
                "--branch",
                "1.19.2",
                "--dry-run",
                "--yes",
            ],
            &["modrinth", "release", "check", "--branch", "1.19.2"],
            &["modrinth", "release", "validate", "--branch", "1.19.2"],
            &[
                "modrinth",
                "release",
                "now",
                "--branch",
                "1.19.2",
                "--dry-run",
            ],
            &[
                "modrinth",
                "release",
                "amend",
                "--branch",
                "1.19.2",
                "--dry-run",
            ],
            &["curseforge", "release", "check", "--branch", "1.19.2"],
            &["curseforge", "release", "validate", "--branch", "1.19.2"],
            &[
                "curseforge",
                "release",
                "now",
                "--branch",
                "1.19.2",
                "--dry-run",
            ],
            &[
                "curseforge",
                "release",
                "amend",
                "--branch",
                "1.19.2",
                "--dry-run",
            ],
            &[
                "curseforge",
                "minecraft",
                "version",
                "list",
                "--branch",
                "core",
            ],
        ];

        for command in commands {
            figue::from_slice::<Cli>(command)
                .into_result()
                .unwrap_or_else(|error| panic!("expected {command:?} to parse: {error}"));
        }
    }

    #[test]
    fn migrated_release_and_server_clis_reject_mc() {
        let commands = [
            &["server", "list", "--mc", "1.19.2"][..],
            &["server", "launch", "--mc", "1.19.2"],
            &["github", "release", "now", "--mc", "1.19.2"],
            &["github", "release", "amend", "--mc", "1.19.2"],
            &["modrinth", "release", "check", "--mc", "1.19.2"],
            &["modrinth", "release", "validate", "--mc", "1.19.2"],
            &["modrinth", "release", "now", "--mc", "1.19.2"],
            &["modrinth", "release", "amend", "--mc", "1.19.2"],
            &["curseforge", "release", "check", "--mc", "1.19.2"],
            &["curseforge", "release", "validate", "--mc", "1.19.2"],
            &["curseforge", "release", "now", "--mc", "1.19.2"],
            &["curseforge", "release", "amend", "--mc", "1.19.2"],
            &[
                "curseforge",
                "minecraft",
                "version",
                "list",
                "--mc",
                "1.19.2",
            ],
        ];

        for command in commands {
            assert!(
                figue::from_slice::<Cli>(command).is_err(),
                "expected {command:?} to reject --mc"
            );
        }
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
        assert!(matches!(cli.command, Command::Jdk(_)));
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

    fn assert_run_cli(args: &[&str]) {
        let cli = figue::from_slice::<Cli>(args)
            .into_result()
            .expect("run command should parse")
            .get_silent();
        match cli.command {
            Command::Run(crate::cli::run::RunArgs {
                command:
                    RunCommand::Client(_)
                    | RunCommand::ClientSmoke(_)
                    | RunCommand::ClientPuppet(_)
                    | RunCommand::Server(_)
                    | RunCommand::Data(_)
                    | RunCommand::GameTestServer(_),
            }) => {}
            command => panic!("expected top-level run command, got {command:?}"),
        }
    }
}
