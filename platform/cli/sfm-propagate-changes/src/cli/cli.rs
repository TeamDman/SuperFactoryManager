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
    /// Audit tracked Rust and Java source policy violations.
    Audit(super::audit::AuditArgs),
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
    /// Discover and run SFM `GameTests`
    GameTest(super::game_test::GameTestArgs),
    /// Discover and run SFM game-puppet definitions
    Puppet(super::puppet::PuppetArgs),
    /// Discover and run Java `JUnit` tests
    Test(super::test::TestArgs),
    /// Repo root related commands
    RepoRoot(super::repo_root::RepoRootArgs),
}

impl Command {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self {
            Command::Audit(args) => args.invoke(),
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
            Command::GameTest(args) => args.invoke(cancellation_token),
            Command::Puppet(args) => args.invoke(cancellation_token),
            Command::Test(args) => args.invoke(cancellation_token),
            Command::RepoRoot(args) => args.invoke(),
        }
    }
}

#[cfg(test)] // todo(2026-06-16) these tests have gotten long, can we create a cli_test.rs or something so they are still close to this file
mod tests {
    use super::Cli;
    use crate::cli::Command;
    use crate::cli::dependency::DependencyCommand;
    use crate::cli::dependency::DependencySourceCommand;
    use crate::cli::dependency::DependencySourceProviderCommand;
    use crate::cli::git::GitCommand;
    use crate::cli::gradle::GradleCommand;
    use crate::cli::jar::JarCommand;
    use crate::cli::run::RunCommand;
    use crate::cli::run::RunGameTestServerCliCommand;
    use crate::jar_build::BuildMode;
    use crate::jar_build::ClientPuppetKeepOpen;
    use crate::jar_build::ErrorAction;
    use crate::jar_build::Parallelism;
    use crate::source_audit::SourceLanguage;
    use facet::Facet;
    use figue as args;
    use tracing::level_filters::LevelFilter;

    #[test]
    fn parses_top_level_run_clis() {
        assert_run_cli(&["run", "compile", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "client", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "client", "--branch", "1.19.2", "--text-editor"]);
        assert_run_cli(&["run", "client", "--branch", "1.19.2", "--input-diag"]);
        assert_run_cli(&[
            "run",
            "client",
            "--branch",
            "1.19.2",
            "--title-screen",
            "input-diag",
        ]);
        assert_run_cli(&["run", "client", "--branch", "1.19.2", "--smoke"]);
        assert_run_cli(&[
            "run",
            "client",
            "--branch",
            "1.19.2",
            "--puppet",
            "game_test_orbit_capture",
            "--game-test",
            "sfm:move_1_stack_direct",
        ]);
        assert_run_cli(&["run", "server", "--branch", "1.19.2"]);
        assert_run_cli(&["run", "data", "--branch", "1.19.2"]);
    }

    #[test]
    fn parses_top_level_audit_cli() {
        let cli = figue::from_slice::<Cli>(&["audit", "--branch", "*"])
            .into_result()
            .expect("top-level audit should parse")
            .get_silent();
        let Command::Audit(args) = cli.command else {
            panic!("expected audit command");
        };
        assert_eq!(args.branch.as_ref(), "*");
        assert!(args.language.is_empty());
        assert!(args.lang.is_empty());
        assert_eq!(args.max_lines, None);
        assert!(args.font_render_surface);
    }

    #[test]
    fn parses_top_level_audit_filters() {
        let cli = figue::from_slice::<Cli>(&[
            "audit",
            "--branch",
            ">=1.19.2",
            "--language",
            "rust",
            "--lang",
            "java",
            "--max-lines",
            "1200",
            "--no-font-render-surface",
        ])
        .into_result()
        .expect("top-level audit filters should parse")
        .get_silent();
        let Command::Audit(args) = cli.command else {
            panic!("expected audit command");
        };
        assert_eq!(args.branch.as_ref(), ">=1.19.2");
        assert_eq!(args.language, vec![SourceLanguage::Rust]);
        assert_eq!(args.lang, vec![SourceLanguage::Java]);
        assert_eq!(args.max_lines.map(|limit| limit.0), Some(1200));
        assert!(!args.font_render_surface);
    }

