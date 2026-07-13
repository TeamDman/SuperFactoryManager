use crate::cli::jar::BranchSelector;
use facet::Facet;
use figue as args;

/// Gradle command - runs arbitrary gradle tasks in each worktree in strict sequence.
#[derive(Facet, Debug)]
pub struct GradleRunArgs {
    /// Gradle tasks to run (for example: `runData`, `runGameTestServer`, `test`).
    #[facet(args::positional)]
    pub tasks: Vec<String>,

    /// Branch selector for worktrees.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// If set, stream gradle stdout/stderr to the console while tasks run.
    ///
    /// By default logs are written to cache files only and not streamed.
    #[facet(args::named, default = false)]
    pub show_logs: bool,

    /// If set, continue with later branches after a task failure.
    ///
    /// Remaining tasks for the failed branch are marked as skipped.
    #[facet(args::named, default = false)]
    pub continue_on_error: bool,
}

impl GradleRunArgs {
    /// # Errors
    ///
    /// Returns an error if any selected Gradle task fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.invoke_with_runtime()
    }
}
