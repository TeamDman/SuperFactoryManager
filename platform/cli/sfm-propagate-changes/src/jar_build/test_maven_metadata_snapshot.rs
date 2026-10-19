use std::collections::BTreeMap;
#[derive(Debug)]
pub(crate) struct TestMavenMetadataSnapshot {
    pub(crate) identity: String,
    pub(crate) catalog: Option<Vec<u8>>,
    pub(crate) files: BTreeMap<String, Vec<u8>>,
    pub(crate) missing: BTreeMap<String, String>,
}
