//! Read-only source inventory for the published 4.34.0 Minecraft version tags.
//!
//! This compares exact tagged Git blobs with the current primary source files.
//! It does not generate sources, modify a target root, or assert JAR parity.

use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Component, Path, PathBuf};
use std::process::{Child, ChildStdin, ChildStdout, Command, Stdio};

use eyre::{Result, WrapErr, ensure};
use facet::Facet;
use sha2::{Digest, Sha256};
use walkdir::WalkDir;

const PROJECT_SOURCE_PREFIX: &str = "platform/minecraft/src/";
const MAX_BLOB_BYTES: u64 = 128 * 1024 * 1024;
const REPORT_SCHEMA: &str = "sfm:release-baseline-comparison@1";
const GRADLE_IMPORT_SCHEMA: &str = "sfm:tagged-gradle-import@1";
const GRADLE_ROOT_FILES: &[&str] = &[
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
];

/// The exact published release tag commits, independently checked when read.
/// The `1.21.0` target deliberately has a `1.21.0` tag even though its Gradle
/// `minecraft_version` property is `1.21`.
pub const RELEASE_4_34_0_TAGS: [(&str, &str, &str); 10] = [
    (
        "1.19.2",
        "4.34.0-1.19.2",
        "31135b8e86801b862d5cb2283c7c5878b7cc5bb4",
    ),
    (
        "1.19.4",
        "4.34.0-1.19.4",
        "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa",
    ),
    (
        "1.20",
        "4.34.0-1.20",
        "3df18123a19535fd0e5d1dc81aa302105c3fd2f6",
    ),
    (
        "1.20.1",
        "4.34.0-1.20.1",
        "bb5babf12f467235b3a44ad5098666ee3ed171ec",
    ),
    (
        "1.20.2",
        "4.34.0-1.20.2",
        "cfbbafaeda4a006ae32743a92de330711983056b",
    ),
    (
        "1.20.3",
        "4.34.0-1.20.3",
        "1b7f9605da0ef13c7601daf3786545868dfc3c78",
    ),
    (
        "1.20.4",
        "4.34.0-1.20.4",
        "a637581b5e1078d7cc0ca68333add568e3e387ff",
    ),
    (
        "1.21.0",
        "4.34.0-1.21.0",
        "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25",
    ),
    (
        "1.21.1",
        "4.34.0-1.21.1",
        "f5366c79c823ff52712130e69dd9c8166c70bd14",
    ),
    (
        "26.1.2",
        "4.34.0-26.1.2",
        "fe32b29453b13b4f3050ad441677c7eb79e80814",
    ),
];

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ReleaseTagSpec {
    pub target_id: String,
    pub release_tag: String,
    pub expected_commit: String,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub enum BaselinePathClass {
    Unchanged,
    Changed,
    ReleaseOnly,
    CanonicalOnly,
}

#[derive(Clone, Debug, Facet)]
pub struct BaselinePathRecord {
    pub classification: BaselinePathClass,
    /// Git blob mode (100644 or 100755), present only for tagged release paths.
    pub release_git_mode: Option<String>,
    /// Exact tag-tree blob ID, present only for tagged release paths.
    pub release_blob_oid: Option<String>,
    pub release_sha256: Option<String>,
    /// Hash of the canonical development working-tree bytes, if present.
    pub canonical_sha256: Option<String>,
    /// Planned repo-relative extraction path for a changed or release-only
    /// blob. The comparison does not create this file.
    pub release_overlay_path: Option<String>,
}

#[derive(Clone, Debug, Facet)]
pub struct ReleaseBaselineTargetReport {
    pub target_id: String,
    pub release_tag: String,
    pub tag_commit: String,
    pub unchanged: u64,
    pub changed: u64,
    pub release_only: u64,
    pub canonical_only: u64,
    /// Project-relative paths, e.g. `src/main/java/Example.java`.
    /// The release membership mask is exactly entries with a release blob.
    pub paths: BTreeMap<String, BaselinePathRecord>,
}

#[derive(Clone, Debug, Facet)]
pub struct ReleaseBaselineReport {
    pub schema: String,
    /// Repository-relative source root; no host path is persisted.
    pub canonical_source_root: String,
    /// HEAD is context only: canonical file hashes come from the working tree.
    pub canonical_head: String,
    /// Exact working-tree source fingerprint, including uncommitted files.
    pub canonical_source_sha256: String,
    /// Source-only comparison. Neither class counts nor hashes prove JAR parity.
    pub jar_parity_proven: bool,
    pub targets: Vec<ReleaseBaselineTargetReport>,
}

#[derive(Debug, Facet)]
pub struct TaggedGradleFile {
    pub git_mode: String,
    pub blob_oid: String,
    pub sha256: String,
}

#[derive(Debug, Facet)]
pub struct TaggedGradleImport {
    pub schema: String,
    pub target_id: String,
    pub release_tag: String,
    pub tag_commit: String,
    /// Exact tagged Gradle project input paths and hashes.
    pub files: BTreeMap<String, TaggedGradleFile>,
}

#[derive(Debug)]
pub struct ReleaseImportMaterialization {
    pub target_id: String,
    /// Repository-relative `.../<tag>/import.json`.
    pub import_manifest_path: String,
    /// Bare SHA-256 hex, suitable for `ReleaseBaselineBinding`.
    pub import_manifest_sha256: String,
    pub created_files: usize,
    pub reused_files: usize,
}

impl ReleaseBaselineReport {
    /// Serialize a stable report with a terminal newline.
    ///
    /// # Errors
    ///
    /// Returns an error if serialization fails.
    pub fn to_json(&self) -> Result<String> {
        let mut json = facet_json::to_string_pretty(self)?;
        json.push('\n');
        Ok(json)
    }

    /// Parse a previously generated release-baseline report.
    ///
    /// # Errors
    ///
    /// Rejects malformed or unsupported report schemas.
    pub fn from_json(json: &str) -> Result<Self> {
        let report: Self = facet_json::from_str(json)?;
        ensure!(
            report.schema == REPORT_SCHEMA,
            "unsupported release baseline schema '{}': expected {REPORT_SCHEMA}",
            report.schema
        );
        ensure!(
            report.canonical_source_root == "platform/minecraft/src",
            "unsupported canonical source root"
        );
        ensure!(
            !report.jar_parity_proven,
            "source inventory must not claim JAR parity"
        );
        Ok(report)
    }
}

/// Materialize exact source imports and tagged Gradle inputs for all ten
/// release targets. Existing different bytes always fail; no prior import is
/// deleted or overwritten. Generated version roots are never touched.
///
/// # Errors
///
/// Fails if a tag moved, a blob is missing, a path is unsafe, or an import
/// destination has been edited or claimed by unrelated content.
pub fn materialize_released_4_34_0_imports(
    repository_root: &Path,
) -> Result<Vec<ReleaseImportMaterialization>> {
    let specs = RELEASE_4_34_0_TAGS
        .iter()
        .map(|(target_id, release_tag, expected_commit)| ReleaseTagSpec {
            target_id: (*target_id).to_owned(),
            release_tag: (*release_tag).to_owned(),
            expected_commit: (*expected_commit).to_owned(),
        })
        .collect::<Vec<_>>();
    materialize_tagged_imports(repository_root, &specs)
}

/// Materialize imports for an exact set of pinned tags, including small test
/// fixtures. This recomputes comparison data before writing any destination.
///
/// # Errors
///
/// Fails closed on changed tags or existing import files, without deleting or
/// replacing the existing files.
pub fn materialize_tagged_imports(
    repository_root: &Path,
    specs: &[ReleaseTagSpec],
) -> Result<Vec<ReleaseImportMaterialization>> {
    let report = compare_tagged_baselines(repository_root, specs)?;
    let root = fs::canonicalize(repository_root)?;
    let mut blobs = GitBlobHasher::start(&root)?;
    let mut desired = BTreeMap::<String, ImportFile>::new();
    let mut outputs = Vec::with_capacity(report.targets.len());

    for target in &report.targets {
        outputs.push(prepare_target_import(
            &root,
            &report,
            target,
            &mut blobs,
            &mut desired,
        )?);
    }
    blobs.finish()?;
    let installed = stage_and_install_imports(&root, &desired)?;
    for (output, target) in outputs.iter_mut().zip(&report.targets) {
        let prefix = format!(
            "platform/minecraft/release-baselines/{}/",
            target.release_tag
        );
        for (path, created) in &installed {
            if path.starts_with(&prefix) {
                if *created {
                    output.created_files += 1;
                } else {
                    output.reused_files += 1;
                }
            }
        }
    }
    Ok(outputs)
}

fn prepare_target_import(
    root: &Path,
    report: &ReleaseBaselineReport,
    target: &ReleaseBaselineTargetReport,
    blobs: &mut GitBlobHasher,
    desired: &mut BTreeMap<String, ImportFile>,
) -> Result<ReleaseImportMaterialization> {
    let import_root = format!(
        "platform/minecraft/release-baselines/{}",
        target.release_tag
    );
    let import_manifest_path = format!("{import_root}/import.json");
    let single_target_report = ReleaseBaselineReport {
        schema: report.schema.clone(),
        canonical_source_root: report.canonical_source_root.clone(),
        canonical_head: report.canonical_head.clone(),
        canonical_source_sha256: report.canonical_source_sha256.clone(),
        jar_parity_proven: false,
        targets: vec![target.clone()],
    };
    let import_json = single_target_report.to_json()?.into_bytes();
    let import_manifest_sha256 = format!("{:x}", Sha256::digest(&import_json));
    insert_import_file(desired, &import_manifest_path, import_json, "100644")?;
    prepare_source_overlays(target, &import_root, blobs, desired)?;
    prepare_gradle_import(root, target, &import_root, blobs, desired)?;
    Ok(ReleaseImportMaterialization {
        target_id: target.target_id.clone(),
        import_manifest_path,
        import_manifest_sha256,
        created_files: 0,
        reused_files: 0,
    })
}

fn prepare_source_overlays(
    target: &ReleaseBaselineTargetReport,
    import_root: &str,
    blobs: &mut GitBlobHasher,
    desired: &mut BTreeMap<String, ImportFile>,
) -> Result<()> {
    for (path, record) in &target.paths {
        if !matches!(
            record.classification,
            BaselinePathClass::Changed | BaselinePathClass::ReleaseOnly
        ) {
            continue;
        }
        let expected_path = format!("{import_root}/overlays/{path}");
        ensure!(
            record.release_overlay_path.as_deref() == Some(expected_path.as_str()),
            "release overlay path mismatch for '{path}'"
        );
        let oid = record
            .release_blob_oid
            .as_deref()
            .ok_or_else(|| eyre::eyre!("missing release blob ID for '{path}'"))?;
        let expected_hash = record
            .release_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("missing release SHA-256 for '{path}'"))?;
        let bytes = blobs.read(oid)?;
        ensure!(
            sha256_hex(&bytes) == expected_hash,
            "release blob SHA-256 mismatch for '{path}'"
        );
        let mode = record
            .release_git_mode
            .as_deref()
            .ok_or_else(|| eyre::eyre!("missing release Git mode for '{path}'"))?;
        insert_import_file(desired, &expected_path, bytes, mode)?;
    }
    Ok(())
}

