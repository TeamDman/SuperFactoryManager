use facet::Facet;
#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct ReleaseReportReceipt {
    pub schema: String,
    pub manifest_sha256: String,
    pub html_sha256: String,
    pub html_bytes: usize,
    pub embedded_images: usize,
    pub compared_jars: usize,
}
