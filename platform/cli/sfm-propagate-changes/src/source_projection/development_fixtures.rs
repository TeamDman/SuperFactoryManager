//! Pinned project-root test fixtures for standalone projections.
//!
//! Only `examples/**` and `docs/architecture/fixtures/**` are eligible. The
//! development import is stored separately from source and Gradle imports;
//! opt-in release examples are read from an exact committed Git tree. Neither
//! path adopts the current checkout's mutable examples.

use super::development_baseline::DevelopmentHeadSpec;
use super::manifest::ReleaseProjectFixtures;
use super::provenance::sha256;
use super::release_baseline::GitBlobHasher;
use super::release_baseline::ImportFile;
use super::release_baseline::git_text;
use super::release_baseline::insert_import_file;
use super::release_baseline::stage_and_install_imports;
use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use sha1::Digest as _;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use walkdir::WalkDir;

const SCHEMA: &str = "sfm:development-project-fixtures@1";
const MAX_FIXTURE_BYTES: u64 = 8 * 1024 * 1024;
const IMPORT_NAME: &str = "project-fixtures";
const PROVENANCE_NAME: &str = "project-fixtures-provenance.json";

#[derive(Clone, Debug, Facet)]
struct FixtureProvenance {
    schema: String,
    target_id: String,
    canonical_commit: String,
    target_commit: String,
    file_count: usize,
    files: BTreeMap<String, FixtureFile>,
}

#[derive(Clone, Debug, Facet)]
struct FixtureFile {
    git_mode: String,
    blob_oid: String,
    sha256: String,
}

