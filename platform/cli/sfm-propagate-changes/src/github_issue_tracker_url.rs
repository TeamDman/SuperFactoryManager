use crate::github_username::GitHubUsername;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use reqwest::Url;
use std::fmt;
use std::str::FromStr;

/// A public GitHub repository's HTTPS issues landing page.
///
/// [Loader metadata](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
/// accepts general issue URLs; requiring GitHub is an SFM policy. This codec uses
/// ordinary owner names and ASCII repository names from the
/// [repository creation rules](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository).
/// It accepts one optional trailing slash and stores the canonical URL without it.
/// Credentials, query/fragment state, percent-encoded paths and dot segments are
/// excluded from this project contract. Parsing does not contact GitHub or prove
/// that the repository or its issue tracker exists.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(opaque, proxy = String, traits(FromStr))]
pub struct GitHubIssueTrackerUrl(#[facet(opaque)] Url);

impl GitHubIssueTrackerUrl {
    /// # Errors
    /// Rejects values outside the project's public GitHub issues URL contract.
    pub fn parse(value: &str) -> Result<Self> {
        ensure!(
            value == value.trim() && !value.chars().any(char::is_control),
            "GitHub issue tracker URL cannot contain padding or control characters"
        );
        let mut url = Url::parse(value).wrap_err("invalid issue tracker URL")?;
        ensure!(
            url.scheme() == "https"
                && url.host_str() == Some("github.com")
                && url.username().is_empty()
                && url.password().is_none()
                && url.port().is_none()
                && url.query().is_none()
                && url.fragment().is_none(),
            "issue tracker must be an HTTPS github.com URL without credentials, a custom port, query or fragment"
        );
        let (_, authority_path) = value
            .split_once("://")
            .ok_or_else(|| eyre::eyre!("issue tracker URL needs an explicit HTTPS authority"))?;
        ensure!(
            !authority_path.starts_with('/') && !authority_path.contains('\\'),
            "issue tracker URL needs a nonempty authority and forward-slash path"
        );
        let (_, raw_path) = authority_path.split_once('/').ok_or_else(|| {
            eyre::eyre!("issue tracker URL needs an owner/repository/issues path")
        })?;
        ensure!(
            !raw_path.contains('%') && !raw_path.split('/').any(|part| matches!(part, "." | "..")),
            "GitHub issue tracker path cannot contain encoded or dot segments"
        );
        let path = url.path().strip_prefix('/').unwrap_or(url.path());
        let path = path.strip_suffix('/').unwrap_or(path);
        let mut parts = path.split('/');
        let owner = parts.next().unwrap_or_default();
        let repository = parts.next().unwrap_or_default();
        let endpoint = parts.next().unwrap_or_default();
        ensure!(
            endpoint == "issues" && parts.next().is_none(),
            "GitHub issue tracker path must be /owner/repository/issues"
        );
        let _ = GitHubUsername::parse(owner).wrap_err("invalid GitHub repository owner")?;
        ensure!(
            (1..=100).contains(&repository.len())
                && repository.bytes().all(|byte| {
                    byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'.' | b'-')
                })
                && !matches!(repository, "." | "..")
                && !repository.eq_ignore_ascii_case(".git"),
            "invalid GitHub repository name in issue tracker URL"
        );
        if url.path().ends_with('/') {
            let canonical_path = url.path().trim_end_matches('/').to_owned();
            url.set_path(&canonical_path);
        }
        Ok(Self(url))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        self.0.as_str()
    }

    #[must_use]
    pub fn as_url(&self) -> &Url {
        &self.0
    }
}

impl FromStr for GitHubIssueTrackerUrl {
    type Err = eyre::Report;

    fn from_str(value: &str) -> Result<Self> {
        Self::parse(value)
    }
}

impl fmt::Display for GitHubIssueTrackerUrl {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl TryFrom<String> for GitHubIssueTrackerUrl {
    type Error = eyre::Report;
    fn try_from(value: String) -> eyre::Result<Self> {
        Self::parse(&value)
    }
}
impl From<&GitHubIssueTrackerUrl> for String {
    fn from(value: &GitHubIssueTrackerUrl) -> Self {
        value.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_issues_routes_and_canonicalizes_a_trailing_slash() -> Result<()> {
        let canonical = "https://github.com/TeamDman/SuperFactoryManager/issues";
        for value in [canonical.to_owned(), format!("{canonical}/")] {
            let parsed = GitHubIssueTrackerUrl::parse(&value)?;
            assert_eq!(parsed.as_str(), canonical);
            assert_eq!(parsed.as_url().host_str(), Some("github.com"));
        }
        assert!(
            GitHubIssueTrackerUrl::parse("https://github.com:443/a/repo_name.v2/issues").is_ok()
        );
        Ok(())
    }

    #[test]
    fn rejects_host_authority_and_transport_confusion() {
        for value in [
            "http://github.com/a/repo/issues",
            "https://github.com.evil.invalid/a/repo/issues",
            "https://evil.invalid/github.com/a/repo/issues",
            "https://github.com@evil.invalid/a/repo/issues",
            "https://evil@github.com/a/repo/issues",
            "https://user:password@github.com/a/repo/issues",
            "https://github.com:444/a/repo/issues",
            "https://github.com/a/repo/issues?state=open",
            "https://github.com/a/repo/issues#top",
            " https://github.com/a/repo/issues",
            "https://github.com/a/repo/issues\n",
            "https:github.com/a/repo/issues",
            "https:///github.com/a/repo/issues",
            r"https://github.com\a\repo\issues",
        ] {
            assert!(GitHubIssueTrackerUrl::parse(value).is_err(), "{value:?}");
        }
    }

    #[test]
    fn rejects_invalid_or_ambiguous_repository_paths() {
        for value in [
            "https://github.com/a/repo",
            "https://github.com/a/repo/issues/new",
            "https://github.com/a/repo/issues//",
            "https://github.com/a/repo/%69ssues",
            "https://github.com/a/%2e%2e/repo/issues",
            "https://github.com/temporary/../a/repo/issues",
            "https://github.com/@a/repo/issues",
            "https://github.com/a/.git/issues",
            "https://github.com/a/repo%20name/issues",
            "https://github.com/a//issues",
        ] {
            assert!(GitHubIssueTrackerUrl::parse(value).is_err(), "{value:?}");
        }
        assert!(
            GitHubIssueTrackerUrl::parse(&format!(
                "https://github.com/a/{}/issues",
                "r".repeat(101)
            ))
            .is_err()
        );
    }
}
