//! Linux support-image authority, distinct from fresh NFRT receipts.
//! Only immutable root-owned image paths can authorize reuse. Export candidates
//! in writable workspaces never authorize execution; image composition reviews
//! and installs them separately. No writable javac output cache is admitted.
use crate::jar_build::nfrt_launch_contract::PreparedNfrtInvocationInput;
use crate::source_projection::nfrt_project_owner::NfrtProjectOwner;
use crate::source_projection::nfrt_tool_sdk::NfrtToolSdk;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::fs;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;

const PROFILE: &str = "/opt/sfm/derived-toolchains";
const AT: &str = "src/main/resources/META-INF/accesstransformer.cfg";
const LIMIT: u64 = 256 * 1024 * 1024;

#[derive(Clone, Debug, Facet, PartialEq)]
struct Output {
    name: String,
    bytes: u64,
    sha256: String,
}
#[derive(Debug, Facet)]
struct Manifest {
    schema: String,
    key: String,
    production_contract_sha256: String,
    production_origin: String,
    production_receipts_json: String,
    classes: Output,
    sources: Output,
}

pub(super) struct ImmutableProfile {
    pub classes: PathBuf,
    _sources: PathBuf,
    // Keep verified descriptors alive; paths are protected from runtime UID
    // replacement by every root-owned, non-writable ancestor, not by these FDs.
    _files: Vec<fs::File>,
}

fn digest(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn append(hasher: &mut Sha256, bytes: &[u8]) {
    hasher.update((bytes.len() as u64).to_le_bytes());
    hasher.update(bytes);
}

/// The complete prepared recipe is retained except invocation/source-owner
/// labels, which are rechecked separately and do not affect transformation.
pub(super) fn key(
    project: &dyn NfrtProjectOwner,
    prepared: &PreparedNfrtInvocationInput,
    sdk: &mut NfrtToolSdk,
) -> Result<Option<String>> {
    let receipt = prepared.receipt().clone();
    if !cfg!(target_os = "linux")
        || receipt.environment != "dev"
        || !matches!(receipt.target_id.as_str(), "1.21.1" | "26.1.2")
    {
        return Ok(None);
    }
    project.recheck_source()?;
    sdk.recheck()?;
    let mut hash = Sha256::new();
    append(&mut hash, b"sfm:immutable_derived_key@1");
    append(&mut hash, std::env::consts::ARCH.as_bytes());
    append(&mut hash, std::env::consts::OS.as_bytes());
    let (_, base_identity) = verified(
        Path::new("/opt/sfm/derived-cache-base-identity"),
        None,
        1024,
    )?;
    append(&mut hash, &base_identity);
    append(&mut hash, stable_recipe(receipt)?.as_bytes());
    append(&mut hash, sdk.identity().as_bytes());
    append(&mut hash, &fs::read(std::env::current_exe()?)?);
    // Bind every SDK distribution file, including native libraries/plugins.
    let home = sdk
        .executable()
        .parent()
        .and_then(Path::parent)
        .ok_or_else(|| eyre::eyre!("Missing SDK home"))?;
    hash_sdk_tree(home, home, &mut hash)?;
    if project.project().receipt().files.contains_key(AT) {
        append(&mut hash, AT.as_bytes());
        append(
            &mut hash,
            project.project().selected_input(AT)?.output_bytes(),
        );
    } else {
        append(&mut hash, b"no-project-access-transformer");
    }
    project.recheck_source()?;
    sdk.recheck()?;
    Ok(Some(format!("{:x}", hash.finalize())))
}

fn stable_recipe(
    mut receipt: crate::jar_build::nfrt_launch_contract::NamedNfrtInvocationInput,
) -> Result<String> {
    receipt.invocation_id.clear();
    receipt.projection_key.clear();
    receipt.preparation_identity.clear();
    // Development request/supplement IDs include source preparation labels.
    // Keep their actual locked inputs, recipe bytes and content digests below.
    receipt.original_request_catalog_identity.clear();
    receipt.supplement_identity.clear();
    Ok(facet_json::to_string(&receipt)?)
}

fn hash_sdk_tree(home: &Path, directory: &Path, hash: &mut Sha256) -> Result<()> {
    let mut entries = fs::read_dir(directory)?.collect::<std::io::Result<Vec<_>>>()?;
    entries.sort_by_key(fs::DirEntry::file_name);
    for entry in entries {
        let path = entry.path();
        let metadata = fs::symlink_metadata(&path)?;
        let checked = if metadata.is_symlink() {
            fs::canonicalize(&path)?
        } else {
            path.clone()
        };
        protected(&checked, !metadata.is_dir())?;
        append(hash, path.strip_prefix(home)?.to_string_lossy().as_bytes());
        if metadata.is_symlink() {
            let target = fs::canonicalize(&path)?;
            ensure!(
                target.starts_with(home) && target.is_file(),
                "Unsafe SDK link"
            );
            append(hash, fs::read_link(&path)?.to_string_lossy().as_bytes());
            append(hash, &fs::read(target)?);
        } else if metadata.is_dir() {
            hash_sdk_tree(home, &path, hash)?;
        } else {
            ensure!(metadata.is_file(), "Nonregular SDK input");
            append(hash, &fs::read(path)?);
        }
    }
    Ok(())
}

#[cfg(target_os = "linux")]
fn protected(path: &Path, file: bool) -> Result<()> {
    use std::os::unix::fs::MetadataExt as _;
    ensure!(path.is_absolute(), "Profile path must be absolute");
    let mut selected = PathBuf::from("/");
    for component in path.components().skip(1) {
        ensure!(
            matches!(component, std::path::Component::Normal(_)),
            "Unsafe profile path"
        );
        selected.push(component);
        let metadata = fs::symlink_metadata(&selected)?;
        ensure!(
            !metadata.is_symlink() && metadata.uid() == 0 && metadata.mode() & 0o022 == 0,
            "Profile has unsafe path/ownership/write permissions"
        );
        if selected == path && file {
            ensure!(
                metadata.is_file() && metadata.nlink() == 1,
                "Profile file is nonregular or hardlinked"
            );
        } else {
            ensure!(metadata.is_dir(), "Profile ancestor is not a directory");
        }
    }
    Ok(())
}
#[cfg(not(target_os = "linux"))]
fn protected(_: &Path, _: bool) -> Result<()> {
    eyre::bail!("Immutable profile is Linux-only")
}

fn verified(path: &Path, expected: Option<&Output>, bound: u64) -> Result<(fs::File, Vec<u8>)> {
    protected(path, true)?;
    let mut options = fs::OpenOptions::new();
    options.read(true);
    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::OpenOptionsExt as _;
        options.custom_flags(0o400_000);
    }
    let mut file = options.open(path)?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file() && metadata.len() > 0 && metadata.len() <= bound,
        "Profile file size invalid"
    );
    let mut bytes = Vec::new();
    (&mut file).take(bound + 1).read_to_end(&mut bytes)?;
    protected(path, true)?;
    ensure!(
        bytes.len() as u64 == metadata.len(),
        "Profile file changed during read"
    );
    if let Some(output) = expected {
        validate_output(&bytes, output)?;
    }
    Ok((file, bytes))
}