#[derive(Clone, Debug)]
struct TreeFile {
    git_mode: String,
    blob_oid: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DevelopmentFixtureImportReport {
    pub target_id: String,
    pub target_commit: String,
    pub project_fixtures_path: String,
    pub provenance_path: String,
    /// Bare lowercase SHA-256 of the provenance file.
    pub provenance_sha256: String,
    pub file_count: usize,
    pub created_files: usize,
    pub reused_files: usize,
}

/// Snapshot the two declared fixture trees from a pinned development commit.
/// Existing files are reused only if they still have the exact pinned bytes;
/// unrelated or edited import files are never adopted or overwritten.
///
/// # Errors
///
/// Rejects moved/missing commits, changed fixture count, unsafe paths or Git
/// modes, symlinks, oversized blobs, and conflicting existing imports.
pub fn materialize_development_project_fixtures(
    repo_root: &Path,
    spec: &DevelopmentHeadSpec,
    expected_file_count: usize,
) -> Result<DevelopmentFixtureImportReport> {
    validate_spec(spec)?;
    ensure!(
        expected_file_count > 0,
        "expected fixture file count is empty"
    );
    let root = repository_root(repo_root)?;
    for commit in [&spec.canonical_commit, &spec.target_commit] {
        let resolved = git_text(
            &root,
            &["rev-parse", "--verify", &format!("{commit}^{{commit}}")],
        )?;
        ensure!(
            resolved == *commit,
            "pinned development commit '{commit}' moved"
        );
    }
    let tree = collect_fixture_tree(&root, &spec.target_commit)?;
    ensure!(
        tree.len() == expected_file_count,
        "pinned fixture tree has {} files, expected {expected_file_count}",
        tree.len()
    );

    let baseline = baseline_path(&spec.target_id);
    let project_fixtures_path = format!("{baseline}/{IMPORT_NAME}");
    let provenance_path = format!("{baseline}/{PROVENANCE_NAME}");
    let mut provenance = FixtureProvenance {
        schema: SCHEMA.to_owned(),
        target_id: spec.target_id.clone(),
        canonical_commit: spec.canonical_commit.clone(),
        target_commit: spec.target_commit.clone(),
        file_count: tree.len(),
        files: BTreeMap::new(),
    };
    let mut desired = BTreeMap::<String, ImportFile>::new();
    let mut blobs = GitBlobHasher::start(&root)?;
    for (path, entry) in tree {
        let bytes = blobs.read(&entry.blob_oid)?;
        ensure!(
            bytes.len() as u64 <= MAX_FIXTURE_BYTES,
            "pinned fixture '{path}' is too large"
        );
        ensure!(
            git_blob_oid(&bytes) == entry.blob_oid,
            "pinned fixture Git blob ID mismatch at '{path}'"
        );
        provenance.files.insert(
            path.clone(),
            FixtureFile {
                git_mode: entry.git_mode.clone(),
                blob_oid: entry.blob_oid,
                sha256: sha256(&bytes),
            },
        );
        insert_import_file(
            &mut desired,
            &format!("{project_fixtures_path}/{path}"),
            bytes,
            &entry.git_mode,
        )?;
    }
    blobs.finish()?;
    let mut json = facet_json::to_string_pretty(&provenance)?;
    json.push('\n');
    let provenance_bytes = json.into_bytes();
    let provenance_sha256 = bare_sha256(&provenance_bytes)?;
    insert_import_file(&mut desired, &provenance_path, provenance_bytes, "100644")?;

    // The shared installer checks desired files, but a complete fixture import
    // must also reject files or directories not declared by the pinned tree.
    let import_root = root.join(&project_fixtures_path);
    if import_root.exists() {
        let (existing_files, existing_dirs) = scan_fixture_import(&import_root)?;
        let declared_files = provenance.files.keys().cloned().collect::<BTreeSet<_>>();
        let declared_dirs = implied_directories(&declared_files);
        ensure!(
            existing_files.is_subset(&declared_files) && existing_dirs.is_subset(&declared_dirs),
            "development fixture import contains undeclared paths"
        );
    }
    let installed = stage_and_install_imports(&root, &desired)?;
    let created_files = installed.values().filter(|created| **created).count();
    Ok(DevelopmentFixtureImportReport {
        target_id: spec.target_id.clone(),
        target_commit: spec.target_commit.clone(),
        project_fixtures_path,
        provenance_path,
        provenance_sha256,
        file_count: provenance.file_count,
        created_files,
        reused_files: installed.len() - created_files,
    })
}

/// Verify a pinned import and collect artifacts at the original project-root
/// paths (`examples/...` and `docs/architecture/fixtures/...`). No Git access
/// or working-tree fixtures are needed after the import is materialized.
///
/// # Errors
///
/// Rejects a wrong target/head/hash, an unsafe or extra path, symlinks, changed
/// bytes or executable modes, and malformed provenance.
pub fn collect_verified_development_project_fixtures(
    repo_root: &Path,
    spec: &DevelopmentHeadSpec,
    expected_provenance_sha256: &str,
) -> Result<BTreeMap<String, ProjectedArtifact>> {
    validate_spec(spec)?;
    ensure!(
        is_lower_hex(expected_provenance_sha256, 64),
        "fixture provenance SHA-256 must be 64 lowercase hex characters"
    );
    let root = repository_directory(repo_root)?;
    let baseline = baseline_path(&spec.target_id);
    let import_root = root.join(&baseline).join(IMPORT_NAME);
    ensure_real_directory_chain(&root, &import_root)?;
    let provenance_path = root.join(&baseline).join(PROVENANCE_NAME);
    let metadata = fs::symlink_metadata(&provenance_path)
        .wrap_err("missing development fixture provenance")?;
    ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink(),
        "development fixture provenance must be a real file"
    );
    let provenance_bytes = fs::read(&provenance_path)?;
    ensure!(
        bare_sha256(&provenance_bytes)? == expected_provenance_sha256,
        "development fixture provenance differs from the pinned SHA-256"
    );
    let provenance_text = std::str::from_utf8(&provenance_bytes)
        .wrap_err("development fixture provenance is not UTF-8")?;
    let provenance: FixtureProvenance = facet_json::from_str(provenance_text)
        .wrap_err("cannot parse development fixture provenance")?;
    ensure!(
        provenance.schema == SCHEMA
            && provenance.target_id == spec.target_id
            && provenance.canonical_commit == spec.canonical_commit
            && provenance.target_commit == spec.target_commit,
        "development fixture provenance identity does not match pinned heads"
    );
    validate_inventory(&provenance)?;
    let (actual_files, actual_dirs) = scan_fixture_import(&import_root)?;
    let declared_files = provenance.files.keys().cloned().collect::<BTreeSet<_>>();
    ensure!(
        actual_files == declared_files && actual_dirs == implied_directories(&declared_files),
        "development fixture import path set differs from pinned provenance"
    );

