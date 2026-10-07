//! Exact Gradle inputs from the pinned 1.19.4 development branch head.
//!
//! The import is a complete, ordinary Gradle project input tree. Its sibling
//! provenance file records the original Git mode and blob identity for every
//! file, so source sync can verify it without access to the old branch ref.

use super::development_baseline::DevelopmentHeadSpec;
use super::manifest::BaselineKind;
use super::manifest::ReleaseBaselineBinding;
use super::provenance::sha256;
use super::release_baseline::GitBlobHasher;
use super::release_baseline::collect_tagged_gradle_tree;
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
use walkdir::WalkDir;

pub const DEVELOPMENT_1194_CANONICAL_COMMIT: &str = "b046574be908e40d647858dedc872ab9c4305170";
pub const DEVELOPMENT_1194_TARGET_COMMIT: &str = "2e3b561c15d663fb89fd353ccc2af67eeb0c2053";
pub const DEVELOPMENT_1194_GRADLE_PROJECT: &str =
    "platform/minecraft/development-baselines/1.19.4/gradle-project";
pub const DEVELOPMENT_1194_GRADLE_PROVENANCE: &str =
    "platform/minecraft/development-baselines/1.19.4/gradle-provenance.json";

const SCHEMA: &str = "sfm:development-gradle-import@1";
const MAX_BLOB_BYTES: u64 = 128 * 1024 * 1024;
#[cfg(test)]
const CHANGED_PATHS: [&str; 4] = [
    "gradle.properties",
    "gradle/source-excludes/1.19.4/gametest-java.txt",
    "settings.gradle",
    "sfm-toolchain.lock.json",
];
const ROOT_FILES: &[&str] = &[
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
];

#[derive(Debug, Facet)]
struct DevelopmentGradleProvenance {
    schema: String,
    target_id: String,
    canonical_commit: String,
    target_commit: String,
    input_count: usize,
    changed_paths: Vec<String>,
    files: BTreeMap<String, DevelopmentGradleFile>,
}

