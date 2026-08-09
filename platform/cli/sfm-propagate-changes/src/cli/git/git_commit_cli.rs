use crate::worktree::Worktree;
use crate::worktree::get_sorted_worktrees;
use eyre::Context;
use eyre::bail;
use facet::Facet;
use figue as args;
use std::process::Command;
use tracing::info;
use tracing::warn;

/// Commit staged changes in each worktree.
#[derive(Facet, Debug)]
pub struct GitCommitArgs {
    /// Commit message. `%BRANCH%` is replaced with the current worktree branch.
    #[facet(args::named, args::short = 'm')]
    pub message: String,
}

impl GitCommitArgs {
    /// # Errors
    ///
    /// Returns an error if the message is empty or if `git commit` fails in any worktree.
    pub fn invoke(self) -> eyre::Result<()> {
        if self.message.trim().is_empty() {
            bail!("Commit message cannot be empty");
        }

        let worktrees = get_sorted_worktrees()?;

        if worktrees.is_empty() {
            info!("No worktrees found.");
            return Ok(());
        }

        let mut committed = 0usize;
        let mut skipped = 0usize;
        let mut failures = Vec::new();

        for worktree in worktrees {
            if !has_staged_changes(&worktree)? {
                skipped += 1;
                info!("Skipping {}: no staged changes", worktree.branch);
                continue;
            }

            let message = self.message.replace("%BRANCH%", &worktree.branch);
            info!(
                "Committing staged changes for {} (in {})",
                worktree.branch,
                worktree.path.display()
            );

            let output = Command::new("git")
                .args(["commit", "-m", &message])
                .current_dir(&worktree.path)
                .output()
                .wrap_err_with(|| {
                    format!("Failed to run git commit in {}", worktree.path.display())
                })?;

            if output.status.success() {
                committed += 1;
                info!("Committed staged changes for {}", worktree.branch);
            } else {
                let stdout = String::from_utf8_lossy(&output.stdout);
                let stderr = String::from_utf8_lossy(&output.stderr);
                warn!(
                    "git commit failed for {}: exit: {:?}, stdout: {}, stderr: {}",
                    worktree.branch, output.status, stdout, stderr
                );
                failures.push(format!(
                    "{}: exit {:?}: {}",
                    worktree.branch,
                    output.status,
                    stderr.trim()
                ));
            }
        }

        if failures.is_empty() {
            info!("Committed {committed} worktree(s); skipped {skipped} with no staged changes.");
            Ok(())
        } else {
            let mut message = String::from("git commit failed for the following branches:\n");
            for failure in failures {
                message.push_str("  - ");
                message.push_str(&failure);
                message.push('\n');
            }
            bail!(message)
        }
    }
}

fn has_staged_changes(worktree: &Worktree) -> eyre::Result<bool> {
    let status = Command::new("git")
        .args(["diff", "--cached", "--quiet"])
        .current_dir(&worktree.path)
        .status()
        .wrap_err_with(|| {
            format!(
                "Failed to check staged changes in {}",
                worktree.path.display()
            )
        })?;

    match status.code() {
        Some(0) => Ok(false),
        Some(1) => Ok(true),
        _ => bail!(
            "git diff --cached --quiet failed in {} with exit {:?}",
            worktree.path.display(),
            status
        ),
    }
}
