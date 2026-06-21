use crate::worktree::get_sorted_worktrees;
use eyre::Context;
use eyre::bail;
use facet::Facet;
use figue as args;
use std::process::Command;
use tracing::info;
use tracing::warn;

/// Stage pathspecs in each worktree.
#[derive(Facet, Debug, Default)]
pub struct GitAddArgs {
    /// Git pathspecs to stage in each worktree.
    #[facet(args::positional)]
    pub paths: Vec<String>,
}

impl GitAddArgs {
    /// # Errors
    ///
    /// Returns an error if no pathspecs are provided or if `git add` fails in any worktree.
    pub fn invoke(self) -> eyre::Result<()> {
        if self.paths.is_empty() {
            bail!("At least one pathspec is required");
        }

        let worktrees = get_sorted_worktrees()?;

        if worktrees.is_empty() {
            info!("No worktrees found.");
            return Ok(());
        }

        let mut failures = Vec::new();

        for worktree in worktrees {
            info!(
                "Staging {} pathspec(s) for {} (in {})",
                self.paths.len(),
                worktree.branch,
                worktree.path.display()
            );

            let output = Command::new("git")
                .arg("add")
                .arg("--")
                .args(&self.paths)
                .current_dir(&worktree.path)
                .output()
                .wrap_err_with(|| {
                    format!("Failed to run git add in {}", worktree.path.display())
                })?;

            if output.status.success() {
                info!("Staged pathspec(s) for {}", worktree.branch);
            } else {
                let stdout = String::from_utf8_lossy(&output.stdout);
                let stderr = String::from_utf8_lossy(&output.stderr);
                warn!(
                    "git add failed for {}: exit: {:?}, stdout: {}, stderr: {}",
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
            info!("Staged pathspec(s) in all worktrees.");
            Ok(())
        } else {
            let mut message = String::from("git add failed for the following branches:\n");
            for failure in failures {
                message.push_str("  - ");
                message.push_str(&failure);
                message.push('\n');
            }
            bail!(message)
        }
    }
}
