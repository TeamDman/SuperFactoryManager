use super::document::ReleaseReportDocument;
use super::receipt::ReleaseReportReceipt;
use super::request::ReleaseReportRequest;
use crate::cancellation::CancellationToken;
use crate::source_projection::provenance::sha256;
use base64::Engine as _;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use liquid::model::Value;
use std::collections::BTreeSet;
use std::fs::OpenOptions;
use std::fs::{self};
use std::io::Read as _;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;

const MAX_MANIFEST: u64 = 32 * 1024 * 1024;
const MAX_PNG: u64 = 8 * 1024 * 1024;
const MAX_HTML: usize = 64 * 1024 * 1024;

pub(super) fn read_bounded(path: &Path, limit: u64) -> Result<Vec<u8>> {
    let mut bytes = Vec::new();
    fs::File::open(path)?
        .take(limit + 1)
        .read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= limit,
        "input exceeds {limit} byte budget: {}",
        path.display()
    );
    Ok(bytes)
}

pub(super) fn escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

pub(super) fn fragment(template: &str, values: &[(&str, &str)]) -> Result<String> {
    let globals: liquid::Object = values
        .iter()
        .map(|(name, value)| {
            (
                (*name).to_owned().into(),
                Value::scalar((*value).to_owned()),
            )
        })
        .collect();
    liquid::ParserBuilder::with_stdlib()
        .build()?
        .parse(template)?
        .render(&globals)
        .wrap_err("release report Liquid rendering failed")
}

fn unique_id(id: &str, ids: &mut BTreeSet<String>) -> Result<()> {
    ensure!(
        !id.is_empty()
            && id
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-'),
        "invalid report id: {id}"
    );
    ensure!(ids.insert(id.to_owned()), "duplicate report id: {id}");
    Ok(())
}

pub(super) fn asset_path(root: &Path, relative: &str) -> Result<std::path::PathBuf> {
    let path = Path::new(relative);
    ensure!(
        !relative.contains('\\')
            && !relative.contains(':')
            && !path.is_absolute()
            && path.components().all(|c| matches!(c, Component::Normal(_))),
        "asset must be a safe relative path: {relative}"
    );
    let canonical = dunce::canonicalize(root.join(path))?;
    ensure!(
        canonical.starts_with(dunce::canonicalize(root)?),
        "asset escapes root: {relative}"
    );
    Ok(canonical)
}

/// Render only explicit local evidence. All untrusted prose/code is HTML escaped.
///
/// # Errors
/// Rejects unknown schemas/fields, unsafe assets, bad hashes, budgets and existing outputs.
#[tracing::instrument(level = "info", skip_all)]
pub fn render(
    request: &ReleaseReportRequest,
    cancellation: &CancellationToken,
) -> Result<ReleaseReportReceipt> {
    cancellation.bail_if_cancelled()?;
    ensure!(
        !request.output.exists(),
        "report output already exists: {}",
        request.output.display()
    );
    let bytes = read_bounded(&request.manifest, MAX_MANIFEST)?;
    let manifest: ReleaseReportDocument = facet_json::from_slice(&bytes)?;
    ensure!(
        manifest.schema == "sfm:release-report-input@1",
        "unsupported release report schema"
    );
    if let Some(article) = &manifest.article {
        super::article_render::validate(article, &manifest)?;
    }
    let mut ids = BTreeSet::new();
    let sections = render_sections(&manifest, &mut ids, cancellation)?;
    let cases = render_cases(&manifest, &mut ids, cancellation)?;
    let (diffs, sfml_originals) = render_diffs(&manifest, &mut ids, cancellation)?;
    let rendered_images = render_images(&manifest, &mut ids, cancellation, &request.asset_root)?;
    let images = rendered_images
        .iter()
        .map(|image| image.html.as_str())
        .collect::<String>();
    let explorer = super::class_tree::render(&manifest.jars, &request.asset_root, cancellation)?;
    let jar_records = render_jars(&manifest, &mut ids, cancellation)?;
    let jars = format!("{explorer}{jar_records}");
    let mut limitations = String::new();
    for value in &manifest.limitations {
        limitations.push_str("<li>");
        limitations.push_str(&escape(value));
        limitations.push_str("</li>");
    }
    let title = escape(&manifest.title);
    let generated = escape(&manifest.generated_utc);
    let summary = escape(&manifest.summary);
    let manifest_hash = sha256(&bytes);
    let script = report_script();
    let style = report_style();
    let download = if manifest.article.is_some() {
        super::evidence_download::render(&bytes, &manifest, &request.asset_root)?
    } else {
        String::new()
    };
    let report_values: [(&str, &str); 14] = [
        ("title", &title),
        ("generated", &generated),
        ("summary", &summary),
        ("limitations", &limitations),
        ("sections", &sections),
        ("cases", &cases),
        ("diffs", &diffs),
        ("images", &images),
        ("jars", &jars),
        ("manifest_hash", &manifest_hash),
        ("style", &style),
        ("sfml_originals", &sfml_originals),
        ("script", &script),
        ("evidence_download", &download),
    ];
    let mut html = if let Some(article) = &manifest.article {
        super::article_render::render(
            article,
            &manifest,
            &report_values,
            &explorer,
            &jar_records,
            &rendered_images,
        )?
    } else {
        fragment(include_str!("templates/page.liquid"), &report_values)?
    };
    let review = super::review_controls::embed(&super::review_controls::document(
        &manifest,
        &manifest_hash,
    )?)?;
    html = html.replacen("<script>", &format!("{review}<script>"), 1);
    ensure!(
        html.len() <= MAX_HTML,
        "HTML exceeds {MAX_HTML} byte budget"
    );
    cancellation.bail_if_cancelled()?;
    OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&request.output)?
        .write_all(html.as_bytes())?;
    Ok(ReleaseReportReceipt {
        schema: "sfm:release-report-receipt@1".to_owned(),
        manifest_sha256: sha256(&bytes),
        html_sha256: sha256(html.as_bytes()),
        html_bytes: html.len(),
        embedded_images: manifest.images.len(),
        compared_jars: manifest.jars.len(),
    })
}

