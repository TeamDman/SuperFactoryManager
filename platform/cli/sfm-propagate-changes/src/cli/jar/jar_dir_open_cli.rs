use facet::Facet;

/// Arguments for opening the configured jar directory.
#[derive(Facet, Debug)]
pub struct JarDirOpenArgs;

impl JarDirOpenArgs {
    /// # Errors
    ///
    /// Returns an error if the configured jar directory cannot be read, created, or opened.
    pub fn invoke(self) -> eyre::Result<()> {
        let path = super::jar_dir_cli::get_jar_dir()?;
        if !path.exists() {
            std::fs::create_dir_all(&path)?;
        }
        open::that(&path)?;
        Ok(())
    }
}
