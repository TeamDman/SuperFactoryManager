use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::cli::run::RunClientPuppetArgs;
use crate::cli::run::RunGameTestServerArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::SourceCatalogAction;
use crate::jar_build::SourceCatalogCategory;
use crate::jar_build::SourceCatalogCommand;
use crate::jar_build::SourceCatalogQuery;
use facet::Facet;
use figue as args;

/// Discover and run SFM `GameTests`.
#[derive(Facet, Debug)]
pub struct GameTestArgs {
    /// `GameTest` subcommand.
    #[facet(args::subcommand)]
    pub command: GameTestCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum GameTestCommand {
    /// Run SFM `GameTests` in an integrated client.
    RunClient(RunClientPuppetArgs),
    /// Run SFM `GameTests` in the dedicated game-test server.
    RunServer(RunGameTestServerArgs),
    /// List static SFM `GameTests` and dynamic generators.
    List(GameTestCatalogArgs),
    /// Show one static SFM `GameTest` or generator entry.
    Show(GameTestShowArgs),
}

#[derive(Facet, Debug)]
pub struct GameTestCatalogArgs {
    /// Branch selector to inspect.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

#[derive(Facet, Debug)]
pub struct GameTestShowArgs {
    /// Branch selector to inspect.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Canonical SFM test id or dynamic generator id.
    #[facet(args::positional)]
    pub id: String,
}

impl GameTestArgs {
    /// # Errors
    ///
    /// Returns an error if `GameTest` execution or static cataloguing fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self.command {
            GameTestCommand::RunClient(args) => args.invoke(cancellation_token),
            GameTestCommand::RunServer(args) => args.invoke(cancellation_token),
            GameTestCommand::List(args) => SourceCatalogCommand::new(
                args.options.into_options(BuildMode::Plan)?,
                SourceCatalogQuery {
                    category: SourceCatalogCategory::GameTest,
                    action: SourceCatalogAction::List,
                },
                cancellation_token,
            )
            .invoke(),
            GameTestCommand::Show(args) => SourceCatalogCommand::new(
                args.options.into_options(BuildMode::Plan)?,
                SourceCatalogQuery {
                    category: SourceCatalogCategory::GameTest,
                    action: SourceCatalogAction::Show { id: args.id },
                },
                cancellation_token,
            )
            .invoke(),
        }
    }
}
