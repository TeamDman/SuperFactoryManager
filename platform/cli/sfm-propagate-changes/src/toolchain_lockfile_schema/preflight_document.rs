use facet::Facet;

#[derive(Clone, Debug, Facet)]
pub(crate) struct PreflightDocument {
    pub(crate) schema_version: u32,
}
