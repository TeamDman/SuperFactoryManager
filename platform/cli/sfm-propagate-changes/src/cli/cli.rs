use crate::cancellation::CancellationToken;
use crate::cli::global_args::GlobalArgs;
use crate::logging::LoggingConfig;
use facet::Facet;
use figue::FigueBuiltins;
use figue::{self as args};

/// A tool for propagating git changes across Minecraft version worktrees.
///
/// This CLI manages merging changes from older Minecraft version branches
/// to newer ones in a sequential manner.
#[derive(Facet, Debug)]
pub struct Cli {
    /// Global arguments that apply to all commands.
    #[facet(flatten)]
    pub global_args: GlobalArgs,

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
        self.global_args.logging_config()
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
    /// Dependency lockfile maintenance commands
    Dependency(super::dependency::DependencyArgs),
    /// JDK discovery and selection commands
    Jdk(super::jdk::JdkArgs),
    /// Prism loader metadata commands
    Loader(super::loader::LoaderArgs),
    /// Modrinth release related commands
    Modrinth(super::modrinth::ModrinthArgs),
    /// Jar directory and release artifact related commands
    Jar(super::jar::JarArgs),
    /// Build and launch Forge userdev run configs
    Run(super::run::RunArgs),
    /// Repo root related commands
    RepoRoot(super::repo_root::RepoRootArgs),
    /// Source code audit commands
    Source(super::source::SourceArgs),
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
            Command::Dependency(args) => args.invoke(cancellation_token),
            Command::Jdk(args) => args.invoke(),
            Command::Loader(args) => args.invoke(),
            Command::Modrinth(args) => args.invoke(),
            Command::Jar(args) => args.invoke(cancellation_token),
            Command::Run(args) => args.invoke(cancellation_token),
            Command::RepoRoot(args) => args.invoke(),
            Command::Source(args) => args.invoke(),
        }
    }
}

#[cfg(test)] // todo(2026-06-16) these tests have gotten long, can we create a cli_test.rs or something so they are still close to this file
mod tests {
    use super::Cli;
    use crate::cli::Command;
    use crate::cli::git::GitCommand;
    use crate::cli::gradle::GradleCommand;
    use crate::cli::jar::JarCommand;
    use crate::cli::run::RunCommand;
    use crate::cli::run::RunGameTestServerCliCommand;
    use crate::cli::run::RunTestCliCommand;
    use crate::cli::source::SourceCommand;
    use crate::jar_build::BuildMode;
    use crate::jar_build::ClientPuppetKeepOpen;
    use crate::jar_build::ErrorAction;
    use crate::jar_build::Parallelism;
    use crate::jar_build::SourceOutputLayout;
    use crate::source_audit::SourceLanguage;
    use facet::Facet;
    use figue as args;
    use tracing::level_filters::LevelFilter;

