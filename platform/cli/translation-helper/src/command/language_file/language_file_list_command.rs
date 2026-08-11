use crate::command::StandaloneCommand;
use crate::config::get_root_dir;
use crate::domain_object::game_version::GameVersion;
use crate::domain_object::repo_path::RepoPath;
use crate::domain_object::repository::discover_repositories;
use crate::domain_object::source_set::SourceSet;
use crate::domain_object::source_set_path::SourceSetPath;
use serde::Serialize;
use tracing::debug;

#[derive(Debug, Clone, Serialize)]
pub struct LanguageFileEntry {
    pub repo_path: RepoPath,
    pub game_version: GameVersion,
    pub source_set: SourceSetPath,
    pub language_file_path: String,
}

pub struct LanguageFileListCommand;

impl StandaloneCommand for LanguageFileListCommand {
    type Output = Vec<LanguageFileEntry>;

    fn execute(self) -> impl std::future::Future<Output = eyre::Result<Self::Output>> + Send {
        async move {
            let mut rtn = Vec::new();
            let root_dir = get_root_dir().await?;
            for (repo, game_version) in discover_repositories(&root_dir)? {
                debug!("Repo: {}, Game Version: {}", repo, game_version);
                let source_sets = SourceSet::list_in_repo(repo.clone())?;
                for source_set in source_sets {
                    debug!("Source Set: {}", source_set);
                    for language_file_path in source_set.list_language_files()? {
                        debug!("Language File: {}", language_file_path);
                        rtn.push(LanguageFileEntry {
                            repo_path: repo.clone(),
                            game_version: game_version.clone(),
                            source_set: source_set.clone(),
                            language_file_path: language_file_path.display().to_string(),
                        });
                    }
                }
            }
            Ok(rtn)
        }
    }
}
