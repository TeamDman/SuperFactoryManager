//! Manual admission of an exact, already locked source-built artifact.
use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::dependency_inventory::DependencyInventory;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::source_build_receipt;
use crate::source_projection::catalog_owned_project::collect_catalog_project;
use crate::source_projection::core_inputs::CORE_ROOT;
use crate::source_projection::projection_catalog::ProjectionEnvironment;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use crate::toolchain_lockfile_write::write_lockfile_atomically;
use eyre::Context;
use eyre::ensure;
use facet::Facet;
use figue as args;
use sha2::Digest as _;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct DependencyArtifactImportArgs {
    /// Exact dependency/component selected by the projection's emitted lock.
    #[facet(args::positional)]
    pub target: String,
    /// Existing catalog projection directory containing sfm-toolchain.lock.json.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Guarded native cache root override; defaults to this checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
    /// User-supplied JAR. The exact locked content hash must already match.
    #[facet(args::named)]
    pub file: PathBuf,
    /// Historical producer receipt selected by the same lock.
    #[facet(args::named)]
    pub producer_receipt: PathBuf,
    /// Full SHA-256 of the supplied JAR, independently stated by the caller.
    #[facet(args::named)]
    pub expected_sha256: String,
    /// Admit new receipt-backed bytes and update the selected development authored lock.
    #[facet(default = false, args::named)]
    pub update_lock: bool,
}

impl DependencyArtifactImportArgs {
    /// # Errors
    /// Refuses unknown identity, changed pins, bad producer evidence, path collisions or I/O failures.
    pub fn invoke(self, cancellation: &CancellationToken) -> eyre::Result<()> {
        cancellation.bail_if_cancelled()?;
        let selected = crate::jar_build::engine::select_project_source_cache(
            &self.project_root,
            self.cache_root.as_deref(),
            cancellation,
        )?;
        let inventory =
            DependencyInventory::load_project(&self.project_root, selected.home().clone())?;
        let report = if self.update_lock {
            repin_source_build(&inventory, &self, cancellation)?
        } else {
            admit_exact_source_build(&inventory, &self, cancellation)?
        };
        let outcome = if self.update_lock {
            "Authored lock updated; run source project sync before building. Keep the supplied JAR and producer receipt for empty-cache recovery."
        } else {
            "Lock bytes unchanged; manual input and producer receipt remain prerequisites for rebuilding an empty cache."
        };
        crate::terminal_output::stdout_line(format!(
            "Admitted source-built {}/{} ({}) to {}. {outcome}",
            report.dependency,
            report.component,
            report.sha256,
            report.destination.display()
        ))?;
        Ok(())
    }
}

struct AdmissionReport {
    dependency: String,
    component: String,
    sha256: String,
    destination: PathBuf,
}

#[derive(Facet)]
struct ManualAdmissionRecord {
    schema: String,
    dependency: String,
    component: String,
    coordinate: String,
    artifact_id: String,
    lock_sha256: String,
    jar_sha256: String,
    locked_content_hash: String,
    producer_receipt_sha256: String,
    method: String,
}

