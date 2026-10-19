use super::JarBuildOptionsArgs;
use crate::cancellation::CancellationToken;
use crate::jar_build::BuildCommand;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use facet::Facet;
use figue as args;

/// Arguments for building an SFM mod jar without invoking Gradle.
#[derive(Facet, Debug, Clone)]
pub struct JarBuildArgs {
    /// Jar build options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,

    /// Create-only .jar filename in one projection's existing build/libs directory; existing outputs are refused.
    #[facet(default, args::named)]
    pub output_name: Option<String>,
}

impl JarBuildArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        let mut options = self.options.into_options(mode)?;
        options.output_name = self.output_name;
        Ok(options)
    }

    /// # Errors
    ///
    /// Returns an error if planning fails or an unsupported build node is reached.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        BuildCommand::new(self.into_options(BuildMode::Build)?, cancellation_token).invoke()
    }
}
