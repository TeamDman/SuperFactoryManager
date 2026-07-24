use super::*;
use std::process::Command;

fn fixture() -> String {
    std::fs::read_to_string(
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../../docs/architecture/fixtures/repository-review-bundle-v1.json"),
    )
    .expect("canonical bundle fixture")
}

#[test]
fn frozen_fixture_parses_round_trips_and_has_frozen_identity() {
    let bundle = parse(&fixture()).expect("fixture parses");
    assert_eq!(
        bundle.id,
        "sha256:53fcdc3aed1b2cd4dbf9bac7ba409db9ad67df9ef6be1326ce8a14abc80c2af4"
    );
    assert_eq!(
        semantic_hash(&bundle).expect("semantic hash"),
        "53fcdc3aed1b2cd4dbf9bac7ba409db9ad67df9ef6be1326ce8a14abc80c2af4"
    );
    let canonical = to_canonical_json(&bundle).expect("canonical JSON");
    let reparsed = parse(&canonical).expect("canonical reparses");
    assert_eq!(reparsed, bundle);
    assert_eq!(
        to_canonical_json(&reparsed).expect("stable JSON"),
        canonical
    );
    assert!(canonical.contains("\"before\": null"));
    assert!(canonical.ends_with('\n'));

    let with_unknown = fixture().replacen(
        "\"name\":",
        "\"future_presentation_hint\": true,\n  \"name\":",
        1,
    );
    parse(&with_unknown).expect("unknown fields are ignored");
}

#[test]
fn duplicate_keys_hostile_paths_hashes_and_ranges_fail_closed() {
    let duplicate = fixture().replacen(
            "\"schema\": \"sfm.repository-review-bundle/1\"",
            "\"schema\": \"sfm.repository-review-bundle/1\",\n  \"schema\": \"sfm.repository-review-bundle/1\"",
            1,
        );
    assert!(
        parse(&duplicate)
            .expect_err("duplicate key")
            .to_string()
            .contains("duplicate")
    );

    for hostile in [
        "../escape.java",
        "/absolute.java",
        "C:/drive.java",
        "src\\backslash.java",
        "src/./dot.java",
        "src//empty.java",
        "src/cafe\u{301}.java",
    ] {
        let mut bundle = parse(&fixture()).expect("fixture");
        bundle.after.files[1].path = hostile.to_owned();
        assert!(
            validate(&bundle)
                .expect_err("hostile path")
                .to_string()
                .contains("path"),
            "accepted hostile path {hostile:?}"
        );
    }

    let mut bundle = parse(&fixture()).expect("fixture");
    bundle.after.files[1].sha256 = "0".repeat(64);
    assert!(
        validate(&bundle)
            .expect_err("bad hash")
            .to_string()
            .contains("SHA-256")
    );

    let mut bundle = parse(&fixture()).expect("fixture");
    bundle.before.id = format!("sha256:{}", "0".repeat(64));
    assert!(
        validate(&bundle)
            .expect_err("snapshot hash")
            .to_string()
            .contains("snapshot id")
    );

    let mut bundle = parse(&fixture()).expect("fixture");
    bundle.id = format!("sha256:{}", "0".repeat(64));
    assert!(
        validate(&bundle)
            .expect_err("bundle hash")
            .to_string()
            .contains("bundle id")
    );

    let mut bundle = parse(&fixture()).expect("fixture");
    bundle.comparison.file_changes[0].operations[0]
        .after
        .as_mut()
        .expect("after selection")
        .sha256 = "0".repeat(64);
    assert!(
        validate(&bundle)
            .expect_err("selection hash")
            .to_string()
            .contains("selection")
    );

    let mut bundle = parse(&fixture()).expect("fixture");
    bundle.comparison.file_changes[0].operations[0]
        .after
        .as_mut()
        .expect("after selection")
        .end_byte = usize::MAX;
    assert!(
        validate(&bundle)
            .expect_err("range bound")
            .to_string()
            .contains("range")
    );

    let malformed_base64 = fixture().replace("\"data\": \"AP9B\"", "\"data\": \"not padded!\"");
    assert!(
        parse(&malformed_base64)
            .expect_err("base64")
            .to_string()
            .contains("base64")
    );

    let malformed_utf8_escape = fixture().replace("\"fixture\"", "\"\\uD800\"");
    assert!(parse(&malformed_utf8_escape).is_err());

    let mut bundle = parse(&fixture()).expect("fixture");
    bundle.after.files[2].path = bundle.after.files[1].path.clone();
    assert!(
        validate(&bundle)
            .expect_err("duplicate path")
            .to_string()
            .contains("duplicate")
    );
}

