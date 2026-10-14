//! Exact records in a bounded downloadable ZIP instead of a giant visible JSON block.
use super::document::ReleaseReportDocument;
use super::render::asset_path;
use super::render::read_bounded;
use crate::source_projection::provenance::sha256;
use base64::Engine as _;
use eyre::Result;
use eyre::ensure;
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
    let bytes = writer.finish()?.into_inner();
    ensure!(
        bytes.len() <= 12 * 1024 * 1024,
        "download ZIP exceeds 12 MiB budget"
    );
    let data = base64::engine::general_purpose::STANDARD.encode(&bytes);
    Ok(format!(
        "<a class=\"evidence-download\" download=\"sfm-release-evidence.zip\" href=\"data:application/zip;base64,{data}\">Download complete machine-readable evidence (ZIP)</a><span class=\"small-note\"> Exact input manifest, full source snapshots, case/section records and all paired class evidence. PNGs displayed in this article retain their original pixels.</span>"
    ))
}
