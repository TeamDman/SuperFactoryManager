#![cfg(windows)]

use super::*;
use crate::artifact_lock::ArtifactLockWaitPolicy;
use std::sync::mpsc;
use std::time::Duration;

fn fixture_cache() -> Result<(tempfile::TempDir, LocalDependencyCache)> {
    let fixture = tempfile::tempdir()?;
    let cache = LocalDependencyCache::begin(
        &fixture.path().join("dependency-cache"),
        super::super::local_dependency_key::fixture_key(),
        &CancellationToken::new(),
    )?;
    Ok((fixture, cache))
}

fn seed_entry(cache: &LocalDependencyCache, name: &str) -> Result<(PathBuf, Vec<u8>)> {
    let entry = cache.directory.join(name);
    fs::create_dir(&entry)?;
    let manifest = super::super::local_dependency_manifest::fixture_manifest(
        &cache.key, b"classes", b"sources",
    );
    let raw = facet_json::to_string(&manifest)?.into_bytes();
    write_new(&entry.join("classes.jar"), b"classes")?;
    write_new(&entry.join("sources.jar"), b"sources")?;
    write_new(&entry.join("manifest.json"), &raw)?;
    write_new(
        &cache.directory.join("current.json"),
        facet_json::to_string(&name.to_owned())?.as_bytes(),
    )?;
    Ok((entry, raw))
}

#[test]
fn canonical_dependency_entry_retains_leaf_and_ancestor_protection() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let (entry, raw_manifest) = seed_entry(&cache, "entry-complete")?;
    let (manifest, selected, files, directories) = cache
        .read_entry(&CancellationToken::new())?
        .ok_or_else(|| eyre::eyre!("Valid complete dependency entry was not selected"))?;
    assert_eq!(selected, entry);
    assert_eq!(manifest.key, cache.key.digest());
    assert_eq!(fs::read(entry.join("manifest.json"))?, raw_manifest);
    assert!(
        OpenOptions::new()
            .write(true)
            .open(entry.join("classes.jar"))
            .is_err()
    );
    assert!(fs::rename(&entry, cache.directory.join("entry-moved")).is_err());
    assert_eq!(fs::read(entry.join("classes.jar"))?, b"classes");
    drop(files);
    drop(directories);
    assert!(
        OpenOptions::new()
            .write(true)
            .open(entry.join("classes.jar"))
            .is_ok()
    );
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn corrupt_dependency_classes_are_rejected_without_destroying_evidence() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let (entry, raw_manifest) = seed_entry(&cache, "entry-corrupt")?;
    let pointer = fs::read(cache.directory.join("current.json"))?;
    // Same length ensures a size-only cache would incorrectly accept this.
    fs::write(entry.join("classes.jar"), b"clazzez")?;
    assert!(cache.read_entry(&CancellationToken::new()).is_err());
    assert_eq!(fs::read(entry.join("classes.jar"))?, b"clazzez");
    assert_eq!(fs::read(entry.join("sources.jar"))?, b"sources");
    assert_eq!(fs::read(entry.join("manifest.json"))?, raw_manifest);
    assert_eq!(fs::read(cache.directory.join("current.json"))?, pointer);
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn malformed_dependency_manifest_is_rejected_and_preserved() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let (entry, _) = seed_entry(&cache, "entry-malformed")?;
    let malformed = b"{\"schema\":\"sfm:unadmitted_manifest@1\"}";
    fs::write(entry.join("manifest.json"), malformed)?;
    assert!(cache.read_entry(&CancellationToken::new()).is_err());
    assert_eq!(fs::read(entry.join("manifest.json"))?, malformed);
    assert_eq!(fs::read(entry.join("classes.jar"))?, b"classes");
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn dependency_pointer_cannot_select_traversal_or_absolute_paths() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let sentinel = fixture.path().join("outside-evidence.bin");
    fs::write(&sentinel, b"preserved outside cache")?;
    for name in [
        "../outside-evidence.bin",
        "entry-../escape",
        "entry-good/child",
        "entry-good\\child",
        "C:\\outside",
        "entry-",
        "entry-..",
        "entry-foo:bar",
    ] {
        assert!(!valid_entry(name));
        fs::write(
            cache.directory.join("current.json"),
            facet_json::to_string(&name.to_owned())?,
        )?;
        assert!(cache.read_entry(&CancellationToken::new()).is_err());
        assert_eq!(fs::read(&sentinel)?, b"preserved outside cache");
    }
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn orphan_partial_dependency_entry_does_not_become_a_hit() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let partial = cache.directory.join("entry-orphan");
    fs::create_dir(&partial)?;
    fs::write(partial.join("classes.jar"), b"unfinished output")?;
    assert!(cache.read_entry(&CancellationToken::new())?.is_none());
    assert_eq!(fs::read(partial.join("classes.jar"))?, b"unfinished output");
    // Even an explicit pointer cannot turn the missing committed receipt into a hit.
    fs::write(cache.directory.join("current.json"), "\"entry-orphan\"")?;
    assert!(cache.read_entry(&CancellationToken::new()).is_err());
    assert_eq!(fs::read(partial.join("classes.jar"))?, b"unfinished output");
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn dependency_producer_lock_is_per_key_and_releases_before_consumption_lifetime() -> Result<()> {
    let (fixture, mut first) = fixture_cache()?;
    let producer_path = first.directory.join("producer.lock");
    assert!(ArtifactLock::try_acquire(&producer_path, "same-key probe")?.is_none());
    let mut other_key = first.key.clone();
    other_key.descriptor_json = other_key.descriptor_json.replace(
        &crate::source_projection::provenance::sha256(b"host-implementation"),
        &crate::source_projection::provenance::sha256(b"other-host-implementation"),
    );
    assert_ne!(other_key.digest(), first.key.digest());
    let other = LocalDependencyCache::begin(
        first.directory.parent().unwrap(),
        other_key,
        &CancellationToken::new(),
    )?;
    assert_ne!(other.directory, first.directory);
    first.release_producer();
    let acquired = ArtifactLock::try_acquire(&producer_path, "same-key after release")?;
    assert!(acquired.is_some());
    drop(acquired);
    drop(other);
    drop(first);
    drop(fixture);
    Ok(())
}