#[derive(Debug, Facet)]
struct DevelopmentGradleFile {
    git_mode: String,
    blob_oid: String,
    sha256: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DevelopmentGradleImportReport {
    pub target_id: String,
    pub target_commit: String,
    pub gradle_project_path: String,
    pub provenance_path: String,
    /// Bare lowercase SHA-256, suitable for the typed manifest binding.
    pub provenance_sha256: String,
    pub created_files: usize,
    pub reused_files: usize,
}

/// Materialize a complete Gradle input tree from two exact development heads.
///
/// The caller records the expected target path count and canonical-to-target
/// differences from review. Existing imports are reused only when their bytes
/// and executable modes match the target tree.
///
/// # Errors
///
/// Rejects missing commits, changed tree membership or modes, unexpected
/// branch differences, corrupt Git blobs, or conflicting existing imports.
#[expect(
    clippy::too_many_lines,
    reason = "the import preflight and staged write remain one reviewable transaction"
)]
pub fn materialize_development_gradle_inputs(
    repo_root: &Path,
    spec: &DevelopmentHeadSpec,
    expected_file_count: usize,
    expected_changed_paths: &[&str],
) -> Result<DevelopmentGradleImportReport> {
    validate_target_id(&spec.target_id)?;
    ensure!(
        is_lower_hex(&spec.canonical_commit, 40) && is_lower_hex(&spec.target_commit, 40),
        "development Gradle commits must be lowercase 40-character Git SHAs"
    );
    ensure!(
        expected_file_count > 0,
        "expected Gradle input count is empty"
    );
    let mut expected_changed = expected_changed_paths.to_vec();
    for path in &expected_changed {
        validate_gradle_path(path)?;
    }
    expected_changed.sort_unstable();
    ensure!(
        expected_changed.windows(2).all(|pair| pair[0] != pair[1]),
        "duplicate expected development Gradle difference"
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

    let canonical = collect_tagged_gradle_tree(&root, &spec.canonical_commit)?;
    let target = collect_tagged_gradle_tree(&root, &spec.target_commit)?;
    ensure!(
        target.len() == expected_file_count,
        "pinned development Gradle tree has {} inputs, expected {expected_file_count}",
        target.len()
    );
    let changed = canonical
        .keys()
        .chain(target.keys())
        .collect::<BTreeSet<_>>()
        .into_iter()
        .filter_map(|path| {
            let old = canonical.get(path);
            let new = target.get(path);
            (old.map(|entry| (&entry.oid, &entry.mode))
                != new.map(|entry| (&entry.oid, &entry.mode)))
            .then_some(path.as_str())
        })
        .collect::<Vec<_>>();
    ensure!(
        changed == expected_changed,
        "pinned development Gradle differences changed: found {changed:?}, expected {expected_changed:?}"
    );
    let import_dir = format!(
        "platform/minecraft/development-baselines/{}",
        spec.target_id
    );
    let gradle_project_path = format!("{import_dir}/gradle-project");
    let provenance_path = format!("{import_dir}/gradle-provenance.json");

    let mut provenance = DevelopmentGradleProvenance {
        schema: SCHEMA.to_owned(),
        target_id: spec.target_id.clone(),
        canonical_commit: spec.canonical_commit.clone(),
        target_commit: spec.target_commit.clone(),
        input_count: target.len(),
        changed_paths: changed.into_iter().map(str::to_owned).collect(),
        files: BTreeMap::new(),
    };
    let mut desired = BTreeMap::new();
    let mut blobs = GitBlobHasher::start(&root)?;
    for (path, entry) in target {
        let bytes = blobs.read(&entry.oid)?;
        ensure!(
            bytes.len() as u64 <= MAX_BLOB_BYTES,
            "pinned Gradle blob '{path}' is too large"
        );
        ensure!(
            git_blob_oid(&bytes) == entry.oid,
            "pinned Gradle Git blob ID mismatch at '{path}'"
        );
        provenance.files.insert(
            path.clone(),
            DevelopmentGradleFile {
                git_mode: entry.mode.clone(),
                blob_oid: entry.oid,
                sha256: sha256(&bytes),
            },
        );
        insert_import_file(
            &mut desired,
            &format!("{gradle_project_path}/{path}"),
            bytes,
            &entry.mode,
        )?;
    }
    blobs.finish()?;
    let mut provenance_json = facet_json::to_string_pretty(&provenance)?;
    provenance_json.push('\n');
    let provenance_bytes = provenance_json.into_bytes();
    let provenance_sha256 = sha256(&provenance_bytes)
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre::eyre!("SHA-256 result has no prefix"))?
        .to_owned();
    insert_import_file(&mut desired, &provenance_path, provenance_bytes, "100644")?;
    let installed = stage_and_install_imports(&root, &desired)?;
    let created_files = installed.values().filter(|created| **created).count();
    Ok(DevelopmentGradleImportReport {
        target_id: spec.target_id.clone(),
        target_commit: spec.target_commit.clone(),
        gradle_project_path,
        provenance_path,
        provenance_sha256,
        created_files,
        reused_files: installed.len() - created_files,
    })
}

