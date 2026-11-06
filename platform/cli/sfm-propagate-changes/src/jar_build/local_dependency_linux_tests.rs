//! Synthetic store/route protocol tests, not Minecraft/NFRT execution receipts.
//! Kept under the launch-contract test module so no production DTO constructor
//! or producer-admission bypass is introduced for fixtures.
use super::*;
use crate::jar_build::local_dependency_cache::linux::LocalDependencyCache;
use crate::jar_build::local_dependency_cache::linux_io::Directory;
use crate::jar_build::local_dependency_image::LocalDependencyImage;
use crate::jar_build::local_dependency_key::LocalDependencyKey;
use crate::jar_build::local_dependency_manifest::fixture_manifest;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
use crate::source_projection::provenance::sha256;
use std::fs;
use std::io::Write as _;
use std::os::unix::fs::PermissionsExt as _;
use std::path::Path;
use std::sync::Arc;
use std::time::Duration;
use std::time::Instant;

fn prepared(owner: &str) -> PreparedNfrtInvocationInput {
    let mut receipt = crate::jar_build::local_dependency_key::fixture_receipt();
    receipt.invocation_id = format!("model-invocation-{owner}");
    receipt.projection_key = format!("model-projection-{owner}");
    receipt.preparation_identity = sha256(owner.as_bytes());
    PreparedNfrtInvocationInput {
        serialized: facet_json::to_string(&receipt).unwrap(),
        receipt,
        snapshots: vec![Arc::from(b"version".as_slice())],
        source_pin_origin: "synthetic-model-only",
        java_trust: None,
    }
}

fn key(input: &PreparedNfrtInvocationInput) -> LocalDependencyKey {
    LocalDependencyKey::from_prepared(
        input,
        &sha256(b"sdk"),
        &sha256(b"image"),
        &sha256(b"host"),
        None,
    )
    .unwrap()
}

fn zip(path: &Path, entries: &[(&str, &[u8])]) -> eyre::Result<Vec<u8>> {
    let mut archive = zip::ZipWriter::new(fs::File::create(path)?);
    for (name, bytes) in entries {
        archive.start_file(*name, zip::write::SimpleFileOptions::default())?;
        archive.write_all(bytes)?;
    }
    archive.finish()?;
    Ok(fs::read(path)?)
}

fn produce(
    cache: &mut LocalDependencyCache,
    input: &PreparedNfrtInvocationInput,
    directory: &Path,
) -> eyre::Result<()> {
    let classes = directory.join("model-classes.jar");
    let sources = directory.join("model-sources.jar");
    let class_bytes = zip(
        &classes,
        &[("net/minecraft/Example.class", b"model class bytes")],
    )?;
    let source_bytes = zip(
        &sources,
        &[(
            "net/minecraft/Example.java",
            b"package net.minecraft; class Example {}",
        )],
    )?;
    let manifest = fixture_manifest(&key(input), &class_bytes, &source_bytes);
    let mut receipts: Vec<NfrtProducerOutputReceipt> =
        facet_json::from_str(&manifest.production_receipts_json)?;
    for receipt in &mut receipts {
        receipt.scope.prepared_contract_sha256 = input.contract_identity();
    }
    let proof = cache.publish_verified(
        input,
        &classes,
        &sources,
        &[&receipts[0], &receipts[1]],
        &crate::cancellation::CancellationToken::new(),
    )?;
    assert!(
        proof
            .unwrap()
            .confirms(&input.contract_identity(), &[&receipts[0], &receipts[1]])
    );
    Ok(())
}

fn payload_inventory(root: &Path) -> eyre::Result<Vec<(String, u64)>> {
    let mut paths = vec![root.to_owned()];
    let mut output = Vec::new();
    while let Some(path) = paths.pop() {
        for entry in fs::read_dir(path)? {
            let entry = entry?;
            if entry.file_type()?.is_dir() {
                paths.push(entry.path());
            } else {
                output.push((
                    entry.path().strip_prefix(root)?.display().to_string(),
                    entry.metadata()?.len(),
                ));
            }
        }
    }
    output.sort();
    Ok(output)
}

