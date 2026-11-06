use super::*;
use std::os::unix::fs::{PermissionsExt as _, symlink};
use std::time::{Duration, Instant};

fn key() -> String {
    format!("sha256:{}", "a".repeat(64))
}
fn store(root: &Path, cancel: &CancellationToken) -> Result<Store> {
    Store::begin(
        root,
        "remap",
        &key(),
        &["specialsource.jar", "remapped.jar"],
        cancel,
    )
}
fn produce(cache: &mut Store, cancel: &CancellationToken) -> Result<()> {
    cache.produce(
        cancel,
        |attempt| {
            let paths = ["specialsource.jar", "remapped.jar"].map(|p| attempt.path.join(p));
            for path in &paths {
                fs::write(path, b"fresh fixture production")?;
            }
            Ok(paths.to_vec())
        },
        || Ok(()),
    )
}

#[test]
fn linux_forge_store_fresh_pair_and_historical_repeat_have_one_payload() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let cancel = CancellationToken::new();
    fs::write(fixture.path().join("legacy.jar"), b"never admitted")?;
    let mut cache = store(fixture.path(), &cancel)?;
    assert!(!cache.lookup(&cancel)?);
    produce(&mut cache, &cancel)?;
    assert!(cache.fresh);
    let path = cache.path("remapped.jar")?;
    let metadata = fs::metadata(&path)?;
    cache.release_producer();
    let before = fs::read_dir(cache.directory.access(""))?.count();
    let mut repeat = store(fixture.path(), &cancel)?;
    assert!(repeat.lookup(&cancel)?);
    assert!(!repeat.fresh);
    assert_eq!(path, repeat.path("remapped.jar")?);
    assert_eq!(metadata.modified()?, fs::metadata(&path)?.modified()?);
    assert_eq!(before, fs::read_dir(cache.directory.access(""))?.count());
    repeat.release_producer();
    cache.recheck(&cancel)?;
    repeat.recheck(&cancel)?;
    Ok(())
}

#[test]
fn linux_forge_store_rejects_corrupt_missing_extra_swapped_and_linked_payloads() -> Result<()> {
    for mutation in [
        "same-size",
        "missing",
        "extra",
        "swapped",
        "symlink",
        "hardlink",
        "mode",
        "head",
        "manifest",
    ] {
        let fixture = tempfile::tempdir()?;
        let cancel = CancellationToken::new();
        let mut cache = store(fixture.path(), &cancel)?;
        produce(&mut cache, &cancel)?;
        let path = cache.path("remapped.jar")?;
        let entry = path.parent().unwrap();
        match mutation {
            "same-size" => {
                fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?;
                fs::write(&path, b"WRONG fixture production")?;
                fs::set_permissions(&path, fs::Permissions::from_mode(0o400))?;
            }
            "missing" => {
                fs::rename(&path, fixture.path().join("retained-missing.jar"))?;
            }
            "extra" => fs::write(entry.join("unexpected.jar"), b"extra")?,
            "swapped" => {
                fs::rename(&path, fixture.path().join("retained-original.jar"))?;
                fs::write(&path, b"fresh fixture production")?;
                fs::set_permissions(&path, fs::Permissions::from_mode(0o400))?;
            }
            "symlink" => {
                fs::rename(&path, fixture.path().join("retained-original.jar"))?;
                symlink(fixture.path().join("retained-original.jar"), &path)?;
            }
            "hardlink" => fs::hard_link(&path, fixture.path().join("alias"))?,
            "mode" => fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?,
            "head" => {
                let head = cache.directory.path.join("current.json");
                fs::set_permissions(&head, fs::Permissions::from_mode(0o600))?;
                fs::write(head, "\"../legacy\"")?;
            }
            "manifest" => {
                let manifest = entry.join("production.json");
                fs::set_permissions(&manifest, fs::Permissions::from_mode(0o600))?;
                fs::write(manifest, b"{}")?;
            }
            _ => unreachable!(),
        }
        assert!(cache.recheck(&cancel).is_err(), "{mutation}");
        cache.release_producer();
        if mutation != "swapped" {
            assert!(
                store(fixture.path(), &cancel)?.lookup(&cancel).is_err(),
                "{mutation}"
            );
        }
        // An equal-byte inode replacement is detected by a live reader; a new
        // trusted-local reader has only the persisted exact-byte authority.
    }
    Ok(())
}