fn prepare_gradle_import(
    root: &Path,
    target: &ReleaseBaselineTargetReport,
    import_root: &str,
    blobs: &mut GitBlobHasher,
    desired: &mut BTreeMap<String, ImportFile>,
) -> Result<()> {
    let tagged_gradle = collect_tagged_gradle_tree(root, &target.tag_commit)?;
    let mut gradle_manifest = TaggedGradleImport {
        schema: GRADLE_IMPORT_SCHEMA.to_owned(),
        target_id: target.target_id.clone(),
        release_tag: target.release_tag.clone(),
        tag_commit: target.tag_commit.clone(),
        files: BTreeMap::new(),
    };
    for (path, entry) in tagged_gradle {
        let bytes = blobs.read(&entry.oid)?;
        let sha256 = sha256_hex(&bytes);
        gradle_manifest.files.insert(
            path.clone(),
            TaggedGradleFile {
                git_mode: entry.mode.clone(),
                blob_oid: entry.oid,
                sha256,
            },
        );
        insert_import_file(
            desired,
            &format!("{import_root}/gradle-project/{path}"),
            bytes,
            &entry.mode,
        )?;
    }
    let mut gradle_json = facet_json::to_string_pretty(&gradle_manifest)?;
    gradle_json.push('\n');
    insert_import_file(
        desired,
        &format!("{import_root}/gradle-provenance.json"),
        gradle_json.into_bytes(),
        "100644",
    )?;
    Ok(())
}

#[derive(Debug)]
pub(crate) struct ImportFile {
    bytes: Vec<u8>,
    #[cfg(unix)]
    git_mode: String,
}

pub(crate) fn insert_import_file(
    desired: &mut BTreeMap<String, ImportFile>,
    path: &str,
    bytes: Vec<u8>,
    mode: &str,
) -> Result<()> {
    validate_import_path(path)?;
    ensure!(
        matches!(mode, "100644" | "100755"),
        "invalid import Git mode '{mode}'"
    );
    ensure!(
        desired
            .insert(
                path.to_owned(),
                ImportFile {
                    bytes,
                    #[cfg(unix)]
                    git_mode: mode.to_owned()
                }
            )
            .is_none(),
        "duplicate release import path '{path}'"
    );
    Ok(())
}

