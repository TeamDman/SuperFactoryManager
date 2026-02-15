use crate::worktree::Worktree;
use crate::worktree::get_sorted_worktrees;
use eyre::bail;
use facet::Facet;
use figue::{self as args};
use std::fs;
use tracing::info;
use tracing::warn;

/// Check command - verifies platform/minecraft/.idea/.name matches sfm-<mcversion>
#[derive(Facet, Debug, Default)]
pub struct CheckCommand {
    /// If set, update or create the `.idea/.name` files to the expected value
    #[facet(args::named)]
    pub fix: bool,
}

impl CheckCommand {
    /// # Errors
    ///
    /// Returns an error if any worktree fails the check.
    pub fn invoke(self) -> eyre::Result<()> {
        let worktrees = get_sorted_worktrees()?;

        if worktrees.is_empty() {
            println!("No worktrees found.");
            return Ok(());
        }

        let mut failures: Vec<String> = Vec::new();

        for wt in worktrees {
            if let Some(err) = self.check_worktree(&wt) {
                warn!("{}", err);
                failures.push(err);
            }
        }

        if failures.is_empty() {
            println!("All worktrees have correct .idea/.name files.");
            Ok(())
        } else {
            let mut msg = String::new();
            msg.push_str("Check failed for the following worktrees:\n");
            for f in failures {
                msg.push_str("  - ");
                msg.push_str(&f);
                msg.push('\n');
            }
            bail!(msg)
        }
    }

    fn check_worktree(&self, wt: &Worktree) -> Option<String> {
        let path = wt
            .path
            .join("platform")
            .join("minecraft")
            .join(".idea")
            .join(".name");
        let expected = format!("sfm-{}", wt.branch);

        match fs::read_to_string(&path) {
            Ok(content) => {
                let found = content.trim();
                if found == expected {
                    info!("{}: .idea/.name OK", wt.branch);
                    None
                } else if self.fix {
                    if let Some(parent) = path.parent()
                        && let Err(e) = fs::create_dir_all(parent)
                    {
                        return Some(format!(
                            "{}: could not create parent dir {} ({})",
                            wt.branch,
                            parent.display(),
                            e
                        ));
                    }

                    match fs::write(&path, expected.as_bytes()) {
                        Ok(()) => {
                            info!("{}: fixed .idea/.name (wrote '{}')", wt.branch, expected);
                            None
                        }
                        Err(e) => Some(format!(
                            "{}: could not write {} ({})",
                            wt.branch,
                            path.display(),
                            e
                        )),
                    }
                } else {
                    Some(format!(
                        "{}: expected '{}', found '{}'",
                        wt.branch, expected, found
                    ))
                }
            }
            Err(e) => {
                if self.fix {
                    if let Some(parent) = path.parent()
                        && let Err(e2) = fs::create_dir_all(parent)
                    {
                        return Some(format!(
                            "{}: could not create parent dir {} ({})",
                            wt.branch,
                            parent.display(),
                            e2
                        ));
                    }

                    match fs::write(&path, expected.as_bytes()) {
                        Ok(()) => {
                            info!("{}: created .idea/.name with '{}'", wt.branch, expected);
                            None
                        }
                        Err(e2) => Some(format!(
                            "{}: could not write {} ({})",
                            wt.branch,
                            path.display(),
                            e2
                        )),
                    }
                } else {
                    Some(format!(
                        "{}: could not read {} ({})",
                        wt.branch,
                        path.display(),
                        e
                    ))
                }
            }
        }
    }
}
