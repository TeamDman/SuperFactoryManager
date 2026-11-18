use crate::gradle_dot_properties_file_content::GradleDotPropertiesFileContent;
use crate::mod_version::ModVersion;
use crate::sfm_mod_authors::SfmModAuthors;
use crate::sfm_mod_description::SfmModDescription;
use crate::sfm_mod_group_id::SfmModGroupId;
use crate::sfm_mod_id::SfmModId;
use crate::sfm_mod_issue_tracker::SfmModIssueTracker;
use crate::sfm_mod_name::SfmModName;
use crate::sfm_mod_software_license::SfmModSoftwareLicense;
use crate::sfm_mod_version::SfmModVersion;
use eyre::Result;

/// Parsed project settings whose fixed metadata identifies SFM.
///
/// The fixed values follow [SFM's authored metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).
/// Generic helpers borrow [`Self::as_generic`]; its immutable view cannot change
/// an identity while retaining this proof. Consuming the wrapper removes that
/// restriction. Version selection remains separate because projections differ.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SfmGradleDotPropertiesFileContent {
    generic: GradleDotPropertiesFileContent,
    pub mod_id: SfmModId,
    pub mod_name: SfmModName,
    pub mod_license: SfmModSoftwareLicense,
    pub mod_issue_tracker: SfmModIssueTracker,
    pub mod_group_id: SfmModGroupId,
    pub mod_authors: SfmModAuthors,
    pub mod_description: SfmModDescription,
}

impl SfmGradleDotPropertiesFileContent {
    /// # Errors
    /// Rejects malformed generic metadata or any fixed value belonging to another mod.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        GradleDotPropertiesFileContent::from_bytes(bytes)?.try_into()
    }
    #[must_use]
    pub const fn as_generic(&self) -> &GradleDotPropertiesFileContent {
        &self.generic
    }
    #[must_use]
    pub const fn mod_version(&self) -> &ModVersion {
        &self.generic.mod_version
    }
    /// # Errors
    /// Rejects versions other than the requested exact release, including qualifiers.
    pub fn require_version<const MAJOR: u64, const MINOR: u64, const PATCH: u64>(
        &self,
    ) -> Result<SfmModVersion<MAJOR, MINOR, PATCH>> {
        self.mod_version().try_into()
    }
}
impl TryFrom<GradleDotPropertiesFileContent> for SfmGradleDotPropertiesFileContent {
    type Error = eyre::Report;
    fn try_from(value: GradleDotPropertiesFileContent) -> Result<Self> {
        let mod_id = SfmModId::try_from(&value.mod_id)?;
        let mod_name = SfmModName::try_from(&value.mod_name)?;
        let mod_license = SfmModSoftwareLicense::try_from(&value.mod_license)?;
        let mod_issue_tracker = SfmModIssueTracker::try_from(&value.mod_issue_tracker)?;
        let mod_group_id = SfmModGroupId::try_from(&value.mod_group_id)?;
        let mod_authors = SfmModAuthors::try_from(&value.mod_authors)?;
        let mod_description = SfmModDescription::try_from(&value.mod_description)?;
        Ok(Self {
            generic: value,
            mod_id,
            mod_name,
            mod_license,
            mod_issue_tracker,
            mod_group_id,
            mod_authors,
            mod_description,
        })
    }
}
impl From<SfmGradleDotPropertiesFileContent> for GradleDotPropertiesFileContent {
    fn from(value: SfmGradleDotPropertiesFileContent) -> Self {
        value.generic
    }
}
impl AsRef<GradleDotPropertiesFileContent> for SfmGradleDotPropertiesFileContent {
    fn as_ref(&self) -> &GradleDotPropertiesFileContent {
        self.as_generic()
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use crate::gradle_dot_properties_file_content::test_content;
    use crate::mod_id::ModId;
    use std::fs;
    use std::path::Path;
    #[test]
    fn sfm_document_rejects_each_wrong_identity_but_generic_helpers_accept_valid_metadata()
    -> Result<()> {
        let valid = test_content("mod_id=sfm\nmod_version=4.35.0\nminecraft_version=1.21\n");
        let sfm = SfmGradleDotPropertiesFileContent::from_bytes(valid.as_bytes())?;
        let _: SfmModId = sfm.mod_id;
        assert_eq!(
            std::mem::size_of::<SfmGradleDotPropertiesFileContent>(),
            std::mem::size_of::<GradleDotPropertiesFileContent>()
        );
        assert!(sfm.require_version::<4, 35, 0>().is_ok());
        assert_eq!(sfm.as_generic().mod_id.as_str(), "sfm");
        for (before, after) in [
            ("mod_id=sfm", "mod_id=other"),
            (
                "mod_name=Super Factory Manager (SFM)",
                "mod_name=Another mod",
            ),
            (
                "mod_license=Mozilla Public License Version 2.0",
                "mod_license=All Rights Reserved",
            ),
            (
                "mod_issue_tracker=https://github.com/TeamDman/SuperFactoryManager/issues",
                "mod_issue_tracker=https://github.com/Other/Another/issues",
            ),
            (
                "mod_group_id=ca.teamdman.sfm",
                "mod_group_id=org.example.other",
            ),
            ("mod_authors=TeamDman", "mod_authors=AnotherAuthor"),
            (
                "mod_description=Adds a certain magical factory manager to the game.",
                "mod_description=Another mod description.",
            ),
        ] {
            let text = valid.replace(before, after);
            assert_ne!(text, valid);
            assert!(
                GradleDotPropertiesFileContent::from_bytes(text.as_bytes()).is_ok(),
                "{after}"
            );
            assert!(
                SfmGradleDotPropertiesFileContent::from_bytes(text.as_bytes()).is_err(),
                "{after}"
            );
        }
        for line in [
            "mod_id=sfm\n",
            "mod_name=Super Factory Manager (SFM)\n",
            "mod_license=Mozilla Public License Version 2.0\n",
        ] {
            assert!(
                SfmGradleDotPropertiesFileContent::from_bytes(valid.replace(line, "").as_bytes())
                    .is_err()
            );
        }
        let generic: GradleDotPropertiesFileContent = sfm.into();
        assert_eq!(generic.mod_id, ModId::parse("sfm")?);
        Ok(())
    }
    #[test]
    fn every_authored_and_flat_project_has_sfm_identity() -> Result<()> {
        let base = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../minecraft");
        let mut count = 0;
        for entry in
            walkdir::WalkDir::new(base.join("core-liquid-template/build")).follow_links(false)
        {
            let entry = entry?;
            if entry.file_type().is_file() && entry.file_name() == "gradle.properties" {
                SfmGradleDotPropertiesFileContent::from_bytes(&fs::read(entry.path())?)?;
                count += 1;
            }
        }
        SfmGradleDotPropertiesFileContent::from_bytes(&fs::read(base.join("gradle.properties"))?)?;
        assert_eq!(count, 31);
        Ok(())
    }
}
