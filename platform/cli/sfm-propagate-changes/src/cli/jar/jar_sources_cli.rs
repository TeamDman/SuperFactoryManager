use super::JarBuildOptionsArgs;
use crate::cancellation::CancellationToken;
use crate::jar_build::BuildMode;
use crate::jar_build::SourceOutputCommand;
use crate::jar_build::SourceOutputLayout;
use crate::jar_build::SourceOutputOptions;
use facet::Facet;
use figue as args;

/// Arguments for materializing transformed source outputs from the clean-slate toolchain.
#[derive(Facet, Debug, Clone)]
pub struct JarSourcesArgs {
    /// Build planning options used to resolve the target worktree and toolchain.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,

    /// Output either the transformed source jar or an expanded source file tree.
    #[facet(default, args::named)]
    pub layout: SourceOutputLayout,
}

impl JarSourcesArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<SourceOutputOptions> {
        Ok(SourceOutputOptions {
            build: self.options.into_options(mode)?,
            layout: self.layout,
        })
    }

    /// # Errors
    ///
    /// Returns an error if planning fails or the transformed source outputs cannot be produced.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        SourceOutputCommand::new(self.into_options(BuildMode::Build)?, cancellation_token).invoke()
    }
}
