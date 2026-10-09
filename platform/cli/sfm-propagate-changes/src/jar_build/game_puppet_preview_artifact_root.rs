use std::path::Path;
use std::path::PathBuf;

pub(crate) fn projection_preview_artifact_root(project_root: &Path) -> PathBuf {
    project_root.join("build/sfm-toolchain/artifacts/game-test-preview")
}

/// Return the persistent game-puppet preview artifact root for a worktree.
#[must_use]
pub(crate) fn game_puppet_preview_artifact_root(worktree_path: &Path) -> PathBuf {
    worktree_path
        .join("platform")
        .join("minecraft")
        .join("build")
        .join("sfm-toolchain")
        .join("artifacts")
        .join("game-test-preview")
}
