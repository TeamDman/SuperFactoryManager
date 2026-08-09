use holda::Holda;
use std::path::PathBuf;

#[derive(Holda)]
#[holda(NoDisplay)]
pub struct RepoPath {
    inner: PathBuf,
}
impl std::fmt::Display for RepoPath {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.inner.display())
    }
}
