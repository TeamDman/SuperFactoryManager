use super::CatalogReleaseArtifactReceipt;
use super::VerifiedCatalogReleaseArtifact;
#[cfg(windows)]
use super::catalog_release_artifact_file::CatalogReleaseArtifactFile;
#[cfg(windows)]
use super::catalog_release_artifact_receipt::MAX_JAR_BYTES;
use super::catalog_release_fixture::CatalogReleaseFixture;
use super::catalog_release_fixture::inventory;
#[cfg(windows)]
use super::pending_catalog_release_receipt::PendingCatalogReleaseReceipt;
use crate::cancellation::CancellationToken;
use crate::jar_build::engine::expand_catalog_release_metadata;
use crate::source_projection::catalog_owned_project::collect_catalog_project;
use crate::source_projection::frozen_recipe_project::tests::install_catalog_release_recipe_fixture;
use crate::source_projection::released_native_inputs::BUILD_CONFIGURATION_PATH;
use eyre::Result;
use std::fs;
use std::fs::File;
#[cfg(windows)]
use std::io::Error as IoError;
use std::io::Write as _;
use std::path::PathBuf;
use zip::CompressionMethod;
use zip::ZipWriter;
use zip::write::SimpleFileOptions;

pub(super) const FIXTURE_JAR: &str = "build/libs/reviewed.jar";

pub(super) fn fixture_class(java_major: u16) -> Vec<u8> {
    let mut bytes = vec![0xca, 0xfe, 0xba, 0xbe, 0, 0];
    bytes.extend((java_major + 44).to_be_bytes());
    bytes.extend(5_u16.to_be_bytes());
    for (name, index) in [("ca/teamdman/sfm/SFM", 1_u16), ("java/lang/Object", 3_u16)] {
        bytes.push(1);
        bytes.extend(u16::try_from(name.len()).unwrap().to_be_bytes());
        bytes.extend(name.as_bytes());
        bytes.push(7);
        bytes.extend(index.to_be_bytes());
    }
    for value in [0x21_u16, 2, 4, 0, 0, 0, 0] {
        bytes.extend(value.to_be_bytes());
    }
    bytes
}

fn fixture_entries(fixture: &CatalogReleaseFixture) -> Result<Vec<(String, Vec<u8>)>> {
    let metadata = fixture.prepare()?;
    let project =
        collect_catalog_project(fixture.repository(), fixture.repository(), &fixture.key)?
            .check_current()?;
    let loader_metadata = if matches!(metadata.target_id(), "1.21.0" | "1.21.1" | "26.1.2") {
        "META-INF/neoforge.mods.toml"
    } else {
        "META-INF/mods.toml"
    };
    let expanded = expand_catalog_release_metadata(
        project
            .selected_input(&format!("src/main/resources/{loader_metadata}"))?
            .output_bytes(),
        project.selected_input("gradle.properties")?.output_bytes(),
    )?;
    Ok(vec![
        (
            "ca/teamdman/sfm/SFM.class".to_owned(),
            fixture_class(metadata.java_major()),
        ),
        (loader_metadata.to_owned(), expanded),
    ])
}

fn write_archive(
    fixture: &CatalogReleaseFixture,
    relative: &str,
    entries: &[(String, Vec<u8>)],
) -> Result<()> {
    let path = fixture.root().join(relative);
    fs::create_dir_all(path.parent().unwrap())?;
    let mut archive = ZipWriter::new(File::create(path)?);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
    for (name, bytes) in entries {
        archive.start_file(name, options)?;
        archive.write_all(bytes)?;
    }
    archive.finish()?.sync_all()?;
    Ok(())
}

/// Synthetic successful-native boundary for tests only. Production can create
/// the receipt only from the native package executor with its original recipe.
pub(super) fn write_fixture_artifact(
    fixture: &CatalogReleaseFixture,
    relative: &str,
) -> Result<()> {
    let project =
        collect_catalog_project(fixture.repository(), fixture.repository(), &fixture.key)?
            .check_current()?;
    install_catalog_release_recipe_fixture(&project)?;
    write_archive(fixture, relative, &fixture_entries(fixture)?)?;
    CatalogReleaseArtifactReceipt::capture_fixture(
        &fixture.request(&fixture.key)?,
        relative,
        &CancellationToken::new(),
    )
}

