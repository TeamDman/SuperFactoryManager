use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::curseforge::CurseforgeArgs;
use crate::cli::curseforge::CurseforgeCommand;
use crate::cli::git::GitArgs;
use crate::cli::git::GitCommand;
use crate::cli::github::GithubArgs;
use crate::cli::github::GithubCommand;
use crate::cli::global_args::GlobalArgs;
use crate::cli::jar::JarArgs;
use crate::cli::jar::JarCommand;
use crate::cli::legacy_release_admission::LegacyReleaseAdmission;
use crate::cli::modrinth::ModrinthArgs;
use crate::cli::modrinth::ModrinthCommand;
use crate::cli::output::CliOutput;
use crate::cli::source::LegacySourceArgs;
use crate::cli::source::LegacySourceCommand;
use crate::cli::source::SourceArgs;
use crate::cli::source::SourceCommand;
use crate::logging::LoggingConfig;
use crate::paths::InvocationDirectory;
use facet::Facet;
use figue::FigueBuiltins;
use figue::{self as args};
use std::env::current_dir;
use std::path::Path;

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
    pub fn logging_config(
        &self,
        invocation_dir: &InvocationDirectory<'_>,
        homes: &InvocationHomes,
    ) -> eyre::Result<LoggingConfig> {
        if self.defers_file_logging() {
            self.global_args
                .logging_config_with_default_file(invocation_dir, homes, false)
        } else {
            self.global_args.logging_config(invocation_dir, homes)
        }
    }

    // Candidate-only staging must validate its checkout before any file write.
    pub(crate) fn defers_file_logging(&self) -> bool {
        matches!(
            &self.command,
            Command::Source(SourceArgs {
                command: SourceCommand::Legacy(LegacySourceArgs {
                    command: LegacySourceCommand::FrozenPresetStage(_),
                }),
            })
        )
    }

    /// # Errors
    ///
    /// This function will return an error if the command fails.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        let invocation_dir = current_dir()?;
        self.invoke_in(cancellation_token, &invocation_dir, homes)
    }

    /// Invoke with an explicit base directory for relative command arguments.
    /// This is the production seam used by scenario tests; it does not mutate
    /// process-wide current-directory state.
    ///
    /// # Errors
    ///
    /// Returns an error when the selected command or invocation-directory
    /// resolution fails.
    pub fn invoke_in(
        self,
        cancellation_token: CancellationToken,
        invocation_dir: &Path,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        self.command
            .invoke_in(cancellation_token, invocation_dir, homes)
    }
}

/// Available commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum Command {
    /// Audit tracked Rust and Java source policy violations.
    Audit(super::audit::AuditArgs),
    /// Legacy name for the resumable validation matrix.
    CheckEverything(super::check_everything::CheckEverythingArgs),
    /// Synchronize selected generated projections.
    Sync(super::check_everything::CheckEverythingArgs),
    /// Incrementally compile and package selected projections.
    Build(super::check_everything::CheckEverythingArgs),
    /// Select and resume strict validation stages across catalog projections.
    Matrix(super::check_everything::CheckEverythingArgs),
    /// Run arbitrary gradle task(s) for each worktree in strict sequence
    Gradle(super::gradle::GradleArgs),
    /// Client instance tracking and management commands
    Client(super::client::ClientArgs),
    /// Build Linux container images from verified inputs.
    Container(super::container::ContainerArgs),
    /// Server instance tracking and management commands
    Server(super::server::ServerArgs),
    /// Git operation commands across all worktrees
    Git(super::git::GitArgs),
    /// Legacy GitHub releases; catalog preparation uses `source release plan`.
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
    /// Legacy Modrinth releases; catalog preparation uses `source release plan`.
    Modrinth(super::modrinth::ModrinthArgs),
    /// Jar directory and release artifact related commands
    Jar(super::jar::JarArgs),
    /// Build and launch catalog projections with their native loader
    Run(super::run::RunArgs),
    /// Discover and run SFM `GameTests`
    GameTest(super::game_test::GameTestArgs),
    /// Discover and run SFM game-puppet definitions
    Puppet(super::puppet::PuppetArgs),
    /// Discover and run Java `JUnit` tests
    Test(super::test::TestArgs),
    /// Inspect Java tests, game tests, or puppets in a generated project.
    ProjectCatalog(super::project_catalog::ProjectCatalogArgs),
    /// Navigate and refactor Java symbols.
    Symbol(super::symbol::SymbolArgs),
    /// Compute streaming file hashes without an external hashing tool.
    Hash(super::hash::HashArgs),
    /// Highlight exact source text or run the reusable syntax worker.
    Syntax(super::syntax::SyntaxArgs),
    /// Project version- and feature-specific Minecraft sources.
    Source(super::source::SourceArgs),
    /// Inspect portable, resumable release-review sessions.
    Review(super::review::ReviewArgs),
    /// Repo root related commands
    RepoRoot(super::repo_root::RepoRootArgs),
}

