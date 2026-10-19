//! Hash-bound readable class explorer, using the existing Histogram text diff.
use super::class_evidence::ClassEvidence;
use super::jar_pair::ReleaseReportJarPair;
use super::render::asset_path;
use super::render::escape;
use super::render::read_bounded;
use crate::cancellation::CancellationToken;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::Path;

fn counted_patch(id: &str, before: &str, after: &str) -> Result<(String, usize, usize)> {
    let (patch, _) = super::static_patch::render(id, before, after)?;
    let added = patch.matches("class=\"patch-added\"").count();
    let removed = patch.matches("class=\"patch-removed\"").count();
    Ok((patch, added, removed))
}

/// Render prepared evidence only after binding it to both actual JAR hashes.
pub(super) fn render(
    pairs: &[ReleaseReportJarPair],
    root: &Path,
    cancel: &CancellationToken,
) -> Result<String> {
    let mut trees = String::new();
    let mut views = String::new();
    let mut options = String::new();
    let mut summary = String::new();
    let mut representative = String::new();
    let mut source_texts = BTreeMap::new();
    let mut archive_views = BTreeMap::new();
    let mut pooled: BTreeMap<String, (String, usize, usize, usize, usize)> = BTreeMap::new();
    for (index, pair) in pairs.iter().enumerate() {
        cancel.bail_if_cancelled()?;
        let data = evidence(pair, root)?;
        let projection = escape(&pair.id.trim_start_matches("jar-").replace('-', "."));
        write!(options, "<option value=\"{index}\">{projection}</option>")?;
        let (packages, states) =
            class_rows(&data, cancel, &mut pooled, &mut views, &mut source_texts)?;
        let counts = states
            .iter()
            .map(|(state, count)| format!("{count} {state}"))
            .collect::<Vec<_>>()
            .join(" · ");
        write!(
            summary,
            "<tr><td>{projection}</td><td>{}</td><td>{}</td></tr>",
            data.classes.len(),
            escape(&counts)
        )?;
        if (representative.is_empty() || pair.id == "jar-1-21-1")
            && let Some(row) = data
                .classes
                .iter()
                .find(|row| row.status == "representation")
        {
            representative = representative_view(row, &projection)?;
        }

        write!(
            trees,
            "<section class=\"class-projection\" data-projection=\"{index}\"><h3>{projection} · 4.34 → 4.35</h3><p>{}</p><details><summary>Disassembler and normalization provenance</summary><pre>{}</pre></details><ul class=\"package-tree\">",
            escape(
                &states
                    .iter()
                    .map(|(s, n)| format!("{n} {s}"))
                    .collect::<Vec<_>>()
                    .join(" · ")
            ),
            escape(&data.tool)
        )?;
        for (name, rows) in packages {
            let main: usize = rows.iter().map(|r| r.1).sum();
            let debug: usize = rows.iter().map(|r| r.2).sum();
            write!(
                trees,
                "<li class=\"class-package\"><details open><summary>{} · {} classes · {main} representation lines · {debug} debug lines</summary><ul>",
                escape(&name.replace('/', " / ")),
                rows.len()
            )?;
            for (leaf, _, _, _) in rows {
                trees.push_str(&leaf);
            }
            trees.push_str("</ul></details></li>");
        }
        trees.push_str(&super::archive_tree::render(
            pair,
            index,
            &data,
            &mut views,
            &mut archive_views,
        )?);
        trees.push_str("</ul></section>");
    }
    if options.is_empty() {
        return Ok(String::new());
    }
    append_source_texts(&mut views, source_texts)?;
    let payload = super::compressed_html::render(&format!(
        "<div class=\"explorer-workbench\"><aside class=\"explorer-sidebar\"><div class=\"tree-controls\"><label>Minecraft projection <select id=\"class-projection-select\">{options}</select></label><label>Search archive path <input id=\"class-search\" type=\"search\"></label><label><input id=\"class-changed-only\" type=\"checkbox\" checked> Changed entries only</label><label>Classification <select id=\"class-classification\"><option value=\"all\">All classifications</option><option value=\"representation\">Instruction/signature changed</option><option value=\"line-map-view\">Printed line-map changes only</option><option value=\"debug-view\">Other printed debug-only changes</option><option value=\"unrepresented-bytes\">Bytes outside printed views</option><option value=\"zip-metadata\">ZIP metadata only</option><option value=\"unchanged\">Unchanged class bytes</option><option value=\"changed\">Resource payload changed</option><option value=\"moved\">Moved entries</option><option value=\"added\">Added entries</option><option value=\"deleted\">Deleted entries</option></select></label><button id=\"class-apply\" type=\"button\">Apply class filters</button></div><p id=\"class-selection-status\" role=\"status\">Choose an archive entry to inspect.</p>{trees}</aside><div class=\"explorer-preview\"><div id=\"class-view-pool\">{views}</div></div></div>"
    ))?;
    Ok(format!(
        "<section id=\"class-explorer\"><details class=\"inspector-method\"><summary>Compiler metadata, counts and comparison method</summary><h2>How the archive differences are classified</h2><p>The transfer fix is visible in OutputStatement. Recipe relocations preserve payloads. Hundreds of raw class changes affect debug tables while the printed instruction/signature view stays equal; this is narrower than proof that all class differences are debug-only.</p><p>Heat measures inserted + deleted instruction/signature representation lines: 0 (quiet), 1–19 (low), 20–99 (medium), 100+ (high). Shifted offsets and compiler layout can inflate counts. Debug-table counts are separate and debug changes remain available alongside their source-causal explanations when provided. Zero heat does not imply byte-identical classes or semantic equivalence; inspect optional debug details to see line/local table shifts. The existing Histogram diff supplies the counts; no digest-bit distance is used. Constant-pool slot numbers in instruction operands are normalized; symbolic comments, string literals and bootstrap indices are retained. Equal printed views are not semantic equivalence.</p><p id=\"class-expansion-status\" role=\"status\">The complete class explorer requires JavaScript and a browser supporting DecompressionStream. Its evidence expands locally without a network download. Without JavaScript, the projection summary, representative instruction diff, authored source diffs, all captures and full JAR provenance remain readable.</p><table><thead><tr><th>Minecraft</th><th>Classes</th><th>All-class printed-view classifications</th></tr></thead><tbody>{summary}</tbody></table>{representative}</details><div id=\"class-interactive\"></div>{payload}</section>"
    ))
}

