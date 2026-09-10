//! Commit-before / captured-files-after source adapter. No Git writes or live comment targets.

use crate::cancellation::CancellationToken;
use crate::release_review_capture::WorkingTreeCaptureV1;
use crate::release_review_capture::WorkingTreeEntryV1;
use crate::release_review_capture::{self};
use crate::release_review_capture_io::CaptureBudget;
use crate::release_review_capture_io::DiskFile;
use crate::release_review_capture_io::FILE_LIMIT;
use crate::release_review_capture_io::TEXT_LIMIT;
use crate::release_review_capture_io::TOTAL_LIMIT;
use crate::release_review_capture_io::read_disk;
use crate::review_session_v1::sha256;
use eyre::Context as _;
use eyre::eyre;
use sha1::Digest as _;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;

const PATH_LIMIT: usize = 100_000;
const LIST_LIMIT: u64 = 32 * 1024 * 1024;

#[derive(Clone, Debug)]
pub struct WorkingTreeReviewConfig {
    pub repository_root: PathBuf,
    pub lane_id: String,
    pub repository_id: String,
    pub before: String,
    pub scope_paths: Vec<String>,
    pub excluded_paths: Vec<String>,
    pub include_untracked: bool,
    pub review_evidence_path: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct GitEntry {
    pub mode: String,
    pub object: String,
}

impl GitEntry {
    fn regular(&self) -> bool {
        matches!(self.mode.as_str(), "100644" | "100755")
    }
}

#[derive(Debug)]
pub struct WorkingTreeReviewInputs {
    pub capture: WorkingTreeCaptureV1,
    pub before_commit: String,
    pub before_tree: String,
    pub(crate) before_entries: BTreeMap<String, GitEntry>,
    pub(crate) before_bytes: BTreeMap<String, Vec<u8>>,
    pub(crate) after_text: BTreeMap<String, String>,
}

struct Observation {
    head: String,
    tree: String,
    index_hash: String,
    entries: Vec<WorkingTreeEntryV1>,
    exclusions: Vec<String>,
    texts: BTreeMap<String, String>,
    bytes_read: u64,
}

/// Read two agreeing observations, then immutable before objects. The returned
/// evidence is detached from the checkout and can be materialized without IO.
///
/// # Errors
/// Refuses unsafe paths, conflicts, races, missing authority, cancellation and resource limits.
pub fn capture(
    config: &WorkingTreeReviewConfig,
    cancellation: &CancellationToken,
) -> eyre::Result<WorkingTreeReviewInputs> {
    capture_with_between_passes(config, cancellation, || {})
}

fn capture_with_between_passes(
    config: &WorkingTreeReviewConfig,
    cancellation: &CancellationToken,
    between_passes: impl FnOnce(),
) -> eyre::Result<WorkingTreeReviewInputs> {
    let budget = CaptureBudget::new(cancellation);
    capture_observed(config, &budget, between_passes, true)
}

/// Compare only the declared capture scope to disk; changes outside it are irrelevant.
///
/// # Errors
/// Returns unavailable on unsafe source state, conflicting edits, cancellation or deadline.
pub fn scope_matches(
    config: &WorkingTreeReviewConfig,
    saved: &WorkingTreeCaptureV1,
    cancellation: &CancellationToken,
) -> eyre::Result<(bool, String)> {
    saved.validate()?;
    let budget = CaptureBudget::with_timeout(cancellation, std::time::Duration::from_secs(20));
    let mut now = capture_observed(config, &budget, || {}, false)?.capture;
    // Commit identity is reported independently. Derived document IDs and clocks
    // are not part of the content projection; the original baseline still is.
    let head = now.observed_head_commit.clone();
    now.observed_head_commit
        .clone_from(&saved.observed_head_commit);
    now.observed_head_tree.clone_from(&saved.observed_head_tree);
    Ok((now.computed_id() == saved.id, head))
}

#[expect(
    clippy::too_many_lines,
    reason = "keep the two-pass observation and publication preconditions in one auditable sequence"
)]
fn capture_observed(
    config: &WorkingTreeReviewConfig,
    budget: &CaptureBudget,
    between_passes: impl FnOnce(),
    include_before_bytes: bool,
) -> eyre::Result<WorkingTreeReviewInputs> {
    let root =
        dunce::canonicalize(&config.repository_root).wrap_err("resolve capture repository")?;
    let git_root = budget.git(&root, &["rev-parse", "--show-toplevel"], None, 16 * 1024)?;
    let git_root = PathBuf::from(std::str::from_utf8(&git_root)?.trim());
    if dunce::canonicalize(&git_root)? != root {
        return Err(eyre!("capture root must be the Git top-level"));
    }
    if config.lane_id.trim().is_empty() || config.repository_id.trim().is_empty() {
        return Err(eyre!("capture lane/repository must not be blank"));
    }
    let scopes = canonical_prefixes(config.scope_paths.clone())?;
    if scopes.is_empty() {
        return Err(eyre!("working-tree capture requires an explicit --scope"));
    }
    release_review_capture::validate_path(&config.review_evidence_path, false)?;
    let mut exclusions = config.excluded_paths.clone();
    exclusions.push(config.review_evidence_path.clone());
    let exclusions = canonical_prefixes(exclusions)?;
    let before_commit = resolve(budget, &root, &config.before, "commit")?;
    let before_tree = resolve(budget, &root, &before_commit, "tree")?;
    let before_entries = tree_entries(budget, &root, &before_commit, &scopes)?;
    let first = observe(
        budget,
        &root,
        &scopes,
        &exclusions,
        config.include_untracked,
        &before_entries,
    )?;
    between_passes();
    let second = observe(
        budget,
        &root,
        &scopes,
        &exclusions,
        config.include_untracked,
        &before_entries,
    )?;
    if first.head != second.head
        || first.tree != second.tree
        || first.index_hash != second.index_hash
        || first.entries != second.entries
        || first.exclusions != second.exclusions
    {
        return Err(eyre!(
            "working-tree changed between capture passes; no review was created"
        ));
    }
    let mut manifest = WorkingTreeCaptureV1 {
        schema: release_review_capture::SCHEMA.into(),
        id: String::new(),
        observed_head_commit: second.head,
        observed_head_tree: second.tree,
        captured_at_unix_ms: std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)?
            .as_millis()
            .try_into()?,
        consistency: "verified_two_pass".into(),
        scope_paths: scopes,
        excluded_paths: second.exclusions,
        include_untracked: config.include_untracked,
        entries: second.entries,
    };
    manifest.id = manifest.computed_id();
    for entry in &mut manifest.entries {
        if entry.materialization == "utf8" {
            entry.document_revision_id = Some(document_id(
                config,
                &manifest.id,
                "after",
                &entry.path,
                entry.sha256.as_deref().expect("regular file hash"),
            ));
        }
    }
    manifest.validate()?;
    // Only changed paths need embedded before bytes; unchanged files remain hash-only scope evidence.
    let changed = manifest
        .entries
        .iter()
        .filter(|e| e.materialization != "unchanged")
        .filter_map(|entry| {
            before_entries
                .get(&entry.path)
                .filter(|old| old.regular())
                .map(|old| (entry.path.clone(), old.clone()))
        })
        .collect::<BTreeMap<_, _>>();
    let before_bytes = if include_before_bytes {
        read_blobs(
            budget,
            &root,
            &changed,
            first.bytes_read.max(second.bytes_read),
        )?
    } else {
        BTreeMap::new()
    };
    budget.check()?;
    Ok(WorkingTreeReviewInputs {
        capture: manifest,
        before_commit,
        before_tree,
        before_entries,
        before_bytes,
        after_text: second.texts,
    })
}