fn validate_import_path(path: &str) -> Result<()> {
    validate_project_relative_path(path)?;
    ensure!(
        path.starts_with("platform/minecraft/release-baselines/")
            || path.starts_with("platform/minecraft/development-baselines/"),
        "pinned import path is outside the baseline roots: '{path}'"
    );
    Ok(())
}

pub(crate) fn collect_tagged_gradle_tree(
    root: &Path,
    commit: &str,
) -> Result<BTreeMap<String, GitTreeEntry>> {
    let output = git_stdout(
        root,
        &["ls-tree", "-r", "-z", commit, "--", "platform/minecraft"],
    )?;
    let mut files = BTreeMap::new();
    let mut case = BTreeSet::new();
    for record in output
        .split(|byte| *byte == 0)
        .filter(|record| !record.is_empty())
    {
        let entry = parse_tree_entry(record)?;
        let Some(relative) = entry.path.strip_prefix("platform/minecraft/") else {
            continue;
        };
        if !GRADLE_ROOT_FILES.contains(&relative) && !relative.starts_with("gradle/") {
            continue;
        }
        validate_project_relative_path(relative)?;
        ensure!(
            entry.kind == "blob" && matches!(entry.mode.as_str(), "100644" | "100755"),
            "unsupported tagged Gradle mode/type at '{relative}'"
        );
        validate_sha1(&entry.oid, "tagged Gradle blob ID")?;
        ensure!(
            case.insert(relative.to_ascii_lowercase()),
            "case-colliding tagged Gradle path '{relative}'"
        );
        ensure!(
            files
                .insert(
                    relative.to_owned(),
                    GitTreeEntry {
                        mode: entry.mode,
                        oid: entry.oid
                    }
                )
                .is_none(),
            "duplicate tagged Gradle path '{relative}'"
        );
    }
    for required in GRADLE_ROOT_FILES {
        ensure!(
            files.contains_key(*required),
            "tagged Gradle project is missing '{required}'"
        );
    }
    ensure!(
        files.keys().any(|path| path.starts_with("gradle/")),
        "tagged Gradle project has no gradle/ inputs"
    );
    Ok(files)
}

pub(crate) fn stage_and_install_imports(
    root: &Path,
    desired: &BTreeMap<String, ImportFile>,
) -> Result<BTreeMap<String, bool>> {
    // Preflight every target before writing even one destination file. A
    // repository-owned temp directory is on the same volume as the imports.
    preflight_imports(root, desired)?;
    let stage = tempfile::Builder::new()
        .prefix(".sfm-release-import-stage-")
        .tempdir_in(root.join("platform/minecraft"))?;
    let mut staged = BTreeMap::<String, PathBuf>::new();
    for (index, (path, content)) in desired.iter().enumerate() {
        let staged_path = stage.path().join(index.to_string());
        fs::write(&staged_path, &content.bytes)
            .wrap_err_with(|| format!("cannot stage release import '{path}'"))?;
        #[cfg(unix)]
        set_import_mode(&staged_path, &content.git_mode)?;
        staged.insert(path.clone(), staged_path);
    }

    // Detect edits made while staging. Hard links below use create-new
    // semantics on both Windows and Unix, so a late collision cannot replace
    // another file even after this second preflight.
    let already_present = preflight_imports(root, desired)?;
    let mut installed = BTreeMap::new();
    let mut created_paths = Vec::new();
    for (path, content) in desired {
        if already_present.contains(path) {
            installed.insert(path.clone(), false);
            continue;
        }
        let destination = root.join(path);
        let result = (|| -> Result<()> {
            let parent = destination
                .parent()
                .ok_or_else(|| eyre::eyre!("import has no parent"))?;
            fs::create_dir_all(parent)?;
            validate_import_parents(root, path)?;
            let staged_path = staged
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing staged import '{path}'"))?;
            ensure!(
                fs::read(staged_path)? == content.bytes,
                "staged import '{path}' changed before installation"
            );
            fs::hard_link(staged_path, &destination)
                .wrap_err_with(|| format!("cannot install release import '{path}'"))?;
            Ok(())
        })();
        if let Err(error) = result {
            let mut rollback_failures = Vec::new();
            for created in created_paths.iter().rev() {
                if let Err(rollback_error) = fs::remove_file(root.join(created)) {
                    rollback_failures.push(format!("{created}: {rollback_error}"));
                }
            }
            if rollback_failures.is_empty() {
                return Err(
                    error.wrap_err("release import failed; newly created files were rolled back")
                );
            }
            eyre::bail!(
                "release import failed: {error:?}; rollback could not remove new files: {}",
                rollback_failures.join(", ")
            );
        }
        created_paths.push(path.clone());
        installed.insert(path.clone(), true);
    }
    Ok(installed)
}

pub(crate) fn preflight_imports(
    root: &Path,
    desired: &BTreeMap<String, ImportFile>,
) -> Result<BTreeSet<String>> {
    let existing_paths = scan_existing_import_paths(root)?;
    let mut present = BTreeSet::new();
    let mut desired_case = BTreeSet::new();
    for (path, content) in desired {
        validate_import_path(path)?;
        ensure!(
            desired_case.insert(path.to_ascii_lowercase()),
            "case-colliding desired release import '{path}'"
        );
        for prefix in path_prefixes(path) {
            if let Some(actual) = existing_paths.get(&prefix.to_ascii_lowercase()) {
                ensure!(
                    actual == &prefix,
                    "release import path casing conflicts with existing '{actual}'"
                );
            }
        }
        validate_import_parents(root, path)?;
        let destination = root.join(path);
        match fs::symlink_metadata(&destination) {
            Ok(metadata) => {
                ensure!(
                    metadata.is_file() && !metadata.file_type().is_symlink(),
                    "release import destination '{path}' is not a regular file"
                );
                ensure!(
                    fs::read(&destination)? == content.bytes,
                    "release import destination '{path}' differs from pinned tag bytes"
                );
                #[cfg(unix)]
                validate_existing_mode(&destination, &content.git_mode)?;
                present.insert(path.clone());
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("cannot inspect release import '{path}'"));
            }
        }
    }
    Ok(present)
}

