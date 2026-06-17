use facet::Facet;

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(super) enum ArtifactAuditSeverity {
    Error,
    Warning,
}