fn sidecar(fixture: &CatalogReleaseFixture) -> PathBuf {
    fixture
        .root()
        .join(format!("{FIXTURE_JAR}.sfm-release.json"))
}

#[test]
fn native_receipt_and_exact_archive_are_read_only_verified() -> Result<()> {
    for target in ["1.19.2", "1.21.0", "26.1.2"] {
        let fixture = CatalogReleaseFixture::new(target)?;
        fixture.publish();
        write_fixture_artifact(&fixture, FIXTURE_JAR)?;
        let before = inventory(fixture.repository())?;
        let artifact = VerifiedCatalogReleaseArtifact::verify(
            &fixture.request(&fixture.key)?,
            FIXTURE_JAR,
            &CancellationToken::new(),
        )?;
        assert_eq!(artifact.relative_path(), FIXTURE_JAR);
        assert_eq!(artifact.metadata().target_id(), target);
        assert!(artifact.bytes() > 0);
        assert!(artifact.sha256().starts_with("sha256:"));
        assert!(artifact.receipt_sha256().starts_with("sha256:"));
        artifact.recheck()?;
        assert_eq!(inventory(fixture.repository())?, before);
    }
    Ok(())
}

#[test]
fn a_filename_and_existing_jar_do_not_replace_native_receipt() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    write_archive(&fixture, FIXTURE_JAR, &fixture_entries(&fixture)?)?;
    let before = inventory(fixture.repository())?;
    assert!(
        VerifiedCatalogReleaseArtifact::verify(
            &fixture.request(&fixture.key)?,
            FIXTURE_JAR,
            &CancellationToken::new(),
        )
        .is_err()
    );
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[test]
fn changed_jar_receipt_recipe_and_selected_inputs_are_refused() -> Result<()> {
    for changed in ["jar", "receipt", "recipe", "source"] {
        let mut fixture = CatalogReleaseFixture::new("1.21.0")?;
        fixture.publish();
        write_fixture_artifact(&fixture, FIXTURE_JAR)?;
        match changed {
            "jar" => fs::write(fixture.root().join(FIXTURE_JAR), b"changed archive")?,
            "receipt" => {
                let raw = fs::read_to_string(sidecar(&fixture))?;
                fs::write(
                    sidecar(&fixture),
                    raw.replace(&fixture.key, "wrong/projection"),
                )?;
            }
            "recipe" => fs::write(
                fixture.repository().join(BUILD_CONFIGURATION_PATH),
                b"changed recipe",
            )?,
            "source" => fixture
                .set_properties("mod_id=sfm\nmod_version=4.36.0\nminecraft_version=1.21\n")?,
            _ => unreachable!(),
        }
        let before = inventory(fixture.repository())?;
        assert!(
            VerifiedCatalogReleaseArtifact::verify(
                &fixture.request(&fixture.key)?,
                FIXTURE_JAR,
                &CancellationToken::new(),
            )
            .is_err(),
            "{changed}"
        );
        assert_eq!(inventory(fixture.repository())?, before);
    }
    Ok(())
}

#[test]
fn retained_artifact_and_receipt_refuse_same_byte_path_replacements() -> Result<()> {
    for replace_receipt in [false, true] {
        let fixture = CatalogReleaseFixture::new("1.21.0")?;
        fixture.publish();
        write_fixture_artifact(&fixture, FIXTURE_JAR)?;
        let artifact = VerifiedCatalogReleaseArtifact::verify(
            &fixture.request(&fixture.key)?,
            FIXTURE_JAR,
            &CancellationToken::new(),
        )?;
        let path = if replace_receipt {
            sidecar(&fixture)
        } else {
            fixture.root().join(FIXTURE_JAR)
        };
        let bytes = fs::read(&path)?;
        let moved = path.with_extension("moved");
        if fs::rename(&path, &moved).is_ok() {
            fs::write(&path, bytes)?;
            assert!(artifact.recheck().is_err());
        } else {
            // Windows file sharing retains object identity before a swap can occur.
            artifact.recheck()?;
        }
    }
    Ok(())
}

