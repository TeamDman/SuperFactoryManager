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
                super::home_path_command::invoke();
                Ok(())
            }
            HomeCommand::Open => super::home_open_command::invoke(),
        }
    }
}
