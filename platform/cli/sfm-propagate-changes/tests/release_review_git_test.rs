#[path = "../src/release_review_git.rs"]
mod release_review_git;

use release_review_git::AmbientRepositoryRelationshipKind;
use release_review_git::GitChangeKind;
use release_review_git::GitReconciliationDisposition;
use release_review_git::GitReviewUnitKind;
use release_review_git::classify_ambient_repository_relationship;
use release_review_git::produce_git_review_domain;
use std::fs;
use std::path::Path;
use std::process::Command;
use std::process::Output;

const REVIEW_EVIDENCE_PATH: &str = "docs/reviews/release.sfm-review.json";

#[test]
fn working_tree_probe_distinguishes_uncommitted_source_from_review_evidence_without_writes() {
    let fixture = relationship_repository();
    let paths = [REVIEW_EVIDENCE_PATH.to_owned()];
    let clean = release_review_git::inspect_working_tree(fixture.root(), &paths);
    assert_eq!(clean.source_dirty, Some(false));
    assert_eq!(
        clean.head.as_deref(),
        Some(fixture.candidate_commit.as_str())
    );
    write(fixture.root(), REVIEW_EVIDENCE_PATH, b"review draft\n");
    let evidence_only = release_review_git::inspect_working_tree(fixture.root(), &paths);
    assert_eq!(evidence_only.source_dirty, Some(false));
    assert_eq!(evidence_only.changes.len(), 1);
    assert!(evidence_only.changes[0].review_evidence_only);
    write(
        fixture.root(),
        "src/Example.java",
        b"class Example { int staged = 2; }\n",
    );
    git(fixture.root(), ["add", "src/Example.java"]);
    write(
        fixture.root(),
        "src/Example.java",
        b"class Example { int unstaged = 3; }\n",
    );
    write(
        fixture.root(),
        "src/Untracked.java",
        b"class Untracked {}\n",
    );
    let index_before = fs::read(fixture.root().join(".git/index")).expect("index before probe");
    let dirty = release_review_git::inspect_working_tree(fixture.root(), &paths);
    assert_eq!(dirty.source_dirty, Some(true));
    assert_eq!(dirty.head, clean.head);
    let tracked = dirty
        .changes
        .iter()
        .find(|change| change.path.utf8.as_deref() == Some("src/Example.java"))
        .unwrap();
    assert_eq!((tracked.index_status, tracked.worktree_status), ('M', 'M'));
    let untracked = dirty
        .changes
        .iter()
        .find(|change| change.path.utf8.as_deref() == Some("src/Untracked.java"))
        .unwrap();
    assert_eq!(
        (untracked.index_status, untracked.worktree_status),
        ('?', '?')
    );
    assert_eq!(
        fs::read(fixture.root().join(".git/index")).unwrap(),
        index_before
    );
    assert_eq!(
        git_stdout(fixture.root(), ["rev-parse", "HEAD"]),
        fixture.candidate_commit
    );
    assert_eq!(
        fs::read(fixture.root().join("src/Untracked.java")).unwrap(),
        b"class Untracked {}\n"
    );
}

#[test]
fn working_tree_status_keeps_rename_endpoints_and_raw_path_bytes() {
    let input =
        b"R  docs/reviews/new.json\0src/old.java\0?? space and\nnewline\0 M non-utf8-\xff\0";
    let changes =
        release_review_git::parse_working_tree_status(input, &["docs/reviews/new.json".to_owned()])
            .unwrap();
    let rename = changes
        .iter()
        .find(|change| change.index_status == 'R')
        .unwrap();
    assert_eq!(
        rename.previous_path.as_ref().unwrap().utf8.as_deref(),
        Some("src/old.java")
    );
    assert!(
        !rename.review_evidence_only,
        "moving source into evidence must not hide the source removal"
    );
    assert!(
        changes
            .iter()
            .any(|change| change.path.utf8.as_deref() == Some("space and\nnewline"))
    );
    assert!(
        changes
            .iter()
            .any(|change| change.path.utf8.is_none() && change.path.bytes_hex.ends_with("ff"))
    );
    assert!(release_review_git::parse_working_tree_status(b"R  dest\0", &[]).is_err());
    assert!(release_review_git::parse_working_tree_status(b"?? no-terminator", &[]).is_err());
    assert!(release_review_git::parse_working_tree_status(b"XX bad\0", &[]).is_err());
}

