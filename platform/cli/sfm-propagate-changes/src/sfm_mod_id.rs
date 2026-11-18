use crate::mod_id::ModId;
use eyre::Result;
use eyre::ensure;

/// Zero-sized witness for SFM's fixed mod identifier.
///
/// The value comes from the [SFM project metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).
/// Generic syntax is owned by [`ModId`]; this witness establishes the exact
/// project value rather than narrowing every valid mod's metadata to SFM.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub struct SfmModId;

impl SfmModId {
    pub const VALUE: &'static str = "sfm";
}

impl TryFrom<SfmModId> for ModId {
    type Error = eyre::Report;

    fn try_from(_: SfmModId) -> Result<Self> {
        Self::parse(SfmModId::VALUE)
    }
}

impl TryFrom<&ModId> for SfmModId {
    type Error = eyre::Report;

    fn try_from(value: &ModId) -> Result<Self> {
        ensure!(value.as_str() == Self::VALUE, "selected mod_id must be sfm");
        Ok(Self)
    }
}

impl TryFrom<ModId> for SfmModId {
    type Error = eyre::Report;

    fn try_from(value: ModId) -> Result<Self> {
        Self::try_from(&value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn witness_is_zero_sized_and_round_trips_through_generic_metadata() -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModId>(), 0);
        let value: Result<ModId> = SfmModId.try_into();
        let value = value?;
        assert_eq!(value.as_str(), SfmModId::VALUE);
        assert_eq!(value, ModId::parse(SfmModId::VALUE)?);
        assert_eq!(SfmModId::try_from(&value)?, SfmModId);
        assert_eq!(SfmModId::try_from(value)?, SfmModId);
        Ok(())
    }

    #[test]
    fn another_valid_generic_value_is_not_an_sfm_witness() -> Result<()> {
        let value = ModId::parse("another")?;
        assert!(SfmModId::try_from(&value).is_err());
        assert!(SfmModId::try_from(value).is_err());
        Ok(())
    }
}
