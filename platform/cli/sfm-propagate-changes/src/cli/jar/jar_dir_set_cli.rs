use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for setting the jar directory path.
#[derive(Facet, Debug)]
pub struct JarDirSetArgs {
    /// The path to the jar directory.
    #[facet(args::positional)]
    pub path: PathBuf,
}

impl JarDirSetArgs {
    /// # Errors
    ///
    /// Returns an error if the jar directory cannot be created or persisted.
    pub fn invoke(self) -> eyre::Result<()> {
        super::jar_dir_cli::set_jar_dir(&self.path)
    }
}
