use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use facet::Facet;

/// Arguments for launching the Forge game test server userdev run config.
#[derive(Facet, Debug, Clone)]
pub struct RunGameTestServerArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

impl RunGameTestServerArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        RunCommand::new(
            self.into_options(BuildMode::Build)?,
            RunKind::GameTestServer,
            cancellation_token,
        )
        .invoke()
    }
}
