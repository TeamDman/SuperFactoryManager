use crate::paths::APP_HOME;
use crate::terminal_output::stdout_line;
use facet::Facet;

/// Arguments for showing the home directory path.
#[derive(Facet, Debug)]
pub struct HomePathArgs;

impl HomePathArgs {
    /// # Errors
    ///
    /// Returns an error if stdout cannot be written.
    pub fn invoke(self) -> eyre::Result<()> {
        stdout_line(APP_HOME.0.display())
    }
}
