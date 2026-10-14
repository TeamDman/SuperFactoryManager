use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportJarPair {
    pub id: String,
    pub baseline_path: String,
    pub candidate_path: String,
    pub provenance: String,
    #[facet(default)]
    pub class_evidence_path: Option<String>,
    #[facet(default)]
    pub class_evidence_sha256: Option<String>,
}
