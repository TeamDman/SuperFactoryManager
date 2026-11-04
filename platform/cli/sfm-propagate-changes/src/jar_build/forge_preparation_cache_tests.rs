use super::*;
use crate::cancellation::CancellationToken;
use eyre::Result;
use std::sync::Arc;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering;

#[test]
fn forced_mcp_rebuild_does_not_force_forge() {
    assert!(!force_fresh_preparation(false, false, "mcp"));
    assert!(force_fresh_preparation(false, true, "mcp"));
    assert!(!force_fresh_preparation(false, true, "forge"));
    assert!(force_fresh_preparation(true, false, "forge"));
}

fn fixture_outputs(root: &Path, count: usize) -> Result<Vec<(&'static str, PathBuf)>> {
    let names = ["one.jar", "two.jar", "three.tsrg", "four.tsrg", "five.jar"];
    make_directory(root)?;
    names[..count]
        .iter()
        .enumerate()
        .map(|(index, name)| {
            let path = root.join(name);
            fs::write(&path, format!("produced-{index}"))?;
            Ok((*name, path))
        })
        .collect()
}

fn destinations(
    root: &Path,
    outputs: &[(&'static str, PathBuf)],
) -> Result<Vec<(&'static str, PathBuf)>> {
    make_directory(root)?;
    Ok(outputs
        .iter()
        .map(|(name, _)| (*name, root.join(name)))
        .collect())
}

#[test]
fn forge_preparation_store_materializes_exact_bytes_into_a_different_project_namespace()
-> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("original"), 5)?;
    let key = make_directory(&temp.path().join("store/key"))?;
    publish(&key, "fixture-key", &original, || Ok(()))?;
    let project = make_directory(&temp.path().join("different-source-project"))?;
    let outputs = destinations(&project, &original)?;
    assert!(restore(&key, "fixture-key", &project, &outputs, || Ok(()))?);
    for ((_, source), (_, output)) in original.iter().zip(outputs) {
        assert_eq!(fs::read(source)?, fs::read(output)?);
    }
    Ok(())
}

#[test]
fn mcp_shared_hit_holds_verified_inputs_without_project_copies() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("producer"), 3)?;
    let key = make_directory(&temp.path().join("store/key"))?;
    assert!(publish(&key, "fixture-key", &original, || Ok(()))?);
    let other_project = make_directory(&temp.path().join("other-project"))?;
    let held = hold_shared_preparation(
        &key,
        "fixture-key",
        "mcp",
        &["one.jar", "two.jar", "three.tsrg"],
        &|| Ok(()),
    )?
    .expect("complete verified MCP entry");
    for (name, original_path) in &original {
        let shared_path = held.path("mcp", name).expect("held role");
        assert_eq!(fs::read(original_path)?, fs::read(shared_path)?);
        assert!(!other_project.join(name).exists());
    }
    assert!(held.path("forge", "one.jar").is_none());
    assert!(hold_shared_preparation(
        &key,
        "wrong-key",
        "mcp",
        &["one.jar", "two.jar", "three.tsrg"],
        &|| Ok(()),
    )?
    .is_none());
    Ok(())
}

#[test]
fn forge_shared_hit_holds_mappings_and_dev_jar_without_project_copies() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let producer = make_directory(&temp.path().join("producer"))?;
    let names = [
        "official_sources.jar",
        "deobfuscated_sources.jar",
        "srg_to_official.tsrg",
        "official_to_srg.tsrg",
        "dev_compile.jar",
    ];
    let original = names
        .iter()
        .map(|name| {
            let path = producer.join(name);
            fs::write(&path, name.as_bytes())?;
            Ok((*name, path))
        })
        .collect::<Result<Vec<_>>>()?;
    let key = make_directory(&temp.path().join("store/key"))?;
    assert!(publish(&key, "fixture-key", &original, || Ok(()))?);
    let project = make_directory(&temp.path().join("other-project"))?;
    let mappings = hold_shared_preparation(
        &key,
        "fixture-key",
        "forge",
        &["srg_to_official.tsrg", "official_to_srg.tsrg"],
        &|| Ok(()),
    )?
    .expect("verified shared mappings");
    let dev = restore_held_dev_jar(&key, "fixture-key", &project, &[], &|| Ok(()))?
        .expect("verified shared dev JAR");
    assert_eq!(fs::read(dev.path())?, b"dev_compile.jar");
    for name in ["srg_to_official.tsrg", "official_to_srg.tsrg"] {
        assert_eq!(fs::read(mappings.path("forge", name).expect("mapping role"))?, name.as_bytes());
        assert!(!project.join(name).exists());
    }
    assert!(!project.join("dev_compile.jar").exists());
    Ok(())
}

