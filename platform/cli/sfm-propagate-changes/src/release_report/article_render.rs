//! Controlled editorial layout composed from the validated evidence producers.
use super::article::ReleaseReportArticle;
use super::document::ReleaseReportDocument;
use super::render::escape;
use super::render::fragment;
use super::rendered_image::RenderedReportImage;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeSet;

fn panel(id: &str, title: &str, note: &str, body: &str) -> Result<String> {
    fragment(
        include_str!("templates/article_panel.liquid"),
        &[
            ("id", id),
            ("title", &escape(title)),
            ("note", &escape(note)),
            ("body", body),
        ],
    )
}

/// This profile is the reviewed 4.35 article, not a generic acceptance inference.
pub(super) fn validate(article: &ReleaseReportArticle, doc: &ReleaseReportDocument) -> Result<()> {
    validate_sources(article, doc)?;
    validate_results(doc)?;
    validate_images(article, doc)?;
    if article.profile.ends_with("@4") {
        super::article_release_validation::validate(doc)?;
    }
    Ok(())
}

fn validate_sources(article: &ReleaseReportArticle, doc: &ReleaseReportDocument) -> Result<()> {
    ensure!(
        matches!(
            article.profile.as_str(),
            "sfm-4.35-energy-candidate@1"
                | "sfm-4.35-energy-candidate@2"
                | "sfm-4.35-energy-candidate@3"
                | "sfm-4.35-energy-candidate@4"
        ),
        "unsupported editorial article profile"
    );
    ensure!(
        article.program_diff_id == "energy-asset-ordered-dropper-first-sfml"
            && article.branch_diff_id == "issue610-output-statement",
        "article requires its reviewed source evidence IDs"
    );
    let program = doc
        .diffs
        .iter()
        .find(|d| d.id == article.program_diff_id)
        .ok_or_else(|| eyre::eyre!("article program source missing"))?;
    ensure!(
        program.after.trim()
            == "EVERY TICK DO\nINPUT fe:: FROM PowerSource TOP SIDE\nOUTPUT fe:: TO Dropper, Healthy\nEND",
        "article program does not match reviewed reproduction"
    );
    let branch = doc
        .diffs
        .iter()
        .find(|d| d.id == article.branch_diff_id)
        .ok_or_else(|| eyre::eyre!("article branch source missing"))?;
    for (source, retired) in [(&branch.before, true), (&branch.after, false)] {
        let part = source
            .split("if (resourceType.isEmpty(extracted))")
            .nth(1)
            .and_then(|s| s.split("// insert item for real").next())
            .ok_or_else(|| eyre::eyre!("article extraction branch missing"))?;
        ensure!(
            part.contains("source.setDone();") == retired && part.contains("return;"),
            "article source retirement branch differs from reviewed fix"
        );
    }
    Ok(())
}

fn validate_results(doc: &ReleaseReportDocument) -> Result<()> {
    let expected = [
        "jar-1-19-2",
        "jar-1-19-4",
        "jar-1-20",
        "jar-1-20-1",
        "jar-1-20-2",
        "jar-1-20-3",
        "jar-1-20-4",
        "jar-1-21-0",
        "jar-1-21-1",
        "jar-26-1-2",
    ];
    ensure!(
        doc.jars.iter().map(|j| j.id.as_str()).eq(expected),
        "article requires the reviewed ten packaging targets"
    );
    for (test, red) in [
        ("single_label", "expected-failure"),
        ("dropper_first", "expected-failure"),
        ("healthy_first", "passed"),
        ("full_dropper", "passed"),
        ("quantity_1000", "expected-failure"),
        ("retain_1000", "passed"),
        ("each_side", "expected-failure"),
        ("resource_progress", "expected-failure"),
    ] {
        let prefix = if test == "resource_progress" {
            "issue_610_resource_progress".to_owned()
        } else {
            format!("issue_610_jdt_progress_{test}")
        };
        for (suffix, status) in [("red", red), ("green", "passed")] {
            ensure!(
                doc.cases
                    .iter()
                    .any(|c| c.id == format!("{prefix}-{suffix}") && c.status == status),
                "article regression evidence missing or status changed: {prefix}-{suffix}"
            );
        }
    }
    for id in ["issue610-gui-red", "issue610-gui-green"] {
        ensure!(
            doc.cases.iter().any(|c| c.id == id && c.status == "passed"),
            "article final GUI evidence must pass: {id}"
        );
    }
    Ok(())
}

