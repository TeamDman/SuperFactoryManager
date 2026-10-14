use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportImage {
    pub id: String,
    pub relative_path: String,
    pub sha256: String,
    pub caption: String,
    pub alt: String,
}
