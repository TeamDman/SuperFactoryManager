use super::BuildOptions;
use super::RunTestOptions;
use crate::cancellation::CancellationToken;

#[derive(Debug)]
pub struct RunTestCommand {
    options: BuildOptions,
    test_options: RunTestOptions,
    cancellation_token: CancellationToken,
}

impl RunTestCommand {
    #[must_use]
    pub fn new(
        options: BuildOptions,
        test_options: RunTestOptions,
        cancellation_token: CancellationToken,
    ) -> Self {
        Self {
            options,
            test_options,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate build graph cannot be resolved or JUnit execution fails.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_run_test(&self.options, &self.test_options, &self.cancellation_token)
    }
}