#[test]
fn linux_explicit_root_model_cold_then_warm_reuses_pair_and_source_filetree() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let root = temp.path().join("cache");
    let cancel = crate::cancellation::CancellationToken::new();
    let input = prepared("first");
    let mut cold = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
    assert!(cold.lookup(&input, &cancel)?.is_none());
    produce(&mut cold, &input, temp.path())?;
    let original = cold.paths(&cancel)?;
    cold.release_producer();
    cold.recheck(&cancel)?;
    drop(cold);
    let changed_source = prepared("source-only-change");
    assert_eq!(key(&input), key(&changed_source));
    let mut warm = LocalDependencyCache::begin(&root, key(&changed_source), &cancel)?;
    let pair = warm
        .lookup(&changed_source, &cancel)?
        .expect("warm reuse, no model producer needed");
    assert_eq!(pair, original);
    warm.release_producer();
    let tree = warm.materialize_sources(&pair.1, &cancel)?;
    assert!(tree.join("net/minecraft/Example.java").is_file());
    let before = payload_inventory(&root)?;
    warm.recheck(&cancel)?;
    drop(warm);
    for _ in 0..4 {
        let mut next = LocalDependencyCache::begin(&root, key(&changed_source), &cancel)?;
        assert_eq!(next.lookup(&changed_source, &cancel)?.unwrap(), pair);
        next.release_producer();
        assert_eq!(next.materialize_sources(&pair.1, &cancel)?, tree);
        next.recheck(&cancel)?;
    }
    assert_eq!(
        payload_inventory(&root)?,
        before,
        "unchanged warm runs must not add payload copies or receipts"
    );
    Ok(())
}

#[test]
fn linux_cache_same_size_mutation_replacement_and_corruption_are_errors() -> eyre::Result<()> {
    for attack in ["same-size", "replacement", "head", "manifest"] {
        let temp = tempfile::tempdir()?;
        let root = temp.path().join("cache");
        let cancel = crate::cancellation::CancellationToken::new();
        let input = prepared("first");
        let mut cache = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
        produce(&mut cache, &input, temp.path())?;
        let pair = cache.paths(&cancel)?;
        cache.release_producer();
        let leaf = match attack {
            "head" => root.join(key(&input).digest()).join("current.json"),
            "manifest" => pair.0.parent().unwrap().join("manifest.json"),
            _ => pair.0.clone(),
        };
        let original = fs::read(&leaf)?;
        if attack == "replacement" {
            fs::rename(&leaf, leaf.with_extension("old"))?;
            fs::write(&leaf, &original)?;
            fs::set_permissions(&leaf, fs::Permissions::from_mode(0o400))?;
        } else {
            fs::set_permissions(&leaf, fs::Permissions::from_mode(0o600))?;
            let mut changed = original.clone();
            changed[0] ^= 1;
            fs::write(&leaf, changed)?;
        }
        assert!(cache.recheck(&cancel).is_err(), "{attack}");
        drop(cache);
        let mut next = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
        if attack == "replacement" {
            assert!(next.lookup(&input, &cancel)?.is_some());
        } else {
            assert!(next.lookup(&input, &cancel).is_err());
        }
        assert_eq!(fs::read(&leaf)?.len(), original.len(), "evidence remains");
    }
    Ok(())
}

