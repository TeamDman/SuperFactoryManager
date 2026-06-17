use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use facet::Facet;

/// Arguments for launching the Forge client and running SFM game tests.
#[derive(Facet, Debug, Clone)]
pub struct RunClientPuppetArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

impl RunClientPuppetArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, launching, or game-test validation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        RunCommand::new(self.into_options(BuildMode::Build)?, RunKind::ClientPuppet).invoke()
    }
}
