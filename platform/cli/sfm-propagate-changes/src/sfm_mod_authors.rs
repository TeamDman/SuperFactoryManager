use crate::github_username::GitHubUsername;
use crate::mod_authors::ModAuthors;
use eyre::Result;
use eyre::ensure;

/// Zero-sized witness for the single GitHub identity credited in SFM metadata.
///
/// The credit comes from the [SFM project metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).
/// [`ModAuthors`] owns the typed list and [`GitHubUsername`] owns username syntax
/// and case-insensitive identity. CSV formatting is not part of this witness.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub struct SfmModAuthors;

impl SfmModAuthors {
    pub const VALUE: &'static str = "TeamDman";
}

impl TryFrom<SfmModAuthors> for ModAuthors {
    type Error = eyre::Report;

    fn try_from(_: SfmModAuthors) -> Result<Self> {
        Self::new(vec![GitHubUsername::parse(SfmModAuthors::VALUE)?])
    }
}

impl TryFrom<&ModAuthors> for SfmModAuthors {
    type Error = eyre::Report;

    fn try_from(value: &ModAuthors) -> Result<Self> {
        ensure!(
            matches!(value.as_slice(), [author] if author.as_str().eq_ignore_ascii_case(Self::VALUE)),
            "metadata must credit exactly SFM's TeamDman author identity"
        );
        Ok(Self)
    }
}

impl TryFrom<ModAuthors> for SfmModAuthors {
    type Error = eyre::Report;

    fn try_from(value: ModAuthors) -> Result<Self> {
        Self::try_from(&value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn witness_is_zero_sized_and_round_trips_through_generic_metadata() -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModAuthors>(), 0);
        let value: Result<ModAuthors> = SfmModAuthors.try_into();
        let value = value?;
        assert_eq!(value.as_slice()[0].as_str(), SfmModAuthors::VALUE);
        assert_eq!(SfmModAuthors::try_from(&value)?, SfmModAuthors);
        assert_eq!(SfmModAuthors::try_from(value)?, SfmModAuthors);
        Ok(())
    }

    #[test]
    fn author_identity_is_case_insensitive() -> Result<()> {
        let value = ModAuthors::new(vec![GitHubUsername::parse("teamdman")?])?;
        assert_eq!(SfmModAuthors::try_from(value)?, SfmModAuthors);
        Ok(())
    }

    #[test]
    fn different_or_additional_authors_are_not_sfm_witnesses() -> Result<()> {
        for names in [vec!["another-author"], vec!["TeamDman", "another-author"]] {
            let authors = names
                .into_iter()
                .map(GitHubUsername::parse)
                .collect::<Result<Vec<_>>>()?;
            let value = ModAuthors::new(authors)?;
            assert!(SfmModAuthors::try_from(&value).is_err());
            assert!(SfmModAuthors::try_from(value).is_err());
        }
        Ok(())
    }
}
