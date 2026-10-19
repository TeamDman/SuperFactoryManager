use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::RunCommand;
use crate::jar_build::RunKind;
use crate::jar_build::RunOptions;
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
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, or launching fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
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
            self.into_options(BuildMode::Build)?,
            RunKind::Data,
            run_options,
            cancellation_token,
        )
        .invoke()
    }
}
