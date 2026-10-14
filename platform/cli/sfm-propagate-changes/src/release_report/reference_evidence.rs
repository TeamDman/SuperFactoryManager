//! A declaration association bound to the exact source payload in this report.
use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportReferenceEvidence {
    pub output_sha256: String,
    pub related_references: crate::source_projection::reference_associations::ReferenceAssociations,
}
impl ReleaseReportReferenceEvidence {
    pub(super) fn validate(&self, source: &str) -> eyre::Result<()> {
        eyre::ensure!(
            self.output_sha256 == crate::source_projection::provenance::sha256(source.as_bytes()),
            "reference evidence output SHA256 does not match report source"
        );
        self.related_references.validate()
    }
}