/// Publish a new source-built generation before changing its authored pin.
/// A crash before the final lock write leaves an unreferenced managed entry;
/// a crash after the write is repaired by the ordinary source sync.
fn repin_source_build(
    inventory: &DependencyInventory,
    args: &DependencyArtifactImportArgs,
    cancellation: &CancellationToken,
) -> eyre::Result<AdmissionReport> {
    let (catalog, key) =
        crate::source_projection::core_catalog::CoreCatalog::for_project(&args.project_root)?;
    let project =
        collect_catalog_project(&catalog.repo_root, &catalog.repo_root, &key)?.check_current()?;
    ensure!(
        project.receipt().environment == ProjectionEnvironment::Dev,
        "new source-built pins may only update a development projection"
    );
    let selected = project.selected_input("sfm-toolchain.lock.json")?;
    selected.require_exact_copy()?;
    ensure!(
        dedicated_development_lock(selected.source_path()),
        "manual pin update requires a dedicated development authored lock; refusing a source shared with release projections"
    );
    ensure!(
        Path::new(selected.source_path()).starts_with(Path::new(CORE_ROOT)),
        "selected authored lock lies outside the core source root"
    );
    ensure!(
        selected.source_bytes() == inventory.original_input.as_bytes(),
        "selected authored and emitted locks differ"
    );
    let projected = project
        .artifacts()
        .get("sfm-toolchain.lock.json")
        .expect("selected input has a projected artifact");
    ensure!(
        projected.overlay.is_none(),
        "manual pin update requires the primary checked authored lock"
    );
    let authored = project.repo_root().join(selected.source_path());
    let original_authored = fs::read_to_string(&authored)?;
    ensure!(
        original_authored == inventory.original_input,
        "authored lock changed since selected project validation"
    );

    let (dependency_id, component_id) = exact_target(&args.target)?;
    let component = inventory
        .dependency(dependency_id)?
        .components
        .iter()
        .find(|component| component.id == component_id)
        .ok_or_else(|| eyre::eyre!("Unknown component {dependency_id}/{component_id}"))?;
    let artifact = inventory.artifact(component);
    ensure!(
        artifact.provenance == ArtifactProvenanceV3::SourceBuild
            && artifact.source_git.is_some()
            && artifact.source_build.is_some(),
        "new manual pins require an existing source-built component and producer recipe"
    );
    let expected_sha256 = validated_sha256(&args.expected_sha256)?;
    let jar = read_regular_bounded(&args.file, 64 * 1024 * 1024)?;
    ensure!(
        sha256(&jar) == expected_sha256,
        "supplied JAR SHA-256 mismatch"
    );
    let receipt = read_regular_bounded(&args.producer_receipt, 2 * 1024 * 1024)?;
    let receipt_pin = sha256(&receipt);
    let new_hash = ContentHash::from_bytes(&jar, artifact.hash.algorithm);
    let old_pin = artifact
        .source_build
        .as_ref()
        .and_then(|build| build.environment.get(source_build_receipt::RECEIPT_KEY))
        .ok_or_else(|| {
            eyre::eyre!("source-built component has no existing producer receipt pin")
        })?;
    ensure!(
        new_hash != artifact.hash || receipt_pin != *old_pin,
        "artifact already has this lock identity; omit --update-lock"
    );
    let filename = artifact
        .cache_path
        .file_name()
        .ok_or_else(|| eyre::eyre!("locked producer output has no filename"))?
        .to_string_lossy();
    let new_path = PathBuf::from(format!(
        "$sfm-cache/producer-outputs/{receipt_pin}/{filename}"
    ));
    let mut updated_document = match parse_document(&original_authored)? {
        ToolchainLockfileDocument::V4(document) => document,
        _ => eyre::bail!("development authored lock update requires schema 4"),
    };
    update_source_build_entry(
        &mut updated_document.dependencies,
        &mut updated_document.artifacts,
        &component.derived_checks.artifact_id,
        new_hash,
        &new_path,
        &receipt_pin,
    )?;
    let updated_source = updated_document.to_canonical_json()?;
    let mut updated_inventory = DependencyInventory {
        target: inventory.target.clone(),
        lockfile_path: inventory.lockfile_path.clone(),
        cache_home: inventory.cache_home.clone(),
        original_input: updated_source.clone(),
        lockfile: inventory.lockfile.clone(),
    };
    update_source_build_entry(
        &mut updated_inventory.lockfile.dependencies,
        &mut updated_inventory.lockfile.artifacts,
        &component.derived_checks.artifact_id,
        new_hash,
        &new_path,
        &receipt_pin,
    )?;
    let report = admit_exact_source_build(&updated_inventory, args, cancellation)?;
    cancellation.bail_if_cancelled()?;
    write_lockfile_atomically(&authored, &original_authored, updated_source.as_bytes())?;
    Ok(report)
}

fn update_source_build_entry(
    dependencies: &mut [DependencyV3],
    artifacts: &mut [ArtifactV3],
    artifact_id: &str,
    hash: ContentHash,
    path: &Path,
    receipt_pin: &str,
) -> eyre::Result<()> {
    let artifact = artifacts
        .iter_mut()
        .find(|artifact| artifact.id == artifact_id)
        .ok_or_else(|| eyre::eyre!("Unknown source-built artifact {artifact_id}"))?;
    ensure!(
        artifact.provenance == ArtifactProvenanceV3::SourceBuild,
        "manual pin update requires source-built provenance"
    );
    artifact.hash = hash;
    artifact.cache_path = path.to_path_buf();
    artifact
        .source_build
        .as_mut()
        .ok_or_else(|| eyre::eyre!("source-built artifact has no recipe"))?
        .environment
        .insert(
            source_build_receipt::RECEIPT_KEY.to_owned(),
            receipt_pin.to_owned(),
        );
    let mut matches = 0;
    for dependency in dependencies {
        for component in &mut dependency.components {
            if component.derived_checks.artifact_id == artifact_id {
                component.derived_checks.expected_hash = hash;
                component.derived_checks.cache_path = path.to_path_buf();
                matches += 1;
            }
        }
    }
    ensure!(
        matches == 1,
        "source-built artifact must have one exact component owner"
    );
    Ok(())
}

