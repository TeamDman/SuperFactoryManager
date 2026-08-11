use crate::domain_object::game_version::GameVersion;
use crate::domain_object::language::Language;
use crate::domain_object::language_file_path::LanguageFilePath;
use crate::domain_object::translation_key::TranslationKey;
use crate::domain_object::translation_value::TranslationValue;
use eyre::Context;
use std::collections::HashMap;
use std::fs;
use std::path::Path;

pub struct LanguageFile {
    pub path: LanguageFilePath,
    pub contents: HashMap<TranslationKey, TranslationValue>,
}
impl LanguageFile {
    pub fn load_from_path(path: LanguageFilePath) -> eyre::Result<Self> {
        let contents = fs::read_to_string(path.as_path())
            .wrap_err_with(|| format!("Failed to read language file: {}", path.display()))?;
        let contents = serde_json::from_str(&contents)
            .wrap_err_with(|| format!("Failed to parse language file: {}", path.display()))?;
        Ok(Self { path, contents })
    }

    pub fn discover_in_root_dir(
        root_dir: &Path,
    ) -> eyre::Result<HashMap<GameVersion, HashMap<Language, LanguageFile>>> {
        // get paths
        let language_file_paths = LanguageFilePath::discover_in_root_dir(root_dir)?;
        let mut files: HashMap<GameVersion, HashMap<Language, LanguageFile>> = HashMap::new();
        for (path, game_version) in language_file_paths {
            let file = LanguageFile::load_from_path(path)?;
            let language = (&file).try_into()?;
            files
                .entry(game_version)
                .or_default()
                .insert(language, file);
        }
        Ok(files)
    }
}
