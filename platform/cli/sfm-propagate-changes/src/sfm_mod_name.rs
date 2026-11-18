use crate::mod_name::ModName;
use eyre::Result;
use eyre::ensure;

/// Zero-sized witness for SFM's fixed display name.
///
/// The value comes from the [SFM project metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).
/// Generic syntax is owned by [`ModName`]; this witness establishes the exact
/// project value rather than narrowing every valid mod's metadata to SFM.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub struct SfmModName;

impl SfmModName {
    pub const VALUE: &'static str = "Super Factory Manager (SFM)";
}

impl TryFrom<&ModName> for SfmModName {
    type Error = eyre::Report;

    fn try_from(value: &ModName) -> Result<Self> {
        ensure!(
            value.as_str() == Self::VALUE,
            "metadata must use SFM's display name"
        );
        Ok(Self)
    }
}

impl TryFrom<ModName> for SfmModName {
    type Error = eyre::Report;

    fn try_from(value: ModName) -> Result<Self> {
        Self::try_from(&value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn witness_is_zero_sized_and_round_trips_through_generic_metadata() -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModName>(), 0);
        let value: ModName = SfmModName.into();
        assert_eq!(value.as_str(), SfmModName::VALUE);
        assert_eq!(value, ModName::parse(SfmModName::VALUE)?);
        assert_eq!(SfmModName::try_from(&value)?, SfmModName);
        assert_eq!(SfmModName::try_from(value)?, SfmModName);
        Ok(())
    }

    #[test]
    fn another_valid_generic_value_is_not_an_sfm_witness() -> Result<()> {
        let value = ModName::parse("Another factory mod")?;
        assert!(SfmModName::try_from(&value).is_err());
        assert!(SfmModName::try_from(value).is_err());
        Ok(())
    }
}