#[test]
fn unsafe_incomplete_and_wrong_metadata_archives_never_receive_receipts() -> Result<()> {
    for invalid in ["duplicate", "traversal", "missing", "class", "metadata"] {
        let fixture = CatalogReleaseFixture::new("1.21.0")?;
        fixture.publish();
        let project =
            collect_catalog_project(fixture.repository(), fixture.repository(), &fixture.key)?
                .check_current()?;
        install_catalog_release_recipe_fixture(&project)?;
        let mut entries = fixture_entries(&fixture)?;
        match invalid {
            "duplicate" => {
                entries.push(("CA/TEAMDMan/SFM/SFM.class".to_owned(), entries[0].1.clone()))
            }
            "traversal" => entries.push(("../escaped".to_owned(), vec![1])),
            "missing" => {
                entries.remove(0);
            }
            "class" => entries[0].1.truncate(12),
            "metadata" => entries[1].1 = b"modId=\"other\"\nversion=\"4.35.0\"\n".to_vec(),
            _ => unreachable!(),
        }
        write_archive(&fixture, FIXTURE_JAR, &entries)?;
        let before = inventory(fixture.repository())?;
        assert!(
            CatalogReleaseArtifactReceipt::capture_fixture(
                &fixture.request(&fixture.key)?,
                FIXTURE_JAR,
                &CancellationToken::new(),
            )
            .is_err(),
            "{invalid}"
        );
        assert!(!sidecar(&fixture).exists());
        assert_eq!(inventory(fixture.repository())?, before);
    }
    Ok(())
}

#[test]
fn release_artifacts_require_direct_owned_portable_jar_leaves() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    let before = inventory(fixture.repository())?;
    for relative in [
        "../build/libs/a.jar",
        "build/libs/../a.jar",
        "build/libs/sub/a.jar",
        "build/libs/a.zip",
        "C:/outside.jar",
        "build\\libs\\a.jar",
    ] {
        assert!(
            VerifiedCatalogReleaseArtifact::verify(
                &fixture.request(&fixture.key)?,
                relative,
                &CancellationToken::new(),
            )
            .is_err(),
            "{relative}"
        );
    }
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[test]
fn cancelled_artifact_verification_does_not_create_a_receipt() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    let token = CancellationToken::new();
    token.request_cancel("cancelled release artifact fixture");
    let before = inventory(fixture.repository())?;
    assert!(
        VerifiedCatalogReleaseArtifact::verify(
            &fixture.request(&fixture.key)?,
            FIXTURE_JAR,
            &token
        )
        .is_err()
    );
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[test]
fn identical_native_receipt_is_idempotent_and_different_sidecars_are_preserved() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    write_fixture_artifact(&fixture, FIXTURE_JAR)?;
    let request = fixture.request(&fixture.key)?;
    let before = inventory(fixture.repository())?;
    CatalogReleaseArtifactReceipt::capture_fixture(
        &request,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )?;
    assert_eq!(inventory(fixture.repository())?, before);
    let mut changed = fs::read(sidecar(&fixture))?;
    changed.push(b'\n');
    fs::write(sidecar(&fixture), changed)?;
    let before = inventory(fixture.repository())?;
    let error = CatalogReleaseArtifactReceipt::capture_fixture(
        &request,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )
    .unwrap_err();
    assert!(
        error.to_string().contains("fresh --output-name"),
        "{error:#}"
    );
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[test]
fn an_isolated_bogus_recipe_id_is_rejected_against_current_selection() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    write_fixture_artifact(&fixture, FIXTURE_JAR)?;
    let raw = fs::read_to_string(sidecar(&fixture))?;
    let old = "\"recipe_id\": \"sfm:released-native-inputs/4.34.0/1.21.0@1\"";
    assert!(
        raw.contains(old),
        "fixture must retain the real reviewed recipe id"
    );
    let changed = raw.replacen(old, "\"recipe_id\": \"sfm:bogus-recipe@1\"", 1);
    fs::write(sidecar(&fixture), changed)?;
    let before = inventory(fixture.repository())?;
    let error = VerifiedCatalogReleaseArtifact::verify(
        &fixture.request(&fixture.key)?,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )
    .unwrap_err();
    assert!(
        error.to_string().contains("producer or packaging identity"),
        "{error:#}"
    );
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[cfg(windows)]
#[test]
fn exclusive_receipt_grants_readers_only_after_close_without_releasing_jar_ancestors() -> Result<()>
{
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    write_fixture_artifact(&fixture, FIXTURE_JAR)?;
    let complete = fs::read(sidecar(&fixture))?;
    fs::remove_file(sidecar(&fixture))?;
    let held_jar = CatalogReleaseArtifactFile::read(&fixture.root(), FIXTURE_JAR, MAX_JAR_BYTES)?;
    let mut pending = PendingCatalogReleaseReceipt::new(&fixture.root(), FIXTURE_JAR)?;
    let split = complete.len() / 2;
    pending.write_partial_for_test(&complete[..split])?;
    let denied = VerifiedCatalogReleaseArtifact::verify(
        &fixture.request(&fixture.key)?,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )
    .unwrap_err();
    assert_eq!(
        denied
            .downcast_ref::<IoError>()
            .and_then(IoError::raw_os_error),
        Some(32)
    );
    pending.finish(&complete[split..], &CancellationToken::new())?;
    let verified = VerifiedCatalogReleaseArtifact::verify(
        &fixture.request(&fixture.key)?,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )?;
    let before = inventory(fixture.repository())?;
    CatalogReleaseArtifactReceipt::capture_fixture(
        &fixture.request(&fixture.key)?,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )?;
    assert_eq!(inventory(fixture.repository())?, before);
    let owner = fixture.root();
    let parent = owner.join("build/libs");
    let moved = owner.join("build/libs-not-moved");
    assert!(parent.starts_with(&owner) && moved.starts_with(&owner));
    assert!(fs::rename(parent, moved).is_err());
    held_jar.recheck()?;
    verified.recheck()?;
    Ok(())
}

