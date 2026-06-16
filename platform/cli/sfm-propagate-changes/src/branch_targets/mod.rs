mod branch_conjunction;
mod branch_glob;
mod branch_logical_op;
mod branch_name;
mod branch_query;
mod branch_rule;
mod exact_branch;
mod minecraft_version;
mod target_discovery;
mod target_selection;
mod version_op;
mod version_scope;
mod worktree_path;
mod worktree_target;

pub use branch_conjunction::BranchConjunction;
pub use branch_glob::BranchGlob;
pub use branch_name::BranchName;
pub use branch_query::BranchQuery;
pub use branch_rule::BranchRule;
pub use exact_branch::ExactBranch;
pub use minecraft_version::MinecraftVersion;
pub use target_discovery::discover_worktree_targets;
pub use target_selection::select_required_worktree_targets;
pub use target_selection::select_single_worktree_target;
pub use target_selection::select_worktree_targets;
pub use version_op::VersionOp;
pub use version_scope::VersionScope;
pub use worktree_path::WorktreePath;
pub use worktree_target::WorktreeTarget;

#[cfg(test)]
mod tests;