pub(crate) fn document_id(
    config: &WorkingTreeReviewConfig,
    source: &str,
    side: &str,
    path: &str,
    hash: &str,
) -> String {
    let digest = sha256(
        format!(
            "{}\0{}\0{source}\0{side}\0{path}\0{hash}",
            config.repository_id, config.lane_id
        )
        .as_bytes(),
    );
    format!("review-document:sha256:{digest}")
}

fn resolve(
    budget: &CaptureBudget,
    root: &Path,
    revision: &str,
    kind: &str,
) -> eyre::Result<String> {
    let reference = format!("{revision}^{{{kind}}}");
    let bytes = budget.git(
        root,
        &["rev-parse", "--verify", "--end-of-options", &reference],
        None,
        256,
    )?;
    let value = std::str::from_utf8(&bytes)?.trim();
    if value.len() != 40
        || !value
            .bytes()
            .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
    {
        return Err(eyre!("capture requires a resolved SHA-1 Git {kind}"));
    }
    Ok(value.into())
}

fn scoped_git(
    budget: &CaptureBudget,
    root: &Path,
    arguments: &[&str],
    scopes: &[String],
) -> eyre::Result<Vec<u8>> {
    let mut args = arguments.to_vec();
    args.push("--");
    args.extend(scopes.iter().map(String::as_str));
    budget.git(root, &args, None, LIST_LIMIT)
}

