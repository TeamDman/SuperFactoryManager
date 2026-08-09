use super::BranchName;
use super::MinecraftVersion;
use super::WorktreePath;
use crate::worktree::Worktree;
use eyre::Context;
use std::path::Path;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct WorktreeTarget {
    pub branch: BranchName,
    pub worktree_path: WorktreePath,
    pub core: bool,
    pub mc_version: Option<MinecraftVersion>,
}

impl WorktreeTarget {
    /// Convert a Git worktree record into a branch-query target.
    ///
    /// # Errors
    ///
    /// Returns an error if the worktree has an invalid `minecraft_version` property.
    pub fn from_worktree(worktree: Worktree) -> eyre::Result<Self> {
        Self::from_parts(
            BranchName::from(worktree.branch),
            WorktreePath::from(worktree.path),
        )
    }

    /// Build a branch-query target from a branch and worktree path.
    ///
    /// # Errors
    ///
    /// Returns an error if the worktree has an invalid `minecraft_version` property.
    pub fn from_parts(branch: BranchName, worktree_path: WorktreePath) -> eyre::Result<Self> {
        let mc_version = read_minecraft_version(worktree_path.as_path())?;
        let core = branch.is_core_branch();
        Ok(Self {
            branch,
            worktree_path,
            core,
            mc_version,
        })
    }
}

fn read_minecraft_version(worktree_path: &Path) -> eyre::Result<Option<MinecraftVersion>> {
    let properties_path = worktree_path
        .join("platform")
        .join("minecraft")
        .join("gradle.properties");
    if !properties_path.is_file() {
        return Ok(None);
    }

    let content = std::fs::read_to_string(&properties_path)
        .wrap_err_with(|| format!("Failed to read {}", properties_path.display()))?;
    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let Some((key, value)) = trimmed.split_once('=') else {
            continue;
        };
        if key.trim() == "minecraft_version" {
            return Ok(Some(MinecraftVersion::parse(value.trim())?));
        }
    }

    Ok(None)
}
