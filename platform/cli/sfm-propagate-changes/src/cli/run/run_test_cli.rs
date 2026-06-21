use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::RunTestAction;
use crate::jar_build::RunTestCommand as JarBuildRunTestCommand;
use crate::jar_build::RunTestOptions;
use facet::Facet;
use figue as args;

/// Arguments for compiling and running the Java `JUnit` test source set.
#[derive(Facet, Debug, Clone)]
pub struct RunTestArgs {
    /// Build and test options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,

    /// Show captured stdout/stderr from successful tests.
    #[facet(rename = "no-capture", default = false, args::named)]
    pub no_capture: bool,

    /// Run or list only tests whose display name, class, method, or unique id contains this text.
    #[facet(default, args::named)]
    pub filter: Option<String>,

    /// Optional test subcommand. Defaults to running tests.
    #[facet(default, args::subcommand)]
    pub command: Option<RunTestCliCommand>,
}

/// Java `JUnit` test subcommands.
#[derive(Facet, Debug, Clone)]
#[repr(u8)]
pub enum RunTestCliCommand {
    /// List discovered Java `JUnit` tests without running them.
    List(RunTestListArgs),
}

/// Arguments for listing Java `JUnit` tests.
#[derive(Facet, Debug, Clone, Default)]
pub struct RunTestListArgs;

impl RunTestCliCommand {
    const fn action(&self) -> RunTestAction {
        match self {
            RunTestCliCommand::List(_) => RunTestAction::List,
        }
    }
}

impl RunTestArgs {
    /// # Errors
    ///
    /// Returns an error if planning, building, compiling tests, or running `JUnit` fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let Self {
            options,
            no_capture,
            filter,
            command,
        } = self;
        let action = command
            .as_ref()
            .map_or(RunTestAction::Run, RunTestCliCommand::action);
        JarBuildRunTestCommand::new(
            options.into_options(BuildMode::Build)?,
            RunTestOptions {
                action,
                filter,
                no_capture,
            },
            cancellation_token,
        )
        .invoke()
    }
}