fn exact_target(target: &str) -> eyre::Result<(&str, &str)> {
    let (dependency, component) = target.split_once('/').ok_or_else(|| {
        eyre::eyre!("Specify an exact dependency/component, for example mekanism/main")
    })?;
    ensure!(
        !dependency.is_empty() && !component.is_empty() && !component.contains('/'),
        "invalid dependency/component identity"
    );
    Ok((dependency, component))
}

fn validated_sha256(value: &str) -> eyre::Result<String> {
    let normalized = value.to_ascii_lowercase();
    ensure!(
        normalized.len() == 64 && normalized.bytes().all(|byte| byte.is_ascii_hexdigit()),
        "--expected-sha256 must be 64 hex characters"
    );
    Ok(normalized)
}

fn dedicated_development_lock(source_path: &str) -> bool {
    Path::new(source_path)
        .file_name()
        .and_then(|name| name.to_str())
        .is_some_and(|name| name.starts_with("schema-4-dev-") && name.ends_with(".json"))
}

fn admit_exact_source_build(
    inventory: &DependencyInventory,
    args: &DependencyArtifactImportArgs,
    cancellation: &CancellationToken,
) -> eyre::Result<AdmissionReport> {
    let (dependency_id, component_id) = exact_target(&args.target)?;
    let dependency = inventory.dependency(dependency_id)?;
    let component = dependency
        .components
        .iter()
        .find(|component| component.id == component_id)
        .ok_or_else(|| eyre::eyre!("Unknown component {dependency_id}/{component_id}"))?;
    let artifact = inventory.artifact(component);
    ensure!(
        artifact.owner.as_ref().is_some_and(
            |owner| owner.dependency_id == dependency_id && owner.component_id == component_id
        ),
        "artifact ownership differs from selected component"
    );
    ensure!(
        artifact.provenance == ArtifactProvenanceV3::SourceBuild
            && artifact.source_git.is_some()
            && artifact.source_build.is_some(),
        "manual import is limited to receipt-pinned source-built JARs; use dependency add with the exact official Maven or CurseForge coordinate for published artifacts"
    );
    let expected = validated_sha256(&args.expected_sha256)?;
    let jar = read_regular_bounded(&args.file, 64 * 1024 * 1024)?;
    let jar_sha256 = sha256(&jar);
    ensure!(jar_sha256 == expected, "supplied JAR SHA-256 mismatch");
    ensure!(
        ContentHash::from_bytes(&jar, artifact.hash.algorithm) == artifact.hash,
        "supplied JAR differs from the exact locked content hash; no lock was changed"
    );
    let receipt = read_regular_bounded(&args.producer_receipt, 2 * 1024 * 1024)?;
    let pin = artifact
        .source_build
        .as_ref()
        .and_then(|build| build.environment.get(source_build_receipt::RECEIPT_KEY))
        .ok_or_else(|| eyre::eyre!("source-built artifact has no pinned producer receipt"))?;
    ensure!(
        sha256(&receipt) == *pin,
        "producer receipt SHA-256 mismatch"
    );
    let staged = tempfile::tempdir()?;
    let staged_receipt = staged.path().join(format!("producer-receipts/{pin}.json"));
    fs::create_dir_all(staged_receipt.parent().expect("receipt has parent"))?;
    fs::write(&staged_receipt, &receipt)?;
    let staged_jar = staged.path().join("selected.jar");
    fs::write(&staged_jar, &jar)?;
    source_build_receipt::verify(
        staged.path(),
        &staged_jar,
        artifact.coordinate.as_deref(),
        artifact.hash,
        artifact.source_git.as_ref(),
        artifact.source_build.as_ref(),
    )?
    .ok_or_else(|| eyre::eyre!("source-built artifact lacks a verified producer receipt"))?;
    cancellation.bail_if_cancelled()?;
    let cache = inventory.cache_home.join("minecraft-toolchain");
    ensure!(
        artifact
            .cache_path
            .starts_with(Path::new("$sfm-cache/producer-outputs"))
            && artifact
                .cache_path
                .components()
                .all(|part| matches!(part, std::path::Component::Normal(_)))
            && component.derived_checks.cache_path == artifact.cache_path,
        "manual source-build destination is not the exact locked producer output"
    );
    let destination = inventory.local_path(&artifact.cache_path);
    ensure!(
        destination.starts_with(&cache),
        "producer destination escapes managed cache"
    );
    check_existing_ancestors(&destination, &cache)?;
    fs::create_dir_all(destination.parent().expect("artifact has parent"))?;
    let name = destination
        .file_name()
        .expect("artifact has name")
        .to_string_lossy();
    let lock = destination.with_file_name(format!("{name}.lock"));
    let _writer = ArtifactLock::acquire_with_cancellation(
        &lock,
        format!("manual source-build admission for {dependency_id}/{component_id}"),
        cancellation.clone(),
    )?;
    let receipt_destination = cache.join(format!("producer-receipts/{pin}.json"));
    check_existing_ancestors(&receipt_destination, &cache)?;
    publish_exact(&receipt_destination, &receipt)?;
    cancellation.bail_if_cancelled()?;
    publish_exact(&destination, &jar)?;
    source_build_receipt::verify(
        &cache,
        &destination,
        artifact.coordinate.as_deref(),
        artifact.hash,
        artifact.source_git.as_ref(),
        artifact.source_build.as_ref(),
    )?
    .ok_or_else(|| eyre::eyre!("admitted output lacks verified provenance"))?;
    cancellation.bail_if_cancelled()?;
    let record = ManualAdmissionRecord {
        schema: "sfm:manual-source-build-admission@1".to_owned(),
        dependency: dependency_id.to_owned(),
        component: component_id.to_owned(),
        coordinate: artifact
            .coordinate
            .clone()
            .expect("receipt verified coordinate"),
        artifact_id: artifact.id.clone(),
        lock_sha256: sha256(inventory.original_input.as_bytes()),
        jar_sha256: jar_sha256.clone(),
        locked_content_hash: artifact.hash.to_string(),
        producer_receipt_sha256: pin.clone(),
        method: "user-supplied-exact-file".to_owned(),
    };
    let record_bytes = facet_json::to_string_pretty(&record)?.into_bytes();
    let record_path = cache.join(format!(
        "manual-admissions/{}.json",
        sha256(format!("{}:{jar_sha256}:{pin}", artifact.id).as_bytes())
    ));
    check_existing_ancestors(&record_path, &cache)?;
    publish_exact(&record_path, &record_bytes)?;
    Ok(AdmissionReport {
        dependency: dependency_id.to_owned(),
        component: component_id.to_owned(),
        sha256: jar_sha256,
        destination,
    })
}