#[test]
fn dependency_producer_wait_is_cancellable_with_a_bounded_lock_policy() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let producer_path = cache.directory.join("producer.lock");
    let cancel = CancellationToken::new();
    let worker_cancel = cancel.clone();
    let policy = ArtifactLockWaitPolicy::new(Duration::from_millis(10), Duration::from_secs(1))
        .with_max_wait(Duration::from_secs(2))
        .with_cancellation(worker_cancel);
    let (entered, receiving) = mpsc::sync_channel(1);
    let worker = std::thread::spawn(move || {
        entered.send(()).unwrap();
        ArtifactLock::acquire_with_policy(&producer_path, "same-key cancelled waiter", &policy)
            .map(drop)
    });
    receiving.recv_timeout(Duration::from_secs(1))?;
    // The production lock is demonstrably still held; cancellation cannot cancel its owner.
    assert!(
        ArtifactLock::try_acquire(cache.directory.join("producer.lock"), "owner probe")?.is_none()
    );
    cancel.request_cancel("dependency-cache fixture cancellation");
    let outcome = worker
        .join()
        .map_err(|_| eyre::eyre!("Lock waiter panicked"))?;
    assert!(
        outcome
            .unwrap_err()
            .to_string()
            .contains("dependency-cache fixture cancellation")
    );
    assert!(cache.lock.is_some());
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn cancelled_dependency_admission_creates_no_cache_root() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = fixture.path().join("never-created-cache");
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("cancelled before cache admission");
    assert!(
        LocalDependencyCache::begin(
            &root,
            super::super::local_dependency_key::fixture_key(),
            &cancellation,
        )
        .is_err()
    );
    assert!(!root.exists());
    Ok(())
}

