use super::RepoRootOpenArgs;
use super::RepoRootSetArgs;
use super::RepoRootShowArgs;
use super::RepoRootUnsetArgs;
use crate::paths::AppHome;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::fs::read_to_string;
use std::path::PathBuf;

pub(super) const REPO_ROOT_FILE: &str = "repo_root.txt";

/// Arguments for repo root commands.
#[derive(Facet, Debug)]
pub struct RepoRootArgs {
    /// Repo root subcommand.
    #[facet(args::subcommand)]
    pub command: RepoRootCommand,
}

impl RepoRootArgs {
    /// # Errors
    ///
    /// Returns an error if the selected repo root command fails.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        self.command.invoke(app_home)
    }
}

/// Repo root related commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum RepoRootCommand {
    /// Set the repo root path
    Set(RepoRootSetArgs),
    /// Unset the repo root path
    Unset(RepoRootUnsetArgs),
    /// Show the current repo root path
    Show(RepoRootShowArgs),
    /// Open the repo root in the file explorer
    Open(RepoRootOpenArgs),
}

impl RepoRootCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        match self {
            RepoRootCommand::Set(args) => args.invoke(app_home),
            RepoRootCommand::Unset(args) => args.invoke(app_home),
            RepoRootCommand::Show(args) => args.invoke(app_home),
            RepoRootCommand::Open(args) => args.invoke(app_home),
        }
    }
}

/// Get the configured repo root path
///
/// # Errors
///
/// This function will return an error if the repo root has not been set or if the file cannot be read.
pub fn get_repo_root(app_home: &AppHome) -> eyre::Result<PathBuf> {
    require_existing_repo_root(read_repo_root(app_home)?)
}

/// Read an absolute legacy release root without ambient path resolution.
///
/// # Errors
/// Rejects relative stored settings before probing their target. The normal
/// settings commands remain available to replace the path with a canonical one.
pub(crate) fn get_legacy_release_repo_root(app_home: &AppHome) -> eyre::Result<PathBuf> {
    let root = read_repo_root(app_home)?;
    eyre::ensure!(
        root.is_absolute(),
        "legacy release commands require an absolute configured repository root; \
         reset it with `repo-root set <path>`. Catalog preparation uses \
         `source release plan --projection <key> --expected-mod-version <version>`"
    );
    require_existing_repo_root(root)
}

fn read_repo_root(app_home: &AppHome) -> eyre::Result<PathBuf> {
    let repo_root_file = app_home.file_path(REPO_ROOT_FILE);

    if !repo_root_file.exists() {
        eyre::bail!(
            "Repo root not set. Use `sfm-propagate-changes repo-root set <path>` to set it."
        );
    }

    let content = read_to_string(&repo_root_file).wrap_err("Failed to read repo root file")?;

    Ok(PathBuf::from(content.trim()))
}

fn require_existing_repo_root(path: PathBuf) -> eyre::Result<PathBuf> {
    if !path.exists() {
        eyre::bail!(
            "Configured repo root does not exist: {}. Use `sfm-propagate-changes repo-root set <path>` to update it.",
            path.display()
        );
    }

    Ok(path)
}
