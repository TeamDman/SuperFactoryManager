use super::BuildMode;
use super::ErrorAction;
use super::Parallelism;
use crate::branch_targets::BranchQuery;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use std::path::PathBuf;

#[derive(Clone, Debug)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "This type carries normalized CLI flags into the build engine."
)]
pub struct BuildOptions {
    pub app_home: AppHome,
    /// Application cache fallback; selected native roots take precedence.
    pub cache_home: Option<CacheHome>,
    pub selection: BuildSelection,
    /// Explicit guarded native-root override; catalog operations select the checkout root by default.
    pub cache_root: Option<PathBuf>,
    pub refresh: bool,
    /// Skip initial comprehensive source checks for development projections.
    pub skip_source_checks: bool,
    pub explain_rebuild: bool,
    pub plan_json: Option<PathBuf>,
    /// Create-only filename in one projection's existing build/libs directory.
    pub output_name: Option<String>,
    pub java_home: Option<PathBuf>,
    pub dry_run: bool,
    pub allow_local_artifact_cache: bool,
    pub artifact_sources: Vec<PathBuf>,
    pub require_portable_artifacts: bool,
    pub error_action: ErrorAction,
    pub parallelism: Parallelism,
    pub wait_for_build_lock: bool,
    pub mode: BuildMode,
}

/// Build identity is separate from Git operations. Worktrees remain an internal
/// adapter for existing callers; the shared CLI accepts projections only.
#[derive(Clone, Debug)]
pub enum BuildSelection {
    Worktree(BranchQuery),
    Projection {
        selector: String,
        repo_root: Option<PathBuf>,
    },
}

impl BuildOptions {
    /// Retain the application cache selected for this invocation.
    #[must_use]
    pub fn with_cache_home(mut self, cache_home: Option<&CacheHome>) -> Self {
        self.cache_home = cache_home.cloned();
        self
    }

    pub(crate) fn worktree_query(&self) -> eyre::Result<&BranchQuery> {
        match &self.selection {
            BuildSelection::Worktree(query) => Ok(query),
            BuildSelection::Projection { .. } => {
                eyre::bail!("projection cannot be resolved as a Git branch")
            }
        }
    }
}