#[test]
fn directory_provider_handles_utf8_binary_empty_and_determinism() {
    let root = tempfile::tempdir().expect("root");
    let before = root.path().join("before");
    let after = root.path().join("after");
    std::fs::create_dir_all(before.join("src")).expect("before dirs");
    std::fs::create_dir_all(after.join("src")).expect("after dirs");
    std::fs::write(before.join("src/unicode.txt"), "aéz").expect("before unicode");
    std::fs::write(after.join("src/unicode.txt"), "aêz").expect("after unicode");
    std::fs::write(before.join("binary.bin"), [0xff, 0]).expect("before binary");
    std::fs::write(after.join("binary.bin"), [0xfe, 0]).expect("after binary");
    std::fs::write(after.join("empty.txt"), []).expect("empty");

    let make = || {
        prepare(
            SnapshotProviderV1::Directory,
            root.path(),
            "fixture-directory",
            "Directory fixture",
            before.to_str().expect("UTF-8 before"),
            after.to_str().expect("UTF-8 after"),
        )
        .expect("prepare directory bundle")
    };
    let first = make();
    let second = make();
    assert_eq!(first, second);
    assert_eq!(
        to_canonical_json(&first).expect("first"),
        to_canonical_json(&second).expect("second")
    );

    let unicode = first
        .comparison
        .file_changes
        .iter()
        .find(|change| change.after_path.as_deref() == Some("src/unicode.txt"))
        .expect("unicode change");
    let operation = &unicode.operations[0];
    assert_eq!(
        (
            operation.before.as_ref().expect("before").start_byte,
            operation.before.as_ref().expect("before").end_byte
        ),
        (1, 3)
    );

    let mut off_boundary = first.clone();
    let unicode_change = off_boundary
        .comparison
        .file_changes
        .iter_mut()
        .find(|change| change.after_path.as_deref() == Some("src/unicode.txt"))
        .expect("unicode change");
    unicode_change.operations[0]
        .after
        .as_mut()
        .expect("after selection")
        .start_byte = 2;
    assert!(
        validate(&off_boundary)
            .expect_err("UTF-8 boundary")
            .to_string()
            .contains("UTF-8")
    );
    assert_eq!(
        (
            operation.after.as_ref().expect("after").start_byte,
            operation.after.as_ref().expect("after").end_byte
        ),
        (1, 3)
    );

    let binary = first
        .comparison
        .file_changes
        .iter()
        .find(|change| change.binary)
        .expect("binary change");
    assert!(binary.operations.is_empty());
    assert!(!binary.diagnostics.is_empty());
    let empty = first
        .comparison
        .file_changes
        .iter()
        .find(|change| change.after_path.as_deref() == Some("empty.txt"))
        .expect("empty change");
    assert!(empty.operations.is_empty());
    assert!(empty.diagnostics[0].contains("Empty"));
}

