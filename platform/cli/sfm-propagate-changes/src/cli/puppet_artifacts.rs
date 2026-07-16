use crate::branch_targets::select_single_worktree_target;
use crate::cli::jar::BranchSelector;
use crate::jar_build::game_puppet_preview_artifact_root;
use crate::terminal_output::stdout_line;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

/// Locate or open preview artifacts produced by an existing game-puppet run.
#[derive(Facet, Debug)]
pub struct PuppetArtifactsArgs {
    /// Artifact subcommand.
    #[facet(args::subcommand)]
    pub command: PuppetArtifactsCommand,
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum PuppetArtifactsCommand {
    /// Print the existing preview artifact directory. Suitable for scripts and CI.
    Path(PuppetArtifactsPathArgs),
    /// Open the existing preview artifact directory in the system file explorer.
    Open(PuppetArtifactsOpenArgs),
}

#[derive(Facet, Debug)]
pub struct PuppetArtifactsPathArgs {
    /// Branch selector that must identify exactly one worktree with existing preview artifacts.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

#[derive(Facet, Debug)]
pub struct PuppetArtifactsOpenArgs {
    /// Branch selector that must identify exactly one worktree with existing preview artifacts.
    #[facet(args::named)]
    pub branch: BranchSelector,
}

impl PuppetArtifactsArgs {
    /// # Errors
    ///
    /// Returns an error if the branch does not select exactly one worktree, or
    /// if that worktree has no complete preview artifact root.
    pub fn invoke(self) -> eyre::Result<()> {
        match self.command {
            PuppetArtifactsCommand::Path(args) => {
                stdout_line(resolve_existing_preview_artifact_root(args.branch)?.display())
            }
            PuppetArtifactsCommand::Open(args) => {
                let artifact_root = resolve_existing_preview_artifact_root(args.branch)?;
                open::that(&artifact_root).wrap_err_with(|| {
                    format!(
                        "Failed to open game-puppet preview artifacts at {}",
                        artifact_root.display()
                    )
                })
            }
        }
    }
}

fn resolve_existing_preview_artifact_root(branch: BranchSelector) -> eyre::Result<PathBuf> {
    let selector = branch.to_string();
    let query = branch.into_query()?;
    let target = select_single_worktree_target(&query)?;
    let artifact_root = game_puppet_preview_artifact_root(target.worktree_path.as_path());
    require_complete_preview_artifact_root(&artifact_root, &selector)?;
    Ok(artifact_root)
}

fn require_complete_preview_artifact_root(
    artifact_root: &Path,
    selector: &str,
) -> eyre::Result<()> {
    if !artifact_root.is_dir() {
        eyre::bail!(
            "No game-puppet preview artifacts exist for --branch '{selector}' at {}. Run `puppet run <puppet> --branch {selector}` first.",
            artifact_root.display()
        );
    }

    let manifest = artifact_root.join("preview-manifest.json");
    if !manifest.is_file() {
        eyre::bail!(
            "Game-puppet preview artifacts for --branch '{selector}' are incomplete: {} is missing.",
            manifest.display()
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::require_complete_preview_artifact_root;
    use crate::jar_build::game_puppet_preview_artifact_root;
    use std::fs;
    use tempfile::tempdir;

    #[test]
    fn preview_artifact_root_uses_the_run_pipeline_location() {
        let worktree = tempdir().expect("temporary worktree");
        assert_eq!(
            game_puppet_preview_artifact_root(worktree.path()),
            worktree
                .path()
                .join("platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview")
        );
    }

    #[test]
    fn preview_artifact_root_requires_an_existing_manifest() {
        let temporary = tempdir().expect("temporary artifact root");
        let artifact_root = temporary.path().join("game-test-preview");
        let missing_root = require_complete_preview_artifact_root(&artifact_root, "1.19.2")
            .expect_err("missing artifact root should fail");
        assert!(
            missing_root
                .to_string()
                .contains("No game-puppet preview artifacts exist")
        );

        fs::create_dir_all(&artifact_root).expect("create artifact root");
        let missing_manifest = require_complete_preview_artifact_root(&artifact_root, "1.19.2")
            .expect_err("missing manifest should fail");
        assert!(missing_manifest.to_string().contains("are incomplete"));

        fs::write(artifact_root.join("preview-manifest.json"), "{}").expect("write manifest");
        require_complete_preview_artifact_root(&artifact_root, "1.19.2")
            .expect("complete artifact root should validate");
    }
}
