use crate::minecraft_mapping_version::MinecraftMappingVersion;
use crate::minecraft_mapping_version_proxy::MinecraftMappingVersionProxy;
use crate::mod_authors::ModAuthors;
use crate::mod_authors_csv_proxy::ModAuthorsCsvProxy;
use crate::mod_description::ModDescription;
use crate::mod_group_id::ModGroupId;
use crate::mod_id::ModId;
use crate::mod_issue_tracker::ModIssueTracker;
use crate::mod_name::ModName;
use crate::mod_software_license::ModSoftwareLicense;
use crate::mod_version::ModVersion;
use crate::raw_gradle_properties_document::RawGradlePropertiesDocument;
use crate::semver_version_range::SemverVersionRange;
use eyre::Result;
use facet::Facet;

/// Named project settings parsed from a complete SFM Gradle properties file.
///
/// Dotted Gradle keys retain their external spelling through field renames.
/// Optional fields retain absence; parsing does not invent Gradle defaults.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct GradleDotPropertiesFileContent {
    #[facet(rename = "org.gradle.jvmargs")]
    pub org_gradle_jvmargs: String,
    #[facet(rename = "org.gradle.daemon")]
    pub org_gradle_daemon: bool,
    #[facet(default, rename = "org.gradle.debug")]
    pub org_gradle_debug: Option<bool>,
    #[facet(default, rename = "org.gradle.parallel")]
    pub org_gradle_parallel: Option<bool>,
    #[facet(default, rename = "org.gradle.caching")]
    pub org_gradle_caching: Option<bool>,
    pub minecraft_version: String,
    pub minecraft_version_range: SemverVersionRange,
    pub neo_version: String,
    pub neo_version_range: SemverVersionRange,
    pub loader_version_range: SemverVersionRange,
    #[facet(default)]
    pub mapping_channel: Option<String>,
    #[facet(default, opaque, proxy = MinecraftMappingVersionProxy)]
    pub mapping_version: Option<MinecraftMappingVersion>,
    #[facet(default, rename = "neogradle.subsystems.parchment.minecraftVersion")]
    pub neogradle_subsystems_parchment_minecraft_version: Option<String>,
    #[facet(default, rename = "neogradle.subsystems.parchment.mappingsVersion")]
    pub neogradle_subsystems_parchment_mappings_version: Option<String>,
    #[facet(default)]
    pub pack_format_number: Option<u32>,
    pub mod_id: ModId,
    pub mod_name: ModName,
    pub mod_license: ModSoftwareLicense,
    pub mod_version: ModVersion,
    pub mod_issue_tracker: ModIssueTracker,
    pub mod_group_id: ModGroupId,
    #[facet(opaque, proxy = ModAuthorsCsvProxy)]
    pub mod_authors: ModAuthors,
    pub mod_description: ModDescription,
}

impl GradleDotPropertiesFileContent {
    /// # Errors
    /// Rejects invalid property syntax, unknown/duplicate/missing fields, invalid
    /// booleans or unsigned integers, and unsupported version-range syntax.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        RawGradlePropertiesDocument::from_bytes(bytes)?.deserialize::<Self>()
    }

    /// # Errors
    /// Rejects the same syntax and schema errors as the byte-based constructor.
    pub fn parse(contents: String) -> Result<Self> {
        Self::from_bytes(&contents.into_bytes())
    }
}

/// Supply complete non-metadata settings to focused metadata fixtures.
#[cfg(test)]
pub(crate) fn test_content(metadata: &str) -> String {
    format!(
        "org.gradle.jvmargs=-Xmx3G\n\
         org.gradle.daemon=false\n\
         minecraft_version_range=[1,)\n\
         neo_version=43.4.0\n\
         neo_version_range=[1,)\n\
         loader_version_range=[1,)\n\
         mod_name=Super Factory Manager (SFM)\n\
         mod_license=Mozilla Public License Version 2.0\n\
         mod_issue_tracker=https://github.com/TeamDman/SuperFactoryManager/issues\n\
         mod_group_id=ca.teamdman.sfm\n\
         mod_authors=TeamDman\n\
         mod_description=Adds a certain magical factory manager to the game.\\nAutomation is within your grasp!\n\
         {metadata}"
    )
}

#[cfg(test)]
mod tests {
    use super::GradleDotPropertiesFileContent;
    use super::test_content;
    use crate::minecraft_mapping_version::MinecraftMappingVersion;
    use crate::mod_description::ModDescription;
    use crate::mod_id::ModId;
    use crate::mod_name::ModName;
    use crate::mod_version::ModVersion;
    use crate::semver_version_bound::SemverVersionBound;
    use chrono::NaiveDate;
    use eyre::Result;
    use std::fs;
    use std::path::Path;

    const METADATA: &str = "mod_id=sfm\nmod_version=4.35.0\nminecraft_version=1.21\n";

