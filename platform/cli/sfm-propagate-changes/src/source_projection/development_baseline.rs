//! Pinned committed-development source imports. These are distinct from the
//! immutable release-tag imports, but use the same exact-path record format.

use super::release_baseline::BaselinePathClass;
use super::release_baseline::BaselinePathRecord;
use super::release_baseline::GitBlobHasher;
use super::release_baseline::ImportFile;
use super::release_baseline::ReleaseBaselineReport;
use super::release_baseline::ReleaseBaselineTargetReport;
use super::release_baseline::collect_release_tree;
use super::release_baseline::git_text;
use super::release_baseline::hash_canonical_tree;
use super::release_baseline::insert_import_file;
use super::release_baseline::sha256_hex;
use super::release_baseline::stage_and_install_imports;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

pub const DEVELOPMENT_SOURCE_SCHEMA: &str = "sfm:development-head-comparison@1";
pub const CANONICAL_COMMIT: &str = "b046574be908e40d647858dedc872ab9c4305170";
pub const TARGET_1_19_4_COMMIT: &str = "2e3b561c15d663fb89fd353ccc2af67eeb0c2053";

#[derive(Clone, Debug)]
pub struct DevelopmentHeadSpec {
    pub target_id: String,
    pub canonical_commit: String,
    pub target_commit: String,
}

#[derive(Debug)]
pub struct DevelopmentSourceImportReport {
    pub import_manifest_path: String,
    pub import_manifest_sha256: String,
    pub unchanged: u64,
    pub changed: u64,
    pub target_only: u64,
    pub canonical_only: u64,
    pub created_files: usize,
    pub reused_files: usize,
}

/// Compare two exact committed Git trees by logical `src/...` path and blob.
/// Working-tree edits and branch movement cannot alter this inventory.
///
/// # Errors
///
/// Rejects missing commits, unsafe or case-colliding paths, unsupported Git
/// modes and unreadable blobs.
pub fn compare_committed_source_heads(
    repository_root: &Path,
    spec: &DevelopmentHeadSpec,
) -> Result<ReleaseBaselineReport> {
    validate_spec(spec)?;
    let root = fs::canonicalize(repository_root)
        .wrap_err("cannot resolve development import repository root")?;
    ensure!(root.is_dir(), "development import root must be a directory");
    let git_root = fs::canonicalize(git_text(&root, &["rev-parse", "--show-toplevel"])?)?;
    ensure!(
        root == git_root,
        "development import requires the Git worktree root"
    );
    for commit in [&spec.canonical_commit, &spec.target_commit] {
        ensure!(
            git_text(&root, &["cat-file", "-t", commit])? == "commit",
            "development import revision {commit} is not a commit"
        );
    }

    let canonical = collect_release_tree(&root, &spec.canonical_commit)?;
    let target = collect_release_tree(&root, &spec.target_commit)?;
    let mut blobs = GitBlobHasher::start(&root)?;
    let mut canonical_hashes = BTreeMap::new();
    for (path, entry) in &canonical {
        canonical_hashes.insert(path.clone(), blobs.hash(&entry.oid)?);
    }
    let mut paths = BTreeMap::new();
    let mut counts = [0_u64; 4];
    let import_root = format!(
        "platform/minecraft/development-baselines/{}",
        spec.target_id
    );
    for path in canonical
        .keys()
        .chain(target.keys())
        .collect::<BTreeSet<_>>()
    {
        let canonical_hash = canonical_hashes.get(path.as_str());
        let target_entry = target.get(path.as_str());
        let target_hash = target_entry
            .map(|entry| blobs.hash(&entry.oid))
            .transpose()?;
        if let (Some(canonical_entry), Some(target_entry)) =
            (canonical.get(path.as_str()), target_entry)
        {
            ensure!(
                canonical_entry.mode == target_entry.mode || canonical_hash != target_hash.as_ref(),
                "mode-only development difference at '{path}' needs an explicit mode overlay"
            );
        }
        let classification = match (&target_hash, canonical_hash) {
            (Some(target_hash), Some(canonical_hash)) if target_hash == canonical_hash => {
                counts[0] += 1;
                BaselinePathClass::Unchanged
            }
            (Some(_), Some(_)) => {
                counts[1] += 1;
                BaselinePathClass::Changed
            }
            (Some(_), None) => {
                counts[2] += 1;
                BaselinePathClass::ReleaseOnly
            }
            (None, Some(_)) => {
                counts[3] += 1;
                BaselinePathClass::CanonicalOnly
            }
            (None, None) => unreachable!("path came from one of the two trees"),
        };
        paths.insert(
            (*path).clone(),
            BaselinePathRecord {
                classification,
                release_git_mode: target_entry.map(|entry| entry.mode.clone()),
                release_blob_oid: target_entry.map(|entry| entry.oid.clone()),
                release_sha256: target_hash,
                canonical_sha256: canonical_hash.cloned(),
                release_overlay_path: matches!(
                    classification,
                    BaselinePathClass::Changed | BaselinePathClass::ReleaseOnly
                )
                .then(|| format!("{import_root}/overlays/{path}")),
            },
        );
    }
    blobs.finish()?;
    Ok(ReleaseBaselineReport {
        schema: DEVELOPMENT_SOURCE_SCHEMA.to_owned(),
        canonical_source_root: "platform/minecraft/src".to_owned(),
        canonical_head: spec.canonical_commit.clone(),
        canonical_source_sha256: hash_canonical_tree(&canonical_hashes),
        jar_parity_proven: false,
        targets: vec![ReleaseBaselineTargetReport {
            target_id: spec.target_id.clone(),
            release_tag: spec.target_id.clone(),
            tag_commit: spec.target_commit.clone(),
            unchanged: counts[0],
            changed: counts[1],
            release_only: counts[2],
            canonical_only: counts[3],
            paths,
        }],
    })
}

