use facet::Facet;
#[derive(Debug, Facet)]
pub struct ComparisonPreparationReceipt {
    pub schema: String,
    pub output_directory: String,
    pub comparison_identity: String,
    pub logical_files: usize,
    pub changed_logical_files: usize,
}
