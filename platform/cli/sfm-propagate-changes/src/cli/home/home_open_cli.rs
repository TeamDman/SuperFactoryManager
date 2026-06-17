use crate::paths::APP_HOME;
use facet::Facet;

/// Arguments for opening the home directory.
#[derive(Facet, Debug)]
pub struct HomeOpenArgs;

impl HomeOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the home directory cannot be created or opened.
    pub fn invoke(self) -> eyre::Result<()> {
        let path = &APP_HOME.0;
        if !path.exists() {
            std::fs::create_dir_all(path)?;
        }
        open::that(path)?;
        Ok(())
    }
}