fn read_regular_bounded(path: &Path, limit: u64) -> eyre::Result<Vec<u8>> {
    let metadata = fs::symlink_metadata(path)
        .wrap_err_with(|| format!("cannot inspect supplied file {}", path.display()))?;
    ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink(),
        "supplied input is not a regular file: {}",
        path.display()
    );
    ensure!(
        metadata.len() <= limit,
        "supplied input exceeds byte budget: {}",
        path.display()
    );
    let bytes = fs::read(path)?;
    ensure!(
        bytes.len() as u64 == metadata.len(),
        "supplied input changed while reading: {}",
        path.display()
    );
    Ok(bytes)
}

fn check_existing_ancestors(destination: &Path, cache: &Path) -> eyre::Result<()> {
    let relative = destination.strip_prefix(cache)?;
    let mut current = cache.to_path_buf();
    for part in relative.components() {
        current.push(part);
        if let Ok(metadata) = fs::symlink_metadata(&current) {
            ensure!(
                !metadata.file_type().is_symlink(),
                "managed cache path is a link: {}",
                current.display()
            );
        }
    }
    Ok(())
}

fn publish_exact(destination: &Path, bytes: &[u8]) -> eyre::Result<()> {
    if let Ok(metadata) = fs::symlink_metadata(destination) {
        ensure!(
            metadata.is_file() && !metadata.file_type().is_symlink(),
            "managed destination is not a regular file: {}",
            destination.display()
        );
        ensure!(
            fs::read(destination)? == bytes,
            "managed destination contains different bytes; run explicit doctor/repair before admission: {}",
            destination.display()
        );
        return Ok(());
    }
    crate::payload_fetcher::write_payload_atomically(destination, bytes)?;
    ensure!(
        fs::read(destination)? == bytes,
        "admitted bytes changed after publication"
    );
    Ok(())
}

