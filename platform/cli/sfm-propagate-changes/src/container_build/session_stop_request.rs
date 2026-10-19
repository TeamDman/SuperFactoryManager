use super::SessionTarget;
use facet::Facet;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SessionStopRequest {
    pub target: SessionTarget,
    pub output: Option<PathBuf>,
    pub remove: bool,
}