fn render_sections(
    manifest: &ReleaseReportDocument,
    ids: &mut BTreeSet<String>,
    cancellation: &CancellationToken,
) -> Result<String> {
    let mut sections = String::new();
    for item in &manifest.sections {
        cancellation.bail_if_cancelled()?;
        unique_id(&item.id, ids)?;
        sections.push_str(&fragment(
            include_str!("templates/section.liquid"),
            &[
                ("id", &item.id),
                ("title", &escape(&item.title)),
                ("body", &escape(&item.body)),
            ],
        )?);
    }
    Ok(sections)
}

fn render_cases(
    manifest: &ReleaseReportDocument,
    ids: &mut BTreeSet<String>,
    cancellation: &CancellationToken,
) -> Result<String> {
    let mut cases = String::new();
    for item in &manifest.cases {
        cancellation.bail_if_cancelled()?;
        unique_id(&item.id, ids)?;
        ensure!(
            [
                "passed",
                "failed",
                "expected-failure",
                "aborted",
                "untested"
            ]
            .contains(&item.status.as_str()),
            "invalid case status: {}",
            item.status
        );
        cases.push_str(&fragment(
            include_str!("templates/case.liquid"),
            &[
                ("id", &item.id),
                ("projection", &escape(&item.projection)),
                ("status", &item.status),
                ("observed", &escape(&item.observed)),
                ("method", &escape(&item.method)),
                ("sources", &escape(&item.source_paths.join("\n"))),
            ],
        )?);
    }
    Ok(cases)
}

