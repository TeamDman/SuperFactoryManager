use crate::cli::jar::BranchSelector;
use facet::Facet;
use figue as args;

/// Arguments for creating or updating a GitHub release.
#[derive(Facet, Debug)]
pub struct GithubReleaseNowArgs {
    /// Branch selector used to choose release jar Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// GitHub repository in owner/name form.
    #[facet(default, args::named)]
    pub repo: Option<String>,

    /// Print the resolved release plan without calling `gh`.
    #[facet(default, args::named)]
    pub dry_run: bool,

    /// Skip the interactive confirmation prompt.
    #[facet(default, args::named)]
    pub yes: bool,
}

impl GithubReleaseNowArgs {
    /// # Errors
    ///
    /// Returns an error if the release plan cannot be resolved or applied.
    pub fn invoke(self) -> eyre::Result<()> {
        super::github_cli::release_now(self.branch, self.repo, self.dry_run, self.yes)
    }
}