/// Verify the checked-in development Gradle import and optional collected
/// Gradle artifacts against their pinned per-file content and Git identities.
///
/// An empty artifact map verifies only the import. A nonempty map must contain
/// exactly the declared Gradle project inputs; settings output may include the
/// generated project-name override, while its source bytes stay exact.
///
/// # Errors
///
/// Rejects a wrong target or location, changed provenance, missing or extra
/// paths, symlinks, content/hash/mode mismatches, or changed artifact bytes.
#[expect(
    clippy::too_many_lines,
    reason = "all Gradle input provenance checks must pass before accepting any artifact"
)]
pub fn verify_development_gradle_inputs(
    repo_root: &Path,
    canonical_commit: &str,
    target_commit: &str,
    import_root: &Path,
    expected_provenance_sha256: &str,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<()> {
    ensure!(
        is_lower_hex(canonical_commit, 40),
        "development Gradle canonical commit must be a lowercase 40-character Git SHA"
    );
    ensure!(
        is_lower_hex(target_commit, 40),
        "development Gradle target commit must be a lowercase 40-character Git SHA"
    );
    ensure!(
        is_lower_hex(expected_provenance_sha256, 64),
        "development Gradle provenance SHA-256 must be 64 lowercase hex characters"
    );
    let root = repository_directory(repo_root)?;
    let provided_import_root =
        fs::canonicalize(import_root).wrap_err("cannot resolve development Gradle import root")?;
    let relative = provided_import_root
        .strip_prefix(&root)
        .wrap_err("development Gradle import escapes repository root")?;
    let parts = relative
        .components()
        .map(|component| match component {
            Component::Normal(part) => part
                .to_str()
                .ok_or_else(|| eyre::eyre!("development Gradle import path is not UTF-8")),
            _ => Err(eyre::eyre!(
                "development Gradle import path is not relative"
            )),
        })
        .collect::<Result<Vec<_>>>()?;
    ensure!(
        parts.len() == 5
            && parts[0] == "platform"
            && parts[1] == "minecraft"
            && parts[2] == "development-baselines"
            && parts[4] == "gradle-project",
        "development Gradle import is outside the declared baseline layout"
    );
    let target_id = parts[3];
    validate_target_id(target_id)?;
    let expected_import_root = root.join(format!(
        "platform/minecraft/development-baselines/{target_id}/gradle-project"
    ));
    ensure_real_directory_chain(&root, &expected_import_root)?;
    ensure!(
        provided_import_root == fs::canonicalize(&expected_import_root)?,
        "development Gradle import must use the target's pinned path"
    );
    let provenance_path = root.join(format!(
        "platform/minecraft/development-baselines/{target_id}/gradle-provenance.json"
    ));
    let metadata =
        fs::symlink_metadata(&provenance_path).wrap_err("missing development Gradle provenance")?;
    ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink(),
        "development Gradle provenance must be a real file"
    );
    let provenance_bytes = fs::read(&provenance_path)?;
    ensure!(
        sha256(&provenance_bytes) == format!("sha256:{expected_provenance_sha256}"),
        "development Gradle provenance differs from the pinned SHA-256"
    );
    let provenance_text = std::str::from_utf8(&provenance_bytes)
        .wrap_err("development Gradle provenance is not UTF-8")?;
    let provenance: DevelopmentGradleProvenance = facet_json::from_str(provenance_text)
        .wrap_err("cannot parse development Gradle provenance")?;
    ensure!(
        provenance.schema == SCHEMA
            && provenance.target_id == target_id
            && provenance.canonical_commit == canonical_commit
            && provenance.target_commit == target_commit,
        "development Gradle provenance identity does not match the pinned heads"
    );
    ensure!(
        provenance
            .changed_paths
            .iter()
            .all(|path| validate_gradle_path(path).is_ok())
            && provenance
                .changed_paths
                .windows(2)
                .all(|pair| pair[0] < pair[1]),
        "development Gradle provenance has invalid changed paths"
    );
    validate_inventory(&provenance.files)?;
    ensure!(
        provenance.input_count == provenance.files.len(),
        "development Gradle provenance input count does not match its records"
    );

    let actual_paths = scan_import_paths(&expected_import_root)?;
    let declared_paths = provenance.files.keys().cloned().collect::<BTreeSet<_>>();
    ensure!(
        actual_paths == declared_paths,
        "development Gradle import path set differs from pinned provenance"
    );
    if !artifacts.is_empty() {
        ensure!(
            artifacts.len() == provenance.files.len()
                && artifacts.keys().eq(provenance.files.keys()),
            "projected Gradle artifact path set differs from pinned provenance"
        );
    }
    for (path, record) in &provenance.files {
        let file = expected_import_root.join(path);
        let metadata = fs::symlink_metadata(&file)?;
        ensure!(
            metadata.is_file() && !metadata.file_type().is_symlink(),
            "development Gradle import '{path}' is not a regular file"
        );
        ensure!(
            metadata.len() <= MAX_BLOB_BYTES,
            "development Gradle import '{path}' is too large"
        );
        #[cfg(unix)]
        verify_unix_mode(&file, &record.git_mode)?;
        let bytes = fs::read(&file)?;
        ensure!(
            sha256(&bytes) == record.sha256 && git_blob_oid(&bytes) == record.blob_oid,
            "development Gradle import '{path}' differs from pinned blob/hash"
        );
        if let Some(artifact) = artifacts.get(path) {
            ensure!(
                artifact.source_bytes == bytes,
                "projected Gradle input '{path}' differs from pinned import"
            );
            if path != "settings.gradle" {
                ensure!(
                    artifact.output_bytes == bytes,
                    "projected Gradle output '{path}' differs from pinned import"
                );
            }
        }
    }
    Ok(())
}

