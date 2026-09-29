//! Copy one fully verified projection candidate into a new local package.
//!
//! This command does not use the configured shared JAR directory, infer tags,
//! change generated roots or contact publication services. The completion
//! manifest is written only after every locked JAR copy has passed its hash.
//! The caller must keep the repository, candidate roots and output parent
//! quiescent. Directory publication is not atomic: partial files are visible
//! and retained on failure. Path checks do not defeat hostile concurrent swaps.

use super::candidate_lock_cli::CandidateVerifyArgs;
use super::candidate_lock_cli::parse_roots;
use super::candidate_lock_cli::read_candidate_lock;
use super::candidate_lock_cli::resolve_path;
use super::candidate_lock_cli::verify_candidate_in;
use super::release_inventory_cli::ReleaseInventory;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::candidate_lock::is_within;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::fs;
use std::fs::File;
use std::fs::OpenOptions;
use std::io;
use std::io::Read as _;
use std::io::Write as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

pub(super) const INVENTORY_FILE: &str = "release-inventory.json";
pub(super) const COMPLETION_FILE: &str = "release-package.json";
pub(super) const PACKAGE_SCHEMA: &str = "sfm:source_release_package@1";
const REPORT_SCHEMA: &str = "sfm:source_release_package_report@1";
const MAX_INVENTORY_BYTES: u64 = 1024 * 1024;
pub(super) const PACKAGE_SCOPE: &str =
    "verified-local-candidate-package-only; no promotion, tag or publication";

