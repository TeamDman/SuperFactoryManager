use super::CompareOptions;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct CompareCommand {
    options: CompareOptions,
    cancellation_token: CancellationToken,
}

impl CompareCommand {
    #[must_use]
    pub fn new(options: CompareOptions, cancellation_token: CancellationToken) -> Self {
        Self {
            options,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if either jar cannot be read or normalized differences are found.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_compare(&self.options, &self.cancellation_token)
    }
}