    #[test]
    fn named_fields_use_external_gradle_spellings_and_typed_scalars() -> Result<()> {
        let text = test_content(&format!(
            "{METADATA}\
             org.gradle.debug=true\n\
             org.gradle.parallel=false\n\
             org.gradle.caching=true\n\
             mapping_channel=parchment\n\
             mapping_version=2022.11.27-1.19.2\n\
             neogradle.subsystems.parchment.minecraftVersion=1.20.6\n\
             neogradle.subsystems.parchment.mappingsVersion=2024.05.01\n\
             pack_format_number=18\n"
        ));
        let properties = GradleDotPropertiesFileContent::from_bytes(text.as_bytes())?;
        assert_eq!(properties.org_gradle_jvmargs, "-Xmx3G");
        assert!(!properties.org_gradle_daemon);
        assert_eq!(properties.org_gradle_debug, Some(true));
        assert_eq!(properties.org_gradle_parallel, Some(false));
        assert_eq!(properties.org_gradle_caching, Some(true));
        assert_eq!(properties.pack_format_number, Some(18));
        assert_eq!(properties.mapping_channel.as_deref(), Some("parchment"));
        let Some(MinecraftMappingVersion::Parchment((date, version))) =
            properties.mapping_version.as_ref()
        else {
            eyre::bail!("expected a dated Parchment mapping");
        };
        assert_eq!(*date, NaiveDate::from_ymd_opt(2022, 11, 27).unwrap());
        assert_eq!(version.as_str(), "1.19.2");
        assert_eq!(
            properties
                .neogradle_subsystems_parchment_minecraft_version
                .as_deref(),
            Some("1.20.6")
        );
        assert_eq!(
            properties
                .neogradle_subsystems_parchment_mappings_version
                .as_deref(),
            Some("2024.05.01")
        );
        assert_eq!(properties.mod_id.as_str(), "sfm");
        assert_eq!(properties.mod_name.as_str(), "Super Factory Manager (SFM)");
        assert_eq!(properties.mod_license.to_string(), "MPL-2.0");
        assert_eq!(
            properties.mod_issue_tracker.as_str(),
            "https://github.com/TeamDman/SuperFactoryManager/issues"
        );
        assert_eq!(properties.mod_group_id.to_string(), "ca.teamdman.sfm");
        assert_eq!(properties.mod_authors.as_slice()[0].as_str(), "TeamDman");
        assert_eq!(
            properties.mod_description.as_str(),
            "Adds a certain magical factory manager to the game.\nAutomation is within your grasp!"
        );
        Ok(())
    }

