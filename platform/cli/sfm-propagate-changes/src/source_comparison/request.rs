use super::input::ComparisonInput;
use crate::source_projection::oracle_compare::ComparisonPolicy;
use facet::Facet;
#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ComparisonRequest {
    pub schema: String,
    pub left: ComparisonInput,
    pub right: ComparisonInput,
    /// Explicit byte allowances, applied from left to right.
    pub policy: ComparisonPolicy,
}
