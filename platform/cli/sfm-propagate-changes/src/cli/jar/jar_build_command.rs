use crate::cli::jar::BranchSelector;
use crate::jar_build::BuildCommand;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Options shared by `jar plan` and `jar build`.
#[derive(Facet, Debug, Clone)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "This type is a thin CLI flag container; each bool maps directly to a named flag."
)]
pub struct JarBuildCommand {
    /// Branch selector to build. Defaults to `core`.
    #[facet(default, args::named)]
    pub branch: BranchSelector,

    /// Ignore reusable SFM-owned cache state and recompute resolved metadata.
    #[facet(default = false, args::named)]
    pub refresh: bool,

    /// Print the reason each graph node is considered dirty.
    #[facet(rename = "explain-rebuild", default = false, args::named)]
    pub explain_rebuild: bool,

    /// Optional path to write the resolved build plan JSON.
    #[facet(rename = "plan-json", default, args::named)]
    pub plan_json: Option<PathBuf>,

    /// Optional Java home to use for tool execution. Defaults to `JAVA_HOME`, then java on PATH.
    #[facet(rename = "java-home", default, args::named)]
    pub java_home: Option<PathBuf>,

    /// Resolve and prepare as much as possible, then skip the final build or launch action.
    #[facet(rename = "dry-run", default = false, args::named)]
    pub dry_run: bool,

    /// Allow bootstrapping missing artifacts from local .m2 or Gradle module caches.
    #[facet(rename = "allow-local-artifact-cache", default = false, args::named)]
    pub allow_local_artifact_cache: bool,

    /// Failure behavior for multi-target selectors: `bail` or `continue`.
    #[facet(rename = "error-action", default, args::named)]
    pub error_action: ErrorAction,

    /// Run matching targets in parallel. Bare `--parallel` defaults to 10 before parsing.
    #[facet(default, args::named)]
    pub parallel: Option<usize>,
}

impl JarBuildCommand {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        Ok(BuildOptions {
            branch: self.branch.into_query()?,
            refresh: self.refresh,
            explain_rebuild: self.explain_rebuild,
            plan_json: self.plan_json,
            java_home: self.java_home,
            dry_run: self.dry_run,
            allow_local_artifact_cache: self.allow_local_artifact_cache,
            error_action: self.error_action,
            parallelism: Parallelism::from_cli(self.parallel)?,
            mode,
        })
    }
}

/// Run `jar plan`.
///
/// # Errors
///
/// Returns an error if the clean-slate plan cannot be resolved or written.
pub(crate) fn invoke_plan(command: JarBuildCommand) -> eyre::Result<()> {
    BuildCommand::new(command.into_options(BuildMode::Plan)?).invoke()
}

/// Run `jar build`.
///
/// # Errors
///
/// Returns an error if planning fails or an unsupported build node is reached.
pub(crate) fn invoke_build(command: JarBuildCommand) -> eyre::Result<()> {
    BuildCommand::new(command.into_options(BuildMode::Build)?).invoke()
}

/// Run the Forge client userdev launch.
///
/// # Errors
///
/// Returns an error if planning, building, or launching fails.
pub(crate) fn invoke_run_client(command: JarBuildCommand) -> eyre::Result<()> {
    RunCommand::new(command.into_options(BuildMode::Build)?, RunKind::Client).invoke()
}

/// Run the Forge client userdev launch and exit when the title screen opens.
///
/// # Errors
///
/// Returns an error if planning, building, launching, or title-screen detection fails.
pub(crate) fn invoke_run_client_smoke(command: JarBuildCommand) -> eyre::Result<()> {
    RunCommand::new(
        command.into_options(BuildMode::Build)?,
        RunKind::ClientSmoke,
    )
    .invoke()
}

/// Run the Forge client userdev launch and execute SFM game tests in an integrated client.
///
/// # Errors
///
/// Returns an error if planning, building, launching, or game-test validation fails.
pub(crate) fn invoke_run_client_puppet(command: JarBuildCommand) -> eyre::Result<()> {
    RunCommand::new(
        command.into_options(BuildMode::Build)?,
        RunKind::ClientPuppet,
    )
    .invoke()
}

/// Run the Forge server userdev launch.
///
/// # Errors
///
/// Returns an error if planning, building, or launching fails.
pub(crate) fn invoke_run_server(command: JarBuildCommand) -> eyre::Result<()> {
    RunCommand::new(command.into_options(BuildMode::Build)?, RunKind::Server).invoke()
}

/// Run the Forge datagen userdev launch.
///
/// # Errors
///
/// Returns an error if planning, building, or launching fails.
pub(crate) fn invoke_run_data(command: JarBuildCommand) -> eyre::Result<()> {
    RunCommand::new(command.into_options(BuildMode::Build)?, RunKind::Data).invoke()
}

/// Run the Forge game test server userdev launch.
///
/// # Errors
///
/// Returns an error if planning, building, or launching fails.
pub(crate) fn invoke_run_game_test_server(command: JarBuildCommand) -> eyre::Result<()> {
    RunCommand::new(
        command.into_options(BuildMode::Build)?,
        RunKind::GameTestServer,
    )
    .invoke()
}
