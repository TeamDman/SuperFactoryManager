use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::cli::puppet_artifacts::PuppetArtifactsArgs;
use crate::cli::puppet_matrix::PuppetMatrixArgs;
use crate::cli::run::invoke_game_puppet;
use crate::jar_build::BuildMode;
use crate::jar_build::SourceCatalogAction;
use crate::jar_build::SourceCatalogCategory;
use crate::jar_build::SourceCatalogCommand;
use crate::jar_build::SourceCatalogQuery;
use facet::Facet;
use figue as args;

/// Discover and run SFM game-puppet definitions.
#[derive(Facet, Debug)]
pub struct PuppetArgs {
    /// Puppet subcommand.
    #[facet(args::subcommand)]
    pub command: PuppetCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum PuppetCommand {
    /// Launch selected SFM game puppets in a client preview.
    Run(PuppetRunArgs),
    /// List statically discovered puppet definitions.
    List(PuppetCatalogArgs),
    /// Show one statically discovered puppet definition.
    Show(PuppetShowArgs),
    /// Locate or open existing game-puppet preview artifacts.
    Artifacts(PuppetArtifactsArgs),
    /// Run selected puppets across branches and collect a durable preview matrix.
    Matrix(PuppetMatrixArgs),
}

#[derive(Facet, Debug)]
pub struct PuppetRunArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Puppet selector. Supports unqualified names, `*`, `?`, and comma-separated selectors.
    #[facet(args::positional)]
    pub puppet: String,
    /// Exact SFM `GameTest` id supplied to a parameterized puppet. Accepts `sfm:<name>` or `<name>`.
    #[facet(default, args::named)]
    pub game_test: Option<String>,
    /// Window width used for native screenshot captures.
    #[facet(default, args::named)]
    pub width: Option<u16>,
    /// Window height used for native screenshot captures.
    #[facet(default, args::named)]
    pub height: Option<u16>,
    /// Viewport variants to run: declared, preferred, or WIDTHxHEIGHT@auto|SCALE.
    #[facet(default = "declared", args::named)]
    pub variant: String,
    /// Mute Minecraft audio during the puppet run by default. Use `--no-mute` to hear it.
    #[facet(default = true, args::named)]
    pub mute: bool,
    /// Hold the final puppet world. Bare keeps it open forever; values accept humantime durations.
    #[facet(default, args::named)]
    pub keep_open: Option<Option<String>>,
}

#[derive(Facet, Debug)]
pub struct PuppetCatalogArgs {
    /// Branch selector to inspect.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

#[derive(Facet, Debug)]
pub struct PuppetShowArgs {
    /// Branch selector to inspect.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Canonical `sfm:<name>` puppet id.
    #[facet(args::positional)]
    pub id: String,
}

impl PuppetArgs {
    /// # Errors
    ///
    /// Returns an error if discovery, build, or preview execution fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        match self.command {
            PuppetCommand::Run(args) => invoke_game_puppet(
                args.options,
                &args.puppet,
                args.game_test,
                args.width,
                args.height,
                &args.variant,
                args.mute,
                args.keep_open,
                cancellation_token,
            ),
            PuppetCommand::List(args) => SourceCatalogCommand::new(
                args.options.into_options(BuildMode::Plan)?,
                SourceCatalogQuery {
                    category: SourceCatalogCategory::Puppet,
                    action: SourceCatalogAction::List,
                },
                cancellation_token,
            )
            .invoke(),
            PuppetCommand::Show(args) => SourceCatalogCommand::new(
                args.options.into_options(BuildMode::Plan)?,
                SourceCatalogQuery {
                    category: SourceCatalogCategory::Puppet,
                    action: SourceCatalogAction::Show { id: args.id },
                },
                cancellation_token,
            )
            .invoke(),
            PuppetCommand::Artifacts(args) => args.invoke(),
            PuppetCommand::Matrix(args) => args.invoke(&cancellation_token),
        }
    }
}