fn support_process(status: &str) -> bool {
    let pair = |key: &str, value: &str| {
        status
            .lines()
            .any(|line| line.split_whitespace().eq([key, value]))
    };
    status.lines().any(|line| {
        line.split_whitespace()
            .eq(["Uid:", "10001", "10001", "10001", "10001"])
    }) && ["CapPrm:", "CapEff:", "CapAmb:"]
        .iter()
        .all(|key| pair(key, "0000000000000000"))
        && pair("NoNewPrivs:", "1")
}

fn read_profile(root: &Path, key: &str) -> Result<ImmutableProfile> {
    ensure!(
        key.len() == 64
            && key
                .bytes()
                .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase()),
        "Invalid profile key"
    );
    ensure!(
        support_process(&fs::read_to_string("/proc/self/status")?),
        "Immutable profile requires UID10001, zero permitted/effective/ambient capabilities and no-new-privileges"
    );
    let entry = root.join(key);
    protected(&entry, false)?;
    let (manifest_file, bytes) = verified(&entry.join("manifest.json"), None, 64 * 1024)?;
    let manifest: Manifest = facet_json::from_slice(&bytes)?;
    validate_manifest(&manifest, key)?;
    let classes = entry.join("classes.jar");
    let sources = entry.join("sources.jar");
    let (class_file, _) = verified(&classes, Some(&manifest.classes), LIMIT)?;
    let (source_file, _) = verified(&sources, Some(&manifest.sources), LIMIT)?;
    eprintln!(
        "Derived toolchain hit: authority=immutable-root-owned-profile key={key} historical-production={} current-consumption=verified (not fresh execution)",
        manifest.production_contract_sha256
    );
    Ok(ImmutableProfile {
        classes,
        _sources: sources,
        _files: vec![manifest_file, class_file, source_file],
    })
}

