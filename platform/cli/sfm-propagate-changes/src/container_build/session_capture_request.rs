use super::SessionTarget;
use facet::Facet;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SessionCaptureRequest {
    pub target: SessionTarget,
    pub angle: f64,
    pub output: PathBuf,
}
