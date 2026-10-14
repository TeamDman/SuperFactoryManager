use facet::Facet;
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[facet(deny_unknown_fields)]
pub struct ComparisonFile {
    #[facet(default, skip_serializing_if = Option::is_none)]
    pub related_references:
        Option<crate::source_projection::reference_associations::ReferenceAssociations>,
    pub sha256: String,
    /// Original bytes remain evidence even when a normalized view matches.
    pub bytes: Vec<u8>,
    /// Windows directories cannot certify Git executable bits.
    pub mode: Option<u32>,
    pub git_oid: Option<String>,
    pub source_path: Option<String>,
    pub source_sha256: Option<String>,
}