fn tree_entries(
    budget: &CaptureBudget,
    root: &Path,
    commit: &str,
    scopes: &[String],
) -> eyre::Result<BTreeMap<String, GitEntry>> {
    let bytes = scoped_git(
        budget,
        root,
        &["ls-tree", "-r", "-z", "--full-tree", commit],
        scopes,
    )?;
    let mut entries = BTreeMap::new();
    for record in records(&bytes)? {
        let (header, path) = record
            .split_once('\t')
            .ok_or_else(|| eyre!("invalid Git tree entry"))?;
        let fields = header.split(' ').collect::<Vec<_>>();
        if fields.len() != 3 {
            return Err(eyre!("invalid Git tree header"));
        }
        release_review_capture::validate_path(path, false)?;
        entries.insert(
            path.to_owned(),
            GitEntry {
                mode: fields[0].into(),
                object: fields[2].into(),
            },
        );
    }
    Ok(entries)
}

fn index_entries(bytes: &[u8]) -> eyre::Result<BTreeMap<String, GitEntry>> {
    let mut entries = BTreeMap::new();
    for record in records(bytes)? {
        let (header, path) = record
            .split_once('\t')
            .ok_or_else(|| eyre!("invalid Git index entry"))?;
        let fields = header.split(' ').collect::<Vec<_>>();
        if fields.len() != 3 || fields[2] != "0" {
            return Err(eyre!(
                "working-tree capture refuses unmerged/conflicted index entries"
            ));
        }
        release_review_capture::validate_path(path, false)?;
        if entries
            .insert(
                path.into(),
                GitEntry {
                    mode: fields[0].into(),
                    object: fields[1].into(),
                },
            )
            .is_some()
        {
            return Err(eyre!("duplicate Git index path"));
        }
    }
    Ok(entries)
}

fn records(bytes: &[u8]) -> eyre::Result<Vec<&str>> {
    let text =
        std::str::from_utf8(bytes).wrap_err("capture cannot represent non-UTF-8 Git paths")?;
    if !text.is_empty() && !text.ends_with('\0') {
        return Err(eyre!("truncated Git path listing"));
    }
    let values = text.split_terminator('\0').collect::<Vec<_>>();
    if values.len() > PATH_LIMIT {
        return Err(eyre!("capture exceeds {PATH_LIMIT} enumerated paths"));
    }
    Ok(values)
}

