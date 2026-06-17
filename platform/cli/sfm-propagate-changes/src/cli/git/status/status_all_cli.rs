use facet::Facet;
use figue as args;

/// Arguments for showing status for all worktrees.
#[derive(Facet, Debug, Default)]
pub struct StatusAllArgs {
    /// Show short status (hide untracked file details).
    #[facet(default, args::named, args::short = 's')]
    pub short: bool,
}

impl StatusAllArgs {
    /// # Errors
    ///
    /// Returns an error if getting worktree status fails.
    pub fn invoke(self) -> eyre::Result<()> {
        super::git_status_cli::run_all(self.short)
    }
}
