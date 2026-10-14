//! Small identity-bound review payloads for standalone comparison and release HTML.
use super::document::ReleaseReportDocument;
use super::review_file::ReviewFile;
use super::review_payload::ReviewPayload;
use crate::source_comparison::report::ComparisonReport;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use std::collections::BTreeMap;

fn record(
    id: String,
    scope: String,
    old_path: Option<String>,
    new_path: Option<String>,
    before_sha256: Option<String>,
    after_sha256: Option<String>,
    status: String,
) -> Result<ReviewFile> {
    let identity = sha256(
        facet_json::to_string(&(
            &scope,
            (&old_path, &new_path),
            (&before_sha256, &after_sha256),
        ))?
        .as_bytes(),
    );
    let extension = new_path
        .as_ref()
        .or(old_path.as_ref())
        .and_then(|path| std::path::Path::new(path).extension())
        .and_then(|ext| ext.to_str())
        .unwrap_or("(none)")
        .to_lowercase();
    Ok(ReviewFile {
        whitespace_basis: None,
        old_related_references: None,
        new_related_references: None,
        id,
        identity,
        scope,
        old_path,
        new_path,
        before_sha256,
        after_sha256,
        status,
        extension,
    })
}

pub(super) fn comparison(report: &ComparisonReport) -> Result<ReviewPayload> {
    let provenance = BTreeMap::from([
        (
            "left_snapshot_sha256".into(),
            report.left.identity.snapshot_sha256.clone(),
        ),
        (
            "right_snapshot_sha256".into(),
            report.right.identity.snapshot_sha256.clone(),
        ),
        (
            "request_json".into(),
            facet_json::to_string(&report.request)?,
        ),
    ]);
    let comparison_identity = report.comparison_identity.clone();
    let files = report
        .differences
        .iter()
        .enumerate()
        .map(|(index, diff)| {
            record(
                format!("comparison-{index}"),
                String::new(),
                diff.old_path.clone(),
                diff.new_path.clone(),
                diff.old_path
                    .as_ref()
                    .and_then(|path| report.left.files.get(path))
                    .map(|file| file.sha256.clone()),
                diff.new_path
                    .as_ref()
                    .and_then(|path| report.right.files.get(path))
                    .map(|file| file.sha256.clone()),
                diff.status.clone(),
            )
            .map(|mut record| {
                if diff.old_path == diff.new_path && diff.mode_equal != Some(false) {
                    record.whitespace_basis = diff.old_path.as_ref().and_then(|path| {
                        let before =
                            std::str::from_utf8(&report.left.files.get(path)?.bytes).ok()?;
                        let after =
                            std::str::from_utf8(&report.right.files.get(path)?.bytes).ok()?;
                        super::whitespace_review::basis(path, before, after)
                    });
                }
                record.old_related_references = diff
                    .old_path
                    .as_ref()
                    .and_then(|path| report.left.files.get(path))
                    .and_then(|file| file.related_references.clone());
                record.new_related_references = diff
                    .new_path
                    .as_ref()
                    .and_then(|path| report.right.files.get(path))
                    .and_then(|file| file.related_references.clone());
                record
            })
        })
        .collect::<Result<_>>()?;
    Ok(ReviewPayload {
        schema: "sfm:comparison-review-input@1".into(),
        comparison_identity,
        provenance,
        files,
    })
}

