use facet::Facet;
use figue as args;

/// Arguments for showing only dirty worktrees.
#[derive(Facet, Debug, Default)]
pub struct StatusDirtyArgs {
    /// Show short status (hide untracked file details).
    #[facet(default, args::named, args::short = 's')]
    pub short: bool,
}

impl StatusDirtyArgs {
    /// # Errors
    ///
    /// Returns an error if getting worktree status fails.
    pub fn invoke(self) -> eyre::Result<()> {
        super::git_status_cli::run_dirty(self.short)
    }
}
