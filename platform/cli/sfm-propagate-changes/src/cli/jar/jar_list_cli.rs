use crate::cli::jar::get_jar_dir;
use crate::terminal_output::stdout_line;
use facet::Facet;
use std::ffi::OsStr;

/// Arguments for listing jars in the configured jar directory.
#[derive(Facet, Debug)]
pub struct JarListArgs;

impl JarListArgs {
    /// # Errors
    ///
    /// Returns an error if the jar directory cannot be read or stdout cannot be written.
    pub fn invoke(self) -> eyre::Result<()> {
        let jar_dir = get_jar_dir()?;
        let jars = super::jar_dir_cli::list_jar_files_sorted(&jar_dir)?;

        if jars.is_empty() {
            return stdout_line(format!("No jars found in {}", jar_dir.display()));
        }

        for jar in jars {
            if let Some(name) = jar.file_name().and_then(OsStr::to_str) {
                stdout_line(name)?;
            } else {
                stdout_line(jar.display())?;
            }
        }

        Ok(())
    }
}
