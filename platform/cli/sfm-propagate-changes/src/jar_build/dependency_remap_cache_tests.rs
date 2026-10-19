//! Synthetic Windows pair-store fixtures; no Java, downloads, or SDK products.
#![cfg(windows)]

use super::*;
use crate::cancellation::CancellationToken;
use crate::file_identity::file_identity;
use eyre::Result;

const FINGERPRINT: &str = "dependency-remap-pair-fixture";
const PHASE: &str = "dependency_remap.fixture_materialize";

fn pair_paths(root: &Path) -> Vec<(&'static str, PathBuf)> {
    ["specialsource.jar", "remapped.jar"]
        .into_iter()
        .map(|name| (name, root.join(name)))
        .collect()
}

fn produced_pair(root: &Path) -> Result<Vec<(&'static str, PathBuf)>> {
    make_directory(root)?;
    let outputs = pair_paths(root);
    for (index, (_, path)) in outputs.iter().enumerate() {
        fs::write(path, format!("synthetic-producer-output-{index}"))?;
    }
    Ok(outputs)
}

fn publish_pair(key: &Path, outputs: &[(&str, PathBuf)]) -> Result<PathBuf> {
    publish_entry(key, FINGERPRINT, outputs, || Ok(()))?
        .ok_or_else(|| eyre::eyre!("Synthetic pair publication declined"))
}

fn receipts(entry: &Path) -> Result<[Vec<u8>; 2]> {
    Ok([
        fs::read(entry.join("inputs.sha256"))?,
        fs::read(entry.join("inputs.outputs.json"))?,
    ])
}

fn assert_pair_bytes(left: &[(&str, PathBuf)], right: &[(&str, PathBuf)]) -> Result<()> {
    assert_eq!(left.len(), 2);
    assert_eq!(right.len(), 2);
    for ((left_name, left), (right_name, right)) in left.iter().zip(right) {
        assert_eq!(left_name, right_name);
        assert_eq!(fs::read(left)?, fs::read(right)?);
    }
    Ok(())
}

#[test]
fn remap_equal_pair_is_current_with_external_read_guards_and_unchanged_file_identity() -> Result<()>
{
    let temp = tempfile::tempdir()?;
    let produced = produced_pair(&temp.path().join("producer"))?;
    let key = make_directory(&temp.path().join("store/key"))?;
    let entry = publish_pair(&key, &produced)?;
    let project = make_directory(&temp.path().join("project"))?;
    let outputs = pair_paths(&project);
    assert_eq!(
        restore_entry(&key, FINGERPRINT, &project, &outputs, || Ok(()), PHASE)?,
        Some((entry.clone(), false))
    );
    let guards = outputs
        .iter()
        .map(|(_, path)| project_compilation_cache::open_read(path))
        .collect::<Result<Vec<_>>>()?;
    let before = guards
        .iter()
        .map(|file| -> Result<_> { Ok((file_identity(file)?, file.metadata()?.modified()?)) })
        .collect::<Result<Vec<_>>>()?;
    for (_, path) in &outputs {
        assert!(fs::write(path, b"writer must be refused").is_err());
    }
    assert_eq!(
        restore_entry(&key, FINGERPRINT, &project, &outputs, || Ok(()), PHASE)?,
        Some((entry.clone(), true))
    );
    assert_eq!(
        restore_completed(&entry, FINGERPRINT, &project, &outputs, &|| Ok(()), PHASE)?,
        Some(true)
    );
    assert_eq!(
        materialize_outputs(&project, &produced, &outputs, &|| Ok(()), PHASE)?,
        Some(true)
    );
    for ((_, path), (guard, snapshot)) in outputs.iter().zip(guards.iter().zip(&before)) {
        let current = project_compilation_cache::open_read(path)?;
        assert_eq!(file_identity(&current)?, snapshot.0);
        assert_eq!(file_identity(guard)?, snapshot.0);
        assert_eq!(current.metadata()?.modified()?, snapshot.1);
        assert!(fs::write(path, b"writer is still refused").is_err());
    }
    assert_pair_bytes(&produced, &outputs)
}