    #[test]
    fn parses_top_level_test_run_options() {
        let cli = figue::from_slice::<Cli>(&[
            "test",
            "run",
            "--branch",
            "1.19.2",
            "--filter",
            "lavaSearch",
            "--no-capture",
        ])
        .into_result()
        .expect("test run command should parse")
        .get_silent();
        let Command::Test(crate::cli::test::TestArgs {
            command: crate::cli::test::TestCommand::Run(args),
        }) = cli.command
        else {
            panic!("expected test run command");
        };
        assert_eq!(args.filter.as_deref(), Some("lavaSearch"));
        assert!(args.no_capture);
        assert!(args.command.is_none());
    }

    #[test]
    fn parses_game_test_run_filters() {
        let game_test_server = figue::from_slice::<Cli>(&[
            "game-test",
            "run-server",
            "--branch",
            "1.19.2",
            "--filter",
            "wither_aggro_*",
        ])
        .into_result()
        .expect("game test server filter should parse")
        .get_silent();
        match game_test_server.command {
            Command::GameTest(crate::cli::game_test::GameTestArgs {
                command: crate::cli::game_test::GameTestCommand::RunServer(args),
            }) => {
                assert_eq!(args.filter.as_deref(), Some("wither_aggro_*"));
            }
            command => panic!("expected game-test run-server command, got {command:?}"),
        }

        let client_puppet = figue::from_slice::<Cli>(&[
            "game-test",
            "run-client",
            "--branch",
            "1.19.2",
            "--filter",
            "sfm:wither_aggro_*,sfm:tough_cable_*",
        ])
        .into_result()
        .expect("client puppet filter should parse")
        .get_silent();
        match client_puppet.command {
            Command::GameTest(crate::cli::game_test::GameTestArgs {
                command: crate::cli::game_test::GameTestCommand::RunClient(args),
            }) => {
                assert_eq!(
                    args.filter.as_deref(),
                    Some("sfm:wither_aggro_*,sfm:tough_cable_*")
                );
            }
            command => panic!("expected game-test run-client command, got {command:?}"),
        }

        let game_test_preview = figue::from_slice::<Cli>(&[
            "puppet",
            "run",
            "--branch",
            "1.19.2",
            "game_test_orbit_capture",
            "--game-test",
            "sfm:move_1_stack_direct",
            "--width",
            "1600",
            "--height",
            "900",
            "--no-mute",
        ])
        .into_result()
        .expect("game test preview arguments should parse")
        .get_silent();
        match game_test_preview.command {
            Command::Puppet(crate::cli::puppet::PuppetArgs {
                command: crate::cli::puppet::PuppetCommand::Run(args),
            }) => {
                assert_eq!(args.puppet, "game_test_orbit_capture".to_string());
                assert_eq!(args.game_test.as_deref(), Some("sfm:move_1_stack_direct"));
                assert_eq!(args.width, Some(1600));
                assert_eq!(args.height, Some(900));
                assert!(!args.mute);
            }
            command => panic!("expected puppet run command, got {command:?}"),
        }

        let default_muted_puppet = figue::from_slice::<Cli>(&[
            "puppet",
            "run",
            "--branch",
            "1.19.2",
            "title_screen_capture",
        ])
        .into_result()
        .expect("default puppet mute should parse")
        .get_silent();
        match default_muted_puppet.command {
            Command::Puppet(crate::cli::puppet::PuppetArgs {
                command: crate::cli::puppet::PuppetCommand::Run(args),
            }) => assert!(args.mute),
            command => panic!("expected puppet run command, got {command:?}"),
        }

        let puppet_artifacts_path =
            figue::from_slice::<Cli>(&["puppet", "artifacts", "path", "--branch", "1.19.2"])
                .into_result()
                .expect("puppet artifact path arguments should parse")
                .get_silent();
        match puppet_artifacts_path.command {
            Command::Puppet(crate::cli::puppet::PuppetArgs {
                command:
                    crate::cli::puppet::PuppetCommand::Artifacts(
                        crate::cli::puppet_artifacts::PuppetArtifactsArgs {
                            command:
                                crate::cli::puppet_artifacts::PuppetArtifactsCommand::Path(args),
                        },
                    ),
            }) => {
                assert_eq!(args.branch.to_string(), "1.19.2");
            }
            command => panic!("expected puppet artifacts path command, got {command:?}"),
        }

        let puppet_matrix = figue::from_slice::<Cli>(&[
            "puppet",
            "matrix",
            "move_1_stack_direct_walkthrough",
            "--branch",
            "1.19.2",
            "--parallel",
            "1",
        ])
        .into_result()
        .expect("puppet matrix arguments should parse")
        .get_silent();
        match puppet_matrix.command {
            Command::Puppet(crate::cli::puppet::PuppetArgs {
                command:
                    crate::cli::puppet::PuppetCommand::Matrix(
                        crate::cli::puppet_matrix::PuppetMatrixArgs {
                            puppet, options, ..
                        },
                    ),
            }) => {
                assert_eq!(puppet, "move_1_stack_direct_walkthrough");
                assert_eq!(options.branch.to_string(), "1.19.2");
                assert_eq!(options.parallel, Some(Some(1)));
            }
            command => panic!("expected puppet matrix command, got {command:?}"),
        }
    }