#[test]
fn unavailable_working_tree_is_unknown_not_clean() {
    let missing = tempfile::tempdir().unwrap();
    let report = release_review_git::inspect_working_tree(missing.path(), &[]);
    assert_eq!(report.source_dirty, None);
    assert_eq!(report.head, None);
    assert!(!report.diagnostics.is_empty());
}

#[test]
fn ambient_repository_relationship_is_exact_at_the_pinned_candidate() {
    let fixture = relationship_repository();
    let report = classify_ambient_repository_relationship(
        fixture.root(),
        &fixture.candidate_commit,
        &[REVIEW_EVIDENCE_PATH.to_owned()],
    );

    assert_eq!(
        report.classification,
        AmbientRepositoryRelationshipKind::Exact
    );
    assert_eq!(
        report.ambient_head.as_deref(),
        Some(fixture.candidate_commit.as_str())
    );
    assert_eq!(report.candidate_is_ancestor, Some(true));
    assert!(report.intervening_commits.is_empty());
    assert!(report.changed_paths.is_empty());
    assert!(report.source_affecting_paths.is_empty());
    assert_eq!(report.diagnostics[0].code, "ambient.head-exact");
}

#[test]
fn ambient_repository_relationship_accepts_an_evidence_only_descendant() {
    let fixture = relationship_repository();
    write(
        fixture.root(),
        REVIEW_EVIDENCE_PATH,
        b"{\"schema\":\"sfm.release-review/1\"}\n",
    );
    commit_all(fixture.root(), "review evidence");
    let ambient_head = git_stdout(fixture.root(), ["rev-parse", "HEAD"]);

    let report = classify_ambient_repository_relationship(
        fixture.root(),
        &fixture.candidate_commit,
        &[REVIEW_EVIDENCE_PATH.to_owned()],
    );

    assert_eq!(
        report.classification,
        AmbientRepositoryRelationshipKind::ReviewEvidenceOnly
    );
    assert_eq!(report.ambient_head.as_deref(), Some(ambient_head.as_str()));
    assert_eq!(report.candidate_is_ancestor, Some(true));
    assert_eq!(report.intervening_commits, [ambient_head]);
    assert_eq!(
        report
            .changed_paths
            .iter()
            .filter_map(|path| path.utf8.as_deref())
            .collect::<Vec<_>>(),
        [REVIEW_EVIDENCE_PATH]
    );
    assert!(report.source_affecting_paths.is_empty());
    assert_eq!(report.diagnostics[0].code, "ambient.review-evidence-only");
}

#[test]
fn ambient_repository_relationship_rejects_a_source_changing_descendant() {
    let fixture = relationship_repository();
    write(
        fixture.root(),
        "src/Example.java",
        b"class Example { int changed = 2; }\n",
    );
    commit_all(fixture.root(), "source change");

    let report = classify_ambient_repository_relationship(
        fixture.root(),
        &fixture.candidate_commit,
        &[REVIEW_EVIDENCE_PATH.to_owned()],
    );

    assert_eq!(
        report.classification,
        AmbientRepositoryRelationshipKind::SourceAffectingDivergence
    );
    assert_eq!(report.candidate_is_ancestor, Some(true));
    assert_eq!(
        report
            .source_affecting_paths
            .iter()
            .filter_map(|path| path.utf8.as_deref())
            .collect::<Vec<_>>(),
        ["src/Example.java"]
    );
    assert_eq!(report.diagnostics[0].code, "ambient.source-affecting-paths");
}

