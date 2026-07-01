use super::SourceOutputOptions;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct SourceOutputCommand {
    options: SourceOutputOptions,
    cancellation_token: CancellationToken,
}

impl SourceOutputCommand {
    #[must_use]
    pub fn new(options: SourceOutputOptions, cancellation_token: CancellationToken) -> Self {
        Self {
            options,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if transformed source outputs cannot be resolved or materialized.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_source_outputs(&self.options, &self.cancellation_token)
    }
}