    #[test]
    fn parses_client_puppet_keep_open_options() {
        let bare = figue::from_slice::<Cli>(&[
            "game-test",
            "run-client",
            "--branch",
            "core",
            "--keep-open",
        ])
        .into_result()
        .expect("bare keep-open should parse")
        .get_silent();
        match bare.command {
            Command::GameTest(crate::cli::game_test::GameTestArgs {
                command: crate::cli::game_test::GameTestCommand::RunClient(args),
            }) => {
                assert_eq!(args.keep_open, Some(None));
                assert_eq!(
                    ClientPuppetKeepOpen::from_cli(args.keep_open)
                        .expect("bare keep-open should convert"),
                    ClientPuppetKeepOpen::Forever
                );
            }
            command => panic!("expected game-test run-client command, got {command:?}"),
        }

        let valued = figue::from_slice::<Cli>(&[
            "game-test",
            "run-client",
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
            Command::GameTest(crate::cli::game_test::GameTestArgs {
                command: crate::cli::game_test::GameTestCommand::RunClient(args),
            }) => {
                assert_eq!(args.keep_open, Some(Some("5m".to_string())));
                assert_eq!(
                    ClientPuppetKeepOpen::from_cli(args.keep_open)
                        .expect("valued keep-open should convert"),
                    ClientPuppetKeepOpen::Countdown { seconds: 300 }
                );
            }
            command => panic!("expected game-test run-client command, got {command:?}"),
        }

        let numeric_seconds = figue::from_slice::<Cli>(&[
            "game-test",
            "run-client",
            "--branch",
            "core",
            "--keep-open",
            "90",
        ])
        .into_result()
        .expect("numeric keep-open seconds should parse")
        .get_silent();
        match numeric_seconds.command {
            Command::GameTest(crate::cli::game_test::GameTestArgs {
                command: crate::cli::game_test::GameTestCommand::RunClient(args),
            }) => {
                assert_eq!(
                    ClientPuppetKeepOpen::from_cli(args.keep_open)
                        .expect("numeric keep-open seconds should convert"),
                    ClientPuppetKeepOpen::Countdown { seconds: 90 }
                );
            }
            command => panic!("expected game-test run-client command, got {command:?}"),
        }
    }

    #[test]
    fn parses_game_test_server_bisect_options() {
        let cli = figue::from_slice::<Cli>(&[
            "game-test",
            "run-server",
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
            Command::GameTest(crate::cli::game_test::GameTestArgs {
                command: crate::cli::game_test::GameTestCommand::RunServer(args),
            }) => {
                assert_eq!(args.filter.as_deref(), Some("sfm:*"));
                let Some(RunGameTestServerCliCommand::Bisect(bisect)) = args.command else {
                    panic!("expected game-test run-server bisect command");
                };
                assert_eq!(
                    bisect.target,
                    "wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall"
                );
                assert_eq!(bisect.max_runs, Some(12));
            }
            command => panic!("expected game-test run-server command, got {command:?}"),
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
    fn parses_bare_parallel() {
        let cli = figue::from_slice::<Cli>(&[
            "game-test",
            "run-server",
            "--branch",
            "core",
            "--parallel",
            "--dry-run",
        ])
        .into_result()
        .expect("bare parallel should parse")
        .get_silent();
        match cli.command {
            Command::GameTest(crate::cli::game_test::GameTestArgs {
                command: crate::cli::game_test::GameTestCommand::RunServer(command),
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
            command => panic!("expected game-test run-server command, got {command:?}"),
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
    fn parses_dependency_migrate_check_with_explicit_branch() {
        let cli =
            figue::from_slice::<Cli>(&["dependency", "migrate", "--branch", "1.19.2", "--check"])
                .into_result()
                .expect("dependency migrate should parse")
                .get_silent();
        match cli.command {
            Command::Dependency(crate::cli::dependency::DependencyArgs {
                command: DependencyCommand::Migrate(args),
            }) => {
                assert_eq!(args.branch.as_ref(), "1.19.2");
                assert!(args.check);
            }
            command => panic!("expected dependency migrate command, got {command:?}"),
        }
    }

    #[test]
    fn parses_dependency_list_and_show_with_explicit_branch() {
        let list = figue::from_slice::<Cli>(&["dependency", "list", "--branch", "1.19.2"])
            .into_result()
            .expect("dependency list should parse")
            .get_silent();
        assert!(matches!(
            list.command,
            Command::Dependency(crate::cli::dependency::DependencyArgs {
                command: DependencyCommand::List(_),
            })
        ));

        let show =
            figue::from_slice::<Cli>(&["dependency", "show", "cc-tweaked", "--branch", "1.19.2"])
                .into_result()
                .expect("dependency show should parse")
                .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Show(args),
        }) = show.command
        else {
            panic!("expected dependency show command");
        };
        assert_eq!(args.id, "cc-tweaked");
        assert_eq!(args.branch.as_ref(), "1.19.2");
    }

    #[test]
    fn parses_dependency_artifact_accept_and_rejects_legacy_add() {
        let cli = figue::from_slice::<Cli>(&[
            "dependency",
            "artifact",
            "accept",
            "cc-tweaked/main",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("dependency artifact accept should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Artifact(artifact),
        }) = cli.command
        else {
            panic!("expected dependency artifact command");
        };
        let crate::cli::dependency::DependencyArtifactCommand::Accept(args) = artifact.command;
        assert_eq!(args.target, "cc-tweaked/main");
        assert_eq!(args.branch.as_ref(), "1.19.2");
    }

    #[test]
    fn parses_dependency_add_as_v3_declaration_creation() {
        let cli = figue::from_slice::<Cli>(&[
            "dependency",
            "add",
            "cc-tweaked",
            "--branch",
            "1.19.2",
            "--maven",
            "org.squiddev:cc-tweaked-1.19.2:1.101.3",
            "--kind",
            "mod",
            "--role",
            "integration",
            "--repository",
            "squiddev",
            "--scope",
            "compile",
            "--scope",
            "runtime",
            "--scope",
            "gametest-compile",
            "--scope",
            "gametest-runtime",
            "--artifact-treatment",
            "loader-managed-mod",
        ])
        .into_result()
        .expect("dependency add should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Add(args),
        }) = cli.command
        else {
            panic!("expected dependency add command");
        };
        assert_eq!(args.id, "cc-tweaked");
        assert_eq!(args.branch.as_ref(), "1.19.2");
        assert_eq!(
            args.maven.as_deref(),
            Some("org.squiddev:cc-tweaked-1.19.2:1.101.3")
        );
        assert_eq!(args.scope.len(), 4);
        assert_eq!(
            args.kind,
            Some(crate::toolchain_lockfile_schema::version::v3::DependencyKindV3::Mod)
        );
        assert_eq!(
            args.role,
            Some(crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3::Integration)
        );
        assert_eq!(
            args.artifact_treatment,
            Some(crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::LoaderManagedMod)
        );
        figue::from_slice::<Cli>(&[
            "dependency",
            "add",
            "curse.maven:cc-tweaked-282001:4433584",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect_err("dependency add requires structured declaration options");
    }

    #[test]
    fn parses_dependency_remove_with_component_target() {
        let cli = figue::from_slice::<Cli>(&[
            "dependency",
            "remove",
            "applied-energistics-2/api",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("dependency remove should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Remove(args),
        }) = cli.command
        else {
            panic!("expected dependency remove command");
        };
        assert_eq!(args.target, "applied-energistics-2/api");
        assert_eq!(args.branch.as_ref(), "1.19.2");
    }

    #[test]
    fn parses_dependency_component_add() {
        let cli = figue::from_slice::<Cli>(&[
            "dependency",
            "component",
            "add",
            "mekanism",
            "api",
            "--branch",
            "1.19.2",
            "--maven",
            "mekanism:Mekanism:1.19.2-10.3.9.13:api",
            "--repository",
            "modmaven",
            "--scope",
            "compile",
            "--artifact-treatment",
            "plain",
        ])
        .into_result()
        .expect("dependency component add should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Component(component),
        }) = cli.command
        else {
            panic!("expected dependency component command");
        };
        let crate::cli::dependency::DependencyComponentCommand::Add(args) = component.command;
        assert_eq!(args.dependency, "mekanism");
        assert_eq!(args.component, "api");
        assert_eq!(args.branch.as_ref(), "1.19.2");
        assert_eq!(
            args.scope,
            [crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3::Compile]
        );
        assert_eq!(
            args.artifact_treatment,
            Some(crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::Plain)
        );
    }

    #[test]
    fn parses_dependency_refresh_all_and_targeted() {
        let all = figue::from_slice::<Cli>(&["dependency", "refresh", "--branch", "1.19.2"])
            .into_result()
            .expect("dependency refresh all should parse")
            .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Refresh(args),
        }) = all.command
        else {
            panic!("expected dependency refresh command");
        };
        assert_eq!(args.target, None);

        let targeted = figue::from_slice::<Cli>(&[
            "dependency",
            "refresh",
            "cc-tweaked/main",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("targeted dependency refresh should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Refresh(args),
        }) = targeted.command
        else {
            panic!("expected targeted dependency refresh command");
        };
        assert_eq!(args.target.as_deref(), Some("cc-tweaked/main"));
        assert_eq!(args.branch.as_ref(), "1.19.2");
    }

    #[test]
    fn parses_dependency_source_configuration_and_cache_with_explicit_branch() {
        let configure = figue::from_slice::<Cli>(&[
            "dependency",
            "source",
            "configure",
            "cc-tweaked",
            "--branch",
            "1.19.2",
            "--maven-sources",
            "--root",
            "dan200/computercraft",
            "--prefer",
        ])
        .into_result()
        .expect("source configure should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Source(source),
        }) = configure.command
        else {
            panic!("expected dependency source command");
        };
        let DependencySourceCommand::Configure(args) = source.command else {
            panic!("expected source configure command");
        };
        assert!(args.maven_sources);
        assert_eq!(args.root, ["dan200/computercraft"]);
        assert!(args.prefer);

        let decompile = figue::from_slice::<Cli>(&[
            "dependency",
            "source",
            "configure",
            "mekanism/main",
            "--branch",
            "1.19.2",
            "--decompile",
            "--decompiler",
            "vineflower/main",
        ])
        .into_result()
        .expect("decompile source configure should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Source(source),
        }) = decompile.command
        else {
            panic!("expected dependency source command");
        };
        let DependencySourceCommand::Configure(args) = source.command else {
            panic!("expected source configure command");
        };
        assert!(args.decompile);
        assert_eq!(args.decompiler.as_deref(), Some("vineflower/main"));

        let cache_audit = figue::from_slice::<Cli>(&[
            "dependency",
            "source",
            "cache",
            "audit",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("source cache audit should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Source(source),
        }) = cache_audit.command
        else {
            panic!("expected dependency source command");
        };
        assert!(matches!(source.command, DependencySourceCommand::Cache(_)));
    }

    #[test]
    fn parses_dependency_source_provider_and_acquisition_with_explicit_branch() {
        let providers = figue::from_slice::<Cli>(&[
            "dependency",
            "source",
            "provider",
            "list",
            "cc-tweaked",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("provider list should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Source(source),
        }) = providers.command
        else {
            panic!("expected dependency source command");
        };
        let DependencySourceCommand::Provider(provider) = source.command else {
            panic!("expected provider command");
        };
        let DependencySourceProviderCommand::List(args) = provider.command;
        assert_eq!(args.target.as_deref(), Some("cc-tweaked"));

        let acquire = figue::from_slice::<Cli>(&[
            "dependency",
            "source",
            "acquire",
            "cc-tweaked",
            "--provider",
            "maven-sources",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("source acquire should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Source(source),
        }) = acquire.command
        else {
            panic!("expected dependency source command");
        };
        let DependencySourceCommand::Acquire(args) = source.command else {
            panic!("expected source acquire command");
        };
        assert_eq!(
            args.provider,
            Some(crate::cli::dependency::DependencySourceProviderSelector::MavenSources)
        );
        assert_eq!(args.target.as_deref(), Some("cc-tweaked"));
        assert!(!args.all);

        let acquire_all = figue::from_slice::<Cli>(&[
            "dependency",
            "source",
            "acquire",
            "--all",
            "--provider",
            "any",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("all source acquire should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Source(source),
        }) = acquire_all.command
        else {
            panic!("expected dependency source command");
        };
        let DependencySourceCommand::Acquire(args) = source.command else {
            panic!("expected source acquire command");
        };
        assert_eq!(args.target, None);
        assert!(args.all);
        assert_eq!(
            args.provider,
            Some(crate::cli::dependency::DependencySourceProviderSelector::Any)
        );
    }

    #[test]
    fn parses_dependency_source_search_with_explicit_branch() {
        let search = figue::from_slice::<Cli>(&[
            "dependency",
            "source",
            "search",
            "IPeripheralProvider",
            "--dependency",
            "cc-tweaked",
            "--dependency",
            "minecraft",
            "--provider-id",
            "upstream",
            "--require-complete",
            "--branch",
            "1.19.2",
        ])
        .into_result()
        .expect("source search should parse")
        .get_silent();
        let Command::Dependency(crate::cli::dependency::DependencyArgs {
            command: DependencyCommand::Source(source),
        }) = search.command
        else {
            panic!("expected dependency source command");
        };
        let DependencySourceCommand::Search(args) = source.command else {
            panic!("expected source search command");
        };
        assert_eq!(args.pattern, "IPeripheralProvider");
        assert_eq!(args.dependency, ["cc-tweaked", "minecraft"]);
        assert_eq!(args.provider_id.as_deref(), Some("upstream"));
        assert!(args.require_complete);
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
            &["dependency", "migrate"],
            &["dependency", "list"],
            &["dependency", "show", "cc-tweaked"],
            &["gradle", "run", "runData"],
            &["loader", "list"],
            &["audit"],
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
    fn obsolete_source_commands_are_rejected() {
        assert!(figue::from_slice::<Cli>(&["jar", "sources", "--branch", "1.19.2"]).is_err());
        assert!(figue::from_slice::<Cli>(&["source", "audit", "--branch", "1.19.2"]).is_err());
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
                    | RunCommand::Server(_)
                    | RunCommand::Data(_),
            }) => {}
            command => panic!("expected top-level run command, got {command:?}"),
        }
    }
}
