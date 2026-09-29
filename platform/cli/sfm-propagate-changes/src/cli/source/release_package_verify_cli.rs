//! Read-only verification of one completed local release package.
//!
//! The reviewed completion-manifest digest anchors the inventory and all ten
//! JARs. The caller must keep the package quiescent while it is checked; path
//! checks cannot defeat hostile concurrent swaps.

use super::release_inventory_cli::INVENTORY_SCHEMA;
use super::release_inventory_cli::INVENTORY_SCOPE;
use super::release_inventory_cli::ReleaseInventory;
use super::release_package_cli::COMPLETION_FILE;
use super::release_package_cli::INVENTORY_FILE;
use super::release_package_cli::PACKAGE_SCHEMA;
use super::release_package_cli::PACKAGE_SCOPE;
use super::release_package_cli::ReleasePackageManifest;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use sha2::Digest as _;
use sha2::Sha256;
use std::collections::BTreeSet;
use std::fs;
use std::fs::File;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;

const REPORT_SCHEMA: &str = "sfm:source_release_package_verification@1";
const REPORT_SCOPE: &str = "verified-local-package-only; no promotion, tag or publication";
const MAX_JSON_BYTES: u64 = 1024 * 1024;
const EXPECTED_TARGETS: [(&str, &str, &str, u16, &str); 10] = [
    ("1.19.2", "1.19.2", "forge", 17, "reobfJar"),
    ("1.19.4", "1.19.4", "forge", 17, "reobfJar"),
    ("1.20", "1.20", "forge", 17, "reobfJar"),
    ("1.20.1", "1.20.1", "neoforge", 17, "reobfJar"),
    ("1.20.2", "1.20.2", "neoforge", 17, "jar"),
    ("1.20.3", "1.20.3", "neoforge", 17, "jar"),
    ("1.20.4", "1.20.4", "neoforge", 17, "jar"),
    ("1.21.0", "1.21", "neoforge", 21, "jar"),
    ("1.21.1", "1.21.1", "neoforge", 21, "jar"),
    ("26.1.2", "26.1.2", "neoforge", 25, "jarJar"),
];

#[derive(Debug, Facet)]
pub struct ReleasePackageVerifyArgs {
    /// Existing absolute local directory created by `source release-package`.
    #[facet(args::named)]
    pub package_root: PathBuf,
    /// Separately reviewed SHA-256 of the exact release-package.json bytes.
    #[facet(args::named)]
    pub completion_manifest_sha256: String,
}

#[derive(Debug, Facet)]
struct ReleasePackageVerificationReport {
    schema: String,
    scope: String,
    completion_manifest_sha256: String,
    inventory_sha256: String,
    lock_sha256: String,
    verified_target_count: usize,
}

impl ReleasePackageVerifyArgs {
    /// Verify a completed package without changing any file or release state.
    ///
    /// # Errors
    ///
    /// Rejects an unreviewed completion manifest, invalid package contract,
    /// unexpected path, or changed inventory/JAR bytes.
    pub(super) fn invoke_in(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        validate_digest(&self.completion_manifest_sha256)?;
        let root = checked_directory(&self.package_root)?;
        let manifest_bytes = read_bounded(&root, COMPLETION_FILE)?;
        ensure!(
            sha256(&manifest_bytes) == self.completion_manifest_sha256,
            "completion manifest SHA-256 differs from --completion-manifest-sha256"
        );
        let manifest: ReleasePackageManifest =
            facet_json::from_str(std::str::from_utf8(&manifest_bytes)?)
                .wrap_err("cannot parse strict release package manifest JSON")?;
        let inventory_bytes = read_bounded(&root, INVENTORY_FILE)?;
        let inventory: ReleaseInventory =
            facet_json::from_str(std::str::from_utf8(&inventory_bytes)?)
                .wrap_err("cannot parse strict release inventory JSON")?;
        validate_contract(&manifest, &inventory, &inventory_bytes)?;
        validate_directory_entries(&root, &manifest)?;
        for target in &manifest.targets {
            cancellation.bail_if_cancelled()?;
            ensure!(
                hash_regular(&root, &target.file_name, cancellation)? == target.sha256,
                "packaged JAR SHA-256 differs for '{}'",
                target.target_id
            );
        }
        // Catch a newly added entry during hashing when the package was not quiescent.
        validate_directory_entries(&root, &manifest)?;
        Ok(CliOutput::facet(ReleasePackageVerificationReport {
            schema: REPORT_SCHEMA.to_owned(),
            scope: REPORT_SCOPE.to_owned(),
            completion_manifest_sha256: self.completion_manifest_sha256,
            inventory_sha256: manifest.inventory_sha256,
            lock_sha256: manifest.lock_sha256,
            verified_target_count: manifest.targets.len(),
        }))
    }
}