/// Materialize the exact target-only and divergent source blobs once. Existing
/// imports are accepted only if every byte and executable bit still matches.
///
/// # Errors
///
/// Fails without replacing edited imports or touching source worktrees.
pub fn materialize_committed_source_import(
    repository_root: &Path,
    spec: &DevelopmentHeadSpec,
) -> Result<DevelopmentSourceImportReport> {
    let report = compare_committed_source_heads(repository_root, spec)?;
    let root = fs::canonicalize(repository_root)?;
    let target = &report.targets[0];
    let import_root = format!(
        "platform/minecraft/development-baselines/{}",
        spec.target_id
    );
    let manifest_path = format!("{import_root}/import.json");
    let manifest_bytes = report.to_json()?.into_bytes();
    let manifest_sha256 = sha256_hex(&manifest_bytes)
        .trim_start_matches("sha256:")
        .to_owned();
    let mut desired = BTreeMap::<String, ImportFile>::new();
    insert_import_file(&mut desired, &manifest_path, manifest_bytes, "100644")?;
    let mut blobs = GitBlobHasher::start(&root)?;
    for (path, record) in &target.paths {
        let Some(overlay_path) = &record.release_overlay_path else {
            continue;
        };
        ensure!(
            overlay_path == &format!("{import_root}/overlays/{path}"),
            "development overlay path mismatch for '{path}'"
        );
        let oid = record
            .release_blob_oid
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development overlay '{path}' has no blob ID"))?;
        let expected = record
            .release_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development overlay '{path}' has no SHA-256"))?;
        let bytes = blobs.read(oid)?;
        ensure!(
            sha256_hex(&bytes) == expected,
            "development blob hash mismatch"
        );
        let mode = record
            .release_git_mode
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development overlay '{path}' has no Git mode"))?;
        insert_import_file(&mut desired, overlay_path, bytes, mode)?;
    }
    blobs.finish()?;
    let installed = stage_and_install_imports(&root, &desired)?;
    Ok(DevelopmentSourceImportReport {
        import_manifest_path: manifest_path,
        import_manifest_sha256: manifest_sha256,
        unchanged: target.unchanged,
        changed: target.changed,
        target_only: target.release_only,
        canonical_only: target.canonical_only,
        created_files: installed.values().filter(|created| **created).count(),
        reused_files: installed.values().filter(|created| !**created).count(),
    })
}

