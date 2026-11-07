use super::*;
use crate::cancellation::CancellationToken;
use std::os::unix::fs::MetadataExt as _;

fn fixture() -> (tempfile::TempDir, JdkPinV4, Vec<u8>) {
    let bytes = super::tests::sdk_tar();
    let mut pin = super::tests::pin_for(&bytes);
    pin.artifacts[0].platform = "linux-x64".to_owned();
    pin.artifacts[0].url = pin.artifacts[0]
        .url
        .replace("windows-x64", "linux-x64")
        .replace(".zip", ".tar.gz");
    let root = tempfile::tempdir().unwrap();
    fs::create_dir(root.path().join("archives")).unwrap();
    fs::write(
        root.path()
            .join("archives")
            .join(archive_name(&pin.artifacts[0].sha512, &pin.artifacts[0])),
        &bytes,
    )
    .unwrap();
    (root, pin, bytes)
}

#[test]
fn sdk_only_offline_install_reuses_archive_and_paced_warm_identity_without_copies()
-> eyre::Result<()> {
    let (root, pin, bytes) = fixture();
    let artifact = &pin.artifacts[0];
    let archive = root
        .path()
        .join("archives")
        .join(archive_name(&artifact.sha512, artifact));
    let before = fs::metadata(&archive)?;
    let mut image = acquire_with_control(
        &pin,
        artifact,
        root.path(),
        true,
        InstallControl {
            extraction_bytes_per_second: Some(2097152),
            cancellation: None,
        },
        |_, _| panic!("an existing offline archive must never fetch"),
    )?;
    image.recheck_for_controlled(root.path(), &pin, artifact, InstallControl::default())?;
    let home = image.home().to_path_buf();
    let java = fs::metadata(home.join("bin/java"))?;
    let receipt_before = fs::read(home.join(RECEIPT_NAME))?;
    let seal = seal_path(&root.path().join("installs"), &artifact.sha512);
    let seal_before = fs::read(&seal)?;
    drop(image);
    let image = acquire_with_control(
        &pin,
        artifact,
        root.path(),
        true,
        InstallControl {
            extraction_bytes_per_second: Some(1048576),
            cancellation: None,
        },
        |_, _| panic!("warm offline reuse must never fetch"),
    )?;
    assert_eq!(image.home(), home);
    assert_eq!(fs::metadata(home.join("bin/java"))?.ino(), java.ino());
    assert_eq!(fs::metadata(&archive)?.ino(), before.ino());
    assert_eq!(fs::metadata(&archive)?.modified()?, before.modified()?);
    assert_eq!(fs::read(&archive)?, bytes);
    assert_eq!(fs::read(home.join(RECEIPT_NAME))?, receipt_before);
    assert_eq!(fs::read(&seal)?, seal_before);
    assert_eq!(fs::read_dir(root.path().join("archives"))?.count(), 1);
    assert_eq!(fs::read_dir(root.path().join("installs"))?.count(), 2);
    drop(image);
    fs::write(home.join("bin/java"), b"evil")?;
    assert!(
        install_verified_pinned_jbrsdk(
            &pin,
            artifact,
            root.path(),
            true,
            InstallControl::default()
        )
        .is_err()
    );
    assert_eq!(fs::read(home.join("bin/java"))?, b"evil");
    assert_eq!(fs::read(&seal)?, seal_before);
    Ok(())
}

#[test]
fn sdk_only_offline_missing_archive_and_invalid_rate_never_fetch_or_publish() -> eyre::Result<()> {
    let (_populated, pin, _) = fixture();
    let root = tempfile::tempdir()?;
    let absent = root.path().join("invalid-rate");
    assert!(
        acquire_with_control(
            &pin,
            &pin.artifacts[0],
            &absent,
            true,
            InstallControl {
                extraction_bytes_per_second: Some(0),
                cancellation: None
            },
            |_, _| panic!("invalid rate must never fetch")
        )
        .is_err()
    );
    assert!(!absent.exists());
    assert!(
        acquire_with_control(
            &pin,
            &pin.artifacts[0],
            root.path(),
            true,
            InstallControl::default(),
            |_, _| panic!("offline miss must never fetch")
        )
        .is_err()
    );
    assert_eq!(fs::read_dir(root.path().join("archives"))?.count(), 0);
    assert_eq!(fs::read_dir(root.path().join("installs"))?.count(), 0);
    Ok(())
}

#[test]
fn sdk_only_cancel_during_extract_and_final_audit_reaps_helper_and_preserves_archive()
-> eyre::Result<()> {
    use crate::jdk_install_control::with_spawn_observer;
    use std::cell::Cell;
    use std::rc::Rc;
    let (root, pin, bytes) = fixture();
    let artifact = &pin.artifacts[0];
    let cancel = CancellationToken::new();
    let request = cancel.clone();
    let pid = Rc::new(Cell::new(0));
    let observed = Rc::clone(&pid);
    let result = with_spawn_observer(
        move |id| {
            observed.set(id);
            request.request_cancel("cancel extraction");
        },
        || {
            install_verified_pinned_jbrsdk(
                &pin,
                artifact,
                root.path(),
                true,
                InstallControl {
                    extraction_bytes_per_second: Some(1),
                    cancellation: Some(&cancel),
                },
            )
        },
    );
    assert!(result.is_err());
    assert!(format!("{:#}", result.expect_err("cancelled extract")).contains("cancel extraction"));
    assert!(pid.get() > 0 && !Path::new(&format!("/proc/{}", pid.get())).exists());
    assert_eq!(fs::read_dir(root.path().join("installs"))?.count(), 0);
    assert_eq!(
        fs::read(
            root.path()
                .join("archives")
                .join(archive_name(&artifact.sha512, artifact))
        )?,
        bytes
    );
    let mut image = install_verified_pinned_jbrsdk(
        &pin,
        artifact,
        root.path(),
        true,
        InstallControl::default(),
    )?;
    let home = image.home().to_path_buf();
    let seal = seal_path(&root.path().join("installs"), &artifact.sha512);
    let seal_before = fs::read(&seal)?;
    let cancel = CancellationToken::new();
    let request = cancel.clone();
    pid.set(0);
    let observed = Rc::clone(&pid);
    let result = with_spawn_observer(
        move |id| {
            observed.set(id);
            request.request_cancel("cancel final audit");
        },
        || {
            image.recheck_for_controlled(
                root.path(),
                &pin,
                artifact,
                InstallControl {
                    extraction_bytes_per_second: None,
                    cancellation: Some(&cancel),
                },
            )
        },
    );
    assert!(result.is_err());
    assert!(format!("{:#}", result.expect_err("cancelled audit")).contains("cancel final audit"));
    assert!(pid.get() > 0 && !Path::new(&format!("/proc/{}", pid.get())).exists());
    assert_eq!(fs::read(&seal)?, seal_before);
    assert_eq!(fs::read(home.join("bin/java"))?, b"java");
    assert_eq!(
        fs::read(
            root.path()
                .join("archives")
                .join(archive_name(&artifact.sha512, artifact))
        )?,
        bytes
    );
    Ok(())
}
