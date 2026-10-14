use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportCodeDiff {
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub old_reference_evidence: Option<super::reference_evidence::ReleaseReportReferenceEvidence>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub new_reference_evidence: Option<super::reference_evidence::ReleaseReportReferenceEvidence>,
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub source_paths: Option<super::source_paths::ReleaseReportSourcePaths>,
    /// Keep a frozen payload in the downloadable record without duplicating it in the current explorer.
    #[facet(default)]
    pub evidence_only: bool,
    pub id: String,
    pub title: String,
    pub language: String,
    pub before: String,
    pub after: String,
}