fn validate_images(article: &ReleaseReportArticle, doc: &ReleaseReportDocument) -> Result<()> {
    ensure!(
        article.primary_images.len() == 2
            && article.supporting_images.len() == if has_initial_capture(article) { 7 } else { 6 }
            && article.historical_images.len()
                == if article.profile.ends_with("@1") {
                    8
                } else {
                    0
                }
            && article.linux_images.len()
                == if article.profile.ends_with("@1") {
                    8
                } else {
                    0
                },
        "article capture groups must retain all selected release images (legacy profile may include Linux)"
    );
    for (figure, (phase, category, version, value, description, tooltip)) in
        article.primary_images.iter().zip([
            (
                "red",
                "baseline",
                "4.34 baseline",
                "0 FE",
                "The later consumer stays empty.",
                "Energy: 0/100.0k FE",
            ),
            (
                "green",
                "fixed",
                "4.35 candidate",
                "100,000 FE",
                "The later consumer receives power.",
                "Energy: 100.0k/100.0k FE",
            ),
        ])
    {
        ensure!(
            figure.image_id == format!("issue610-{phase}-healthy-after-final")
                && figure.category == category
                && figure.version == version
                && figure.value == value
                && figure.description == description
                && figure.tooltip == tooltip,
            "article primary capture labels differ from reviewed pair"
        );
    }
    ensure!(
        article
            .supporting_images
            .iter()
            .all(|id| id.starts_with("issue610-")
                && id.ends_with("-final")
                && !id.contains("healthy-after")),
        "supporting gallery must contain final cube/dropper captures"
    );
    ensure!(
        article
            .historical_images
            .iter()
            .all(|id| id.starts_with("issue610-") && !id.ends_with("-final")),
        "historical gallery must contain prior GUI captures"
    );
    ensure!(
        article
            .linux_images
            .iter()
            .all(|id| id.starts_with("container-orbit-")),
        "Linux gallery must contain prior orbit captures"
    );
    let assigned: Vec<_> = article
        .primary_images
        .iter()
        .map(|f| &f.image_id)
        .chain(&article.supporting_images)
        .chain(&article.historical_images)
        .chain(&article.linux_images)
        .collect();
    let set: BTreeSet<_> = assigned.iter().copied().collect();
    ensure!(
        assigned.len() == set.len()
            && set.len() == doc.images.len()
            && doc.images.iter().all(|i| set.contains(&i.id)),
        "article images must be assigned exactly once, with no missing or unknown IDs"
    );
    Ok(())
}

fn initial_capture_evidence(doc: &ReleaseReportDocument) -> Result<String> {
    let record = |id: &str| {
        doc.sections
            .iter()
            .find(|section| section.id == id)
            .map(|section| section.body.as_str())
            .ok_or_else(|| eyre::eyre!("initial capture record missing: {id}"))
    };
    let body = format!(
        "<pre>{}</pre><details id=\"initial-gap-puppet-source\"><summary>Exact initial-gap capture puppet snapshot</summary><pre>{}</pre></details>",
        escape(record("initial-25-fe-gap-capture")?),
        escape(record("initial-gap-puppet-source")?)
    );
    panel(
        "initial-25-fe-gap-capture",
        "Initial 25-FE capture · raw assertions and timing",
        "Real drop operation and synchronized client menu assert 99,975 / 100,000 FE; capture precedes every SFM transfer.",
        &body,
    )
}

