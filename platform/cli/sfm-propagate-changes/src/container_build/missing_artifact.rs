use facet::Facet;
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct MissingArtifact {
    pub identity: String,
    pub reason: String,
    pub remediation: String,
}