    let mut artifacts = BTreeMap::new();
    for (path, record) in &provenance.files {
        let file = import_root.join(path);
        let metadata = fs::symlink_metadata(&file)?;
        ensure!(
            metadata.is_file() && !metadata.file_type().is_symlink(),
            "development fixture '{path}' is not a regular file"
        );
        ensure!(
            metadata.len() <= MAX_FIXTURE_BYTES,
            "development fixture '{path}' is too large"
        );
        #[cfg(unix)]
        verify_unix_mode(&file, &record.git_mode)?;
        let bytes = fs::read(&file)?;
        ensure!(
            sha256(&bytes) == record.sha256 && git_blob_oid(&bytes) == record.blob_oid,
            "development fixture '{path}' differs from pinned blob/hash"
        );
        artifacts.insert(
            path.clone(),
            ProjectedArtifact {
                source_path: path.clone(),
                source_bytes: bytes.clone(),
                output_bytes: bytes,
                overlay: Some("development-fixtures".to_owned()),
            },
        );
    }
    Ok(artifacts)
}

/// Project test examples from an exact release commit, never the working tree.
/// The expected tree OID closes membership as well as file content, while the
/// ordinary generated-file manifest owns every emitted project-root path.
///
/// # Errors
///
/// Rejects a moved commit, stale tree OID, changed file count, unsafe path or
/// Git mode, or a blob whose bytes do not match its object ID.
pub fn collect_pinned_release_examples(
    repo_root: &Path,
    commit: &str,
    expected: &ReleaseProjectFixtures,
) -> Result<BTreeMap<String, ProjectedArtifact>> {
    ensure!(
        is_lower_hex(commit, 40),
        "release fixture commit must be a Git SHA"
    );
    ensure!(
        is_lower_hex(&expected.examples_tree_oid, 40),
        "release examples tree OID must be a Git SHA"
    );
    let root = repository_root(repo_root)?;
    let resolved = git_text(
        &root,
        &["rev-parse", "--verify", &format!("{commit}^{{commit}}")],
    )?;
    ensure!(resolved == commit, "release fixture commit moved");
    let tree_oid = git_text(
        &root,
        &["rev-parse", "--verify", &format!("{commit}:examples")],
    )?;
    ensure!(
        tree_oid == expected.examples_tree_oid,
        "release examples tree differs from the pinned OID"
    );
    ensure!(
        git_text(&root, &["cat-file", "-t", &tree_oid])? == "tree",
        "release examples path is not a Git tree"
    );
    let tree = collect_fixture_tree_at(&root, commit, &["examples"])?;
    ensure!(
        tree.len() == expected.file_count,
        "release examples tree has {} files, expected {}",
        tree.len(),
        expected.file_count
    );
    let mut artifacts = BTreeMap::new();
    let mut blobs = GitBlobHasher::start(&root)?;
    for (path, entry) in tree {
        ensure!(
            path.starts_with("examples/"),
            "non-example release fixture '{path}'"
        );
        let bytes = blobs.read(&entry.blob_oid)?;
        ensure!(
            bytes.len() as u64 <= MAX_FIXTURE_BYTES,
            "release fixture '{path}' is too large"
        );
        ensure!(
            git_blob_oid(&bytes) == entry.blob_oid,
            "release fixture Git blob ID mismatch at '{path}'"
        );
        artifacts.insert(
            path.clone(),
            ProjectedArtifact {
                source_path: path.clone(),
                source_bytes: bytes.clone(),
                output_bytes: bytes,
                overlay: Some("release-tag-examples".to_owned()),
            },
        );
    }
    blobs.finish()?;
    Ok(artifacts)
}

