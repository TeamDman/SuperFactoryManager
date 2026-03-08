use eyre::Context;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

pub(super) const REPO_ROOT_FILE: &str = "repo_root.txt";

/// Repo root related commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum RepoRootCommand {
    /// Set the repo root path
    Set {
        /// The path to the repo root
        #[facet(args::positional)]
        path: PathBuf,
    },
    /// Unset the repo root path
    Unset,
    /// Show the current repo root path
    Show,
    /// Open the repo root in the file explorer
    Open,
}

impl RepoRootCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            RepoRootCommand::Set { path } => super::repo_root_set_command::invoke(path),
            RepoRootCommand::Unset => super::repo_root_unset_command::invoke(),
            RepoRootCommand::Show => super::repo_root_show_command::invoke(),
            RepoRootCommand::Open => super::repo_root_open_command::invoke(),
        }
    }
}

/// Get the configured repo root path
///
/// # Errors
///
/// This function will return an error if the repo root has not been set or if the file cannot be read.
pub fn get_repo_root() -> eyre::Result<PathBuf> {
    let repo_root_file = crate::paths::APP_HOME.file_path(REPO_ROOT_FILE);

    if !repo_root_file.exists() {
        eyre::bail!(
            "Repo root not set. Use `sfm-propagate-changes repo-root set <path>` to set it."
        );
    }

    let content =
        std::fs::read_to_string(&repo_root_file).wrap_err("Failed to read repo root file")?;

    let path = PathBuf::from(content.trim());

    if !path.exists() {
        eyre::bail!(
            "Configured repo root does not exist: {}. Use `sfm-propagate-changes repo-root set <path>` to update it.",
            path.display()
        );
    }

    Ok(path)
}