#[derive(Clone, Debug, Facet)]
pub struct ReleasePackageArgs {
    /// Reviewed lock and all ten local candidate project roots.
    #[facet(flatten)]
    pub candidate: CandidateVerifyArgs,
    /// Frozen JSON output of `source release-inventory` for this lock and roots.
    #[facet(args::named)]
    pub inventory: PathBuf,
    /// Separately reviewed SHA-256 of the exact candidate lock file.
    #[facet(args::named)]
    pub candidate_lock_sha256: String,
    /// Fresh absolute directory outside the authored repository and candidate roots.
    #[facet(args::named)]
    pub output_root: PathBuf,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub(super) struct ReleasePackageManifest {
    pub(super) schema: String,
    pub(super) scope: String,
    pub(super) lock_sha256: String,
    pub(super) source_commit: String,
    pub(super) source_manifest_sha256: String,
    pub(super) mod_version: String,
    pub(super) candidate_preset_id: String,
    pub(super) candidate_definition_identity: String,
    pub(super) inventory_relative_path: String,
    pub(super) inventory_sha256: String,
    pub(super) build_inputs_are_reviewed_assertions: bool,
    pub(super) targets: Vec<ReleasePackageTarget>,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub(super) struct ReleasePackageTarget {
    pub(super) target_id: String,
    pub(super) minecraft_version: String,
    pub(super) loader: String,
    pub(super) file_name: String,
    pub(super) sha256: String,
}

#[derive(Debug, Facet)]
struct ReleasePackageReport {
    schema: String,
    scope: String,
    output_root: String,
    completion_manifest_relative_path: String,
    lock_sha256: String,
    target_count: usize,
}

impl ReleasePackageArgs {
    /// Reverify an exact candidate and copy its ten locked production JARs.
    ///
    /// # Errors
    ///
    /// Fails before creating an output for stale inputs or an unsafe destination.
    /// A copy failure leaves the new directory without a completion manifest.
    /// The caller must keep the input and output roots quiescent; this command
    /// does not publish the directory atomically against concurrent writers.
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        self.invoke_in_with_hook(cancellation, invocation_dir, None)
    }

    fn invoke_in_with_hook(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
        before_copy: Option<&dyn Fn(usize) -> Result<()>>,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let repo_root = resolve_path(self.candidate.repo_root, invocation_dir);
        let lock_path = resolve_path(self.candidate.lock, invocation_dir);
        let inventory_path = resolve_path(self.inventory, invocation_dir);
        let (lock, lock_sha256) = read_candidate_lock(&lock_path)?;
        ensure!(
            lock_sha256 == self.candidate_lock_sha256,
            "candidate lock SHA-256 differs from --candidate-lock-sha256"
        );
        let roots = parse_roots(&self.candidate.candidate_root)?;
        let inventory_bytes = read_inventory(&inventory_path)?;
        let frozen: ReleaseInventory = facet_json::from_str(std::str::from_utf8(&inventory_bytes)?)
            .wrap_err("cannot parse strict frozen release inventory JSON")?;
        let verified = verify_candidate_in(
            cancellation,
            &repo_root,
            &lock,
            &roots,
            lock_sha256.clone(),
            #[cfg(test)]
            None,
        )?;
        let expected = ReleaseInventory::from_verified(&lock, &verified)?;
        ensure!(
            frozen == expected,
            "frozen release inventory differs from the fully verified candidate"
        );
        let output_root = preflight_output_root(&self.output_root, &repo_root, &roots)?;
        cancellation.bail_if_cancelled()?;
        fs::create_dir(&output_root).wrap_err_with(|| {
            format!(
                "cannot exclusively create release package directory '{}'",
                output_root.display()
            )
        })?;
        let result = copy_verified_package(
            cancellation,
            &output_root,
            &repo_root,
            &roots,
            frozen,
            &inventory_bytes,
            before_copy,
        );
        result.wrap_err_with(|| {
            format!(
                "release package at '{}' is incomplete or unverified; its files were retained",
                output_root.display()
            )
        })
    }
}

fn copy_verified_package(
    cancellation: &CancellationToken,
    output_root: &Path,
    repo_root: &Path,
    roots: &BTreeMap<String, PathBuf>,
    frozen: ReleaseInventory,
    inventory_bytes: &[u8],
    before_copy: Option<&dyn Fn(usize) -> Result<()>>,
) -> Result<CliOutput> {
    let created_root = checked_directory(output_root)?;
    ensure_no_overlap(&created_root, repo_root, roots)?;

    let mut targets = Vec::with_capacity(frozen.targets.len());
    for (index, target) in frozen.targets.iter().enumerate() {
        cancellation.bail_if_cancelled()?;
        if let Some(hook) = before_copy {
            hook(index)?;
        }
        let root = &roots[&target.target_id];
        let source = checked_file(root, &target.production_jar_relative_path)?;
        let file_name = Path::new(&target.production_jar_relative_path)
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| eyre::eyre!("locked production JAR has no UTF-8 filename"))?;
        let destination = created_root.join(file_name);
        copy_new_and_verify(&source, &destination, &target.production_jar_sha256)?;
        targets.push(ReleasePackageTarget {
            target_id: target.target_id.clone(),
            minecraft_version: target.minecraft_version.clone(),
            loader: target.loader.clone(),
            file_name: file_name.to_owned(),
            sha256: target.production_jar_sha256.clone(),
        });
    }

    write_new(&created_root.join(INVENTORY_FILE), inventory_bytes)?;
    ensure!(
        hash_file(&checked_file(&created_root, INVENTORY_FILE)?)? == sha256(inventory_bytes),
        "copied release inventory hash mismatch"
    );
    for target in &targets {
        ensure!(
            hash_file(&checked_file(&created_root, &target.file_name)?)? == target.sha256,
            "packaged JAR changed before completion for '{}'",
            target.target_id
        );
    }
    let manifest = ReleasePackageManifest {
        schema: PACKAGE_SCHEMA.to_owned(),
        scope: PACKAGE_SCOPE.to_owned(),
        lock_sha256: frozen.lock_sha256,
        source_commit: frozen.source_commit,
        source_manifest_sha256: frozen.source_manifest_sha256,
        mod_version: frozen.mod_version,
        candidate_preset_id: frozen.candidate_preset_id,
        candidate_definition_identity: frozen.candidate_definition_identity,
        inventory_relative_path: INVENTORY_FILE.to_owned(),
        inventory_sha256: sha256(inventory_bytes),
        build_inputs_are_reviewed_assertions: frozen.build_inputs_are_reviewed_assertions,
        targets,
    };
    let manifest_bytes = (facet_json::to_string_pretty(&manifest)? + "\n").into_bytes();
    cancellation.bail_if_cancelled()?;
    write_new(&created_root.join(COMPLETION_FILE), &manifest_bytes)?;
    Ok(CliOutput::facet(ReleasePackageReport {
        schema: REPORT_SCHEMA.to_owned(),
        scope: PACKAGE_SCOPE.to_owned(),
        output_root: created_root.display().to_string(),
        completion_manifest_relative_path: COMPLETION_FILE.to_owned(),
        lock_sha256: manifest.lock_sha256,
        target_count: manifest.targets.len(),
    }))
}