fn collect_fixture_tree(root: &Path, commit: &str) -> Result<BTreeMap<String, TreeFile>> {
    collect_fixture_tree_at(root, commit, &["examples", "docs/architecture/fixtures"])
}

fn collect_fixture_tree_at(
    root: &Path,
    commit: &str,
    roots: &[&str],
) -> Result<BTreeMap<String, TreeFile>> {
    let output = Command::new("git")
        .current_dir(root)
        .env("GIT_OPTIONAL_LOCKS", "0")
        .args(["ls-tree", "-r", "-z", "--full-tree", commit, "--"])
        .args(roots)
        .output()
        .wrap_err("cannot read pinned fixture Git tree")?;
    ensure!(
        output.status.success(),
        "cannot read pinned fixture Git tree: {}",
        String::from_utf8_lossy(&output.stderr).trim()
    );
    parse_fixture_tree(&output.stdout)
}

fn parse_fixture_tree(output: &[u8]) -> Result<BTreeMap<String, TreeFile>> {
    ensure!(
        output.is_empty() || output.last() == Some(&0),
        "fixture Git tree is not NUL-terminated"
    );
    let mut files = BTreeMap::new();
    for record in output
        .split(|byte| *byte == 0)
        .filter(|record| !record.is_empty())
    {
        let tab = record
            .iter()
            .position(|byte| *byte == b'\t')
            .ok_or_else(|| eyre::eyre!("malformed fixture Git tree record"))?;
        let metadata = std::str::from_utf8(&record[..tab])?;
        let path = std::str::from_utf8(&record[tab + 1..])?;
        let mut fields = metadata.split(' ');
        let mode = fields.next().unwrap_or_default();
        let kind = fields.next().unwrap_or_default();
        let oid = fields.next().unwrap_or_default();
        ensure!(
            fields.next().is_none() && kind == "blob" && matches!(mode, "100644" | "100755"),
            "unsupported fixture Git mode/type at '{path}'"
        );
        ensure!(
            is_lower_hex(oid, 40),
            "invalid fixture Git blob ID at '{path}'"
        );
        validate_fixture_path(path)?;
        ensure!(
            files
                .insert(
                    path.to_owned(),
                    TreeFile {
                        git_mode: mode.to_owned(),
                        blob_oid: oid.to_owned(),
                    }
                )
                .is_none(),
            "duplicate fixture Git path '{path}'"
        );
    }
    ensure!(!files.is_empty(), "pinned fixture Git tree is empty");
    validate_path_inventory(files.keys().map(String::as_str))?;
    Ok(files)
}

fn validate_inventory(provenance: &FixtureProvenance) -> Result<()> {
    ensure!(
        provenance.file_count > 0 && provenance.file_count == provenance.files.len(),
        "development fixture provenance file count is invalid"
    );
    validate_path_inventory(provenance.files.keys().map(String::as_str))?;
    for (path, record) in &provenance.files {
        ensure!(
            matches!(record.git_mode.as_str(), "100644" | "100755")
                && is_lower_hex(&record.blob_oid, 40)
                && record
                    .sha256
                    .strip_prefix("sha256:")
                    .is_some_and(|digest| is_lower_hex(digest, 64)),
            "development fixture '{path}' has invalid Git or SHA-256 identity"
        );
    }
    Ok(())
}

