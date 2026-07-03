use super::DependencyAddOptions;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct DependencyAddCommand {
    options: DependencyAddOptions,
    cancellation_token: CancellationToken,
}

impl DependencyAddCommand {
    #[must_use]
    pub fn new(options: DependencyAddOptions, cancellation_token: CancellationToken) -> Self {
        Self {
            options,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if the dependency lock entry cannot be updated.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_dependency_add(&self.options, &self.cancellation_token)
    }
}