fn evidence(pair: &ReleaseReportJarPair, root: &Path) -> Result<ClassEvidence> {
    let data = if let Some(relative) = &pair.class_evidence_path {
        let bytes = read_bounded(&asset_path(root, relative)?, 32 * 1024 * 1024)?;
        ensure!(
            Some(sha256(&bytes)) == pair.class_evidence_sha256,
            "class evidence hash mismatch"
        );
        facet_json::from_slice::<ClassEvidence>(&bytes)?
    } else {
        ClassEvidence {schema:"sfm:jar-class-evidence@1".into(),projection:pair.id.clone(),baseline_sha256:sha256(&read_bounded(Path::new(&pair.baseline_path),128*1024*1024)?),candidate_sha256:sha256(&read_bounded(Path::new(&pair.candidate_path),128*1024*1024)?),tool:"No prepared disassembly. Exact archive members remain available; no structural/behavioral classification is inferred from bytes.".into(),classes:vec![]}
    };
    ensure!(
        data.schema == "sfm:jar-class-evidence@1" && data.projection == pair.id,
        "class evidence identity mismatch"
    );
    ensure!(
        data.classes.len() <= 20000,
        "class evidence exceeds entry budget"
    );
    for (path, expected) in [
        (&pair.baseline_path, &data.baseline_sha256),
        (&pair.candidate_path, &data.candidate_sha256),
    ] {
        ensure!(
            &sha256(&read_bounded(Path::new(path), 128 * 1024 * 1024)?) == expected,
            "class evidence JAR mismatch"
        );
    }
    Ok(data)
}

fn representative_view(row: &super::class_entry::ClassEntry, projection: &str) -> Result<String> {
    let (patch, _, _) = counted_patch("class-static-representative", &row.before, &row.after)?;
    Ok(format!(
        "<details id=\"class-static-evidence\"><summary>Representative instruction/signature diff: {projection} · {}</summary><p>This static example remains readable without JavaScript. Every projection and complete class/debug view is retained in the compressed explorer.</p>{patch}</details>",
        escape(&row.path)
    ))
}