impl Command {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        let invocation_dir = current_dir()?;
        self.invoke_in(cancellation_token, &invocation_dir, homes)
    }

    /// Invoke with an explicit base directory for relative command arguments.
    ///
    /// # Errors
    ///
    /// Returns an error when the selected command fails.
    pub fn invoke_in(
        self,
        cancellation_token: CancellationToken,
        invocation_dir: &Path,
        homes: &InvocationHomes,
    ) -> eyre::Result<CliOutput> {
        let _legacy_release_admission = if self.requires_legacy_release_admission() {
            Some(LegacyReleaseAdmission::new(
                invocation_dir,
                homes.app_home(),
            )?)
        } else {
            None
        };
        let result = match self {
            Command::Audit(args) => legacy_output(args.invoke_in(invocation_dir, homes.app_home())),
            Command::CheckEverything(args) | Command::Matrix(args) => {
                args.invoke_in(&cancellation_token, invocation_dir)
            }
            Command::Sync(args) => args.invoke_sync_in(&cancellation_token, invocation_dir),
            Command::Build(args) => args.invoke_build_in(&cancellation_token, invocation_dir),
            Command::Gradle(args) => legacy_output(args.invoke(invocation_dir, homes)),
            Command::Client(args) => legacy_output(args.invoke(homes)),
            Command::Container(args) => args.invoke_in(&cancellation_token, invocation_dir, homes),
            Command::Server(args) => legacy_output(args.invoke(homes.app_home())),
            Command::Git(args) => legacy_output(args.invoke(homes.app_home())),
            Command::Github(args) => legacy_output(args.invoke(homes.app_home())),
            Command::Home(args) => legacy_output(args.invoke(homes.app_home())),
            Command::Cache(args) => {
                legacy_output(args.invoke_in(&InvocationDirectory::new(invocation_dir)?, homes))
            }
            Command::Curseforge(args) => legacy_output(args.invoke(homes.app_home())),
            Command::Dependency(args) => {
                legacy_output(args.invoke(cancellation_token, invocation_dir, homes))
            }
            Command::Jdk(args) => args.invoke_in(
                &cancellation_token,
                &InvocationDirectory::new(invocation_dir)?,
                homes,
            ),
            Command::Loader(args) => {
                legacy_output(args.invoke(homes.app_home(), homes.cache_home()?))
            }
            Command::Modrinth(args) => legacy_output(args.invoke(homes.app_home())),
            Command::Jar(args) => {
                legacy_output(args.invoke(cancellation_token, invocation_dir, homes))
            }
            Command::Run(args) => {
                legacy_output(args.invoke(cancellation_token, invocation_dir, homes))
            }
            Command::GameTest(args) => {
                legacy_output(args.invoke(cancellation_token, invocation_dir, homes))
            }
            Command::Puppet(args) => {
                legacy_output(args.invoke(cancellation_token, invocation_dir, homes))
            }
            Command::Test(args) => args.invoke(cancellation_token, invocation_dir, homes),
            Command::ProjectCatalog(args) => {
                legacy_output(args.invoke(cancellation_token, invocation_dir))
            }
            Command::Symbol(args) => args.invoke_in(&cancellation_token, invocation_dir, homes),
            Command::Hash(args) => args.invoke_in(&cancellation_token, invocation_dir),
            Command::Syntax(args) => args.invoke(&cancellation_token, invocation_dir),
            Command::Source(args) => args.invoke_in(&cancellation_token, invocation_dir, homes),
            Command::Review(args) => {
                args.invoke_with_cancellation(invocation_dir, &cancellation_token)
            }
            Command::RepoRoot(args) => legacy_output(
                args.invoke_in(&InvocationDirectory::new(invocation_dir)?, homes.app_home()),
            ),
        };
        result.map_err(|error| homes.explain_cache_error(error))
    }

    fn requires_legacy_release_admission(&self) -> bool {
        matches!(
            self,
            Command::Github(GithubArgs {
                command: GithubCommand::Release(_),
            }) | Command::Curseforge(CurseforgeArgs {
                command: CurseforgeCommand::Release(_),
            }) | Command::Modrinth(ModrinthArgs {
                command: ModrinthCommand::Release(_),
            }) | Command::Jar(JarArgs {
                command: JarCommand::Collect(_),
            }) | Command::Git(GitArgs {
                command: GitCommand::Tag(_),
            })
        )
    }
}

