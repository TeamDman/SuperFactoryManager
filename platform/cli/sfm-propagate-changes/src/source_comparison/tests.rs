use super::input::ComparisonInput;
use super::request::ComparisonRequest;
use super::*;
use crate::cancellation::CancellationToken;
use crate::source_projection::oracle_compare::ComparisonPolicy;
use crate::source_projection::provenance::sha256;
use std::collections::BTreeMap;
use std::fs;
use std::io::Write as _;
use std::path::Path;
use std::process::Command;
use std::process::Stdio;

fn request(left: ComparisonInput, right: ComparisonInput) -> ComparisonRequest {
    ComparisonRequest {
        schema: "sfm:source-comparison-request@1".into(),
        left,
        right,
        policy: ComparisonPolicy {
            allow_generated_banner: false,
            ..Default::default()
        },
    }
}
fn directory(root: &Path) -> ComparisonInput {
    ComparisonInput::Directory {
        root: root.to_str().unwrap().to_owned(),
    }
}
fn write(root: &Path, path: &str, bytes: &[u8]) {
    let path = root.join(path);
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, bytes).unwrap();
}
fn git(root: &Path, args: &[&str], input: Option<&[u8]>) -> String {
    let mut child = Command::new("git")
        .arg("-C")
        .arg(root)
        .args([
            "-c",
            "user.name=Comparison fixture",
            "-c",
            "user.email=fixture@example.invalid",
            "-c",
            "commit.gpgSign=false",
        ])
        .args(args)
        .env_remove("GIT_DIR")
        .env_remove("GIT_WORK_TREE")
        .env_remove("GIT_INDEX_FILE")
        .env("GIT_AUTHOR_NAME", "Comparison fixture")
        .env("GIT_AUTHOR_EMAIL", "fixture@example.invalid")
        .env("GIT_COMMITTER_NAME", "Comparison fixture")
        .env("GIT_COMMITTER_EMAIL", "fixture@example.invalid")
        .stdin(if input.is_some() {
            Stdio::piped()
        } else {
            Stdio::null()
        })
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap();
    if let Some(bytes) = input {
        child.stdin.take().unwrap().write_all(bytes).unwrap();
    }
    let result = child.wait_with_output().unwrap();
    assert!(
        result.status.success(),
        "{}",
        String::from_utf8_lossy(&result.stderr)
    );
    String::from_utf8(result.stdout).unwrap().trim().to_owned()
}
fn pinned_fixture(root: &Path, bytes: &[u8]) -> (ComparisonInput, String) {
    git(root, &["init", "--quiet"], None);
    let blob = git(root, &["hash-object", "-w", "--stdin"], Some(bytes));
    let tree = git(
        root,
        &["mktree"],
        Some(format!("100644 blob {blob}\tA.txt\n").as_bytes()),
    );
    let commit = git(
        root,
        &["commit-tree", &tree, "-m", "synthetic comparison fixture"],
        None,
    );
    (
        ComparisonInput::PinnedGit {
            repository: root.to_str().unwrap().to_owned(),
            commit,
            prefix: "".into(),
        },
        blob,
    )
}
#[test]
fn deterministic_binary_add_remove_empty_and_raw_evidence() {
    let temp = tempfile::tempdir().unwrap();
    let left = temp.path().join("left");
    let right = temp.path().join("right");
    write(&left, "z.txt", b"old\n");
    write(&left, "removed.txt", b"removed");
    write(&left, "binary.bin", b"\xff\0");
    write(&right, "z.txt", b"new\n");
    write(&right, "added.txt", b"");
    write(&right, "binary.bin", b"\xfe\0");
    let request = request(directory(&left), directory(&right));
    let report = compare(&request, &CancellationToken::new()).unwrap();
    assert_eq!(
        facet_json::to_string(&report).unwrap(),
        facet_json::to_string(&compare(&request, &CancellationToken::new()).unwrap()).unwrap()
    );
    assert_eq!(
        report
            .differences
            .iter()
            .map(|diff| diff.path.as_str())
            .collect::<Vec<_>>(),
        ["added.txt", "binary.bin", "removed.txt", "z.txt"]
    );
    assert_eq!(report.differences[0].status, "added");
    assert_eq!(report.differences[2].status, "removed");
    assert!(report.differences[1].before.is_none());
    assert_eq!(report.left.files["binary.bin"].bytes, b"\xff\0");
    let decoded: super::report::ComparisonReport =
        facet_json::from_str(&facet_json::to_string(&report).unwrap()).unwrap();
    assert_eq!(report.left, decoded.left);
}
#[test]
fn directory_git_equivalence_retains_distinct_provenance() {
    let temp = tempfile::tempdir().unwrap();
    let repo = temp.path().join("repo");
    fs::create_dir(&repo).unwrap();
    let (git_input, _) = pinned_fixture(&repo, b"fixture\n");
    let local = temp.path().join("local");
    write(&local, "A.txt", b"fixture\n");
    let report = compare(
        &request(directory(&local), git_input),
        &CancellationToken::new(),
    )
    .unwrap();
    assert_eq!(
        report.left.identity.content_sha256,
        report.right.identity.content_sha256
    );
    assert_ne!(
        report.left.identity.snapshot_sha256,
        report.right.identity.snapshot_sha256
    );
    assert!(report.differences[0].exact_equal);
    assert!(!report.right.identity.evidence["commit"].is_empty());
    assert!(!report.right.identity.evidence["tree"].is_empty());
}
#[test]
fn missing_git_objects_are_errors_never_absent_files() {
    let temp = tempfile::tempdir().unwrap();
    let (input, oid) = pinned_fixture(temp.path(), b"fixture\n");
    fs::remove_file(
        temp.path()
            .join(".git/objects")
            .join(&oid[..2])
            .join(&oid[2..]),
    )
    .unwrap();
    assert!(snapshot(&input, &CancellationToken::new()).is_err());
    if let ComparisonInput::PinnedGit { repository, .. } = input {
        assert!(
            snapshot(
                &ComparisonInput::PinnedGit {
                    repository,
                    commit: "1111111111111111111111111111111111111111".into(),
                    prefix: "".into(),
                },
                &CancellationToken::new()
            )
            .is_err()
        );
    }
}
#[test]
fn git_requires_full_commit_and_safe_prefix() {
    let temp = tempfile::tempdir().unwrap();
    let (mut input, _) = pinned_fixture(temp.path(), b"x");
    if let ComparisonInput::PinnedGit { prefix, .. } = &mut input {
        *prefix = "../escape".into();
    }
    assert!(snapshot(&input, &CancellationToken::new()).is_err());
    if let ComparisonInput::PinnedGit { commit, prefix, .. } = &mut input {
        prefix.clear();
        commit.truncate(8);
    }
    assert!(snapshot(&input, &CancellationToken::new()).is_err());
}
#[test]
fn rejects_traversal_reserved_names_case_and_file_directory_collisions() {
    let input = ComparisonInput::Directory {
        root: "unused".into(),
    };
    let file = super::file::ComparisonFile {
        related_references: None,
        sha256: sha256(b"x"),
        bytes: b"x".to_vec(),
        mode: None,
        git_oid: None,
        source_path: None,
        source_sha256: None,
    };
    for paths in [
        vec!["../escape"],
        vec!["CON.txt"],
        vec!["a", "A"],
        vec!["a", "a/b"],
        vec!["A/x", "a/y"],
    ] {
        let files = paths
            .into_iter()
            .map(|path| (path.into(), file.clone()))
            .collect();
        assert!(super::adapters::seal(input.clone(), BTreeMap::new(), vec![], files).is_err());
    }
}
#[test]
fn normalization_declares_transformations_and_keeps_raw_evidence() {
    let temp = tempfile::tempdir().unwrap();
    let left = temp.path().join("left");
    let right = temp.path().join("right");
    write(&left, "A.txt", b"a\n");
    write(&right, "A.txt", b"a\r\n");
    let mut request = request(directory(&left), directory(&right));
    request.policy.normalize_crlf = true;
    let report = compare(&request, &CancellationToken::new()).unwrap();
    let diff = &report.differences[0];
    assert_eq!(diff.status, "allowed-byte-difference");
    assert!(!diff.exact_equal && diff.normalized_equal);
    assert!(!diff.transformations.is_empty());
    assert_eq!(report.right.files["A.txt"].bytes, b"a\r\n");
    assert_ne!(
        report.left.identity.content_sha256,
        report.right.identity.content_sha256
    );
}
#[test]
fn prepare_readonly_no_overwrite_no_overlap_and_escaped_html() {
    let temp = tempfile::tempdir().unwrap();
    let left = temp.path().join("left");
    let right = temp.path().join("right");
    write(&left, "A.txt", b"<script>alert('left')</script>\n");
    write(&right, "A.txt", b"<script>alert('right')</script>\n");
    let request = request(directory(&left), directory(&right));
    let output = temp.path().join("bundle");
    let report = prepare(&request, &output, &CancellationToken::new()).unwrap();
    assert_eq!(
        fs::read(output.join("right/A.txt")).unwrap(),
        report.right.files["A.txt"].bytes
    );
    assert!(
        fs::metadata(output.join("left/A.txt"))
            .unwrap()
            .permissions()
            .readonly()
    );
    let html = fs::read_to_string(output.join("index.html")).unwrap();
    assert!(html.contains("&lt;script&gt;"));
    assert!(!html.contains("<script>alert("));
    assert!(html.contains("source-link") && html.contains("comparison-status"));
    assert!(prepare(&request, &output, &CancellationToken::new()).is_err());
    assert!(prepare(&request, &left.join("inside"), &CancellationToken::new()).is_err());
}
#[test]
fn unsupported_schemas_are_rejected() {
    let temp = tempfile::tempdir().unwrap();
    let mut request = request(directory(temp.path()), directory(temp.path()));
    request.schema = "other".into();
    assert!(compare(&request, &CancellationToken::new()).is_err());
    request.schema = "sfm:source-comparison-request@1".into();
    request.policy.schema_version = 2;
    assert!(compare(&request, &CancellationToken::new()).is_err());
}
#[cfg(unix)]
#[test]
fn directory_links_are_rejected() {
    let temp = tempfile::tempdir().unwrap();
    let root = temp.path().join("root");
    fs::create_dir(&root).unwrap();
    std::os::unix::fs::symlink(temp.path(), root.join("escape")).unwrap();
    assert!(snapshot(&directory(&root), &CancellationToken::new()).is_err());
}

