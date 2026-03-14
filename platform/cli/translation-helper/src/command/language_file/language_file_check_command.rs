use crate::command::StandaloneCommand;
use crate::config::get_root_dir;
use crate::domain_object::language::Language;
use crate::domain_object::language_file::LanguageFile;
use crate::domain_object::language_file_path::LanguageFilePath;
use crate::domain_object::translation_key::TranslationKey;
use eyre::bail;
use serde::Serialize;
use std::collections::HashSet;

pub struct LanguageFileCheckCommand;

#[derive(Debug, Serialize)]
pub enum Problem {
    KeyPresentInEnglishButNotOtherLanguage {
        language_file_path: LanguageFilePath,
        missing_key: TranslationKey,
    },
    KeyPresentInOtherLanguageButNotEnglish {
        language_file_path: LanguageFilePath,
        missing_key: TranslationKey,
    },
}

#[derive(Debug, Serialize)]
pub struct LanguageFileCheckCommandResponse {
    pub problems: Vec<Problem>,
}

impl StandaloneCommand for LanguageFileCheckCommand {
    type Output = LanguageFileCheckCommandResponse;

    fn execute(self) -> impl std::future::Future<Output = eyre::Result<Self::Output>> + Send {
        async move {
            // get root dir
            let root_dir = get_root_dir().await?;

            // discover language files
            let language_files = LanguageFile::discover_in_root_dir(&root_dir)?;

            let mut rtn = LanguageFileCheckCommandResponse {
                problems: Vec::new(),
            };

            for (game_version, languages) in language_files {
                let Some(english_translation_file) = languages.get(&Language::new("en_us")) else {
                    bail!(
                        "No English translation file found for game version: {}",
                        game_version
                    );
                };
                let english_keys = english_translation_file
                    .contents
                    .keys()
                    .cloned()
                    .collect::<HashSet<_>>();
                for (lang, lang_file) in languages {
                    if lang == Language::new("en_us") {
                        continue;
                    }
                    let other_keys = lang_file.contents.keys().cloned().collect::<HashSet<_>>();

                    let keys_in_english_but_not_other = english_keys
                        .difference(&other_keys)
                        .cloned()
                        .collect::<Vec<_>>();
                    for missing_key in keys_in_english_but_not_other {
                        rtn.problems
                            .push(Problem::KeyPresentInEnglishButNotOtherLanguage {
                                language_file_path: lang_file.path.clone(),
                                missing_key,
                            });
                    }

                    let keys_in_other_but_not_english = other_keys
                        .difference(&english_keys)
                        .cloned()
                        .collect::<Vec<_>>();

                    for missing_key in keys_in_other_but_not_english {
                        rtn.problems
                            .push(Problem::KeyPresentInOtherLanguageButNotEnglish {
                                language_file_path: lang_file.path.clone(),
                                missing_key,
                            });
                    }
                }
            }
            rtn.problems.sort_by_key(|problem| match problem {
                Problem::KeyPresentInEnglishButNotOtherLanguage {
                    language_file_path,
                    missing_key,
                } => (language_file_path.to_string(), missing_key.to_string()),
                Problem::KeyPresentInOtherLanguageButNotEnglish {
                    language_file_path,
                    missing_key,
                } => (language_file_path.to_string(), missing_key.to_string()),
            });
            Ok(rtn)
        }
    }
}
