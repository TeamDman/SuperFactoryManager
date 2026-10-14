use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportArticleFigure {
    pub image_id: String,
    pub category: String,
    pub version: String,
    pub value: String,
    pub description: String,
    pub tooltip: String,
}