#[test]
fn dependency_pointer_publication_holds_producer_lock_and_retains_previous_raw_evidence()
-> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let (old, _) = seed_entry(&cache, "entry-previous")?;
    let next = cache.directory.join("entry-next");
    fs::create_dir(&next)?;
    let target = cache.directory.join("current.json");
    let previous = fs::read(&target)?;
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("cancel before cache pointer publication");
    assert!(cache.commit_pointer(&next, &cancellation).is_err());
    assert_eq!(fs::read(&target)?, previous);
    assert!(old.is_dir());
    let malformed = b"not a valid pointer: \xff";
    fs::write(&target, malformed)?;
    cache.commit_pointer(&next, &CancellationToken::new())?;
    assert_eq!(fs::read(&target)?, b"\"entry-next\"");
    let mut preserved = false;
    for file in fs::read_dir(&cache.directory)? {
        let file = file?;
        if file
            .file_name()
            .to_string_lossy()
            .starts_with("previous-head-")
        {
            preserved |= fs::read(file.path())? == malformed;
        }
    }
    assert!(
        preserved,
        "Replacement must retain even malformed raw index evidence"
    );
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn dependency_pointer_refuses_existing_hardlink_and_preserves_its_target() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = fixture.path().join("dependency-cache");
    let key = super::super::local_dependency_key::fixture_key();
    let directory = root.join(key.digest());
    fs::create_dir_all(&directory)?;
    let evidence = fixture.path().join("independent-evidence.bin");
    fs::write(&evidence, b"outside evidence")?;
    let pointer = directory.join("current.json");
    // Install the hostile pre-existing alias before the strong ancestry
    // lease. Creating a new hardlink under that lease is itself forbidden.
    fs::hard_link(&evidence, &pointer)?;
    let cache = LocalDependencyCache::begin(&root, key, &CancellationToken::new())?;
    let entry = cache.directory.join("entry-complete");
    fs::create_dir(&entry)?;
    assert!(cache.read_entry(&CancellationToken::new()).is_err());
    assert!(
        cache
            .commit_pointer(&entry, &CancellationToken::new())
            .is_err()
    );
    assert_eq!(fs::read(&evidence)?, b"outside evidence");
    assert_eq!(fs::read(&pointer)?, b"outside evidence");
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn copying_unsealed_or_cancelled_dependency_output_never_publishes_it() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let source = fixture.path().join("fresh-classes.jar");
    fs::write(&source, b"classes")?;
    let entry = cache.directory.join("entry-incomplete");
    fs::create_dir(&entry)?;
    let copied = entry.join("classes.jar");
    assert!(
        copy_verified(
            &source,
            &copied,
            7,
            &crate::source_projection::provenance::sha256(b"foreign"),
            &CancellationToken::new()
        )
        .is_err()
    );
    assert_eq!(fs::read(&copied)?, b"classes");
    assert!(cache.read_entry(&CancellationToken::new())?.is_none());
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("cancel dependency copy");
    assert!(
        copy_verified(
            &source,
            &entry.join("cancelled.jar"),
            7,
            &crate::source_projection::provenance::sha256(b"classes"),
            &cancellation
        )
        .is_err()
    );
    assert!(cache.read_entry(&CancellationToken::new())?.is_none());
    assert_eq!(fs::read(&source)?, b"classes");
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn same_key_waiter_rechecks_committed_pair_before_producing() -> Result<()> {
    use std::sync::Arc;
    use std::sync::atomic::AtomicUsize;
    use std::sync::atomic::Ordering;
    let (fixture, mut owner) = fixture_cache()?;
    let root = owner.directory.parent().unwrap().to_owned();
    let key = owner.key.clone();
    let produced = Arc::new(AtomicUsize::new(0));
    let waiter_count = Arc::clone(&produced);
    let (entered, receiving) = mpsc::sync_channel(1);
    let (finished, completion) = mpsc::sync_channel(1);
    let cancellation = CancellationToken::new();
    let waiter_cancel = cancellation.clone();
    let waiter = std::thread::spawn(move || -> Result<()> {
        entered.send(())?;
        let mut cache = LocalDependencyCache::begin(&root, key, &waiter_cancel)?;
        if cache.read_entry(&CancellationToken::new())?.is_none() {
            waiter_count.fetch_add(1, Ordering::SeqCst);
            seed_entry(&cache, "entry-duplicate")?;
        }
        cache.release_producer();
        finished.send(())?;
        Ok(())
    });
    receiving.recv_timeout(Duration::from_secs(1))?;
    assert!(owner.read_entry(&CancellationToken::new())?.is_none());
    produced.fetch_add(1, Ordering::SeqCst);
    seed_entry(&owner, "entry-single-production")?;
    owner.release_producer();
    let completed = completion.recv_timeout(Duration::from_secs(3)).is_ok();
    if !completed {
        cancellation.request_cancel("bounded singleflight fixture timeout");
    }
    waiter
        .join()
        .map_err(|_| eyre::eyre!("Same-key waiter panicked"))??;
    assert!(
        completed,
        "Same-key waiter did not observe the committed entry within its bound"
    );
    assert_eq!(produced.load(Ordering::SeqCst), 1);
    assert!(owner.read_entry(&CancellationToken::new())?.is_some());
    drop(owner);
    drop(fixture);
    Ok(())
}

#[test]
fn inherited_jvm_control_presence_declines_cache_without_poisoning() -> Result<()> {
    let cases = [
        (None, "absent"),
        (Some(("_JAVA_OPTIONS", "")), "present"),
        (Some(("_JAVA_OPTIONS", "fixture-options")), "present"),
        (Some(("JDK_ALTERNATE_VM", "")), "present"),
        (Some(("JDK_ALTERNATE_VM", "fixture-vm")), "present"),
    ];
    for (setting, expected) in cases {
        let mut child = std::process::Command::new(std::env::current_exe()?);
        let name = concat!(module_path!(), "::inherited_jvm_control_child");
        let name = name.split_once("::").map_or(name, |(_, name)| name);
        child
            .args(["--exact", name, "--nocapture"])
            .env_remove("_JAVA_OPTIONS")
            .env_remove("JDK_ALTERNATE_VM")
            .env("SFM_LOCAL_CACHE_ENV_FIXTURE", expected);
        if let Some((name, value)) = setting {
            child.env(name, value);
        }
        let output = child.output()?;
        ensure!(
            output.status.success(),
            "Isolated cache environment policy fixture failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        ensure!(
            String::from_utf8_lossy(&output.stdout).contains("SFM_CACHE_ENV_CASE_VERIFIED"),
            "Isolated environment fixture did not actually execute its exact child test"
        );
    }
    Ok(())
}

