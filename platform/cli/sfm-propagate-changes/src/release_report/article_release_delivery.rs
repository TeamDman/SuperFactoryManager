use super::article_release_artifact::ArticleReleaseArtifact;
use facet::Facet;

/// Reviewed artifact delivery assertions, separate from runtime acceptance.
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub(super) struct ArticleReleaseDelivery {
    pub schema: String,
    pub artifacts: Vec<ArticleReleaseArtifact>,
}