#[test]
fn ordinary_forge_hit_does_not_copy_unneeded_source_archives() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let source = make_directory(&temp.path().join("producer"))?;
    let names = [
        "official_sources.jar",
        "deobfuscated_sources.jar",
        "srg_to_official.tsrg",
        "official_to_srg.tsrg",
        "dev_compile.jar",
    ];
    let original = names
        .iter()
        .map(|name| {
            let path = source.join(name);
            fs::write(&path, name.as_bytes())?;
            Ok((*name, path))
        })
        .collect::<Result<Vec<_>>>()?;
    let key = make_directory(&temp.path().join("store/key"))?;
    assert!(publish(&key, "fixture-key", &original, || Ok(()))?);
    let project = make_directory(&temp.path().join("other-projection"))?;
    let all_destinations = destinations(&project, &original)?;
    let compile_destinations = selected_restore_outputs("forge", false, &all_destinations);
    assert_eq!(compile_destinations.len(), 3);
    assert!(restore(
        &key,
        "fixture-key",
        &project,
        &compile_destinations,
        || Ok(())
    )?);
    assert!(compile_destinations.iter().all(|(_, path)| path.is_file()));
    assert!(all_destinations[..2].iter().all(|(_, path)| !path.exists()));
    let source_destinations = selected_restore_outputs("forge", true, &all_destinations);
    assert_eq!(source_destinations.len(), 5);
    assert!(restore(
        &key,
        "fixture-key",
        &project,
        &source_destinations,
        || Ok(())
    )?);
    assert!(source_destinations.iter().all(|(_, path)| path.is_file()));
    Ok(())
}