fn read_inventory(path: &Path) -> Result<Vec<u8>> {
    let file = File::open(path)
        .wrap_err_with(|| format!("cannot open frozen release inventory '{}'", path.display()))?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file(),
        "frozen release inventory must be a regular file"
    );
    ensure!(
        metadata.len() <= MAX_INVENTORY_BYTES,
        "frozen release inventory exceeds the {MAX_INVENTORY_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_INVENTORY_BYTES + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_INVENTORY_BYTES,
        "frozen release inventory exceeds the {MAX_INVENTORY_BYTES}-byte limit"
    );
    Ok(bytes)
}

fn preflight_output_root(
    output: &Path,
    repo: &Path,
    roots: &BTreeMap<String, PathBuf>,
) -> Result<PathBuf> {
    ensure!(
        output.is_absolute(),
        "release package output root must be absolute"
    );
    ensure!(
        !output
            .components()
            .any(|part| matches!(part, Component::ParentDir | Component::CurDir)),
        "release package output root cannot contain parent/current traversal"
    );
    let name = output
        .file_name()
        .ok_or_else(|| eyre::eyre!("release package output root needs a directory name"))?;
    validate_relative_path(
        name.to_str()
            .ok_or_else(|| eyre::eyre!("release package directory name is not UTF-8"))?,
    )?;
    let parent = output
        .parent()
        .ok_or_else(|| eyre::eyre!("release package output root needs an existing parent"))?;
    let output = checked_directory(parent)?.join(name);
    match fs::symlink_metadata(&output) {
        Ok(_) => eyre::bail!("release package output root already exists"),
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
    }
    ensure_no_overlap(&output, repo, roots)?;
    Ok(output)
}

fn ensure_no_overlap(output: &Path, repo: &Path, roots: &BTreeMap<String, PathBuf>) -> Result<()> {
    let repo = checked_directory(repo)?;
    ensure!(
        !is_within(output, &repo) && !is_within(&repo, output),
        "release package output root overlaps the authored repository"
    );
    for (target, root) in roots {
        let root = checked_directory(root)?;
        ensure!(
            !is_within(output, &root) && !is_within(&root, output),
            "release package output root overlaps candidate '{target}'"
        );
    }
    Ok(())
}

fn copy_new_and_verify(source: &Path, destination: &Path, expected_sha256: &str) -> Result<()> {
    let mut input = File::open(source)
        .wrap_err_with(|| format!("cannot open locked JAR '{}'", source.display()))?;
    let mut output = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(destination)
        .wrap_err_with(|| format!("cannot create packaged JAR '{}'", destination.display()))?;
    io::copy(&mut input, &mut output)?;
    output.sync_all()?;
    ensure!(
        hash_file(destination)? == expected_sha256,
        "packaged JAR hash mismatch for '{}'",
        destination.display()
    );
    Ok(())
}

fn write_new(path: &Path, bytes: &[u8]) -> Result<()> {
    let mut output = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)
        .wrap_err_with(|| format!("cannot create package file '{}'", path.display()))?;
    output.write_all(bytes)?;
    output.sync_all()?;
    Ok(())
}

