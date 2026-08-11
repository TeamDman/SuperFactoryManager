use facet::Facet;

/// Arguments for showing summary counts only.
#[derive(Facet, Debug, Default)]
pub struct StatusSummaryArgs;

impl StatusSummaryArgs {
    /// # Errors
    ///
    /// Returns an error if getting worktree status fails.
    pub fn invoke(self) -> eyre::Result<()> {
        super::git_status_cli::run_summary()
    }
}
