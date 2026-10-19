use super::ArtifactProvenance;
use super::ArtifactSource;
use super::ContentHash;
use super::ContentHashAlgorithm;
use super::artifact_provenance_path;
use super::read_artifact_provenance;
use super::write_artifact_provenance;
use eyre::Context as _;
use std::fs;
use std::io::Read;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::sync::mpsc;
use std::thread;
use std::time::Duration;
use std::time::UNIX_EPOCH;

fn receipt(label: &str, padding: usize) -> ArtifactProvenance {
    ArtifactProvenance {
        schema_version: 1,
        source: ArtifactSource::RemoteMaven,
        coordinate: Some(format!("test:artifact:{label}")),
        repository: Some("fixture".to_owned()),
        url: Some(format!(
            "https://example.invalid/{label}/{}",
            "x".repeat(padding)
        )),
        original_path: None,
        source_relative_path: None,
        source_git: None,
        source_build: None,
        hash: ContentHash::from_bytes(label.as_bytes(), ContentHashAlgorithm::Blake3),
    }
}

#[test]
fn identical_receipt_keeps_sidecar_modification_time() -> eyre::Result<()> {
    let temporary = tempfile::tempdir()?;
    let artifact = temporary.path().join("artifact.jar");
    let provenance = receipt("same", 32);
    write_artifact_provenance(&artifact, &provenance)?;
    let sidecar = artifact_provenance_path(&artifact)?;
    let expected = fs::read(&sidecar)?;
    let sentinel = UNIX_EPOCH + Duration::from_secs(1_234_567_890);
    fs::OpenOptions::new()
        .write(true)
        .open(&sidecar)?
        .set_times(fs::FileTimes::new().set_modified(sentinel))?;
    let modified = fs::metadata(&sidecar)?.modified()?;

    write_artifact_provenance(&artifact, &provenance)?;

    assert_eq!(fs::read(&sidecar)?, expected);
    assert_eq!(fs::metadata(&sidecar)?.modified()?, modified);
    assert_eq!(fs::read_dir(temporary.path())?.count(), 1);
    Ok(())
}

#[test]
fn changed_receipt_is_published_and_remains_readable() -> eyre::Result<()> {
    let temporary = tempfile::tempdir()?;
    let artifact = temporary.path().join("nested").join("artifact.jar");
    let before = receipt("before", 32);
    let after = receipt("after", 96);
    write_artifact_provenance(&artifact, &before)?;
    assert_eq!(read_artifact_provenance(&artifact)?, Some(before));

    write_artifact_provenance(&artifact, &after)?;

    assert_eq!(read_artifact_provenance(&artifact)?, Some(after.clone()));
    assert_eq!(
        fs::read(artifact_provenance_path(&artifact)?)?,
        facet_json::to_string_pretty(&after)?.as_bytes()
    );
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_TEMPORARY;

        assert_eq!(
            fs::metadata(artifact_provenance_path(&artifact)?)?.file_attributes()
                & FILE_ATTRIBUTE_TEMPORARY.0,
            0,
            "published receipts must not retain the staging attribute"
        );
    }
    assert_eq!(fs::read_dir(artifact.parent().unwrap())?.count(), 1);
    Ok(())
}

#[test]
fn concurrent_readers_only_observe_complete_old_or_new_receipts() -> eyre::Result<()> {
    let temporary = tempfile::tempdir()?;
    let artifact = temporary.path().join("artifact.jar");
    let sidecar = artifact_provenance_path(&artifact)?;
    // Different large receipts expose both truncation windows and partial writes.
    let before = receipt("before", 64 * 1024);
    let after = receipt("after", 96 * 1024);
    let before_bytes = facet_json::to_string_pretty(&before)?.into_bytes();
    write_artifact_provenance(&artifact, &before)?;
    let stop = AtomicBool::new(false);
    let (ready_tx, ready_rx) = mpsc::channel();
    let (changed_tx, changed_rx) = mpsc::channel();

    thread::scope(|scope| -> eyre::Result<()> {
        let reader = scope.spawn(|| -> eyre::Result<(usize, usize)> {
            // Keep an old read handle across publication to detect in-place writes
            // deterministically, in addition to repeatedly reopening the live path.
            let mut pinned = fs::File::open(&sidecar)?;
            let (mut old_reads, mut new_reads) = (0, 0);
            loop {
                let observed = read_artifact_provenance(&artifact)?
                    .ok_or_else(|| eyre::eyre!("published receipt disappeared"))?;
                if observed == before {
                    old_reads += 1;
                    if old_reads == 1 {
                        ready_tx.send(())?;
                    }
                } else {
                    eyre::ensure!(observed == after, "unexpected provenance receipt");
                    new_reads += 1;
                    if new_reads == 1 {
                        changed_tx.send(())?;
                    }
                }
                if stop.load(Ordering::Acquire) {
                    break;
                }
                thread::yield_now();
            }
            let mut pinned_bytes = Vec::new();
            pinned.read_to_end(&mut pinned_bytes)?;
            eyre::ensure!(
                pinned_bytes == before_bytes,
                "old open file was overwritten"
            );
            Ok((old_reads, new_reads))
        });
        let publication = (|| -> eyre::Result<()> {
            ready_rx.recv_timeout(Duration::from_secs(10))?;
            write_artifact_provenance(&artifact, &after)
                .wrap_err("first replacement while old receipt handle remains open")?;
            changed_rx.recv_timeout(Duration::from_secs(10))?;
            for index in 0..64 {
                let next = if index % 2 == 0 { &before } else { &after };
                write_artifact_provenance(&artifact, next)
                    .wrap_err_with(|| format!("concurrent publication iteration {index}"))?;
            }
            Ok(())
        })();
        // Always release the reader even if publication or its handshake failed.
        stop.store(true, Ordering::Release);
        let reads = reader
            .join()
            .map_err(|_| eyre::eyre!("provenance reader panicked"))??;
        publication?;
        assert!(reads.0 > 0 && reads.1 > 0, "both receipts must be observed");
        Ok(())
    })?;
    assert_eq!(read_artifact_provenance(&artifact)?, Some(after));
    Ok(())
}

