use crate::mod_group_id::ModGroupId;
use eyre::Result;
use eyre::ensure;

/// Zero-sized witness for SFM's Maven group and Java package-style namespace.
///
/// The group comes from the [SFM project metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).
/// [`ModGroupId`] owns portable package-style syntax; this witness establishes
/// the particular group rather than claiming ownership for arbitrary reversed domains.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub struct SfmModGroupId;

impl SfmModGroupId {
    pub const VALUE: &'static str = "ca.teamdman.sfm";
}

impl TryFrom<SfmModGroupId> for ModGroupId {
    type Error = eyre::Report;

    fn try_from(_: SfmModGroupId) -> Result<Self> {
        Self::parse(SfmModGroupId::VALUE)
    }
}

impl TryFrom<&ModGroupId> for SfmModGroupId {
    type Error = eyre::Report;

    fn try_from(value: &ModGroupId) -> Result<Self> {
        ensure!(
            value
                .package_name()
                .segments()
                .iter()
                .map(crate::java_identifier::JavaIdentifier::as_str)
                .eq(Self::VALUE.split('.')),
            "selected mod_group_id must be ca.teamdman.sfm"
        );
        Ok(Self)
    }
}

impl TryFrom<ModGroupId> for SfmModGroupId {
    type Error = eyre::Report;

    fn try_from(value: ModGroupId) -> Result<Self> {
        Self::try_from(&value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn witness_is_zero_sized_and_round_trips_through_generic_metadata() -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModGroupId>(), 0);
        let value: ModGroupId = SfmModGroupId.try_into()?;
        assert_eq!(value.to_string(), SfmModGroupId::VALUE);
        assert_eq!(SfmModGroupId::try_from(&value)?, SfmModGroupId);
        assert_eq!(SfmModGroupId::try_from(value)?, SfmModGroupId);
        Ok(())
    }

    #[test]
    fn another_valid_group_is_not_an_sfm_witness() -> Result<()> {
        let value = ModGroupId::parse("example.sfm")?;
        assert!(SfmModGroupId::try_from(&value).is_err());
        assert!(SfmModGroupId::try_from(value).is_err());
        Ok(())
    }
}
