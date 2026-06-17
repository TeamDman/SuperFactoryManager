use crate::terminal_output::stdout_line;
use facet::Facet;

/// Arguments for showing the configured jar directory path.
#[derive(Facet, Debug)]
pub struct JarDirShowArgs;

impl JarDirShowArgs {
    /// # Errors
    ///
    /// Returns an error if the configured jar directory cannot be read or stdout cannot be written.
    pub fn invoke(self) -> eyre::Result<()> {
        let path = super::jar_dir_cli::get_jar_dir()?;
        stdout_line(path.display())
    }
}
