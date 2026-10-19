use facet::Facet;
use std::collections::BTreeMap;
#[derive(Debug, Facet)]
pub(super) struct ToolingReceipt {
    pub base_image: String,
    pub cli_source_identity: String,
    pub source_files: BTreeMap<String, String>,
    pub bytes: u64,
    pub files: BTreeMap<String, super::tooling_file::ToolingFile>,
    pub context_prepared: bool,
    pub image_built: bool,
    pub gameplay_validated: bool,
}
