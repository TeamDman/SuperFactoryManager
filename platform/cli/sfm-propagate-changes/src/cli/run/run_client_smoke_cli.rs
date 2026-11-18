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

/// Arguments for launching the Forge client and exiting when the title screen opens.
#[derive(Facet, Debug, Clone)]
pub struct RunClientSmokeArgs {
    /// Build and launch options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,

    /// Launch SFM without dependency mod jars declared by the schema v3 lockfile.
    #[facet(default, args::named)]
    pub solo: bool,
}

impl RunClientSmokeArgs {
    pub(crate) fn into_options(
        self,
        mode: BuildMode,
        app_home: &AppHome,
    ) -> eyre::Result<BuildOptions> {
        self.options.into_options(mode, app_home)
    }

    /// # Errors
    ///
    /// Returns an error if planning, building, launching, or title-screen detection fails.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        homes: &InvocationHomes,
    ) -> eyre::Result<()> {
        let app_home = homes.app_home();
        let solo = self.solo;
        RunCommand::with_run_options(
            self.into_options(BuildMode::Build, app_home)?
                .with_cache_home(homes.available_cache_home()),
            RunKind::ClientSmoke,
            RunOptions {
                client_solo: solo,
                ..RunOptions::default()
            },
            cancellation_token,
        )
        .invoke()
    }
}