fn hash_file(path: &Path) -> Result<String> {
    let mut input = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 16_384];
    loop {
        let count = input.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("sha256:{:x}", hasher.finalize()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::source_projection::candidate_lock::tests::Fixture;

    fn fixture_args() -> (Fixture, ReleasePackageArgs) {
        let fixture = Fixture::new();
        let scratch = fixture.repo().parent().unwrap();
        let lock_path = scratch.join("reviewed-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(fixture.lock()).unwrap() + "\n";
        fs::write(&lock_path, &lock_bytes).unwrap();
        let lock_sha256 = sha256(lock_bytes.as_bytes());
        let report = verify_candidate_in(
            &CancellationToken::new(),
            fixture.repo(),
            fixture.lock(),
            fixture.roots(),
            lock_sha256.clone(),
            None,
        )
        .unwrap();
        let inventory = ReleaseInventory::from_verified(fixture.lock(), &report).unwrap();
        let inventory_path = scratch.join("frozen-inventory.json");
        fs::write(
            &inventory_path,
            facet_json::to_string_pretty(&inventory).unwrap() + "\n",
        )
        .unwrap();
        let candidate_root = fixture
            .roots()
            .iter()
            .map(|(target, root)| format!("{target}={}", root.display()))
            .collect();
        let args = ReleasePackageArgs {
            candidate: CandidateVerifyArgs {
                repo_root: fixture.repo().to_path_buf(),
                lock: lock_path,
                candidate_root,
            },
            inventory: inventory_path,
            candidate_lock_sha256: lock_sha256,
            output_root: scratch.join("package"),
        };
        (fixture, args)
    }

    #[test]
    fn release_package_parses_explicit_inventory_lock_and_output() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "release-package",
            "--repo-root",
            "C:/reviewed/repo",
            "--lock",
            "candidate.json",
            "--candidate-root",
            "1.19.2=C:/candidate/1.19.2",
            "--inventory",
            "inventory.json",
            "--candidate-lock-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "--output-root",
            "C:/new-package",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ReleasePackage(args),
        }) = parsed.command
        else {
            panic!("expected source release-package command");
        };
        assert_eq!(args.candidate.candidate_root.len(), 1);
        assert_eq!(args.inventory, PathBuf::from("inventory.json"));
    }

    #[test]
    fn packages_only_ten_exact_locked_jars_and_writes_manifest_last() {
        let (fixture, args) = fixture_args();
        let package = args.output_root.clone();
        let extra = fixture.roots()["1.19.2"].join("build/libs/Other-MC1.19.2-4.35.0.jar");
        fs::write(extra, b"unlocked artifact").unwrap();

        args.invoke_in(&CancellationToken::new(), fixture.repo())
            .unwrap();

        let manifest: ReleasePackageManifest =
            facet_json::from_str(&fs::read_to_string(package.join(COMPLETION_FILE)).unwrap())
                .unwrap();
        assert_eq!(manifest.schema, PACKAGE_SCHEMA);
        assert_eq!(manifest.targets.len(), 10);
        assert!(manifest.build_inputs_are_reviewed_assertions);
        assert!(manifest.scope.contains("no promotion, tag or publication"));
        assert_eq!(
            manifest.inventory_sha256,
            hash_file(&package.join(INVENTORY_FILE)).unwrap()
        );
        assert_eq!(fs::read_dir(&package).unwrap().count(), 12);
        for target in &manifest.targets {
            assert_eq!(
                hash_file(&package.join(&target.file_name)).unwrap(),
                target.sha256
            );
        }
        assert!(package.join(INVENTORY_FILE).is_file());
        assert!(!package.join("Other-MC1.19.2-4.35.0.jar").exists());
    }

    #[test]
    fn wrong_lock_or_changed_inventory_creates_no_output() {
        let (fixture, mut args) = fixture_args();
        let output = args.output_root.clone();
        args.candidate_lock_sha256 = sha256(b"other lock");
        assert!(
            args.invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!output.exists());

        let (fixture, args) = fixture_args();
        let output = args.output_root.clone();
        let mut inventory: ReleaseInventory =
            facet_json::from_str(&fs::read_to_string(&args.inventory).unwrap()).unwrap();
        inventory.targets.pop();
        fs::write(
            &args.inventory,
            facet_json::to_string_pretty(&inventory).unwrap(),
        )
        .unwrap();
        assert!(
            args.invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!output.exists());
    }

    #[test]
    fn missing_or_mutated_target_creates_no_output() {
        let (fixture, args) = fixture_args();
        let output = args.output_root.clone();
        let locked = &fixture.lock().targets[0];
        let jar = fixture.roots()[&locked.target_id].join(&locked.production_jar_relative_path);
        let missing = jar.with_extension("jar.missing");
        fs::rename(&jar, &missing).unwrap();
        assert!(
            args.clone()
                .invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!output.exists());
        fs::rename(&missing, &jar).unwrap();
        fs::write(&jar, b"mutated candidate JAR").unwrap();
        assert!(
            args.invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!output.exists());
    }

    #[test]
    fn existing_or_overlapping_output_is_preserved() {
        let (fixture, mut args) = fixture_args();
        let output = args.output_root.clone();
        fs::create_dir(&output).unwrap();
        fs::write(output.join("owner.txt"), "keep").unwrap();
        assert!(
            args.clone()
                .invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert_eq!(
            fs::read_to_string(output.join("owner.txt")).unwrap(),
            "keep"
        );

        args.output_root = fixture.repo().parent().unwrap().join("occupied-file");
        fs::write(&args.output_root, "keep file").unwrap();
        assert!(
            args.clone()
                .invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert_eq!(fs::read_to_string(&args.output_root).unwrap(), "keep file");

        args.output_root = fixture.repo().join("candidate-package");
        assert!(
            args.clone()
                .invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!args.output_root.exists());
        args.output_root = fixture.roots()["1.19.2"].join("candidate-package");
        assert!(
            args.clone()
                .invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!args.output_root.exists());
        args.output_root = fixture.repo().parent().unwrap().join("CON");
        assert!(
            args.clone()
                .invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!args.output_root.exists());
        args.output_root = PathBuf::from("relative-package");
        assert!(
            args.invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
    }

    #[test]
    fn failure_after_first_copy_leaves_incomplete_directory_without_deleting_it() {
        let (fixture, args) = fixture_args();
        let output = args.output_root.clone();
        let failure = |index| {
            if index == 1 {
                eyre::bail!("injected copy interruption")
            }
            Ok(())
        };
        assert!(
            args.invoke_in_with_hook(&CancellationToken::new(), fixture.repo(), Some(&failure))
                .is_err()
        );
        assert!(output.is_dir());
        assert_eq!(fs::read_dir(&output).unwrap().count(), 1);
        assert!(!output.join(COMPLETION_FILE).exists());
        assert!(!output.join(INVENTORY_FILE).exists());
    }

    #[test]
    fn changed_jar_after_verification_cannot_complete_package() {
        let (fixture, args) = fixture_args();
        let output = args.output_root.clone();
        let locked = &fixture.lock().targets[0];
        let jar = fixture.roots()[&locked.target_id].join(&locked.production_jar_relative_path);
        let mutate = |index| {
            if index == 0 {
                fs::write(&jar, b"changed after verification")?;
            }
            Ok(())
        };
        assert!(
            args.invoke_in_with_hook(&CancellationToken::new(), fixture.repo(), Some(&mutate))
                .is_err()
        );
        assert!(output.is_dir());
        assert!(!output.join(COMPLETION_FILE).exists());
    }

    #[test]
    fn reparse_output_parent_is_rejected_before_creation() {
        let (fixture, mut args) = fixture_args();
        let link = fixture.repo().parent().unwrap().join("linked-parent");
        let real = fixture.repo().parent().unwrap().join("real-parent");
        fs::create_dir(&real).unwrap();
        #[cfg(windows)]
        let linked = std::os::windows::fs::symlink_dir(&real, &link).is_ok();
        #[cfg(unix)]
        let linked = std::os::unix::fs::symlink(&real, &link).is_ok();
        if !linked {
            return;
        }
        args.output_root = link.join("package");
        assert!(
            args.invoke_in(&CancellationToken::new(), fixture.repo())
                .is_err()
        );
        assert!(!real.join("package").exists());
    }
}