#[test]
fn ordinary_forge_hit_holds_dev_jar_without_a_project_copy() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let producer = make_directory(&temp.path().join("producer"))?;
    let names = [
        "official_sources.jar",
        "deobfuscated_sources.jar",
        "srg_to_official.tsrg",
        "official_to_srg.tsrg",
        "dev_compile.jar",
    ];
    let original = names
        .iter()
        .map(|name| {
            let path = producer.join(name);
            fs::write(&path, name.as_bytes())?;
            Ok((*name, path))
        })
        .collect::<Result<Vec<_>>>()?;
    let key = make_directory(&temp.path().join("store/key"))?;
    assert!(publish(&key, "fixture-key", &original, || Ok(()))?);
    let project = make_directory(&temp.path().join("other-projection"))?;
    let destinations = destinations(&project, &original)?;
    let mappings = selected_restore_outputs("forge", false, &destinations)
        .into_iter()
        .filter(|(name, _)| *name != "dev_compile.jar")
        .collect::<Vec<_>>();
    let held = restore_held_dev_jar(&key, "fixture-key", &project, &mappings, &|| Ok(()))?
        .expect("completed Forge entry should be held");
    assert!(mappings.iter().all(|(_, path)| path.is_file()));
    assert!(!destinations[4].1.exists());
    assert!(!held.path().to_string_lossy().starts_with(r"\\?\"));
    assert_eq!(fs::read(held.path())?, b"dev_compile.jar");
    assert!(fs::write(held.path(), b"mutated").is_err());
    let runtime_project = make_directory(&temp.path().join("runtime-projection"))?;
    let runtime_destinations = self::destinations(&runtime_project, &original)?;
    let runtime_mappings = selected_restore_outputs("forge", false, &runtime_destinations)
        .into_iter()
        .filter(|(name, _)| *name != "dev_compile.jar")
        .collect::<Vec<_>>();
    let runtime_hold = restore_held_dev_jar(
        &key,
        "fixture-key",
        &runtime_project,
        &runtime_mappings,
        &|| Ok(()),
    )?
    .expect("runtime must reacquire the complete Forge entry without a dev-JAR copy");
    assert!(runtime_mappings.iter().all(|(_, path)| path.is_file()));
    assert!(!runtime_destinations[4].1.exists());
    assert!(fs::write(runtime_hold.path(), b"mutated").is_err());
    drop(runtime_hold);
    let stored = held.path().to_path_buf();
    drop(held);
    fs::write(&stored, b"mutated")?;
    assert!(restore_held_dev_jar(&key, "fixture-key", &project, &mappings, &|| Ok(()))?.is_none());
    Ok(())
}

#[test]
fn forge_preparation_store_rejects_corruption_of_every_required_output() -> Result<()> {
    for selected in 0..5 {
        let temp = tempfile::tempdir()?;
        let original = fixture_outputs(&temp.path().join("original"), 5)?;
        let key = make_directory(&temp.path().join("store/key"))?;
        publish(&key, "fixture-key", &original, || Ok(()))?;
        let entry = entry_directories(&key)?.unwrap().pop().unwrap();
        let damaged = entry.join("payload").join(original[selected].0);
        fs::write(&damaged, b"edited")?;
        let project = make_directory(&temp.path().join("new-project"))?;
        let outputs = destinations(&project, &original)?;
        assert!(!restore(
            &key,
            "fixture-key",
            &project,
            &outputs,
            || Ok(())
        )?);
        assert!(outputs.iter().all(|(_, path)| !path.exists()));
        assert_eq!(fs::read(damaged)?, b"edited");
    }
    Ok(())
}

#[test]
fn forge_preparation_store_missing_each_required_output_and_wrong_key_are_misses() -> Result<()> {
    for count in [3, 5] {
        for selected in 0..count {
            let temp = tempfile::tempdir()?;
            let original = fixture_outputs(&temp.path().join("original"), count)?;
            let key = make_directory(&temp.path().join("store/key"))?;
            publish(&key, "fixture-key", &original, || Ok(()))?;
            let project = make_directory(&temp.path().join("new-project"))?;
            let outputs = destinations(&project, &original)?;
            assert!(!restore(&key, "different-key", &project, &outputs, || Ok(
                ()
            ))?);
            let entry = entry_directories(&key)?.unwrap().pop().unwrap();
            fs::remove_file(entry.join("payload").join(original[selected].0))?;
            assert!(!restore(
                &key,
                "fixture-key",
                &project,
                &outputs,
                || Ok(())
            )?);
            assert!(outputs.iter().all(|(_, path)| !path.exists()));
        }
    }
    Ok(())
}

#[test]
fn forge_preparation_store_does_not_publish_an_interrupted_export_or_replace_prior_evidence()
-> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("original"), 3)?;
    let key = make_directory(&temp.path().join("store/key"))?;
    publish(&key, "old-key", &original, || Ok(()))?;
    let old = entry_directories(&key)?.unwrap().pop().unwrap();
    let before = fs::read(old.join("inputs.sha256"))?;
    let check = || -> Result<()> {
        for entry in entry_directories(&key)?.unwrap() {
            if entry != old && entry.join("payload").join(original[0].0).exists() {
                eyre::bail!("fixture cancelled after first copied file");
            }
        }
        Ok(())
    };
    assert!(publish(&key, "new-key", &original, check).is_err());
    assert_eq!(fs::read(old.join("inputs.sha256"))?, before);
    let entries = entry_directories(&key)?.unwrap();
    assert_eq!(entries, vec![old.clone()]);
    assert!(old.join("inputs.sha256").exists());
    let project = make_directory(&temp.path().join("new-project"))?;
    let outputs = destinations(&project, &original)?;
    assert!(!restore(&key, "new-key", &project, &outputs, || Ok(()))?);
    assert!(restore(&key, "old-key", &project, &outputs, || Ok(()))?);
    Ok(())
}