pub(super) fn lookup(key: &str) -> Option<ImmutableProfile> {
    match read_profile(Path::new(PROFILE), key) {
        Ok(profile) => Some(profile),
        Err(error) => {
            eprintln!("Derived toolchain miss: {error:#}; using fresh owned execution");
            None
        }
    }
}

/// Called only inside successful fresh held-output consumption. Export is a
/// candidate for separate trusted image composition, never runtime authority.
pub(super) fn export(
    key: &str,
    contract: &str,
    classes: &Path,
    sources: &Path,
    receipts: &[&crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
         2],
) -> Result<()> {
    let Some(root) = std::env::var_os("SFM_AGENT_DERIVED_EXPORT_ROOT") else {
        return Ok(());
    };
    let root = PathBuf::from(root);
    ensure!(
        root.is_absolute() && root.is_dir() && !fs::symlink_metadata(&root)?.is_symlink(),
        "Unsafe export root"
    );
    let staging = root.join(format!(
        ".stage-{}-{}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)?
            .as_nanos()
    ));
    fs::create_dir(&staging)?;
    let output = |source: &Path, name: &str, expected: &crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt| -> Result<Output> {
        let metadata = fs::metadata(source)?;
        ensure!(metadata.is_file() && metadata.len() > 0 && metadata.len() <= LIMIT, "Export output bound invalid");
        let bytes = fs::read(source)?;
        ensure!(bytes.len() == expected.bytes && digest(&bytes) == expected.full_sha256.trim_start_matches("sha256:"), "Export bytes differ from the privately sealed fresh producer receipt");
        let mut file = fs::OpenOptions::new().write(true).create_new(true).open(staging.join(name))?;
        file.write_all(&bytes)?; file.sync_all()?;
        Ok(Output { name: name.to_owned(), bytes: bytes.len() as u64, sha256: digest(&bytes) })
    };
    let manifest = Manifest {
        schema: "sfm:immutable_derived_profile@1".to_owned(),
        key: key.to_owned(),
        production_contract_sha256: contract.trim_start_matches("sha256:").to_owned(),
        production_origin: "owned-fresh-export".to_owned(),
        production_receipts_json: facet_json::to_string(receipts)?,
        classes: output(classes, "classes.jar", receipts[0])?,
        sources: output(sources, "sources.jar", receipts[1])?,
    };
    let mut file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(staging.join("manifest.json"))?;
    let manifest_text = facet_json::to_string(&manifest)?;
    file.write_all(manifest_text.as_bytes())?;
    file.sync_all()?;
    fs::File::open(&staging)?.sync_all()?;
    // Nonempty directory rename refuses races/conflicts; never replace entries.
    validate_manifest(&manifest, key)?;
    publish(&staging, &root.join(key))?;
    fs::File::open(&root)?.sync_all()?;
    eprintln!(
        "SFM_DERIVED_EXPORT_COMMIT key={key} manifest_sha256={}",
        digest(manifest_text.as_bytes())
    );
    eprintln!(
        "Derived toolchain exported candidate: key={key}; requires immutable image composition"
    );
    Ok(())
}

fn validate_manifest(manifest: &Manifest, key: &str) -> Result<()> {
    use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
    ensure!(
        manifest.schema == "sfm:immutable_derived_profile@1"
            && manifest.key == key
            && manifest.production_origin == "owned-fresh-export"
            && manifest.production_contract_sha256.len() == 64
            && manifest
                .production_contract_sha256
                .bytes()
                .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
            && manifest.classes.name == "classes.jar"
            && manifest.sources.name == "sources.jar",
        "Profile manifest authority/key/roles invalid"
    );
    let receipts: Vec<NfrtProducerOutputReceipt> =
        facet_json::from_str(&manifest.production_receipts_json)?;
    ensure!(
        receipts.len() == 2 && receipts[0].scope == receipts[1].scope,
        "Missing/conflicting historical fresh receipt pair"
    );
    for (receipt, output, producer, kind) in [
        (
            &receipts[0],
            &manifest.classes,
            "compiledWithNeoForge",
            "JAR",
        ),
        (
            &receipts[1],
            &manifest.sources,
            "sourcesWithNeoForge",
            "ZIP",
        ),
    ] {
        ensure!(
            receipt.schema == "sfm:nfrt_fresh_producer_output@1"
                && receipt.completion_was_fresh
                && receipt.inherited_cache_restore_disabled
                && receipt.memory_snapshot_immutable
                && receipt
                    .scope
                    .prepared_contract_sha256
                    .trim_start_matches("sha256:")
                    == manifest.production_contract_sha256
                && receipt.declaration.producer_id == producer
                && receipt.declaration.output_id == "output"
                && receipt.declaration.output_type == kind
                && receipt.bytes as u64 == output.bytes
                && receipt.full_sha256.trim_start_matches("sha256:") == output.sha256,
            "Historical receipt/output binding invalid"
        );
    }
    Ok(())
}

