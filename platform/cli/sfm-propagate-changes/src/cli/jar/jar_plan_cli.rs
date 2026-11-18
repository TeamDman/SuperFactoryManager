use super::JarBuildOptionsArgs;
use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::jar_build::BuildCommand;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for resolving the clean-slate jar build graph.
#[derive(Facet, Debug, Clone)]
pub struct JarPlanArgs {
    /// Jar build planning options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,

    /// Plan a create-only .jar filename in one projection's existing build/libs directory; existing outputs are refused.
    #[facet(default, args::named)]
    pub output_name: Option<String>,
}

impl JarPlanArgs {
    pub(crate) fn into_options(
        self,
        mode: BuildMode,
        app_home: &AppHome,
    ) -> eyre::Result<BuildOptions> {
        let mut options = self.options.into_options(mode, app_home)?;
        options.output_name = self.output_name;
        Ok(options)
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate plan cannot be resolved or written.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        let mut options = self.into_options(BuildMode::Plan, homes.app_home())?;
        options.cache_home = homes.available_cache_home().cloned();
        BuildCommand::new(options, cancellation_token).invoke()
    }
}
