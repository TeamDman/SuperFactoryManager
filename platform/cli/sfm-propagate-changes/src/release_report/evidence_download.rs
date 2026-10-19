//! Exact records in a bounded downloadable ZIP instead of a giant visible JSON block.
use super::article_release_delivery::ArticleReleaseDelivery;
use super::document::ReleaseReportDocument;
use super::render::asset_path;
use super::render::read_bounded;
use crate::source_projection::provenance::sha256;
use base64::Engine as _;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;
use std::io::Cursor;
use std::io::Write as _;
use std::path::Path;

pub(super) fn render(manifest: &[u8], doc: &ReleaseReportDocument, root: &Path) -> Result<String> {
    let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);
    writer.start_file("report-input.json", options)?;
    writer.write_all(manifest)?;
    let mut total = manifest.len();
    for pair in &doc.jars {
        let Some(relative) = &pair.class_evidence_path else {
            continue;
        };
        let bytes = read_bounded(&asset_path(root, relative)?, 32 * 1024 * 1024)?;
        ensure!(
            Some(sha256(&bytes)) == pair.class_evidence_sha256,
            "download evidence hash mismatch: {relative}"
        );
        total += bytes.len();
        ensure!(
            total <= 128 * 1024 * 1024,
            "download expanded evidence exceeds 128 MiB budget"
        );
        writer.start_file(format!("classes/{}.json", pair.id), options)?;
        writer.write_all(&bytes)?;
    }
    append_delivery_receipts(&mut writer, options, doc, &mut total)?;
    let bytes = writer.finish()?.into_inner();
    ensure!(
        bytes.len() <= 12 * 1024 * 1024,
        "download ZIP exceeds 12 MiB budget"
    );
    let data = base64::engine::general_purpose::STANDARD.encode(&bytes);
    Ok(format!(
        "<a class=\"evidence-download\" download=\"sfm-release-evidence.zip\" href=\"data:application/zip;base64,{data}\">Download complete machine-readable evidence (ZIP)</a><span class=\"small-note\"> Exact input manifest, supplied source snapshots, case/section records and all paired class evidence. PNGs displayed in this article retain their original pixels.</span>"
    ))
}

fn append_delivery_receipts(
    writer: &mut zip::ZipWriter<Cursor<Vec<u8>>>,
    options: zip::write::SimpleFileOptions,
    doc: &ReleaseReportDocument,
    total: &mut usize,
) -> Result<()> {
    if doc
        .article
        .as_ref()
        .is_none_or(|article| article.profile != "sfm-4.35-energy-candidate@4")
    {
        return Ok(());
    }
    let mut sections = doc
        .sections
        .iter()
        .filter(|section| section.id == "release435-artifact-delivery");
    let section = sections
        .next()
        .ok_or_else(|| eyre::eyre!("release delivery section missing for receipt download"))?;
    ensure!(
        sections.next().is_none(),
        "duplicate release delivery section"
    );
    let delivery: ArticleReleaseDelivery = facet_json::from_str(&section.body)?;
    ensure!(
        delivery.schema == "sfm:release435-artifact-delivery@1"
            && delivery.artifacts.len() == 10
            && delivery.artifacts.len() == doc.jars.len(),
        "receipt download requires the ten declared release artifacts"
    );
    let mut identities = BTreeSet::new();
    for (artifact, pair) in delivery.artifacts.iter().zip(&doc.jars) {
        crate::source_projection::promotion::validate_relative_path(&artifact.id)?;
        ensure!(
            artifact.id == pair.id && identities.insert(&artifact.id),
            "duplicate or mismatched receipt artifact identity"
        );
        for (kind, path, expected) in [
            (
                "package",
                &artifact.package_receipt_path,
                &artifact.package_receipt_sha256,
            ),
            (
                "resource",
                &artifact.resource_audit_path,
                &artifact.resource_audit_sha256,
            ),
            (
                "class",
                &artifact.class_audit_path,
                &artifact.class_audit_sha256,
            ),
        ] {
            let bytes = delivery_receipt(path, expected)?;
            *total = total
                .checked_add(bytes.len())
                .ok_or_else(|| eyre::eyre!("download evidence size overflow"))?;
            ensure!(
                *total <= 128 * 1024 * 1024,
                "download expanded evidence exceeds 128 MiB budget"
            );
            writer.start_file(format!("delivery/{}/{kind}.json", artifact.id), options)?;
            writer.write_all(&bytes)?;
        }
    }
    Ok(())
}

fn delivery_receipt(path: &str, expected: &str) -> Result<Vec<u8>> {
    use crate::source_projection::candidate_lock::checked_directory;
    use crate::source_projection::candidate_lock::checked_file;

    ensure!(
        expected.len() == 64
            && expected
                .bytes()
                .all(|byte| { byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte) }),
        "receipt download requires a lowercase raw SHA256"
    );
    let selected = Path::new(path);
    let parent = selected
        .parent()
        .ok_or_else(|| eyre::eyre!("receipt path needs a parent"))?;
    let name = selected
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("receipt path needs a UTF-8 filename"))?;
    let root = checked_directory(parent)?;
    let bytes = read_bounded(&checked_file(&root, name)?, 32 * 1024 * 1024)?;
    ensure!(
        sha256(&bytes) == format!("sha256:{expected}"),
        "download receipt hash mismatch: {path}"
    );
    Ok(bytes)
}

#[cfg(test)]
#[path = "evidence_download_tests.rs"]
mod tests;
