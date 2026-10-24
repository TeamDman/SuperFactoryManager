//! Every ZIP member is reviewed by exact bytes; class representations remain separate.
use super::archive_resource_pair::ArchiveResourcePair;
use super::class_evidence::ClassEvidence;
use super::jar_pair::ReleaseReportJarPair;
use super::render::escape;
use super::render::read_bounded;
use crate::source_projection::provenance::sha256;
use base64::Engine as _;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fmt::Write as _;
use std::io::Cursor;
use std::io::Read as _;
use std::path::Path;
pub(crate) fn entries(path: &str, expected: &str) -> Result<BTreeMap<String, Vec<u8>>> {
    let bytes = read_bounded(Path::new(path), 128 * 1024 * 1024)?;
    ensure!(sha256(&bytes) == expected, "archive raw identity mismatch");
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes))?;
    ensure!(zip.len() <= 20000, "archive entry budget exceeded");
    let mut result = BTreeMap::new();
    let mut total = 0_u64;
    for i in 0..zip.len() {
        let entry = zip.by_index(i)?;
        let name = entry.name().to_owned();
        crate::source_projection::promotion::validate_relative_path(name.trim_end_matches('/'))?;
        ensure!(
            !result.contains_key(&name),
            "duplicate archive entry: {name}"
        );
        ensure!(
            entry.size() <= 16 * 1024 * 1024,
            "archive member exceeds 16 MiB: {name}"
        );
        total = total
            .checked_add(entry.size())
            .ok_or_else(|| eyre::eyre!("archive size overflow"))?;
        ensure!(
            total <= 512 * 1024 * 1024,
            "archive expanded budget exceeded"
        );
        let expected = entry.size();
        let mut bytes = Vec::new();
        entry.take(16 * 1024 * 1024 + 1).read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() as u64 == expected,
            "archive member size mismatch: {name}"
        );
        result.insert(name, bytes);
    }
    Ok(result)
}
fn side(bytes: Option<&Vec<u8>>, path: &str) -> String {
    let Some(bytes) = bytes else {
        return "<p>Entry absent.</p>".into();
    };
    let encoded = base64::engine::general_purpose::STANDARD.encode(bytes);
    let mut html = format!(
        "<p>{} bytes · <code>{}</code> · <a download=\"{}\" href=\"data:application/octet-stream;base64,{encoded}\">Download exact member</a></p>",
        bytes.len(),
        sha256(bytes),
        escape(path.rsplit('/').next().unwrap_or(path))
    );
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        let _ = write!(
            html,
            "<img alt=\"Archive image {}\" style=\"max-width:100%\" src=\"data:image/png;base64,{encoded}\">",
            escape(path)
        );
    }
    html
}
fn text(bytes: Option<&Vec<u8>>) -> String {
    bytes
        .and_then(|b| std::str::from_utf8(b).ok())
        .filter(|s| !s.contains('\0'))
        .unwrap_or("")
        .to_owned()
}
fn resource_view(
    id: &str,
    old: Option<&str>,
    new: Option<&str>,
    a: Option<&Vec<u8>>,
    b: Option<&Vec<u8>>,
) -> Result<String> {
    let before = text(a);
    let after = text(b);
    let (patch, _) = super::static_patch::render(id, &before, &after)?;
    Ok(format!(
        "<section class=\"class-view archive-view code-diff\" id=\"{id}\" data-language=\"plaintext\"><h3>Archive member</h3><p>Exact ZIP-member payload comparison. Empty text views may be binary; inspect hashes and download the raw members. No whitespace or behavioral equivalence is inferred.</p><div class=\"review-paths\"><p>Old: {}</p><p>New: {}</p></div><div class=\"code-pair\"><div>{}</div><div>{}</div></div><div class=\"viewer-tabs\"><button data-viewer=\"monaco\">Monaco</button><button data-viewer=\"html\">HTML</button></div><p class=\"editor-status\"></p><details><summary>Raw text diff</summary>{patch}</details><div class=\"static-diff\"><pre class=\"before\"><code>{}</code></pre><pre class=\"after\"><code>{}</code></pre></div><div class=\"editor\"></div></section>",
        escape(old.unwrap_or("(absent)")),
        escape(new.unwrap_or("(absent)")),
        side(a, old.unwrap_or("entry")),
        side(b, new.unwrap_or("entry")),
        escape(&before),
        escape(&after)
    ))
}
fn moves(a: &BTreeMap<String, Vec<u8>>, b: &BTreeMap<String, Vec<u8>>) -> BTreeMap<String, String> {
    let mut left: BTreeMap<String, Vec<String>> = BTreeMap::new();
    let mut right: BTreeMap<String, Vec<String>> = BTreeMap::new();
    for (entries, other, out) in [(a, b, &mut left), (b, a, &mut right)] {
        for (p, bytes) in entries {
            if !other.contains_key(p)
                && !p.ends_with('/')
                && !Path::new(p)
                    .extension()
                    .is_some_and(|ext| ext.eq_ignore_ascii_case("class"))
            {
                out.entry(sha256(bytes)).or_default().push(p.clone());
            }
        }
    }
    left.into_iter()
        .filter_map(|(hash, paths)| {
            let next = right.get(&hash)?;
            (paths.len() == 1 && next.len() == 1).then(|| (paths[0].clone(), next[0].clone()))
        })
        .collect()
}
pub(super) fn render(
    pair: &ReleaseReportJarPair,
    index: usize,
    data: &ClassEvidence,
    views: &mut String,
    pooled: &mut BTreeMap<ArchiveResourcePair, String>,
) -> Result<String> {
    let a = entries(&pair.baseline_path, &data.baseline_sha256)?;
    let b = entries(&pair.candidate_path, &data.candidate_sha256)?;
    for row in &data.classes {
        ensure!(
            a.contains_key(&row.path) || b.contains_key(&row.path),
            "prepared class absent from both archives: {}",
            row.path
        );
        for (map, hash) in [(&a, &row.before_sha256), (&b, &row.after_sha256)] {
            if let Some(bytes) = map.get(&row.path) {
                ensure!(
                    sha256(bytes).trim_start_matches("sha256:")
                        == hash.trim_start_matches("sha256:"),
                    "class member evidence mismatch: {}",
                    row.path
                );
            }
        }
    }
    let prepared: BTreeSet<_> = data.classes.iter().map(|r| r.path.as_str()).collect();
    let mut moved = moves(&a, &b);
    crate::source_comparison::namespace_moves::extend_archive(&a, &b, &mut moved);
    let targets: BTreeSet<_> = moved.values().collect();
    let paths: BTreeSet<_> = a.keys().chain(b.keys()).collect();
    let mut folders: BTreeMap<String, String> = BTreeMap::new();
    let mut count = 0;
    for path in paths {
        if prepared.contains(path.as_str()) || targets.contains(path) {
            continue;
        }
        let old = a.contains_key(path).then_some(path.as_str());
        let new = moved
            .get(path)
            .map(String::as_str)
            .or_else(|| b.contains_key(path).then_some(path.as_str()));
        let before = old.and_then(|p| a.get(p));
        let after = new.and_then(|p| b.get(p));
        let status = if moved.contains_key(path) {
            "moved"
        } else if before.is_none() {
            "added"
        } else if after.is_none() {
            "deleted"
        } else if before == after {
            "unchanged"
        } else {
            "changed"
        };
        let key = ArchiveResourcePair::new(
            old,
            new,
            before.map(Vec::as_slice),
            after.map(Vec::as_slice),
        );
        let id = if let Some(id) = pooled.get(&key) {
            id.clone()
        } else {
            let id = format!("archive-{index}-{count}");
            views.push_str(&resource_view(&id, old, new, before, after)?);
            pooled.insert(key, id.clone());
            id
        };
        count += 1;
        let (folder, file) = path.rsplit_once('/').unwrap_or(("(root)", path));
        let file = if file.is_empty() {
            "(directory entry)"
        } else {
            file
        };
        let label = new
            .filter(|p| Some(*p) != old)
            .map_or_else(|| file.to_owned(), |p| format!("{file} → {p}"));
        let leaf = format!(
            "<li class=\"class-leaf archive-leaf\" data-path=\"{}\" data-old-path=\"{}\" data-new-path=\"{}\" data-status=\"{status}\" data-classification=\"{status}\" data-before-sha256=\"{}\" data-after-sha256=\"{}\"><a class=\"class-link\" href=\"#{id}\">{}</a><span class=\"class-badge\">{status}</span></li>",
            escape(path),
            escape(old.unwrap_or("")),
            escape(new.unwrap_or("")),
            before.map_or_else(String::new, |b| sha256(b)),
            after.map_or_else(String::new, |b| sha256(b)),
            escape(&label)
        );
        folders
            .entry(folder.to_owned())
            .or_default()
            .push_str(&leaf);
    }
    let mut html = format!(
        "<li class=\"archive-summary\">All raw ZIP entries: {} before / {} after; {} prepared classes; {count} other logical entries. Unique byte-identical resource relocations and narrowly guarded advancement self-ID relocations are paired. Changed bytes remain shown; duplicate copies remain separate.</li>",
        a.len(),
        b.len(),
        data.classes.len()
    );
    for (folder, leaves) in folders {
        let _ = write!(
            html,
            "<li class=\"class-package\"><details open><summary>{}</summary><ul>{leaves}</ul></details></li>",
            escape(&folder)
        );
    }
    Ok(html)
}
#[cfg(test)]
mod tests {
    #[test]
    fn unique_moves_do_not_conflate_copies() {
        use std::collections::BTreeMap;
        let a = BTreeMap::from([("old".into(), vec![1])]);
        let b = BTreeMap::from([("new".into(), vec![1])]);
        assert_eq!(super::moves(&a, &b)["old"], "new");
        let mut copies = a;
        copies.insert("copy".into(), vec![1]);
        assert!(super::moves(&copies, &b).is_empty());
    }
}