#[test]
fn linux_cache_refuses_links_foreign_root_and_replaced_ancestry_or_lock() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let root = temp.path().join("cache");
    let cancel = crate::cancellation::CancellationToken::new();
    let input = prepared("first");
    let mut cache = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
    let lock = root.join(key(&input).digest()).join("producer.lock");
    fs::rename(&lock, lock.with_extension("old"))?;
    fs::write(&lock, b"")?;
    assert!(cache.lookup(&input, &cancel).is_err());
    drop(cache);
    let directory = Directory::open(&root)?;
    fs::rename(&root, temp.path().join("moved"))?;
    fs::create_dir(&root)?;
    assert!(directory.recheck().is_err());
    fs::write(root.join("foreign"), b"not adopted")?;
    assert!(LocalDependencyCache::begin(&root, key(&input), &cancel).is_err());
    let linked = temp.path().join("linked");
    std::os::unix::fs::symlink(&root, &linked)?;
    assert!(LocalDependencyCache::begin(&linked, key(&input), &cancel).is_err());
    Ok(())
}

#[test]
fn linux_cache_partial_entries_cancel_and_no_replace_preserve_evidence() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let root = temp.path().join("cache");
    let cancel = crate::cancellation::CancellationToken::new();
    let input = prepared("first");
    let mut cache = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
    let key_root = root.join(key(&input).digest());
    fs::create_dir(key_root.join("entry-orphan"))?;
    fs::write(key_root.join("entry-orphan/classes.jar"), b"partial")?;
    assert!(cache.lookup(&input, &cancel)?.is_none());
    let directory = Directory::open(&key_root)?;
    directory.publish("collision", b"foreign", &cancel)?;
    assert!(
        directory
            .publish("collision", b"replacement", &cancel)
            .is_err()
    );
    assert_eq!(fs::read(key_root.join("collision"))?, b"foreign");
    cancel.request_cancel("bounded cache cancellation");
    assert!(cache.lookup(&input, &cancel).is_err());
    assert!(
        directory
            .publish("cancelled", b"must not publish", &cancel)
            .is_err()
    );
    assert!(!key_root.join("cancelled").exists());
    assert_eq!(
        fs::read(key_root.join("entry-orphan/classes.jar"))?,
        b"partial"
    );
    Ok(())
}

fn sdk(home: &Path) -> eyre::Result<()> {
    for name in ["bin", "conf", "lib", "lib/server"] {
        fs::create_dir_all(home.join(name))?;
    }
    fs::write(home.join("bin/java"), b"held SDK core")?;
    fs::write(home.join("bin/javac"), b"held SDK compiler")?;
    fs::write(home.join("release"), b"held SDK release")?;
    fs::write(home.join("lib/modules"), b"held SDK modules")?;
    fs::write(home.join("lib/server/libjvm.so"), b"native runtime")?;
    fs::write(home.join("conf/jvm.cfg"), b"configuration")?;
    Ok(())
}

#[test]
fn linux_sdk_image_binds_contained_file_and_directory_links_and_full_contents() -> eyre::Result<()>
{
    let temp = tempfile::tempdir()?;
    sdk(temp.path())?;
    std::os::unix::fs::symlink("server/libjvm.so", temp.path().join("lib/libjvm-alias.so"))?;
    std::os::unix::fs::symlink("server", temp.path().join("lib/server-alias"))?;
    let cancel = crate::cancellation::CancellationToken::new();
    let image = LocalDependencyImage::capture_home(temp.path(), &sha256(b"sdk"), &cancel)?;
    image.recheck(&cancel)?;
    fs::write(temp.path().join("lib/server/libjvm.so"), b"mutant runtime")?;
    assert!(image.recheck(&cancel).is_err());
    Ok(())
}