fn validate_contract(
    manifest: &ReleasePackageManifest,
    inventory: &ReleaseInventory,
    inventory_bytes: &[u8],
) -> Result<()> {
    ensure!(
        manifest.schema == PACKAGE_SCHEMA && manifest.scope == PACKAGE_SCOPE,
        "unsupported release package schema or scope"
    );
    ensure!(
        manifest.inventory_relative_path == INVENTORY_FILE,
        "release package must name its exact inventory file"
    );
    validate_digest(&manifest.inventory_sha256)?;
    ensure!(
        sha256(inventory_bytes) == manifest.inventory_sha256,
        "release inventory SHA-256 differs from completion manifest"
    );
    ensure!(
        inventory.schema == INVENTORY_SCHEMA && inventory.scope == INVENTORY_SCOPE,
        "unsupported release inventory schema or scope"
    );
    validate_inventory_fields(inventory)?;
    ensure!(
        manifest.lock_sha256 == inventory.lock_sha256
            && manifest.source_commit == inventory.source_commit
            && manifest.source_manifest_sha256 == inventory.source_manifest_sha256
            && manifest.mod_version == inventory.mod_version
            && manifest.candidate_preset_id == inventory.candidate_preset_id
            && manifest.candidate_definition_identity == inventory.candidate_definition_identity
            && manifest.build_inputs_are_reviewed_assertions
                == inventory.build_inputs_are_reviewed_assertions,
        "release package identity differs from frozen inventory"
    );
    validate_targets(manifest, inventory)
}

fn validate_inventory_fields(inventory: &ReleaseInventory) -> Result<()> {
    ensure!(
        inventory.deterministic_source_check && inventory.build_inputs_are_reviewed_assertions,
        "release inventory lacks required verification assertions"
    );
    validate_digest(&inventory.lock_sha256)?;
    validate_digest(&inventory.source_manifest_sha256)?;
    validate_digest(&inventory.compatibility_evidence_sha256)?;
    validate_lower_hex(&inventory.source_commit, 40, "source commit")?;
    let definition = inventory
        .candidate_definition_identity
        .strip_prefix("blake3:")
        .ok_or_else(|| eyre::eyre!("invalid candidate definition identity"))?;
    validate_lower_hex(definition, 64, "candidate definition identity")?;
    validate_relative_path(&inventory.compatibility_evidence_relative_path)?;
    ensure!(
        safe_label(&inventory.mod_version) && !inventory.mod_version.contains("-dev."),
        "invalid release mod version"
    );
    ensure!(
        inventory.candidate_preset_id == format!("released-{}", inventory.mod_version),
        "invalid release candidate preset ID"
    );
    Ok(())
}