/// Replace explicitly bound Gradle inputs only after the complete imported
/// development Gradle tree and its projected artifacts pass provenance checks.
/// Overlay bytes live outside the immutable import and are pinned by the
/// development preset's definition identity.
///
/// # Errors
///
/// Rejects release bindings, altered imports or overlays, unsafe paths, and
/// overlay paths absent from the pinned Gradle project. The artifact map is
/// unchanged on failure.
pub fn apply_post_baseline_gradle_sources(
    repo_root: &Path,
    binding: &ReleaseBaselineBinding,
    artifacts: &mut BTreeMap<String, ProjectedArtifact>,
) -> Result<()> {
    if binding.post_baseline_gradle_sources.is_empty() {
        return Ok(());
    }
    ensure!(
        binding.kind == BaselineKind::DevelopmentHead,
        "release-tag binding cannot apply post-baseline Gradle sources"
    );
    validate_target_id(&binding.target_id)?;
    let root = repository_directory(repo_root)?;
    let import_root = root.join(format!(
        "platform/minecraft/development-baselines/{}/gradle-project",
        binding.target_id
    ));
    verify_development_gradle_inputs(
        &root,
        binding
            .canonical_commit
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development canonical commit is missing"))?,
        &binding.tag_commit,
        &import_root,
        binding
            .gradle_provenance_sha256
            .as_deref()
            .ok_or_else(|| eyre::eyre!("development Gradle provenance hash is missing"))?,
        artifacts,
    )?;

    let mut pending = artifacts.clone();
    for (path, expected_hash) in &binding.post_baseline_gradle_sources {
        validate_gradle_path(path)?;
        ensure!(
            path.starts_with("gradle/") && path.ends_with(".gradle"),
            "post-baseline Gradle source '{path}' must be a gradle/... .gradle path"
        );
        ensure!(
            is_lower_hex(expected_hash, 64),
            "post-baseline Gradle source '{path}' needs a bare lowercase SHA-256"
        );
        let overlay_path = root.join(format!(
            "platform/minecraft/development-overlays/{}/{path}",
            binding.target_id
        ));
        ensure_real_directory_chain(
            &root,
            overlay_path
                .parent()
                .ok_or_else(|| eyre::eyre!("post-baseline Gradle source has no parent"))?,
        )?;
        let metadata = fs::symlink_metadata(&overlay_path).wrap_err_with(|| {
            format!(
                "missing post-baseline Gradle source '{}'",
                overlay_path.display()
            )
        })?;
        ensure!(
            metadata.is_file() && !metadata.file_type().is_symlink(),
            "post-baseline Gradle source '{path}' must be a real file"
        );
        ensure!(
            metadata.len() <= MAX_BLOB_BYTES,
            "post-baseline Gradle source '{path}' is too large"
        );
        let bytes = fs::read(&overlay_path)?;
        ensure!(
            sha256(&bytes) == format!("sha256:{expected_hash}"),
            "post-baseline Gradle source '{path}' differs from its pinned SHA-256"
        );
        let artifact = pending.get_mut(path).ok_or_else(|| {
            eyre::eyre!("post-baseline Gradle source '{path}' is absent from the pinned import")
        })?;
        artifact.source_path = format!(
            "platform/minecraft/development-overlays/{}/{path}",
            binding.target_id
        );
        artifact.source_bytes.clone_from(&bytes);
        artifact.output_bytes = bytes;
        artifact.overlay = Some("development-gradle-portability".to_owned());
    }
    *artifacts = pending;
    Ok(())
}

