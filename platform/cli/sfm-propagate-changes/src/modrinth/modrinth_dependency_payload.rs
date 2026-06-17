use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct ModrinthDependencyPayload {
    #[facet(default, rename = "project_id")]
    pub project_id: Option<String>,
    #[facet(default, rename = "version_id")]
    pub version_id: Option<String>,
    #[facet(default, rename = "file_name")]
    pub file_name: Option<String>,
    #[facet(rename = "dependency_type")]
    pub dependency_type: String,
}
