use super::CompareOptions;

#[derive(Debug)]
pub struct CompareCommand {
    options: CompareOptions,
}

impl CompareCommand {
    #[must_use]
    pub fn new(options: CompareOptions) -> Self {
        Self { options }
    }

    /// # Errors
    ///
    /// Returns an error if either jar cannot be read or normalized differences are found.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_compare(&self.options)
    }
}