pub(super) fn render(
    article: &ReleaseReportArticle,
    doc: &ReleaseReportDocument,
    values: &[(&str, &str)],
    explorer: &str,
    _jar_records: &str,
    images: &[RenderedReportImage],
) -> Result<String> {
    validate(article, doc)?;
    let lookup = |id: &str| {
        images
            .iter()
            .find(|i| i.id == id)
            .ok_or_else(|| eyre::eyre!("validated article image missing: {id}"))
    };
    let group = |ids: &[String]| -> Result<String> {
        ids.iter()
            .map(|id| Ok(lookup(id)?.html.as_str()))
            .collect::<Result<Vec<_>>>()
            .map(|parts| parts.concat())
    };
    let baseline_pair = primary_pair(article, doc, images, "red")?;
    let fixed_pair = primary_pair(article, doc, images, "green")?;
    let appendix_images: Vec<_> = article
        .supporting_images
        .iter()
        .filter(|id| !id.ends_with("source-after-hover-final") && !id.contains("dropper-initial"))
        .cloned()
        .collect();
    let mut archive_values = values.to_vec();
    let appendix_records = super::article_appendix::render(doc)?;
    let artifact_table = artifact_summary(doc);
    let diffs = values
        .iter()
        .find(|(key, _)| *key == "diffs")
        .map_or("", |(_, v)| *v);
    let correlations = super::correlation_render::render(doc)?;
    let (changes, supporting_sources) =
        super::source_explorer::render(doc, diffs, explorer, &correlations);
    archive_values.push(("appendix_records", &appendix_records));
    archive_values.push(("artifact_table", &artifact_table));
    archive_values.push(("supporting_sources", &supporting_sources));
    archive_values.push(("correlations", &correlations));
    let archive = fragment(
        include_str!("templates/article_archive.liquid"),
        &archive_values,
    )?;
    let notice = "";
    let appendix = [
        if has_initial_capture(article) { initial_capture_evidence(doc)? } else { String::new() },
        panel("supporting-captures", "Supporting views · source cube and idle first dropper", "These four views show the full source before the first transfer and the nearly full first dropper. The after-pass source and later-consumer views appear in the main story.", &group(&appendix_images)?)?,
        panel("preserved-review-archive", "Verification · sources, tests and artifact identities", "Inspect selected sources and comparisons; download complete machine-readable evidence for independent verification.", &format!("{notice}{archive}"))?,


    ].concat();
    let program = &doc
        .diffs
        .iter()
        .find(|d| d.id == article.program_diff_id)
        .expect("validated source")
        .after;
    let mut versions = String::new();
    for jar in &doc.jars {
        versions.push_str("<span>");
        versions.push_str(&escape(
            &jar.id.trim_start_matches("jar-").replace('-', "."),
        ));
        versions.push_str("</span>");
    }
    let body = fragment(
        article_body(article),
        &[
            ("program", &super::sfml::render(program.trim())),
            ("image_count", &doc.images.len().to_string()),
            (
                "before_branch",
                "if (resourceType.isEmpty(extracted)) {\n<span class=\"removed-line\">    source.setDone();</span>    return;\n}",
            ),
            (
                "after_branch",
                "if (resourceType.isEmpty(extracted)) {\n<span class=\"fixed-line\">    return;</span>\n}",
            ),
            ("baseline_pair", &baseline_pair),
            ("fixed_pair", &fixed_pair),
            ("versions", &versions),
            ("appendix", &appendix),
            ("changes", &changes),
        ],
    )?;
    let (style, script) = article_assets();
    let sfml_originals = values
        .iter()
        .find(|(key, _)| *key == "sfml_originals")
        .map_or("", |(_, value)| *value);
    let html = fragment(
        include_str!("templates/article_page.liquid"),
        &[
            ("body", &body),
            ("style", &style),
            ("script", &script),
            ("sfml_originals", sfml_originals),
        ],
    )?;
    ensure!(
        html.len() <= 32 * 1024 * 1024,
        "editorial HTML exceeds 32 MiB budget: {} bytes",
        html.len()
    );
    Ok(html)
}

fn article_assets() -> (String, String) {
    let style = format!(
        "{}\n{}\n{}\n{}\n{}",
        include_str!("templates/article_evidence_style.css"),
        include_str!("templates/article_style.css"),
        include_str!("templates/sfml.css"),
        include_str!("templates/article_layout.css"),
        include_str!("templates/review.css")
    );
    let script = format!(
        "{}\n{}\n{}\n{}",
        include_str!("templates/monaco.js"),
        include_str!("templates/class_tree.js"),
        include_str!("templates/article.js"),
        include_str!("templates/review.js")
    );
    (style, script)
}