#[test]
fn ambient_repository_relationship_rejects_a_non_descendant_head() {
    let fixture = relationship_repository();
    git(
        fixture.root(),
        [
            "checkout",
            "--quiet",
            "-b",
            "divergent",
            &fixture.base_commit,
        ],
    );
    write(fixture.root(), "divergent.txt", b"different future\n");
    commit_all(fixture.root(), "divergent change");

    let report = classify_ambient_repository_relationship(
        fixture.root(),
        &fixture.candidate_commit,
        &[REVIEW_EVIDENCE_PATH.to_owned()],
    );

    assert_eq!(
        report.classification,
        AmbientRepositoryRelationshipKind::SourceAffectingDivergence
    );
    assert_eq!(report.candidate_is_ancestor, Some(false));
    assert!(report.intervening_commits.is_empty());
    assert_eq!(report.diagnostics[0].code, "ambient.non-descendant");
}

#[test]
fn ambient_repository_relationship_observes_reverted_source_history() {
    let fixture = relationship_repository();
    let candidate_source =
        fs::read(fixture.root().join("src/Example.java")).expect("candidate source should exist");
    write(
        fixture.root(),
        "src/Example.java",
        b"class Example { int transient_change = 3; }\n",
    );
    commit_all(fixture.root(), "temporary source change");
    write(fixture.root(), "src/Example.java", &candidate_source);
    commit_all(fixture.root(), "revert source bytes");
    assert!(
        git_stdout(
            fixture.root(),
            ["diff", "--name-only", &fixture.candidate_commit, "HEAD"]
        )
        .is_empty(),
        "the endpoint diff intentionally hides the intervening source change"
    );

    let report = classify_ambient_repository_relationship(
        fixture.root(),
        &fixture.candidate_commit,
        &[REVIEW_EVIDENCE_PATH.to_owned()],
    );

    assert_eq!(
        report.classification,
        AmbientRepositoryRelationshipKind::SourceAffectingDivergence
    );
    assert_eq!(report.intervening_commits.len(), 2);
    assert_eq!(
        report.source_affecting_paths[0].utf8.as_deref(),
        Some("src/Example.java")
    );
}

struct RelationshipFixture {
    repository: tempfile::TempDir,
    base_commit: String,
    candidate_commit: String,
}

impl RelationshipFixture {
    fn root(&self) -> &Path {
        self.repository.path()
    }
}

fn relationship_repository() -> RelationshipFixture {
    let repository = tempfile::tempdir().expect("temporary Git repository");
    git(repository.path(), ["init", "--quiet"]);
    git(repository.path(), ["config", "user.name", "SFM Test"]);
    git(
        repository.path(),
        ["config", "user.email", "sfm-test@example.invalid"],
    );
    write(repository.path(), "README.md", b"base\n");
    commit_all(repository.path(), "base");
    let base_commit = git_stdout(repository.path(), ["rev-parse", "HEAD"]);
    write(
        repository.path(),
        "src/Example.java",
        b"class Example { int candidate = 1; }\n",
    );
    commit_all(repository.path(), "candidate");
    let candidate_commit = git_stdout(repository.path(), ["rev-parse", "HEAD"]);
    RelationshipFixture {
        repository,
        base_commit,
        candidate_commit,
    }
}

fn commit_all(root: &Path, message: &str) {
    git(root, ["add", "--all"]);
    git(root, ["commit", "--quiet", "-m", message]);
}

#[test]
fn produces_complete_deterministic_domain_for_real_git_changes() {
    let (repository, before_commit, candidate_commit, branch_before) = changed_repository();
    let first = produce_git_review_domain(repository.path(), "review-before", "HEAD")
        .expect("Git domain should be produced");
    let second = produce_git_review_domain(repository.path(), "review-before", "HEAD")
        .expect("deterministic rerun should succeed");

    assert_eq!(
        first, second,
        "rerunning against the same commits must be stable"
    );
    assert_eq!(first.before.commit_id, before_commit);
    assert_eq!(first.candidate.commit_id, candidate_commit);
    assert_repository_was_not_mutated(repository.path(), &candidate_commit, &branch_before);
    assert_change_shapes(&first);
    assert_complete_reconciliation(&first);
}