fn validate_path_inventory<'a>(paths: impl Iterator<Item = &'a str>) -> Result<()> {
    let mut case_paths = BTreeMap::<String, String>::new();
    let mut files = BTreeSet::new();
    let mut directories = BTreeSet::new();
    for path in paths {
        validate_fixture_path(path)?;
        let parts = path.split('/').collect::<Vec<_>>();
        for index in 1..=parts.len() {
            let prefix = parts[..index].join("/");
            let key = prefix.to_ascii_lowercase();
            if let Some(existing) = case_paths.insert(key.clone(), prefix.clone()) {
                ensure!(
                    existing == prefix,
                    "case-colliding fixture paths '{existing}' and '{prefix}'"
                );
            }
            if index == parts.len() {
                ensure!(
                    !directories.contains(&key),
                    "fixture file/directory collision at '{path}'"
                );
                files.insert(key);
            } else {
                ensure!(
                    !files.contains(&key),
                    "fixture file/directory collision at '{prefix}'"
                );
                directories.insert(key);
            }
        }
    }
    Ok(())
}

fn validate_fixture_path(path: &str) -> Result<()> {
    ensure!(
        path.starts_with("examples/") || path.starts_with("docs/architecture/fixtures/"),
        "path '{path}' is outside declared project fixtures"
    );
    ensure!(
        !path
            .chars()
            .any(|character| character.is_control() || "\\<>:\"|?*".contains(character)),
        "nonportable project fixture path '{path}'"
    );
    for part in path.split('/') {
        let stem = part
            .split('.')
            .next()
            .unwrap_or_default()
            .to_ascii_uppercase();
        ensure!(
            !part.is_empty()
                && part != "."
                && part != ".."
                && !part.ends_with('.')
                && !part.ends_with(' ')
                && !matches!(stem.as_str(), "CON" | "PRN" | "AUX" | "NUL")
                && !(stem.len() == 4
                    && (stem.starts_with("COM") || stem.starts_with("LPT"))
                    && matches!(stem.as_bytes()[3], b'1'..=b'9')),
            "nonportable project fixture path '{path}'"
        );
    }
    Ok(())
}

fn scan_fixture_import(root: &Path) -> Result<(BTreeSet<String>, BTreeSet<String>)> {
    let metadata = fs::symlink_metadata(root).wrap_err("missing development fixture import")?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "development fixture import must be a real directory"
    );
    let mut files = BTreeSet::new();
    let mut directories = BTreeSet::new();
    for entry in WalkDir::new(root).follow_links(false).sort_by_file_name() {
        let entry = entry.wrap_err("cannot walk development fixture import")?;
        if entry.path() == root {
            continue;
        }
        ensure!(
            !entry.file_type().is_symlink(),
            "development fixture import traverses a symlink: '{}'",
            entry.path().display()
        );
        let relative = entry.path().strip_prefix(root)?;
        let path = relative
            .components()
            .map(|component| match component {
                Component::Normal(part) => part
                    .to_str()
                    .map(str::to_owned)
                    .ok_or_else(|| eyre::eyre!("fixture import path is not UTF-8")),
                _ => Err(eyre::eyre!("fixture import path is not relative")),
            })
            .collect::<Result<Vec<_>>>()?
            .join("/");
        if entry.file_type().is_dir() {
            directories.insert(path);
        } else {
            ensure!(
                entry.file_type().is_file(),
                "fixture import has a non-file entry"
            );
            validate_fixture_path(&path)?;
            files.insert(path);
        }
    }
    validate_path_inventory(files.iter().map(String::as_str))?;
    Ok((files, directories))
}

fn implied_directories(files: &BTreeSet<String>) -> BTreeSet<String> {
    let mut directories = BTreeSet::new();
    for path in files {
        let mut prefix = String::new();
        for part in path.split('/').take(path.split('/').count() - 1) {
            if !prefix.is_empty() {
                prefix.push('/');
            }
            prefix.push_str(part);
            directories.insert(prefix.clone());
        }
    }
    directories
}

