use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::GameTestBisectOptions;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use crate::jar_build::RunOptions;
use facet::Facet;
use figue as args;

/// Arguments for launching the Forge game test server userdev run config.
#[derive(Facet, Debug, Clone)]
pub struct RunGameTestServerArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,

    /// Run only SFM game tests matching this selector. Supports unqualified names, `*`, `?`, and comma-separated selectors.
    #[facet(default, args::named)]
    pub filter: Option<String>,

    /// Optional game-test-server subcommand. Defaults to running the selected game tests.
    #[facet(default, args::subcommand)]
    pub command: Option<RunGameTestServerCliCommand>,
}

/// Game-test-server subcommands.
#[derive(Facet, Debug, Clone)]
#[repr(u8)]
pub enum RunGameTestServerCliCommand {
    /// Find a smaller set of other SFM game tests that makes the target fail.
    Bisect(RunGameTestBisectArgs),
}

/// Arguments for bisecting a target SFM game test against other game tests.
#[derive(Facet, Debug, Clone)]
pub struct RunGameTestBisectArgs {
    /// Target SFM game test that must be included in every run.
    #[facet(args::positional)]
    pub target: String,

    /// Stop after this many game-test-server launches.
    #[facet(default, args::named)]
    pub max_runs: Option<usize>,
}

impl RunGameTestServerArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let filter = self.filter.clone();
        let game_test_bisect = self.command.as_ref().map(|command| match command {
            RunGameTestServerCliCommand::Bisect(args) => GameTestBisectOptions {
                target: args.target.clone(),
                max_runs: args.max_runs,
            },
        });
        RunCommand::with_run_options(
            self.into_options(BuildMode::Build)?,
            RunKind::GameTestServer,
            RunOptions {
                game_test_filter: filter,
                game_test_bisect,
                ..RunOptions::default()
            },
            cancellation_token,
        )
        .invoke()
    }
}
