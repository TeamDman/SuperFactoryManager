//! One explicit GitHub reference identity; kind is never inferred from its number.
use super::source_reference_kind::SourceReferenceKind;
use facet::Facet;
#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(deny_unknown_fields)]
pub struct SourceReference {
    pub kind: SourceReferenceKind,
    pub owner: String,
    pub repository: String,
    pub number: u32,
}
impl SourceReference {
    #[must_use]
    pub fn sfm(kind: SourceReferenceKind, number: u32) -> Self {
        Self {
            kind,
            owner: "TeamDman".into(),
            repository: "SuperFactoryManager".into(),
            number,
        }
    }
    /// # Errors
    /// Rejects unsafe repository components or nonpositive reference numbers.
    pub fn validate(&self) -> eyre::Result<()> {
        eyre::ensure!(self.number > 0, "reference requires a positive number");
        eyre::ensure!(
            !self.owner.is_empty()
                && self.owner.len() <= 39
                && !self.owner.starts_with('-')
                && !self.owner.ends_with('-')
                && self
                    .owner
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b == b'-'),
            "invalid GitHub reference owner"
        );
        eyre::ensure!(
            !self.repository.is_empty()
                && self.repository.len() <= 100
                && !matches!(self.repository.as_str(), "." | "..")
                && self
                    .repository
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-')),
            "invalid GitHub reference repository"
        );
        Ok(())
    }
    #[must_use]
    pub fn url(&self) -> String {
        let route = match self.kind {
            SourceReferenceKind::Issue => "issues",
            SourceReferenceKind::PullRequest => "pull",
            SourceReferenceKind::Discussion => "discussions",
        };
        format!(
            "https://github.com/{}/{}/{route}/{}",
            self.owner, self.repository, self.number
        )
    }
    #[must_use]
    pub fn identity(&self) -> (SourceReferenceKind, String, String, u32) {
        (
            self.kind,
            self.owner.to_ascii_lowercase(),
            self.repository.to_ascii_lowercase(),
            self.number,
        )
    }
    pub(super) fn parse(kind: &str, scope: Option<&str>, number: &str) -> eyre::Result<Self> {
        eyre::ensure!(
            !number.is_empty() && number.bytes().all(|b| b.is_ascii_digit()),
            "reference requires an integer literal"
        );
        let kind = match kind {
            "issue" => SourceReferenceKind::Issue,
            "pr" => SourceReferenceKind::PullRequest,
            "discussion" => SourceReferenceKind::Discussion,
            _ => eyre::bail!("unknown reference kind"),
        };
        let mut reference = Self::sfm(kind, number.parse()?);
        if let Some(scope) = scope {
            let scope = scope
                .strip_prefix('"')
                .and_then(|s| s.strip_suffix('"'))
                .or_else(|| scope.strip_prefix('\'').and_then(|s| s.strip_suffix('\'')))
                .unwrap_or(scope);
            let (owner, repository) = scope
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("reference repository must be owner/repository"))?;
            reference.owner = owner.into();
            reference.repository = repository.into();
        }
        reference.validate()?;
        Ok(reference)
    }
}
