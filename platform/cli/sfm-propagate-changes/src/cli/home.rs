use crate::paths::APP_HOME;
use facet::Facet;

/// Home directory commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum HomeCommand {
    /// Show the home directory path
    Path,
    /// Open the home directory in the file explorer
    Open,
}

impl HomeCommand {
    /// # Errors
    ///
    /// This function will return an error if the operation fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            HomeCommand::Path => {
                println!("{}", APP_HOME.0.display());
                Ok(())
            }
            HomeCommand::Open => {
                let path = &APP_HOME.0;
                if !path.exists() {
                    std::fs::create_dir_all(path)?;
                }
                open::that(path)?;
                Ok(())
            }
        }
    }
}
