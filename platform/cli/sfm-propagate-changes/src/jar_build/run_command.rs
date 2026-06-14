use super::BuildOptions;
use super::RunKind;

#[derive(Debug)]
pub struct RunCommand {
    options: BuildOptions,
    kind: RunKind,
}

impl RunCommand {
    #[must_use]
    pub fn new(options: BuildOptions, kind: RunKind) -> Self {
        Self { options, kind }
    }

    /// # Errors
    ///
    /// Returns an error if the clean-slate build graph cannot be resolved or launched.
    pub fn invoke(self) -> eyre::Result<()> {
        super::engine::invoke_run(&self.options, self.kind)
    }
}
