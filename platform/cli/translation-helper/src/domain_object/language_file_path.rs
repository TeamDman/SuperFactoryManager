use crate::domain_object::game_version::GameVersion;
use crate::domain_object::repository::discover_repositories;
use crate::domain_object::source_set::SourceSet;
use crate::domain_object::source_set_path::SourceSetPath;
use holda::Holda;
use std::path::{Path, PathBuf};
use tracing::debug;

#[derive(Holda)]
#[holda(NoDisplay)]
pub struct LanguageFilePath {
    inner: PathBuf,
}
impl std::fmt::Display for LanguageFilePath {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.inner.display())
    }
}

impl LanguageFilePath {
    pub fn discover_in_source_set(
        source_set_path: &SourceSetPath,
    ) -> eyre::Result<Vec<LanguageFilePath>> {
        let mut paths = Vec::new();
        let lang_dir = source_set_path.join(r"resources\assets\sfm\lang\");
        let entries = match std::fs::read_dir(&lang_dir) {
            Ok(entries) => entries,
            Err(e) => {
                debug!(
                    "Failed to read language directory, probably doesn't exist for this source set\n{:?}\n{}",
                    lang_dir.display(),
                    e
                );
                return Ok(paths);
            }
        };
        for entry in entries {
            let entry = entry?;
            if entry.file_type()?.is_file() {
                let path = entry.path();
                if path.extension().map(|e| e == "json").unwrap_or(false) {
                    paths.push(LanguageFilePath { inner: path });
                }
            }
        }
        Ok(paths)
    }

    pub fn discover_in_root_dir(
        root_dir: &Path,
    ) -> eyre::Result<Vec<(LanguageFilePath, GameVersion)>> {
        let mut language_files: Vec<(LanguageFilePath, GameVersion)> = Vec::new();
        for (repo, game_version) in discover_repositories(root_dir)? {
            let source_sets = SourceSet::list_in_repo(repo.clone())?;
            for source_set in source_sets {
                for language_file in source_set.list_language_files()? {
                    language_files.push((language_file, game_version.clone()));
                }
            }
        }
        Ok(language_files)
    }
}
