//! Lossless bounded HTML compression using the existing ZIP deflate dependency.
use base64::Engine as _;
use eyre::Result;
use eyre::ensure;
use std::io::Cursor;
use std::io::Write as _;

pub(super) fn render(html: &str) -> Result<String> {
    ensure!(
        html.len() <= 32 * 1024 * 1024,
        "class explorer exceeds expansion budget"
    );
    let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
    writer.start_file(
        "explorer.html",
        zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated)
            .compression_level(Some(9)),
    )?;
    writer.write_all(html.as_bytes())?;
    let bytes = writer.finish()?.into_inner();
    let mut archive = zip::ZipArchive::new(Cursor::new(&bytes))?;
    let entry = archive.by_index(0)?;
    let start = usize::try_from(entry.data_start())?;
    let end = start
        .checked_add(usize::try_from(entry.compressed_size())?)
        .ok_or_else(|| eyre::eyre!("compressed explorer range overflow"))?;
    let compressed = bytes
        .get(start..end)
        .ok_or_else(|| eyre::eyre!("invalid compressed explorer range"))?;
    let data = base64::engine::general_purpose::STANDARD.encode(compressed);
    let digest = crate::source_projection::provenance::sha256(html.as_bytes());
    Ok(format!(
        "<template id=\"class-payload\" data-size=\"{}\" data-sha256=\"{digest}\">{data}</template>",
        html.len()
    ))
}

#[cfg(test)]
mod tests {
    #[test]
    fn compressed_payload_is_inert_bounded_and_hash_bound() {
        let html = "<section>λ</section>".repeat(1000);
        let rendered = super::render(&html).unwrap();
        assert!(rendered.len() < html.len());
        assert!(!rendered.contains("<section>"));
        assert!(
            rendered.contains(&crate::source_projection::provenance::sha256(
                html.as_bytes()
            ))
        );
        assert!(super::render(&"x".repeat(32 * 1024 * 1024 + 1)).is_err());
    }
}
