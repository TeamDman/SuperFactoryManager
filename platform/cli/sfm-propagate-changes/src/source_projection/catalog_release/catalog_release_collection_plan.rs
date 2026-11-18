use facet::Facet;

/// A proposed copy, never an instruction to overwrite the configured JAR directory.
#[derive(Debug, Facet)]
pub struct CatalogReleaseCollectionPlan {
    pub(super) source: String,
    pub(super) destination_filename: String,
    pub(super) sha256: String,
    pub(super) bytes: u64,
    pub(super) performed: bool,
}