#[cfg(test)]
mod fixture_tests {
    use std::io::Cursor;
    use std::io::Write as _;
    fn jar(entries: &[(&str, &[u8])]) -> Vec<u8> {
        let mut writer = zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (path, bytes) in entries {
            writer
                .start_file(*path, zip::write::SimpleFileOptions::default())
                .unwrap();
            writer.write_all(bytes).unwrap();
        }
        writer.finish().unwrap().into_inner()
    }
    #[test]
    fn all_members_moves_additions_removals_and_binary_evidence_are_preserved() {
        let dir = tempfile::tempdir().unwrap();
        let a = jar(&[
            ("META-INF/MANIFEST.MF", b"before"),
            ("old.json", b"{}"),
            ("deleted.txt", b"gone"),
            ("icon.png", b"binary\0"),
        ]);
        let b = jar(&[
            ("META-INF/MANIFEST.MF", b"after"),
            ("new.json", b"{}"),
            ("added.txt", b"new"),
            ("icon.png", b"binary\0"),
        ]);
        let before = dir.path().join("before.jar");
        let after = dir.path().join("after.jar");
        std::fs::write(&before, &a).unwrap();
        std::fs::write(&after, &b).unwrap();
        let pair = super::ReleaseReportJarPair {
            id: "fixture".into(),
            baseline_path: before.to_string_lossy().into(),
            candidate_path: after.to_string_lossy().into(),
            provenance: String::new(),
            class_evidence_path: None,
            class_evidence_sha256: None,
        };
        let data = super::ClassEvidence {
            schema: "sfm:jar-class-evidence@1".into(),
            projection: "fixture".into(),
            baseline_sha256: super::sha256(&a),
            candidate_sha256: super::sha256(&b),
            tool: "fixture".into(),
            classes: vec![],
        };
        let mut views = String::new();
        let tree = super::render(
            &pair,
            0,
            &data,
            &mut views,
            &mut std::collections::BTreeMap::new(),
        )
        .unwrap();
        assert_eq!(tree.matches("class-leaf archive-leaf").count(), 5);
        assert!(tree.contains("data-status=\"moved\""));
        assert!(tree.contains("data-status=\"added\""));
        assert!(tree.contains("data-status=\"deleted\""));
        assert!(tree.contains("MANIFEST.MF"));
        assert!(views.contains("Download exact member"));
        assert!(tree.contains(&super::sha256(b"binary\0")));
    }
    #[test]
    fn unsafe_member_paths_and_wrong_raw_identity_are_rejected() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("unsafe.jar");
        let bytes = jar(&[("../escape", b"x")]);
        std::fs::write(&path, &bytes).unwrap();
        assert!(super::entries(path.to_str().unwrap(), &super::sha256(&bytes)).is_err());
        assert!(super::entries(path.to_str().unwrap(), "sha256:wrong").is_err());
    }
}

#[cfg(test)]
#[path = "archive_resource_pool_tests.rs"]
mod pool_tests;