#[test]
fn non_file_destination_survives_failed_publication() -> eyre::Result<()> {
    let temporary = tempfile::tempdir()?;
    let artifact = temporary.path().join("artifact.jar");
    let sidecar = artifact_provenance_path(&artifact)?;
    fs::create_dir(&sidecar)?;
    let marker = sidecar.join("keep");
    fs::write(&marker, b"unchanged directory")?;

    let _error = write_artifact_provenance(&artifact, &receipt("after", 32)).unwrap_err();

    assert_eq!(fs::read(marker)?, b"unchanged directory");
    assert_eq!(fs::read_dir(temporary.path())?.count(), 1);
    Ok(())
}

#[cfg(windows)]
#[test]
fn denied_replacement_preserves_receipt_and_cleans_staging() -> eyre::Result<()> {
    use std::os::windows::fs::OpenOptionsExt as _;
    use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

    let temporary = tempfile::tempdir()?;
    let artifact = temporary.path().join("artifact.jar");
    let before = receipt("before", 32);
    write_artifact_provenance(&artifact, &before)?;
    let sidecar = artifact_provenance_path(&artifact)?;
    let expected = fs::read(&sidecar)?;
    // A reader explicitly denying deletion must still make publication fail;
    // the writer must never bypass it by truncating, deleting, or changing ACLs.
    let _lease = fs::OpenOptions::new()
        .read(true)
        .share_mode(FILE_SHARE_READ.0)
        .open(&sidecar)?;

    let error = write_artifact_provenance(&artifact, &receipt("after", 96)).unwrap_err();

    assert!(matches!(
        error
            .downcast_ref::<std::io::Error>()
            .and_then(std::io::Error::raw_os_error),
        Some(5 | 32 | 33)
    ));
    assert_eq!(fs::read(&sidecar)?, expected);
    assert_eq!(read_artifact_provenance(&artifact)?, Some(before));
    assert_eq!(fs::read_dir(temporary.path())?.count(), 1);
    Ok(())
}

#[test]
fn missing_receipt_remains_absent_without_creating_files() -> eyre::Result<()> {
    let temporary = tempfile::tempdir()?;
    assert_eq!(
        read_artifact_provenance(&temporary.path().join("missing.jar"))?,
        None
    );
    assert_eq!(fs::read_dir(temporary.path())?.count(), 0);
    Ok(())
}

#[test]
fn malformed_receipt_fails_closed_without_repair() -> eyre::Result<()> {
    let temporary = tempfile::tempdir()?;
    let artifact = temporary.path().join("artifact.jar");
    let sidecar = artifact_provenance_path(&artifact)?;
    let malformed = b"{\"schema_version\":";
    fs::write(&sidecar, malformed)?;

    let error = read_artifact_provenance(&artifact).unwrap_err();

    assert!(error.to_string().contains("Failed to parse"));
    assert_eq!(fs::read(sidecar)?, malformed);
    Ok(())
}

#[cfg(windows)]
#[test]
fn reader_sharing_failure_fails_closed_and_preserves_receipt() -> eyre::Result<()> {
    use std::os::windows::fs::OpenOptionsExt as _;
    use windows::Win32::Storage::FileSystem::FILE_SHARE_DELETE;
    use windows::Win32::Storage::FileSystem::FILE_SHARE_WRITE;

    let temporary = tempfile::tempdir()?;
    let artifact = temporary.path().join("artifact.jar");
    let before = receipt("before", 32);
    write_artifact_provenance(&artifact, &before)?;
    let sidecar = artifact_provenance_path(&artifact)?;
    let lease = fs::OpenOptions::new()
        .write(true)
        .share_mode((FILE_SHARE_WRITE | FILE_SHARE_DELETE).0)
        .open(&sidecar)?;
    let started = std::time::Instant::now();

    let error = read_artifact_provenance(&artifact).unwrap_err();
    let elapsed = started.elapsed();

    assert!(elapsed < Duration::from_secs(3));
    assert!(matches!(
        error
            .downcast_ref::<std::io::Error>()
            .and_then(std::io::Error::raw_os_error),
        Some(5 | 32 | 33)
    ));
    drop(lease);
    assert_eq!(read_artifact_provenance(&artifact)?, Some(before));
    Ok(())
}
