use facet::Facet;
use figue::{self as args};

/// Arguments for git operations across all worktrees.
#[derive(Facet, Debug)]
pub struct GitArgs {
    /// Git subcommand.
    #[facet(args::subcommand)]
    pub command: GitCommand,
}

impl GitArgs {
    /// # Errors
    ///
    /// Returns an error if the selected git command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// Git operation commands across all worktrees
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum GitCommand {
    /// Stage pathspecs in each worktree
    Add(super::git_add_cli::GitAddArgs),
    /// Commit staged changes in each worktree
    Commit(super::git_commit_cli::GitCommitArgs),
    /// Propagate changes by merging from older to newer version branches
    Merge(super::merge::MergeArgs),
    /// Push branches (runs `git push` in each worktree)
    Push(super::push::PushArgs),
    /// Show git status for all worktrees
    Status(super::status::GitStatusArgs),
    /// Tag each branch as `<mod_version>-<mc_version>`
    Tag(super::git_tag_cli::GitTagArgs),
}

impl GitCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            GitCommand::Add(args) => args.invoke(),
            GitCommand::Commit(args) => args.invoke(),
            GitCommand::Merge(args) => args.invoke(),
            GitCommand::Push(args) => args.invoke(),
            GitCommand::Status(args) => args.invoke(),
            GitCommand::Tag(args) => args.invoke(),
        }
    }
}
