use crate::mod_description::ModDescription;
use eyre::Result;
use eyre::ensure;

/// Zero-sized witness for SFM's fixed description.
///
/// The value comes from the [SFM project metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).
/// Generic syntax is owned by [`ModDescription`]; this witness establishes the exact
/// project value rather than narrowing every valid mod's metadata to SFM.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub struct SfmModDescription;

impl SfmModDescription {
    pub const VALUE: &'static str =
        "Adds a certain magical factory manager to the game.\nAutomation is within your grasp!";
}

impl TryFrom<SfmModDescription> for ModDescription {
    type Error = eyre::Report;

    fn try_from(_: SfmModDescription) -> Result<Self> {
        Self::parse(SfmModDescription::VALUE)
    }
}

impl TryFrom<&ModDescription> for SfmModDescription {
    type Error = eyre::Report;

    fn try_from(value: &ModDescription) -> Result<Self> {
        ensure!(
            value.as_str() == Self::VALUE,
            "metadata must use SFM's description"
        );
        Ok(Self)
    }
}

impl TryFrom<ModDescription> for SfmModDescription {
    type Error = eyre::Report;

    fn try_from(value: ModDescription) -> Result<Self> {
        Self::try_from(&value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn witness_is_zero_sized_and_round_trips_through_generic_metadata() -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModDescription>(), 0);
        let value: Result<ModDescription> = SfmModDescription.try_into();
        let value = value?;
        assert_eq!(value.as_str(), SfmModDescription::VALUE);
        assert_eq!(value, ModDescription::parse(SfmModDescription::VALUE)?);
        assert_eq!(SfmModDescription::try_from(&value)?, SfmModDescription);
        assert_eq!(SfmModDescription::try_from(value)?, SfmModDescription);
        Ok(())
    }

    #[test]
    fn another_valid_generic_value_is_not_an_sfm_witness() -> Result<()> {
        let value = ModDescription::parse("Another mod description.")?;
        assert!(SfmModDescription::try_from(&value).is_err());
        assert!(SfmModDescription::try_from(value).is_err());
        Ok(())
    }
}
