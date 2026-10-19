//! Integration fixtures retain per-projection rows while exact view inputs are pooled.
use super::super::archive_resource_pair::ArchiveResourcePair;
use super::super::class_evidence::ClassEvidence;
use super::super::jar_pair::ReleaseReportJarPair;
use base64::Engine as _;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::io::Cursor;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Path;

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

fn fixture(
    root: &Path,
    id: &str,
    before: &[(&str, &[u8])],
    after: &[(&str, &[u8])],
) -> (ReleaseReportJarPair, ClassEvidence) {
    let baseline = jar(before);
    let candidate = jar(after);
    let baseline_path = root.join(format!("{id}-before.jar"));
    let candidate_path = root.join(format!("{id}-after.jar"));
    std::fs::write(&baseline_path, &baseline).unwrap();
    std::fs::write(&candidate_path, &candidate).unwrap();
    (
        ReleaseReportJarPair {
            id: id.into(),
            baseline_path: baseline_path.to_string_lossy().into_owned(),
            candidate_path: candidate_path.to_string_lossy().into_owned(),
            provenance: "Synthetic exact resource pool fixture".into(),
            class_evidence_path: None,
            class_evidence_sha256: None,
        },
        ClassEvidence {
            schema: "sfm:jar-class-evidence@1".into(),
            projection: id.into(),
            baseline_sha256: super::sha256(&baseline),
            candidate_sha256: super::sha256(&candidate),
            tool: "Synthetic resource-only fixture".into(),
            classes: vec![],
        },
    )
}

#[test]
fn identical_resource_pairs_share_one_view_but_keep_both_projection_rows() {
    let root = tempfile::tempdir().unwrap();
    let before = [("raw.bin", b"\0\xffbefore\r\n".as_slice())];
    let after = [("raw.bin", b"\0\x80after\n".as_slice())];
    let mut pool = BTreeMap::new();
    let mut views = String::new();
    let mut pairs = Vec::new();
    for (index, id) in ["jar-1-19-2", "jar-1-20-3"].into_iter().enumerate() {
        let (pair, data) = fixture(root.path(), id, &before, &after);
        let tree = super::render(&pair, index, &data, &mut views, &mut pool).unwrap();
        assert_eq!(tree.matches("class-leaf archive-leaf").count(), 1);
        assert!(tree.contains("href=\"#archive-0-0\""));
        assert!(tree.contains("data-path=\"raw.bin\""));
        assert!(tree.contains("data-old-path=\"raw.bin\" data-new-path=\"raw.bin\""));
        assert!(tree.contains("data-status=\"changed\""));
        assert!(tree.contains(&super::sha256(before[0].1)));
        assert!(tree.contains(&super::sha256(after[0].1)));
        pairs.push(pair);
    }
    assert_eq!(pool.len(), 1);
    assert_eq!(
        views
            .matches("class=\"class-view archive-view code-diff\"")
            .count(),
        1
    );

    // The real class renderer must carry one pool across the version loop, while
    // preserving the projection/path/raw-hash inputs used by the unchanged JS
    // to construct independent scope-qualified review identities for each leaf.
    let html = super::super::class_tree::render(
        &pairs,
        root.path(),
        &crate::cancellation::CancellationToken::new(),
    )
    .unwrap();
    let encoded = html
        .split_once("data-sha256=\"")
        .unwrap()
        .1
        .split_once('>')
        .unwrap()
        .1
        .split_once("</template>")
        .unwrap()
        .0;
    let compressed = base64::engine::general_purpose::STANDARD
        .decode(encoded)
        .unwrap();
    // Decode with the existing ZIP dependency, then check the payload's exact
    // expanded size and SHA256 as production JavaScript does.
    let expanded = inflate_explorer(&compressed, &html);
    assert_eq!(expanded.matches("class-leaf archive-leaf").count(), 2);
    assert_eq!(expanded.matches("href=\"#archive-0-0\"").count(), 2);
    assert_eq!(
        expanded
            .matches("class=\"class-view archive-view code-diff\"")
            .count(),
        1
    );
    assert!(expanded.contains("data-projection=\"0\"><h3>1.19.2 ·"));
    assert!(expanded.contains("data-projection=\"1\"><h3>1.20.3 ·"));
}

fn inflate_explorer(compressed: &[u8], html: &str) -> String {
    let expanded_size: u32 = html
        .split_once("id=\"class-payload\" data-size=\"")
        .unwrap()
        .1
        .split('"')
        .next()
        .unwrap()
        .parse()
        .unwrap();
    // A local ZIP header lets the existing ZIP reader decode raw DEFLATE.
    // Read exactly the declared bound, then verify the original payload SHA256;
    // this fixture does not have or claim a ZIP-container CRC identity.
    let mut framed = Vec::new();
    framed.extend_from_slice(&0x0403_4b50_u32.to_le_bytes());
    framed.extend_from_slice(&[20, 0, 0, 0, 8, 0, 0, 0, 0, 0]);
    framed.extend_from_slice(&[0; 4]);
    framed.extend_from_slice(&u32::try_from(compressed.len()).unwrap().to_le_bytes());
    framed.extend_from_slice(&expanded_size.to_le_bytes());
    framed.extend_from_slice(&[1, 0, 0, 0, b'x']);
    framed.extend_from_slice(compressed);
    let mut cursor = Cursor::new(framed);
    let entry = zip::read::read_zipfile_from_stream(&mut cursor)
        .unwrap()
        .unwrap();
    let mut expanded = String::new();
    entry
        .take(u64::from(expanded_size))
        .read_to_string(&mut expanded)
        .unwrap();
    assert_eq!(expanded.len(), usize::try_from(expanded_size).unwrap());
    let expected = html
        .split_once("id=\"class-payload\"")
        .unwrap()
        .1
        .split_once("data-sha256=\"")
        .unwrap()
        .1
        .split('"')
        .next()
        .unwrap();
    assert_eq!(super::sha256(expanded.as_bytes()), expected);
    expanded
}