fn validate_output(bytes: &[u8], expected: &Output) -> Result<()> {
    ensure!(
        expected.bytes == bytes.len() as u64 && expected.sha256 == digest(bytes),
        "Profile output digest/size mismatch"
    );
    Ok(())
}

fn publish(staging: &Path, destination: &Path) -> Result<()> {
    // Atomic exclusive reservation also refuses empty/partial destinations.
    fs::create_dir(destination)?;
    fs::rename(staging, destination.join("payload"))?;
    #[cfg(unix)]
    fs::File::open(destination)?.sync_all()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn derived_key_framing_distinguishes_boundaries_and_order() {
        let hash = |values: &[&[u8]]| {
            let mut hash = Sha256::new();
            for v in values {
                append(&mut hash, v);
            }
            hash.finalize()
        };
        assert_ne!(hash(&[b"ab", b"c"]), hash(&[b"a", b"bc"]));
        assert_ne!(hash(&[b"sdk", b"input"]), hash(&[b"input", b"sdk"]));
        assert_ne!(hash(&[b"sdk", b"input"]), hash(&[b"sdk", b"changed"]));
    }
    #[test]
    fn derived_profile_cannot_accept_writable_temp_manifest_or_missing_entry() {
        let temp = tempfile::tempdir().unwrap();
        assert!(read_profile(temp.path(), &"a".repeat(64)).is_err());
        assert!(read_profile(temp.path(), "../escape").is_err());
    }
    #[test]
    fn derived_output_tamper_and_size_changes_are_rejected() {
        let original = b"verified immutable bytes";
        let expected = Output {
            name: "classes.jar".into(),
            bytes: original.len() as u64,
            sha256: digest(original),
        };
        validate_output(original, &expected).unwrap();
        assert!(validate_output(b"replaced immutable bytes", &expected).is_err());
        assert!(validate_output(&original[..original.len() - 1], &expected).is_err());
    }
    #[test]
    fn derived_atomic_publication_refuses_conflicting_race_without_overwrite() {
        let temp = tempfile::tempdir().unwrap();
        let first = temp.path().join("first");
        let second = temp.path().join("second");
        let final_path = temp.path().join("entry");
        fs::create_dir(&first).unwrap();
        fs::create_dir(&second).unwrap();
        fs::write(first.join("manifest.json"), b"first").unwrap();
        fs::write(second.join("manifest.json"), b"conflict").unwrap();
        let (a, b) = std::thread::scope(|scope| {
            let a = scope.spawn(|| publish(&first, &final_path));
            let b = scope.spawn(|| publish(&second, &final_path));
            (a.join().unwrap(), b.join().unwrap())
        });
        assert_ne!(a.is_ok(), b.is_ok());
        let content = fs::read(final_path.join("payload/manifest.json")).unwrap();
        assert!(content == b"first" || content == b"conflict");
        assert!(publish(if first.exists() { &first } else { &second }, &final_path).is_err());
        assert_eq!(
            fs::read(final_path.join("payload/manifest.json")).unwrap(),
            content
        );
    }
    #[test]
    fn derived_publication_refuses_partial_empty_reservation() {
        let temp = tempfile::tempdir().unwrap();
        let staging = temp.path().join("stage");
        let dest = temp.path().join("partial");
        fs::create_dir(&staging).unwrap();
        fs::write(staging.join("manifest.json"), b"candidate").unwrap();
        fs::create_dir(&dest).unwrap();
        assert!(publish(&staging, &dest).is_err());
        assert!(staging.exists());
        assert!(!dest.join("payload").exists());
    }
    #[cfg(target_os = "linux")]
    #[test]
    #[ignore = "Requires root-owned fixtures composed into bounded nonroot test image"]
    fn derived_linux_immutable_profile_gate_and_swap_after_verify() {
        let root = Path::new("/opt/sfm/derived-cache-test-fixtures");
        let key = "a".repeat(64);
        let profile = read_profile(&root.join("valid"), &key).unwrap();
        let before = fs::read(&profile.classes).unwrap();
        assert!(fs::rename(&profile.classes, profile.classes.with_extension("swap")).is_err());
        assert!(fs::write(&profile.classes, b"swap after verify").is_err());
        assert_eq!(fs::read(&profile.classes).unwrap(), before);
        for case in [
            "corrupt",
            "partial",
            "wrong-key",
            "wrong-owner",
            "writable",
            "symlink",
            "hardlink",
            "bad-proof",
        ] {
            assert!(
                read_profile(&root.join(case), &key).is_err(),
                "Accepted unsafe fixture {case}"
            );
        }
    }
    #[test]
    fn derived_process_gate_rejects_reacquirable_caps_and_privilege_changes() {
        let status = "Uid: 10001 10001 10001 10001\nCapPrm: 0000000000000000\nCapEff: 0000000000000000\nCapAmb: 0000000000000000\nNoNewPrivs: 1\n";
        assert!(support_process(status));
        for field in ["CapPrm:", "CapEff:", "CapAmb:"] {
            assert!(!support_process(&status.replace(
                &format!("{field} 0000000000000000"),
                &format!("{field} 0000000000000001")
            )));
        }
        assert!(!support_process(
            &status.replace("NoNewPrivs: 1", "NoNewPrivs: 0")
        ));
        assert!(!support_process(&status.replace(
            "Uid: 10001 10001 10001 10001",
            "Uid: 10001 0 10001 10001"
        )));
    }
    #[test]
    fn derived_semantic_key_preserves_all_recipe_inputs_but_not_source_owner_labels() {
        let receipt: crate::jar_build::nfrt_launch_contract::NamedNfrtInvocationInput =
            facet_json::from_str(include_str!("fixtures/derived-contract-26.json")).unwrap();
        let original = stable_recipe(receipt.clone()).unwrap();
        let mut labels = receipt.clone();
        labels.invocation_id = "new invocation".into();
        labels.projection_key = "new derivative".into();
        labels.preparation_identity = "new Java source owner".into();
        labels.original_request_catalog_identity = "new owner-bound catalog".into();
        labels.supplement_identity = "new owner-bound supplement".into();
        assert_eq!(stable_recipe(labels).unwrap(), original);
        let mut mutations = Vec::new();
        let mut value = receipt.clone();
        value.source_lock_sha256.push('x');
        mutations.push(value);
        let mut value = receipt.clone();
        value.supplement_sha256.push('x');
        mutations.push(value);
        let mut value = receipt.clone();
        value.tool_sdk.supplied_executable_sha256.push('x');
        mutations.push(value);
        let mut value = receipt.clone();
        value.tool_sdk.original_archive_sha512 = Some("different SDK distribution".into());
        mutations.push(value);
        let mut value = receipt.clone();
        value.compiler_release += 1;
        mutations.push(value);
        let mut value = receipt.clone();
        value.inputs[0].full_sha256.push('x');
        mutations.push(value);
        let mut value = receipt.clone();
        value.inputs[0].bytes += 1;
        mutations.push(value);
        let mut value = receipt.clone();
        value.inputs[0].snapshot_relative_path.push('x');
        mutations.push(value);
        let mut value = receipt.clone();
        value.inputs[0].exact_url = Some("different pinned transport".into());
        mutations.push(value);
        let mut value = receipt.clone();
        value.functions[0].args.push("changed tool option".into());
        mutations.push(value);
        let mut value = receipt.clone();
        value.functions[0]
            .jvm_args
            .push("changed JVM option".into());
        mutations.push(value);
        let mut value = receipt.clone();
        value.functions[0]
            .classpath
            .push("different processor/tool".into());
        mutations.push(value);
        let mut value = receipt.clone();
        value.joined_steps_json.push("changed graph/config".into());
        mutations.push(value);
        let mut value = receipt.clone();
        value.ordered_dependency_rows.reverse();
        mutations.push(value);
        let mut value = receipt;
        value.inputs.reverse();
        mutations.push(value);
        for value in mutations {
            assert_ne!(stable_recipe(value).unwrap(), original);
        }
    }
}