#[test]
fn projection_adapter_uses_current_catalog_rendering_and_binds_authored_inputs() {
    use crate::source_projection::core_inputs::BuildTargetMetadata;
    use crate::source_projection::core_inputs::CoreProjectInputs;
    use crate::source_projection::core_inputs::InputPredicate;
    use crate::source_projection::core_inputs::InputVariant;
    let temp = tempfile::tempdir().unwrap();
    let repo = temp.path().join("repo");
    let core = repo.join("platform/minecraft/core-liquid-template");
    write(&repo, "platform/minecraft/projections.json",
        br#"{"fixture/release":{"minecraft_version":"1.19.2","environment":"release","features":[]}}"#);
    write(&core, "feature-definitions.json", b"{}");
    write(&core, "src/main/java/Example.java",
        b"{% if targets.mc_1_19_2 %}\nclass Example { int value = 1; }\n{% else %}\nclass Example {}\n{% endif %}\n");
    let mut metadata = CoreProjectInputs {
        schema_version: 1,
        targets: BTreeMap::from([(
            "1.19.2".into(),
            BuildTargetMetadata {
                java_major: 17,
                loader: "forge".into(),
            },
        )]),
        source_rules: BTreeMap::new(),
        project_files: BTreeMap::new(),
    };
    for output in [
        "build.gradle",
        "settings.gradle",
        "gradle.properties",
        "gradlew",
        "gradlew.bat",
        "sfm-toolchain.lock.json",
        "gradle/wrapper/gradle-wrapper.properties",
        "gradle/wrapper/gradle-wrapper.jar",
    ] {
        let input = format!("build/common/{output}");
        let bytes: &[u8] = match output {
            "gradle.properties" => b"minecraft_version=1.19.2\nmod_version=fixture\n",
            "settings.gradle" => b"rootProject.name = 'sfm-1.19.2'\n",
            "gradle/wrapper/gradle-wrapper.properties" => {
                b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n"
            }
            "gradle/wrapper/gradle-wrapper.jar" => b"\0\xffwrapper",
            _ => b"fixture\n",
        };
        write(&core, &input, bytes);
        metadata.project_files.insert(
            output.into(),
            vec![InputVariant {
                reference_template: None,
                input,
                when: InputPredicate::default(),
                template: false,
            }],
        );
    }
    write(
        &core,
        "project-inputs.json",
        (facet_json::to_string(&metadata).unwrap() + &" ".repeat(1024 * 1024 + 1)).as_bytes(),
    );
    let selected = ComparisonInput::Projection {
        repository: repo.to_str().unwrap().to_owned(),
        projection: "fixture/release".into(),
    };
    let captured = snapshot(&selected, &CancellationToken::new()).unwrap();
    let java = &captured.files["src/main/java/Example.java"];
    assert!(
        String::from_utf8(java.bytes.clone())
            .unwrap()
            .contains("int value = 1")
    );
    assert_eq!(
        java.source_sha256,
        Some(sha256(
            &fs::read(core.join("src/main/java/Example.java")).unwrap()
        ))
    );
    assert!(captured.identity.evidence.contains_key("context_identity"));
    assert!(captured.identity.evidence.contains_key("catalog_sha256"));
    let local = temp.path().join("rendered");
    for (path, file) in &captured.files {
        write(&local, path, &file.bytes);
    }
    let report = compare(
        &request(directory(&local), selected),
        &CancellationToken::new(),
    )
    .unwrap();
    assert_eq!(
        report.left.identity.content_sha256,
        report.right.identity.content_sha256
    );
    assert!(report.differences.iter().all(|diff| diff.exact_equal));
    assert!(
        !repo
            .join("platform/minecraft/projections/fixture/release")
            .exists()
    );
}

#[test]
fn unique_path_only_moves_are_one_logical_file_and_ambiguous_copies_stay_separate() {
    let temp = tempfile::tempdir().unwrap();
    let left = temp.path().join("left");
    let right = temp.path().join("right");
    write(
        &left,
        "src/old/ToughCableHorizontal.java",
        b"class ToughCableHorizontal {}\n",
    );
    write(
        &right,
        "src/new/ToughCableHorizontal.java",
        b"class ToughCableHorizontal {}\n",
    );
    write(&left, "old-one.txt", b"duplicate");
    write(&left, "old-two.txt", b"duplicate");
    write(&right, "new-one.txt", b"duplicate");
    let report = compare(
        &request(directory(&left), directory(&right)),
        &CancellationToken::new(),
    )
    .unwrap();
    assert_eq!(report.differences.len(), 4);
    let moved = report
        .differences
        .iter()
        .find(|diff| diff.status == "moved")
        .unwrap();
    assert!(moved.exact_equal && moved.normalized_equal);
    assert_eq!(
        moved.old_path.as_deref(),
        Some("src/old/ToughCableHorizontal.java")
    );
    assert_eq!(
        moved.new_path.as_deref(),
        Some("src/new/ToughCableHorizontal.java")
    );
    let html = crate::release_report::comparison_render::render(&report, &CancellationToken::new())
        .unwrap();
    assert!(
        html.contains("comparison-review-payload")
            && html.contains("review-undo")
            && html.contains("review-apply")
    );
    assert!(html.contains("src/old/ToughCableHorizontal.java"));
}

#[test]
fn comparison_identity_binds_policy_and_inputs_and_cancelled_prepare_publishes_nothing() {
    let temp = tempfile::tempdir().unwrap();
    let left = temp.path().join("left");
    let right = temp.path().join("right");
    write(&left, "A.txt", b"same");
    write(&right, "A.txt", b"same");
    let mut request = request(directory(&left), directory(&right));
    let initial = compare(&request, &CancellationToken::new()).unwrap();
    request.policy.normalize_crlf = true;
    let policy_change = compare(&request, &CancellationToken::new()).unwrap();
    assert_ne!(
        initial.comparison_identity,
        policy_change.comparison_identity
    );
    write(&right, "A.txt", b"different");
    assert_ne!(
        policy_change.comparison_identity,
        compare(&request, &CancellationToken::new())
            .unwrap()
            .comparison_identity
    );
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("fixture cancellation");
    let output = temp.path().join("cancelled");
    assert!(prepare(&request, &output, &cancellation).is_err());
    assert!(!output.exists());
}

#[test]
fn reference_relevance_survives_unique_path_move_without_inference() {
    use crate::source_projection::context::ProjectionContext;
    use crate::source_projection::reference_annotations::associations;
    let old = tempfile::tempdir().unwrap();
    let new = tempfile::tempdir().unwrap();
    write(old.path(), "data/minecraft/disk_reset.json", b"{}");
    write(new.path(), "data/sfm/disk_reset.json", b"{}");
    let cancellation = CancellationToken::new();
    let request = request(directory(old.path()), directory(new.path()));
    let left = snapshot(&request.left, &cancellation).unwrap();
    let mut right = snapshot(&request.right, &cancellation).unwrap();
    assert_eq!(
        super::file_reference_query::FileReferenceQuery::from_snapshot(
            &left,
            "data/minecraft/disk_reset.json"
        )
        .status,
        "unavailable"
    );
    let context = ProjectionContext {
        minecraft_version: "1.21.1".into(),
        preset: String::new(),
        environment: "release".into(),
        projection_key: "fixture/mc-1.21.1".into(),
        features: BTreeMap::new(),
        targets: BTreeMap::new(),
    };
    let source = "{% issue 527 %}\n{% issue 621 %}\n";
    right
        .files
        .get_mut("data/sfm/disk_reset.json")
        .unwrap()
        .related_references = Some(
        associations(
            source,
            "assets/path-issues.liquid",
            source.as_bytes(),
            &context,
        )
        .unwrap(),
    );
    let right =
        super::adapters::seal(request.right.clone(), BTreeMap::new(), vec![], right.files).unwrap();
    let report = super::compare::compare(&request, &cancellation).unwrap();
    let moved = report
        .differences
        .iter()
        .find(|d| d.status == "moved")
        .unwrap();
    assert_eq!(
        moved.old_path.as_deref(),
        Some("data/minecraft/disk_reset.json")
    );
    assert_eq!(moved.new_path.as_deref(), Some("data/sfm/disk_reset.json"));
    let query = super::file_reference_query::FileReferenceQuery::from_snapshot(
        &right,
        "data/sfm/disk_reset.json",
    );
    assert_eq!(query.status, "associated");
    assert_eq!(
        query
            .related_references
            .unwrap()
            .references
            .iter()
            .map(|reference| reference.number)
            .collect::<Vec<_>>(),
        vec![527, 621]
    );
    assert_eq!(
        super::file_reference_query::FileReferenceQuery::from_snapshot(&report.left, "absent")
            .status,
        "absent"
    );
}