fn validate_spec(spec: &DevelopmentHeadSpec) -> Result<()> {
    ensure!(
        !spec.target_id.is_empty()
            && spec.target_id.as_bytes()[0].is_ascii_alphanumeric()
            && spec.target_id.bytes().all(|byte| {
                byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"._-".contains(&byte)
            })
            && !spec.target_id.contains("..")
            && !spec.target_id.ends_with('.'),
        "invalid development fixture target ID '{}'",
        spec.target_id
    );
    ensure!(
        is_lower_hex(&spec.canonical_commit, 40) && is_lower_hex(&spec.target_commit, 40),
        "development fixture commits must be lowercase 40-character Git SHAs"
    );
    Ok(())
}

fn repository_directory(path: &Path) -> Result<PathBuf> {
    let metadata = fs::symlink_metadata(path).wrap_err("cannot inspect repository root")?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "repository root must be a real directory"
    );
    fs::canonicalize(path).wrap_err("cannot resolve repository root")
}

fn repository_root(path: &Path) -> Result<PathBuf> {
    let root = repository_directory(path)?;
    let git_root = fs::canonicalize(git_text(&root, &["rev-parse", "--show-toplevel"])?)?;
    ensure!(
        root == git_root,
        "fixture import requires the Git worktree root"
    );
    Ok(root)
}

fn ensure_real_directory_chain(root: &Path, directory: &Path) -> Result<()> {
    let relative = directory
        .strip_prefix(root)
        .wrap_err("development fixture import escapes repository root")?;
    let mut cursor = root.to_path_buf();
    for component in relative.components() {
        let Component::Normal(part) = component else {
            eyre::bail!("development fixture import has a nonportable directory");
        };
        cursor.push(part);
        let metadata = fs::symlink_metadata(&cursor).wrap_err_with(|| {
            format!(
                "missing development fixture directory '{}'",
                cursor.display()
            )
        })?;
        ensure!(
            metadata.is_dir() && !metadata.file_type().is_symlink(),
            "development fixture directory '{}' is not real",
            cursor.display()
        );
    }
    Ok(())
}

fn baseline_path(target_id: &str) -> String {
    format!("platform/minecraft/development-baselines/{target_id}")
}

fn bare_sha256(bytes: &[u8]) -> Result<String> {
    Ok(sha256(bytes)
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre::eyre!("SHA-256 result has no prefix"))?
        .to_owned())
}

fn git_blob_oid(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(format!("blob {}\0", bytes.len()).as_bytes());
    hasher.update(bytes);
    format!("{:x}", hasher.finalize())
}

