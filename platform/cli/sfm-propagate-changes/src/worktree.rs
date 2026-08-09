//! Common worktree utilities shared across commands.

use crate::cli::repo_root::get_repo_root;
use eyre::Context;
use eyre::bail;
use std::path::PathBuf;
use std::process::Command;

/// Represents a worktree with its path and branch name
#[derive(Debug, Clone)]
pub struct Worktree {
    pub path: PathBuf,
    pub branch: String,
}

/// Parse the output of `git worktree list` to get all worktrees
///
/// # Errors
///
/// Returns an error if `git worktree list --porcelain` fails or its output cannot be parsed.
pub fn get_worktrees(repo_root: &PathBuf) -> eyre::Result<Vec<Worktree>> {
    let output = Command::new("git")
        .args(["worktree", "list", "--porcelain"])
        .current_dir(repo_root)
        .output()
        .wrap_err("Failed to run git worktree list")?;

    if !output.status.success() {
        bail!(
            "git worktree list failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut worktrees = Vec::new();
    let mut current_path: Option<PathBuf> = None;
    let mut current_branch: Option<String> = None;

    for line in stdout.lines() {
        if let Some(path) = line.strip_prefix("worktree ") {
            // Save previous worktree if complete
            if let (Some(path), Some(branch)) = (current_path.take(), current_branch.take()) {
                worktrees.push(Worktree { path, branch });
            }
            current_path = Some(PathBuf::from(path));
        } else if let Some(branch_ref) = line.strip_prefix("branch refs/heads/") {
            current_branch = Some(branch_ref.to_string());
        }
    }

    // Don't forget the last one
    if let (Some(path), Some(branch)) = (current_path, current_branch) {
        worktrees.push(Worktree { path, branch });
    }

    Ok(worktrees)
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