fn scan_existing_import_paths(root: &Path) -> Result<BTreeMap<String, String>> {
    let mut paths = BTreeMap::new();
    for import_dir in ["release-baselines", "development-baselines"] {
        let imports_root = root.join("platform/minecraft").join(import_dir);
        let metadata = match fs::symlink_metadata(&imports_root) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
            Err(error) => {
                return Err(error).wrap_err_with(|| format!("cannot inspect {import_dir} root"));
            }
        };
        ensure!(
            metadata.is_dir() && !metadata.file_type().is_symlink(),
            "{import_dir} root must be a real directory"
        );
        for entry in WalkDir::new(&imports_root)
            .follow_links(false)
            .sort_by_file_name()
        {
            let entry = entry.wrap_err_with(|| format!("cannot walk {import_dir} root"))?;
            if entry.path() == imports_root {
                continue;
            }
            ensure!(
                !entry.file_type().is_symlink(),
                "{import_dir} contains a symlink: '{}'",
                entry.path().display()
            );
            ensure!(
                entry.file_type().is_file() || entry.file_type().is_dir(),
                "{import_dir} contains an unsupported filesystem entry"
            );
            let relative = entry.path().strip_prefix(root)?;
            let path = relative
                .components()
                .map(|component| match component {
                    Component::Normal(part) => part
                        .to_str()
                        .map(str::to_owned)
                        .ok_or_else(|| eyre::eyre!("import path is not UTF-8")),
                    _ => Err(eyre::eyre!("import path is not relative")),
                })
                .collect::<Result<Vec<_>>>()?
                .join("/");
            validate_import_path(&path)?;
            if let Some(previous) = paths.insert(path.to_ascii_lowercase(), path.clone()) {
                ensure!(
                    previous == path,
                    "case-colliding existing imports '{previous}' and '{path}'"
                );
            }
        }
    }
    Ok(paths)
}

fn path_prefixes(path: &str) -> Vec<String> {
    let mut result = Vec::new();
    let mut prefix = String::new();
    for part in path.split('/') {
        if !prefix.is_empty() {
            prefix.push('/');
        }
        prefix.push_str(part);
        result.push(prefix.clone());
    }
    result
}

fn validate_import_parents(root: &Path, path: &str) -> Result<()> {
    let prefixes = path_prefixes(path);
    for parent in prefixes.iter().take(prefixes.len().saturating_sub(1)) {
        match fs::symlink_metadata(root.join(parent)) {
            Ok(metadata) => ensure!(
                metadata.is_dir() && !metadata.file_type().is_symlink(),
                "release import parent '{parent}' is not a real directory"
            ),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => break,
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("cannot inspect import parent '{parent}'"));
            }
        }
    }
    Ok(())
}

#[cfg(unix)]
fn set_import_mode(path: &Path, git_mode: &str) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;
    let mode = if git_mode == "100755" { 0o755 } else { 0o644 };
    fs::set_permissions(path, fs::Permissions::from_mode(mode))?;
    Ok(())
}

#[cfg(unix)]
fn validate_existing_mode(path: &Path, git_mode: &str) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;
    let executable = fs::metadata(path)?.permissions().mode() & 0o111 != 0;
    ensure!(
        executable == (git_mode == "100755"),
        "existing import executable mode differs from tag"
    );
    Ok(())
}

/// Recover a blob only when the pinned commit contains the exact project path,
/// blob ID and SHA-256 recorded by the release import. This remains valid if
/// the canonical development file is subsequently edited or removed.
///
/// # Errors
///
/// Rejects missing paths, non-regular Git entries, moved object identity,
/// oversized blobs, and hash mismatches.
pub fn read_pinned_blob(
    repository_root: &Path,
    tag_commit: &str,
    project_relative_path: &str,
    expected_oid: &str,
    expected_sha256: &str,
) -> Result<Vec<u8>> {
    validate_sha1(tag_commit, "tag commit")?;
    validate_sha1(expected_oid, "release blob ID")?;
    validate_project_relative_path(project_relative_path)?;
    ensure!(
        expected_sha256.len() == 71
            && expected_sha256.starts_with("sha256:")
            && expected_sha256[7..]
                .bytes()
                .all(|byte| byte.is_ascii_hexdigit()),
        "expected release SHA-256 is invalid"
    );
    let full_path = format!("platform/minecraft/{project_relative_path}");
    let tree = git_stdout(
        repository_root,
        &["ls-tree", "-z", tag_commit, "--", &full_path],
    )?;
    let mut exact = None;
    for record in tree
        .split(|byte| *byte == 0)
        .filter(|record| !record.is_empty())
    {
        let entry = parse_tree_entry(record)?;
        if entry.path == full_path {
            ensure!(
                exact.replace(entry).is_none(),
                "duplicate Git tree path '{full_path}'"
            );
        }
    }
    let exact = exact.ok_or_else(|| {
        eyre::eyre!("tag commit {tag_commit} does not contain exact path '{project_relative_path}'")
    })?;
    ensure!(
        exact.kind == "blob" && matches!(exact.mode.as_str(), "100644" | "100755"),
        "tagged path '{project_relative_path}' is not a regular Git blob"
    );
    ensure!(
        exact.oid == expected_oid,
        "tagged path '{project_relative_path}' has blob {}, expected {expected_oid}",
        exact.oid
    );
    let size = git_text(repository_root, &["cat-file", "-s", expected_oid])?
        .parse::<u64>()
        .wrap_err("invalid pinned blob size")?;
    ensure!(size <= MAX_BLOB_BYTES, "pinned blob is too large");
    let bytes = git_stdout(repository_root, &["cat-file", "blob", expected_oid])?;
    ensure!(
        bytes.len() as u64 == size,
        "pinned blob size changed while reading"
    );
    ensure!(
        sha256_hex(&bytes) == expected_sha256,
        "pinned blob SHA-256 mismatch for '{project_relative_path}'"
    );
    Ok(bytes)
}

/// Compare all ten immutable 4.34.0 tag trees to the current primary sources.
/// This reads Git and the working tree without writing to any project root.
///
/// # Errors
///
/// Fails for missing or moved tags, unsafe paths, unsupported Git entries, or
/// unreadable blobs or working-tree files.
pub fn compare_released_4_34_0(repository_root: &Path) -> Result<ReleaseBaselineReport> {
    let specs = RELEASE_4_34_0_TAGS
        .iter()
        .map(|(target_id, release_tag, expected_commit)| ReleaseTagSpec {
            target_id: (*target_id).to_owned(),
            release_tag: (*release_tag).to_owned(),
            expected_commit: (*expected_commit).to_owned(),
        })
        .collect::<Vec<_>>();
    compare_tagged_baselines(repository_root, &specs)
}

