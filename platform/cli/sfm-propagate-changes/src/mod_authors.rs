use crate::github_username::GitHubUsername;
use eyre::Result;
use eyre::ensure;

/// An ordered, nonempty list of ordinary GitHub usernames credited as mod authors.
///
/// [Loader author metadata](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// is display text and permits ordinary names with spaces. Requiring GitHub
/// identities and at least one author is SFM policy. CSV is handled by the field
/// proxy, while this type contains only parsed identities and their order.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ModAuthors(Box<[GitHubUsername]>);

impl ModAuthors {
    /// # Errors
    /// Rejects an empty author list.
    pub fn new(authors: Vec<GitHubUsername>) -> Result<Self> {
        ensure!(
            !authors.is_empty(),
            "mod authors need at least one GitHub username"
        );
        Ok(Self(authors.into_boxed_slice()))
    }

    #[must_use]
    pub fn as_slice(&self) -> &[GitHubUsername] {
        &self.0
    }
}

impl TryFrom<Vec<GitHubUsername>> for ModAuthors {
    type Error = eyre::Report;

    fn try_from(value: Vec<GitHubUsername>) -> Result<Self> {
        Self::new(value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn preserves_typed_authors_and_credit_order() -> Result<()> {
        let authors = ModAuthors::new(vec![
            GitHubUsername::parse("TeamDman")?,
            GitHubUsername::parse("another-author")?,
        ])?;
        assert_eq!(authors.as_slice()[0].as_str(), "TeamDman");
        assert_eq!(authors.as_slice()[1].as_str(), "another-author");
        Ok(())
    }

    #[test]
    fn rejects_an_empty_credit_list() {
        assert!(ModAuthors::new(Vec::new()).is_err());
    }
}
