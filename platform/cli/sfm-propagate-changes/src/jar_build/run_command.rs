use super::BuildOptions;
use super::RunKind;
use super::RunOptions;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct RunCommand {
    options: BuildOptions,
    kind: RunKind,
    run_options: RunOptions,
    cancellation_token: CancellationToken,
}

impl RunCommand {
    #[must_use]
    pub fn new(
        options: BuildOptions,
        kind: RunKind,
        cancellation_token: CancellationToken,
    ) -> Self {
        Self::with_run_options(options, kind, RunOptions::default(), cancellation_token)
    }

    #[must_use]
    pub fn with_run_options(
        options: BuildOptions,
        kind: RunKind,
        run_options: RunOptions,
        cancellation_token: CancellationToken,
    ) -> Self {
        Self {
            options,
            kind,
            run_options,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate build graph cannot be resolved or launched.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_run(
            &self.options,
            self.kind,
            &self.run_options,
            &self.cancellation_token,
        )
    }
}
