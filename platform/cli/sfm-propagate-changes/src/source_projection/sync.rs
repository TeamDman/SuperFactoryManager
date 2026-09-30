//! Deterministic, fail-closed writes for one projected source root.

use super::core_inputs::CORE_ROOT;
use super::projection_catalog::ProjectionCatalog;
use super::projection_catalog::ProjectionEnvironment;
use super::projection_catalog::validate_projection_key;
use super::provenance::CATALOG_MANIFEST_SCHEMA;
use super::provenance::CatalogProjectionOwner;
use super::provenance::LEGACY_MANIFEST_SCHEMA;
use super::provenance::MAX_MANIFEST_BYTES;
use super::provenance::ProjectedFileProvenance;
use super::provenance::ProjectionProvenance;
use super::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::bail;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::ErrorKind;
use std::io::Read as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

/// Kept inside each projected root. A different preset must use a different root.
pub const MANIFEST_FILE: &str = ".sfm-source-projection-manifest.json";

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ProjectionIdentity {
    pub target_id: String,
    pub minecraft_version: String,
    pub preset_id: String,
    pub preset_definition_identity: String,
}

/// Explicit catalog ownership. Nested paths are not flattened into presets.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CatalogProjectionIdentity {
    pub target_id: String,
    pub minecraft_version: String,
    pub projection_key: String,
    pub environment: ProjectionEnvironment,
    pub context_identity: String,
}

impl CatalogProjectionIdentity {
    /// Derive ownership only from a validated catalog's authoritative values.
    ///
    /// # Errors
    /// Rejects absent/unsafe keys, unsupported versions and invalid features.
    pub fn from_catalog(catalog: &ProjectionCatalog, key: &str) -> Result<Self> {
        let entry = catalog.entry(key)?;
        let identity = Self {
            target_id: entry.target_id()?.to_owned(),
            minecraft_version: entry.minecraft_version.clone(),
            projection_key: key.to_owned(),
            environment: entry.environment,
            context_identity: catalog.context_identity(key)?,
        };
        identity
            .owner()
            .validate(&identity.target_id, &identity.minecraft_version)?;
        Ok(identity)
    }

    fn owner(&self) -> CatalogProjectionOwner {
        CatalogProjectionOwner {
            projection_key: self.projection_key.clone(),
            environment: self.environment,
            context_identity: self.context_identity.clone(),
        }
    }
}