#[test]
fn remap_changed_counterpart_drops_comparison_readers_before_pair_copy() -> Result<()> {
    for selected in 0..2 {
        let temp = tempfile::tempdir()?;
        let produced = produced_pair(&temp.path().join("producer"))?;
        let key = make_directory(&temp.path().join("store/key"))?;
        let entry = publish_pair(&key, &produced)?;
        let project = make_directory(&temp.path().join("project"))?;
        let outputs = produced_pair(&project)?;
        fs::write(&outputs[selected].1, b"changed counterpart")?;
        assert_eq!(
            restore_completed(&entry, FINGERPRINT, &project, &outputs, &|| Ok(()), PHASE)?,
            Some(false)
        );
        assert_pair_bytes(&produced, &outputs)?;
        assert_eq!(
            restore_entry(&key, FINGERPRINT, &project, &outputs, || Ok(()), PHASE)?,
            Some((entry, true))
        );
    }
    Ok(())
}

#[test]
fn remap_missing_counterpart_drops_partial_comparison_readers_before_fresh_copy() -> Result<()> {
    for selected in 0..2 {
        let temp = tempfile::tempdir()?;
        let produced = produced_pair(&temp.path().join("producer"))?;
        let project = make_directory(&temp.path().join("project"))?;
        let outputs = produced_pair(&project)?;
        fs::remove_file(&outputs[selected].1)?;
        assert_eq!(
            materialize_outputs(&project, &produced, &outputs, &|| Ok(()), PHASE)?,
            Some(false)
        );
        assert_pair_bytes(&produced, &outputs)?;
    }
    Ok(())
}

#[test]
fn remap_corruption_of_either_stored_counterpart_is_a_miss_with_evidence_retained() -> Result<()> {
    for selected in 0..2 {
        let temp = tempfile::tempdir()?;
        let produced = produced_pair(&temp.path().join("producer"))?;
        let key = make_directory(&temp.path().join("store/key"))?;
        let entry = publish_pair(&key, &produced)?;
        let before = receipts(&entry)?;
        let payload = pair_paths(&entry.join("payload"));
        fs::write(&payload[selected].1, b"retained corrupt payload")?;
        let project = make_directory(&temp.path().join("project"))?;
        let outputs = pair_paths(&project);
        assert!(restore_entry(&key, FINGERPRINT, &project, &outputs, || Ok(()), PHASE)?.is_none());
        assert!(
            restore_completed(&entry, FINGERPRINT, &project, &outputs, &|| Ok(()), PHASE)?
                .is_none()
        );
        assert!(outputs.iter().all(|(_, path)| !path.exists()));
        assert_eq!(fs::read(&payload[selected].1)?, b"retained corrupt payload");
        assert_eq!(
            fs::read(&payload[1 - selected].1)?,
            fs::read(&produced[1 - selected].1)?
        );
        assert_eq!(receipts(&entry)?, before);
        assert_eq!(entry_directories(&key)?, Some(vec![entry]));
    }
    Ok(())
}

#[test]
fn remap_missing_either_stored_counterpart_is_a_miss_without_repairing_history() -> Result<()> {
    for selected in 0..2 {
        let temp = tempfile::tempdir()?;
        let produced = produced_pair(&temp.path().join("producer"))?;
        let key = make_directory(&temp.path().join("store/key"))?;
        let entry = publish_pair(&key, &produced)?;
        let before = receipts(&entry)?;
        let payload = pair_paths(&entry.join("payload"));
        fs::remove_file(&payload[selected].1)?;
        let project = make_directory(&temp.path().join("project"))?;
        let outputs = pair_paths(&project);
        assert!(restore_entry(&key, FINGERPRINT, &project, &outputs, || Ok(()), PHASE)?.is_none());
        assert!(outputs.iter().all(|(_, path)| !path.exists()));
        assert!(!payload[selected].1.exists());
        assert_eq!(
            fs::read(&payload[1 - selected].1)?,
            fs::read(&produced[1 - selected].1)?
        );
        assert_eq!(receipts(&entry)?, before);
        assert_eq!(entry_directories(&key)?, Some(vec![entry]));
    }
    Ok(())
}