#[test]
fn linux_forge_store_partial_failure_cancel_and_output_substitution_never_publish() -> Result<()> {
    for failure in [
        "producer",
        "cancel",
        "missing-role",
        "outside",
        "link",
        "hardlink",
        "mutation",
    ] {
        let fixture = tempfile::tempdir()?;
        let cancel = CancellationToken::new();
        let mut cache = store(fixture.path(), &cancel)?;
        let calls = std::cell::Cell::new(0);
        let paths = std::cell::RefCell::new(Vec::new());
        let result = cache.produce(
            &cancel,
            |attempt| {
                let outputs = ["specialsource.jar", "remapped.jar"].map(|n| attempt.path.join(n));
                fs::write(&outputs[0], b"fresh bytes")?;
                fs::write(&outputs[1], b"fresh bytes")?;
                *paths.borrow_mut() = outputs.to_vec();
                match failure {
                    "producer" => {
                        eyre::bail!("producer failed");
                    }
                    "cancel" => cancel.request_cancel("fixture interrupted"),
                    "missing-role" => return Ok(vec![outputs[0].clone()]),
                    "outside" => {
                        return Ok(vec![fixture.path().join("outside"), outputs[1].clone()]);
                    }
                    "link" => {
                        fs::rename(&outputs[0], attempt.path.join("retained"))?;
                        symlink(attempt.path.join("retained"), &outputs[0])?;
                    }
                    "hardlink" => fs::hard_link(&outputs[0], attempt.path.join("alias"))?,
                    _ => (),
                }
                Ok(outputs.to_vec())
            },
            || {
                calls.set(calls.get() + 1);
                if failure == "mutation" && calls.get() == 3 {
                    fs::write(&paths.borrow()[0], b"WRONG bytes")?;
                }
                Ok(())
            },
        );
        assert!(result.is_err(), "{failure}");
        assert!(!exists(&cache.directory, "current.json")?, "{failure}");
        assert!(fs::read_dir(cache.directory.access(""))?.count() > 1);
    }
    Ok(())
}

#[test]
fn linux_forge_store_rejects_replaced_ancestors_and_lock_paths() -> Result<()> {
    for replaced in ["directory", "lock"] {
        let fixture = tempfile::tempdir()?;
        let cancel = CancellationToken::new();
        let cache = store(fixture.path(), &cancel)?;
        if replaced == "directory" {
            fs::rename(&cache.directory.path, fixture.path().join("retained-key"))?;
            fs::create_dir(&cache.directory.path)?;
        } else {
            fs::rename(
                cache.directory.access("producer.lock"),
                cache.directory.access("retained.lock"),
            )?;
            fs::write(cache.directory.access("producer.lock"), b"")?;
        }
        assert!(cache.recheck(&cancel).is_err());
    }
    Ok(())
}

#[test]
fn linux_forge_publication_refuses_occupied_payload_and_preserves_it() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = Directory::open(fixture.path())?;
    let cancel = CancellationToken::new();
    let attempt = fresh_directory(&root, &format!("attempt-{}", "a".repeat(32)))?;
    let entry = fresh_directory(&root, &format!("entry-{}", "b".repeat(32)))?;
    let source = attempt.path.join("fresh.jar");
    fs::write(&source, b"fresh bytes")?;
    entry.publish("remapped.jar", b"retained existing bytes", &cancel)?;
    assert!(
        copy_output(
            &attempt,
            &entry,
            "remapped.jar",
            &source,
            TOTAL_BOUND,
            &cancel
        )
        .is_err()
    );
    assert_eq!(
        fs::read(entry.access("remapped.jar"))?,
        b"retained existing bytes"
    );
    assert!(!exists(&entry, "current.json")?);
    Ok(())
}

fn zip_source(path: &Path, name: &str) -> Result<()> {
    let mut zip = zip::ZipWriter::new(fs::File::create(path)?);
    zip.start_file(name, zip::write::SimpleFileOptions::default())?;
    zip.write_all(b"package net.minecraft; class Fixture {}")?;
    zip.finish()?;
    Ok(())
}

#[test]
fn linux_forge_sources_reuse_one_verified_tree_and_refuse_corruption_and_traversal() -> Result<()> {
    for name in ["net/minecraft/Fixture.java", "../escape.java"] {
        let fixture = tempfile::tempdir()?;
        let cancel = CancellationToken::new();
        let mut cache = Store::begin(
            fixture.path(),
            "forge",
            &key(),
            &["deobfuscated_sources.jar"],
            &cancel,
        )?;
        cache.produce(
            &cancel,
            |attempt| {
                let jar = attempt.path.join("sources.jar");
                zip_source(&jar, name)?;
                Ok(vec![jar])
            },
            || Ok(()),
        )?;
        cache.release_producer();
        if name.starts_with("..") {
            assert!(cache.materialize_sources(&cancel).is_err());
            assert!(!fixture.path().join("escape.java").exists());
            continue;
        }
        let tree = cache.materialize_sources(&cancel)?;
        assert_eq!(tree, cache.materialize_sources(&cancel)?);
        let file = tree.join(name);
        assert!(fs::read_to_string(&file)?.contains("class Fixture"));
        fs::set_permissions(&file, fs::Permissions::from_mode(0o600))?;
        fs::write(&file, b"changed")?;
        assert!(cache.materialize_sources(&cancel).is_err());
    }
    Ok(())
}