/// Compare pinned release tags with the canonical development working tree.
/// The caller supplies tags and exact commit IDs so a moved tag fails closed.
///
/// # Errors
///
/// Fails for missing or moved tags, unsafe paths, unsupported Git entries, or
/// unreadable blobs or working-tree files.
pub fn compare_tagged_baselines(
    repository_root: &Path,
    specs: &[ReleaseTagSpec],
) -> Result<ReleaseBaselineReport> {
    ensure!(
        !specs.is_empty(),
        "release comparison needs at least one tag"
    );
    let root = fs::canonicalize(repository_root).wrap_err_with(|| {
        format!(
            "cannot resolve repository root '{}'",
            repository_root.display()
        )
    })?;
    ensure!(root.is_dir(), "repository root must be a directory");
    let git_root = git_stdout(&root, &["rev-parse", "--show-toplevel"])?;
    let git_root = String::from_utf8(git_root).wrap_err("Git root is not UTF-8")?;
    let git_root = fs::canonicalize(git_root.trim()).wrap_err("cannot resolve Git root")?;
    ensure!(
        root == git_root,
        "release comparison requires the Git worktree root"
    );

    let canonical_head = git_text(&root, &["rev-parse", "HEAD"])?;
    validate_sha1(&canonical_head, "canonical HEAD")?;
    let canonical = collect_canonical_files(&root.join("platform/minecraft/src"))?;
    let canonical_source_sha256 = hash_canonical_tree(&canonical);

    let mut target_ids = BTreeSet::new();
    let mut tags = BTreeSet::new();
    let mut resolved = Vec::with_capacity(specs.len());
    for spec in specs {
        ensure!(!spec.target_id.is_empty(), "target ID must not be empty");
        ensure!(
            target_ids.insert(&spec.target_id),
            "duplicate target ID '{}'",
            spec.target_id
        );
        validate_tag_name(&spec.release_tag)?;
        ensure!(
            tags.insert(&spec.release_tag),
            "duplicate release tag '{}'",
            spec.release_tag
        );
        validate_sha1(&spec.expected_commit, "expected tag commit")?;
        let tag_ref = format!("refs/tags/{}^{{commit}}", spec.release_tag);
        let actual = git_text(&root, &["rev-parse", "--verify", &tag_ref])?;
        ensure!(
            actual == spec.expected_commit,
            "release tag '{}' moved: expected {}, found {actual}",
            spec.release_tag,
            spec.expected_commit
        );
        resolved.push((spec, actual));
    }

    let mut blobs = GitBlobHasher::start(&root)?;
    let mut targets = Vec::with_capacity(resolved.len());
    for (spec, commit) in resolved {
        targets.push(compare_one_target(
            &root, &canonical, spec, commit, &mut blobs,
        )?);
    }
    blobs.finish()?;
    Ok(ReleaseBaselineReport {
        schema: REPORT_SCHEMA.to_owned(),
        canonical_source_root: "platform/minecraft/src".to_owned(),
        canonical_head,
        canonical_source_sha256,
        jar_parity_proven: false,
        targets,
    })
}

fn compare_one_target(
    root: &Path,
    canonical: &BTreeMap<String, String>,
    spec: &ReleaseTagSpec,
    commit: String,
    blobs: &mut GitBlobHasher,
) -> Result<ReleaseBaselineTargetReport> {
    let release = collect_release_tree(root, &commit)?;
    let mut paths = BTreeMap::new();
    let mut counts = [0_u64; 4];
    for path in release
        .keys()
        .chain(canonical.keys())
        .collect::<BTreeSet<_>>()
    {
        let release_entry = release.get(path.as_str());
        let canonical_hash = canonical.get(path.as_str());
        let release_hash = release_entry
            .map(|entry| blobs.hash(&entry.oid))
            .transpose()?;
        let classification = match (&release_hash, canonical_hash) {
            (Some(release_hash), Some(canonical_hash)) if release_hash == canonical_hash => {
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
            (None, None) => unreachable!("path came from one of the two maps"),
        };
        paths.insert(
            (*path).clone(),
            BaselinePathRecord {
                classification,
                release_git_mode: release_entry.map(|entry| entry.mode.clone()),
                release_blob_oid: release_entry.map(|entry| entry.oid.clone()),
                release_sha256: release_hash,
                canonical_sha256: canonical_hash.cloned(),
                release_overlay_path: matches!(
                    classification,
                    BaselinePathClass::Changed | BaselinePathClass::ReleaseOnly
                )
                .then(|| {
                    format!(
                        "platform/minecraft/release-baselines/{}/overlays/{path}",
                        spec.release_tag
                    )
                }),
            },
        );
    }
    Ok(ReleaseBaselineTargetReport {
        target_id: spec.target_id.clone(),
        release_tag: spec.release_tag.clone(),
        tag_commit: commit,
        unchanged: counts[0],
        changed: counts[1],
        release_only: counts[2],
        canonical_only: counts[3],
        paths,
    })
}

#[derive(Debug)]
pub(crate) struct GitTreeEntry {
    pub(crate) mode: String,
    pub(crate) oid: String,
}

struct ParsedTreeEntry {
    mode: String,
    kind: String,
    oid: String,
    path: String,
}

fn parse_tree_entry(record: &[u8]) -> Result<ParsedTreeEntry> {
    let tab = record
        .iter()
        .position(|byte| *byte == b'\t')
        .ok_or_else(|| eyre::eyre!("malformed Git tree record"))?;
    let (meta, path_with_tab) = record.split_at(tab);
    let fields = meta.split(|byte| *byte == b' ').collect::<Vec<_>>();
    ensure!(fields.len() == 3, "malformed Git tree metadata");
    Ok(ParsedTreeEntry {
        mode: std::str::from_utf8(fields[0])?.to_owned(),
        kind: std::str::from_utf8(fields[1])?.to_owned(),
        oid: std::str::from_utf8(fields[2])?.to_owned(),
        path: std::str::from_utf8(&path_with_tab[1..])
            .wrap_err("Git tree path is not UTF-8")?
            .to_owned(),
    })
}

pub(crate) fn collect_release_tree(
    root: &Path,
    commit: &str,
) -> Result<BTreeMap<String, GitTreeEntry>> {
    let output = git_stdout(
        root,
        &[
            "ls-tree",
            "-r",
            "-z",
            "--full-tree",
            commit,
            "--",
            "platform/minecraft/src",
        ],
    )?;
    let mut result = BTreeMap::new();
    let mut case = BTreeSet::new();
    for record in output
        .split(|byte| *byte == 0)
        .filter(|record| !record.is_empty())
    {
        let entry = parse_tree_entry(record)?;
        ensure!(
            entry.kind == "blob" && matches!(entry.mode.as_str(), "100644" | "100755"),
            "unsupported release Git entry mode/type {}/{}",
            entry.mode,
            entry.kind
        );
        validate_sha1(&entry.oid, "release blob ID")?;
        let tail = entry
            .path
            .strip_prefix(PROJECT_SOURCE_PREFIX)
            .ok_or_else(|| eyre::eyre!("release Git path is outside platform/minecraft/src"))?;
        let relative = format!("src/{tail}");
        validate_project_path(&relative)?;
        ensure!(
            case.insert(relative.to_ascii_lowercase()),
            "case-colliding release paths at '{relative}'"
        );
        ensure!(
            result
                .insert(
                    relative.clone(),
                    GitTreeEntry {
                        mode: entry.mode,
                        oid: entry.oid
                    }
                )
                .is_none(),
            "duplicate release path '{relative}'"
        );
    }
    ensure!(
        !result.is_empty(),
        "release tag {commit} has no Minecraft sources"
    );
    Ok(result)
}

fn collect_canonical_files(source_root: &Path) -> Result<BTreeMap<String, String>> {
    let metadata = fs::symlink_metadata(source_root)
        .wrap_err_with(|| format!("cannot inspect source root '{}'", source_root.display()))?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "canonical source root must be a real directory"
    );
    let mut result = BTreeMap::new();
    let mut case = BTreeSet::new();
    for entry in WalkDir::new(source_root)
        .follow_links(false)
        .sort_by_file_name()
    {
        let entry = entry.wrap_err("cannot walk canonical source root")?;
        if entry.path() == source_root {
            continue;
        }
        ensure!(
            !entry.file_type().is_symlink(),
            "canonical source tree contains a symlink: '{}'",
            entry.path().display()
        );
        if entry.file_type().is_dir() {
            continue;
        }
        ensure!(
            entry.file_type().is_file(),
            "canonical source tree contains a non-file"
        );
        ensure!(
            entry.metadata()?.len() <= MAX_BLOB_BYTES,
            "canonical source file is too large"
        );
        let relative = entry.path().strip_prefix(source_root)?;
        let mut parts = Vec::new();
        for component in relative.components() {
            let Component::Normal(part) = component else {
                eyre::bail!("canonical source path is not relative");
            };
            parts.push(
                part.to_str()
                    .ok_or_else(|| eyre::eyre!("canonical source path is not UTF-8"))?,
            );
        }
        let path = format!("src/{}", parts.join("/"));
        validate_project_path(&path)?;
        ensure!(
            case.insert(path.to_ascii_lowercase()),
            "case-colliding canonical source paths at '{path}'"
        );
        let bytes = fs::read(entry.path())?;
        result.insert(path, sha256_hex(&bytes));
    }
    ensure!(
        !result.is_empty(),
        "canonical Minecraft source tree is empty"
    );
    Ok(result)
}