#[test]
fn remap_wrong_fingerprint_and_extra_payload_do_not_admit_an_exact_pair() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let produced = produced_pair(&temp.path().join("producer"))?;
    let key = make_directory(&temp.path().join("store/key"))?;
    let entry = publish_pair(&key, &produced)?;
    let before = receipts(&entry)?;
    let project = make_directory(&temp.path().join("project"))?;
    let outputs = pair_paths(&project);
    assert!(restore_entry(&key, "different-key", &project, &outputs, || Ok(()), PHASE)?.is_none());
    let extra = entry.join("payload/unrecorded.jar");
    fs::write(&extra, b"unrecorded third output")?;
    assert!(restore_entry(&key, FINGERPRINT, &project, &outputs, || Ok(()), PHASE)?.is_none());
    assert!(outputs.iter().all(|(_, path)| !path.exists()));
    assert_eq!(fs::read(extra)?, b"unrecorded third output");
    assert_eq!(receipts(&entry)?, before);
    assert_pair_bytes(&produced, &pair_paths(&entry.join("payload")))
}

#[test]
fn remap_cancelled_pair_operations_error_before_writes_and_preserve_completed_history() -> Result<()>
{
    let temp = tempfile::tempdir()?;
    let produced = produced_pair(&temp.path().join("producer"))?;
    let key = make_directory(&temp.path().join("store/key"))?;
    let entry = publish_pair(&key, &produced)?;
    let before = receipts(&entry)?;
    let empty_key = make_directory(&temp.path().join("store/empty-key"))?;
    let project = make_directory(&temp.path().join("project"))?;
    let outputs = pair_paths(&project);
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("synthetic pair cancellation before effects");
    let check = || cancellation.bail_if_cancelled();
    assert!(publish_entry(&empty_key, FINGERPRINT, &produced, check).is_err());
    assert!(restore_entry(&key, FINGERPRINT, &project, &outputs, check, PHASE).is_err());
    assert!(restore_completed(&entry, FINGERPRINT, &project, &outputs, &check, PHASE).is_err());
    assert!(materialize_outputs(&project, &produced, &outputs, &check, PHASE).is_err());
    assert_eq!(entry_directories(&empty_key)?, Some(Vec::new()));
    assert!(outputs.iter().all(|(_, path)| !path.exists()));
    assert_eq!(receipts(&entry)?, before);
    assert_pair_bytes(&produced, &pair_paths(&entry.join("payload")))
}

#[test]
fn remap_cancellation_after_first_copy_never_reports_a_completed_pair_and_can_retry() -> Result<()>
{
    let temp = tempfile::tempdir()?;
    let produced = produced_pair(&temp.path().join("producer"))?;
    let key = make_directory(&temp.path().join("store/key"))?;
    let entry = publish_pair(&key, &produced)?;
    let before = receipts(&entry)?;
    let project = make_directory(&temp.path().join("project"))?;
    let outputs = pair_paths(&project);
    let cancellation = CancellationToken::new();
    let check = || {
        if outputs[0].1.exists() {
            cancellation.request_cancel("synthetic pair cancellation after first copy");
        }
        cancellation.bail_if_cancelled()
    };
    assert!(restore_completed(&entry, FINGERPRINT, &project, &outputs, &check, PHASE).is_err());
    assert!(cancellation.is_cancelled());
    assert_eq!(fs::read(&outputs[0].1)?, fs::read(&produced[0].1)?);
    assert!(!outputs[1].1.exists());
    assert_eq!(receipts(&entry)?, before);
    assert_pair_bytes(&produced, &pair_paths(&entry.join("payload")))?;
    assert_eq!(
        restore_completed(&entry, FINGERPRINT, &project, &outputs, &|| Ok(()), PHASE)?,
        Some(false)
    );
    assert_pair_bytes(&produced, &outputs)
}

