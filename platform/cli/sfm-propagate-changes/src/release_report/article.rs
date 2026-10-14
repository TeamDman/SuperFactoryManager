use facet::Facet;
/// Controlled, release-specific editorial profile. Evidence remains in the report document.
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportArticle {
    pub profile: String,
    pub program_diff_id: String,
    pub branch_diff_id: String,
    pub primary_images: Vec<super::article_figure::ReleaseReportArticleFigure>,
    pub supporting_images: Vec<String>,
    pub historical_images: Vec<String>,
    pub linux_images: Vec<String>,
}