fn validate_project_path(path: &str) -> Result<()> {
    ensure!(path.starts_with("src/"), "path must begin with src/");
    validate_project_relative_path(path)
}

fn validate_project_relative_path(path: &str) -> Result<()> {
    ensure!(!path.is_empty(), "project path must not be empty");
    ensure!(
        !path
            .chars()
            .any(|character| character.is_control() || "\\<>:\"|?*".contains(character)),
        "source path '{path}' is not portable"
    );
    for part in path.split('/') {
        ensure!(
            !part.is_empty()
                && part != "."
                && part != ".."
                && !part.ends_with('.')
                && !part.ends_with(' ')
                && !is_windows_device_name(part),
            "source path '{path}' is not portable"
        );
    }
    Ok(())
}

fn is_windows_device_name(part: &str) -> bool {
    let upper = part
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    matches!(upper.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || (upper.len() == 4
            && (upper.starts_with("COM") || upper.starts_with("LPT"))
            && matches!(upper.as_bytes()[3], b'1'..=b'9'))
}

fn validate_tag_name(tag: &str) -> Result<()> {
    ensure!(
        !tag.is_empty()
            && tag
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b".-_".contains(&byte))
            && !tag.contains("..")
            && !tag.ends_with('.'),
        "unsafe release tag name '{tag}'"
    );
    Ok(())
}

fn validate_sha1(value: &str, label: &str) -> Result<()> {
    ensure!(
        value.len() == 40
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "{label} must be a lowercase 40-character Git SHA-1"
    );
    Ok(())
}

pub(crate) fn git_text(root: &Path, args: &[&str]) -> Result<String> {
    let output = git_stdout(root, args)?;
    Ok(String::from_utf8(output)
        .wrap_err("Git output is not UTF-8")?
        .trim()
        .to_owned())
}

fn git_stdout(root: &Path, args: &[&str]) -> Result<Vec<u8>> {
    let output = Command::new("git")
        .current_dir(root)
        .env("GIT_OPTIONAL_LOCKS", "0")
        .args(args)
        .output()
        .wrap_err_with(|| format!("cannot run git {}", args.join(" ")))?;
    ensure!(
        output.status.success(),
        "git {} failed: {}",
        args.join(" "),
        String::from_utf8_lossy(&output.stderr).trim()
    );
    Ok(output.stdout)
}

pub(crate) fn sha256_hex(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    format!("sha256:{digest:x}")
}

pub(crate) fn hash_canonical_tree(files: &BTreeMap<String, String>) -> String {
    let mut hasher = Sha256::new();
    hasher.update(b"sfm:canonical-working-source@1\0");
    for (path, hash) in files {
        hasher.update(path.as_bytes());
        hasher.update([0]);
        hasher.update(hash.as_bytes());
        hasher.update([0]);
    }
    format!("sha256:{:x}", hasher.finalize())
}

pub(crate) struct GitBlobHasher {
    child: Child,
    stdin: Option<ChildStdin>,
    stdout: Option<BufReader<ChildStdout>>,
    cache: BTreeMap<String, String>,
    finished: bool,
}

impl GitBlobHasher {
    pub(crate) fn start(root: &Path) -> Result<Self> {
        let mut child = Command::new("git")
            .current_dir(root)
            .env("GIT_OPTIONAL_LOCKS", "0")
            .args(["cat-file", "--batch"])
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()
            .wrap_err("cannot start Git blob reader")?;
        let stdin = child
            .stdin
            .take()
            .ok_or_else(|| eyre::eyre!("missing Git stdin"))?;
        let stdout = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing Git stdout"))?;
        Ok(Self {
            child,
            stdin: Some(stdin),
            stdout: Some(BufReader::new(stdout)),
            cache: BTreeMap::new(),
            finished: false,
        })
    }

    pub(crate) fn hash(&mut self, oid: &str) -> Result<String> {
        if let Some(hash) = self.cache.get(oid) {
            return Ok(hash.clone());
        }
        let hash = sha256_hex(&self.read(oid)?);
        self.cache.insert(oid.to_owned(), hash.clone());
        Ok(hash)
    }

