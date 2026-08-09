use crate::domain_object::checked_out_branch::CheckedOutBranch;
use crate::domain_object::game_version::GameVersion;
use crate::domain_object::repo_path::RepoPath;
use eyre::Context;
use eyre::bail;
use git2::Repository;
use std::fs;
use std::path::Path;
use std::str::FromStr;

pub fn discover_repositories(root_dir: &Path) -> eyre::Result<Vec<(RepoPath, GameVersion)>> {
    let mut repos: Vec<(RepoPath, CheckedOutBranch)> = Vec::new();

    if !root_dir.exists() {
        bail!("Root directory does not exist: {}", root_dir.display());
    }

    let entries = fs::read_dir(&root_dir)?;

    for entry in entries {
        let entry = entry?;
        let path = entry.path();

        if path.is_dir()
            && let Some(dir_name) = path.file_name()
        {
            let dir_name = dir_name.to_string_lossy().to_string();

            // Try to open as a git repository
            match Repository::open(&path) {
                Ok(repo) => {
                    let branch_name =
                        get_current_branch(&repo).unwrap_or_else(|_| "unknown".to_string());
                    let repo_path =
                        RepoPath::new(root_dir.join(&dir_name).canonicalize().wrap_err_with(
                            || format!("Failed to canonicalize path: {dir_name}"),
                        )?);
                    let branch = CheckedOutBranch::new(branch_name);
                    repos.push((repo_path, branch));
                }
                Err(_) => {
                    // Not a git repository, skip silently
                }
            }
        }
    }

    let mut rtn = Vec::new();
    for (repo_path, branch) in repos {
        rtn.push((
            repo_path,
            GameVersion::from_str(branch.as_str())
                .wrap_err_with(|| format!("Failed to parse branch name '{branch}' as version"))?,
        )); // Convert branch name to GameVersion
    }

    Ok(rtn)
}

pub fn get_current_branch(repo: &Repository) -> Result<String, git2::Error> {
    let head = repo.head()?;

    if let Some(name) = head.shorthand() {
        Ok(name.to_string())
    } else {
        // HEAD is detached, try to get the commit SHA
        if let Some(oid) = head.target() {
            Ok(format!("detached@{oid:.7}"))
        } else {
            Ok("unknown".to_string())
        }
    }
}
