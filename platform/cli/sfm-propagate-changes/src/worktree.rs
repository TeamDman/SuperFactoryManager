//! Common worktree utilities shared across commands.

use crate::cli::repo_root::get_repo_root;
use std::path::PathBuf;

/// Represents a worktree with its path and branch name
#[derive(Debug, Clone)]
pub struct Worktree {
    pub path: PathBuf,
    pub branch: String,
}

/// Read the main and linked worktrees with attached local branches.
///
/// # Errors
///
/// Returns an error if repository or registered worktree metadata cannot be read.
pub fn get_worktrees(repo_root: &PathBuf) -> eyre::Result<Vec<Worktree>> {
    let repository = gix::discover(repo_root)?;
    let mut worktrees = Vec::new();
    append_attached_worktree(&repository.main_repo()?, &mut worktrees)?;
    let mut linked = Vec::new();
    for proxy in repository.worktrees()? {
        append_attached_worktree(
            &proxy.into_repo_with_possibly_inaccessible_worktree()?,
            &mut linked,
        )?;
    }
    linked.sort_by(|a, b| a.path.cmp(&b.path));
    worktrees.extend(linked);
    Ok(worktrees)
}

fn append_attached_worktree(
    repository: &gix::Repository,
    result: &mut Vec<Worktree>,
) -> eyre::Result<()> {
    if repository.is_bare() {
        return Ok(());
    }
    if let (Some(path), Some(name)) = (repository.workdir(), repository.head_name()?) {
        let name = std::str::from_utf8(name.as_bstr())?;
        if let Some(branch) = name.strip_prefix("refs/heads/") {
            result.push(Worktree {
                path: path.to_owned(),
                branch: branch.to_owned(),
            });
        }
    }
    Ok(())
}

/// Parse a Minecraft version string into comparable parts
/// Returns (major, minor, patch) as numbers for sorting
#[must_use]
pub fn parse_version(version: &str) -> Option<(u32, u32, u32)> {
    let parts: Vec<&str> = version.split('.').collect();
    match parts.len() {
        2 => {
            let major = parts[0].parse().ok()?;
            let minor = parts[1].parse().ok()?;
            Some((major, minor, 0))
        }
        3 => {
            let major = parts[0].parse().ok()?;
            let minor = parts[1].parse().ok()?;
            let patch = parts[2].parse().ok()?;
            Some((major, minor, patch))
        }
        _ => None,
    }
}

/// Sort worktrees by their version number (semver-like)
pub fn sort_worktrees_by_version(worktrees: &mut [Worktree]) {
    worktrees.sort_by(|a, b| {
        let a_version = parse_version(&a.branch);
        let b_version = parse_version(&b.branch);

        match (a_version, b_version) {
            (Some(a_v), Some(b_v)) => a_v.cmp(&b_v),
            (Some(_), None) => std::cmp::Ordering::Less,
            (None, Some(_)) => std::cmp::Ordering::Greater,
            (None, None) => a.branch.cmp(&b.branch),
        }
    });
}

/// Get all worktrees from the repo root, sorted by version
///
/// # Errors
///
/// Returns an error if getting the repo root fails or `git worktree list` fails.
pub fn get_sorted_worktrees() -> eyre::Result<Vec<Worktree>> {
    let repo_root = get_repo_root()?;
    let mut worktrees = get_worktrees(&repo_root)?;
    sort_worktrees_by_version(&mut worktrees);
    Ok(worktrees)
}
