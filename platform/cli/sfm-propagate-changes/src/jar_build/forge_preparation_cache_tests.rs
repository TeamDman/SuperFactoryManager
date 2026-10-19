use super::*;
use crate::cancellation::CancellationToken;
use eyre::Result;
use std::sync::Arc;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering;

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
    assert_eq!(entries.len(), 2);
    assert_eq!(
        entries
            .iter()
            .filter(|entry| entry.join("inputs.sha256").exists())
            .count(),
        1
    );
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
    for index in 0..MAX_ENTRIES {
        make_directory(&key.join(format!("entry-{index}")))?;
    }
    publish(&key, "fixture-key", &original, || Ok(()))?;
    assert_eq!(entry_directories(&key)?.unwrap().len(), MAX_ENTRIES);
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
    use windows::Win32::Storage::FileSystem::{
        FILE_FLAG_BACKUP_SEMANTICS, FILE_FLAG_OPEN_REPARSE_POINT, FILE_WRITE_DATA,
    };
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
