use crate::cli::jar::BranchSelector;
use facet::Facet;
use figue as args;

/// Arguments for amending an existing GitHub release.
#[derive(Facet, Debug)]
pub struct GithubReleaseAmendArgs {
    /// Branch selector used to choose release jar Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// GitHub repository in owner/name form.
    #[facet(default, args::named)]
    pub repo: Option<String>,

    /// Print the resolved amend plan without calling `gh`.
    #[facet(default, args::named)]
    pub dry_run: bool,

    /// Skip the interactive confirmation prompt.
    #[facet(default, args::named)]
    pub yes: bool,
}

impl GithubReleaseAmendArgs {
    /// # Errors
    ///
    /// Returns an error if the release amend plan cannot be resolved or applied.
    pub fn invoke(self) -> eyre::Result<()> {
        super::github_cli::release_amend(self.branch, self.repo, self.dry_run, self.yes)
    }
}
