use crate::mod_software_license::ModSoftwareLicense;
use crate::software_license::SoftwareLicense;
use eyre::Result;
use eyre::ensure;

/// Zero-sized witness for SFM's Mozilla Public License 2.0 identity.
///
/// The [SFM project metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties)
/// uses the human-readable license name. [`ModSoftwareLicense`] owns parsing,
/// including aliases for the [SPDX MPL-2.0 identity](https://spdx.org/licenses/MPL-2.0.html).
/// This witness checks that identity rather than requiring one wire spelling.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub struct SfmModSoftwareLicense;

impl SfmModSoftwareLicense {
    pub const VALUE: &'static str = "Mozilla Public License Version 2.0";
}

impl TryFrom<SfmModSoftwareLicense> for ModSoftwareLicense {
    type Error = eyre::Report;

    fn try_from(_: SfmModSoftwareLicense) -> Result<Self> {
        Self::parse(SfmModSoftwareLicense::VALUE)
    }
}

impl TryFrom<&ModSoftwareLicense> for SfmModSoftwareLicense {
    type Error = eyre::Report;

    fn try_from(value: &ModSoftwareLicense) -> Result<Self> {
        ensure!(
            value.license() == &SoftwareLicense::Mpl2_0,
            "metadata must use SFM's MPL 2.0 license"
        );
        Ok(Self)
    }
}

impl TryFrom<ModSoftwareLicense> for SfmModSoftwareLicense {
    type Error = eyre::Report;

    fn try_from(value: ModSoftwareLicense) -> Result<Self> {
        Self::try_from(&value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn witness_is_zero_sized_and_round_trips_through_generic_metadata() -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModSoftwareLicense>(), 0);
        let value: Result<ModSoftwareLicense> = SfmModSoftwareLicense.try_into();
        let value = value?;
        assert_eq!(
            value,
            ModSoftwareLicense::parse(SfmModSoftwareLicense::VALUE)?
        );
        assert_eq!(value.license(), &SoftwareLicense::Mpl2_0);
        assert_eq!(
            SfmModSoftwareLicense::try_from(&value)?,
            SfmModSoftwareLicense
        );
        assert_eq!(
            SfmModSoftwareLicense::try_from(value)?,
            SfmModSoftwareLicense
        );
        Ok(())
    }

    #[test]
    fn mpl_aliases_prove_the_same_sfm_license_identity() -> Result<()> {
        for label in [
            SfmModSoftwareLicense::VALUE,
            "MPL-2.0",
            "Mozilla Public License 2.0",
        ] {
            assert_eq!(
                SfmModSoftwareLicense::try_from(ModSoftwareLicense::parse(label)?)?,
                SfmModSoftwareLicense
            );
        }
        Ok(())
    }

    #[test]
    fn other_license_declarations_are_not_sfm_license_witnesses() -> Result<()> {
        let value = ModSoftwareLicense::parse("All Rights Reserved")?;
        assert!(SfmModSoftwareLicense::try_from(&value).is_err());
        assert!(SfmModSoftwareLicense::try_from(value).is_err());
        Ok(())
    }
}
