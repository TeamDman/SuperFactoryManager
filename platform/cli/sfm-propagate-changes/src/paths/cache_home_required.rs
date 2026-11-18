use std::error::Error;
use std::fmt;

/// An operation reached a shared-cache input without a selected cache home.
///
/// CLI routing can attach the startup selection failure while core operations
/// continue to accept explicit cache values rather than a discovery service.
#[derive(Debug)]
pub struct CacheHomeRequired;

impl fmt::Display for CacheHomeRequired {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("This operation requires a selected application cache home")
    }
}

impl Error for CacheHomeRequired {}
