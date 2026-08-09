use holda::Holda;
use std::path::PathBuf;

use crate::domain_object::language_file_path::LanguageFilePath;

#[derive(Holda)]
#[holda(NoDisplay)]
pub struct SourceSetPath {
    inner: PathBuf,
}
impl SourceSetPath {
    pub fn list_language_files(&self) -> eyre::Result<Vec<LanguageFilePath>> {
        LanguageFilePath::discover_in_source_set(self)
    }
}
impl std::fmt::Display for SourceSetPath {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.inner.display())
    }
}
