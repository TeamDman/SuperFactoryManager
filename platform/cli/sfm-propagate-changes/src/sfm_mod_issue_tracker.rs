use crate::mod_issue_tracker::ModIssueTracker;
use eyre::Result;
use eyre::ensure;

/// Zero-sized witness for SFM's GitHub repository issue tracker.
///
/// The route is recorded in the [SFM project metadata](https://github.com/TeamDman/SuperFactoryManager/blob/d16af8c38/platform/minecraft/gradle.properties).
/// [`ModIssueTracker`] owns the URL contract. GitHub owner and repository names
/// are matched without ASCII case sensitivity, while conversion emits SFM's spelling.
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
pub struct SfmModIssueTracker;

impl SfmModIssueTracker {
    pub const VALUE: &'static str = "https://github.com/TeamDman/SuperFactoryManager/issues";
}

impl TryFrom<SfmModIssueTracker> for ModIssueTracker {
    type Error = eyre::Report;

    fn try_from(_: SfmModIssueTracker) -> Result<Self> {
        Self::parse(SfmModIssueTracker::VALUE)
    }
}

impl TryFrom<&ModIssueTracker> for SfmModIssueTracker {
    type Error = eyre::Report;

    fn try_from(value: &ModIssueTracker) -> Result<Self> {
        ensure!(
            value.as_str().eq_ignore_ascii_case(Self::VALUE),
            "metadata must use SFM's GitHub issue tracker"
        );
        Ok(Self)
    }
}

impl TryFrom<ModIssueTracker> for SfmModIssueTracker {
    type Error = eyre::Report;

    fn try_from(value: ModIssueTracker) -> Result<Self> {
        Self::try_from(&value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn witness_is_zero_sized_and_round_trips_through_generic_metadata() -> Result<()> {
        assert_eq!(std::mem::size_of::<SfmModIssueTracker>(), 0);
        let value: ModIssueTracker = SfmModIssueTracker.try_into()?;
        assert_eq!(value.as_str(), SfmModIssueTracker::VALUE);
        assert_eq!(SfmModIssueTracker::try_from(&value)?, SfmModIssueTracker);
        assert_eq!(SfmModIssueTracker::try_from(value)?, SfmModIssueTracker);
        Ok(())
    }

    #[test]
    fn repository_identity_accepts_case_and_canonical_trailing_slash() -> Result<()> {
        let value =
            ModIssueTracker::parse("https://github.com/teamdman/superfactorymanager/issues/")?;
        assert_eq!(SfmModIssueTracker::try_from(value)?, SfmModIssueTracker);
        Ok(())
    }

    #[test]
    fn other_valid_issue_trackers_are_not_sfm_witnesses() -> Result<()> {
        let value = ModIssueTracker::parse("https://github.com/another/another-repo/issues")?;
        assert!(SfmModIssueTracker::try_from(&value).is_err());
        assert!(SfmModIssueTracker::try_from(value).is_err());
        Ok(())
    }
}
