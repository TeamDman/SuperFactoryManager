use facet::Facet;
#[derive(Clone, Debug, Facet)]
pub struct ImageIdentity {
    pub image_config_id: String,
    pub manifest_digest: String,
    pub manifest_media_type: String,
    pub repository_digests: Vec<String>,
    pub os: String,
    pub architecture: String,
}
