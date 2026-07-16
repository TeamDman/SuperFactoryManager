use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::cli::run::RunTestArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::SourceCatalogAction;
use crate::jar_build::SourceCatalogCategory;
use crate::jar_build::SourceCatalogCommand;
use crate::jar_build::SourceCatalogQuery;
use facet::Facet;
use figue as args;

/// Discover and run Java `JUnit` tests.
#[derive(Facet, Debug)]
pub struct TestArgs {
    /// Test subcommand.
    #[facet(args::subcommand)]
    pub command: TestCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum TestCommand {
    /// Compile and run Java `JUnit` tests.
    Run(RunTestArgs),
    /// List statically discovered Java `JUnit` methods.
    List(TestCatalogArgs),
    /// Show one statically discovered Java `JUnit` method.
    Show(TestShowArgs),
}

#[derive(Facet, Debug)]
pub struct TestCatalogArgs {
    /// Branch selector to inspect.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

#[derive(Facet, Debug)]
pub struct TestShowArgs {
    /// Branch selector to inspect.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Canonical `package.Class#method` test id.
    #[facet(args::positional)]
    pub id: String,
}

impl TestArgs {
    /// # Errors
    ///
    /// Returns an error if test execution or static cataloguing fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self.command {
            TestCommand::Run(args) => args.invoke(cancellation_token),
            TestCommand::List(args) => SourceCatalogCommand::new(
                args.options.into_options(BuildMode::Plan)?,
                SourceCatalogQuery {
                    category: SourceCatalogCategory::Test,
                    action: SourceCatalogAction::List,
                },
                cancellation_token,
            )
            .invoke(),
            TestCommand::Show(args) => SourceCatalogCommand::new(
                args.options.into_options(BuildMode::Plan)?,
                SourceCatalogQuery {
                    category: SourceCatalogCategory::Test,
                    action: SourceCatalogAction::Show { id: args.id },
                },
                cancellation_token,
            )
            .invoke(),
        }
    }
}
