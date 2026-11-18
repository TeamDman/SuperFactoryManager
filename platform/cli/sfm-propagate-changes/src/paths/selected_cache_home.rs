use super::CacheHome;
use super::CacheHomeSource;

/// A cache path and its origin from the same selection decision.
///
/// Keep these together: re-reading mutable settings after selecting a path must
/// never grant permission to delete a previously selected custom cache.
#[derive(Clone, Debug)]
pub struct SelectedCacheHome {
    pub home: CacheHome,
    pub source: CacheHomeSource,
}