#[expect(
    clippy::too_many_lines,
    reason = "one bounded enumeration and per-path evidence pass keeps authority checks adjacent to disk reads"
)]
fn observe(
    budget: &CaptureBudget,
    root: &Path,
    scopes: &[String],
    exclusions: &[String],
    untracked: bool,
    before: &BTreeMap<String, GitEntry>,
) -> eyre::Result<Observation> {
    let head = resolve(budget, root, "HEAD", "commit")?;
    let tree = resolve(budget, root, &head, "tree")?;
    let index_bytes = scoped_git(budget, root, &["ls-files", "--stage", "-z"], scopes)?;
    let index = index_entries(&index_bytes)?;
    let mut paths = before
        .keys()
        .chain(index.keys())
        .cloned()
        .collect::<BTreeSet<_>>();
    let others = if untracked {
        scoped_git(
            budget,
            root,
            &["ls-files", "--others", "--exclude-standard", "-z"],
            scopes,
        )?
    } else {
        Vec::new()
    };
    if untracked {
        for path in records(&others)? {
            release_review_capture::validate_path(path, false)?;
            paths.insert(path.into());
        }
    }
    if paths.len() > PATH_LIMIT {
        return Err(eyre!("capture exceeds {PATH_LIMIT} combined paths"));
    }
    let mut exclusions = exclusions.to_vec();
    for path in &paths {
        if !index.contains_key(path) && !before.contains_key(path) && sensitive_untracked_name(path)
        {
            exclusions.push(path.clone());
        }
    }
    let exclusions = canonical_prefixes(exclusions)?;
    let scope_set = scopes.iter().map(String::as_str).collect::<BTreeSet<_>>();
    let excluded_set = exclusions
        .iter()
        .map(String::as_str)
        .collect::<BTreeSet<_>>();
    paths.retain(|path| within(&scope_set, path) && !within(&excluded_set, path));
    validate_windows_aliases(&paths)?;
    let mut entries = Vec::new();
    let mut texts = BTreeMap::new();
    let mut bytes_read = 0;
    for path in paths {
        budget.check()?;
        let old = before.get(&path);
        let indexed = index.get(&path);
        let tracked = old.is_some() || indexed.is_some();
        let mut entry = WorkingTreeEntryV1 {
            path: path.clone(),
            kind: "regular_file".into(),
            tracked,
            byte_length: None,
            sha256: None,
            executable: false,
            materialization: "unavailable".into(),
            document_revision_id: None,
            diagnostic: None,
        };
        if indexed.or(old).is_some_and(|entry| entry.mode == "160000") {
            entry.kind = "gitlink".into();
            entry.diagnostic = Some("Submodule/gitlink source is not followed".into());
        } else {
            match read_disk(root, &path, budget)? {
                DiskFile::Missing => {
                    if !tracked {
                        return Err(eyre!("untracked source disappeared during capture: {path}"));
                    }
                    entry.kind = "deleted".into();
                    entry.diagnostic = Some("File is absent on disk".into());
                }
                DiskFile::Unsupported(diagnostic) => {
                    entry.kind = "symlink".into();
                    entry.diagnostic = Some(diagnostic);
                }
                DiskFile::Regular { bytes, executable } => {
                    bytes_read += bytes.len() as u64;
                    if bytes_read > TOTAL_LIMIT {
                        return Err(eyre!(
                            "capture exceeds {TOTAL_LIMIT} aggregate bytes at {path}"
                        ));
                    }
                    // Windows has no Unix executable bit. Preserve Git's indexed mode; never use staged file contents.
                    entry.executable = if cfg!(windows) {
                        indexed.or(old).is_some_and(|entry| entry.mode == "100755")
                    } else {
                        executable
                    };
                    entry.byte_length = Some(bytes.len() as u64);
                    entry.sha256 = Some(sha256(&bytes));
                    let mut blob = Sha1::new();
                    blob.update(format!("blob {}\0", bytes.len()).as_bytes());
                    blob.update(&bytes);
                    let blob = format!("{:x}", blob.finalize());
                    let unchanged = old.is_some_and(|old| {
                        old.regular()
                            && old.object == blob
                            && (old.mode == "100755") == entry.executable
                    });
                    if unchanged {
                        entry.materialization = "unchanged".into();
                    } else if bytes.len() > TEXT_LIMIT {
                        entry.materialization = "oversized".into();
                        entry.diagnostic =
                            Some(format!("Text preview exceeds {TEXT_LIMIT}-byte limit"));
                    } else if let Ok(text) = std::str::from_utf8(&bytes)
                        && !bytes.contains(&0)
                    {
                        entry.materialization = "utf8".into();
                        entry.document_revision_id = Some("pending-derived-id".into());
                        texts.insert(path.clone(), text.into());
                    } else {
                        entry.materialization = "binary".into();
                        entry.diagnostic = Some("File is binary or not valid UTF-8 text".into());
                    }
                }
            }
        }
        entries.push(entry);
    }
    // Re-observe HEAD and index after the file reads within each pass as well.
    if resolve(budget, root, "HEAD", "commit")? != head
        || scoped_git(budget, root, &["ls-files", "--stage", "-z"], scopes)? != index_bytes
        || (untracked
            && scoped_git(
                budget,
                root,
                &["ls-files", "--others", "--exclude-standard", "-z"],
                scopes,
            )? != others)
    {
        return Err(eyre!(
            "HEAD/index/untracked paths changed during working-tree capture"
        ));
    }
    Ok(Observation {
        head,
        tree,
        index_hash: sha256(&index_bytes),
        entries,
        exclusions,
        texts,
        bytes_read,
    })
}