#[derive(Clone, Copy, Debug)]
enum SyncIdentity<'a> {
    Legacy(&'a ProjectionIdentity),
    Catalog(&'a CatalogProjectionIdentity),
}

impl SyncIdentity<'_> {
    fn validate(self) -> Result<()> {
        match self {
            Self::Legacy(identity) => validate_identity(identity),
            Self::Catalog(identity) => identity
                .owner()
                .validate(&identity.target_id, &identity.minecraft_version),
        }
    }

    fn matches(self, manifest: &ProjectionProvenance) -> bool {
        match self {
            Self::Legacy(identity) => {
                manifest.schema == LEGACY_MANIFEST_SCHEMA
                    && manifest.catalog.is_none()
                    && manifest.target_id == identity.target_id
                    && manifest.minecraft_version == identity.minecraft_version
                    && manifest.preset_id == identity.preset_id
                    && manifest.preset_definition_identity == identity.preset_definition_identity
            }
            Self::Catalog(identity) => {
                manifest.schema == CATALOG_MANIFEST_SCHEMA
                    && manifest.target_id == identity.target_id
                    && manifest.minecraft_version == identity.minecraft_version
                    && manifest.catalog.as_ref() == Some(&identity.owner())
            }
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ProjectedArtifact {
    /// Canonical path relative to the selected primary or overlay source root.
    pub source_path: String,
    pub source_bytes: Vec<u8>,
    pub output_bytes: Vec<u8>,
    pub overlay: Option<String>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SyncMode {
    /// Report changes without writing. Conflicts are still errors.
    DryRun,
    /// Require the destination to be byte-for-byte current, without writing.
    Check,
    /// Stage the complete change and apply it after a second preflight.
    Apply,
    /// Accept a contributor edit only after authored inputs render to those
    /// exact output bytes; never write or delete a generated source file.
    Reconcile,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct SyncReport {
    pub created: Vec<String>,
    pub updated: Vec<String>,
    pub unchanged: Vec<String>,
    pub manifest_changed: bool,
}

impl SyncReport {
    #[must_use]
    pub fn needs_write(&self) -> bool {
        self.manifest_changed || !self.created.is_empty() || !self.updated.is_empty()
    }
}

struct Inspection {
    report: SyncReport,
    previous: Option<ProjectionProvenance>,
    previous_manifest_bytes: Option<Vec<u8>>,
}

struct AppliedFile {
    destination: PathBuf,
    backup: Option<PathBuf>,
    installed_hash: Option<String>,
}

/// Synchronize a target/preset's complete desired file set into its own root.
///
/// An existing manifest owns only the paths it lists. A new output path that
/// collides with an unowned file is never adopted, even if its bytes match.
/// Files removed from the desired set are reported as stale and never deleted.
/// A maintainer can delete such a file explicitly, then use `Reconcile` to
/// remove its provenance entry without deleting or overwriting anything else.
/// This operation is not a concurrency protocol for editors that write during
/// the final filesystem replacement window.
///
/// # Errors
///
/// Rejects unsafe paths, different manifest identities, edited generated
/// files, unexpected missing outputs, stale outputs, unowned collisions, and failed I/O. No
/// destination file is changed until all input and ownership checks pass.
pub fn sync_projection(
    destination_root: &Path,
    identity: &ProjectionIdentity,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    mode: SyncMode,
) -> Result<SyncReport> {
    sync_owned_projection(
        destination_root,
        SyncIdentity::Legacy(identity),
        artifacts,
        mode,
    )
}

/// Synchronize one named projection with the unchanged ownership transaction.
///
/// Production callers first derive the exact destination and Git policy with
/// `named_root::catalog_projection_root`. This low-level writer never adopts a
/// legacy owner, changes a context identity, or deletes stale outputs.
///
/// # Errors
/// Rejects invalid named identities, non-core provenance, unsafe paths and all
/// existing contributor/ownership conflicts before changing destination files.
pub fn sync_catalog_projection(
    destination_root: &Path,
    identity: &CatalogProjectionIdentity,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    mode: SyncMode,
) -> Result<SyncReport> {
    validate_catalog_artifacts(artifacts)?;
    sync_owned_projection(
        destination_root,
        SyncIdentity::Catalog(identity),
        artifacts,
        mode,
    )
}

fn sync_owned_projection(
    destination_root: &Path,
    identity: SyncIdentity<'_>,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    mode: SyncMode,
) -> Result<SyncReport> {
    identity.validate()?;
    validate_artifacts(artifacts)?;
    let destination_root = absolute_root(destination_root)?;
    inspect_root(&destination_root)?;

    let desired = desired_manifest(identity, artifacts);
    let desired_bytes = desired.to_json()?.into_bytes();
    let reconcile = mode == SyncMode::Reconcile;
    let first = inspect(
        &destination_root,
        identity,
        artifacts,
        &desired_bytes,
        reconcile,
    )?;

    match mode {
        SyncMode::DryRun => return Ok(first.report),
        SyncMode::Check => {
            ensure!(
                !first.report.needs_write(),
                "source projection is out of date: create [{}], update [{}], manifest_changed={}",
                first.report.created.join(", "),
                first.report.updated.join(", "),
                first.report.manifest_changed
            );
            return Ok(first.report);
        }
        SyncMode::Apply if !first.report.needs_write() => return Ok(first.report),
        SyncMode::Apply => {}
        SyncMode::Reconcile => {
            ensure!(
                first.report.created.is_empty() && first.report.updated.is_empty(),
                "reconciliation may only accept already-matching generated edits; create/update the remaining outputs with a separate sync"
            );
            if !first.report.manifest_changed {
                return Ok(first.report);
            }
        }
    }

    // Stage on the destination volume so each installed file can be renamed
    // into place. The stage is outside the destination root.
    let stage_parent = nearest_existing_parent(&destination_root)?;
    let stage = tempfile::Builder::new()
        .prefix(".sfm-source-projection-stage-")
        .tempdir_in(stage_parent)
        .wrap_err("could not create source projection staging directory")?;
    for path in first.report.created.iter().chain(&first.report.updated) {
        let staged = stage.path().join("files").join(path);
        let output_stage_directory = staged
            .parent()
            .ok_or_else(|| eyre::eyre!("generated output '{path}' has no staging parent"))?;
        fs::create_dir_all(output_stage_directory)?;
        let artifact = artifacts.get(path).ok_or_else(|| {
            eyre::eyre!("generated output '{path}' disappeared from the input set")
        })?;
        fs::write(&staged, &artifact.output_bytes)
            .wrap_err_with(|| format!("could not stage generated file '{path}'"))?;
        // The standalone Gradle project must remain executable on Unix. The
        // staged file is renamed into place, so its mode is the output mode.
        #[cfg(unix)]
        if path == "gradlew" {
            use std::os::unix::fs::PermissionsExt as _;
            fs::set_permissions(&staged, fs::Permissions::from_mode(0o755))
                .wrap_err("could not make generated Gradle wrapper executable")?;
        }
    }
    let staged_manifest = stage.path().join("manifest.json");
    fs::write(&staged_manifest, &desired_bytes)
        .wrap_err("could not stage source projection manifest")?;

    // A contributor could edit an output while staging runs. Repeat all
    // ownership checks before changing any destination file.
    let second = inspect(
        &destination_root,
        identity,
        artifacts,
        &desired_bytes,
        reconcile,
    )?;
    ensure!(
        second.report == first.report
            && second.previous_manifest_bytes == first.previous_manifest_bytes,
        "source projection changed while staging; no destination file was written"
    );

    apply_staged(
        &destination_root,
        artifacts,
        &first,
        stage,
        &staged_manifest,
    )?;
    Ok(first.report)
}

fn desired_manifest(
    identity: SyncIdentity<'_>,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> ProjectionProvenance {
    let mut manifest = match identity {
        SyncIdentity::Legacy(identity) => ProjectionProvenance::new(
            &identity.target_id,
            &identity.minecraft_version,
            &identity.preset_id,
            &identity.preset_definition_identity,
        ),
        SyncIdentity::Catalog(identity) => ProjectionProvenance::new_catalog(
            &identity.target_id,
            &identity.minecraft_version,
            identity.owner(),
        ),
    };
    for (path, artifact) in artifacts {
        manifest.files.insert(
            path.clone(),
            ProjectedFileProvenance {
                source_path: artifact.source_path.clone(),
                source_sha256: sha256(&artifact.source_bytes),
                overlay: artifact.overlay.clone(),
                output_sha256: sha256(&artifact.output_bytes),
            },
        );
    }
    manifest
}

fn inspect(
    root: &Path,
    identity: SyncIdentity<'_>,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    desired_bytes: &[u8],
    reconcile: bool,
) -> Result<Inspection> {
    inspect_root(root)?;
    let manifest_path = root.join(MANIFEST_FILE);
    let previous_manifest_bytes = read_manifest_if_present(&manifest_path)?;
    let previous = if let Some(bytes) = &previous_manifest_bytes {
        let text = std::str::from_utf8(bytes).wrap_err("projection manifest is not UTF-8")?;
        let manifest = ProjectionProvenance::from_json(text)
            .wrap_err("could not read existing source projection manifest")?;
        ensure!(
            identity.matches(&manifest),
            "source projection root belongs to schema='{}', target='{}', minecraft='{}', preset='{}', definition='{}', catalog={:?}, not {identity:?}",
            manifest.schema,
            manifest.target_id,
            manifest.minecraft_version,
            manifest.preset_id,
            manifest.preset_definition_identity,
            manifest.catalog
        );
        ensure!(
            manifest.to_json()?.as_bytes() == bytes,
            "source projection manifest has changed or noncanonical bytes; reconcile it explicitly"
        );
        validate_previous_manifest(&manifest)?;
        Some(manifest)
    } else {
        None
    };

    let mut stale = Vec::new();
    if let Some(previous) = &previous {
        for (path, provenance) in &previous.files {
            let destination = root.join(path);
            inspect_output_parents(root, path)?;
            let actual = read_regular_file_if_present(&destination)?;
            let Some(actual) = actual else {
                if reconcile && !artifacts.contains_key(path) {
                    // The maintainer has explicitly removed a no-longer-
                    // projected file. Reconciliation only updates ownership;
                    // it never performs the deletion itself.
                    continue;
                }
                bail!("previously generated file '{path}' is missing; reconcile before sync");
            };
            let matches_previous = sha256(&actual) == provenance.output_sha256;
            let matches_new_projection = reconcile
                && artifacts
                    .get(path)
                    .is_some_and(|artifact| actual == artifact.output_bytes);
            ensure!(
                matches_previous || matches_new_projection,
                "previously generated file '{path}' was edited; backpropagate it to authored sources before reconciliation"
            );
            if !artifacts.contains_key(path) {
                stale.push(path.clone());
            }
        }
    }
    ensure!(
        stale.is_empty(),
        "stale generated files would need removal: [{}]; reconcile explicitly before sync",
        stale.join(", ")
    );

    let mut report = SyncReport {
        manifest_changed: previous_manifest_bytes.as_deref() != Some(desired_bytes),
        ..SyncReport::default()
    };
    for (path, artifact) in artifacts {
        inspect_output_parents(root, path)?;
        let destination = root.join(path);
        let actual = read_regular_file_if_present(&destination)?;
        match (actual, previous.as_ref().and_then(|p| p.files.get(path))) {
            (Some(_), None) => bail!(
                "generated output '{path}' collides with an unowned file; reconcile before sync"
            ),
            (None, Some(_)) => {
                bail!("previously generated file '{path}' is missing; reconcile before sync")
            }
            (None, None) => report.created.push(path.clone()),
            (Some(actual), Some(_)) if actual == artifact.output_bytes => {
                report.unchanged.push(path.clone());
            }
            (Some(_), Some(_)) => report.updated.push(path.clone()),
        }
    }
    Ok(Inspection {
        report,
        previous,
        previous_manifest_bytes,
    })
}

fn apply_staged(
    root: &Path,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
    inspection: &Inspection,
    stage: tempfile::TempDir,
    staged_manifest: &Path,
) -> Result<()> {
    let mut paths: Vec<&String> = inspection
        .report
        .created
        .iter()
        .chain(&inspection.report.updated)
        .collect();
    paths.sort();
    let mut applied = Vec::new();
    let write_result: Result<()> = (|| {
        for (index, path) in paths.iter().enumerate() {
            let destination = root.join(path);
            let expected_old = inspection
                .previous
                .as_ref()
                .and_then(|manifest| manifest.files.get(*path))
                .map(|entry| entry.output_sha256.as_str());
            write_one(
                root,
                path,
                &destination,
                &stage.path().join("files").join(path),
                stage.path().join("backup").join(index.to_string()),
                expected_old,
                &mut applied,
            )?;
            ensure!(
                sha256(&fs::read(&destination)?) == sha256(&artifacts[*path].output_bytes),
                "generated output '{path}' did not match staged bytes after writing"
            );
        }
        if inspection.report.manifest_changed {
            let manifest_path = root.join(MANIFEST_FILE);
            let actual = read_regular_file_if_present(&manifest_path)?;
            ensure!(
                actual == inspection.previous_manifest_bytes,
                "source projection manifest changed during apply"
            );
            let previous_manifest_hash = inspection
                .previous_manifest_bytes
                .as_ref()
                .map(|bytes| sha256(bytes));
            write_one(
                root,
                MANIFEST_FILE,
                &manifest_path,
                staged_manifest,
                stage.path().join("backup").join("manifest"),
                previous_manifest_hash.as_deref(),
                &mut applied,
            )?;
        }
        Ok(())
    })();
    if let Err(error) = write_result {
        let rollback_errors = rollback(&applied);
        if rollback_errors.is_empty() {
            return Err(error.wrap_err("source projection apply failed and was rolled back"));
        }
        // Backups must survive if a filesystem error also blocks rollback.
        let stage_path = stage.path().to_path_buf();
        // The caller owns TempDir, so it will otherwise delete recovery data.
        // Preserve backups by copying them to a retained sibling before return.
        let retained = stage_path.with_extension("recovery");
        if fs::rename(&stage_path, &retained).is_ok() {
            return Err(error.wrap_err(format!(
                "source projection rollback failed: {}; backups retained at '{}'",
                rollback_errors.join("; "),
                retained.display()
            )));
        }
        std::mem::forget(stage);
        return Err(error.wrap_err(format!(
            "source projection rollback failed: {}; inspect staging directory '{}' immediately",
            rollback_errors.join("; "),
            stage_path.display()
        )));
    }
    Ok(())
}

fn write_one(
    root: &Path,
    relative_path: &str,
    destination: &Path,
    staged: &Path,
    backup: PathBuf,
    expected_old_hash: Option<&str>,
    applied: &mut Vec<AppliedFile>,
) -> Result<()> {
    inspect_output_parents(root, relative_path)?;
    let actual = read_regular_file_if_present(destination)?;
    match (actual.as_deref(), expected_old_hash) {
        (Some(bytes), Some(expected)) if sha256(bytes) == expected => {}
        (None, None) => {}
        _ => bail!("generated output '{relative_path}' changed during apply"),
    }
    fs::create_dir_all(destination.parent().expect("validated output has parent"))?;
    let staged_hash = sha256(&fs::read(staged)?);
    let previous_backup = if actual.is_some() {
        fs::create_dir_all(backup.parent().expect("stage backup has parent"))?;
        fs::rename(destination, &backup)
            .wrap_err_with(|| format!("could not back up generated output '{relative_path}'"))?;
        Some(backup)
    } else {
        None
    };
    applied.push(AppliedFile {
        destination: destination.to_path_buf(),
        backup: previous_backup,
        installed_hash: None,
    });
    fs::rename(staged, destination)
        .wrap_err_with(|| format!("could not install staged output '{relative_path}'"))?;
    applied
        .last_mut()
        .expect("the applied write was just recorded")
        .installed_hash = Some(staged_hash);
    Ok(())
}

fn rollback(applied: &[AppliedFile]) -> Vec<String> {
    let mut errors = Vec::new();
    for entry in applied.iter().rev() {
        match fs::symlink_metadata(&entry.destination) {
            Ok(_) => {
                let Some(installed_hash) = &entry.installed_hash else {
                    errors.push(format!(
                        "{} was occupied before the staged file landed",
                        entry.destination.display()
                    ));
                    continue;
                };
                let actual = match read_regular_file_if_present(&entry.destination) {
                    Ok(Some(bytes)) => bytes,
                    Ok(None) => unreachable!("metadata reported a present file"),
                    Err(error) => {
                        errors.push(format!("{}: {error}", entry.destination.display()));
                        continue;
                    }
                };
                if sha256(&actual) != *installed_hash {
                    errors.push(format!(
                        "{} changed after installation; preserving it and its backup",
                        entry.destination.display()
                    ));
                    continue;
                }
                if let Err(error) = fs::remove_file(&entry.destination) {
                    errors.push(format!("{}: {error}", entry.destination.display()));
                    continue;
                }
            }
            Err(error) if error.kind() == ErrorKind::NotFound => {}
            Err(error) => {
                errors.push(format!("{}: {error}", entry.destination.display()));
                continue;
            }
        }
        if let Some(backup) = &entry.backup
            && let Err(error) = fs::rename(backup, &entry.destination)
        {
            errors.push(format!("{}: {error}", entry.destination.display()));
        }
    }
    errors
}

fn validate_identity(identity: &ProjectionIdentity) -> Result<()> {
    for (field, value) in [
        ("target_id", identity.target_id.as_str()),
        ("minecraft_version", identity.minecraft_version.as_str()),
        ("preset_id", identity.preset_id.as_str()),
    ] {
        ensure!(
            !value.is_empty()
                && value
                    .chars()
                    .all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || "_.-".contains(ch))
                && value != "."
                && value != "..",
            "invalid source projection {field} '{value}'"
        );
    }
    let fingerprint = identity
        .preset_definition_identity
        .strip_prefix("blake3:")
        .unwrap_or("");
    ensure!(
        fingerprint.len() == 64
            && fingerprint
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "invalid source projection preset definition identity"
    );
    Ok(())
}

fn validate_artifacts(artifacts: &BTreeMap<String, ProjectedArtifact>) -> Result<()> {
    let mut casefolded = BTreeSet::new();
    for (path, artifact) in artifacts {
        validate_relative_path(path)?;
        ensure!(
            !path
                .split('/')
                .next()
                .is_some_and(|segment| segment.eq_ignore_ascii_case(MANIFEST_FILE)),
            "generated output cannot replace the source projection manifest"
        );
        ensure!(
            casefolded.insert(path.to_lowercase()),
            "generated outputs collide on a case-insensitive filesystem: '{path}'"
        );
        validate_relative_path(&artifact.source_path)?;
        if let Some(overlay) = &artifact.overlay {
            validate_identity_part("overlay", overlay)?;
        }
    }
    validate_no_file_directory_collisions(&casefolded)?;
    Ok(())
}

pub(crate) fn validate_catalog_artifacts(
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<()> {
    validate_artifacts(artifacts)?;
    validate_catalog_case_components(artifacts.keys().map(String::as_str))?;
    for (path, artifact) in artifacts {
        validate_projection_key(path)?;
        ensure!(
            !path.split('/').any(|part| matches!(
                part.to_ascii_lowercase().as_str(),
                ".git" | ".gradle" | ".idea"
            )) && !path.split('/').next().is_some_and(|part| matches!(
                part.to_ascii_lowercase().as_str(),
                "build" | "run" | "target"
            )),
            "catalog output `{path}` names Git, IDE or runtime state"
        );
        validate_catalog_source(&artifact.source_path, artifact.overlay.as_deref())?;
    }
    Ok(())
}

fn validate_catalog_case_components<'a>(paths: impl IntoIterator<Item = &'a str>) -> Result<()> {
    let mut prefixes = BTreeMap::new();
    for path in paths {
        for index in path
            .match_indices('/')
            .map(|(index, _)| index)
            .chain(std::iter::once(path.len()))
        {
            let prefix = &path[..index];
            if let Some(previous) = prefixes.insert(prefix.to_ascii_lowercase(), prefix) {
                ensure!(
                    previous == prefix,
                    "catalog output components `{previous}` and `{prefix}` collide by case"
                );
            }
        }
    }
    Ok(())
}

fn validate_catalog_source(source_path: &str, overlay: Option<&str>) -> Result<()> {
    validate_projection_key(source_path)?;
    ensure!(
        source_path
            .strip_prefix(CORE_ROOT)
            .is_some_and(|tail| tail.starts_with('/') && tail.len() > 1)
            && overlay.is_none(),
        "catalog artifact provenance must be core-owned without a historical overlay"
    );
    Ok(())
}

fn validate_previous_manifest(manifest: &ProjectionProvenance) -> Result<()> {
    if manifest.catalog.is_some() {
        validate_catalog_case_components(manifest.files.keys().map(String::as_str))?;
    }
    let mut casefolded = BTreeSet::new();
    for (path, file) in &manifest.files {
        validate_relative_path(path)?;
        ensure!(
            !path
                .split('/')
                .next()
                .is_some_and(|segment| segment.eq_ignore_ascii_case(MANIFEST_FILE)),
            "source projection manifest lists itself as generated output"
        );
        ensure!(
            casefolded.insert(path.to_lowercase()),
            "source projection manifest has case-colliding paths"
        );
        validate_relative_path(&file.source_path)?;
        if manifest.catalog.is_some() {
            validate_catalog_source(&file.source_path, file.overlay.as_deref())?;
        }
        validate_digest(&file.source_sha256)?;
        validate_digest(&file.output_sha256)?;
        if let Some(overlay) = &file.overlay {
            validate_identity_part("overlay", overlay)?;
        }
    }
    validate_no_file_directory_collisions(&casefolded)?;
    Ok(())
}

fn validate_no_file_directory_collisions(paths: &BTreeSet<String>) -> Result<()> {
    for path in paths {
        for (index, byte) in path.bytes().enumerate() {
            if byte == b'/' {
                ensure!(
                    !paths.contains(&path[..index]),
                    "source projection path '{path}' requires another generated file to be a directory"
                );
            }
        }
    }
    Ok(())
}

fn validate_digest(value: &str) -> Result<()> {
    let hex = value.strip_prefix("sha256:").unwrap_or("");
    ensure!(
        hex.len() == 64 && hex.bytes().all(|byte| byte.is_ascii_hexdigit()),
        "invalid source projection SHA-256 digest"
    );
    Ok(())
}

fn validate_identity_part(field: &str, value: &str) -> Result<()> {
    ensure!(
        !value.is_empty()
            && value
                .chars()
                .all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || "_.-".contains(ch))
            && value != "."
            && value != "..",
        "invalid source projection {field} '{value}'"
    );
    Ok(())
}

fn validate_relative_path(path: &str) -> Result<()> {
    ensure!(!path.is_empty(), "empty source projection path");
    ensure!(
        !Path::new(path).is_absolute()
            && !path.contains('\\')
            && !path.contains(':')
            && !path.starts_with('/')
            && !path.ends_with('/'),
        "noncanonical source projection path '{path}'"
    );
    for segment in path.split('/') {
        ensure!(
            !segment.is_empty()
                && segment != "."
                && segment != ".."
                && !segment.ends_with(' ')
                && !segment.ends_with('.')
                && !segment
                    .chars()
                    .any(|ch| ch.is_control() || "<>\"|?*".contains(ch)),
            "noncanonical source projection path '{path}'"
        );
        let stem = segment.split('.').next().unwrap_or("").to_ascii_uppercase();
        ensure!(
            !matches!(
                stem.as_str(),
                "CON"
                    | "PRN"
                    | "AUX"
                    | "NUL"
                    | "COM1"
                    | "COM2"
                    | "COM3"
                    | "COM4"
                    | "COM5"
                    | "COM6"
                    | "COM7"
                    | "COM8"
                    | "COM9"
                    | "LPT1"
                    | "LPT2"
                    | "LPT3"
                    | "LPT4"
                    | "LPT5"
                    | "LPT6"
                    | "LPT7"
                    | "LPT8"
                    | "LPT9"
            ),
            "reserved Windows name in source projection path '{path}'"
        );
    }
    Ok(())
}

fn absolute_root(root: &Path) -> Result<PathBuf> {
    ensure!(
        !root
            .components()
            .any(|component| component == Component::ParentDir),
        "source projection destination cannot contain '..'"
    );
    let absolute = if root.is_absolute() {
        root.to_path_buf()
    } else {
        std::env::current_dir()?.join(root)
    };
    ensure!(
        absolute.parent().is_some() && absolute.file_name().is_some(),
        "source projection destination cannot be a filesystem root"
    );
    Ok(absolute)
}

fn inspect_root(root: &Path) -> Result<()> {
    for ancestor in root.ancestors() {
        match fs::symlink_metadata(ancestor) {
            Ok(metadata) => {
                ensure!(
                    !is_reparse(&metadata),
                    "source projection destination traverses a symlink or reparse point: '{}'",
                    ancestor.display()
                );
                ensure!(
                    metadata.is_dir(),
                    "source projection destination traverses a non-directory: '{}'",
                    ancestor.display()
                );
            }
            Err(error) if error.kind() == ErrorKind::NotFound => {}
            Err(error) => return Err(error).wrap_err("could not inspect projection destination"),
        }
    }
    Ok(())
}

fn nearest_existing_parent(root: &Path) -> Result<&Path> {
    let mut current = root
        .parent()
        .ok_or_else(|| eyre::eyre!("destination has no parent"))?;
    loop {
        match fs::symlink_metadata(current) {
            Ok(metadata) if metadata.is_dir() && !is_reparse(&metadata) => {
                return Ok(current);
            }
            Ok(_) => bail!("source projection staging parent is a reparse point or non-directory"),
            Err(error) if error.kind() == ErrorKind::NotFound => {
                current = current
                    .parent()
                    .ok_or_else(|| eyre::eyre!("destination has no existing ancestor"))?;
            }
            Err(error) => return Err(error).wrap_err("could not inspect projection parent"),
        }
    }
}

fn inspect_output_parents(root: &Path, path: &str) -> Result<()> {
    let mut parent = root
        .join(path)
        .parent()
        .expect("validated path has parent")
        .to_path_buf();
    while parent.starts_with(root) {
        match fs::symlink_metadata(&parent) {
            Ok(metadata) => {
                ensure!(
                    !is_reparse(&metadata) && metadata.is_dir(),
                    "generated output '{path}' traverses a symlink, reparse point or non-directory"
                );
            }
            Err(error) if error.kind() == ErrorKind::NotFound => {}
            Err(error) => return Err(error).wrap_err("could not inspect generated output parent"),
        }
        if parent == root {
            break;
        }
        parent = parent
            .parent()
            .expect("relative path remains under root")
            .to_path_buf();
    }
    Ok(())
}

fn read_regular_file_if_present(path: &Path) -> Result<Option<Vec<u8>>> {
    match fs::symlink_metadata(path) {
        Ok(metadata) => {
            ensure!(
                metadata.is_file() && !is_reparse(&metadata),
                "source projection path is not a regular non-reparse file: '{}'",
                path.display()
            );
            Ok(Some(fs::read(path).wrap_err_with(|| {
                format!("could not read '{}'", path.display())
            })?))
        }
        Err(error) if error.kind() == ErrorKind::NotFound => Ok(None),
        Err(error) => {
            Err(error).wrap_err_with(|| format!("could not inspect '{}'", path.display()))
        }
    }
}

fn read_manifest_if_present(path: &Path) -> Result<Option<Vec<u8>>> {
    match fs::symlink_metadata(path) {
        Ok(metadata) => {
            ensure!(
                metadata.is_file() && !is_reparse(&metadata),
                "source projection manifest is not a regular non-reparse file"
            );
            ensure!(
                metadata.len() <= MAX_MANIFEST_BYTES as u64,
                "source projection manifest exceeds the {MAX_MANIFEST_BYTES}-byte limit"
            );
            let mut bytes = Vec::new();
            fs::File::open(path)?
                .take(MAX_MANIFEST_BYTES as u64 + 1)
                .read_to_end(&mut bytes)?;
            ensure!(
                bytes.len() <= MAX_MANIFEST_BYTES,
                "source projection manifest exceeds the {MAX_MANIFEST_BYTES}-byte limit"
            );
            Ok(Some(bytes))
        }
        Err(error) if error.kind() == ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error).wrap_err("could not inspect source projection manifest"),
    }
}

fn is_reparse(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.file_attributes() & 0x0000_0400 != 0
    }
    #[cfg(not(windows))]
    {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(windows)]
    fn create_junction(link: &Path, target: &Path) {
        use std::os::windows::fs::MetadataExt as _;

        let output = std::process::Command::new("cmd.exe")
            .args(["/d", "/c", "mklink", "/J"])
            .arg(link)
            .arg(target)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "junction setup failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        assert_ne!(
            fs::symlink_metadata(link).unwrap().file_attributes() & 0x0000_0400,
            0,
            "fixture must be a Windows reparse point"
        );
    }

    fn identity(preset: &str) -> ProjectionIdentity {
        ProjectionIdentity {
            target_id: "mc_1_19_2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            preset_id: preset.to_owned(),
            preset_definition_identity: format!("blake3:{}", "a".repeat(64)),
        }
    }

    fn artifact(output: &str) -> ProjectedArtifact {
        ProjectedArtifact {
            source_path: "src/main/java/Example.java".to_owned(),
            source_bytes: b"class Example {}\n".to_vec(),
            output_bytes: output.as_bytes().to_vec(),
            overlay: None,
        }
    }

    fn files(output: &str) -> BTreeMap<String, ProjectedArtifact> {
        BTreeMap::from([("src/main/java/Example.java".to_owned(), artifact(output))])
    }

    fn catalog_identity() -> CatalogProjectionIdentity {
        CatalogProjectionIdentity {
            target_id: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            projection_key: "custom/nested/key".to_owned(),
            environment: ProjectionEnvironment::Release,
            context_identity: format!("blake3:{}", "a".repeat(64)),
        }
    }

    fn catalog_artifact(output: &str) -> ProjectedArtifact {
        let mut artifact = artifact(output);
        artifact.source_path = format!("{CORE_ROOT}/{}", artifact.source_path);
        artifact
    }

    fn catalog_files(output: &str) -> BTreeMap<String, ProjectedArtifact> {
        BTreeMap::from([(
            "src/main/java/Example.java".to_owned(),
            catalog_artifact(output),
        )])
    }

    #[test]
    fn named_sync_is_deterministic_and_keeps_the_explicit_owner() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("custom/nested/key");
        let identity = catalog_identity();
        let wanted = catalog_files("first\n");
        let dry =
            sync_catalog_projection(&destination, &identity, &wanted, SyncMode::DryRun).unwrap();
        assert_eq!(dry.created, vec!["src/main/java/Example.java"]);
        assert!(!destination.exists());
        sync_catalog_projection(&destination, &identity, &wanted, SyncMode::Apply).unwrap();
        let manifest = fs::read_to_string(destination.join(MANIFEST_FILE)).unwrap();
        let parsed = ProjectionProvenance::from_json(&manifest).unwrap();
        assert_eq!(parsed.schema, CATALOG_MANIFEST_SCHEMA);
        assert_eq!(parsed.catalog.unwrap(), identity.owner());
        assert!(!manifest.contains("preset_id"));
        assert!(
            !sync_catalog_projection(&destination, &identity, &wanted, SyncMode::Check)
                .unwrap()
                .needs_write()
        );
        assert!(
            !sync_catalog_projection(&destination, &identity, &wanted, SyncMode::Apply)
                .unwrap()
                .needs_write()
        );
        assert_eq!(
            fs::read_to_string(destination.join(MANIFEST_FILE)).unwrap(),
            manifest
        );
        let updated = catalog_files("second\n");
        assert_eq!(
            sync_catalog_projection(&destination, &identity, &updated, SyncMode::Apply)
                .unwrap()
                .updated,
            vec!["src/main/java/Example.java"]
        );
    }

    #[test]
    fn named_context_changes_and_legacy_owners_cannot_claim_an_existing_root() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("named");
        let named = catalog_identity();
        let files = catalog_files("unchanged\n");
        sync_catalog_projection(&destination, &named, &files, SyncMode::Apply).unwrap();
        let manifest_before = fs::read(destination.join(MANIFEST_FILE)).unwrap();
        for changed in ["key", "environment", "fingerprint", "target"] {
            let mut wrong = named.clone();
            match changed {
                "key" => wrong.projection_key = "another/nested/key".to_owned(),
                "environment" => wrong.environment = ProjectionEnvironment::Dev,
                "fingerprint" => wrong.context_identity = format!("blake3:{}", "b".repeat(64)),
                "target" => {
                    wrong.target_id = "1.19.4".to_owned();
                    wrong.minecraft_version = "1.19.4".to_owned();
                }
                _ => unreachable!(),
            }
            for mode in [SyncMode::Apply, SyncMode::Reconcile] {
                let error = sync_catalog_projection(&destination, &wrong, &files, mode)
                    .unwrap_err()
                    .to_string();
                assert!(error.contains("belongs to"), "{changed}: {error}");
            }
        }
        let mut legacy = identity("released-4.34.0");
        legacy.target_id = "1.19.2".to_owned();
        assert!(
            sync_projection(&destination, &legacy, &files, SyncMode::Apply)
                .unwrap_err()
                .to_string()
                .contains("belongs to")
        );
        assert_eq!(
            fs::read(destination.join(MANIFEST_FILE)).unwrap(),
            manifest_before
        );
        let old_destination = temporary.path().join("legacy");
        sync_projection(&old_destination, &legacy, &files, SyncMode::Apply).unwrap();
        assert!(
            sync_catalog_projection(&old_destination, &named, &files, SyncMode::Reconcile)
                .unwrap_err()
                .to_string()
                .contains("belongs to")
        );
    }

    #[test]
    fn named_contributor_edits_require_exact_backpropagation_before_reconcile() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("named");
        let identity = catalog_identity();
        sync_catalog_projection(
            &destination,
            &identity,
            &catalog_files("original\n"),
            SyncMode::Apply,
        )
        .unwrap();
        let output = destination.join("src/main/java/Example.java");
        fs::write(&output, b"contributor edit\n").unwrap();
        let before = fs::read(destination.join(MANIFEST_FILE)).unwrap();
        let mut wrong = catalog_files("different\n");
        wrong.insert(
            "src/main/java/Other.java".to_owned(),
            catalog_artifact("other\n"),
        );
        for mode in [SyncMode::Apply, SyncMode::Reconcile] {
            assert!(
                sync_catalog_projection(&destination, &identity, &wrong, mode)
                    .unwrap_err()
                    .to_string()
                    .contains("backpropagate")
            );
        }
        assert!(!destination.join("src/main/java/Other.java").exists());
        assert_eq!(fs::read(destination.join(MANIFEST_FILE)).unwrap(), before);
        let fixed = catalog_files("contributor edit\n");
        assert!(sync_catalog_projection(&destination, &identity, &fixed, SyncMode::Apply).is_err());
        let reconciled =
            sync_catalog_projection(&destination, &identity, &fixed, SyncMode::Reconcile).unwrap();
        assert!(
            reconciled.manifest_changed
                && reconciled.created.is_empty()
                && reconciled.updated.is_empty()
        );
        assert_eq!(fs::read(output).unwrap(), b"contributor edit\n");
        sync_catalog_projection(&destination, &identity, &fixed, SyncMode::Check).unwrap();
    }

    #[test]
    fn named_stale_files_require_explicit_removal_and_unowned_files_are_never_adopted() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("named");
        let identity = catalog_identity();
        let wanted = catalog_files("first\n");
        let mut original = wanted.clone();
        original.insert(
            "src/main/java/Old.java".to_owned(),
            catalog_artifact("old\n"),
        );
        sync_catalog_projection(&destination, &identity, &original, SyncMode::Apply).unwrap();
        let stale = destination.join("src/main/java/Old.java");
        assert!(
            sync_catalog_projection(&destination, &identity, &wanted, SyncMode::Reconcile)
                .unwrap_err()
                .to_string()
                .contains("stale generated files")
        );
        assert_eq!(fs::read(&stale).unwrap(), b"old\n");
        fs::remove_file(stale).unwrap();
        sync_catalog_projection(&destination, &identity, &wanted, SyncMode::Reconcile).unwrap();
        let collision = destination.join("src/main/java/Other.java");
        fs::write(&collision, b"same bytes\n").unwrap();
        let mut unowned = wanted;
        unowned.insert(
            "src/main/java/Other.java".to_owned(),
            catalog_artifact("same bytes\n"),
        );
        assert!(
            sync_catalog_projection(&destination, &identity, &unowned, SyncMode::Apply)
                .unwrap_err()
                .to_string()
                .contains("unowned file")
        );
        assert_eq!(fs::read(collision).unwrap(), b"same bytes\n");
    }

    #[test]
    fn named_invalid_identity_historical_provenance_and_case_components_fail_before_writes() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("named");
        for fault in ["key", "minecraft", "history", "overlay", "case", "state"] {
            let mut identity = catalog_identity();
            let mut wanted = catalog_files("first\n");
            match fault {
                "key" => identity.projection_key = "../escape".to_owned(),
                "minecraft" => {
                    identity.target_id = "1.21.0".to_owned();
                    identity.minecraft_version = "1.21.0".to_owned();
                }
                "history" => {
                    wanted.values_mut().next().unwrap().source_path =
                        "platform/minecraft/release-baselines/old/Example.java".to_owned()
                }
                "overlay" => {
                    wanted.values_mut().next().unwrap().overlay = Some("release-tag".to_owned())
                }
                "case" => {
                    wanted.insert(
                        "src/Main/java/Other.java".to_owned(),
                        catalog_artifact("other\n"),
                    );
                }
                "state" => {
                    wanted.insert(".git/config".to_owned(), catalog_artifact("bad\n"));
                }
                _ => unreachable!(),
            }
            assert!(
                sync_catalog_projection(&destination, &identity, &wanted, SyncMode::Apply).is_err(),
                "accepted {fault}"
            );
            assert!(!destination.exists());
        }
    }

    #[test]
    fn named_target_identity_comes_from_values_not_key_spelling() {
        let catalog = ProjectionCatalog::from_json(r#"{"release/mc-1.19.2":{"minecraft_version":"1.21","environment":"dev","features":[]}}"#, &BTreeSet::new()).unwrap();
        let identity =
            CatalogProjectionIdentity::from_catalog(&catalog, "release/mc-1.19.2").unwrap();
        assert_eq!(identity.target_id, "1.21.0");
        assert_eq!(identity.minecraft_version, "1.21");
        assert_eq!(identity.environment, ProjectionEnvironment::Dev);
        assert_eq!(
            identity.context_identity,
            catalog.context_identity(&identity.projection_key).unwrap()
        );
    }

    #[cfg(windows)]
    #[test]
    fn rejects_junction_in_generated_file_parent_without_writing_outside() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let outside = temporary.path().join("outside");
        fs::create_dir(&destination).unwrap();
        fs::create_dir(&outside).unwrap();
        let sentinel = outside.join("sentinel.txt");
        fs::write(&sentinel, b"outside is unchanged\n").unwrap();
        let junction = destination.join("src");
        create_junction(&junction, &outside);

        let result = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &files("class Example {}\n"),
            SyncMode::Apply,
        );
        // Remove this exact junction before TempDir cleans up either tree.
        fs::remove_dir(&junction).unwrap();
        let error = result.unwrap_err().to_string();
        assert!(error.contains("reparse point"), "{error}");
        assert_eq!(fs::read(sentinel).unwrap(), b"outside is unchanged\n");
        assert!(!outside.join("main").exists());
        assert!(!destination.join(MANIFEST_FILE).exists());
    }

    #[cfg(windows)]
    #[test]
    fn rejects_junction_as_destination_root_without_writing_outside() {
        let temporary = tempfile::tempdir().unwrap();
        let outside = temporary.path().join("outside");
        fs::create_dir(&outside).unwrap();
        let sentinel = outside.join("sentinel.txt");
        fs::write(&sentinel, b"outside is unchanged\n").unwrap();
        let destination = temporary.path().join("generated");
        create_junction(&destination, &outside);

        let result = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &files("class Example {}\n"),
            SyncMode::Apply,
        );
        // Remove this exact junction before TempDir cleans up either tree.
        fs::remove_dir(&destination).unwrap();
        let error = result.unwrap_err().to_string();
        assert!(error.contains("reparse point"), "{error}");
        assert_eq!(fs::read(sentinel).unwrap(), b"outside is unchanged\n");
        assert!(!outside.join("src").exists());
        assert!(!outside.join(MANIFEST_FILE).exists());
    }

    #[cfg(unix)]
    #[test]
    fn generated_gradle_wrapper_is_executable() {
        use std::os::unix::fs::PermissionsExt as _;

        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let files = BTreeMap::from([("gradlew".to_owned(), artifact("#!/bin/sh\n"))]);
        sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &files,
            SyncMode::Apply,
        )
        .unwrap();
        let mode = fs::metadata(destination.join("gradlew"))
            .unwrap()
            .permissions()
            .mode();
        assert_ne!(mode & 0o111, 0);
    }

    #[test]
    fn repeated_sync_is_byte_identical_and_check_is_read_only() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let desired = files("class Example {}\n");
        let dry = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &desired,
            SyncMode::DryRun,
        )
        .unwrap();
        assert_eq!(dry.created, vec!["src/main/java/Example.java"]);
        assert!(!destination.exists());
        let _ = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &desired,
            SyncMode::Check,
        )
        .unwrap_err();
        let first = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &desired,
            SyncMode::Apply,
        )
        .unwrap();
        assert!(first.needs_write());
        let manifest_before = fs::read(destination.join(MANIFEST_FILE)).unwrap();
        let output_before = fs::read(destination.join("src/main/java/Example.java")).unwrap();
        let second = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &desired,
            SyncMode::Apply,
        )
        .unwrap();
        assert!(!second.needs_write());
        assert_eq!(
            manifest_before,
            fs::read(destination.join(MANIFEST_FILE)).unwrap()
        );
        assert_eq!(
            output_before,
            fs::read(destination.join("src/main/java/Example.java")).unwrap()
        );
        assert!(
            !sync_projection(
                &destination,
                &identity("released-4.34.0"),
                &desired,
                SyncMode::Check
            )
            .unwrap()
            .needs_write()
        );
    }

    #[test]
    fn clean_updates_and_manifest_only_changes_are_supported() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let released = identity("released-4.34.0");
        sync_projection(&destination, &released, &files("first\n"), SyncMode::Apply).unwrap();
        let next = files("second\n");
        let preview = sync_projection(&destination, &released, &next, SyncMode::DryRun).unwrap();
        assert_eq!(preview.updated, vec!["src/main/java/Example.java"]);
        let _ = sync_projection(&destination, &released, &next, SyncMode::Check).unwrap_err();
        let result = sync_projection(&destination, &released, &next, SyncMode::Apply).unwrap();
        assert_eq!(result.updated, preview.updated);
        assert_eq!(
            fs::read(destination.join("src/main/java/Example.java")).unwrap(),
            b"second\n"
        );

        let manifest_before = fs::read(destination.join(MANIFEST_FILE)).unwrap();
        let mut source_only = next;
        source_only
            .get_mut("src/main/java/Example.java")
            .unwrap()
            .source_bytes
            .extend_from_slice(b" // changed source");
        let result =
            sync_projection(&destination, &released, &source_only, SyncMode::Apply).unwrap();
        assert!(result.created.is_empty() && result.updated.is_empty());
        assert!(result.manifest_changed);
        assert_ne!(
            manifest_before,
            fs::read(destination.join(MANIFEST_FILE)).unwrap()
        );
        assert_eq!(
            fs::read(destination.join("src/main/java/Example.java")).unwrap(),
            b"second\n"
        );
    }

    #[test]
    fn edited_output_blocks_every_write_and_preserves_unrelated_files() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let first = files("class Example {}\n");
        sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &first,
            SyncMode::Apply,
        )
        .unwrap();
        let unrelated = destination.join("README.md");
        fs::write(&unrelated, b"contributor note\n").unwrap();
        let output = destination.join("src/main/java/Example.java");
        fs::write(&output, b"contributor edit\n").unwrap();
        let manifest_before = fs::read(destination.join(MANIFEST_FILE)).unwrap();
        let mut next = files("new generated content\n");
        next.insert("src/main/java/Other.java".to_owned(), artifact("other\n"));
        let error = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &next,
            SyncMode::Apply,
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("was edited"), "{error}");
        assert_eq!(fs::read(&output).unwrap(), b"contributor edit\n");
        assert!(!destination.join("src/main/java/Other.java").exists());
        assert_eq!(
            fs::read(destination.join(MANIFEST_FILE)).unwrap(),
            manifest_before
        );
        assert_eq!(fs::read(unrelated).unwrap(), b"contributor note\n");
    }

    #[test]
    fn reconciliation_accepts_only_backpropagated_identical_output() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let released = identity("released-4.34.0");
        sync_projection(&destination, &released, &files("before\n"), SyncMode::Apply).unwrap();
        let output = destination.join("src/main/java/Example.java");
        fs::write(&output, b"contributor edit\n").unwrap();

        let error = sync_projection(
            &destination,
            &released,
            &files("different source edit\n"),
            SyncMode::Reconcile,
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("backpropagate"), "{error}");
        assert_eq!(fs::read(&output).unwrap(), b"contributor edit\n");

        let backpropagated = files("contributor edit\n");
        let error = sync_projection(&destination, &released, &backpropagated, SyncMode::Apply)
            .unwrap_err()
            .to_string();
        assert!(error.contains("backpropagate"), "{error}");
        let reconciled = sync_projection(
            &destination,
            &released,
            &backpropagated,
            SyncMode::Reconcile,
        )
        .unwrap();
        assert!(reconciled.created.is_empty() && reconciled.updated.is_empty());
        assert!(reconciled.manifest_changed);
        assert_eq!(fs::read(&output).unwrap(), b"contributor edit\n");
        sync_projection(&destination, &released, &backpropagated, SyncMode::Check).unwrap();
    }

    #[test]
    fn stale_outputs_and_unowned_collisions_are_not_deleted_or_adopted() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let mut first = files("first\n");
        first.insert("src/main/java/Old.java".to_owned(), artifact("old\n"));
        sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &first,
            SyncMode::Apply,
        )
        .unwrap();
        let stale_error = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &files("first\n"),
            SyncMode::Apply,
        )
        .unwrap_err()
        .to_string();
        assert!(
            stale_error.contains("stale generated files"),
            "{stale_error}"
        );
        assert!(destination.join("src/main/java/Old.java").exists());

        let collision = destination.join("src/main/java/Collision.java");
        fs::write(&collision, b"already here\n").unwrap();
        first.insert(
            "src/main/java/Collision.java".to_owned(),
            artifact("already here\n"),
        );
        let collision_error = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &first,
            SyncMode::Apply,
        )
        .unwrap_err()
        .to_string();
        assert!(
            collision_error.contains("unowned file"),
            "{collision_error}"
        );
        assert_eq!(fs::read(collision).unwrap(), b"already here\n");
    }

    #[test]
    fn reconciliation_accepts_explicit_removal_of_a_stale_owned_file() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        let identity = identity("released-4.34.0");
        let mut original = files("first\n");
        original.insert("src/main/java/Old.java".to_owned(), artifact("old\n"));
        sync_projection(&destination, &identity, &original, SyncMode::Apply).unwrap();
        let stale = destination.join("src/main/java/Old.java");
        let wanted = files("first\n");

        let error = sync_projection(&destination, &identity, &wanted, SyncMode::Reconcile)
            .unwrap_err()
            .to_string();
        assert!(error.contains("stale generated files"), "{error}");
        assert_eq!(fs::read(&stale).unwrap(), b"old\n");

        fs::remove_file(&stale).unwrap();
        let error = sync_projection(&destination, &identity, &wanted, SyncMode::Apply)
            .unwrap_err()
            .to_string();
        assert!(error.contains("is missing"), "{error}");

        let report =
            sync_projection(&destination, &identity, &wanted, SyncMode::Reconcile).unwrap();
        assert!(report.manifest_changed);
        assert!(report.created.is_empty() && report.updated.is_empty());
        assert!(!stale.exists());
        sync_projection(&destination, &identity, &wanted, SyncMode::Check).unwrap();
    }

    #[test]
    fn rejects_unknown_or_noncanonical_paths_before_creating_root() {
        let temporary = tempfile::tempdir().unwrap();
        let destination = temporary.path().join("generated");
        for invalid in [
            "../escape.java",
            "src//Example.java",
            "C:/Example.java",
            "src/CON.java",
            "src\\Example.java",
        ] {
            let bad = BTreeMap::from([(invalid.to_owned(), artifact("output"))]);
            assert!(
                sync_projection(
                    &destination,
                    &identity("released-4.34.0"),
                    &bad,
                    SyncMode::Apply
                )
                .is_err(),
                "{invalid} should be rejected"
            );
            assert!(!destination.exists());
        }
        let case_collision = BTreeMap::from([
            ("src/A.java".to_owned(), artifact("one")),
            ("src/a.java".to_owned(), artifact("two")),
        ]);
        let _ = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &case_collision,
            SyncMode::Apply,
        )
        .unwrap_err();
        let parent_collision = BTreeMap::from([
            ("src/Folder".to_owned(), artifact("one")),
            ("src/folder/A.java".to_owned(), artifact("two")),
        ]);
        let _ = sync_projection(
            &destination,
            &identity("released-4.34.0"),
            &parent_collision,
            SyncMode::Apply,
        )
        .unwrap_err();
        assert!(!destination.exists());
    }

    #[test]
    fn presets_need_separate_roots_and_cannot_claim_each_others_outputs() {
        let temporary = tempfile::tempdir().unwrap();
        let released_root = temporary.path().join("released");
        let development_root = temporary.path().join("development");
        sync_projection(
            &released_root,
            &identity("released-4.34.0"),
            &files("released\n"),
            SyncMode::Apply,
        )
        .unwrap();
        sync_projection(
            &development_root,
            &identity("current"),
            &files("development\n"),
            SyncMode::Apply,
        )
        .unwrap();
        assert_eq!(
            fs::read(released_root.join("src/main/java/Example.java")).unwrap(),
            b"released\n"
        );
        assert_eq!(
            fs::read(development_root.join("src/main/java/Example.java")).unwrap(),
            b"development\n"
        );
        let error = sync_projection(
            &released_root,
            &identity("current"),
            &files("development\n"),
            SyncMode::Apply,
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("belongs to"), "{error}");
        assert_eq!(
            fs::read(released_root.join("src/main/java/Example.java")).unwrap(),
            b"released\n"
        );
        let mut redefined_release = identity("released-4.34.0");
        redefined_release.preset_definition_identity = format!("blake3:{}", "b".repeat(64));
        let error = sync_projection(
            &released_root,
            &redefined_release,
            &files("released\n"),
            SyncMode::Apply,
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("belongs to"), "{error}");
    }
}
