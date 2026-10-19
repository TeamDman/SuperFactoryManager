use super::context_file::ContextFile;
use super::missing_artifact::MissingArtifact;
use facet::Facet;
use std::collections::BTreeMap;
#[derive(Clone, Debug, Facet)]
pub struct ContextManifest {
    pub schema: String,
    pub projection: String,
    pub platform: String,
    pub ownership_identity: String,
    pub source_lock_sha256: String,
    pub toolchain_lock_sha256: Option<String>,
    pub base_image: Option<String>,
    pub context_identity: Option<String>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub test_maven_metadata_identity: Option<String>,
    pub files: BTreeMap<String, ContextFile>,
    pub total_bytes: u64,
    pub missing: Vec<MissingArtifact>,
    pub context_prepared: bool,
    pub image_built: bool,
    pub image_identity: Option<super::image_identity::ImageIdentity>,
    pub gameplay_validated: bool,
    #[facet(default)]
    pub dependency_layer: Option<super::dependency_layer::DependencyLayer>,
}
