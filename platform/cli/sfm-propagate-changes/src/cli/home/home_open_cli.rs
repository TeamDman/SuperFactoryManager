use crate::paths::AppHome;
use facet::Facet;
use std::fs::create_dir_all;

/// Arguments for opening the home directory.
#[derive(Facet, Debug)]
pub struct HomeOpenArgs;

impl HomeOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the home directory cannot be created or opened.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        let path = &app_home.0;
        if !path.exists() {
            create_dir_all(path)?;
        }
        open::that(path)?;
        Ok(())
    }
}
