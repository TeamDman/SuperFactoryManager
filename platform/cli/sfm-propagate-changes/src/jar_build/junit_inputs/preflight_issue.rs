use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(crate) struct PreflightIssue {
    pub scope: String,
    pub code: String,
    pub target_ids: Vec<String>,
    pub coordinate: Option<String>,
    pub path: Option<String>,
    pub expected: Option<String>,
    pub actual: Option<String>,
    pub message: String,
}