pub(super) fn document(
    document: &ReleaseReportDocument,
    manifest_sha256: &str,
) -> Result<ReviewPayload> {
    let files = document
        .diffs
        .iter()
        .map(|diff| {
            let (scope, path) = diff.title.split_once(" · ").unwrap_or(("", &diff.title));
            let (old, new) = path.split_once(" → ").unwrap_or((path, path));
            if let Some(evidence) = &diff.old_reference_evidence {
                evidence.validate(&diff.before)?;
            }
            if let Some(evidence) = &diff.new_reference_evidence {
                evidence.validate(&diff.after)?;
            }
            if let Some(paths) = &diff.source_paths {
                paths.validate()?;
            }
            let before_path = diff
                .source_paths
                .as_ref()
                .map_or_else(|| Some(old.to_owned()), |p| p.before.clone());
            let after_path = diff
                .source_paths
                .as_ref()
                .map_or_else(|| Some(new.to_owned()), |p| p.after.clone());
            eyre::ensure!(
                before_path.is_some() || diff.old_reference_evidence.is_none(),
                "absent side cannot carry reference evidence"
            );
            eyre::ensure!(
                after_path.is_some() || diff.new_reference_evidence.is_none(),
                "absent side cannot carry reference evidence"
            );
            record(
                diff.id.clone(),
                scope.into(),
                before_path.clone(),
                after_path.clone(),
                before_path.as_ref().map(|_| sha256(diff.before.as_bytes())),
                after_path.as_ref().map(|_| sha256(diff.after.as_bytes())),
                if before_path.is_none() {
                    "added"
                } else if after_path.is_none() {
                    "removed"
                } else if before_path != after_path {
                    "moved"
                } else if diff.before == diff.after {
                    "exact"
                } else {
                    "changed"
                }
                .into(),
            )
            .map(|mut record| {
                record.old_related_references = diff
                    .old_reference_evidence
                    .as_ref()
                    .map(|e| e.related_references.clone());
                record.new_related_references = diff
                    .new_reference_evidence
                    .as_ref()
                    .map(|e| e.related_references.clone());
                if before_path.is_some() && before_path == after_path {
                    record.whitespace_basis =
                        super::whitespace_review::basis(new, &diff.before, &diff.after);
                }
                record
            })
        })
        .collect::<Result<_>>()?;
    Ok(ReviewPayload {
        schema: "sfm:comparison-review-input@1".into(),
        comparison_identity: manifest_sha256.into(),
        provenance: BTreeMap::from([("manifest_sha256".into(), manifest_sha256.into())]),
        files,
    })
}

pub(super) fn embed(payload: &ReviewPayload) -> Result<String> {
    Ok(format!(
        "<template id=\"comparison-review-payload\">{}</template>",
        super::render::escape(&facet_json::to_string(payload)?)
    ))
}

#[cfg(test)]
mod tests {
    use crate::source_projection::provenance::sha256;
    #[test]
    fn article_references_are_optional_hash_bound_and_transported() {
        let mut doc: super::ReleaseReportDocument = facet_json::from_str(r#"{"schema":"sfm:release-report-input@1","title":"test","generated_utc":"test","summary":"test","limitations":[],"sections":[],"cases":[],"diffs":[{"id":"file","title":"1.21.1 · A.java","language":"java","before":"class A {}","after":"class A { int n; }"}],"images":[],"jars":[]}"#).unwrap();
        assert!(
            super::document(&doc, "manifest").unwrap().files[0]
                .new_related_references
                .is_none()
        );
        let reference = crate::source_projection::source_reference::SourceReference {
            kind: crate::source_projection::source_reference_kind::SourceReferenceKind::Issue,
            owner: "TeamDman".into(),
            repository: "SuperFactoryManager".into(),
            number: 610,
        };
        let evidence = super::super::reference_evidence::ReleaseReportReferenceEvidence {
            output_sha256: sha256(doc.diffs[0].after.as_bytes()),
            related_references:
                crate::source_projection::reference_associations::ReferenceAssociations {
                    schema: "sfm:related-references@1".into(),
                    references: vec![reference],
                    projection_key: "sfm-4.35.0/mc-1.21.1".into(),
                    context_sha256: sha256(b"context"),
                    declarations: std::collections::BTreeMap::from([(
                        "src/A.java".into(),
                        sha256(b"declaration"),
                    )]),
                    conditions: std::collections::BTreeMap::from([("features.fix".into(), true)]),
                },
        };
        doc.diffs[0].new_reference_evidence = Some(evidence);
        let roundtrip: super::ReleaseReportDocument =
            facet_json::from_str(&facet_json::to_string(&doc).unwrap()).unwrap();
        let payload = super::document(&roundtrip, "manifest").unwrap();
        assert!(payload.files[0].old_related_references.is_none());
        assert_eq!(
            payload.files[0]
                .new_related_references
                .as_ref()
                .unwrap()
                .references[0]
                .number,
            610
        );
        doc.diffs[0].after.push(' ');
        assert!(super::document(&doc, "manifest").is_err());
    }
    #[test]
    fn path_move_is_substantive_even_with_whitespace_only_body() {
        let doc: super::ReleaseReportDocument = facet_json::from_str(r#"{"schema":"sfm:release-report-input@1","title":"test","generated_utc":"test","summary":"test","limitations":[],"sections":[],"cases":[],"diffs":[{"id":"file","title":"1.21.1 · A.java → B.java","language":"java","before":"class A {}","after":"class A { }"}],"images":[],"jars":[]}"#).unwrap();
        let payload = super::document(&doc, "manifest").unwrap();
        assert_eq!(payload.files[0].status, "moved");
        assert!(payload.files[0].whitespace_basis.is_none());
    }
}
