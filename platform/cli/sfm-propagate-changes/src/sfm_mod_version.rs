use crate::mod_version::ModVersion;
use eyre::Result;
use eyre::ensure;

/// Zero-sized evidence of one exact SFM version supplied as compile-time numbers.
///
/// [SemVer](https://semver.org/spec/v2.0.0.html) distinguishes prerelease/build
/// data; an exact release witness admits neither. For example,
/// `SfmModVersion<4, 35, 0>` represents the 4.35.0 release. Projection versions
/// remain parsed [`ModVersion`] values until the caller requests this proof.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SfmModVersion<const MAJOR: u64, const MINOR: u64, const PATCH: u64>;

impl<const MAJOR: u64, const MINOR: u64, const PATCH: u64>
    TryFrom<SfmModVersion<MAJOR, MINOR, PATCH>> for ModVersion
{
    type Error = eyre::Report;

    fn try_from(_: SfmModVersion<MAJOR, MINOR, PATCH>) -> Result<Self> {
        Self::parse(&format!("{MAJOR}.{MINOR}.{PATCH}"))
    }
}
impl<const MAJOR: u64, const MINOR: u64, const PATCH: u64> TryFrom<&ModVersion>
    for SfmModVersion<MAJOR, MINOR, PATCH>
{
    type Error = eyre::Report;
    fn try_from(value: &ModVersion) -> Result<Self> {
        let version = value.version();
        ensure!(
            version.major == MAJOR
                && version.minor == MINOR
                && version.patch == PATCH
                && version.pre.is_empty()
                && version.build.is_empty(),
            "mod version must be exactly {MAJOR}.{MINOR}.{PATCH}"
        );
        Ok(Self)
    }
}
impl<const MAJOR: u64, const MINOR: u64, const PATCH: u64> TryFrom<ModVersion>
    for SfmModVersion<MAJOR, MINOR, PATCH>
{
    type Error = eyre::Report;
    fn try_from(value: ModVersion) -> Result<Self> {
        Self::try_from(&value)
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn exact_version_witness_is_zero_sized_and_rejects_different_or_qualified_versions()
    -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModVersion<4, 35, 0>>(), 0);
        let generic: Result<ModVersion> = SfmModVersion::<4, 35, 0>.try_into();
        let generic = generic?;
        assert_eq!(generic.to_string(), "4.35.0");
        assert!(SfmModVersion::<4, 35, 0>::try_from(generic).is_ok());
        for value in ["4.34.0", "4.35.0-dev", "4.35.0+build"] {
            assert!(SfmModVersion::<4, 35, 0>::try_from(ModVersion::parse(value)?).is_err());
        }
        Ok(())
    }
}
