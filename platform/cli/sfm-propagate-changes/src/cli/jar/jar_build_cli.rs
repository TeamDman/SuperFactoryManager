use super::JarBuildOptionsArgs;
use crate::cancellation::CancellationToken;
use crate::jar_build::BuildCommand;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use facet::Facet;

/// Arguments for building an SFM mod jar without invoking Gradle.
#[derive(Facet, Debug, Clone)]
pub struct JarBuildArgs {
    /// Jar build options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

impl JarBuildArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning fails or an unsupported build node is reached.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        BuildCommand::new(self.into_options(BuildMode::Build)?, cancellation_token).invoke()
    }
}
