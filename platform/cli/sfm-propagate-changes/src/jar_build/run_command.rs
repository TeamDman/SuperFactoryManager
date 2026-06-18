use super::BuildOptions;
use super::RunKind;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct RunCommand {
    options: BuildOptions,
    kind: RunKind,
    cancellation_token: CancellationToken,
}

impl RunCommand {
    #[must_use]
    pub fn new(
        options: BuildOptions,
        kind: RunKind,
        cancellation_token: CancellationToken,
    ) -> Self {
        Self {
            options,
            kind,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate build graph cannot be resolved or launched.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_run(&self.options, self.kind, &self.cancellation_token)
    }
}
