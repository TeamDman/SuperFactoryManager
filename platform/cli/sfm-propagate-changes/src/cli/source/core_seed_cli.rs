//! Explicit one-time authoring import, separate from production generation.

use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::core_build_seed::seed_core_build;
use crate::source_projection::core_seed::DEFAULT_SEED_LEDGER;
use crate::source_projection::core_seed::seed_auxiliary_core;
use crate::source_projection::core_seed::seed_shared_core;
use crate::source_projection::core_version_seed::DEFAULT_VERSION_SEED_LEDGER;
use crate::source_projection::core_version_seed::seed_version_core;
use eyre::Result;
use facet::Facet;
use figue::{self as args};
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct CoreSeedArgs {
    /// Worktree containing the fixed reviewed shared-source migration ledger.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Create absent core files after full witness/path preflight; default is preview.
    #[facet(default = false, args::named)]
    pub apply: bool,
}

#[derive(Debug, Facet)]
pub struct CoreVersionSeedArgs {
    /// Worktree containing the fixed reviewed version-only migration ledger.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Create absent templates/assets after reconstruction; default is preview.
    #[facet(default = false, args::named)]
    pub apply: bool,
}

#[derive(Debug, Facet)]
pub struct CoreBuildSeedArgs {
    /// Worktree containing the fixed reviewed standalone-build migration ledger.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Create absent core build inputs; never replace sources, metadata or locks.
    #[facet(default = false, args::named)]
    pub apply: bool,
}

#[derive(Debug, Facet)]
pub struct CoreAuxiliarySeedArgs {
    /// Worktree containing the fixed reviewed shared resources/test-source ledger.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Create absent feature-independent core inputs; default is read-only preview.
    #[facet(default = false, args::named)]
    pub apply: bool,
}

impl CoreAuxiliarySeedArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let root = if self.repo_root.is_absolute() {
            self.repo_root
        } else {
            invocation_dir.join(self.repo_root)
        };
        let root = checked_directory(&root)?;
        Ok(CliOutput::facet(seed_auxiliary_core(&root, self.apply)?))
    }
}

impl CoreBuildSeedArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let root = if self.repo_root.is_absolute() {
            self.repo_root
        } else {
            invocation_dir.join(self.repo_root)
        };
        let root = checked_directory(&root)?;
        Ok(CliOutput::facet(seed_core_build(&root, self.apply)?))
    }
}

impl CoreVersionSeedArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let root = if self.repo_root.is_absolute() {
            self.repo_root
        } else {
            invocation_dir.join(self.repo_root)
        };
        let root = checked_directory(&root)?;
        Ok(CliOutput::facet(seed_version_core(
            &root,
            DEFAULT_VERSION_SEED_LEDGER,
            self.apply,
        )?))
    }
}

impl CoreSeedArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let root = if self.repo_root.is_absolute() {
            self.repo_root
        } else {
            invocation_dir.join(self.repo_root)
        };
        let root = checked_directory(&root)?;
        Ok(CliOutput::facet(seed_shared_core(
            &root,
            DEFAULT_SEED_LEDGER,
            self.apply,
        )?))
    }
}
