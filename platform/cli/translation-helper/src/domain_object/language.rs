use crate::domain_object::language_file::LanguageFile;
use crate::domain_object::language_file_path::LanguageFilePath;
use eyre::OptionExt;
use eyre::bail;
use eyre::eyre;
use holda::StringHolda;

#[derive(StringHolda)]
pub struct Language {
    inner: String,
}

impl TryFrom<&LanguageFilePath> for Language {
    type Error = eyre::Error;

    fn try_from(value: &LanguageFilePath) -> Result<Self, Self::Error> {
        let Some(name) = value.file_name() else {
            bail!(
                "Failed to extract language name from path: {}",
                value.display()
            );
        };
        let name = name.to_string_lossy();
        let without_extension = name.strip_suffix(".json").ok_or_eyre(eyre!(
            "Language file name does not have a valid extension, expected '.json': {}",
            name
        ))?;
        Ok(Language::new(without_extension))
    }
}

impl TryFrom<&LanguageFile> for Language {
    type Error = eyre::Error;

    fn try_from(value: &LanguageFile) -> Result<Self, Self::Error> {
        (&value.path).try_into()
    }
}
