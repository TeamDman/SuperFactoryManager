use crate::paths::AppHome;
use crate::terminal_output::stdout_line;
use facet::Facet;

/// Arguments for showing the home directory path.
#[derive(Facet, Debug)]
pub struct HomePathArgs;

impl HomePathArgs {
    /// # Errors
    ///
    /// Returns an error if stdout cannot be written.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        stdout_line(app_home.0.display())
    }
}
