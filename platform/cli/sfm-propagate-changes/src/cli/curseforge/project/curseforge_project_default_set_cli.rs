#![allow(clippy::doc_markdown)]

use super::super::curseforge_cli::CURSEFORGE_DEFAULT_PROJECT_FILE;
use crate::paths::AppHome;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::fs::write;
use tracing::info;

/// Arguments for setting the default CurseForge project ID.
#[derive(Facet, Debug)]
pub struct CurseforgeProjectDefaultSetArgs {
    /// Project ID to persist as default.
    #[facet(args::positional)]
    pub project: u64,
}

impl CurseforgeProjectDefaultSetArgs {
    /// # Errors
    ///
    /// Returns an error if the default project ID cannot be persisted.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        set_default_project_id(self.project, app_home)
    }
}

fn set_default_project_id(project: u64, app_home: &AppHome) -> eyre::Result<()> {
    app_home.ensure_dir()?;
    let path = app_home.file_path(CURSEFORGE_DEFAULT_PROJECT_FILE);
    write(&path, project.to_string())
        .wrap_err_with(|| format!("Failed to write default project file: {}", path.display()))?;

    info!("Default CurseForge project set to {project}.");
    Ok(())
}