fn legacy_output(result: eyre::Result<()>) -> eyre::Result<CliOutput> {
    result?;
    Ok(CliOutput::none())
}

#[cfg(test)]
mod tests {
    use super::Cli;
    use super::Command;
    use crate::cli::jar::JarArgs;
    use crate::cli::jar::JarBuildOptionsArgs;
    use crate::cli::jar::JarCommand;
    use crate::jar_build::BuildMode;
    use crate::jar_build::BuildSelection;
    use crate::jar_build::ClientPuppetKeepOpen;
    use crate::jar_build::Parallelism;
    use crate::paths::AppHome;
    use crate::source_projection::core_catalog::write_project_catalog_fixture;
    use crate::source_projection::sfm_repo_path::SfmRepoPath;
    use gix::init as init_repository;
    use std::path::Path;
    use tempfile::tempdir;

    #[test]
    fn jar_build_discovers_projection_from_a_nested_invocation() -> eyre::Result<()> {
        let checkout = tempdir()?;
        init_repository(checkout.path())?;
        let projection = "sfm-dev/mc-1.19.2";
        write_project_catalog_fixture(checkout.path(), projection, "1.19.2")?;
        let invocation_dir = checkout.path().join("platform/minecraft");
        let cli = figue::from_slice::<Cli>(&[
            "jar",
            "build",
            "--projection",
            "sfm-dev/*",
            "--artifact-source",
            "local-maven",
        ])
        .into_result()?
        .get_silent();
        let Command::Jar(JarArgs {
            command: JarCommand::Build(command),
        }) = cli.command
        else {
            panic!("expected jar build command");
        };
        let app_home = AppHome(checkout.path().join("app-home"));
        let options = command.into_options(BuildMode::Build, &app_home, &invocation_dir)?;
        let BuildSelection::Projection {
            repo_root,
            projections,
        } = options.selection
        else {
            panic!("expected projection build targets");
        };
        assert_eq!(repo_root, SfmRepoPath::try_from(checkout.path())?);
        assert!(matches!(projections.as_slice(), [name] if name.as_str() == projection));
        assert_eq!(
            options.artifact_sources,
            [invocation_dir.join("local-maven")]
        );
        Ok(())
    }

    #[test]
    fn zero_parallelism_fails_before_checkout_discovery() -> eyre::Result<()> {
        let args = figue::from_slice::<JarBuildOptionsArgs>(&[
            "--projection",
            "sfm-dev/mc-1.19.2",
            "--parallel",
            "0",
        ])
        .into_result()?
        .get_silent();
        let invocation_dir = Path::new(if cfg!(windows) {
            "C:/fixture-invocation-directory"
        } else {
            "/fixture-invocation-directory"
        });
        let app_home = AppHome(invocation_dir.join("app-home"));
        let error = args
            .into_options(BuildMode::Build, &app_home, invocation_dir)
            .expect_err("zero parallelism should fail before checking the fixture path");
        assert_eq!(error.to_string(), "--parallel must be greater than zero");
        Ok(())
    }

    #[test]
    fn bare_parallelism_uses_the_default_worker_limit() -> eyre::Result<()> {
        assert_eq!(Parallelism::from_cli(None)?, Parallelism::Sequential);
        assert_eq!(
            Parallelism::from_cli(Some(None))?,
            Parallelism::Parallel {
                limit: Parallelism::DEFAULT_LIMIT
            }
        );
        Ok(())
    }

    #[test]
    fn client_puppet_keep_open_supports_durations_and_indefinite_waits() -> eyre::Result<()> {
        assert_eq!(
            ClientPuppetKeepOpen::from_cli(Some(None))?,
            ClientPuppetKeepOpen::Forever
        );
        for (duration, seconds) in [("5m", 300), ("90", 90)] {
            assert_eq!(
                ClientPuppetKeepOpen::from_cli(Some(Some(duration.to_owned())))?,
                ClientPuppetKeepOpen::Countdown { seconds }
            );
        }
        Ok(())
    }
}