fn class_view(
    id: &str,
    row: &super::class_entry::ClassEntry,
    patch: &str,
    debug: &str,
    methods: &str,
    counts: (usize, usize, usize, usize),
    sources: &[String; 4],
) -> String {
    let (add, remove, da, dr) = counts;
    let debug_selected = row.before == row.after && row.debug_before != row.debug_after;
    let (before, after) = if debug_selected {
        (&sources[2], &sources[3])
    } else {
        (&sources[0], &sources[1])
    };
    let selected = if debug_selected {
        "debug"
    } else {
        "instruction"
    };
    format!(
        "<section class=\"class-view code-diff\" id=\"{id}\" data-language=\"plaintext\"><h3>Readable class representation</h3><p>Instruction/signature: +{add} / −{remove}; debug: +{da} / −{dr}. Printed views do not exhaust raw class attributes or establish behavioral equivalence.</p><label>Representation <select class=\"class-representation\"><option value=\"instruction\"{}>Instruction/signature</option><option value=\"debug\"{}>Debug tables</option></select></label><template data-class-before=\"instruction\" data-source-id=\"{}\"></template><template data-class-after=\"instruction\" data-source-id=\"{}\"></template><template data-class-before=\"debug\" data-source-id=\"{}\"></template><template data-class-after=\"debug\" data-source-id=\"{}\"></template><div class=\"viewer-tabs\" role=\"tablist\" aria-label=\"Diff viewer\"><button type=\"button\" role=\"tab\" data-viewer=\"monaco\">Monaco</button><button type=\"button\" role=\"tab\" data-viewer=\"html\">HTML</button></div><p class=\"editor-status\" role=\"status\"></p><details><summary>Instruction/signature patch</summary>{patch}</details><details><summary>Printed method signatures (candidate)</summary><pre>{methods}</pre></details><details><summary>Debug table diff</summary>{debug}</details><details><summary>Complete selected representation before and after</summary><div class=\"static-diff\"><pre class=\"before\"><code data-source-original=\"{}\"></code></pre><pre class=\"after\"><code data-source-original=\"{}\"></code></pre></div></details><div class=\"editor\" data-representation=\"{selected}\"></div></section>",
        if debug_selected { "" } else { " selected" },
        if debug_selected { " selected" } else { "" },
        sources[0],
        sources[1],
        sources[2],
        sources[3],
        escape(before),
        escape(after)
    )
}

pub(super) fn class_partition(row: &super::class_entry::ClassEntry) -> &str {
    if row.status != "debug-view" {
        return &row.status;
    }
    let without_lines = |text: &str| {
        text.lines()
            .filter(|line| {
                let Some((number, pc)) = line
                    .trim()
                    .strip_prefix("line ")
                    .and_then(|s| s.split_once(':'))
                else {
                    return true;
                };
                number.parse::<usize>().is_err() || pc.trim().parse::<usize>().is_err()
            })
            .collect::<Vec<_>>()
            .join("\n")
    };
    if row.debug_before != row.debug_after
        && without_lines(&row.debug_before) == without_lines(&row.debug_after)
    {
        "line-map-view"
    } else {
        "debug-view"
    }
}

fn append_source_texts(views: &mut String, sources: BTreeMap<String, String>) -> Result<()> {
    for (id, text) in sources {
        write!(views, "<template id=\"{id}\">{}</template>", escape(&text))?;
    }
    Ok(())
}
fn class_leaf(
    row: &super::class_entry::ClassEntry,
    id: &str,
    counts: (usize, usize, usize, usize),
) -> String {
    let (add, remove, da, dr) = counts;
    let heat = if add + remove == 0 {
        0
    } else if add + remove < 20 {
        1
    } else if add + remove < 100 {
        2
    } else {
        3
    };
    let label = match row.status.as_str() {
        "representation" => "instruction/signature view changed",
        "debug-view" => {
            "printed debug metadata only; instruction/signature view equal (raw bytes differ)"
        }
        "zip-metadata" => "ZIP metadata only; class bytes identical",
        "unrepresented-bytes" => "class bytes changed outside printed views",
        other => other,
    };
    let (_, file) = row.path.rsplit_once('/').unwrap_or(("(root)", &row.path));
    let leaf = format!(
        "<li class=\"class-leaf heat-{heat}\" data-path=\"{}\" data-status=\"{}\" data-classification=\"{}\" data-before-sha256=\"{}\" data-after-sha256=\"{}\"><a class=\"class-link\" href=\"#{id}\">{}</a><span class=\"class-badge\">{}</span><span>+{add} / −{remove} · debug +{da} / −{dr}</span><details><summary>Class hashes</summary><code>{} → {}</code></details></li>",
        escape(&row.path),
        escape(&row.status),
        escape(class_partition(row)),
        escape(&row.before_sha256),
        escape(&row.after_sha256),
        escape(file),
        escape(label),
        escape(&row.before_sha256),
        escape(&row.after_sha256)
    );
    leaf
}

