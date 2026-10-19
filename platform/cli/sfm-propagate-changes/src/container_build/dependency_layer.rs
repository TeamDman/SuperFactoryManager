use facet::Facet;
use std::collections::BTreeMap;
/// Verified portable-input authority remains in the final context receipt.
#[derive(Clone, Debug, Facet)]
pub struct DependencyLayer {
    pub context_identity: String,
    pub image: super::image_identity::ImageIdentity,
    pub files: BTreeMap<String, super::context_file::ContextFile>,
    pub total_bytes: u64,
}