pub(super) fn render_diffs(
    manifest: &ReleaseReportDocument,
    ids: &mut BTreeSet<String>,
    cancellation: &CancellationToken,
) -> Result<(String, String)> {
    let mut diffs = String::new();
    let mut originals = std::collections::BTreeMap::new();
    for item in &manifest.diffs {
        cancellation.bail_if_cancelled()?;
        unique_id(&item.id, ids)?;
        if item.evidence_only {
            continue;
        }
        let (patch, patch_ids) = super::static_patch::render(&item.id, &item.before, &item.after)?;
        for id in patch_ids {
            unique_id(&id, ids)?;
        }
        let sfml = super::sfml::applies(&item.language, &item.id);
        let mut raw_ids = Vec::new();
        if sfml {
            for source in [&item.before, &item.after] {
                let id = format!(
                    "sfml-original-{}",
                    sha256(source.as_bytes()).trim_start_matches("sha256:")
                );
                if !originals.contains_key(&id) {
                    unique_id(&id, ids)?;
                    originals.insert(id.clone(), escape(source));
                }
                raw_ids.push(id);
            }
        }
        let language = if sfml {
            "sfml".to_owned()
        } else {
            escape(&item.language)
        };
        let before = if sfml {
            super::sfml::render(&item.before)
        } else {
            escape(&item.before)
        };
        let after = if sfml {
            super::sfml::render(&item.after)
        } else {
            escape(&item.after)
        };
        let split_patch = if item.before == item.after {
            super::static_patch::equal_view(&before)
        } else {
            super::static_patch::split_view(&patch)
        };
        diffs.push_str(&fragment(
            if sfml {
                include_str!("templates/sfml_diff.liquid")
            } else {
                include_str!("templates/diff.liquid")
            },
            &[
                ("id", &item.id),
                ("title", &escape(&item.title)),
                ("language", &language),
                ("raw_before", raw_ids.first().map_or("", String::as_str)),
                ("raw_after", raw_ids.get(1).map_or("", String::as_str)),
                ("before", &before),
                ("after", &after),
                ("patch", &patch),
                ("split_patch", &split_patch),
            ],
        )?);
    }
    let mut raw = String::new();
    for (id, source) in originals {
        raw.push_str("<template id=\"");
        raw.push_str(&id);
        raw.push_str("\">");
        raw.push_str(&source);
        raw.push_str("</template>");
    }
    Ok((diffs, raw))
}

fn render_images(
    manifest: &ReleaseReportDocument,
    ids: &mut BTreeSet<String>,
    cancellation: &CancellationToken,
    asset_root: &Path,
) -> Result<Vec<super::rendered_image::RenderedReportImage>> {
    let mut images = Vec::new();
    let mut image_bytes = 0;
    for item in &manifest.images {
        cancellation.bail_if_cancelled()?;
        unique_id(&item.id, ids)?;
        ensure!(
            !item.alt.trim().is_empty() && !item.caption.trim().is_empty(),
            "image requires alt and caption: {}",
            item.id
        );
        let data = read_bounded(&asset_path(asset_root, &item.relative_path)?, MAX_PNG)?;
        ensure!(
            data.starts_with(b"\x89PNG\r\n\x1a\n"),
            "image must be PNG: {}",
            item.id
        );
        ensure!(
            sha256(&data) == item.sha256,
            "image SHA256 mismatch: {}",
            item.id
        );
        let encoded = base64::engine::general_purpose::STANDARD.encode(data);
        let html = fragment(
            include_str!("templates/image.liquid"),
            &[
                ("id", &item.id),
                ("alt", &escape(&item.alt)),
                ("caption", &escape(&item.caption)),
                ("human_caption", &escape(&super::captions::human(item))),
                ("sha256", &item.sha256),
                ("data", &encoded),
            ],
        )?;
        image_bytes += html.len();
        images.push(super::rendered_image::RenderedReportImage {
            id: item.id.clone(),
            html,
            data: encoded,
        });
        ensure!(
            image_bytes <= MAX_HTML,
            "embedded images exceed report budget"
        );
    }
    Ok(images)
}

fn render_jars(
    manifest: &ReleaseReportDocument,
    ids: &mut BTreeSet<String>,
    cancellation: &CancellationToken,
) -> Result<String> {
    let mut jars = String::new();
    for item in &manifest.jars {
        cancellation.bail_if_cancelled()?;
        unique_id(&item.id, ids)?;
        let comparison = crate::jar_build::engine::release_jar_comparison_json(
            Path::new(&item.baseline_path),
            Path::new(&item.candidate_path),
        )?;
        jars.push_str("<details class=\"jar-technical\"><summary>JAR hashes and technical comparison evidence</summary>");
        jars.push_str(&fragment(
            include_str!("templates/section.liquid"),
            &[
                ("id", &item.id),
                ("title", &escape(&format!("JAR comparison: {}", item.id))),
                (
                    "body",
                    &escape(&format!("{}\n{comparison}", item.provenance)),
                ),
            ],
        )?);
        jars.push_str("</details>");
    }
    Ok(jars)
}

fn report_script() -> String {
    format!(
        "{}\n{}\n{}",
        include_str!("templates/monaco.js"),
        include_str!("templates/class_tree.js"),
        include_str!("templates/review.js")
    )
}

fn report_style() -> String {
    format!(
        "{}\n{}\n{}",
        include_str!("templates/style.css"),
        include_str!("templates/sfml.css"),
        include_str!("templates/review.css")
    )
}