fn read_blobs(
    budget: &CaptureBudget,
    root: &Path,
    paths: &BTreeMap<String, GitEntry>,
    mut total: u64,
) -> eyre::Result<BTreeMap<String, Vec<u8>>> {
    let objects = paths
        .values()
        .map(|entry| entry.object.as_str())
        .collect::<BTreeSet<_>>();
    if objects.is_empty() {
        return Ok(BTreeMap::new());
    }
    let mut input = Vec::new();
    for id in &objects {
        input.extend_from_slice(id.as_bytes());
        input.push(b'\n');
    }
    // Read sizes first so a huge before object is never fetched merely to discover its limit.
    let sizes = budget.git(
        root,
        &["cat-file", "--batch-check"],
        Some(input.clone()),
        LIST_LIMIT,
    )?;
    let mut lengths = BTreeMap::new();
    for line in std::str::from_utf8(&sizes)?.lines() {
        let fields = line.split(' ').collect::<Vec<_>>();
        if fields.len() != 3 || fields[1] != "blob" || !objects.contains(fields[0]) {
            return Err(eyre!("unexpected Git blob size response"));
        }
        let length: u64 = fields[2].parse()?;
        if length > FILE_LIMIT {
            return Err(eyre!(
                "before blob {} exceeds {FILE_LIMIT}-byte read limit",
                fields[0]
            ));
        }
        lengths.insert(fields[0], length);
    }
    if lengths.len() != objects.len() {
        return Err(eyre!("missing before blob size evidence"));
    }
    for (path, entry) in paths {
        total += lengths[entry.object.as_str()];
        if total > TOTAL_LIMIT {
            return Err(eyre!(
                "capture including before bytes exceeds {TOTAL_LIMIT} at {path}"
            ));
        }
    }
    let output = budget.git(
        root,
        &["cat-file", "--batch"],
        Some(input),
        TOTAL_LIMIT + LIST_LIMIT,
    )?;
    let mut cursor = output.as_slice();
    let mut bodies = BTreeMap::new();
    for expected in objects {
        let end = cursor
            .iter()
            .position(|b| *b == b'\n')
            .ok_or_else(|| eyre!("truncated Git blob header"))?;
        let fields = std::str::from_utf8(&cursor[..end])?
            .split(' ')
            .collect::<Vec<_>>();
        if fields.len() != 3 || fields[0] != expected || fields[1] != "blob" {
            return Err(eyre!("unexpected Git blob response"));
        }
        let size = fields[2].parse::<usize>()?;
        if size as u64 > FILE_LIMIT {
            return Err(eyre!("before blob exceeds {FILE_LIMIT}-byte read limit"));
        }
        cursor = &cursor[end + 1..];
        if cursor.len() <= size || cursor[size] != b'\n' {
            return Err(eyre!("truncated Git blob body"));
        }
        bodies.insert(expected, cursor[..size].to_vec());
        cursor = &cursor[size + 1..];
    }
    if !cursor.is_empty() {
        return Err(eyre!("unexpected trailing Git blob data"));
    }
    let mut result = BTreeMap::new();
    for (path, entry) in paths {
        let bytes = &bodies[entry.object.as_str()];
        result.insert(path.clone(), bytes.clone());
    }
    Ok(result)
}

fn canonical_prefixes(paths: Vec<String>) -> eyre::Result<Vec<String>> {
    if paths.len() > PATH_LIMIT {
        return Err(eyre!("too many capture scope/exclusion paths"));
    }
    let paths = paths.into_iter().collect::<BTreeSet<_>>();
    let mut result = Vec::new();
    let mut seen = BTreeSet::new();
    for path in &paths {
        release_review_capture::validate_path(path, true)?;
        if !within(&seen, path) {
            result.push(path.clone());
            seen.insert(path.as_str());
        }
    }
    Ok(result)
}

fn within(prefixes: &BTreeSet<&str>, path: &str) -> bool {
    prefixes.contains(".")
        || prefixes.contains(path)
        || path
            .match_indices('/')
            .any(|(i, _)| prefixes.contains(&path[..i]))
}

#[expect(
    clippy::case_sensitive_file_extension_comparisons,
    reason = "basename is explicitly ASCII-lowercased before all policy comparisons"
)]
fn sensitive_untracked_name(path: &str) -> bool {
    let name = path.rsplit('/').next().unwrap_or(path).to_ascii_lowercase();
    name == ".env"
        || name.starts_with(".env.")
        || name.ends_with(".pem")
        || name.ends_with(".key")
        || matches!(
            name.as_str(),
            "id_rsa" | "id_ed25519" | "credentials" | "credentials.json" | "credentials.toml"
        )
}