fn is_lower_hex(value: &str, len: usize) -> bool {
    value.len() == len
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

#[cfg(unix)]
fn verify_unix_mode(path: &Path, expected: &str) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;

    let executable = fs::metadata(path)?.permissions().mode() & 0o111 != 0;
    ensure!(
        executable == (expected == "100755"),
        "development fixture '{}' has the wrong executable mode",
        path.display()
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::process::Stdio;

    fn git(root: &Path, args: &[&str]) -> String {
        let output = Command::new("git")
            .current_dir(root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {args:?} failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn write(root: &Path, path: &str, bytes: &[u8]) {
        let destination = root.join(path);
        fs::create_dir_all(destination.parent().unwrap()).unwrap();
        fs::write(destination, bytes).unwrap();
    }

    fn fixture_repo(root: &Path) -> DevelopmentHeadSpec {
        fs::create_dir_all(root.join("platform/minecraft")).unwrap();
        git(root, &["init", "-q"]);
        git(root, &["config", "user.name", "SFM Test"]);
        git(root, &["config", "user.email", "sfm-test@example.invalid"]);
        write(
            root,
            "platform/minecraft/src/main/java/Example.java",
            b"class Example {}\n",
        );
        git(root, &["add", "--", "platform/minecraft/src"]);
        git(root, &["commit", "-qm", "canonical"]);
        let canonical_commit = git(root, &["rev-parse", "HEAD"]);
        write(root, "examples/01.sfm", b"pinned example\n");
        write(
            root,
            "docs/architecture/fixtures/review.json",
            b"{\"pinned\":true}\n",
        );
        write(root, "docs/architecture/other.json", b"not a fixture\n");
        git(root, &["add", "--", "examples", "docs/architecture"]);
        git(root, &["commit", "-qm", "target"]);
        DevelopmentHeadSpec {
            target_id: "test".to_owned(),
            canonical_commit,
            target_commit: git(root, &["rev-parse", "HEAD"]),
        }
    }

    #[test]
    fn pinned_snapshot_ignores_working_tree_and_other_docs() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        let spec = fixture_repo(root);
        write(root, "examples/01.sfm", b"uncommitted change\n");
        write(root, "examples/untracked.sfm", b"untracked\n");
        let report = materialize_development_project_fixtures(root, &spec, 2).unwrap();
        assert_eq!((report.file_count, report.created_files), (2, 3));
        let artifacts =
            collect_verified_development_project_fixtures(root, &spec, &report.provenance_sha256)
                .unwrap();
        assert_eq!(artifacts.len(), 2);
        assert_eq!(
            artifacts["examples/01.sfm"].output_bytes,
            b"pinned example\n"
        );
        assert!(artifacts.contains_key("docs/architecture/fixtures/review.json"));
        assert!(!artifacts.contains_key("docs/architecture/other.json"));
        assert!(!artifacts.contains_key("examples/untracked.sfm"));
        let second = materialize_development_project_fixtures(root, &spec, 2).unwrap();
        assert_eq!((second.created_files, second.reused_files), (0, 3));
    }

    #[test]
    fn release_examples_use_exact_tag_tree_not_working_tree() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        let spec = fixture_repo(root);
        let tree_oid = git(
            root,
            &["rev-parse", &format!("{}:examples", spec.target_commit)],
        );
        let expected = ReleaseProjectFixtures {
            examples_tree_oid: tree_oid,
            file_count: 1,
        };
        write(root, "examples/01.sfm", b"uncommitted change\n");
        write(root, "examples/untracked.sfm", b"untracked\n");
        let artifacts =
            collect_pinned_release_examples(root, &spec.target_commit, &expected).unwrap();
        assert_eq!(artifacts.len(), 1);
        assert_eq!(
            artifacts["examples/01.sfm"].output_bytes,
            b"pinned example\n"
        );
        assert_eq!(
            artifacts["examples/01.sfm"].overlay.as_deref(),
            Some("release-tag-examples")
        );
        assert!(!artifacts.contains_key("examples/untracked.sfm"));
        assert!(!artifacts.contains_key("docs/architecture/fixtures/review.json"));
    }

    #[test]
    fn release_examples_reject_stale_tree_or_file_count() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        let spec = fixture_repo(root);
        let tree_oid = git(
            root,
            &["rev-parse", &format!("{}:examples", spec.target_commit)],
        );
        let mut expected = ReleaseProjectFixtures {
            examples_tree_oid: "0".repeat(40),
            file_count: 1,
        };
        let error = collect_pinned_release_examples(root, &spec.target_commit, &expected)
            .unwrap_err()
            .to_string();
        assert!(error.contains("differs from the pinned OID"), "{error}");
        expected.examples_tree_oid = tree_oid;
        expected.file_count = 2;
        let error = collect_pinned_release_examples(root, &spec.target_commit, &expected)
            .unwrap_err()
            .to_string();
        assert!(error.contains("has 1 files, expected 2"), "{error}");
    }

    #[test]
    fn verifier_and_materializer_reject_tampering_and_extra_paths() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        let spec = fixture_repo(root);
        let report = materialize_development_project_fixtures(root, &spec, 2).unwrap();
        assert!(
            collect_verified_development_project_fixtures(root, &spec, &"0".repeat(64)).is_err()
        );
        let mut wrong_identity = spec.clone();
        wrong_identity.canonical_commit = wrong_identity.target_commit.clone();
        assert!(
            collect_verified_development_project_fixtures(
                root,
                &wrong_identity,
                &report.provenance_sha256
            )
            .is_err()
        );
        let import = root.join(&report.project_fixtures_path);
        fs::write(import.join("examples/01.sfm"), b"edited\n").unwrap();
        assert!(
            collect_verified_development_project_fixtures(root, &spec, &report.provenance_sha256)
                .is_err()
        );
        assert!(materialize_development_project_fixtures(root, &spec, 2).is_err());
        fs::write(import.join("examples/01.sfm"), b"pinned example\n").unwrap();
        write(&import, "examples/extra.sfm", b"extra\n");
        assert!(
            collect_verified_development_project_fixtures(root, &spec, &report.provenance_sha256)
                .is_err()
        );
        assert!(materialize_development_project_fixtures(root, &spec, 2).is_err());
        assert!(materialize_development_project_fixtures(root, &spec, 3).is_err());
    }

    #[test]
    fn parser_rejects_unsafe_paths_modes_and_case_collisions() {
        let oid = "a".repeat(40);
        for path in [
            "examples/../outside",
            "examples/CON.txt",
            "examples/trailing.",
            "examples/back\\slash",
            "docs/architecture/other.json",
        ] {
            let record = format!("100644 blob {oid}\t{path}\0");
            assert!(
                parse_fixture_tree(record.as_bytes()).is_err(),
                "accepted {path}"
            );
        }
        let symlink = format!("120000 blob {oid}\texamples/link\0");
        assert!(parse_fixture_tree(symlink.as_bytes()).is_err());
        let unterminated = format!("100644 blob {oid}\texamples/one.sfm");
        assert!(parse_fixture_tree(unterminated.as_bytes()).is_err());
        let case_collision = format!(
            "100644 blob {oid}\texamples/A.sfm\0\
             100644 blob {oid}\texamples/a.sfm\0"
        );
        assert!(parse_fixture_tree(case_collision.as_bytes()).is_err());
        let directory_case_collision = format!(
            "100644 blob {oid}\texamples/A/one.sfm\0\
             100644 blob {oid}\texamples/a/two.sfm\0"
        );
        assert!(parse_fixture_tree(directory_case_collision.as_bytes()).is_err());
    }

    #[test]
    fn pinned_git_symlink_is_rejected() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        let mut spec = fixture_repo(root);
        let mut child = Command::new("git")
            .current_dir(root)
            .args(["hash-object", "-w", "--stdin"])
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();
        use std::io::Write;
        child
            .stdin
            .take()
            .unwrap()
            .write_all(b"../elsewhere")
            .unwrap();
        let output = child.wait_with_output().unwrap();
        assert!(output.status.success());
        let oid = String::from_utf8(output.stdout).unwrap();
        git(
            root,
            &[
                "update-index",
                "--add",
                "--cacheinfo",
                &format!("120000,{},examples/link", oid.trim()),
            ],
        );
        git(root, &["commit", "-qm", "symlink fixture"]);
        spec.target_commit = git(root, &["rev-parse", "HEAD"]);
        let error = materialize_development_project_fixtures(root, &spec, 3).unwrap_err();
        assert!(format!("{error:?}").contains("unsupported fixture Git mode/type"));
    }

    #[test]
    #[ignore = "materialize only after the pinned development-import checkpoint"]
    fn materialize_pinned_1194_fixtures() {
        let repo_root = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .unwrap();
        let spec = DevelopmentHeadSpec {
            target_id: "1.19.4".to_owned(),
            canonical_commit: super::super::development_baseline::CANONICAL_COMMIT.to_owned(),
            target_commit: super::super::development_baseline::TARGET_1_19_4_COMMIT.to_owned(),
        };
        let report = materialize_development_project_fixtures(repo_root, &spec, 26).unwrap();
        let artifacts = collect_verified_development_project_fixtures(
            repo_root,
            &spec,
            &report.provenance_sha256,
        )
        .unwrap();
        assert_eq!(artifacts.len(), 26);
        eprintln!("{report:#?}");
    }
}
