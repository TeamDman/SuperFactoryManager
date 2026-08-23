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