    #[test]
    fn domain_scalar_json_roundtrips_escape_human_text_and_reject_invalid_values() -> Result<()> {
        let name = ModName::parse("Factory \"Δ\" 'manager'")?;
        let wire = facet_json::to_string(&name)?;
        assert_eq!(facet_json::from_str::<ModName>(&wire)?, name);
        let description = ModDescription::parse("First \"quoted\" line\n日本語 and 'apostrophes'")?;
        let wire = facet_json::to_string(&description)?;
        assert_eq!(facet_json::from_str::<ModDescription>(&wire)?, description);
        assert!(facet_json::from_str::<ModId>(r#""INVALID""#).is_err());
        let version = ModVersion::parse("4.35.0-dev.1+build.7")?;
        let wire = facet_json::to_string(&version)?;
        assert_eq!(facet_json::from_str::<ModVersion>(&wire)?, version);
        Ok(())
    }

    #[test]
    fn optional_settings_retain_absence_and_versions_retain_identifiers() -> Result<()> {
        let text = test_content("mod_id=sfm\nmod_version=4.35.0-dev.abc\nminecraft_version=1.20\n")
            .replace("neo_version=43.4.0", "neo_version=26.1.2.72");
        let properties = GradleDotPropertiesFileContent::parse(text)?;
        assert_eq!(properties.org_gradle_debug, None);
        assert_eq!(properties.org_gradle_parallel, None);
        assert_eq!(properties.org_gradle_caching, None);
        assert_eq!(properties.mapping_channel, None);
        assert_eq!(properties.mapping_version, None);
        assert_eq!(
            properties.neogradle_subsystems_parchment_minecraft_version,
            None
        );
        assert_eq!(
            properties.neogradle_subsystems_parchment_mappings_version,
            None
        );
        assert_eq!(properties.pack_format_number, None);
        assert_eq!(properties.minecraft_version, "1.20");
        assert_eq!(properties.neo_version, "26.1.2.72");
        assert_eq!(properties.mod_version.to_string(), "4.35.0-dev.abc");
        Ok(())
    }

    #[test]
    fn range_fields_have_included_excluded_and_unbounded_endpoints() -> Result<()> {
        let text = test_content(METADATA)
            .replace(
                "minecraft_version_range=[1,)",
                "minecraft_version_range=[1.21,1.21.1)",
            )
            .replace("neo_version_range=[1,)", "neo_version_range=[21.0.0-beta,)")
            .replace("loader_version_range=[1,)", "loader_version_range=(,4]");
        let properties = GradleDotPropertiesFileContent::parse(text)?;
        assert_eq!(
            properties.minecraft_version_range.lower(),
            &SemverVersionBound::Included("1.21".to_owned())
        );
        assert_eq!(
            properties.minecraft_version_range.upper(),
            &SemverVersionBound::Excluded("1.21.1".to_owned())
        );
        assert_eq!(
            properties.neo_version_range.lower(),
            &SemverVersionBound::Included("21.0.0-beta".to_owned())
        );
        assert_eq!(
            properties.neo_version_range.upper(),
            &SemverVersionBound::Unbounded
        );
        assert_eq!(
            properties.loader_version_range.lower(),
            &SemverVersionBound::Unbounded
        );
        assert_eq!(
            properties.loader_version_range.upper(),
            &SemverVersionBound::Included("4".to_owned())
        );
        Ok(())
    }

    #[test]
    fn mapping_proxy_preserves_official_and_cross_version_components() -> Result<()> {
        let official = GradleDotPropertiesFileContent::parse(test_content(&format!(
            "{METADATA}mapping_channel=official\nmapping_version=1.20.1\n"
        )))?;
        let Some(MinecraftMappingVersion::Official(version)) = official.mapping_version else {
            eyre::bail!("expected an official mapping version");
        };
        assert_eq!(version.as_str(), "1.20.1");

        let cross = GradleDotPropertiesFileContent::parse(test_content(&format!(
            "{METADATA}mapping_channel=parchment\nmapping_version=1.19.3-2023.03.12-1.19.4\n"
        )))?;
        let Some(MinecraftMappingVersion::CrossVersionParchment {
            mappings: (date, source),
            target,
        }) = cross.mapping_version
        else {
            eyre::bail!("expected a cross-version Parchment mapping");
        };
        assert_eq!(date, NaiveDate::from_ymd_opt(2023, 3, 12).unwrap());
        assert_eq!(source.as_str(), "1.19.3");
        assert_eq!(target.as_str(), "1.19.4");
        Ok(())
    }

    #[test]
    fn complete_project_schema_rejects_missing_unknown_and_duplicate_keys() {
        let valid = test_content(METADATA);
        for text in [
            valid.replace("mod_name=Super Factory Manager (SFM)\n", ""),
            valid.replace("org.gradle.daemon=false\n", ""),
            valid.replace("minecraft_version_range=[1,)\n", ""),
            format!("{valid}unknown_project_property=unexpected\n"),
            format!("{valid}org_gradle_debug=true\n"),
            format!("{valid}mod_id=sfm\n"),
            format!("{valid}org.gradle.daemon=true\n"),
            METADATA.to_owned(),
        ] {
            assert!(
                GradleDotPropertiesFileContent::parse(text.clone()).is_err(),
                "{text:?}"
            );
        }
    }

    #[test]
    fn typed_fields_reject_invalid_booleans_numbers_and_ranges() {
        let valid = test_content(METADATA);
        for text in [
            valid.replace("org.gradle.daemon=false", "org.gradle.daemon=TRUE"),
            valid.replace("org.gradle.daemon=false", "org.gradle.daemon=1"),
            format!("{valid}org.gradle.debug=not-a-bool\n"),
            format!("{valid}pack_format_number=-1\n"),
            format!("{valid}pack_format_number=4294967296\n"),
            format!("{valid}pack_format_number=eighteen\n"),
            valid.replace(
                "minecraft_version_range=[1,)",
                "minecraft_version_range=1.21",
            ),
            valid.replace("neo_version_range=[1,)", "neo_version_range=[21.0.0-beta,]"),
            valid.replace("loader_version_range=[1,)", "loader_version_range=[,4]"),
            format!("{valid}mapping_version=2023.02.29-1.19.2\n"),
            format!("{valid}mapping_version=not-a-version\n"),
            format!("{valid}mapping_version=1.19.3-2023.03.12-1.19.x\n"),
        ] {
            assert!(
                GradleDotPropertiesFileContent::parse(text.clone()).is_err(),
                "{text:?}"
            );
        }
    }

    #[test]
    fn every_authored_project_properties_file_has_the_named_schema() -> Result<()> {
        let root = Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../minecraft/core-liquid-template/build");
        let mut parsed = 0;
        for entry in walkdir::WalkDir::new(root).follow_links(false) {
            let entry = entry?;
            if !entry.file_type().is_file() || entry.file_name() != "gradle.properties" {
                continue;
            }
            let bytes = fs::read(entry.path())?;
            let properties = GradleDotPropertiesFileContent::from_bytes(&bytes)
                .map_err(|error| eyre::eyre!("{}: {error}", entry.path().display()))?;
            assert_eq!(properties.mod_id.as_str(), "sfm");
            assert_eq!(
                properties.mod_description.as_str(),
                "Adds a certain magical factory manager to the game.\nAutomation is within your grasp!"
            );
            parsed += 1;
        }
        assert_eq!(
            parsed, 31,
            "selected and review-only authored property corpus"
        );
        Ok(())
    }
}