fn validate_windows_aliases(paths: &BTreeSet<String>) -> eyre::Result<()> {
    if !cfg!(windows) {
        return Ok(());
    }
    let mut seen = BTreeSet::new();
    for path in paths {
        if !seen.insert(path.to_lowercase())
            || path.split('/').any(|part| part.ends_with(['.', ' ']))
        {
            return Err(eyre!(
                "capture refuses case-colliding or normalized Windows path alias: {path}"
            ));
        }
    }
    Ok(())
}

#[cfg(all(test, windows))]
mod tests {
    use super::*;
    use std::process::Command;

    fn git(root: &Path, args: &[&str]) -> Vec<u8> {
        let output = Command::new("git")
            .arg("-C")
            .arg(root)
            .args(args)
            .env("GIT_OPTIONAL_LOCKS", "0")
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {args:?}: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        output.stdout
    }

    fn fixture() -> (tempfile::TempDir, WorkingTreeReviewConfig) {
        let root = tempfile::tempdir().unwrap();
        git(root.path(), &["init", "--quiet"]);
        git(root.path(), &["config", "user.name", "SFM capture test"]);
        git(
            root.path(),
            &["config", "user.email", "sfm-capture@example.invalid"],
        );
        std::fs::create_dir(root.path().join("src")).unwrap();
        for (name, text) in [
            ("A.java", "class A { int old; }\n"),
            ("Keep.java", "class Keep {}\n"),
            ("Removed.java", "class Removed {}\n"),
        ] {
            std::fs::write(root.path().join("src").join(name), text).unwrap();
        }
        std::fs::write(root.path().join(".gitignore"), "src/ignored.txt\n").unwrap();
        git(root.path(), &["add", "."]);
        git(
            root.path(),
            &["commit", "--quiet", "-m", "capture fixture baseline"],
        );
        let config = WorkingTreeReviewConfig {
            repository_root: root.path().to_owned(),
            lane_id: "1.19.2".into(),
            repository_id: "fixture".into(),
            before: "HEAD".into(),
            scope_paths: vec!["src".into()],
            excluded_paths: Vec::new(),
            include_untracked: true,
            review_evidence_path: "review.sfm-review.json".into(),
        };
        (root, config)
    }