fn validate_inventory(files: &BTreeMap<String, DevelopmentGradleFile>) -> Result<()> {
    ensure!(!files.is_empty(), "development Gradle provenance is empty");
    let mut casefold = BTreeSet::new();
    for (path, record) in files {
        validate_gradle_path(path)?;
        ensure!(
            casefold.insert(path.to_ascii_lowercase()),
            "case-colliding development Gradle path '{path}'"
        );
        ensure!(
            matches!(record.git_mode.as_str(), "100644" | "100755"),
            "development Gradle path '{path}' has an invalid Git mode"
        );
        ensure!(
            is_lower_hex(&record.blob_oid, 40)
                && record
                    .sha256
                    .strip_prefix("sha256:")
                    .is_some_and(|hash| is_lower_hex(hash, 64)),
            "development Gradle path '{path}' has an invalid blob or SHA-256"
        );
    }
    for required in ROOT_FILES {
        ensure!(
            files.contains_key(*required),
            "missing Gradle input '{required}'"
        );
    }
    for required in [
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
    ] {
        ensure!(
            files.contains_key(required),
            "missing Gradle input '{required}'"
        );
    }
    Ok(())
}

fn validate_gradle_path(path: &str) -> Result<()> {
    ensure!(
        ROOT_FILES.contains(&path) || path.starts_with("gradle/"),
        "path '{path}' is outside declared Gradle inputs"
    );
    ensure!(
        !path.is_empty()
            && !path
                .chars()
                .any(|character| character.is_control() || "\\<>:\"|?*".contains(character)),
        "nonportable development Gradle path '{path}'"
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
            "nonportable development Gradle path '{path}'"
        );
    }
    Ok(())
}

fn validate_target_id(target_id: &str) -> Result<()> {
    ensure!(
        !target_id.is_empty()
            && target_id.as_bytes()[0].is_ascii_alphanumeric()
            && target_id.bytes().all(|byte| {
                byte.is_ascii_lowercase() || byte.is_ascii_digit() || b"._-".contains(&byte)
            })
            && !target_id.contains("..")
            && !target_id.ends_with('.'),
        "invalid development Gradle target ID '{target_id}'"
    );
    Ok(())
}

fn scan_import_paths(root: &Path) -> Result<BTreeSet<String>> {
    let mut paths = BTreeSet::new();
    let mut casefold = BTreeSet::new();
    for entry in WalkDir::new(root).follow_links(false).sort_by_file_name() {
        let entry = entry.wrap_err("cannot walk development Gradle import")?;
        if entry.path() == root {
            continue;
        }
        ensure!(
            !entry.file_type().is_symlink(),
            "development Gradle import traverses a symlink: '{}'",
            entry.path().display()
        );
        if entry.file_type().is_dir() {
            continue;
        }
        ensure!(
            entry.file_type().is_file(),
            "development Gradle import has a non-file entry"
        );
        let relative = entry.path().strip_prefix(root)?;
        let path = relative
            .components()
            .map(|component| match component {
                Component::Normal(part) => part
                    .to_str()
                    .map(str::to_owned)
                    .ok_or_else(|| eyre::eyre!("development Gradle path is not UTF-8")),
                _ => Err(eyre::eyre!("development Gradle path is not relative")),
            })
            .collect::<Result<Vec<_>>>()?
            .join("/");
        validate_gradle_path(&path)?;
        ensure!(
            casefold.insert(path.to_ascii_lowercase()),
            "case-colliding imported Gradle path '{path}'"
        );
        paths.insert(path);
    }
    Ok(paths)
}

fn repository_root(path: &Path) -> Result<PathBuf> {
    let root = repository_directory(path)?;
    let git_root = git_text(&root, &["rev-parse", "--show-toplevel"])?;
    ensure!(
        fs::canonicalize(git_root).wrap_err("cannot resolve Git worktree root")? == root,
        "development Gradle import requires the Git worktree root"
    );
    Ok(root)
}

fn repository_directory(path: &Path) -> Result<PathBuf> {
    let metadata = fs::symlink_metadata(path).wrap_err("cannot inspect repository root")?;
    ensure!(
        metadata.is_dir() && !metadata.file_type().is_symlink(),
        "repository root must be a real directory"
    );
    fs::canonicalize(path).wrap_err("cannot resolve repository root")
}