#[cfg(windows)]
#[test]
fn interrupted_partial_receipt_is_preserved_and_cannot_qualify_or_be_clobbered() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    write_fixture_artifact(&fixture, FIXTURE_JAR)?;
    fs::remove_file(sidecar(&fixture))?;
    let held_jar = CatalogReleaseArtifactFile::read(&fixture.root(), FIXTURE_JAR, MAX_JAR_BYTES)?;
    let mut pending = PendingCatalogReleaseReceipt::new(&fixture.root(), FIXTURE_JAR)?;
    pending.write_partial_for_test(b"{")?;
    drop(pending);
    let before = inventory(fixture.repository())?;
    assert!(
        VerifiedCatalogReleaseArtifact::verify(
            &fixture.request(&fixture.key)?,
            FIXTURE_JAR,
            &CancellationToken::new(),
        )
        .is_err()
    );
    let refused = CatalogReleaseArtifactReceipt::capture_fixture(
        &fixture.request(&fixture.key)?,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )
    .unwrap_err();
    assert!(
        refused.to_string().contains("fresh --output-name"),
        "{refused:#}"
    );
    assert_eq!(fs::read(sidecar(&fixture))?, b"{");
    assert_eq!(inventory(fixture.repository())?, before);
    held_jar.recheck()?;
    Ok(())
}

#[cfg(windows)]
#[test]
fn cancelled_writer_can_leave_a_complete_independently_verified_local_observation() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    write_fixture_artifact(&fixture, FIXTURE_JAR)?;
    let complete = fs::read(sidecar(&fixture))?;
    fs::remove_file(sidecar(&fixture))?;
    let held_jar = CatalogReleaseArtifactFile::read(&fixture.root(), FIXTURE_JAR, MAX_JAR_BYTES)?;
    let mut pending = PendingCatalogReleaseReceipt::new(&fixture.root(), FIXTURE_JAR)?;
    pending.write_partial_for_test(&complete)?;
    let cancelled = CancellationToken::new();
    cancelled.request_cancel("complete local receipt before interrupted handoff");
    assert!(pending.finish(&[], &cancelled).is_err());
    assert_eq!(fs::read(sidecar(&fixture))?, complete);
    let before = inventory(fixture.repository())?;
    let independently_verified = VerifiedCatalogReleaseArtifact::verify(
        &fixture.request(&fixture.key)?,
        FIXTURE_JAR,
        &CancellationToken::new(),
    )?;
    independently_verified.recheck()?;
    held_jar.recheck()?;
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}