    #[test]
    fn captured_files_use_disk_not_index_and_preserve_git_and_source_bytes() {
        let (root, config) = fixture();
        std::fs::write(root.path().join("src/A.java"), "class A { int staged; }\n").unwrap();
        git(root.path(), &["add", "src/A.java"]);
        std::fs::write(
            root.path().join("src/A.java"),
            "class A { int finalDisk; }\n",
        )
        .unwrap();
        std::fs::remove_file(root.path().join("src/Removed.java")).unwrap();
        std::fs::write(
            root.path().join("src/New.java"),
            "class New { String café; }\n",
        )
        .unwrap();
        std::fs::write(root.path().join("src/Raw.bin"), [0, 1, 2]).unwrap();
        std::fs::write(root.path().join("src/ignored.txt"), "ignored sentinel").unwrap();
        std::fs::write(root.path().join("src/.env"), "secret sentinel").unwrap();
        let index_before = std::fs::read(root.path().join(".git/index")).unwrap();
        let head_before = git(root.path(), &["rev-parse", "HEAD"]);
        let captured = capture(&config, &CancellationToken::new()).unwrap();
        assert_eq!(
            captured.after_text["src/A.java"],
            "class A { int finalDisk; }\n"
        );
        assert_eq!(
            captured.before_bytes["src/A.java"],
            b"class A { int old; }\n"
        );
        assert!(
            captured
                .capture
                .entries
                .iter()
                .any(|entry| entry.path == "src/New.java" && !entry.tracked)
        );
        assert!(
            captured
                .capture
                .entries
                .iter()
                .any(|entry| entry.path == "src/Removed.java" && entry.kind == "deleted")
        );
        assert!(
            captured
                .capture
                .entries
                .iter()
                .any(|entry| entry.path == "src/Keep.java" && entry.materialization == "unchanged")
        );
        assert!(
            captured
                .capture
                .entries
                .iter()
                .any(|entry| entry.path == "src/Raw.bin" && entry.materialization == "binary")
        );
        assert!(
            !captured
                .capture
                .entries
                .iter()
                .any(|entry| entry.path.ends_with(".env") || entry.path.ends_with("ignored.txt"))
        );
        assert!(captured.capture.excluded_paths.contains(&"src/.env".into()));
        assert_eq!(
            index_before,
            std::fs::read(root.path().join(".git/index")).unwrap()
        );
        assert_eq!(head_before, git(root.path(), &["rev-parse", "HEAD"]));
        assert_eq!(
            std::fs::read_to_string(root.path().join("src/A.java")).unwrap(),
            captured.after_text["src/A.java"]
        );
        assert!(!root.path().join("review.sfm-review.json").exists());
        let again = capture(&config, &CancellationToken::new()).unwrap();
        assert_eq!(captured.capture.id, again.capture.id);
        assert!(
            scope_matches(&config, &captured.capture, &CancellationToken::new())
                .unwrap()
                .0,
            "dirty tracked and untracked bytes are current when they match the capture"
        );
        std::fs::write(root.path().join("outside.txt"), "not in scope").unwrap();
        git(root.path(), &["add", "outside.txt"]);
        git(root.path(), &["commit", "--quiet", "-m", "outside scope"]);
        assert!(
            scope_matches(&config, &captured.capture, &CancellationToken::new())
                .unwrap()
                .0,
            "an outside-scope commit does not stale captured source bytes"
        );
        std::fs::write(root.path().join("src/A.java"), "changed later").unwrap();
        assert!(
            !scope_matches(&config, &captured.capture, &CancellationToken::new())
                .unwrap()
                .0
        );
        assert_eq!(
            captured.after_text["src/A.java"], "class A { int finalDisk; }\n",
            "saved evidence must be detached from disk"
        );
        assert_ne!(
            captured.capture.id,
            capture(&config, &CancellationToken::new())
                .unwrap()
                .capture
                .id
        );
        let captured_id = captured.capture.id.clone();
        let review =
            crate::release_review_working_tree_materialize::materialize(&config, captured).unwrap();
        let observation =
            crate::release_review_working_tree_materialize::materialize_observation(&config, again)
                .unwrap();
        assert!(observation.review_session.comments.is_empty());
        assert!(observation.review_session.style_rules.is_empty());
        assert!(!review.review_units.is_empty());
        assert_eq!(review.review_units, observation.review_units);
        assert_eq!(review.corpus_documents, observation.corpus_documents);
        assert_eq!(
            review.review_session.revision_lanes,
            observation.review_session.revision_lanes
        );
        for expression in [
            format!("1.19.2 {captured_id}"),
            format!("(1.19.2 {captured_id}) difference effective(#approved)"),
            format!("effective(#approved) intersect 1.19.2 {captured_id}"),
        ] {
            assert_eq!(
                crate::release_review_v1::query(&review, &expression).unwrap(),
                crate::release_review_v1::query(&observation, &expression).unwrap(),
                "synthetic comments must not determine working-tree coverage"
            );
        }
        assert_eq!(review.schema, crate::release_review_v1::WORKING_TREE_SCHEMA);
        assert_eq!(
            review.review_session.revision_lanes[0].after.id,
            captured_id
        );
        assert!(
            review
                .review_units
                .iter()
                .any(|unit| unit.path_after.as_deref() == Some("src/New.java"))
        );
        assert!(
            review
                .review_units
                .iter()
                .any(
                    |unit| unit.path_before.as_deref() == Some("src/Removed.java")
                        && unit.path_after.is_none()
                )
        );
        assert_eq!(
            review.review_session.revision_lanes[0]
                .after
                .documents
                .iter()
                .find(|doc| doc.path == "src/A.java")
                .unwrap()
                .text,
            "class A { int finalDisk; }\n",
            "materialization may not reread newer disk contents"
        );
        assert!(
            crate::release_review_v1::query(
                &review,
                &format!("effective(#approved) intersect 1.19.2 {captured_id}")
            )
            .unwrap()
            .review_unit_ids
            .is_empty(),
            "new captures cannot inherit approval"
        );
        let json = crate::release_review_v1::to_canonical_json(&review).unwrap();
        assert_eq!(crate::release_review_v1::parse(&json).unwrap(), review);
        assert!(
            !crate::release_review_v1::query(&review, "unsupported")
                .unwrap()
                .review_unit_ids
                .is_empty()
        );
    }

