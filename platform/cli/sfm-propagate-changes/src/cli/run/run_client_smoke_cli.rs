use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use facet::Facet;

/// Arguments for launching the Forge client and exiting when the title screen opens.
#[derive(Facet, Debug, Clone)]
pub struct RunClientSmokeArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

impl RunClientSmokeArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, launching, or title-screen detection fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        RunCommand::new(
            self.into_options(BuildMode::Build)?,
            RunKind::ClientSmoke,
            cancellation_token,
        )
        .invoke()
    }
}