fn validate_targets(manifest: &ReleasePackageManifest, inventory: &ReleaseInventory) -> Result<()> {
    ensure!(
        manifest.targets.len() == EXPECTED_TARGETS.len()
            && inventory.targets.len() == EXPECTED_TARGETS.len(),
        "release package requires the complete ten-target matrix"
    );

    let mut manifest_ids = BTreeSet::new();
    let mut manifest_names = BTreeSet::new();
    for target in &manifest.targets {
        validate_relative_path(&target.file_name)?;
        ensure!(
            !target.file_name.contains('/'),
            "packaged JAR filename must be a single safe component"
        );
        validate_digest(&target.sha256)?;
        ensure!(
            manifest_ids.insert(target.target_id.to_ascii_lowercase()),
            "duplicate release package target '{}'",
            target.target_id
        );
        ensure!(
            manifest_names.insert(target.file_name.to_ascii_lowercase()),
            "duplicate packaged JAR filename '{}'",
            target.file_name
        );
    }

    for (
        (expected_id, expected_minecraft, expected_loader, expected_jdk, expected_task),
        (target, packaged),
    ) in EXPECTED_TARGETS
        .iter()
        .zip(inventory.targets.iter().zip(&manifest.targets))
    {
        ensure!(
            target.target_id == *expected_id
                && target.minecraft_version == *expected_minecraft
                && target.loader == *expected_loader
                && target.jdk_major == *expected_jdk
                && target.production_task == *expected_task,
            "release inventory has an invalid target mapping for '{}'",
            target.target_id
        );
        ensure!(
            safe_label(&target.loader_version) && safe_label(&target.gradle_profile),
            "invalid release inventory build field for '{}'",
            target.target_id
        );
        let (_, jdk_version) = target
            .jdk_build_id
            .rsplit_once('-')
            .ok_or_else(|| eyre::eyre!("invalid JDK build ID for '{}'", target.target_id))?;
        ensure!(
            safe_label(&target.jdk_build_id)
                && jdk_version.split('.').next() == Some(target.jdk_major.to_string().as_str()),
            "invalid JDK build ID for '{}'",
            target.target_id
        );
        validate_digest(&target.provenance_manifest_sha256)?;
        validate_digest(&target.production_jar_sha256)?;
        validate_relative_path(&target.production_jar_relative_path)?;
        let parts = target
            .production_jar_relative_path
            .split('/')
            .collect::<Vec<_>>();
        let ["build", "libs", file_name] = parts.as_slice() else {
            eyre::bail!("invalid production JAR location for '{}'", target.target_id);
        };
        ensure!(
            file_name.ends_with(&format!(
                "-MC{}-{}.jar",
                target.minecraft_version, inventory.mod_version
            )) && !file_name.contains("-dev."),
            "invalid production JAR filename for '{}'",
            target.target_id
        );
        ensure!(
            packaged.target_id == target.target_id
                && packaged.minecraft_version == target.minecraft_version
                && packaged.loader == target.loader
                && packaged.file_name == *file_name
                && packaged.sha256 == target.production_jar_sha256,
            "packaged target differs from frozen inventory for '{}'",
            target.target_id
        );
    }
    Ok(())
}

fn validate_directory_entries(root: &Path, manifest: &ReleasePackageManifest) -> Result<()> {
    let mut expected = BTreeSet::from([COMPLETION_FILE.to_owned(), INVENTORY_FILE.to_owned()]);
    let mut expected_lower = BTreeSet::from([
        COMPLETION_FILE.to_ascii_lowercase(),
        INVENTORY_FILE.to_ascii_lowercase(),
    ]);
    for target in &manifest.targets {
        ensure!(
            expected_lower.insert(target.file_name.to_ascii_lowercase())
                && expected.insert(target.file_name.clone()),
            "duplicate package filename"
        );
    }
    let mut actual = BTreeSet::new();
    let mut actual_lower = BTreeSet::new();
    for entry in fs::read_dir(root).wrap_err("cannot list release package directory")? {
        let entry = entry?;
        let name = entry.file_name().into_string().map_err(|invalid_name| {
            eyre::eyre!("package contains a non-UTF-8 filename: {invalid_name:?}")
        })?;
        ensure!(
            expected.contains(&name),
            "release package contains an unexpected entry"
        );
        ensure!(
            actual_lower.insert(name.to_ascii_lowercase()) && actual.insert(name.clone()),
            "release package contains a duplicate filename"
        );
        checked_file(root, &name)?;
    }
    ensure!(
        actual == expected,
        "release package is missing a required file"
    );
    Ok(())
}

fn read_bounded(root: &Path, name: &str) -> Result<Vec<u8>> {
    let path = checked_file(root, name)?;
    let file = File::open(path).wrap_err_with(|| format!("cannot open package file '{name}'"))?;
    ensure!(
        file.metadata()?.len() <= MAX_JSON_BYTES,
        "package JSON file exceeds the {MAX_JSON_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_JSON_BYTES + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_JSON_BYTES,
        "package JSON file exceeds the {MAX_JSON_BYTES}-byte limit"
    );
    Ok(bytes)
}