    #[test]
    fn a_changed_file_or_new_untracked_path_between_passes_refuses_capture() {
        let (root, config) = fixture();
        let result = capture_with_between_passes(&config, &CancellationToken::new(), || {
            std::fs::write(root.path().join("src/A.java"), "concurrent edit").unwrap();
        });
        assert!(result.is_err());
        let result = capture_with_between_passes(&config, &CancellationToken::new(), || {
            std::fs::write(root.path().join("src/New.java"), "new path during capture").unwrap();
        });
        assert!(result.is_err());
        assert!(!root.path().join("review.sfm-review.json").exists());
    }

    #[test]
    fn tracked_only_explicit_scope_and_cancel_are_honored() {
        let (root, mut config) = fixture();
        std::fs::write(root.path().join("src/New.java"), "new").unwrap();
        config.include_untracked = false;
        assert!(
            !capture(&config, &CancellationToken::new())
                .unwrap()
                .capture
                .entries
                .iter()
                .any(|e| !e.tracked)
        );
        config.scope_paths = Vec::new();
        assert!(capture(&config, &CancellationToken::new()).is_err());
        config.scope_paths = vec!["../outside".into()];
        assert!(capture(&config, &CancellationToken::new()).is_err());
        let cancellation = CancellationToken::new();
        cancellation.request_cancel("test cancellation");
        assert!(capture(&config, &cancellation).is_err());
    }

    #[test]
    fn source_parser_rejects_conflict_stages_and_non_utf8_paths() {
        assert!(
            index_entries(format!("100644 {} 2\tsrc/A.java\0", "a".repeat(40)).as_bytes()).is_err()
        );
        assert!(records(&[0xff, 0]).is_err());
        assert!(records(b"unterminated").is_err());
        assert!(canonical_prefixes(vec!["src/../escape".into()]).is_err());
        assert_eq!(
            canonical_prefixes(vec!["src/sub".into(), "src".into(), "src".into()]).unwrap(),
            vec!["src"]
        );
    }

    #[test]
    fn staged_deletion_retains_disk_content_and_mode_only_changes_are_represented() {
        let (root, config) = fixture();
        git(root.path(), &["rm", "--cached", "src/A.java"]);
        git(
            root.path(),
            &["update-index", "--chmod=+x", "src/Keep.java"],
        );
        let input = capture(&config, &CancellationToken::new()).unwrap();
        assert_eq!(
            input
                .capture
                .entries
                .iter()
                .find(|entry| entry.path == "src/A.java")
                .unwrap()
                .kind,
            "regular_file"
        );
        let mode = input
            .capture
            .entries
            .iter()
            .find(|entry| entry.path == "src/Keep.java")
            .unwrap();
        assert!(mode.executable);
        assert_eq!(mode.materialization, "utf8");
        let review =
            crate::release_review_working_tree_materialize::materialize(&config, input).unwrap();
        assert!(
            review
                .review_units
                .iter()
                .any(|unit| unit.path_after.as_deref() == Some("src/Keep.java"))
        );
    }

    #[test]
    fn oversized_text_has_a_limitation_and_hard_read_limit_refuses_before_reading() {
        let (root, config) = fixture();
        std::fs::write(
            root.path().join("src/Large.txt"),
            vec![b'x'; TEXT_LIMIT + 1],
        )
        .unwrap();
        let input = capture(&config, &CancellationToken::new()).unwrap();
        assert_eq!(
            input
                .capture
                .entries
                .iter()
                .find(|entry| entry.path == "src/Large.txt")
                .unwrap()
                .materialization,
            "oversized"
        );
        let review =
            crate::release_review_working_tree_materialize::materialize(&config, input).unwrap();
        assert!(
            review
                .review_units
                .iter()
                .any(|unit| unit.path_after.as_deref() == Some("src/Large.txt"))
        );
        std::fs::OpenOptions::new()
            .write(true)
            .open(root.path().join("src/Large.txt"))
            .unwrap()
            .set_len(FILE_LIMIT + 1)
            .unwrap();
        assert!(
            capture(&config, &CancellationToken::new())
                .unwrap_err()
                .to_string()
                .contains("read limit")
        );
    }
}
