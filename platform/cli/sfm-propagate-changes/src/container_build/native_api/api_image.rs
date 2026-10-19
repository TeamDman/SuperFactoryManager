use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiImage {
    #[facet(rename = "Id")]
    pub id: String,
    #[facet(rename = "Digest")]
    pub digest: String,
    #[facet(rename = "ManifestType")]
    pub manifest_type: String,
    #[facet(rename = "RepoDigests", default)]
    pub repo_digests: Vec<String>,
    #[facet(rename = "Os")]
    pub os: String,
    #[facet(rename = "Architecture")]
    pub architecture: String,
    #[facet(rename = "Config", default)]
    pub config: super::api_image_config::ApiImageConfig,
}
