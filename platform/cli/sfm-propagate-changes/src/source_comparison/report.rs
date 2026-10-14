use super::difference::FileDifference;
use super::request::ComparisonRequest;
use super::snapshot::ComparisonSnapshot;
use facet::Facet;
#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ComparisonReport {
    pub schema: String,
    pub comparison_identity: String,
    pub request: ComparisonRequest,
    pub left: ComparisonSnapshot,
    pub right: ComparisonSnapshot,
    pub differences: Vec<FileDifference>,
    pub limitations: Vec<String>,
}
