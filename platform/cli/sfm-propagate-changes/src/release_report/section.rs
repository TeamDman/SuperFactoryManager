use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportSection {
    pub id: String,
    pub title: String,
    pub body: String,
}