#[test]
fn remap_equal_pair_outside_project_is_refused_before_the_current_fast_path() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let produced = produced_pair(&temp.path().join("producer"))?;
    let key = make_directory(&temp.path().join("store/key"))?;
    let entry = publish_pair(&key, &produced)?;
    let project = make_directory(&temp.path().join("project"))?;
    let outside = produced_pair(&temp.path().join("outside"))?;
    assert!(materialize_outputs(&project, &produced, &outside, &|| Ok(()), PHASE).is_err());
    assert!(restore_completed(&entry, FINGERPRINT, &project, &outside, &|| Ok(()), PHASE).is_err());
    assert_pair_bytes(&produced, &outside)?;
    assert_eq!(fs::read_dir(project)?.count(), 0);
    Ok(())
}

#[test]
fn remap_traversal_destination_is_refused_even_when_resolved_bytes_equal_the_pair() -> Result<()> {
    let temp = tempfile::tempdir()?;
    let produced = produced_pair(&temp.path().join("producer"))?;
    let key = make_directory(&temp.path().join("store/key"))?;
    let entry = publish_pair(&key, &produced)?;
    let project = make_directory(&temp.path().join("project"))?;
    let outputs = produced_pair(&project)?;
    make_directory(&project.join("nested"))?;
    let traversal_root = dunce::simplified(&project).join("nested/..");
    assert!(
        traversal_root
            .components()
            .any(|part| matches!(part, std::path::Component::ParentDir))
    );
    let traversal = pair_paths(&traversal_root);
    assert!(materialize_outputs(&project, &produced, &traversal, &|| Ok(()), PHASE).is_err());
    assert!(
        restore_completed(&entry, FINGERPRINT, &project, &traversal, &|| Ok(()), PHASE).is_err()
    );
    assert_pair_bytes(&produced, &outputs)
}

#[test]
fn remap_hardlinked_destination_counterpart_is_refused_before_any_pair_write() -> Result<()> {
    for selected in 0..2 {
        let temp = tempfile::tempdir()?;
        let produced = produced_pair(&temp.path().join("producer"))?;
        let key = make_directory(&temp.path().join("store/key"))?;
        let entry = publish_pair(&key, &produced)?;
        let project = make_directory(&temp.path().join("project"))?;
        let outputs = produced_pair(&project)?;
        let alias = temp.path().join("outside-alias.jar");
        fs::hard_link(&outputs[selected].1, &alias)?;
        assert!(materialize_outputs(&project, &produced, &outputs, &|| Ok(()), PHASE).is_err());
        assert!(
            restore_completed(&entry, FINGERPRINT, &project, &outputs, &|| Ok(()), PHASE).is_err()
        );
        assert_eq!(fs::read(alias)?, fs::read(&produced[selected].1)?);
        assert_pair_bytes(&produced, &outputs)?;
    }
    Ok(())
}

#[test]
fn remap_hardlinked_stored_counterpart_is_a_miss_and_retains_old_entry_bytes() -> Result<()> {
    for selected in 0..2 {
        let temp = tempfile::tempdir()?;
        let produced = produced_pair(&temp.path().join("producer"))?;
        let key = make_directory(&temp.path().join("store/key"))?;
        let entry = publish_pair(&key, &produced)?;
        let before = receipts(&entry)?;
        let payload = pair_paths(&entry.join("payload"));
        let alias = temp.path().join("payload-alias.jar");
        fs::hard_link(&payload[selected].1, &alias)?;
        let project = make_directory(&temp.path().join("project"))?;
        let outputs = pair_paths(&project);
        assert!(restore_entry(&key, FINGERPRINT, &project, &outputs, || Ok(()), PHASE)?.is_none());
        assert!(outputs.iter().all(|(_, path)| !path.exists()));
        assert_eq!(receipts(&entry)?, before);
        assert_eq!(fs::read(alias)?, fs::read(&produced[selected].1)?);
        assert_pair_bytes(&produced, &payload)?;
        assert_eq!(entry_directories(&key)?, Some(vec![entry]));
    }
    Ok(())
}