fn changed_repository() -> (tempfile::TempDir, String, String, String) {
    let repository = tempfile::tempdir().expect("temporary Git repository");
    git(repository.path(), ["init", "--quiet"]);
    git(repository.path(), ["config", "user.name", "SFM Test"]);
    git(
        repository.path(),
        ["config", "user.email", "sfm-test@example.invalid"],
    );

    write(repository.path(), "delete.txt", b"deleted content\n");
    write(repository.path(), "modify.txt", b"before\nshared\n");
    write(repository.path(), "rename-old.txt", b"unique rename body\n");
    write(repository.path(), "copy-source.txt", b"stable copy body\n");
    write(repository.path(), "binary.bin", b"before\0binary\x01");
    write(repository.path(), "white space.txt", b"alpha\nbeta\n");
    git(repository.path(), ["add", "--all"]);
    git(repository.path(), ["commit", "--quiet", "-m", "before"]);
    git(repository.path(), ["tag", "review-before"]);
    let before_commit = git_stdout(repository.path(), ["rev-parse", "HEAD"]);

    fs::remove_file(repository.path().join("delete.txt")).expect("delete fixture file");
    write(
        repository.path(),
        "add.txt",
        b"brand new content\nsecond line\n",
    );
    write(repository.path(), "modify.txt", b"after\nshared\nextra\n");
    fs::rename(
        repository.path().join("rename-old.txt"),
        repository.path().join("rename-new.txt"),
    )
    .expect("rename fixture file");
    fs::copy(
        repository.path().join("copy-source.txt"),
        repository.path().join("copy-destination.txt"),
    )
    .expect("copy fixture file");
    write(repository.path(), "binary.bin", b"after\0binary\x02");
    write(repository.path(), "white space.txt", b"alpha  \nbeta\n");
    git(repository.path(), ["add", "--all"]);
    git(repository.path(), ["commit", "--quiet", "-m", "candidate"]);
    let candidate_commit = git_stdout(repository.path(), ["rev-parse", "HEAD"]);
    let branch_before = git_stdout(repository.path(), ["symbolic-ref", "--short", "HEAD"]);
    (repository, before_commit, candidate_commit, branch_before)
}

fn assert_repository_was_not_mutated(root: &Path, candidate_commit: &str, branch_before: &str) {
    assert_eq!(
        git_stdout(root, ["rev-parse", "HEAD"]),
        candidate_commit,
        "producer must not move HEAD"
    );
    assert_eq!(
        git_stdout(root, ["symbolic-ref", "--short", "HEAD"]),
        branch_before,
        "producer must not change the checked-out branch"
    );
    assert!(git_stdout(root, ["status", "--porcelain"]).is_empty());
}

