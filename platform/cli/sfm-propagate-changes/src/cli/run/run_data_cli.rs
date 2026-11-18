use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use crate::jar_build::RunOptions;
use crate::paths::AppHome;
use facet::Facet;
use figue as args;

/// Arguments for launching the Forge datagen userdev run config.
#[derive(Facet, Debug, Clone)]
pub struct RunDataArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Persistent runtime slot; defaults to the lowest free positive slot.
    #[facet(default, args::named)]
    pub runtime_slot: Option<u32>,
    /// Share the explicit runtime slot, retaining Minecraft's native world lock.
    #[facet(default, args::named)]
    pub share_runtime: bool,
}

impl RunDataArgs {
    pub(crate) fn into_options(
        self,
        mode: BuildMode,
        app_home: &AppHome,
    ) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode, app_home)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        let app_home = homes.app_home();
        eyre::ensure!(
            !self.share_runtime || self.runtime_slot.is_some(),
            "--share-runtime requires explicit --runtime-slot"
        );
        let run_options = RunOptions {
            runtime_slot: self.runtime_slot,
            share_runtime: self.share_runtime,
            ..RunOptions::default()
        };
        RunCommand::with_run_options(
            self.into_options(BuildMode::Build, app_home)?
                .with_cache_home(homes.available_cache_home()),
            RunKind::Data,
            run_options,
            cancellation_token,
        )
        .invoke()
    }
}
