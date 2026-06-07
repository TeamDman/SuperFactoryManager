use facet::Facet;
use figue::{self as args};

/// Git operation commands across all worktrees
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum GitCommand {
    /// Propagate changes by merging from older to newer version branches
    Merge {
        /// Merge options
        #[facet(flatten)]
        command: super::merge::MergeCommand,
    },
    /// Push branches (runs `git push` in each worktree)
    Push {
        /// Push options
        #[facet(flatten)]
        command: super::push::PushCommand,
    },
    /// Show git status for all worktrees
    Status {
        /// Status subcommand
        #[facet(default, args::subcommand)]
        command: Option<super::status::StatusCommand>,
    },
    /// Tag each branch as `<mod_version>-<mc_version>`
    Tag {
        /// Tag options
        #[facet(flatten)]
        command: super::git_tag_command::TagCommand,
    },
}

impl GitCommand {
    /// # Errors
    ///
    /// This function will return an error if the subcommand fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            GitCommand::Merge { command } => command.invoke(),
            GitCommand::Push { command } => command.invoke(),
            GitCommand::Status { command } => command.unwrap_or_default().invoke(),
            GitCommand::Tag { .. } => super::git_tag_command::TagCommand::invoke(),
        }
    }
}