fn validate_spec(spec: &DevelopmentHeadSpec) -> Result<()> {
    ensure!(
        !spec.target_id.is_empty()
            && spec.target_id.bytes().all(|byte| {
                byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"._-".contains(&byte)
            }),
        "development target ID is not portable"
    );
    for commit in [&spec.canonical_commit, &spec.target_commit] {
        ensure!(
            commit.len() == 40
                && commit
                    .bytes()
                    .all(|byte| byte.is_ascii_digit() || matches!(byte, b'a'..=b'f')),
            "development commit must be 40 lowercase hexadecimal characters"
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::process::Command;

    fn git(root: &Path, args: &[&str]) -> String {
        let output = Command::new("git")
            .current_dir(root)
            .args(args)
            .output()
            .unwrap();
        assert!(output.status.success(), "git {args:?} failed");
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn write(root: &Path, path: &str, bytes: &[u8]) {
        let destination = root.join(path);
        fs::create_dir_all(destination.parent().unwrap()).unwrap();
        fs::write(destination, bytes).unwrap();
    }

    #[test]
    fn committed_import_has_exact_membership_and_sparse_overlays() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        git(root, &["init", "-q"]);
        git(root, &["config", "user.name", "SFM Test"]);
        git(root, &["config", "user.email", "sfm-test@example.invalid"]);
        let source = "platform/minecraft/src";
        write(root, &format!("{source}/same.txt"), b"same\n");
        write(root, &format!("{source}/changed.txt"), b"canonical\n");
        write(
            root,
            &format!("{source}/canonical-only.txt"),
            b"canonical\n",
        );
        git(root, &["add", "--", "platform/minecraft/src"]);
        git(root, &["commit", "-qm", "canonical"]);
        let canonical_commit = git(root, &["rev-parse", "HEAD"]);
        write(root, &format!("{source}/changed.txt"), b"target\n");
        fs::remove_file(root.join(format!("{source}/canonical-only.txt"))).unwrap();
        write(root, &format!("{source}/target-only.txt"), b"target\n");
        git(root, &["add", "-A", "--", "platform/minecraft/src"]);
        git(root, &["commit", "-qm", "target"]);
        let target_commit = git(root, &["rev-parse", "HEAD"]);
        let spec = DevelopmentHeadSpec {
            target_id: "test".to_owned(),
            canonical_commit,
            target_commit,
        };
        let first = materialize_committed_source_import(root, &spec).unwrap();
        assert_eq!(
            (
                first.unchanged,
                first.changed,
                first.target_only,
                first.canonical_only
            ),
            (1, 1, 1, 1)
        );
        assert_eq!(first.created_files, 3);
        let import = root.join("platform/minecraft/development-baselines/test");
        assert!(import.join("import.json").is_file());
        assert_eq!(
            fs::read(import.join("overlays/src/changed.txt")).unwrap(),
            b"target\n"
        );
        assert_eq!(
            fs::read(import.join("overlays/src/target-only.txt")).unwrap(),
            b"target\n"
        );
        assert!(!import.join("overlays/src/same.txt").exists());
        let second = materialize_committed_source_import(root, &spec).unwrap();
        assert_eq!((second.created_files, second.reused_files), (0, 3));
        fs::write(import.join("overlays/src/changed.txt"), b"edited\n").unwrap();
        assert!(materialize_committed_source_import(root, &spec).is_err());
    }

    #[test]
    #[ignore = "run with SFM_DEVELOPMENT_IMPORT_REPO_ROOT to materialize the pinned repository import"]
    fn materialize_pinned_1_19_4_repository_import() {
        let root = std::env::var_os("SFM_DEVELOPMENT_IMPORT_REPO_ROOT")
            .expect("SFM_DEVELOPMENT_IMPORT_REPO_ROOT is required");
        let report = materialize_committed_source_import(
            Path::new(&root),
            &DevelopmentHeadSpec {
                target_id: "1.19.4".to_owned(),
                canonical_commit: CANONICAL_COMMIT.to_owned(),
                target_commit: TARGET_1_19_4_COMMIT.to_owned(),
            },
        )
        .unwrap();
        assert_eq!(
            (
                report.unchanged,
                report.changed,
                report.target_only,
                report.canonical_only
            ),
            (2592, 145, 26, 60)
        );
        println!("{report:?}");
    }
}
