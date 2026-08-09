use super::BuildOptions;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct BuildCommand {
    options: BuildOptions,
    cancellation_token: CancellationToken,
}

impl BuildCommand {
    #[must_use]
    pub fn new(options: BuildOptions, cancellation_token: CancellationToken) -> Self {
        Self {
            options,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate build graph cannot be resolved or executed.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_build(&self.options, &self.cancellation_token)
    }
}
