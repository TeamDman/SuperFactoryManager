use super::SessionTarget;
use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionRunTestRequest {
    pub target: SessionTarget,
    pub game_test: String,
}