type Pool = BTreeMap<String, (String, usize, usize, usize, usize)>;
type Packages = BTreeMap<String, Vec<(String, usize, usize, usize)>>;
fn class_rows(
    data: &ClassEvidence,
    cancel: &CancellationToken,
    pooled: &mut Pool,
    views: &mut String,
    source_texts: &mut BTreeMap<String, String>,
) -> Result<(Packages, BTreeMap<String, usize>)> {
    let mut packages: BTreeMap<String, Vec<(String, usize, usize, usize)>> = BTreeMap::new();
    let mut states: BTreeMap<String, usize> = BTreeMap::new();
    let mut seen = std::collections::BTreeSet::new();
    for row in &data.classes {
        cancel.bail_if_cancelled()?;
        ensure!(
            seen.insert(&row.path)
                && Path::new(&row.path)
                    .extension()
                    .is_some_and(|ext| ext.eq_ignore_ascii_case("class"))
                && !row.path.contains('\\')
                && !row.path.split('/').any(|x| x == ".." || x.is_empty()),
            "invalid/duplicate class path"
        );
        ensure!(
            [
                "added",
                "deleted",
                "unchanged",
                "zip-metadata",
                "representation",
                "debug-view",
                "unrepresented-bytes"
            ]
            .contains(&row.status.as_str()),
            "invalid class status"
        );
        *states.entry(row.status.clone()).or_default() += 1;
        let (main_a, main_b) = (&row.before, &row.after);
        let key = sha256(
            format!(
                "{main_a}\0{main_b}\0{}\0{}",
                row.debug_before, row.debug_after
            )
            .as_bytes(),
        );
        if !pooled.contains_key(&key) {
            let id = format!("class-view-{}", pooled.len() + 1);
            let (patch, add, remove) = counted_patch(&id, main_a, main_b)?;
            let (debug, da, dr) =
                counted_patch(&format!("{id}-debug"), &row.debug_before, &row.debug_after)?;
            let methods = main_b
                .lines()
                .filter(|l| {
                    l.starts_with("  ")
                        && !l.starts_with("    ")
                        && l.contains('(')
                        && l.trim_end().ends_with(';')
                })
                .map(escape)
                .collect::<Vec<_>>()
                .join("\n");
            let sources =
                [&row.before, &row.after, &row.debug_before, &row.debug_after].map(|text| {
                    let id = format!(
                        "class-text-{}",
                        sha256(text.as_bytes()).trim_start_matches("sha256:")
                    );
                    source_texts
                        .entry(id.clone())
                        .or_insert_with(|| text.clone());
                    id
                });
            views.push_str(&class_view(
                &id,
                row,
                &patch,
                &debug,
                &methods,
                (add, remove, da, dr),
                &sources,
            ));
            pooled.insert(key.clone(), (id, add, remove, da, dr));
        }
        let (id, add, remove, da, dr) = &pooled[&key];
        let (package, _) = row.path.rsplit_once('/').unwrap_or(("(root)", &row.path));
        let mut leaf = class_leaf(row, id, (*add, *remove, *da, *dr));
        let source = row
            .after
            .lines()
            .next()
            .or_else(|| row.before.lines().next())
            .and_then(|line| line.strip_prefix("Compiled from \""))
            .and_then(|line| line.strip_suffix('"'));
        if let Some(source) = source {
            let package = row.path.rsplit_once('/').map_or("", |p| p.0);
            let path = format!("{package}/{source}");
            leaf = leaf.replacen(
                "data-path=",
                &format!("data-compiled-source=\"{}\" data-path=", escape(&path)),
                1,
            );
        }
        packages
            .entry(package.to_owned())
            .or_default()
            .push((leaf, add + remove, da + dr, 1));
    }
    Ok((packages, states))
}