#[test]
fn linux_forge_source_validation_detects_replacement_after_fd_validation() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let cancel = CancellationToken::new();
    let mut cache = Store::begin(
        fixture.path(),
        "forge",
        &key(),
        &["deobfuscated_sources.jar"],
        &cancel,
    )?;
    cache.produce(
        &cancel,
        |attempt| {
            let jar = attempt.path.join("sources.jar");
            zip_source(&jar, "net/minecraft/Fixture.java")?;
            Ok(vec![jar])
        },
        || Ok(()),
    )?;
    cache.release_producer();
    let path = cache.materialize_sources(&cancel)?;
    let tree = cache.source_tree.lock().unwrap().clone().unwrap();
    assert!(
        sources::verify_tree(
            &tree,
            &cache.path("deobfuscated_sources.jar")?,
            &cancel,
            || {
                fs::rename(&path, fixture.path().join("retained-source-tree"))?;
                fs::create_dir(&path)?;
                Ok(())
            }
        )
        .is_err()
    );
    assert!(cache.recheck(&cancel).is_err());
    Ok(())
}

#[test]
fn linux_forge_source_tree_rejects_hardlinks_and_linked_heads() -> Result<()> {
    for mutation in ["hardlink", "head-link"] {
        let fixture = tempfile::tempdir()?;
        let cancel = CancellationToken::new();
        let mut cache = Store::begin(
            fixture.path(),
            "forge",
            &key(),
            &["deobfuscated_sources.jar"],
            &cancel,
        )?;
        cache.produce(
            &cancel,
            |attempt| {
                let jar = attempt.path.join("sources.jar");
                zip_source(&jar, "Fixture.java")?;
                Ok(vec![jar])
            },
            || Ok(()),
        )?;
        cache.release_producer();
        let tree = cache.materialize_sources(&cancel)?;
        if mutation == "hardlink" {
            fs::hard_link(tree.join("Fixture.java"), fixture.path().join("alias.java"))?;
        } else {
            let head = cache.directory.path.join("source-views/current.json");
            fs::rename(&head, fixture.path().join("retained-head"))?;
            symlink(fixture.path().join("retained-head"), &head)?;
        }
        assert!(cache.materialize_sources(&cancel).is_err(), "{mutation}");
    }
    Ok(())
}

#[test]
fn linux_forge_source_validation_compares_the_observed_second_inventory_bytes() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let cancel = CancellationToken::new();
    let mut cache = Store::begin(
        fixture.path(),
        "forge",
        &key(),
        &["deobfuscated_sources.jar"],
        &cancel,
    )?;
    cache.produce(
        &cancel,
        |attempt| {
            let jar = attempt.path.join("sources.jar");
            zip_source(&jar, "Fixture.java")?;
            Ok(vec![jar])
        },
        || Ok(()),
    )?;
    cache.release_producer();
    let path = cache.materialize_sources(&cancel)?;
    let tree = cache.source_tree.lock().unwrap().clone().unwrap();
    assert!(
        sources::verify_tree_at_boundaries(
            &tree,
            &cache.path("deobfuscated_sources.jar")?,
            &cancel,
            || Ok(()),
            || {
                let leaf = path.join("Fixture.java");
                let mut bytes = fs::read(&leaf)?;
                bytes[0] ^= 1;
                fs::set_permissions(&leaf, fs::Permissions::from_mode(0o600))?;
                fs::write(&leaf, bytes)?;
                fs::set_permissions(&leaf, fs::Permissions::from_mode(0o400))?;
                Ok(())
            }
        )
        .is_err()
    );
    Ok(())
}

struct Workers(Vec<std::process::Child>);
impl Drop for Workers {
    fn drop(&mut self) {
        for child in &mut self.0 {
            if !matches!(child.try_wait(), Ok(Some(_))) {
                let _ = child.kill();
                let _ = child.wait();
            }
        }
    }
}
#[test]
fn linux_forge_process_worker() -> Result<()> {
    let Some(root) = std::env::var_os("SFM_LINUX_FORGE_TEST_ROOT") else {
        return Ok(());
    };
    let cancel = CancellationToken::new();
    let root = Path::new(&root);
    let mut cache = store(root, &cancel)?;
    if !cache.lookup(&cancel)? {
        fs::OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(root.join("fresh-producer-once"))?;
        produce(&mut cache, &cancel)?;
    }
    cache.release_producer();
    std::thread::sleep(Duration::from_millis(50));
    cache.recheck(&cancel)
}
#[test]
fn linux_forge_multi_process_writers_and_readers_share_one_pair() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let mut workers = Workers(Vec::new());
    for _ in 0..3 {
        workers.0.push(
            std::process::Command::new(std::env::current_exe()?)
                .arg("linux_forge_process_worker")
                .arg("--nocapture")
                .env("SFM_LINUX_FORGE_TEST_ROOT", fixture.path())
                .spawn()?,
        );
    }
    let deadline = Instant::now() + Duration::from_secs(15);
    for worker in &mut workers.0 {
        loop {
            if let Some(status) = worker.try_wait()? {
                ensure!(status.success(), "cache child failed");
                break;
            }
            ensure!(Instant::now() < deadline, "bounded child wait expired");
            std::thread::sleep(Duration::from_millis(10));
        }
    }
    assert!(fixture.path().join("fresh-producer-once").is_file());
    Ok(())
}
