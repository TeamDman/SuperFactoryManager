use super::*;
use crate::jdk::ResolvedJava;
use std::os::windows::fs::OpenOptionsExt as _;

fn fixture() -> Result<(tempfile::TempDir, BuildSnapshotReceipt)> {
    let temporary = tempfile::tempdir()?;
    let minecraft = temporary.path().join("projection");
    let cache = minecraft.join("build/sfm-toolchain/fixture");
    for root in ROOT_NAMES {
        fs::create_dir_all(cache.join("project").join(root))?;
    }
    fs::create_dir_all(cache.join("project/staged-resources/empty"))?;
    fs::write(cache.join("project/classes/Main.class"), b"main")?;
    fs::write(cache.join("project/staged-resources/empty.txt"), b"")?;
    fs::write(cache.join("project/gametest/classes/Test.class"), b"test")?;
    fs::write(
        cache.join("project/datagen/classes/Unicode-\u{03bb}.class"),
        b"data",
    )?;
    let java = ResolvedJava {
        executable: minecraft.join("sdk/bin/java.exe"),
        home: Some(minecraft.join("sdk")),
        version_output: "snapshot fixture SDK".to_owned(),
        major_version: 17,
        selection: "fixture".to_owned(),
        pin_url: None,
        pin_sha512: None,
    };
    let receipt = BuildSnapshotReceipt::publish(
        &minecraft,
        &cache,
        "1.19.2",
        &java,
        &cache.join("project"),
        "sdk-fixture",
    )?;
    Ok((temporary, receipt))
}

fn candidate(source: &BuildSnapshotReceipt) -> Result<(tempfile::TempDir, BuildSnapshotReceipt)> {
    let directory = tempfile::Builder::new()
        .prefix("snapshot-")
        .tempdir_in(source.cache_dir.join("build-snapshots"))?;
    let mut receipt = source.clone();
    receipt.snapshot_dir = canonical_directory(directory.path())?;
    receipt.snapshot_id = receipt
        .snapshot_dir
        .file_name()
        .unwrap()
        .to_str()
        .unwrap()
        .to_owned();
    receipt.files.clear();
    receipt.directories = BTreeSet::from(["project".to_owned()]);
    ensure_owned_directory(&receipt.snapshot_dir, &receipt.snapshot_dir.join("project"))?;
    Ok((directory, receipt))
}

fn finish(receipt: &BuildSnapshotReceipt) -> Result<()> {
    receipt.validate()?;
    write_new(&receipt.receipt_path(), &receipt.json_bytes()?)?;
    receipt.recheck()
}

#[test]
fn windows_capture_matches_legacy_receipt_and_reads_each_payload_once() -> Result<()> {
    let (_temporary, source) = fixture()?;
    let project = source.cache_dir.join("project");
    let token = CancellationToken::new();
    let mut capture = SnapshotSourceCapture::prepare(&project, &token)?.unwrap();
    let (_new_directory, mut observed) = candidate(&source)?;
    let mut total = 0;
    capture.copy_into(&mut observed, &mut total, &token)?;
    assert_eq!(capture.payload_reads, 4);
    assert_eq!(capture.held_files.len(), 4);
    assert_eq!(total, 12);
    let (_old_directory, mut legacy) = candidate(&source)?;
    let mut old_total = 0;
    for root in ROOT_NAMES {
        legacy.copy_root(&project, root, &mut old_total, &token)?;
    }
    assert_eq!(observed.files, legacy.files);
    assert_eq!(observed.directories, legacy.directories);
    assert_eq!(total, old_total);
    finish(&observed)?;
    assert!(
        observed
            .directories
            .contains("project/staged-resources/empty")
    );
    Ok(())
}

#[test]
fn captured_leaf_and_ancestors_remain_held_until_the_owner_drops() -> Result<()> {
    let (temporary, source) = fixture()?;
    let project = source.cache_dir.join("project");
    let leaf = project.join("classes/Main.class");
    let alias = temporary.path().join("hardlink.class");
    fs::hard_link(&leaf, &alias)?;
    let token = CancellationToken::new();
    let mut capture = SnapshotSourceCapture::prepare(&project, &token)?.unwrap();
    let (_directory, mut receipt) = candidate(&source)?;
    capture.copy_into(&mut receipt, &mut 0, &token)?;
    assert!(fs::write(&leaf, b"edit").is_err());
    assert!(fs::write(&alias, b"edit").is_err());
    assert!(fs::remove_file(&leaf).is_err());
    assert!(fs::rename(project.join("classes"), project.join("moved")).is_err());
    finish(&receipt)?;
    receipt.publish_latest(&receipt.json_bytes()?, &token)?;
    assert!(fs::write(&leaf, b"edit").is_err());
    drop(capture);
    fs::write(&alias, b"edit")?;
    fs::rename(project.join("classes"), project.join("moved"))?;
    receipt.recheck()?;
    Ok(())
}

#[test]
fn directory_capacity_fallback_precedes_all_source_payload_and_copy_effects() -> Result<()> {
    let (_temporary, source) = fixture()?;
    let project = source.cache_dir.join("project");
    let token = CancellationToken::new();
    assert_eq!(MAX_DIRECTORY_LEASES, 16_384);
    let before = fs::read_dir(source.cache_dir.join("build-snapshots"))?.count();
    assert!(SnapshotSourceCapture::prepare_bounded(&project, &token, 0)?.is_none());
    assert!(
        SnapshotSourceCapture::prepare_bounded(&project, &token, project.ancestors().count())?
            .is_none()
    );
    assert_eq!(
        fs::read_dir(source.cache_dir.join("build-snapshots"))?.count(),
        before
    );
    // Both outcomes dropped all owners; a normal producer can still edit.
    fs::write(project.join("classes/Main.class"), b"edited after fallback")?;
    Ok(())
}