fn artifact_summary(doc: &ReleaseReportDocument) -> String {
    if doc
        .article
        .as_ref()
        .is_some_and(|article| article.profile.ends_with("@4"))
    {
        return format!(
            "<p>{} paired archive records bind class evidence to the published 4.34 baseline and rebuilt 4.35 candidate. Source panels retain their own projection or pinned-tree input identities; baseline source rendering alone does not establish the historical compiler inputs. Exact records and receipts are available in the evidence ZIP.</p>",
            doc.jars.len()
        );
    }

    format!(
        "<p>{} paired archive records bind the source/class evidence to the published4.34 baseline and native4.35 candidate. Download the evidence ZIP for exact archive paths, hashes and verification details; current source rendering does not establish inclusion in those frozen JARs.</p>",
        doc.jars.len()
    )
}

fn primary_pair(
    article: &ReleaseReportArticle,
    doc: &ReleaseReportDocument,
    images: &[RenderedReportImage],
    phase: &str,
) -> Result<String> {
    let source_id = format!("issue610-{phase}-source-after-hover-final");
    let source = doc
        .images
        .iter()
        .find(|image| image.id == source_id)
        .ok_or_else(|| eyre::eyre!("story source capture missing: {source_id}"))?;
    let rendered = images
        .iter()
        .find(|image| image.id == source_id)
        .ok_or_else(|| eyre::eyre!("story source pixels missing: {source_id}"))?;
    let (version, value, description) = if phase == "red" {
        (
            "4.34 baseline",
            "Cube still full",
            "After the controlled second pass, the source still shows 102.4 MFE / 102.4 MFE. The recorded source state remains 256,000,000 J; the later consumer remains empty.",
        )
    } else {
        (
            "4.35 candidate",
            "Energy leaves the cube",
            "After the controlled second pass, the source shows 102.3 MFE / 102.4 MFE. Its recorded state is 255,750,000 J; the later consumer receives 100,000 FE.",
        )
    };
    let mut pair = fragment(
        include_str!("templates/article_story_figure.liquid"),
        &[
            ("id", &source_id),
            ("version", version),
            ("value", value),
            ("description", description),
            ("alt", &escape(&source.alt)),
            ("data", &rendered.data),
        ],
    )?;
    if phase == "red" && has_initial_capture(article) {
        let initial = images
            .iter()
            .find(|image| image.id == "issue610-red-dropper-initial-final")
            .ok_or_else(|| eyre::eyre!("initial gap capture missing"))?;
        let metadata = doc
            .images
            .iter()
            .find(|image| image.id == initial.id)
            .ok_or_else(|| eyre::eyre!("initial gap metadata missing"))?;
        pair.push_str(&fragment(
            include_str!("templates/article_initial_figure.liquid"),
            &[
                ("id", &initial.id),
                ("data", &initial.data),
                ("alt", &escape(&metadata.alt)),
            ],
        )?);
    }
    for f in article
        .primary_images
        .iter()
        .filter(|figure| figure.image_id.starts_with(&format!("issue610-{phase}-")))
    {
        pair.push_str(&fragment(
            include_str!("templates/article_figure.liquid"),
            &[
                ("id", &f.image_id),
                ("category", &f.category),
                ("version", &escape(&f.version)),
                ("value", &escape(&f.value)),
                ("description", &escape(&f.description)),
                ("tooltip", &escape(&f.tooltip)),
                (
                    "alt",
                    &escape(
                        &doc.images
                            .iter()
                            .find(|i| i.id == f.image_id)
                            .expect("validated image")
                            .alt,
                    ),
                ),
                (
                    "data",
                    &images
                        .iter()
                        .find(|i| i.id == f.image_id)
                        .expect("validated image")
                        .data,
                ),
            ],
        )?);
    }
    Ok(pair)
}

fn has_initial_capture(article: &ReleaseReportArticle) -> bool {
    matches!(
        article.profile.as_str(),
        "sfm-4.35-energy-candidate@3" | "sfm-4.35-energy-candidate@4"
    )
}

fn article_body(article: &ReleaseReportArticle) -> &'static str {
    if article.profile.ends_with("@4") {
        include_str!("templates/article_rebuilt_body.liquid")
    } else {
        include_str!("templates/article_body.liquid")
    }
}
