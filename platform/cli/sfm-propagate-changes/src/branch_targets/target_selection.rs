use super::BranchQuery;
use super::WorktreeTarget;
use super::discover_worktree_targets;

/// Resolve a branch query against discovered worktrees.
///
/// # Errors
///
/// Returns an error if worktree discovery fails.
pub fn select_worktree_targets(query: &BranchQuery) -> eyre::Result<Vec<WorktreeTarget>> {
    Ok(discover_worktree_targets()?
        .into_iter()
        .filter(|target| query.matches(target))
        .collect())
}

/// Resolve a branch query against discovered worktrees and require at least one match.
///
/// # Errors
///
/// Returns an error if worktree discovery fails or no worktrees match.
pub fn select_required_worktree_targets(query: &BranchQuery) -> eyre::Result<Vec<WorktreeTarget>> {
    let targets = select_worktree_targets(query)?;
    if targets.is_empty() {
        eyre::bail!("No worktrees match --branch '{query}'.");
    }
    Ok(targets)
}

/// Resolve a branch query that must identify exactly one worktree.
///
/// # Errors
///
/// Returns an error if no worktrees match, or if the query matches more than one worktree.
pub fn select_single_worktree_target(query: &BranchQuery) -> eyre::Result<WorktreeTarget> {
    let targets = select_required_worktree_targets(query)?;
    if let [target] = targets.as_slice() {
        return Ok(target.clone());
    }

    let matched = targets
        .iter()
        .map(|target| target.branch.as_str())
        .collect::<Vec<_>>()
        .join(", ");
    eyre::bail!(
        "--branch '{query}' matched multiple worktrees ({matched}); select one branch explicitly for this command."
    );
}
