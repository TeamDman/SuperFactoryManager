use super::BranchQuery;
use super::MinecraftVersion;
use super::select_required_worktree_targets;
use std::collections::BTreeSet;

/// Resolve a branch query into the Minecraft versions represented by matching worktrees.
///
/// # Errors
///
/// Returns an error if the branch query matches no worktrees or none of the matching worktrees
/// have a readable `minecraft_version` value.
pub fn select_required_minecraft_versions(
    // todo(2026-06-16) should this be an instance method
    query: &BranchQuery,
) -> eyre::Result<BTreeSet<MinecraftVersion>> {
    let versions = select_required_worktree_targets(query)?
        .into_iter()
        .filter_map(|target| target.mc_version)
        .collect::<BTreeSet<_>>();

    if versions.is_empty() {
        eyre::bail!("No Minecraft versions are represented by --branch '{query}'.");
    }

    Ok(versions)
}
