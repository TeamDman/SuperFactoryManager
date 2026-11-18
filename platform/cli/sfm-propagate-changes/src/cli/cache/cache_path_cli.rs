use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use facet::Facet;

/// Arguments for showing the cache directory path.
#[derive(Facet, Debug)]
pub struct CachePathArgs;

impl CachePathArgs {
    /// # Errors
    ///
    /// Returns an error if cache selection or writing stdout fails.
    pub fn invoke(self) -> eyre::Result<()> {
        stdout_line(CacheHome::current()?.0.display())
    }
}