#[cfg(test)]
mod tests {
    #[test]
    fn prepared_evidence_hash_contract_and_jar_tampering() {
        let dir = tempfile::tempdir().unwrap();
        let a = dir.path().join("before.jar");
        let b = dir.path().join("after.jar");
        let empty_jar = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()))
            .finish()
            .unwrap()
            .into_inner();
        std::fs::write(&a, &empty_jar).unwrap();
        std::fs::write(&b, &empty_jar).unwrap();
        let data = format!(
            r#"{{"schema":"sfm:jar-class-evidence@1","projection":"jar-test","baseline_sha256":"{}","candidate_sha256":"{}","tool":"synthetic fixture","classes":[]}}"#,
            super::sha256(&empty_jar),
            super::sha256(&empty_jar)
        );
        std::fs::write(dir.path().join("evidence.json"), &data).unwrap();
        let pair = super::ReleaseReportJarPair {
            id: "jar-test".to_owned(),
            baseline_path: a.to_string_lossy().into_owned(),
            candidate_path: b.to_string_lossy().into_owned(),
            provenance: String::new(),
            class_evidence_path: Some("evidence.json".to_owned()),
            class_evidence_sha256: Some(super::sha256(data.as_bytes())),
        };
        let cancel = crate::cancellation::CancellationToken::new();
        assert!(super::render(std::slice::from_ref(&pair), dir.path(), &cancel).is_ok());
        let raw_pair = super::ReleaseReportJarPair {
            id: pair.id.clone(),
            baseline_path: pair.baseline_path.clone(),
            candidate_path: pair.candidate_path.clone(),
            provenance: String::new(),
            class_evidence_path: None,
            class_evidence_sha256: None,
        };
        assert!(
            !super::render(&[raw_pair], dir.path(), &cancel)
                .unwrap()
                .is_empty()
        );
        std::fs::write(&b, b"tampered").unwrap();
        assert!(super::render(&[pair], dir.path(), &cancel).is_err());
    }
    #[test]
    fn actual_patch_counts_added_removed_and_escaped_content() {
        let (_, a, r) = super::counted_patch("synthetic", "", "<script>\n").unwrap();
        assert_eq!((a, r), (1, 0));
        let (p, a, r) = super::counted_patch("removed", "old\n", "").unwrap();
        assert_eq!((a, r), (0, 1));
        assert!(p.contains("patch-removed"));
        let (p, a, r) = super::counted_patch("same", "x\n", "x\n").unwrap();
        assert_eq!((a, r), (0, 0));
        assert!(!p.contains("class=\"patch-added\""));
        let (p, _, _) = super::counted_patch("escape", "a\n", "</script><img>\n").unwrap();
        assert!(!p.contains("</script>"));
    }
}

#[cfg(test)]
mod model_tests {
    #[test]
    fn debug_only_views_use_captured_models_and_conservative_partition() {
        let mut row = super::super::class_entry::ClassEntry {
            path: "A.class".into(),
            status: "debug-view".into(),
            added: 0,
            removed: 0,
            before: String::new(),
            after: String::new(),
            debug_before: "LineNumberTable:\n  line 1: 0\n".into(),
            debug_after: "LineNumberTable:\n  line 2: 0\n".into(),
            before_sha256: "before".into(),
            after_sha256: "after".into(),
        };
        let html = super::class_view(
            "test",
            &row,
            "",
            "debug patch",
            "",
            (0, 0, 1, 1),
            &[
                "main-old".into(),
                "main-new".into(),
                "debug-old".into(),
                "debug-new".into(),
            ],
        );
        assert!(html.contains("value=\"debug\" selected"));
        assert!(html.contains("data-source-original=\"debug-old\""));
        assert_eq!(super::class_partition(&row), "line-map-view");
        row.debug_after.push_str("LocalVariableTable: changed\n");
        assert_eq!(super::class_partition(&row), "debug-view");
        row.status = "unrepresented-bytes".into();
        assert_eq!(super::class_partition(&row), "unrepresented-bytes");
    }
}
