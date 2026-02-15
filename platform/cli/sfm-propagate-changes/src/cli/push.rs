use crate::worktree::get_sorted_worktrees;
use eyre::Context;
use eyre::bail;
use facet::Facet;
use std::process::Command;
use tracing::info;
use tracing::warn;

/// Push command - runs `git push` in each worktree
#[derive(Facet, Debug, Default)]
pub struct PushCommand;

impl PushCommand {
    /// # Errors
    ///
    /// Returns an error if any push fails.
    pub fn invoke(self) -> eyre::Result<()> {
        let worktrees = get_sorted_worktrees()?;

        if worktrees.is_empty() {
            println!("No worktrees found.");
            return Ok(());
        }

        let mut failures: Vec<String> = Vec::new();

        for wt in worktrees {
            info!("Pushing {} (in {})", wt.branch, wt.path.display());

            let output = Command::new("git")
                .args(["push"])
                .current_dir(&wt.path)
                .output()
                .wrap_err_with(|| format!("Failed to run git push in {}", wt.path.display()))?;

            if output.status.success() {
                info!("Push successful for {}", wt.branch);
            } else {
                let stdout = String::from_utf8_lossy(&output.stdout);
                let stderr = String::from_utf8_lossy(&output.stderr);
                warn!(
                    "Push failed for {}: exit: {:?}, stdout: {}, stderr: {}",
                    wt.branch, output.status, stdout, stderr
                );
                failures.push(format!("{}: exit {:?}", wt.branch, output.status));
            }
        }

        if failures.is_empty() {
            println!("All branches pushed successfully.");
            Ok(())
        } else {
            let mut msg = String::new();
            msg.push_str("Push failed for the following branches:\n");
            for f in failures {
                msg.push_str("  - ");
                msg.push_str(&f);
                msg.push('\n');
            }
            bail!(msg)
        }
    }
}
