//! Synthetic common authority/key tests without an SDK product or subprocess.

use super::ContentHash;
use super::ContentHashAlgorithm;
use super::DirectoryLeases;
use super::ForgePreparationInputRequest;
use super::HeldForgePreparationInput;
use super::SPECIALSOURCE_REVIEWED_COORDINATE;
use super::SPECIALSOURCE_REVIEWED_HASH;
use super::SPECIALSOURCE_REVIEWED_MAIN;
use super::finish_fingerprint;
use super::recipe_basis;
use super::reviewed_remap_identity;
use super::sha256;
use super::validate_selected_lock;
use crate::cancellation::CancellationToken;
use eyre::Result;
use sha2::Digest as _;
use sha2::Sha256;
use std::fs;
use std::path::Path;

fn capture_common(
    root: &Path,
    mapping: &[u8],
) -> Result<(DirectoryLeases, Vec<HeldForgePreparationInput>)> {
    let mut directories = DirectoryLeases::default();
    let mut files = Vec::new();
    let inputs: [(&str, &str, &[u8]); 8] = [
        ("sdk:java", "sdk/bin/java.exe", b"fake selected java"),
        ("sdk:javac", "sdk/bin/javac.exe", b"fake selected javac"),
        ("sdk:release", "sdk/release", b"fake selected release"),
        ("sdk:modules", "sdk/lib/modules", b"fake selected modules"),
        (
            "selected-lock:authored",
            "core/schema-4.json",
            b"{\"schema\":4}\n",
        ),
        (
            "selected-lock:generated",
            "project/schema-4.json",
            b"{\"schema\":4}\n",
        ),
        (
            "artifact:tool-specialsource",
            "maven/specialsource.jar",
            b"fake selected tool",
        ),
        ("dependency-remap:mapping", "project/mapping.tsrg", mapping),
    ];
    for (role, relative, raw) in inputs {
        let path = root.join(relative);
        fs::create_dir_all(path.parent().unwrap())?;
        fs::write(&path, raw)?;
        let request = ForgePreparationInputRequest::new(role, path);
        let captured = HeldForgePreparationInput::capture(
            &request,
            &mut directories,
            &CancellationToken::new(),
        )?
        .ok_or_else(|| eyre::eyre!("Synthetic input holding unavailable"))?;
        files.push(captured.input);
    }
    Ok((directories, files))
}

fn common_key(files: &[HeldForgePreparationInput]) -> String {
    let mut hash = Sha256::new();
    recipe_basis(
        &mut hash,
        "remap",
        "1.19.2",
        "net.minecraftforge:forge:1.19.2-43.4.0",
        17,
        Some("official"),
        Some("1.19.2"),
    );
    finish_fingerprint(
        hash,
        "remap",
        "same-real-sdk-identity",
        "same-held-sdk-image",
        files,
    )
}

#[test]
fn remap_common_key_omits_sfm_bodies_at_minecraft_and_userdev_inputs() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let first = fixture.path().join("generation-a");
    let second = fixture.path().join("generation-b");
    let (_first_dirs, first_files) = capture_common(&first, b"a actual.Mapping\n")?;
    let (_second_dirs, second_files) = capture_common(&second, b"a actual.Mapping\n")?;
    let before = common_key(&first_files);
    for relative in [
        "project/SFM.java",
        "project/at.cfg",
        "minecraft/client.jar",
        "forge/userdev.jar",
    ] {
        let path = first.join(relative);
        fs::create_dir_all(path.parent().unwrap())?;
        fs::write(path, b"unconsumed body edit")?;
    }
    assert_eq!(common_key(&first_files), before);
    assert_eq!(common_key(&second_files), before);
    assert_eq!(first_files.len(), 8);
    Ok(())
}

#[test]
fn remap_common_key_binds_actual_mapping_bytes() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let (_first_dirs, first) =
        capture_common(&fixture.path().join("first"), b"a before.Mapping\n")?;
    let (_second_dirs, second) =
        capture_common(&fixture.path().join("second"), b"a after.Mapping\n")?;
    assert_ne!(common_key(&first), common_key(&second));
    Ok(())
}

#[test]
fn remap_mapping_is_retained_from_the_held_file_for_the_parser() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let path = fixture.path().join("mapping.tsrg");
    let original = b"a actual.Mapping\n";
    fs::write(&path, original)?;
    let request =
        ForgePreparationInputRequest::new("dependency-remap:mapping", path.clone()).retained(1024);
    let mut directories = DirectoryLeases::default();
    let captured =
        HeldForgePreparationInput::capture(&request, &mut directories, &CancellationToken::new())?
            .ok_or_else(|| eyre::eyre!("Synthetic mapping holding unavailable"))?;
    assert_eq!(captured.retained.as_deref(), Some(original.as_slice()));
    assert!(fs::write(&path, b"changed before parser").is_err());
    captured.input.recheck()?;
    drop(captured);
    fs::write(path, b"changed after all workers")?;
    Ok(())
}

#[test]
fn remap_raw_lock_checks_actual_authored_and_generated_files() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let selected = b"{\"schema\":4}\n";
    let expected = sha256(selected);
    let mut directories = DirectoryLeases::default();
    for role in ["authored", "generated"] {
        let path = fixture.path().join(format!("{role}.json"));
        fs::write(&path, selected)?;
        let request = ForgePreparationInputRequest::new(role, path.clone()).retained(1024);
        let captured = HeldForgePreparationInput::capture(
            &request,
            &mut directories,
            &CancellationToken::new(),
        )?
        .ok_or_else(|| eyre::eyre!("Synthetic lock holding unavailable"))?;
        validate_selected_lock(captured.retained.as_ref().unwrap(), selected, &expected)?;
        drop(captured);
        fs::write(&path, b"{\"schema\":4}\n \n")?;
        let changed = HeldForgePreparationInput::capture(
            &request,
            &mut directories,
            &CancellationToken::new(),
        )?
        .ok_or_else(|| eyre::eyre!("Synthetic changed lock holding unavailable"))?;
        assert!(
            validate_selected_lock(changed.retained.as_ref().unwrap(), selected, &expected)
                .is_err()
        );
    }
    Ok(())
}

#[test]
fn remap_requires_the_exact_reviewed_shaded_specialsource_identity() -> Result<()> {
    let hash =
        ContentHash::parse(SPECIALSOURCE_REVIEWED_HASH).map_err(|error| eyre::eyre!(error))?;
    assert!(reviewed_remap_identity(
        Some(SPECIALSOURCE_REVIEWED_COORDINATE),
        &hash,
        SPECIALSOURCE_REVIEWED_MAIN
    ));
    assert!(!reviewed_remap_identity(
        Some("net.md-5:SpecialSource:other"),
        &hash,
        SPECIALSOURCE_REVIEWED_MAIN
    ));
    assert!(!reviewed_remap_identity(
        Some(SPECIALSOURCE_REVIEWED_COORDINATE),
        &ContentHash::from_bytes(b"different tool", ContentHashAlgorithm::Blake3),
        SPECIALSOURCE_REVIEWED_MAIN
    ));
    assert!(!reviewed_remap_identity(
        Some(SPECIALSOURCE_REVIEWED_COORDINATE),
        &hash,
        "other.Main"
    ));
    Ok(())
}
