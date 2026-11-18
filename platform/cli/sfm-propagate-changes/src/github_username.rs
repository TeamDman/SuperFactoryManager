use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::cmp::Ordering;
use std::fmt;
use std::hash::Hash;
use std::hash::Hasher;
use std::str::FromStr;

/// An ordinary public GitHub username, preserving its presentation spelling.
///
/// [GitHub's signup rules](https://github.com/signup) allow ASCII alphanumerics
/// and single internal hyphens. The documented
/// [account limit](https://docs.github.com/en/enterprise-server%403.19/admin/managing-iam/iam-configuration-reference/username-considerations-for-external-authentication)
/// is 39 characters. This excludes mention syntax, bot actor logins and
/// [managed-user names](https://docs.github.com/en/enterprise-cloud%40latest/admin/managing-iam/iam-configuration-reference/username-considerations-for-external-authentication).
/// Construction establishes syntax, not account existence or availability.
/// Account-name comparison is ASCII case-insensitive; formatting retains spelling.
#[derive(Clone, Debug, Facet)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct GitHubUsername(String);

impl GitHubUsername {
    /// # Errors
    /// Rejects names outside the ordinary public-account grammar or length limit.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            (1..=39).contains(&value.len())
                && value
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
                && !value.starts_with('-')
                && !value.ends_with('-')
                && !value.contains("--"),
            "GitHub username must contain 1-39 ASCII alphanumerics with single internal hyphens"
        );
        Ok(Self(value.to_owned()))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl FromStr for GitHubUsername {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for GitHubUsername {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl PartialEq for GitHubUsername {
    fn eq(&self, other: &Self) -> bool {
        self.0.eq_ignore_ascii_case(&other.0)
    }
}

impl Eq for GitHubUsername {}

impl Ord for GitHubUsername {
    fn cmp(&self, other: &Self) -> Ordering {
        self.0
            .bytes()
            .map(|byte| byte.to_ascii_lowercase())
            .cmp(other.0.bytes().map(|byte| byte.to_ascii_lowercase()))
    }
}

impl PartialOrd for GitHubUsername {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Hash for GitHubUsername {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.0.to_ascii_lowercase().hash(state);
    }
}

impl TryFrom<String> for GitHubUsername {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&GitHubUsername> for String {
    fn from(value: &GitHubUsername) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::hash_map::DefaultHasher;

    #[test]
    fn accepts_ordinary_names_and_preserves_spelling() -> Result<()> {
        for value in ["a", "7", "TeamDman", "a-b-c", "FixtureAuthor"] {
            assert_eq!(GitHubUsername::parse(value)?.as_str(), value);
        }
        assert!(GitHubUsername::parse(&"a".repeat(39)).is_ok());
        Ok(())
    }

    #[test]
    fn rejects_nonordinary_names_and_boundary_errors() {
        for value in [
            "",
            "-a",
            "a-",
            "a--b",
            "@TeamDman",
            "two names",
            "user_name",
            "bot[bot]",
            "ümlaut",
            "a.b",
            "a/b",
            " name",
            "name ",
        ] {
            assert!(GitHubUsername::parse(value).is_err(), "{value:?}");
        }
        assert!(GitHubUsername::parse(&"a".repeat(40)).is_err());
    }

    #[test]
    fn identity_comparison_and_hashing_ignore_ascii_case() -> Result<()> {
        let left = GitHubUsername::parse("TeamDman")?;
        let right = GitHubUsername::parse("teamdman")?;
        assert_eq!(left, right);
        assert_eq!(left.cmp(&right), Ordering::Equal);
        let mut left_hash = DefaultHasher::new();
        let mut right_hash = DefaultHasher::new();
        left.hash(&mut left_hash);
        right.hash(&mut right_hash);
        assert_eq!(left_hash.finish(), right_hash.finish());
        assert_eq!(left.to_string(), "TeamDman");
        Ok(())
    }
}