#[test]
fn file_and_snapshot_bounds_are_checked_before_output() {
    let oversized = vec![0; MAX_FILE_BYTES + 1];
    assert!(
        providers::snapshot_file("large.bin".to_owned(), oversized)
            .expect_err("file bound")
            .to_string()
            .contains("limit")
    );

    let file = SnapshotFileV1 {
        path: "a.txt".to_owned(),
        encoding: "utf8".to_owned(),
        text: Some(String::new()),
        data: None,
        sha256: sha256(b""),
    };
    let too_many = vec![file; MAX_FILES + 1];
    assert!(
        providers::make_snapshot(
            SnapshotSourceV1 {
                kind: "directory".to_owned(),
                revision: "fixture".to_owned(),
                label: "Before".to_owned()
            },
            too_many
        )
        .expect_err("file count")
        .to_string()
        .contains("file")
    );
    assert!(
        providers::check_snapshot_bounds(1, MAX_SNAPSHOT_BYTES + 1)
            .expect_err("snapshot total bytes")
            .to_string()
            .contains("byte")
    );

    let sparse = tempfile::tempdir().expect("sparse directory");
    let sparse_path = sparse.path().join("oversized.bin");
    let sparse_file = std::fs::File::create(&sparse_path).expect("sparse file");
    sparse_file
        .set_len(u64::try_from(MAX_FILE_BYTES + 1).expect("size fits u64"))
        .expect("set sparse length");
    assert!(
        providers::load_directory_files(sparse.path())
            .expect_err("metadata bound before read")
            .to_string()
            .contains("before allocation")
    );

    let oversized_header = format!("abc blob {}\n", MAX_FILE_BYTES + 1);
    let mut reader = std::io::Cursor::new(oversized_header.into_bytes());
    let mut total = 0;
    assert!(
        providers::read_git_blob(&mut reader, "abc", &mut total)
            .expect_err("git per-file preallocation bound")
            .to_string()
            .contains("before allocation")
    );
    let mut reader = std::io::Cursor::new(b"abc blob 1\n".to_vec());
    let mut total = MAX_SNAPSHOT_BYTES;
    assert!(
        providers::read_git_blob(&mut reader, "abc", &mut total)
            .expect_err("git total preallocation bound")
            .to_string()
            .contains("byte")
    );
}

#[test]
fn git_revision_provider_reads_trees_without_checkout() {
    let repository = tempfile::tempdir().expect("git repo");
    git_test(repository.path(), &["init"]);
    git_test(
        repository.path(),
        &["config", "user.email", "fixture@example.invalid"],
    );
    git_test(repository.path(), &["config", "user.name", "Fixture"]);
    for invalid in ["", "--help", "bad\nrevision", "bad\0revision"] {
        assert!(
            prepare(
                SnapshotProviderV1::Git,
                repository.path(),
                "git-fixture",
                "Git fixture",
                invalid,
                "HEAD",
            )
            .expect_err("invalid Git revision")
            .to_string()
            .contains("Git revision"),
            "accepted revision {invalid:?}"
        );
    }
    std::fs::write(repository.path().join("A.txt"), "before\n").expect("before file");
    git_test(repository.path(), &["add", "A.txt"]);
    git_test(repository.path(), &["commit", "-m", "before"]);
    let before = String::from_utf8(
        providers::git(repository.path(), &["rev-parse", "HEAD"]).expect("before rev"),
    )
    .expect("UTF-8 rev")
    .trim()
    .to_owned();
    std::fs::write(repository.path().join("A.txt"), "after\n").expect("after file");
    std::fs::write(repository.path().join("B.txt"), "added\n").expect("added file");
    git_test(repository.path(), &["add", "A.txt", "B.txt"]);
    git_test(repository.path(), &["commit", "-m", "after"]);
    let after = String::from_utf8(
        providers::git(repository.path(), &["rev-parse", "HEAD"]).expect("after rev"),
    )
    .expect("UTF-8 rev")
    .trim()
    .to_owned();
    let head_before = after.clone();

    let bundle = prepare(
        SnapshotProviderV1::Git,
        repository.path(),
        "git-fixture",
        "Git fixture",
        &before,
        &after,
    )
    .expect("git bundle");
    assert_eq!(bundle.before.files.len(), 1);
    assert_eq!(bundle.after.files.len(), 2);
    assert_eq!(bundle.comparison.file_changes.len(), 2);
    let current = String::from_utf8(
        providers::git(repository.path(), &["rev-parse", "HEAD"]).expect("current rev"),
    )
    .expect("UTF-8 rev")
    .trim()
    .to_owned();
    assert_eq!(current, head_before, "provider must not move HEAD");
}

fn git_test(repository: &Path, arguments: &[&str]) {
    let output = Command::new("git")
        .arg("-C")
        .arg(repository)
        .args(arguments)
        .output()
        .expect("git starts");
    assert!(
        output.status.success(),
        "git {:?}: {}",
        arguments,
        String::from_utf8_lossy(&output.stderr)
    );
}
