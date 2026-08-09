#![allow(clippy::doc_markdown)]

use super::super::curseforge_cli::get_default_project_id;
use facet::Facet;

/// Arguments for showing the default CurseForge project ID.
#[derive(Facet, Debug)]
pub struct CurseforgeProjectDefaultShowArgs;

impl CurseforgeProjectDefaultShowArgs {
    /// # Errors
    ///
    /// Returns an error if the default project ID cannot be read.
    pub fn invoke(self) -> eyre::Result<()> {
        let project = get_default_project_id()?;
        tracing::info!("{project}");
        Ok(())
    }
}