fn assert_change_shapes(domain: &release_review_git::GitReviewDomain) {
    let added = change_with_new_path(domain, "add.txt");
    assert_eq!(added.status.kind, GitChangeKind::Added);
    assert!(
        added.old_path.is_none(),
        "add must retain an empty before side"
    );
    assert!(added.old_mode.is_none());
    assert!(added.old_blob_id.is_none());
    assert!(!added.hunks.is_empty());

    let deleted = change_with_old_path(domain, "delete.txt");
    assert_eq!(deleted.status.kind, GitChangeKind::Deleted);
    assert!(
        deleted.new_path.is_none(),
        "delete must retain an empty candidate side"
    );
    assert!(deleted.new_mode.is_none());
    assert!(deleted.new_blob_id.is_none());
    assert!(!deleted.hunks.is_empty());

    let modified = change_with_new_path(domain, "modify.txt");
    assert_eq!(modified.status.kind, GitChangeKind::Modified);
    assert!(modified.old_blob_id.is_some());
    assert!(modified.new_blob_id.is_some());
    assert!(!modified.hunks.is_empty());
    assert!(!modified.numstat.as_ref().unwrap().binary);

    let renamed = change_with_new_path(domain, "rename-new.txt");
    assert_eq!(renamed.status.kind, GitChangeKind::Renamed);
    assert_eq!(
        renamed.old_path.as_ref().unwrap().as_utf8(),
        Some("rename-old.txt")
    );
    assert_eq!(renamed.status.similarity_percent, Some(100));

    let copied = change_with_new_path(domain, "copy-destination.txt");
    assert_eq!(copied.status.kind, GitChangeKind::Copied);
    assert_eq!(
        copied.old_path.as_ref().unwrap().as_utf8(),
        Some("copy-source.txt")
    );
    assert_eq!(copied.status.similarity_percent, Some(100));

    let binary = change_with_new_path(domain, "binary.bin");
    assert!(binary.numstat.as_ref().unwrap().binary);
    assert!(binary.hunks.is_empty());
    assert!(binary.unit_ids.iter().any(|unit_id| {
        domain.units.iter().any(|unit| {
            &unit.unit_id == unit_id && matches!(unit.kind, GitReviewUnitKind::BinaryFile)
        })
    }));

    let whitespace = change_with_new_path(domain, "white space.txt");
    assert_eq!(whitespace.status.kind, GitChangeKind::Modified);
    assert!(!whitespace.hunks.is_empty());
}

fn assert_complete_reconciliation(domain: &release_review_git::GitReviewDomain) {
    assert!(domain.reconciliation.complete);
    assert_eq!(domain.reconciliation.raw_change_count, domain.changes.len());
    assert_eq!(domain.reconciliation.unreconciled_side_path_count, 0);
    assert_eq!(
        domain.reconciliation.explicitly_unsupported_side_path_count,
        0
    );
    assert_eq!(
        domain.reconciliation.represented_side_path_count,
        domain.reconciliation.raw_side_path_count
    );
    assert!(domain.reconciliation.paths.iter().all(|path| {
        matches!(path.disposition, GitReconciliationDisposition::Represented)
            && !path.unit_ids.is_empty()
    }));
    assert!(
        domain
            .changes
            .iter()
            .all(|change| change.change_id.starts_with("git-change:blake3:")
                && !change.unit_ids.is_empty())
    );
    assert!(
        domain
            .units
            .iter()
            .all(|unit| unit.unit_id.starts_with("git-unit:blake3:"))
    );
}

fn change_with_new_path<'a>(
    domain: &'a release_review_git::GitReviewDomain,
    path: &str,
) -> &'a release_review_git::GitDomainChange {
    domain
        .changes
        .iter()
        .find(|change| {
            change
                .new_path
                .as_ref()
                .and_then(release_review_git::GitPath::as_utf8)
                == Some(path)
        })
        .unwrap_or_else(|| panic!("missing candidate path {path}"))
}

fn change_with_old_path<'a>(
    domain: &'a release_review_git::GitReviewDomain,
    path: &str,
) -> &'a release_review_git::GitDomainChange {
    domain
        .changes
        .iter()
        .find(|change| {
            change
                .old_path
                .as_ref()
                .and_then(release_review_git::GitPath::as_utf8)
                == Some(path)
        })
        .unwrap_or_else(|| panic!("missing before path {path}"))
}

fn write(root: &Path, relative: &str, contents: &[u8]) {
    let path = root.join(relative);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).expect("create fixture parent");
    }
    fs::write(path, contents).unwrap_or_else(|error| panic!("write {relative}: {error}"));
}

fn git<const N: usize>(root: &Path, arguments: [&str; N]) -> Output {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(arguments)
        .output()
        .expect("Git should launch without a shell");
    assert!(
        output.status.success(),
        "Git failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    output
}

fn git_stdout<const N: usize>(root: &Path, arguments: [&str; N]) -> String {
    String::from_utf8(git(root, arguments).stdout)
        .expect("Git output should be UTF-8")
        .trim()
        .to_owned()
}
