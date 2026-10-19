//! Restore only reviewed appendix records and concise supplied archive identities.
use super::document::ReleaseReportDocument;
use super::render::escape;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;
use std::fmt::Write as _;

const MAX_APPENDIX_HTML: usize = 512 * 1024;

pub(super) fn render(doc: &ReleaseReportDocument) -> Result<String> {
    let mut html = String::new();
    reviewed_sections(doc, &mut html)?;
    archive_identities(doc, &mut html)?;
    ensure!(
        html.len() <= MAX_APPENDIX_HTML,
        "article appendix records exceed 512 KiB"
    );
    Ok(html)
}

fn reviewed_sections(doc: &ReleaseReportDocument, html: &mut String) -> Result<()> {
    for id in [
        "release435-label-gun-chat",
        "release435-artifact-delivery",
        "recipe-example-provenance",
    ] {
        let mut records = doc.sections.iter().filter(|section| section.id == id);
        let Some(section) = records.next() else {
            continue;
        };
        ensure!(
            records.next().is_none(),
            "duplicate reviewed appendix section: {id}"
        );
        ensure!(
            section.title.len() <= 1024 && section.body.len() <= 64 * 1024,
            "reviewed appendix section exceeds text budget: {id}"
        );
        write!(
            html,
            "<details id=\"{id}\" class=\"appendix-record\"><summary>{}</summary><pre>{}</pre></details>",
            escape(&section.title),
            escape(&section.body)
        )?;
    }
    Ok(())
}

fn archive_identities(doc: &ReleaseReportDocument, html: &mut String) -> Result<()> {
    ensure!(
        doc.jars.len() <= 20,
        "appendix archive identity count exceeds 20"
    );
    let mut seen = BTreeSet::new();
    for pair in &doc.jars {
        ensure!(
            !pair.id.is_empty()
                && pair.id.len() <= 128
                && pair
                    .id
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_' || byte == b'-')
                && seen.insert(&pair.id),
            "invalid or duplicate appendix archive identity"
        );
        ensure!(
            pair.baseline_path.len() <= 4096
                && pair.candidate_path.len() <= 4096
                && pair
                    .class_evidence_path
                    .as_ref()
                    .is_none_or(|path| path.len() <= 4096)
                && pair
                    .class_evidence_sha256
                    .as_ref()
                    .is_none_or(|hash| hash.len() <= 128),
            "appendix archive identity exceeds text budget"
        );
        write!(
            html,
            "<details id=\"{}\" class=\"archive-identity\"><summary>Paired archive identity · {}</summary><p>Supplied manifest paths and prepared class-evidence identity. Complete raw records and verification receipts remain in the evidence ZIP.</p><dl><dt>Baseline path</dt><dd><code>{}</code></dd><dt>Candidate path</dt><dd><code>{}</code></dd><dt>Paired class evidence path</dt><dd><code>{}</code></dd><dt>Paired class evidence SHA256</dt><dd><code>{}</code></dd></dl></details>",
            escape(&pair.id),
            escape(&pair.id),
            escape(&pair.baseline_path),
            escape(&pair.candidate_path),
            escape(
                pair.class_evidence_path
                    .as_deref()
                    .unwrap_or("not supplied")
            ),
            escape(
                pair.class_evidence_sha256
                    .as_deref()
                    .unwrap_or("not supplied")
            )
        )?;
    }
    Ok(())
}

#[cfg(test)]
#[path = "article_appendix_tests.rs"]
mod tests;