#[test]
fn forge_preparation_store_cancelled_check_is_an_error_before_any_entry_write() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("original"), 3)?;
    let key = make_directory(&temp.path().join("store/key"))?;
    assert!(publish(&key, "key", &original, || eyre::bail!("cancelled")).is_err());
    assert!(entry_directories(&key)?.unwrap().is_empty());
    Ok(())
}

#[test]
fn forge_preparation_output_guards_deny_writes_and_ancestor_replacement_until_drop() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let root = temp.path().join("source");
    let original = fixture_outputs(&root, 3)?;
    let held = HeldForgePreparationOutputs::open_paths(&original, &|| Ok(()))?.unwrap();
    assert!(fs::write(&original[0].1, b"edited").is_err());
    assert!(fs::rename(&root, temp.path().join("moved")).is_err());
    drop(held);
    fs::write(&original[0].1, b"edited")?;
    fs::rename(&root, temp.path().join("moved"))?;
    Ok(())
}

#[test]
fn forge_preparation_output_guards_refuse_hardlink_and_traversal() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("source"), 1)?;
    fs::hard_link(&original[0].1, temp.path().join("alias.jar"))?;
    assert!(HeldForgePreparationOutputs::open_paths(&original, &|| Ok(())).is_err());
    assert!(make_directory(&temp.path().join("source/../escaped")).is_err());
    Ok(())
}

#[test]
fn forge_preparation_store_uses_direct_byte_equality_for_materialized_outputs() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let left = fixture_outputs(&temp.path().join("left"), 1)?;
    let right = fixture_outputs(&temp.path().join("right"), 1)?;
    fs::write(&right[0].1, b"different0")?;
    let left = HeldForgePreparationOutputs::open_paths(&left, &|| Ok(()))?.unwrap();
    let right = HeldForgePreparationOutputs::open_paths(&right, &|| Ok(()))?.unwrap();
    assert!(!left.content_matches(&right, &|| Ok(()))?);
    Ok(())
}

#[test]
fn forge_preparation_store_bounds_history_without_erasing_or_refusing_original_outputs()
-> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("source"), 1)?;
    let key = make_directory(&temp.path().join("store/key"))?;
    for index in 0..MAX_COMPLETED_ENTRIES {
        make_directory(&key.join(format!("entry-{index}")))?;
        fs::write(
            key.join(format!("entry-{index}/inputs.sha256")),
            b"retained-history",
        )?;
    }
    publish(&key, "fixture-key", &original, || Ok(()))?;
    assert_eq!(
        entry_directories(&key)?.unwrap().len(),
        MAX_COMPLETED_ENTRIES
    );
    assert_eq!(fs::read(&original[0].1)?, b"produced-0");
    Ok(())
}

#[test]
fn forge_preparation_store_partial_history_does_not_exhaust_completed_allowance() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("source"), 1)?;
    let key = make_directory(&temp.path().join("store/key"))?;
    for index in 0..MAX_COMPLETED_ENTRIES {
        make_directory(&key.join(format!("entry-orphan-{index}")))?;
    }
    let committed = publish_entry(&key, "fixture-key", &original, || Ok(()))?;
    assert!(committed.is_some());
    assert_eq!(
        entry_directories(&key)?.unwrap().len(),
        MAX_COMPLETED_ENTRIES + 1
    );
    assert_eq!(fs::read(&original[0].1)?, b"produced-0");
    Ok(())
}

#[test]
fn forge_preparation_store_bounds_unfinished_history_without_erasing_it() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = fixture_outputs(&temp.path().join("source"), 1)?;
    let key = make_directory(&temp.path().join("store/key"))?;
    for index in 0..MAX_ENTRY_DIRECTORIES {
        make_directory(&key.join(format!("entry-orphan-{index}")))?;
    }
    assert!(publish_entry(&key, "fixture-key", &original, || Ok(()))?.is_none());
    assert_eq!(
        entry_directories(&key)?.unwrap().len(),
        MAX_ENTRY_DIRECTORIES
    );
    assert_eq!(fs::read(&original[0].1)?, b"produced-0");
    Ok(())
}