    pub(crate) fn read(&mut self, oid: &str) -> Result<Vec<u8>> {
        validate_sha1(oid, "release blob ID")?;
        let stdin = self
            .stdin
            .as_mut()
            .ok_or_else(|| eyre::eyre!("Git blob reader is closed"))?;
        stdin.write_all(oid.as_bytes())?;
        stdin.write_all(b"\n")?;
        stdin.flush()?;

        let stdout = self
            .stdout
            .as_mut()
            .ok_or_else(|| eyre::eyre!("Git blob reader is closed"))?;
        let mut header = Vec::new();
        stdout.read_until(b'\n', &mut header)?;
        ensure!(
            header.last() == Some(&b'\n'),
            "Git blob reader ended before header"
        );
        let header = std::str::from_utf8(&header[..header.len() - 1])?;
        let mut fields = header.split(' ');
        let found_oid = fields.next().unwrap_or_default();
        let kind = fields.next().unwrap_or_default();
        let size = fields.next().unwrap_or_default();
        ensure!(
            found_oid == oid && kind == "blob" && fields.next().is_none(),
            "unexpected Git blob header '{header}'"
        );
        let size: u64 = size.parse().wrap_err("invalid Git blob size")?;
        ensure!(size <= MAX_BLOB_BYTES, "release blob {oid} is too large");
        let mut bytes = vec![0_u8; usize::try_from(size)?];
        stdout.read_exact(&mut bytes)?;
        let mut terminator = [0_u8; 1];
        stdout.read_exact(&mut terminator)?;
        ensure!(terminator == [b'\n'], "Git blob stream lost framing");
        Ok(bytes)
    }

    pub(crate) fn finish(&mut self) -> Result<()> {
        self.stdin.take();
        self.stdout.take();
        let status = self.child.wait()?;
        self.finished = true;
        ensure!(status.success(), "Git blob reader failed");
        Ok(())
    }
}

