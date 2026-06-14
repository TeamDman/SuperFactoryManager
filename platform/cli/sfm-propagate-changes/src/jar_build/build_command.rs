use super::BuildOptions;

#[derive(Debug)]
pub struct BuildCommand {
    options: BuildOptions,
}

impl BuildCommand {
    #[must_use]
    pub fn new(options: BuildOptions) -> Self {
        Self { options }
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate build graph cannot be resolved or executed.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_build(&self.options)
    }
}