    #[test]
    fn parses_top_level_run_clis() {
        assert_run_cli(&["run", "compile", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "client", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "client-smoke", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "client-puppet", "--branch", "1.19.2"]);
        assert_run_cli(&[
            "run",
            "client-puppet",
            "--branch",
            "1.19.2",
            "--filter",
            "wither_aggro_*",
        ]);
        assert_run_cli(&["run", "server", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "data", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "game-test-server", "--branch", "1.19.2"]);
        assert_run_cli(&[
            "run",
            "game-test-server",
            "--branch",
            "1.19.2",
            "--filter",
            "sfm:wither_aggro_*,sfm:tough_cable_*",
        ]);
        assert_run_cli(&[
            "run",
            "game-test-server",
            "--branch",
            "1.19.2",
            "bisect",
            "wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall",
            "--max-runs",
            "8",
        ]);
        assert_run_cli(&["run", "test", "--branch", "1.19.2"]);
        assert_run_cli(&[
            "run",
            "test",
            "--branch",
            "1.19.2",
            "--filter",
            "lavaSearch",
        ]);
        assert_run_cli(&["run", "test", "--branch", "1.19.2", "--no-capture"]);
        assert_run_cli(&["run", "test", "--branch", "1.19.2", "list"]);
        assert_run_cli(&[
            "run",
            "test",
            "list",
            "--branch",
            "1.19.2",
            "--filter",
            "lavaSearch",
        ]);
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
    fn parses_source_audit_cli() {
        let cli = figue::from_slice::<Cli>(&["source", "audit", "--branch", "*"])
            .into_result()
            .expect("source audit should parse")
            .get_silent();
        let Command::Source(args) = cli.command else {
            panic!("expected source command");
        };
        let SourceCommand::Audit(args) = args.command;
        assert_eq!(args.branch.as_ref(), "*");
        assert!(args.language.is_empty());
        assert!(args.lang.is_empty());
        assert_eq!(args.max_lines.0, 1000);
    }

    #[test]
    fn parses_source_audit_filters() {
        let cli = figue::from_slice::<Cli>(&[
            "source",
            "audit",
            "--branch",
            ">=1.19.2",
            "--language",
            "rust",
            "--lang",
            "java",
            "--max-lines",
            "1200",
        ])
        .into_result()
        .expect("source audit filters should parse")
        .get_silent();
        let Command::Source(args) = cli.command else {
            panic!("expected source command");
        };
        let SourceCommand::Audit(args) = args.command;
        assert_eq!(args.branch.as_ref(), ">=1.19.2");
        assert_eq!(args.language, vec![SourceLanguage::Rust]);
        assert_eq!(args.lang, vec![SourceLanguage::Java]);
        assert_eq!(args.max_lines.0, 1200);
    }

    #[test]
    fn parses_run_test_options() {
        let cli = figue::from_slice::<Cli>(&[
            "run",
            "test",
            "--branch",
            "1.19.2",
            "--filter",
            "lavaSearch",
            "--no-capture",
            "list",
        ])
        .into_result()
        .expect("run test list command should parse")
        .get_silent();
        let Command::Run(crate::cli::run::RunArgs {
            command: RunCommand::Test(args),
        }) = cli.command
        else {
            panic!("expected run test command");
        };
        assert_eq!(args.filter.as_deref(), Some("lavaSearch"));
        assert!(args.no_capture);
        assert!(matches!(args.command, Some(RunTestCliCommand::List(_))));
    }

    #[test]
    fn parses_game_test_run_filters() {
        let game_test_server = figue::from_slice::<Cli>(&[
            "run",
            "game-test-server",
            "--branch",
            "1.19.2",
            "--filter",
            "wither_aggro_*",
        ])
        .into_result()
        .expect("game test server filter should parse")
        .get_silent();
        match game_test_server.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::GameTestServer(args),
            }) => {
                assert_eq!(args.filter.as_deref(), Some("wither_aggro_*"));
            }
            command => panic!("expected game-test-server run command, got {command:?}"),
        }

        let client_puppet = figue::from_slice::<Cli>(&[
            "run",
            "client-puppet",
            "--branch",
            "1.19.2",
            "--filter",
            "sfm:wither_aggro_*,sfm:tough_cable_*",
        ])
        .into_result()
        .expect("client puppet filter should parse")
        .get_silent();
        match client_puppet.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::ClientPuppet(args),
            }) => {
                assert_eq!(
                    args.filter.as_deref(),
                    Some("sfm:wither_aggro_*,sfm:tough_cable_*")
                );
            }
            command => panic!("expected client-puppet run command, got {command:?}"),
        }
    }

    #[test]
    fn parses_client_puppet_keep_open_options() {
        let bare =
            figue::from_slice::<Cli>(&["run", "client-puppet", "--branch", "core", "--keep-open"])
                .into_result()
                .expect("bare keep-open should parse")
                .get_silent();
        match bare.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::ClientPuppet(args),
            }) => {
                assert_eq!(args.keep_open, Some(None));
                assert_eq!(
                    ClientPuppetKeepOpen::from_cli(args.keep_open)
                        .expect("bare keep-open should convert"),
                    ClientPuppetKeepOpen::Forever
                );
            }
            command => panic!("expected client-puppet run command, got {command:?}"),
        }

        let valued = figue::from_slice::<Cli>(&[
            "run",
            "client-puppet",
            "--branch",
            "core",
            "--keep-open",
            "5m",
            "--filter",
            "wither_*",
        ])
        .into_result()
        .expect("valued keep-open should parse")
        .get_silent();
        match valued.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::ClientPuppet(args),
            }) => {
                assert_eq!(args.keep_open, Some(Some("5m".to_string())));
                assert_eq!(
                    ClientPuppetKeepOpen::from_cli(args.keep_open)
                        .expect("valued keep-open should convert"),
                    ClientPuppetKeepOpen::Countdown { seconds: 300 }
                );
            }
            command => panic!("expected client-puppet run command, got {command:?}"),
        }

        let numeric_seconds = figue::from_slice::<Cli>(&[
            "run",
            "client-puppet",
            "--branch",
            "core",
            "--keep-open",
            "90",
        ])
        .into_result()
        .expect("numeric keep-open seconds should parse")
        .get_silent();
        match numeric_seconds.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::ClientPuppet(args),
            }) => {
                assert_eq!(
                    ClientPuppetKeepOpen::from_cli(args.keep_open)
                        .expect("numeric keep-open seconds should convert"),
                    ClientPuppetKeepOpen::Countdown { seconds: 90 }
                );
            }
            command => panic!("expected client-puppet run command, got {command:?}"),
        }
    }

    #[test]
    fn parses_game_test_server_bisect_options() {
        let cli = figue::from_slice::<Cli>(&[
            "run",
            "game-test-server",
            "--branch",
            "1.19.2",
            "--filter",
            "sfm:*",
            "bisect",
            "wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall",
            "--max-runs",
            "12",
        ])
        .into_result()
        .expect("game test server bisect should parse")
        .get_silent();
        match cli.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::GameTestServer(args),
            }) => {
                assert_eq!(args.filter.as_deref(), Some("sfm:*"));
                let Some(RunGameTestServerCliCommand::Bisect(bisect)) = args.command else {
                    panic!("expected game-test-server bisect command");
                };
                assert_eq!(
                    bisect.target,
                    "wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall"
                );
                assert_eq!(bisect.max_runs, Some(12));
            }
            command => panic!("expected game-test-server run command, got {command:?}"),
        }
    }

    #[test]
    fn parses_git_add_and_commit() {
        let add = figue::from_slice::<Cli>(&[
            "git",
            "add",
            "platform/minecraft/sfm-toolchain.lock.json",
            "README.md",
        ])
        .into_result()
        .expect("git add should parse")
        .get_silent();
        match add.command {
            Command::Git(crate::cli::git::GitArgs {
                command: GitCommand::Add(args),
            }) => {
                assert_eq!(
                    args.paths,
                    vec![
                        "platform/minecraft/sfm-toolchain.lock.json".to_string(),
                        "README.md".to_string()
                    ]
                );
            }
            command => panic!("expected git add command, got {command:?}"),
        }

        let commit =
            figue::from_slice::<Cli>(&["git", "commit", "-m", "%BRANCH% - update lockfile"])
                .into_result()
                .expect("git commit should parse")
                .get_silent();
        match commit.command {
            Command::Git(crate::cli::git::GitArgs {
                command: GitCommand::Commit(args),
            }) => {
                assert_eq!(args.message, "%BRANCH% - update lockfile");
            }
            command => panic!("expected git commit command, got {command:?}"),
        }
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
    fn parses_jar_sources_filetree_layout() {
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "sources",
            "--branch",
            "1.19.2",
            "--layout",
            "filetree",
            "--parallel",
            "2",
        ])
        .into_result()
        .expect("jar sources filetree should parse")
        .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Sources(command),
            }) => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("jar sources options should parse");
                assert_eq!(options.build.branch.to_string(), "1.19.2");
                assert_eq!(options.layout, SourceOutputLayout::Filetree);
                assert_eq!(
                    options.build.parallelism,
                    Parallelism::Parallel { limit: 2 }
                );
            }
            command => panic!("expected jar sources command, got {command:?}"),
        }
    }

    #[test]
    fn parses_bare_parallel() {
        let cli = figue::from_slice::<Cli>(&[
            "run",
            "game-test-server",
            "--branch",
            "core",
            "--parallel",
            "--dry-run",
        ])
        .into_result()
        .expect("bare parallel should parse")
        .get_silent();
        match cli.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::GameTestServer(command),
            }) => {
                let options = command
                    .into_options(BuildMode::Build)
                    .expect("bare parallel should parse");
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
    fn parses_wait_for_build_lock() {
        let cli = figue::from_slice::<Cli>(&[
            "run",
            "compile",
            "--branch",
            "core",
            "--wait-for-build-lock",
        ])
        .into_result()
        .expect("wait-for-build-lock should parse")
        .get_silent();
        match cli.command {
            Command::Run(crate::cli::run::RunArgs {
                command: RunCommand::Compile(command),
            }) => {
                let options = command
                    .options
                    .into_options(BuildMode::Build)
                    .expect("compile options should parse");
                assert!(options.wait_for_build_lock);
            }
            command => panic!("expected compile run command, got {command:?}"),
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
    fn parses_jar_compare_bare_parallel() {
        let cli = figue::from_slice::<Cli>(&["jar", "compare", "--branch", "core", "--parallel"])
            .into_result()
            .expect("bare compare parallel should parse")
            .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::Compare(command),
            }) => {
                let options = command
                    .into_options()
                    .expect("bare compare parallel should parse");
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
    fn parses_jar_artifact_audit_bare_parallel() {
        let cli =
            figue::from_slice::<Cli>(&["jar", "audit-artifacts", "--branch", "core", "--parallel"])
                .into_result()
                .expect("bare artifact audit parallel should parse")
                .get_silent();
        match cli.command {
            Command::Jar(crate::cli::jar::JarArgs {
                command: JarCommand::AuditArtifacts(command),
            }) => {
                let options = command
                    .into_options()
                    .expect("bare artifact audit parallel should parse");
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
        let cli =
            figue::from_slice::<Cli>(&["jar", "build", "--branch", "core", "--parallel", "0"])
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
                    .into_query()
                    .expect("branch query should parse");
                assert_eq!(query.to_string(), "core>=1.21.0");
                assert_eq!(command.tasks, ["runData"]);
            }
            command => panic!("expected gradle run command, got {command:?}"),
        }
    }

    #[test]
    fn branch_is_required_for_commands_that_accept_branch() {
        let commands = [
            &["run", "compile"][..],
            &["run", "client"],
            &["client", "launch"],
            &["jar", "plan"],
            &["jar", "build"],
            &["jar", "compare"],
            &["jar", "audit-artifacts"],
            &["gradle", "run", "runData"],
            &["loader", "list"],
            &["source", "audit"],
            &["server", "list"],
            &["server", "launch"],
            &["github", "release", "now"],
            &["github", "release", "amend"],
            &["modrinth", "release", "check"],
            &["modrinth", "release", "validate"],
            &["modrinth", "release", "now"],
            &["modrinth", "release", "amend"],
            &["curseforge", "release", "check"],
            &["curseforge", "release", "validate"],
            &["curseforge", "release", "now"],
            &["curseforge", "release", "amend"],
            &["curseforge", "minecraft", "version", "list"],
        ];

        for command in commands {
            assert!(
                figue::from_slice::<Cli>(command).is_err(),
                "expected {command:?} to require --branch"
            );
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
    fn parses_loader_list() {
        let cli = figue::from_slice::<Cli>(&["loader", "list", "--branch", "core"])
            .into_result()
            .expect("loader list should parse")
            .get_silent();
        assert!(matches!(cli.command, Command::Loader(_)));
    }

    #[test]
    fn parses_client_open_and_launch() {
        let open = figue::from_slice::<Cli>(&["client", "open"])
            .into_result()
            .expect("client open should parse")
            .get_silent();
        assert!(matches!(open.command, Command::Client(_)));

        let launch = figue::from_slice::<Cli>(&["client", "launch", "--branch", "core"])
            .into_result()
            .expect("client launch should parse")
            .get_silent();
        assert!(matches!(launch.command, Command::Client(_)));
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

    #[test]
    fn top_level_stop_after_configures_logging() {
        let cli =
            figue::from_slice::<Cli>(&["--stop-after", "create_plan_for_target", "jdk", "list"])
                .into_result()
                .expect("jdk list should parse")
                .get_silent();
        let logging = cli.logging_config().expect("logging config should build");
        assert_eq!(
            logging.stop_after.as_deref(),
            Some("create_plan_for_target")
        );
    }

    #[test]
    fn stop_after_after_subcommand_parses_as_global_arg() {
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "build",
            "--dry-run",
            "--branch",
            "1.19.2",
            "--stop-after",
            "create_plan_for_target{branch=1.19.2}",
        ])
        .into_result()
        .expect("jar build with stop-after should parse")
        .get_silent();
        let logging = cli.logging_config().expect("logging config should build");
        assert_eq!(
            logging.stop_after.as_deref(),
            Some("create_plan_for_target{branch=1.19.2}")
        );
    }

    #[test]
    fn figue_nested_option_models_optional_value_flags() {
        #[expect(
            clippy::option_option,
            reason = "This test intentionally checks whether figue can model absent, bare, and valued flags."
        )]
        #[derive(Facet, Debug)]
        struct Args {
            #[facet(args::named, default)]
            maybe: Option<Option<usize>>,
        }

        assert!(
            figue::from_slice::<Args>(&[])
                .into_result()
                .expect("absent flag should parse")
                .get_silent()
                .maybe
                .is_none()
        );
        assert_eq!(
            figue::from_slice::<Args>(&["--maybe", "12"])
                .into_result()
                .expect("valued flag should parse")
                .get_silent()
                .maybe,
            Some(Some(12))
        );
        assert_eq!(
            figue::from_slice::<Args>(&["--maybe"])
                .into_result()
                .expect("bare flag should parse")
                .get_silent()
                .maybe,
            Some(None)
        );
    }

    fn assert_run_cli(args: &[&str]) {
        let cli = figue::from_slice::<Cli>(args)
            .into_result()
            .expect("run command should parse")
            .get_silent();
        match cli.command {
            Command::Run(crate::cli::run::RunArgs {
                command:
                    RunCommand::Compile(_)
                    | RunCommand::Client(_)
                    | RunCommand::ClientSmoke(_)
                    | RunCommand::ClientPuppet(_)
                    | RunCommand::Server(_)
                    | RunCommand::Data(_)
                    | RunCommand::GameTestServer(_)
                    | RunCommand::Test(_),
            }) => {}
            command => panic!("expected top-level run command, got {command:?}"),
        }
    }
}