#[test]
fn linux_sdk_image_rejects_outward_links_hardlinks_membership_and_replacement() -> eyre::Result<()>
{
    for attack in ["outward", "hardlink", "membership", "replacement"] {
        let temp = tempfile::tempdir()?;
        sdk(temp.path())?;
        let cancel = crate::cancellation::CancellationToken::new();
        let image = LocalDependencyImage::capture_home(temp.path(), &sha256(b"sdk"), &cancel)?;
        match attack {
            "outward" => {
                std::os::unix::fs::symlink("/etc/ssl/certs", temp.path().join("conf/certs"))?
            }
            "hardlink" => fs::hard_link(
                temp.path().join("lib/server/libjvm.so"),
                temp.path().join("lib/alias"),
            )?,
            "membership" => fs::write(temp.path().join("conf/new.cfg"), b"new input")?,
            _ => {
                fs::rename(temp.path().join("conf"), temp.path().join("old-conf"))?;
                fs::create_dir(temp.path().join("conf"))?;
                fs::write(temp.path().join("conf/jvm.cfg"), b"configuration")?;
            }
        }
        assert!(image.recheck(&cancel).is_err(), "{attack}");
    }
    Ok(())
}

#[test]
fn linux_cache_process_worker() -> eyre::Result<()> {
    let Ok(root) = std::env::var("SFM_LINUX_CACHE_TEST_ROOT") else {
        return Ok(());
    };
    let root = std::path::PathBuf::from(root);
    if std::env::var_os("SFM_LINUX_CACHE_UMASK_ZERO").is_some() {
        unsafe extern "C" {
            fn umask(mask: u32) -> u32;
        }
        // SAFETY: only this dedicated child test process changes its own umask.
        unsafe {
            umask(0);
        }
    }
    let input = prepared("process-consumer");
    let cancel = crate::cancellation::CancellationToken::new();
    let mut cache = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
    if cache.lookup(&input, &cancel)?.is_none() {
        let model_invocation = root.parent().unwrap().join("model-producer-launched-once");
        fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(model_invocation)?;
        std::thread::sleep(Duration::from_millis(100));
        produce(&mut cache, &input, root.parent().unwrap())?;
    }
    cache.release_producer();
    cache.recheck(&cancel)?;
    Ok(())
}

// Test workers are always reaped on assertion, timeout or spawn failure.
struct Workers(Vec<std::process::Child>);
impl Drop for Workers {
    fn drop(&mut self) {
        for child in &mut self.0 {
            match child.try_wait() {
                Ok(Some(_)) => (),
                _ => {
                    let _ = child.kill();
                    let _ = child.wait();
                }
            }
        }
    }
}

#[test]
fn linux_failed_worker_group_reaps_every_child() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let mut workers = Workers(Vec::new());
    for _ in 0..3 {
        workers.0.push(
            std::process::Command::new(std::env::current_exe()?)
                .arg("linux_cache_process_worker")
                .env(
                    "SFM_LINUX_CACHE_TEST_ROOT",
                    temp.path().join("unsupported-profile"),
                )
                .env("LD_LIBRARY_PATH", "")
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .spawn()?,
        );
    }
    let pids = workers
        .0
        .iter()
        .map(std::process::Child::id)
        .collect::<Vec<_>>();
    drop(workers);
    for pid in pids {
        assert!(!Path::new("/proc").join(pid.to_string()).exists());
    }
    Ok(())
}

#[test]
fn linux_multi_process_producer_and_readers_share_one_pair() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let root = temp.path().join("cache");
    let mut children = Workers(Vec::new());
    for _ in 0..3 {
        children.0.push(
            std::process::Command::new(std::env::current_exe()?)
                .arg("linux_cache_process_worker")
                .arg("--nocapture")
                .env("SFM_LINUX_CACHE_TEST_ROOT", &root)
                .env("SFM_LINUX_CACHE_UMASK_ZERO", "1")
                .spawn()?,
        );
    }
    let deadline = Instant::now() + Duration::from_secs(15);
    for child in &mut children.0 {
        loop {
            if let Some(status) = child.try_wait()? {
                assert!(status.success());
                break;
            }
            if Instant::now() > deadline {
                child.kill()?;
                child.wait()?;
                eyre::bail!("bounded cache worker timed out");
            }
            std::thread::sleep(Duration::from_millis(10));
        }
    }
    assert!(temp.path().join("model-producer-launched-once").is_file());
    assert_eq!(fs::metadata(&root)?.permissions().mode() & 0o777, 0o700);
    for entry in fs::read_dir(root.join(key(&prepared("process-consumer")).digest()))? {
        let entry = entry?;
        if entry.file_type()?.is_dir() {
            assert_eq!(entry.metadata()?.permissions().mode() & 0o777, 0o700);
        }
    }
    assert_eq!(
        payload_inventory(&root)?
            .iter()
            .filter(|(p, _)| p.ends_with("classes.jar"))
            .count(),
        1
    );
    assert_eq!(
        payload_inventory(&root)?
            .iter()
            .filter(|(p, _)| p.ends_with("sources.jar"))
            .count(),
        1
    );
    Ok(())
}

