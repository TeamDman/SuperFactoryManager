use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use facet::Facet;

/// Arguments for launching the Forge client userdev run config.
#[derive(Facet, Debug, Clone)]
pub struct RunClientArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

impl RunClientArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(self) -> eyre::Result<()> {
        RunCommand::new(self.into_options(BuildMode::Build)?, RunKind::Client).invoke()
    }
}
