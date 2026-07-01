use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct SourceJarPath(PathBuf);

impl SourceJarPath {
    #[must_use]
    pub(super) fn new(path: PathBuf) -> Self {
        Self(path)
    }

    #[must_use]
    pub(super) fn as_path(&self) -> &Path {
        &self.0
    }

    #[must_use]
    pub(super) fn into_path_buf(self) -> PathBuf {
        self.0
    }
}
