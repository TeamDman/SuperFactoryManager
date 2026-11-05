//! Manual admission of an exact, already locked source-built artifact.
use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::dependency_inventory::DependencyInventory;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::source_build_receipt;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
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
    /// User-supplied JAR. The exact locked content hash must already match.
    #[facet(args::named)]
    pub file: PathBuf,
    /// Historical producer receipt selected by the same lock.
    #[facet(args::named)]
    pub producer_receipt: PathBuf,
    /// Full SHA-256 of the supplied JAR, independently stated by the caller.
    #[facet(args::named)]
    pub expected_sha256: String,
}

impl DependencyArtifactImportArgs {
    /// # Errors
    /// Refuses unknown identity, changed pins, bad producer evidence, path collisions or I/O failures.
    pub fn invoke(self, cancellation: &CancellationToken) -> eyre::Result<()> {
        cancellation.bail_if_cancelled()?;
        let selected = crate::jar_build::engine::select_project_source_cache(
            &self.project_root,
            None,
            cancellation,
        )?;
        let inventory =
            DependencyInventory::load_project(&self.project_root, selected.home().clone())?;
        let report = admit_exact_source_build(&inventory, &self, cancellation)?;
        crate::terminal_output::stdout_line(format!(
            "Admitted exact locked source-built {}/{} ({}) to {}. Lock bytes unchanged; manual input and producer receipt remain prerequisites for rebuilding an empty cache.",
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

fn admit_exact_source_build(
    inventory: &DependencyInventory,
    args: &DependencyArtifactImportArgs,
    cancellation: &CancellationToken,
) -> eyre::Result<AdmissionReport> {
    let (dependency_id, component_id) = args.target.split_once('/').ok_or_else(|| {
        eyre::eyre!("Specify an exact dependency/component, for example mekanism/main")
    })?;
    ensure!(
        !dependency_id.is_empty() && !component_id.is_empty() && !component_id.contains('/'),
        "invalid dependency/component identity"
    );
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
    let expected = args.expected_sha256.to_ascii_lowercase();
    ensure!(
        expected.len() == 64 && expected.bytes().all(|byte| byte.is_ascii_hexdigit()),
        "--expected-sha256 must be 64 hex characters"
    );
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
            expected_sha256: sha256(&fs::read(&supplied_jar).unwrap()),
            file: supplied_jar,
            producer_receipt,
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
}
