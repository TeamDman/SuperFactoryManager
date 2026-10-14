use facet::Facet;
#[derive(Debug, Facet)]
pub struct FileReferenceQuery {
    pub schema: String,
    pub output_path: String,
    pub status: String,
    pub links: Vec<String>,
    pub snapshot_sha256: String,
    pub output_sha256: Option<String>,
    pub source_path: Option<String>,
    pub source_sha256: Option<String>,
    pub related_references:
        Option<crate::source_projection::reference_associations::ReferenceAssociations>,
}
impl FileReferenceQuery {
    /// Query only explicit captured evidence. No inference from names or comments.
    #[must_use]
    pub fn from_snapshot(snapshot: &super::snapshot::ComparisonSnapshot, path: &str) -> Self {
        let file = snapshot.files.get(path);
        let related_references = file.and_then(|f| f.related_references.clone());
        let status = match (&file, &related_references) {
            (None, _) => "absent",
            (_, None) => "unavailable",
            (_, Some(a)) if a.references.is_empty() => "unassociated",
            _ => "associated",
        };
        Self {
            schema: "sfm:file-references@1".into(),
            output_path: path.into(),
            status: status.into(),
            snapshot_sha256: snapshot.identity.snapshot_sha256.clone(),
            output_sha256: file.map(|f| f.sha256.clone()),
            source_path: file.and_then(|f| f.source_path.clone()),
            source_sha256: file.and_then(|f| f.source_sha256.clone()),
            links: related_references.as_ref().map_or_else(Vec::new, |a| {
                a.references
                    .iter()
                    .map(crate::source_projection::source_reference::SourceReference::url)
                    .collect()
            }),
            related_references,
        }
    }
}