#[test]
fn forge_preparation_store_explicit_root_isolates_outputs_from_default_and_other_roots() {
    let project = Path::new(r"C:\fixture\project");
    let a = Path::new(r"C:\fixture\cache-a");
    let b = Path::new(r"C:\fixture\cache-b");
    assert_eq!(
        preparation_root(project, None),
        project.join("build/sfm-toolchain")
    );
    assert_eq!(preparation_root(project, Some(a)), a);
    assert_ne!(
        preparation_root(project, Some(a)),
        preparation_root(project, Some(b))
    );
    assert_ne!(
        preparation_root(project, None),
        preparation_root(project, Some(a))
    );
}

#[test]
fn forge_preparation_same_key_waiter_rechecks_and_runs_one_synthetic_producer() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let original = Arc::new(fixture_outputs(&temp.path().join("source"), 3)?);
    let base = make_directory(&temp.path().join("store"))?;
    let key = make_directory(&base.join("key"))?;
    let count = Arc::new(AtomicUsize::new(0));
    let mut workers = Vec::new();
    for index in 0..2 {
        let key = key.clone();
        let lock = base.join("key.lock");
        let project = make_directory(&temp.path().join(format!("project-{index}")))?;
        let original = Arc::clone(&original);
        let count = Arc::clone(&count);
        workers.push(std::thread::spawn(move || -> Result<()> {
            let _lock =
                ArtifactLock::acquire_with_cancellation(lock, "fixture", CancellationToken::new())?;
            let outputs = destinations(&project, &original)?;
            if !restore(&key, "fixture-key", &project, &outputs, || Ok(()))? {
                count.fetch_add(1, Ordering::SeqCst);
                publish(&key, "fixture-key", &original, || Ok(()))?;
                assert!(restore(&key, "fixture-key", &project, &outputs, || Ok(()))?);
            }
            Ok(())
        }));
    }
    for worker in workers {
        worker.join().unwrap()?;
    }
    assert_eq!(count.load(Ordering::SeqCst), 1);
    Ok(())
}

#[test]
fn forge_preparation_at_transport_holds_actual_java_input_against_write_and_rename() -> Result<()> {
    let held = HeldForgeAtTransport::capture(b"public fixture.Owner field\n")?.unwrap();
    let path = held.path().to_path_buf();
    assert_eq!(fs::read(&path)?, b"public fixture.Owner field\n");
    assert!(fs::write(&path, b"changed").is_err());
    assert!(fs::rename(&path, path.with_extension("moved")).is_err());
    drop(held);
    assert!(!path.exists());
    Ok(())
}

#[test]
fn forge_preparation_store_directory_admission_holds_created_ancestors_until_drop() -> Result<()> {
    use std::os::windows::fs::OpenOptionsExt as _;
    use windows::Win32::Storage::FileSystem::FILE_FLAG_BACKUP_SEMANTICS;
    use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;
    use windows::Win32::Storage::FileSystem::FILE_WRITE_DATA;
    let temp = tempfile::tempdir()?;
    let requested = temp.path().join("new/base/key");
    let (key, held) = make_held_directory(&requested)?.unwrap();
    fs::write(key.join("new-child"), b"children remain creatable")?;
    assert!(fs::rename(&key, key.with_file_name("moved")).is_err());
    let parent = key.parent().unwrap();
    assert!(fs::rename(parent, parent.with_file_name("moved-base")).is_err());
    assert!(
        fs::OpenOptions::new()
            .access_mode(FILE_WRITE_DATA.0)
            .share_mode(7)
            .custom_flags((FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT).0)
            .open(&key)
            .is_err()
    );
    drop(held);
    fs::rename(&key, key.with_file_name("moved"))?;
    Ok(())
}
