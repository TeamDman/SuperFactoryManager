use crate::paths::CACHE_DIR;
use crate::terminal_output::stdout_line;
use facet::Facet;

/// Arguments for showing the cache directory path.
#[derive(Facet, Debug)]
pub struct CachePathArgs;

impl CachePathArgs {
    /// # Errors
    ///
    /// Returns an error if stdout cannot be written.
    pub fn invoke(self) -> eyre::Result<()> {
        stdout_line(CACHE_DIR.0.display())
    }
}