fn hash_regular(root: &Path, relative: &str, cancellation: &CancellationToken) -> Result<String> {
    let path = checked_file(root, relative)?;
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 16_384];
    loop {
        cancellation.bail_if_cancelled()?;
        let count = file.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("sha256:{:x}", hasher.finalize()))
}

fn validate_digest(value: &str) -> Result<()> {
    let hex = value.strip_prefix("sha256:").unwrap_or("");
    validate_lower_hex(hex, 64, "SHA-256 digest")
}

fn validate_lower_hex(value: &str, length: usize, label: &str) -> Result<()> {
    ensure!(
        value.len() == length
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "invalid {label}"
    );
    Ok(())
}

fn safe_label(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 128
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._+-".contains(&byte))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;
    use crate::cli::output::OutputFormat;
    use crate::cli::source::CandidateVerifyArgs;
    use crate::cli::source::ReleasePackageArgs;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::cli::source::candidate_lock_cli::verify_candidate_in;
    use crate::source_projection::candidate_lock::tests::Fixture;

    fn fixture_package() -> (Fixture, ReleasePackageVerifyArgs) {
        let fixture = Fixture::new();
        let scratch = fixture.repo().parent().unwrap();
        let lock = scratch.join("reviewed-candidate-lock.json");
        let lock_bytes = facet_json::to_string_pretty(fixture.lock()).unwrap() + "\n";
        fs::write(&lock, &lock_bytes).unwrap();
        let lock_sha256 = sha256(lock_bytes.as_bytes());
        let verified = verify_candidate_in(
            &CancellationToken::new(),
            fixture.repo(),
            fixture.lock(),
            fixture.roots(),
            lock_sha256.clone(),
            None,
        )
        .unwrap();
        let inventory = ReleaseInventory::from_verified(fixture.lock(), &verified).unwrap();
        let inventory_path = scratch.join("frozen-inventory.json");
        fs::write(
            &inventory_path,
            facet_json::to_string_pretty(&inventory).unwrap() + "\n",
        )
        .unwrap();
        let package_root = scratch.join("package");
        ReleasePackageArgs {
            candidate: CandidateVerifyArgs {
                repo_root: fixture.repo().to_path_buf(),
                lock,
                candidate_root: fixture
                    .roots()
                    .iter()
                    .map(|(target, root)| format!("{target}={}", root.display()))
                    .collect(),
            },
            inventory: inventory_path,
            candidate_lock_sha256: lock_sha256,
            output_root: package_root.clone(),
        }
        .invoke_in(&CancellationToken::new(), fixture.repo())
        .unwrap();
        let completion_manifest_sha256 =
            sha256(&fs::read(package_root.join(COMPLETION_FILE)).unwrap());
        (
            fixture,
            ReleasePackageVerifyArgs {
                package_root,
                completion_manifest_sha256,
            },
        )
    }

    fn manifest(root: &Path) -> ReleasePackageManifest {
        facet_json::from_str(&fs::read_to_string(root.join(COMPLETION_FILE)).unwrap()).unwrap()
    }

    fn write_manifest(args: &mut ReleasePackageVerifyArgs, manifest: &ReleasePackageManifest) {
        let bytes = facet_json::to_string_pretty(manifest).unwrap() + "\n";
        fs::write(args.package_root.join(COMPLETION_FILE), &bytes).unwrap();
        args.completion_manifest_sha256 = sha256(bytes.as_bytes());
    }

    fn verify(args: &ReleasePackageVerifyArgs) -> Result<CliOutput> {
        ReleasePackageVerifyArgs {
            package_root: args.package_root.clone(),
            completion_manifest_sha256: args.completion_manifest_sha256.clone(),
        }
        .invoke_in(&CancellationToken::new())
    }

    fn rejects(args: &ReleasePackageVerifyArgs) {
        let error = verify(args).unwrap_err();
        assert!(!error.to_string().is_empty());
    }

    #[test]
    fn parses_reviewed_digest_and_verifies_producer_package_with_path_free_report() {
        let parsed = figue::from_slice::<Cli>(&[
            "source",
            "release-package-verify",
            "--package-root",
            "C:/reviewed/package",
            "--completion-manifest-sha256",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let Command::Source(SourceArgs {
            command: SourceCommand::ReleasePackageVerify(parsed_args),
        }) = parsed.command
        else {
            panic!("expected source release-package-verify command");
        };
        assert_eq!(
            parsed_args.package_root,
            PathBuf::from("C:/reviewed/package")
        );

        let (_fixture, args) = fixture_package();
        let before = fs::read_dir(&args.package_root).unwrap().count();
        let report = verify(&args).unwrap();
        let rendered = report
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        assert!(rendered.contains(REPORT_SCHEMA));
        assert!(rendered.contains("\"verified_target_count\": 10"));
        assert!(!rendered.contains(&args.package_root.display().to_string()));
        assert_eq!(fs::read_dir(&args.package_root).unwrap().count(), before);
    }

    #[test]
    fn rejects_wrong_or_malformed_reviewed_completion_digest() {
        let (_fixture, mut args) = fixture_package();
        args.completion_manifest_sha256 = sha256(b"different manifest");
        rejects(&args);
        args.completion_manifest_sha256 = "sha256:ABC".to_owned();
        rejects(&args);
    }

    #[test]
    fn rejects_changed_manifest_schema_scope_unknown_and_duplicate_json_fields() {
        let (_fixture, mut args) = fixture_package();
        let original = fs::read_to_string(args.package_root.join(COMPLETION_FILE)).unwrap();
        let original_digest = args.completion_manifest_sha256.clone();
        let mut changed = manifest(&args.package_root);
        changed.schema = "sfm:source_release_package@2".to_owned();
        write_manifest(&mut args, &changed);
        rejects(&args);

        changed.schema = PACKAGE_SCHEMA.to_owned();
        changed.scope = "publishable".to_owned();
        write_manifest(&mut args, &changed);
        rejects(&args);

        let unknown = original.replacen("\"schema\":", "\"extra\":1,\"schema\":", 1);
        fs::write(args.package_root.join(COMPLETION_FILE), &unknown).unwrap();
        args.completion_manifest_sha256 = sha256(unknown.as_bytes());
        rejects(&args);

        let duplicate = original.replacen(
            "\"schema\":",
            "\"schema\":\"sfm:source_release_package@1\",\"schema\":",
            1,
        );
        fs::write(args.package_root.join(COMPLETION_FILE), &duplicate).unwrap();
        args.completion_manifest_sha256 = sha256(duplicate.as_bytes());
        rejects(&args);
        fs::write(args.package_root.join(COMPLETION_FILE), original).unwrap();
        args.completion_manifest_sha256 = original_digest;
        verify(&args).unwrap();
    }

    #[test]
    fn rejects_changed_inventory_bytes_or_target_mapping_even_with_updated_digests() {
        let (_fixture, mut args) = fixture_package();
        let inventory_path = args.package_root.join(INVENTORY_FILE);
        let original = fs::read(&inventory_path).unwrap();
        fs::write(&inventory_path, b"changed inventory").unwrap();
        rejects(&args);

        let unknown = std::str::from_utf8(&original).unwrap().replacen(
            "\"schema\":",
            "\"extra\":1,\"schema\":",
            1,
        );
        fs::write(&inventory_path, &unknown).unwrap();
        let mut completion = manifest(&args.package_root);
        completion.inventory_sha256 = sha256(unknown.as_bytes());
        write_manifest(&mut args, &completion);
        rejects(&args);

        let mut inventory: ReleaseInventory =
            facet_json::from_str(std::str::from_utf8(&original).unwrap()).unwrap();
        inventory.schema = "sfm:source_release_inventory@2".to_owned();
        let changed = facet_json::to_string_pretty(&inventory).unwrap() + "\n";
        fs::write(&inventory_path, &changed).unwrap();
        completion.inventory_sha256 = sha256(changed.as_bytes());
        write_manifest(&mut args, &completion);
        rejects(&args);

        inventory.schema = INVENTORY_SCHEMA.to_owned();
        inventory.scope = "publishable".to_owned();
        let changed = facet_json::to_string_pretty(&inventory).unwrap() + "\n";
        fs::write(&inventory_path, &changed).unwrap();
        completion.inventory_sha256 = sha256(changed.as_bytes());
        write_manifest(&mut args, &completion);
        rejects(&args);

        inventory.scope = INVENTORY_SCOPE.to_owned();
        inventory.targets[0].minecraft_version = "1.19.4".to_owned();
        let changed = facet_json::to_string_pretty(&inventory).unwrap() + "\n";
        fs::write(&inventory_path, &changed).unwrap();
        completion.inventory_sha256 = sha256(changed.as_bytes());
        write_manifest(&mut args, &completion);
        rejects(&args);
    }

    #[test]
    fn rejects_missing_extra_or_changed_jar() {
        let (_fixture, args) = fixture_package();
        let extra = args.package_root.join("unlocked.jar");
        fs::write(&extra, b"extra").unwrap();
        rejects(&args);
        fs::remove_file(extra).unwrap();

        let jar = args
            .package_root
            .join(&manifest(&args.package_root).targets[0].file_name);
        let original = fs::read(&jar).unwrap();
        fs::remove_file(&jar).unwrap();
        rejects(&args);
        fs::write(&jar, b"changed jar").unwrap();
        rejects(&args);
        fs::write(&jar, original).unwrap();
        verify(&args).unwrap();
    }

    #[test]
    fn rejects_unsafe_and_duplicate_names_or_targets() {
        let (_fixture, mut args) = fixture_package();
        let original = fs::read(args.package_root.join(COMPLETION_FILE)).unwrap();
        let original_digest = args.completion_manifest_sha256.clone();
        let mut changed = manifest(&args.package_root);
        changed.targets[0].file_name = "../outside.jar".to_owned();
        write_manifest(&mut args, &changed);
        rejects(&args);

        fs::write(args.package_root.join(COMPLETION_FILE), &original).unwrap();
        let mut changed = manifest(&args.package_root);
        changed.targets[1].file_name = changed.targets[0].file_name.clone();
        write_manifest(&mut args, &changed);
        rejects(&args);

        fs::write(args.package_root.join(COMPLETION_FILE), &original).unwrap();
        let mut changed = manifest(&args.package_root);
        changed.targets[1].target_id = changed.targets[0].target_id.clone();
        write_manifest(&mut args, &changed);
        rejects(&args);

        fs::write(args.package_root.join(COMPLETION_FILE), &original).unwrap();
        let mut changed = manifest(&args.package_root);
        changed.lock_sha256 = sha256(b"different lock");
        write_manifest(&mut args, &changed);
        rejects(&args);
        fs::write(args.package_root.join(COMPLETION_FILE), original).unwrap();
        args.completion_manifest_sha256 = original_digest;
        verify(&args).unwrap();
    }

    #[test]
    fn rejects_reparse_package_file_when_symlinks_are_available() {
        let (_fixture, args) = fixture_package();
        let jar = args
            .package_root
            .join(&manifest(&args.package_root).targets[0].file_name);
        let replacement = args
            .package_root
            .parent()
            .unwrap()
            .join("replacement-outside-package");
        fs::rename(&jar, &replacement).unwrap();
        #[cfg(windows)]
        let linked = std::os::windows::fs::symlink_file(&replacement, &jar).is_ok();
        #[cfg(unix)]
        let linked = std::os::unix::fs::symlink(&replacement, &jar).is_ok();
        if !linked {
            return;
        }
        rejects(&args);
    }

    #[test]
    fn rejects_reparse_package_root_when_symlinks_are_available() {
        let (_fixture, mut args) = fixture_package();
        let link = args.package_root.parent().unwrap().join("package-link");
        #[cfg(windows)]
        let linked = std::os::windows::fs::symlink_dir(&args.package_root, &link).is_ok();
        #[cfg(unix)]
        let linked = std::os::unix::fs::symlink(&args.package_root, &link).is_ok();
        if !linked {
            return;
        }
        args.package_root = link;
        rejects(&args);
    }
}
