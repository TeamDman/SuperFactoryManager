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
    require_single_worktree_target(query, &targets)
}

/// Require exactly one target from an already-discovered and filtered target slice.
///
/// # Errors
///
/// Returns an error if the slice is empty or contains more than one target.
pub fn require_single_worktree_target(
    query: &BranchQuery,
    targets: &[WorktreeTarget],
) -> eyre::Result<WorktreeTarget> {
    if targets.is_empty() {
        eyre::bail!("No worktrees match --branch '{query}'.");
    }
    if let [target] = targets {
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

#[cfg(test)]
mod tests {
    use super::require_single_worktree_target;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::BranchQuery;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use std::path::PathBuf;

    #[test]
    fn exact_target_cardinality_reports_zero_one_and_multiple() {
        let query = BranchQuery::parse("1.19.2").expect("query fixture");
        let zero =
            require_single_worktree_target(&query, &[]).expect_err("zero targets should fail");
        assert!(zero.to_string().contains("No worktrees match"));

        let one = target("1.19.2");
        assert_eq!(
            require_single_worktree_target(&query, std::slice::from_ref(&one)).expect("one target"),
            one
        );

        let multiple = require_single_worktree_target(
            &query,
            &[target("1.19.2"), target("feat/1.19.2/example")],
        )
        .expect_err("multiple targets should fail");
        assert!(multiple.to_string().contains("matched multiple worktrees"));
        assert!(
            multiple
                .to_string()
                .contains("select one branch explicitly")
        );
    }

    fn target(branch: &str) -> WorktreeTarget {
        WorktreeTarget {
            branch: BranchName::from(branch),
            worktree_path: WorktreePath::from(PathBuf::from(branch)),
            core: !branch.contains('/'),
            mc_version: None,
        }
    }
}
