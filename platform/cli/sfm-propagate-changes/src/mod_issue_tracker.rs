use crate::github_issue_tracker_url::GitHubIssueTrackerUrl;
use eyre::Result;
use facet::Facet;
use std::fmt;
use std::str::FromStr;

/// A mod's parsed issue tracker under SFM's GitHub-only metadata policy.
///
/// [Loader issue metadata](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// permits a general URL. The stronger GitHub repository route is a project choice
/// owned by [`GitHubIssueTrackerUrl`], rather than a claim about every mod loader.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct ModIssueTracker(GitHubIssueTrackerUrl);

impl ModIssueTracker {
    /// # Errors
    /// Rejects values outside the project's GitHub issue-tracker URL contract.
    pub fn parse(value: &str) -> Result<Self> {
        GitHubIssueTrackerUrl::parse(value).map(Self)
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        self.0.as_str()
    }

    #[must_use]
    pub fn url(&self) -> &GitHubIssueTrackerUrl {
        &self.0
    }
}

impl FromStr for ModIssueTracker {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for ModIssueTracker {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl TryFrom<String> for ModIssueTracker {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&ModIssueTracker> for String {
    fn from(value: &ModIssueTracker) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parsed_tracker_exposes_a_validated_url() -> Result<()> {
        let value = "https://github.com/TeamDman/SuperFactoryManager/issues";
        let tracker: ModIssueTracker = value.parse()?;
        assert_eq!(tracker.as_str(), value);
        assert_eq!(tracker.url().as_url().scheme(), "https");
        assert!(ModIssueTracker::parse("https://example.invalid/issues").is_err());
        Ok(())
    }
}
