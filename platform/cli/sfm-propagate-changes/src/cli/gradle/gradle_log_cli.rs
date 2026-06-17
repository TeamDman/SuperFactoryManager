use crate::cli::gradle::GradleLogsCommand;
use facet::Facet;
use figue as args;

/// Arguments for Gradle log inspection.
#[derive(Facet, Debug)]
pub struct GradleLogArgs {
    /// Log inspection subcommand.
    #[facet(args::subcommand)]
    pub command: GradleLogsCommand,
}

impl GradleLogArgs {
    /// # Errors
    ///
    /// Returns an error if the selected Gradle log command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}
