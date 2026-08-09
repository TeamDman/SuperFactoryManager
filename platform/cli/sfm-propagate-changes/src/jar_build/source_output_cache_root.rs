use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct SourceOutputCacheRoot(PathBuf);

impl SourceOutputCacheRoot {
    #[must_use]
    pub(super) fn new(path: PathBuf) -> Self {
        Self(path)
    }

    #[must_use]
    pub(super) fn as_path(&self) -> &Path {
        &self.0
    }
}
