use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportCase {
    pub id: String,
    pub projection: String,
    pub status: String,
    pub observed: String,
    pub method: String,
    pub source_paths: Vec<String>,
}
