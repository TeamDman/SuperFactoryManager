use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportDocument {
    pub schema: String,
    pub title: String,
    pub generated_utc: String,
    pub summary: String,
    pub limitations: Vec<String>,
    pub sections: Vec<super::section::ReleaseReportSection>,
    pub cases: Vec<super::case::ReleaseReportCase>,
    pub diffs: Vec<super::code_diff::ReleaseReportCodeDiff>,
    pub images: Vec<super::image::ReleaseReportImage>,
    #[facet(default)]
    pub article: Option<super::article::ReleaseReportArticle>,
    #[facet(default)]
    pub correlations: Vec<super::source_correlation::SourceCorrelation>,
    pub jars: Vec<super::jar_pair::ReleaseReportJarPair>,
}