fn ensure_real_directory_chain(root: &Path, directory: &Path) -> Result<()> {
    let relative = directory
        .strip_prefix(root)
        .wrap_err("development Gradle import escapes repository root")?;
    let mut cursor = root.to_path_buf();
    for component in relative.components() {
        let Component::Normal(part) = component else {
            eyre::bail!("development Gradle import has a nonportable directory");
        };
        cursor.push(part);
        let metadata = fs::symlink_metadata(&cursor).wrap_err_with(|| {
            format!(
                "missing development Gradle directory '{}'",
                cursor.display()
            )
        })?;
        ensure!(
            metadata.is_dir() && !metadata.file_type().is_symlink(),
            "development Gradle directory '{}' is not real",
            cursor.display()
        );
    }
    Ok(())
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
        "development Gradle input '{}' has the wrong executable mode",
        path.display()
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::manifest::SourceProjectionManifest;
    use crate::source_projection::project_layout::collect_gradle_project_inputs;

    fn current_repository() -> PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .unwrap()
            .to_path_buf()
    }

    fn copy_import_into_temp(temp: &Path, target: &str) -> Result<PathBuf> {
        let relative = format!("platform/minecraft/development-baselines/{target}");
        let fixture = tempfile::tempdir()?;
        super::super::legacy_test_fixture::materialize(
            fixture.path(),
            &[
                &format!("{relative}/gradle-project"),
                &format!("{relative}/gradle-provenance.json"),
            ],
        )?;
        let original = fixture.path().join(&relative).join("gradle-project");
        let copied = temp.join(&relative).join("gradle-project");
        for entry in WalkDir::new(&original).follow_links(false) {
            let entry = entry?;
            let relative = entry.path().strip_prefix(&original)?;
            let destination = copied.join(relative);
            if entry.file_type().is_dir() {
                fs::create_dir_all(destination)?;
            } else {
                fs::copy(entry.path(), destination)?;
            }
        }
        let original_provenance = fixture
            .path()
            .join(&relative)
            .join("gradle-provenance.json");
        let copied_provenance = temp.join(relative).join("gradle-provenance.json");
        fs::copy(original_provenance, copied_provenance)?;
        Ok(copied)
    }

    #[test]
    fn pinned_import_rejects_content_and_path_drift() {
        let temp = tempfile::tempdir().unwrap();
        let import_root = copy_import_into_temp(temp.path(), "1.19.4").unwrap();
        let expected =
            sha256(&fs::read(temp.path().join(DEVELOPMENT_1194_GRADLE_PROVENANCE)).unwrap());
        let expected = expected.strip_prefix("sha256:").unwrap();
        verify_development_gradle_inputs(
            temp.path(),
            DEVELOPMENT_1194_CANONICAL_COMMIT,
            DEVELOPMENT_1194_TARGET_COMMIT,
            &import_root,
            expected,
            &BTreeMap::new(),
        )
        .unwrap();

        let wrong_canonical = "a".repeat(40);
        let error = verify_development_gradle_inputs(
            temp.path(),
            &wrong_canonical,
            DEVELOPMENT_1194_TARGET_COMMIT,
            &import_root,
            expected,
            &BTreeMap::new(),
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("pinned heads"));

        let mut artifacts = collect_gradle_project_inputs(&import_root).unwrap();
        verify_development_gradle_inputs(
            temp.path(),
            DEVELOPMENT_1194_CANONICAL_COMMIT,
            DEVELOPMENT_1194_TARGET_COMMIT,
            &import_root,
            expected,
            &artifacts,
        )
        .unwrap();
        artifacts
            .get_mut("settings.gradle")
            .unwrap()
            .source_bytes
            .push(b'!');
        let error = verify_development_gradle_inputs(
            temp.path(),
            DEVELOPMENT_1194_CANONICAL_COMMIT,
            DEVELOPMENT_1194_TARGET_COMMIT,
            &import_root,
            expected,
            &artifacts,
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("settings.gradle"));

        let property = import_root.join("gradle.properties");
        let original = fs::read(&property).unwrap();
        fs::write(&property, b"minecraft_version=wrong\n").unwrap();
        let error = verify_development_gradle_inputs(
            temp.path(),
            DEVELOPMENT_1194_CANONICAL_COMMIT,
            DEVELOPMENT_1194_TARGET_COMMIT,
            &import_root,
            expected,
            &BTreeMap::new(),
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("gradle.properties"));
        fs::write(&property, original).unwrap();

        fs::write(import_root.join("gradle/unexpected.gradle"), b"surprise\n").unwrap();
        let error = verify_development_gradle_inputs(
            temp.path(),
            DEVELOPMENT_1194_CANONICAL_COMMIT,
            DEVELOPMENT_1194_TARGET_COMMIT,
            &import_root,
            expected,
            &BTreeMap::new(),
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("path set"));
    }

    #[test]
    #[ignore = "one-time repository import; run when pinning a new development head"]
    fn materialize_pinned_1194_gradle_import() {
        let spec = DevelopmentHeadSpec {
            target_id: "1.19.4".to_owned(),
            canonical_commit: DEVELOPMENT_1194_CANONICAL_COMMIT.to_owned(),
            target_commit: DEVELOPMENT_1194_TARGET_COMMIT.to_owned(),
        };
        let report = materialize_development_gradle_inputs(
            &current_repository(),
            &spec,
            153,
            &CHANGED_PATHS,
        )
        .unwrap();
        eprintln!("{report:#?}");
    }

    #[test]
    fn pinned_2612_gradle_overlay_applies_only_after_verified_import() {
        let fixture = tempfile::tempdir().unwrap();
        super::super::legacy_test_fixture::materialize(
            fixture.path(),
            &[
                "platform/minecraft/source-projection.json",
                "platform/minecraft/development-baselines/26.1.2/gradle-project",
                "platform/minecraft/development-baselines/26.1.2/gradle-provenance.json",
                "platform/minecraft/development-overlays/26.1.2",
            ],
        )
        .unwrap();
        let root = fixture.path().to_path_buf();
        let manifest = SourceProjectionManifest::from_json(
            &fs::read_to_string(root.join("platform/minecraft/source-projection.json")).unwrap(),
        )
        .unwrap();
        let binding = manifest
            .preset("current-development-head-26.1.2")
            .unwrap()
            .release_baseline_for("26.1.2")
            .unwrap();
        let import_root =
            root.join("platform/minecraft/development-baselines/26.1.2/gradle-project");
        let mut artifacts = collect_gradle_project_inputs(&import_root).unwrap();
        let imported = artifacts["gradle/lockfile-features.gradle"].clone();

        apply_post_baseline_gradle_sources(&root, binding, &mut artifacts).unwrap();
        let overlaid = &artifacts["gradle/lockfile-features.gradle"];
        assert_ne!(overlaid.output_bytes, imported.output_bytes);
        assert_eq!(
            overlaid.source_path,
            "platform/minecraft/development-overlays/26.1.2/gradle/lockfile-features.gradle"
        );
        assert_eq!(
            overlaid.overlay.as_deref(),
            Some("development-gradle-portability")
        );
        assert_eq!(
            artifacts["build.gradle"].output_bytes,
            fs::read(import_root.join("build.gradle")).unwrap()
        );

        let mut wrong_hash = binding.clone();
        wrong_hash
            .post_baseline_gradle_sources
            .insert("gradle/lockfile-features.gradle".to_owned(), "0".repeat(64));
        let mut unchanged = collect_gradle_project_inputs(&import_root).unwrap();
        let original = unchanged.clone();
        let error =
            apply_post_baseline_gradle_sources(&root, &wrong_hash, &mut unchanged).unwrap_err();
        assert!(format!("{error:?}").contains("pinned SHA-256"));
        assert_eq!(unchanged, original);
    }

    #[test]
    fn pinned_2612_gradle_overlay_rejects_import_drift() {
        let manifest = SourceProjectionManifest::from_json(include_str!(
            "../../tests/fixtures/source_projection/legacy-source-projection.json"
        ))
        .unwrap();
        let binding = manifest
            .preset("current-development-head-26.1.2")
            .unwrap()
            .release_baseline_for("26.1.2")
            .unwrap();
        let temp = tempfile::tempdir().unwrap();
        let import_root = copy_import_into_temp(temp.path(), "26.1.2").unwrap();
        let mut artifacts = collect_gradle_project_inputs(&import_root).unwrap();
        let original = artifacts.clone();
        fs::write(
            import_root.join("gradle/lockfile-features.gradle"),
            b"drift\n",
        )
        .unwrap();

        let error =
            apply_post_baseline_gradle_sources(temp.path(), binding, &mut artifacts).unwrap_err();
        assert!(format!("{error:?}").contains("pinned blob/hash"));
        assert_eq!(artifacts, original);
    }
}
