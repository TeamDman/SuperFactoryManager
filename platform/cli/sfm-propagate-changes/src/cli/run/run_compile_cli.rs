use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::RunTestAction;
use crate::jar_build::RunTestCommand as JarBuildRunTestCommand;
use crate::jar_build::RunTestOptions;
use facet::Facet;

/// Arguments for compiling all Java source sets without launching userdev or tests.
#[derive(Facet, Debug, Clone)]
pub struct RunCompileArgs {
    /// Build and compile options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
}

impl RunCompileArgs {
    /// # Errors
    ///
    /// Returns an error if planning, building, or Java compilation fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        JarBuildRunTestCommand::new(
            self.options.into_options(BuildMode::Build)?,
            RunTestOptions {
                action: RunTestAction::Compile,
                filter: None,
                no_capture: false,
            },
            cancellation_token,
        )
        .invoke()
    }
}