fn sha256(bytes: &[u8]) -> String {
    format!("{:x}", sha2::Sha256::digest(bytes))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::{BranchName, WorktreePath, WorktreeTarget};
    use crate::paths::CacheHome;
    use crate::toolchain_lockfile_schema::read_current;

    fn fixture() -> (
        tempfile::TempDir,
        DependencyInventory,
        DependencyArtifactImportArgs,
    ) {
        let (temp, supplied_jar, entry) = source_build_receipt::tests::resolver_fixture();
        let input = include_str!(
            "../../../../../minecraft/projections/sfm-dev/mc-26.1.2/sfm-toolchain.lock.json"
        );
        let mut lockfile = read_current(input).unwrap();
        let component = lockfile
            .dependencies
            .iter_mut()
            .find(|dependency| dependency.id == "mekanism")
            .unwrap()
            .components
            .iter_mut()
            .find(|component| component.id == "main")
            .unwrap();
        component.derived_checks.expected_hash = entry.hash;
        component.derived_checks.cache_path = PathBuf::from(&entry.cache_path);
        let artifact_id = component.derived_checks.artifact_id.clone();
        let artifact = lockfile
            .artifacts
            .iter_mut()
            .find(|artifact| artifact.id == artifact_id)
            .unwrap();
        artifact.hash = entry.hash;
        artifact.cache_path = PathBuf::from(&entry.cache_path);
        artifact.coordinate = entry.coordinate;
        artifact.provenance = ArtifactProvenanceV3::SourceBuild;
        artifact.source_git = entry.source_git;
        artifact.source_build = entry.source_build;
        let pin = artifact
            .source_build
            .as_ref()
            .unwrap()
            .environment
            .get(source_build_receipt::RECEIPT_KEY)
            .unwrap();
        let producer_receipt = temp.path().join(format!("producer-receipts/{pin}.json"));
        let lockfile_path = temp.path().join("sfm-toolchain.lock.json");
        fs::write(&lockfile_path, input).unwrap();
        let inventory = DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::new("project:sfm-dev/mc-26.1.2"),
                worktree_path: WorktreePath::new(temp.path()),
                core: false,
                mc_version: None,
            },
            lockfile_path: lockfile_path.clone(),
            cache_home: CacheHome(temp.path().join("managed")),
            original_input: input.to_owned(),
            lockfile,
        };
        let args = DependencyArtifactImportArgs {
            target: "mekanism/main".to_owned(),
            project_root: temp.path().to_path_buf(),
            cache_root: None,
            expected_sha256: sha256(&fs::read(&supplied_jar).unwrap()),
            file: supplied_jar,
            producer_receipt,
            update_lock: false,
        };
        (temp, inventory, args)
    }

    #[test]
    fn exact_import_is_idempotent_and_does_not_change_the_lock() {
        let (_temp, inventory, args) = fixture();
        let before = fs::read(&inventory.lockfile_path).unwrap();
        let first = admit_exact_source_build(&inventory, &args, &CancellationToken::new()).unwrap();
        assert_eq!(
            fs::read(&first.destination).unwrap(),
            fs::read(&args.file).unwrap()
        );
        let second =
            admit_exact_source_build(&inventory, &args, &CancellationToken::new()).unwrap();
        assert_eq!(first.destination, second.destination);
        assert_eq!(fs::read(&inventory.lockfile_path).unwrap(), before);
        assert_eq!(
            fs::read_dir(
                inventory
                    .cache_home
                    .join("minecraft-toolchain/manual-admissions")
            )
            .unwrap()
            .count(),
            1
        );
    }

    #[test]
    fn wrong_jar_or_receipt_is_refused_before_publication() {
        let (temp, inventory, mut args) = fixture();
        let destination = inventory.local_path(
            &inventory
                .artifact(
                    &inventory
                        .dependency("mekanism")
                        .unwrap()
                        .components
                        .iter()
                        .find(|component| component.id == "main")
                        .unwrap(),
                )
                .cache_path,
        );
        let wrong = temp.path().join("wrong.jar");
        fs::write(&wrong, b"not the locked jar").unwrap();
        args.file = wrong;
        args.expected_sha256 = sha256(&fs::read(&args.file).unwrap());
        assert!(admit_exact_source_build(&inventory, &args, &CancellationToken::new()).is_err());
        assert!(!destination.exists());
        args.file = temp.path().join("producer-outputs/test/output.jar");
        args.expected_sha256 = sha256(&fs::read(&args.file).unwrap());
        let wrong_receipt = temp.path().join("wrong-receipt.json");
        fs::write(&wrong_receipt, b"{}").unwrap();
        args.producer_receipt = wrong_receipt;
        assert!(admit_exact_source_build(&inventory, &args, &CancellationToken::new()).is_err());
        assert!(!destination.exists());
    }

    #[test]
    fn cancellation_and_corrupt_destination_fail_closed() {
        let (_temp, inventory, args) = fixture();
        let token = CancellationToken::new();
        token.request_cancel("test interruption");
        assert!(admit_exact_source_build(&inventory, &args, &token).is_err());
        let destination = inventory.local_path(
            &inventory
                .artifact(
                    &inventory
                        .dependency("mekanism")
                        .unwrap()
                        .components
                        .iter()
                        .find(|component| component.id == "main")
                        .unwrap(),
                )
                .cache_path,
        );
        assert!(!destination.exists());
        fs::create_dir_all(destination.parent().unwrap()).unwrap();
        fs::write(&destination, b"corrupt").unwrap();
        assert!(admit_exact_source_build(&inventory, &args, &CancellationToken::new()).is_err());
        assert_eq!(fs::read(destination).unwrap(), b"corrupt");
    }

    #[test]
    fn interrupted_receipt_publication_can_resume_without_changing_the_lock() {
        let (_temp, inventory, args) = fixture();
        let component = inventory
            .dependency("mekanism")
            .unwrap()
            .components
            .iter()
            .find(|component| component.id == "main")
            .unwrap();
        let artifact = inventory.artifact(component);
        let pin = artifact
            .source_build
            .as_ref()
            .unwrap()
            .environment
            .get(source_build_receipt::RECEIPT_KEY)
            .unwrap();
        let destination = inventory
            .cache_home
            .join("minecraft-toolchain")
            .join(format!("producer-receipts/{pin}.json"));
        fs::create_dir_all(destination.parent().unwrap()).unwrap();
        fs::copy(&args.producer_receipt, &destination).unwrap();
        let before = fs::read(&inventory.lockfile_path).unwrap();
        admit_exact_source_build(&inventory, &args, &CancellationToken::new()).unwrap();
        assert_eq!(
            fs::read(destination).unwrap(),
            fs::read(args.producer_receipt).unwrap()
        );
        assert_eq!(fs::read(&inventory.lockfile_path).unwrap(), before);
    }

    #[test]
    fn lock_path_traversal_is_refused_before_any_cache_write() {
        let (_temp, mut inventory, args) = fixture();
        let dependency = inventory
            .lockfile
            .dependencies
            .iter_mut()
            .find(|dependency| dependency.id == "mekanism")
            .unwrap();
        let component = dependency
            .components
            .iter_mut()
            .find(|component| component.id == "main")
            .unwrap();
        let artifact_id = component.derived_checks.artifact_id.clone();
        let escaped = PathBuf::from("$sfm-cache/producer-outputs/../../escape.jar");
        component.derived_checks.cache_path = escaped.clone();
        inventory
            .lockfile
            .artifacts
            .iter_mut()
            .find(|artifact| artifact.id == artifact_id)
            .unwrap()
            .cache_path = escaped;
        assert!(admit_exact_source_build(&inventory, &args, &CancellationToken::new()).is_err());
        assert!(!inventory.cache_home.0.exists());
    }

    #[test]
    fn new_receipt_updates_only_one_authored_component_and_keeps_old_bytes_addressable() {
        let (_temp, inventory, _args) = fixture();
        let mut document = match parse_document(&inventory.original_input).unwrap() {
            ToolchainLockfileDocument::V4(document) => document,
            _ => panic!("expected authored schema 4"),
        };
        let old_api = document
            .dependencies
            .iter()
            .find(|dependency| dependency.id == "mekanism")
            .unwrap()
            .components
            .iter()
            .find(|component| component.id == "api")
            .unwrap()
            .derived_checks
            .clone();
        let old_main = document
            .dependencies
            .iter()
            .find(|dependency| dependency.id == "mekanism")
            .unwrap()
            .components
            .iter()
            .find(|component| component.id == "main")
            .unwrap()
            .derived_checks
            .clone();
        let new_hash =
            ContentHash::from_bytes(b"new producer output", old_main.expected_hash.algorithm);
        let new_pin = "a".repeat(64);
        let new_path = PathBuf::from(format!("$sfm-cache/producer-outputs/{new_pin}/new.jar"));
        update_source_build_entry(
            &mut document.dependencies,
            &mut document.artifacts,
            &old_main.artifact_id,
            new_hash,
            &new_path,
            &new_pin,
        )
        .unwrap();
        let encoded = document.to_canonical_json().unwrap();
        let reparsed = match parse_document(&encoded).unwrap() {
            ToolchainLockfileDocument::V4(document) => document,
            _ => panic!("expected updated schema 4"),
        };
        let mekanism = reparsed
            .dependencies
            .iter()
            .find(|dependency| dependency.id == "mekanism")
            .unwrap();
        let api = mekanism
            .components
            .iter()
            .find(|component| component.id == "api")
            .unwrap();
        let main = mekanism
            .components
            .iter()
            .find(|component| component.id == "main")
            .unwrap();
        assert_eq!(api.derived_checks, old_api);
        assert_eq!(main.derived_checks.expected_hash, new_hash);
        assert_eq!(main.derived_checks.cache_path, new_path);
        let artifact = reparsed
            .artifacts
            .iter()
            .find(|artifact| artifact.id == old_main.artifact_id)
            .unwrap();
        assert_eq!(artifact.hash, new_hash);
        assert_eq!(artifact.cache_path, new_path);
        assert_eq!(
            artifact
                .source_build
                .as_ref()
                .unwrap()
                .environment
                .get(source_build_receipt::RECEIPT_KEY),
            Some(&new_pin)
        );
        assert_eq!(
            fs::read(&inventory.lockfile_path).unwrap(),
            inventory.original_input.as_bytes()
        );
    }

    #[test]
    fn repin_requires_a_dedicated_development_authored_lock() {
        assert!(dedicated_development_lock(
            "build/lockfiles/26.1.2/schema-4-dev-ae2-runtime.json"
        ));
        assert!(!dedicated_development_lock(
            "build/lockfiles/26.1.2/schema-4.json"
        ));
        assert!(!dedicated_development_lock(
            "build/lockfiles/26.1.2/schema-2-historical-4.34.0.json"
        ));
    }

    #[test]
    fn new_receipt_publishes_a_second_generation_without_replacing_the_first() {
        let (_old_temp, mut inventory, old_args) = fixture();
        let old =
            admit_exact_source_build(&inventory, &old_args, &CancellationToken::new()).unwrap();
        let old_bytes = fs::read(&old.destination).unwrap();
        let (new_temp, new_file, new_entry) =
            source_build_receipt::tests::rebuilt_resolver_fixture();
        let new_pin = new_entry
            .source_build
            .as_ref()
            .unwrap()
            .environment
            .get(source_build_receipt::RECEIPT_KEY)
            .unwrap()
            .clone();
        let new_path = PathBuf::from(format!("$sfm-cache/producer-outputs/{new_pin}/output.jar"));
        let artifact_id = inventory
            .dependency("mekanism")
            .unwrap()
            .components
            .iter()
            .find(|component| component.id == "main")
            .unwrap()
            .derived_checks
            .artifact_id
            .clone();
        update_source_build_entry(
            &mut inventory.lockfile.dependencies,
            &mut inventory.lockfile.artifacts,
            &artifact_id,
            new_entry.hash,
            &new_path,
            &new_pin,
        )
        .unwrap();
        let args = DependencyArtifactImportArgs {
            file: new_file.clone(),
            producer_receipt: new_temp
                .path()
                .join(format!("producer-receipts/{new_pin}.json")),
            expected_sha256: sha256(&fs::read(&new_file).unwrap()),
            update_lock: true,
            ..old_args
        };
        let new = admit_exact_source_build(&inventory, &args, &CancellationToken::new()).unwrap();
        assert_ne!(old.destination, new.destination);
        assert_eq!(fs::read(old.destination).unwrap(), old_bytes);
        assert_eq!(
            fs::read(new.destination).unwrap(),
            fs::read(new_file).unwrap()
        );
    }
}
