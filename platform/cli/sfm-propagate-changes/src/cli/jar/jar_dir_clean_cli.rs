use crate::paths::AppHome;
use facet::Facet;

/// Arguments for cleaning the configured jar directory.
#[derive(Facet, Debug)]
pub struct JarDirCleanArgs;

impl JarDirCleanArgs {
    /// # Errors
    ///
    /// Returns an error if the configured jar directory cannot be cleaned.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        super::jar_dir_cli::clean_jars(app_home)
    }
}
