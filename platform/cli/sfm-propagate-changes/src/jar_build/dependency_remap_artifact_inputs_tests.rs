//! Actual dependency input keys and real store-decision negatives, without Java.

use super::DependencyRemapArtifactInputs;
use super::ForgePreparationInputRequest;
use super::valid_shared_key;
use crate::cancellation::CancellationToken;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use std::fs;
use std::path::Path;

fn capture_file(
    path: &Path,
    expected: &[u8],
    shared: &str,
) -> Result<DependencyRemapArtifactInputs> {
    let request = ForgePreparationInputRequest::new("artifact:dependency", path.to_owned()).pinned(
        ContentHash::from_bytes(expected, ContentHashAlgorithm::Sha1),
    );
    DependencyRemapArtifactInputs::capture_selected(
        &request,
        "example:dependency:1",
        shared,
        &CancellationToken::new(),
    )?
    .ok_or_else(|| eyre::eyre!("Synthetic dependency holding unavailable"))
}

#[test]
fn remap_dependency_key_omits_project_root_filename_configuration_and_sfm_body() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let shared = sha256(b"actual common SDK tool mapping authority");
    let first = fixture.path().join("first.jar");
    let second = fixture.path().join("different-source-generation.jar");
    fs::write(&first, b"actual dependency")?;
    fs::write(&second, b"actual dependency")?;
    let first = capture_file(&first, b"actual dependency", &shared)?;
    let second = capture_file(&second, b"actual dependency", &shared)?;
    let before = first.fingerprint().to_owned();
    fs::write(fixture.path().join("SFM.java"), b"body-only edit")?;
    fs::write(
        fixture.path().join("configuration.txt"),
        b"different declaration aliases",
    )?;
    assert_eq!(first.fingerprint(), before);
    assert_eq!(second.fingerprint(), before);
    assert_eq!(before.len(), 71);
    Ok(())
}

#[test]
fn changed_dependency_bytes_rekey_the_actual_store_decision_and_preserve_history() -> Result<()> {
    use super::super::super::entry_directories;
    use super::super::super::make_directory;
    use super::super::super::publish;
    use super::super::super::restore;

    let fixture = tempfile::tempdir()?;
    let shared = sha256(b"common SDK tool mapping authority");
    let jar = fixture.path().join("dependency.jar");
    fs::write(&jar, b"original dependency")?;
    let original = capture_file(&jar, b"original dependency", &shared)?;
    let original_key = original.fingerprint().to_owned();
    let original_store = make_directory(
        &fixture
            .path()
            .join("store")
            .join(original_key.trim_start_matches("sha256:")),
    )?;
    let produced = make_directory(&fixture.path().join("produced"))?;
    let output = produced.join("remapped.jar");
    fs::write(&output, b"old real synthetic producer output")?;
    publish(
        &original_store,
        &original_key,
        &[("remapped.jar", output.clone())],
        || Ok(()),
    )?;
    let old_entry = entry_directories(&original_store)?.unwrap().pop().unwrap();
    let old_state = fs::read(old_entry.join("inputs.sha256"))?;
    let old_payload = fs::read(old_entry.join("payload/remapped.jar"))?;
    drop(original);

    fs::write(&jar, b"new admitted dependency")?;
    assert!(
        capture_file(&jar, b"original dependency", &shared).is_err(),
        "old pin must refuse tampering"
    );
    // Model a newly admitted current resolver pin, never replacement of the old pin.
    let current = capture_file(&jar, b"new admitted dependency", &shared)?;
    let current_key = current.fingerprint().to_owned();
    assert_ne!(current_key, original_key);
    let current_store = make_directory(
        &fixture
            .path()
            .join("store")
            .join(current_key.trim_start_matches("sha256:")),
    )?;
    let destination = make_directory(&fixture.path().join("new-project"))?;
    let selected = [("remapped.jar", destination.join("remapped.jar"))];
    assert!(!restore(
        &current_store,
        &current_key,
        &destination,
        &selected,
        || Ok(())
    )?);
    assert!(!restore(
        &original_store,
        &current_key,
        &destination,
        &selected,
        || Ok(())
    )?);
    assert!(!selected[0].1.exists());
    fs::write(&output, b"new real synthetic producer output")?;
    publish(
        &current_store,
        &current_key,
        &[("remapped.jar", output)],
        || Ok(()),
    )?;
    assert!(restore(
        &current_store,
        &current_key,
        &destination,
        &selected,
        || Ok(())
    )?);
    assert_eq!(
        fs::read(&selected[0].1)?,
        b"new real synthetic producer output"
    );
    assert_eq!(fs::read(old_entry.join("inputs.sha256"))?, old_state);
    assert_eq!(
        fs::read(old_entry.join("payload/remapped.jar"))?,
        old_payload
    );
    Ok(())
}

#[test]
fn remap_dependency_owner_holds_the_actual_pinned_leaf_until_effects_finish() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let jar = fixture.path().join("dependency.jar");
    fs::write(&jar, b"actual dependency")?;
    let owner = capture_file(&jar, b"actual dependency", &sha256(b"common"))?;
    assert!(fs::write(&jar, b"changed during materialization").is_err());
    assert!(fs::rename(&jar, fixture.path().join("renamed.jar")).is_err());
    owner.held.recheck()?;
    drop(owner);
    fs::write(jar, b"changed after publication")?;
    Ok(())
}

#[test]
fn remap_artifact_requires_a_full_shared_authority_key() {
    assert!(valid_shared_key(&sha256(b"full shared inputs")));
    assert!(!valid_shared_key("sha256:0123456789ab"));
    assert!(!valid_shared_key("0123456789ab"));
    assert!(!valid_shared_key(&format!("sha256:{}", "A".repeat(64))));
}