#[test]
fn exact_pool_keys_preserve_both_paths_both_bytes_and_absent_empty() {
    let keys = [
        (
            Some("old"),
            Some("new"),
            Some(b"\0\xff".as_slice()),
            Some(b"\0\xfe".as_slice()),
        ),
        (
            Some("other"),
            Some("new"),
            Some(b"\0\xff".as_slice()),
            Some(b"\0\xfe".as_slice()),
        ),
        (
            Some("old"),
            Some("other"),
            Some(b"\0\xff".as_slice()),
            Some(b"\0\xfe".as_slice()),
        ),
        (
            Some("old"),
            Some("new"),
            Some(b"\0\xfd".as_slice()),
            Some(b"\0\xfe".as_slice()),
        ),
        (
            Some("old"),
            Some("new"),
            Some(b"\0\xff".as_slice()),
            Some(b"\0\xfd".as_slice()),
        ),
        (None, Some("new"), None, Some(b"".as_slice())),
        (
            Some("old"),
            Some("new"),
            Some(b"".as_slice()),
            Some(b"".as_slice()),
        ),
        (Some("old"), None, Some(b"".as_slice()), None),
        (Some(""), Some("new"), None, Some(b"".as_slice())),
        (Some("old"), Some("new"), None, Some(b"".as_slice())),
        (Some("old"), Some("new"), Some(b"".as_slice()), None),
    ]
    .map(|(old, new, before, after)| ArchiveResourcePair::new(old, new, before, after));
    let unique: BTreeSet<_> = keys.into_iter().collect();
    assert_eq!(unique.len(), 11);
}

#[test]
fn binary_pairs_with_equal_empty_text_views_are_not_pooled() {
    let root = tempfile::tempdir().unwrap();
    let mut pool = BTreeMap::new();
    let mut views = String::new();
    for (index, raw) in [b"\0\xff".as_slice(), b"\0\xfe".as_slice()]
        .into_iter()
        .enumerate()
    {
        let entries = [("binary", raw)];
        let (pair, data) = fixture(root.path(), &format!("binary-{index}"), &entries, &entries);
        super::render(&pair, index, &data, &mut views, &mut pool).unwrap();
    }
    assert_eq!(pool.len(), 2);
    assert_eq!(
        views
            .matches("class=\"class-view archive-view code-diff\"")
            .count(),
        2
    );
}

#[test]
fn pooled_downloads_decode_to_original_non_utf8_crlf_and_png_bytes() {
    let root = tempfile::tempdir().unwrap();
    let before = b"\0\xffunmodified\r\n".as_slice();
    let after = b"\x89PNG\r\n\x1a\n\0\xffexact\r\n".as_slice();
    let (pair, data) = fixture(
        root.path(),
        "raw-download",
        &[("image.png", before)],
        &[("image.png", after)],
    );
    let mut views = String::new();
    let mut pool = BTreeMap::new();
    for index in 0..2 {
        super::render(&pair, index, &data, &mut views, &mut pool).unwrap();
    }
    let decoded = views
        .split("href=\"data:application/octet-stream;base64,")
        .skip(1)
        .map(|part| {
            base64::engine::general_purpose::STANDARD
                .decode(part.split('"').next().unwrap())
                .unwrap()
        })
        .collect::<Vec<_>>();
    assert_eq!(decoded, [before, after]);
    assert_eq!(views.matches("download=\"image.png\"").count(), 2);
    assert!(views.contains(&super::sha256(before)));
    assert!(views.contains(&super::sha256(after)));
    let png = views
        .split_once("src=\"data:image/png;base64,")
        .unwrap()
        .1
        .split('"')
        .next()
        .unwrap();
    assert_eq!(
        base64::engine::general_purpose::STANDARD
            .decode(png)
            .unwrap(),
        after
    );
}

#[test]
fn rendered_path_and_presence_changes_never_share_resource_views() {
    let root = tempfile::tempdir().unwrap();
    let moved_from = [("old.txt", b"exact".as_slice())];
    let moved_to = [("new.txt", b"exact".as_slice())];
    let other = [("other.txt", b"exact".as_slice())];
    let empty = [("new.txt", b"".as_slice())];
    let cases = [
        (moved_from.as_slice(), moved_to.as_slice(), "moved"),
        (moved_from.as_slice(), other.as_slice(), "moved"),
        (other.as_slice(), moved_to.as_slice(), "moved"),
        (&[], empty.as_slice(), "added"),
        (empty.as_slice(), empty.as_slice(), "unchanged"),
        (empty.as_slice(), &[], "deleted"),
    ];
    let mut pool = BTreeMap::new();
    let mut views = String::new();
    for (index, (before, after, status)) in cases.into_iter().enumerate() {
        let (pair, data) = fixture(
            root.path(),
            &format!("path-presence-{index}"),
            before,
            after,
        );
        let tree = super::render(&pair, index, &data, &mut views, &mut pool).unwrap();
        assert_eq!(tree.matches("class-leaf archive-leaf").count(), 1);
        assert!(tree.contains(&format!("data-status=\"{status}\"")));
    }
    assert_eq!(pool.len(), 6);
    assert_eq!(
        views
            .matches("class=\"class-view archive-view code-diff\"")
            .count(),
        6
    );
}