#[test]
fn held_directories_do_not_claim_to_close_the_enumerated_child_set() -> Result<()> {
    let (_temporary, source) = fixture()?;
    let project = source.cache_dir.join("project");
    let token = CancellationToken::new();
    let mut capture = SnapshotSourceCapture::prepare(&project, &token)?.unwrap();
    fs::write(project.join("classes/Later.class"), b"outside captured set")?;
    let (_directory, mut receipt) = candidate(&source)?;
    capture.copy_into(&mut receipt, &mut 0, &token)?;
    assert!(!receipt.files.contains_key("project/classes/Later.class"));
    finish(&receipt)?;
    fs::write(
        receipt
            .snapshot_dir
            .join("project/classes/Unexpected.class"),
        b"extra",
    )?;
    assert!(receipt.recheck().is_err());
    Ok(())
}

#[test]
fn existing_writer_and_cancellation_preserve_latest_success_and_release_holds() -> Result<()> {
    let (_temporary, source) = fixture()?;
    let project = source.cache_dir.join("project");
    let latest = source.cache_dir.join("state/latest-successful-build.json");
    let original = fs::read(&latest)?;
    let writer = fs::OpenOptions::new()
        .write(true)
        .share_mode(7)
        .open(project.join("gametest/classes/Test.class"))?;
    let token = CancellationToken::new();
    let mut capture = SnapshotSourceCapture::prepare(&project, &token)?.unwrap();
    let (_directory, mut receipt) = candidate(&source)?;
    assert!(capture.copy_into(&mut receipt, &mut 0, &token).is_err());
    assert_eq!(
        capture.held_files.len(),
        2,
        "earlier sorted leaves were captured"
    );
    assert!(fs::write(project.join("classes/Main.class"), b"main").is_err());
    assert!(
        fs::write(
            project.join("datagen/classes/Unicode-\u{03bb}.class"),
            b"data"
        )
        .is_err()
    );
    assert_eq!(fs::read(&latest)?, original);
    drop(capture);
    fs::write(project.join("classes/Main.class"), b"main")?;
    fs::write(
        project.join("datagen/classes/Unicode-\u{03bb}.class"),
        b"data",
    )?;
    drop(writer);
    let mut capture = SnapshotSourceCapture::prepare(&project, &token)?.unwrap();
    token.request_cancel("fixture cancellation");
    assert!(SnapshotSourceCapture::prepare(&project, &token).is_err());
    assert!(capture.copy_into(&mut receipt, &mut 0, &token).is_err());
    drop(capture);
    fs::write(project.join("classes/Main.class"), b"released")?;
    assert_eq!(fs::read(&latest)?, original);
    source.recheck()?;
    Ok(())
}

#[test]
fn oversized_source_and_failed_destination_verification_preserve_latest() -> Result<()> {
    let (_temporary, source) = fixture()?;
    let project = source.cache_dir.join("project");
    let latest = source.cache_dir.join("state/latest-successful-build.json");
    let original = fs::read(&latest)?;
    let large = project.join("classes/Large.class");
    File::create(&large)?.set_len(MAX_FILE_BYTES + 1)?;
    let token = CancellationToken::new();
    assert!(SnapshotSourceCapture::prepare(&project, &token).is_err());
    assert_eq!(fs::read(&latest)?, original);
    fs::remove_file(large)?;
    let mut capture = SnapshotSourceCapture::prepare(&project, &token)?.unwrap();
    let (_directory, mut receipt) = candidate(&source)?;
    capture.copy_into(&mut receipt, &mut 0, &token)?;
    finish(&receipt)?;
    fs::write(
        receipt.snapshot_dir.join("project/classes/Main.class"),
        b"altered",
    )?;
    assert!(receipt.recheck().is_err());
    assert_eq!(fs::read(&latest)?, original);
    Ok(())
}

#[test]
fn unavailable_directory_list_share_capability_falls_back_to_the_legacy_publisher() -> Result<()> {
    let (_temporary, source) = fixture()?;
    let project = source.cache_dir.join("project");
    // An allowed pre-existing directory writer conflicts with the additional
    // read-only shared LIST lease, but not metadata checks or file reads.
    let directory_writer = fs::OpenOptions::new()
        .access_mode(0x0002)
        .share_mode(7)
        .custom_flags(0x0200_0000 | 0x0020_0000)
        .open(project.join("classes"))?;
    let token = CancellationToken::new();
    assert!(SnapshotSourceCapture::prepare(&project, &token)?.is_none());
    // A non-directory must remain a validation error, never capability fallback.
    let mut unsafe_lease = DirectoryLeases::default();
    assert!(
        unsafe_lease
            .prepare_if_available(&project.join("staged-resources/empty.txt"), "child")
            .is_err()
    );
    drop(unsafe_lease);
    let (_directory, mut legacy) = candidate(&source)?;
    for root in ROOT_NAMES {
        legacy.copy_root(&project, root, &mut 0, &token)?;
    }
    finish(&legacy)?;
    let mut required = DirectoryLeases::default();
    assert!(required.prepare(&project, "classes/Main.class").is_err());
    drop(required);
    drop(directory_writer);
    assert!(SnapshotSourceCapture::prepare(&project, &token)?.is_some());
    Ok(())
}