impl Drop for GitBlobHasher {
    fn drop(&mut self) {
        if !self.finished {
            let _ = self.child.kill();
            let _ = self.child.wait();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn git(root: &Path, args: &[&str]) -> String {
        git_text(root, args).unwrap()
    }

    fn write(root: &Path, relative: &str, bytes: &[u8]) {
        let path = root.join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, bytes).unwrap();
    }

    fn fixture() -> (tempfile::TempDir, ReleaseTagSpec, PathBuf) {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        git(root, &["init", "-q"]);
        git(root, &["config", "user.name", "SFM Test"]);
        git(root, &["config", "user.email", "sfm-test@example.invalid"]);
        let src = "platform/minecraft/src";
        write(root, &format!("{src}/same.txt"), b"same\n");
        write(root, &format!("{src}/changed.txt"), b"release\n");
        write(root, &format!("{src}/old.txt"), b"old\n");
        for name in GRADLE_ROOT_FILES {
            write(
                root,
                &format!("platform/minecraft/{name}"),
                format!("tagged {name}\n").as_bytes(),
            );
        }
        write(
            root,
            "platform/minecraft/gradle/wrapper/gradle-wrapper.properties",
            b"tagged wrapper\n",
        );
        git(root, &["add", "--", "platform/minecraft"]);
        git(root, &["commit", "-qm", "release"]);
        git(root, &["tag", "4.34.0-test"]);
        let commit = git(root, &["rev-parse", "HEAD"]);
        write(root, &format!("{src}/changed.txt"), b"development\n");
        write(
            root,
            "platform/minecraft/build.gradle",
            b"development Gradle\n",
        );
        fs::remove_file(root.join(src).join("old.txt")).unwrap();
        write(root, &format!("{src}/new.txt"), b"new\n");
        (
            temp,
            ReleaseTagSpec {
                target_id: "test".to_owned(),
                release_tag: "4.34.0-test".to_owned(),
                expected_commit: commit,
            },
            PathBuf::from(src),
        )
    }

    #[test]
    fn exact_four_way_classification_includes_hashes_and_release_membership() {
        let (temp, spec, _) = fixture();
        let report = compare_tagged_baselines(temp.path(), &[spec]).unwrap();
        let target = &report.targets[0];
        assert_eq!(
            (
                target.unchanged,
                target.changed,
                target.release_only,
                target.canonical_only
            ),
            (1, 1, 1, 1)
        );
        assert_eq!(
            target.paths["src/same.txt"].classification,
            BaselinePathClass::Unchanged
        );
        assert_eq!(
            target.paths["src/changed.txt"].classification,
            BaselinePathClass::Changed
        );
        assert_eq!(
            target.paths["src/old.txt"].classification,
            BaselinePathClass::ReleaseOnly
        );
        assert_eq!(
            target.paths["src/new.txt"].classification,
            BaselinePathClass::CanonicalOnly
        );
        assert_eq!(
            target.paths["src/same.txt"].release_sha256,
            target.paths["src/same.txt"].canonical_sha256
        );
        assert_ne!(
            target.paths["src/changed.txt"].release_sha256,
            target.paths["src/changed.txt"].canonical_sha256
        );
        assert_eq!(target.paths["src/new.txt"].release_blob_oid, None);
        assert_eq!(target.paths["src/old.txt"].canonical_sha256, None);
        assert_eq!(
            target.paths["src/old.txt"].release_git_mode.as_deref(),
            Some("100644")
        );
        assert_eq!(
            target.paths["src/old.txt"].release_overlay_path.as_deref(),
            Some("platform/minecraft/release-baselines/4.34.0-test/overlays/src/old.txt")
        );
        assert_eq!(target.paths["src/same.txt"].release_overlay_path, None);
        assert!(!report.jar_parity_proven);
        let json = report.to_json().unwrap();
        assert!(json.contains("\"classification\": \"release_only\""));
        assert!(!json.contains(&temp.path().display().to_string()));
    }

    #[test]
    fn report_is_repeatable_and_does_not_write_to_source_root() {
        let (temp, spec, source) = fixture();
        let source_root = temp.path().join(source);
        let before = fs::read(source_root.join("changed.txt")).unwrap();
        let first = compare_tagged_baselines(temp.path(), std::slice::from_ref(&spec))
            .unwrap()
            .to_json()
            .unwrap();
        let second = compare_tagged_baselines(temp.path(), &[spec])
            .unwrap()
            .to_json()
            .unwrap();
        assert_eq!(first, second);
        assert_eq!(fs::read(source_root.join("changed.txt")).unwrap(), before);
        assert!(!source_root.join("old.txt").exists());
    }

    #[test]
    fn materialized_import_contains_only_divergent_sources_and_exact_tagged_gradle() {
        let (temp, spec, _) = fixture();
        let root = temp.path();
        let outcomes = materialize_tagged_imports(root, &[spec]).unwrap();
        assert_eq!(outcomes.len(), 1);
        let outcome = &outcomes[0];
        assert!(outcome.created_files > 0);
        assert_eq!(outcome.reused_files, 0);
        assert_eq!(outcome.import_manifest_sha256.len(), 64);
        let import_root = root.join("platform/minecraft/release-baselines/4.34.0-test");
        let json = fs::read_to_string(import_root.join("import.json")).unwrap();
        assert_eq!(
            sha256_hex(json.as_bytes())[7..],
            outcome.import_manifest_sha256
        );
        let parsed = ReleaseBaselineReport::from_json(&json).unwrap();
        assert_eq!(parsed.targets.len(), 1);
        assert!(!parsed.canonical_source_sha256.is_empty());

        assert_eq!(
            fs::read(import_root.join("overlays/src/changed.txt")).unwrap(),
            b"release\n"
        );
        assert_eq!(
            fs::read(import_root.join("overlays/src/old.txt")).unwrap(),
            b"old\n"
        );
        assert!(!import_root.join("overlays/src/same.txt").exists());
        assert!(!import_root.join("overlays/src/new.txt").exists());
        assert_eq!(
            fs::read(import_root.join("gradle-project/build.gradle")).unwrap(),
            b"tagged build.gradle\n"
        );
        assert_eq!(
            fs::read(import_root.join("gradle-project/settings.gradle")).unwrap(),
            b"tagged settings.gradle\n"
        );
        assert_eq!(
            fs::read(import_root.join("gradle-project/gradle/wrapper/gradle-wrapper.properties"))
                .unwrap(),
            b"tagged wrapper\n"
        );
        assert!(!import_root.join("gradle-project/src").exists());
        assert!(!import_root.join("gradle-project/build").exists());
        let gradle_provenance =
            fs::read_to_string(import_root.join("gradle-provenance.json")).unwrap();
        assert!(gradle_provenance.contains("\"settings.gradle\""));
        assert!(gradle_provenance.contains("\"sha256\""));
    }

    #[test]
    fn import_is_idempotent_and_existing_mismatch_blocks_all_writes() {
        let (temp, spec, _) = fixture();
        let root = temp.path();
        let first = materialize_tagged_imports(root, std::slice::from_ref(&spec)).unwrap();
        let second = materialize_tagged_imports(root, std::slice::from_ref(&spec)).unwrap();
        assert_eq!(second[0].created_files, 0);
        assert_eq!(second[0].reused_files, first[0].created_files);

        let import_root = root.join("platform/minecraft/release-baselines/4.34.0-test");
        let old = import_root.join("overlays/src/old.txt");
        fs::write(&old, b"contributor edit\n").unwrap();
        let error = materialize_tagged_imports(root, &[spec]).unwrap_err();
        assert!(format!("{error:?}").contains("differs from pinned tag bytes"));
        assert_eq!(fs::read(old).unwrap(), b"contributor edit\n");
    }

    #[test]
    fn preflight_conflict_does_not_write_other_imports() {
        let (temp, spec, _) = fixture();
        let root = temp.path();
        let import_root = root.join("platform/minecraft/release-baselines/4.34.0-test");
        write(
            root,
            "platform/minecraft/release-baselines/4.34.0-test/gradle-project/build.gradle",
            b"owned by contributor\n",
        );
        let error = materialize_tagged_imports(root, &[spec]).unwrap_err();
        assert!(format!("{error:?}").contains("differs from pinned tag bytes"));
        assert!(!import_root.join("import.json").exists());
        assert!(!import_root.join("overlays/src/old.txt").exists());
        assert_eq!(
            fs::read(import_root.join("gradle-project/build.gradle")).unwrap(),
            b"owned by contributor\n"
        );
    }

    #[test]
    fn pinned_blob_reader_survives_canonical_edit_and_rejects_wrong_hash() {
        let (temp, spec, _) = fixture();
        let root = temp.path();
        let report = compare_tagged_baselines(root, &[spec]).unwrap();
        let target = &report.targets[0];
        let record = &target.paths["src/same.txt"];
        fs::write(
            root.join("platform/minecraft/src/same.txt"),
            b"edited after import\n",
        )
        .unwrap();
        let bytes = read_pinned_blob(
            root,
            &target.tag_commit,
            "src/same.txt",
            record.release_blob_oid.as_deref().unwrap(),
            record.release_sha256.as_deref().unwrap(),
        )
        .unwrap();
        assert_eq!(bytes, b"same\n");
        let error = read_pinned_blob(
            root,
            &target.tag_commit,
            "src/same.txt",
            record.release_blob_oid.as_deref().unwrap(),
            &sha256_hex(b"wrong"),
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("SHA-256 mismatch"));
    }

    #[test]
    fn moved_or_wrong_tag_commit_fails_closed() {
        let (temp, mut spec, _) = fixture();
        spec.expected_commit = "0000000000000000000000000000000000000000".to_owned();
        let error = compare_tagged_baselines(temp.path(), &[spec]).unwrap_err();
        assert!(format!("{error:?}").contains("moved"));
    }

    #[test]
    fn unsupported_tag_symlink_fails_closed() {
        let (temp, mut spec, _) = fixture();
        let root = temp.path();
        // Git stores symlinks as mode 120000 even when the fixture runs on Windows.
        let oid = git(root, &["rev-parse", "HEAD:platform/minecraft/src/same.txt"]);
        git(
            root,
            &[
                "update-index",
                "--add",
                "--cacheinfo",
                &format!("120000,{oid},platform/minecraft/src/link"),
            ],
        );
        git(root, &["commit", "-qm", "symlink mode"]);
        git(root, &["tag", "-f", "4.34.0-test"]);
        spec.expected_commit = git(root, &["rev-parse", "HEAD"]);
        let error = compare_tagged_baselines(root, &[spec]).unwrap_err();
        assert!(format!("{error:?}").contains("unsupported release Git entry"));
    }

    #[test]
    fn release_matrix_has_ten_distinct_pinned_targets() {
        let targets = RELEASE_4_34_0_TAGS
            .iter()
            .map(|entry| entry.0)
            .collect::<BTreeSet<_>>();
        assert_eq!(targets.len(), 10);
        assert!(targets.contains("1.21.0"));
        for (_, tag, sha) in RELEASE_4_34_0_TAGS {
            validate_tag_name(tag).unwrap();
            validate_sha1(sha, "commit").unwrap();
        }
    }
}
