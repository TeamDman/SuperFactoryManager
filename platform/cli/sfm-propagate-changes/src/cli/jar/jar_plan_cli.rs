use super::JarBuildOptionsArgs;
use crate::jar_build::BuildCommand;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use facet::Facet;

/// Arguments for resolving the clean-slate jar build graph.
#[derive(Facet, Debug, Clone)]
pub struct JarPlanArgs {
    /// Jar build planning options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

impl JarPlanArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate plan cannot be resolved or written.
    pub fn invoke(self) -> eyre::Result<()> {
        BuildCommand::new(self.into_options(BuildMode::Plan)?).invoke()
    }
}
