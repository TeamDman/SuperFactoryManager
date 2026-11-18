use super::WorktreeTarget;
use crate::paths::AppHome;
use crate::worktree::get_sorted_worktrees;

/// Discover Git worktrees and convert them to branch-query targets.
///
/// # Errors
///
/// Returns an error if Git worktree discovery fails or a discovered worktree has an invalid
/// `minecraft_version` property.
pub fn discover_worktree_targets(app_home: &AppHome) -> eyre::Result<Vec<WorktreeTarget>> {
    get_sorted_worktrees(app_home)?
        .into_iter()
        .map(WorktreeTarget::from_worktree)
        .collect()
}
