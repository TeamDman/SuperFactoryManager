use super::GithubReleaseCommand;
use facet::Facet;
use figue as args;

/// Arguments for GitHub release operations.
#[derive(Facet, Debug)]
pub struct GithubReleaseArgs {
    /// GitHub release subcommand.
    #[facet(args::subcommand)]
    pub command: GithubReleaseCommand,
}

impl GithubReleaseArgs {
    /// # Errors
    ///
    /// Returns an error if the selected GitHub release command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}