#[test]
fn linux_source_filetree_never_follows_foreign_marker_or_head_links() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let root = temp.path().join("cache");
    let input = prepared("first");
    let cancel = crate::cancellation::CancellationToken::new();
    let mut cache = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
    produce(&mut cache, &input, temp.path())?;
    cache.release_producer();
    let sources = cache.paths(&cancel)?.1;
    let entry = sources.parent().unwrap();
    let sentinel = temp.path().join("outside");
    fs::write(&sentinel, b"foreign sentinel")?;
    std::os::unix::fs::symlink(&sentinel, entry.join("sources.filetree.input.blake3"))?;
    assert!(
        cache
            .materialize_sources(&sources, &cancel)?
            .join("net/minecraft/Example.java")
            .is_file()
    );
    assert_eq!(fs::read(&sentinel)?, b"foreign sentinel");
    fs::rename(
        entry.join("source-filetree.json"),
        entry.join("retained-tree-head"),
    )?;
    std::os::unix::fs::symlink(&sentinel, entry.join("source-filetree.json"))?;
    assert!(cache.materialize_sources(&sources, &cancel).is_err());
    assert_eq!(fs::read(&sentinel)?, b"foreign sentinel");
    Ok(())
}

#[test]
fn linux_entry_permission_changes_are_rejected_before_consumption() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let root = temp.path().join("cache");
    let input = prepared("first");
    let cancel = crate::cancellation::CancellationToken::new();
    let mut cache = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
    produce(&mut cache, &input, temp.path())?;
    let classes = cache.paths(&cancel)?.0;
    fs::set_permissions(classes.parent().unwrap(), fs::Permissions::from_mode(0o777))?;
    assert!(cache.recheck(&cancel).is_err());
    cache.release_producer();
    let mut next = LocalDependencyCache::begin(&root, key(&input), &cancel)?;
    assert!(next.lookup(&input, &cancel).is_err());
    Ok(())
}

#[test]
fn linux_compiler_binding_ignores_source_owner_but_binds_trust_and_dependencies() -> eyre::Result<()>
{
    let temp = tempfile::tempdir()?;
    let cancel = crate::cancellation::CancellationToken::new();
    let root = temp.path().join("cache");
    let first = prepared("first");
    let cache = LocalDependencyCache::begin(&root, key(&first), &cancel)?;
    let identity = cache.compiler_identity();
    drop(cache);
    let owner = prepared("other-source-owner");
    let cache = LocalDependencyCache::begin(&root, key(&owner), &cancel)?;
    assert_eq!(cache.compiler_identity(), identity);
    drop(cache);
    let mut trust = prepared("first");
    trust.receipt.java_trust_sha256 = Some(sha256(b"changed exact trust store"));
    let cache = LocalDependencyCache::begin(&root, key(&trust), &cancel)?;
    assert_ne!(cache.compiler_identity(), identity);
    drop(cache);
    let mut dependency = prepared("first");
    dependency.receipt.inputs[0].full_sha256 = sha256(b"changed dependency artifact");
    let cache = LocalDependencyCache::begin(&root, key(&dependency), &cancel)?;
    assert_ne!(cache.compiler_identity(), identity);
    Ok(())
}
