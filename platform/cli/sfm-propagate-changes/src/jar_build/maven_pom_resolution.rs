use super::MavenPomModel;
use std::collections::BTreeMap;
#[derive(Default)]
pub(super) struct MavenPomResolution {
    pub stack: Vec<String>,
    pub cache: BTreeMap<String, MavenPomModel>,
    pub visits: usize,
    pub bytes: usize,
}
