use crate::worktree::Worktree;
use crate::worktree::get_sorted_worktrees;
use eyre::Context;
use eyre::bail;
use facet::Facet;
use std::path::Path;
use std::process::Command;
use tracing::info;

/// Tag command - tag each branch with `<mod_version>-<mc_version>`.
#[derive(Facet, Debug, Default)]
pub struct TagCommand;

impl TagCommand {
    /// # Errors
    ///
    /// Returns an error if any worktree is dirty or tagging fails.
    pub fn invoke() -> eyre::Result<()> {
        let worktrees = get_sorted_worktrees()?;

        if worktrees.is_empty() {
            println!("No worktrees found.");
            return Ok(());
        }

        for worktree in &worktrees {
            ensure_worktree_clean(worktree)?;
        }

        let mod_version = read_mod_version_for_first_worktree(&worktrees[0])?;

        for worktree in worktrees {
            let tag = format!("{mod_version}-{}", worktree.branch);
            info!("Tagging {} (in {})", tag, worktree.path.display());

            let tag_output = Command::new("git")
                .args(["tag", &tag])
                .current_dir(&worktree.path)
                .output()
                .wrap_err_with(|| {
                    format!("Failed to run git tag in {}", worktree.path.display())
                })?;

            if !tag_output.status.success() {
                bail!(
                    "Failed to create tag {} in {}: {}",
                    tag,
                    worktree.path.display(),
                    String::from_utf8_lossy(&tag_output.stderr)
                );
            }

            println!("Tagged {tag}");
        }

        Ok(())
    }
}

fn ensure_worktree_clean(worktree: &Worktree) -> eyre::Result<()> {
    let staged = Command::new("git")
        .args(["diff", "--cached", "--quiet"])
        .current_dir(&worktree.path)
        .status()
        .wrap_err_with(|| format!("Failed to check staged diff in {}", worktree.path.display()))?;

    if !staged.success() {
        bail!("Worktree {} has staged changes", worktree.branch);
    }

    let unstaged = Command::new("git")
        .args(["diff", "--quiet"])
        .current_dir(&worktree.path)
        .status()
        .wrap_err_with(|| {
            format!(
                "Failed to check unstaged diff in {}",
                worktree.path.display()
            )
        })?;

    if !unstaged.success() {
        bail!("Worktree {} has unstaged changes", worktree.branch);
    }

    Ok(())
}

fn read_mod_version_for_first_worktree(worktree: &Worktree) -> eyre::Result<String> {
    let gradle_properties = worktree
        .path
        .join("platform")
        .join("minecraft")
        .join("gradle.properties");

    read_mod_version(&gradle_properties).wrap_err_with(|| {
        format!(
            "Failed to read mod_version from {}",
            gradle_properties.display()
        )
    })
}

fn read_mod_version(gradle_properties: &Path) -> eyre::Result<String> {
    let content = std::fs::read_to_string(gradle_properties)
        .wrap_err("Failed to read gradle.properties for mod_version")?;

    let mod_version = content
        .lines()
        .map(str::trim)
        .find_map(|line| line.strip_prefix("mod_version=").map(str::trim))
        .ok_or_else(|| eyre::eyre!("mod_version not found"))?;

    if mod_version.is_empty() {
        eyre::bail!("mod_version was empty");
    }

    Ok(mod_version.to_string())
}