#[test]
fn inherited_jvm_control_child() -> Result<()> {
    let Some(expected) = std::env::var_os("SFM_LOCAL_CACHE_ENV_FIXTURE") else {
        return Ok(());
    };
    let allowed = expected == "absent";
    assert_eq!(environment_allows_reuse(), allowed);
    let fixture = tempfile::tempdir()?;
    let root = fixture.path().join("cache-admission");
    let sentinel = fixture.path().join("retained-history.bin");
    fs::write(&sentinel, b"retained producer evidence")?;
    let admitted = LocalDependencyCache::begin(
        &root,
        super::super::local_dependency_key::fixture_key(),
        &CancellationToken::new(),
    );
    assert_eq!(admitted.is_ok(), allowed);
    if !allowed {
        assert!(
            !root.exists(),
            "Unbound inherited VM settings must not create cache state"
        );
    }
    assert_eq!(fs::read(sentinel)?, b"retained producer evidence");
    drop(admitted);
    println!("SFM_CACHE_ENV_CASE_VERIFIED");
    Ok(())
}

#[test]
fn dependency_entry_junction_cannot_supply_or_receive_cache_bytes() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let outside = tempfile::tempdir()?;
    let sentinel = outside.path().join("classes.jar");
    fs::write(&sentinel, b"outside evidence")?;
    let link = cache.directory.join("entry-junction");
    let created = std::process::Command::new("cmd.exe")
        .args(["/d", "/c", "mklink", "/J"])
        .arg(&link)
        .arg(outside.path())
        .output()?;
    ensure!(
        created.status.success(),
        "Disposable junction fixture setup failed: {}",
        String::from_utf8_lossy(&created.stderr)
    );
    write_new(&cache.directory.join("current.json"), b"\"entry-junction\"")?;
    let read = cache.read_entry(&CancellationToken::new());
    let publish = cache.commit_pointer(&link, &CancellationToken::new());
    // Remove this exact fixture junction only; never recurse into its target.
    ensure!(
        link.parent() == Some(cache.directory.as_path()) && link.starts_with(fixture.path()),
        "Fixture junction escaped its selected temporary owner"
    );
    fs::remove_dir(&link)?;
    assert!(read.is_err());
    assert!(publish.is_err());
    assert_eq!(fs::read(&sentinel)?, b"outside evidence");
    assert_eq!(fs::read_dir(outside.path())?.count(), 1);
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn dependency_pointer_torn_head_recovery_keeps_immutable_entries() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let (entry, _) = seed_entry(&cache, "entry-complete")?;
    let target = cache.directory.join("current.json");
    // Model a process dying between truncation and completion under its lock.
    fs::write(&target, b"\"entry-")?;
    assert!(cache.read_entry(&CancellationToken::new()).is_err());
    cache.commit_pointer(&entry, &CancellationToken::new())?;
    assert!(cache.read_entry(&CancellationToken::new())?.is_some());
    assert_eq!(fs::read(entry.join("classes.jar"))?, b"classes");
    assert!(
        fs::read_dir(&cache.directory)?
            .filter_map(std::result::Result::ok)
            .any(|file| file
                .file_name()
                .to_string_lossy()
                .starts_with("previous-head-")
                && fs::read(file.path()).is_ok_and(|raw| raw == b"\"entry-"))
    );
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn dependency_pointer_initial_publication_succeeds_with_directory_leases_held() -> Result<()> {
    let (fixture, cache) = fixture_cache()?;
    let entry = cache.directory.join("entry-new");
    fs::create_dir(&entry)?;
    cache.commit_pointer(&entry, &CancellationToken::new())?;
    assert_eq!(
        fs::read(cache.directory.join("current.json"))?,
        b"\"entry-new\""
    );
    assert!(entry.is_dir());
    drop(cache);
    drop(fixture);
    Ok(())
}

#[test]
fn dependency_pointer_publication_after_producer_release_is_rejected_before_effects() -> Result<()>
{
    let (fixture, mut cache) = fixture_cache()?;
    let entry = cache.directory.join("entry-new");
    fs::create_dir(&entry)?;
    cache.release_producer();
    assert!(
        cache
            .commit_pointer(&entry, &CancellationToken::new())
            .is_err()
    );
    assert!(!cache.directory.join("current.json").exists());
    assert!(
        !fs::read_dir(&cache.directory)?
            .filter_map(std::result::Result::ok)
            .any(|file| file
                .file_name()
                .to_string_lossy()
                .starts_with("previous-head-"))
    );
    drop(cache);
    drop(fixture);
    Ok(())
}
