use super::CatalogReleaseVersion;
use crate::sfm_gradle_dot_properties_file_content::SfmGradleDotPropertiesFileContent;
use eyre::Result;

/// SFM release metadata validated from one parsed Gradle properties file.
#[derive(Debug)]
pub(super) struct CatalogReleaseProperties {
    pub(super) mod_version: CatalogReleaseVersion,
    pub(super) minecraft_version: String,
}

impl CatalogReleaseProperties {
    pub(super) fn from_gradle_properties(
        properties: &SfmGradleDotPropertiesFileContent,
    ) -> Result<Self> {
        Ok(Self {
            mod_version: CatalogReleaseVersion::parse(&properties.mod_version().to_string())?,
            minecraft_version: properties.as_generic().minecraft_version.clone(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(bytes: &[u8]) -> Result<CatalogReleaseProperties> {
        CatalogReleaseProperties::from_gradle_properties(
            &SfmGradleDotPropertiesFileContent::from_bytes(bytes)?,
        )
    }

    #[test]
    fn ambiguous_properties_are_rejected_before_metadata_is_credited() {
        let valid = crate::gradle_dot_properties_file_content::test_content(
            "mod_id=sfm\nmod_version=4.35.0\nminecraft_version=1.21\n",
        );
        for extra in [
            "mod_version=4.36.0\n",
            "mod_version:4.35.0\n",
            "mod_version 4.35.0\n",
            "other=hidden\rmod_version=4.35.0\n",
            "mod\\_version=4.35.0\n",
            "mod_\\u0076ersion=4.35.0\n",
            "other=continued\\\nmod_version=4.35.0\n",
            "minecraft_version=${minecraft_version}\n",
        ] {
            assert!(
                parse(format!("{valid}{extra}").as_bytes()).is_err(),
                "{extra}"
            );
        }
        for (old, new) in [
            ("mod_id=sfm", "mod_id=other"),
            ("mod_version=4.35.0", "mod_version="),
            ("mod_version=4.35.0", " mod_version=4.35.0"),
            ("mod_version=4.35.0", "mod_version=4.35.0-dev"),
        ] {
            assert!(parse(valid.replace(old, new).as_bytes()).is_err(), "{new}");
        }
        assert!(parse(valid.replace('\n', "\r\n").as_bytes()).is_ok());
    }
}
