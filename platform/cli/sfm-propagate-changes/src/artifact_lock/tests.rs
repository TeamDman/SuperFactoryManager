use super::ArtifactLock;
use super::ArtifactLockWaitPolicy;
use super::ArtifactReadLock;
use crate::cancellation::CancellationToken;
use std::sync::Arc;
use std::sync::Barrier;
use std::thread;
use std::time::Duration;
use std::time::Instant;
use tempfile::TempDir;

#[test]
fn acquires_uncontended_lock_and_creates_lock_file() {
    let dir = temp_test_dir("uncontended");
    let lock_path = dir.path().join("artifact.jar.lock");

    let lock = ArtifactLock::acquire(&lock_path, "artifact.jar").expect("lock should acquire");

    assert_eq!(lock.path(), lock_path.as_path());
    assert_eq!(lock.artifact(), "artifact.jar");
    assert!(lock_path.is_file());
    drop(lock);
}

#[test]
fn try_acquire_returns_none_while_lock_is_held() {
    let dir = temp_test_dir("try-contended");
    let lock_path = dir.path().join("artifact.jar.lock");
    let first = ArtifactLock::acquire(&lock_path, "artifact.jar").expect("first lock");

    let second =
        ArtifactLock::try_acquire(&lock_path, "artifact.jar").expect("try lock should not fail");
    assert!(second.is_none());

    drop(first);
    let third =
        ArtifactLock::try_acquire(&lock_path, "artifact.jar").expect("try lock should not fail");
    assert!(third.is_some());
    drop(third);
}

#[test]
fn stale_lock_file_without_os_lock_does_not_block() {
    let dir = temp_test_dir("stale");
    let lock_path = dir.path().join("artifact.jar.lock");
    std::fs::write(&lock_path, "left behind by a crashed process").expect("write stale file");

    let lock = ArtifactLock::try_acquire(&lock_path, "artifact.jar")
        .expect("try lock should not fail")
        .expect("stale lock file should not block without an OS lock");

    drop(lock);
}

#[test]
fn multiple_read_locks_can_be_held_together() {
    let dir = temp_test_dir("shared-readers");
    let lock_path = dir.path().join("artifact.jar.lock");

    let first =
        ArtifactReadLock::acquire(&lock_path, "artifact.jar").expect("first read lock should work");
    let second = ArtifactReadLock::try_acquire(&lock_path, "artifact.jar")
        .expect("second read lock try should not fail")
        .expect("second read lock should coexist with first");

    assert_eq!(first.path(), lock_path.as_path());
    assert_eq!(first.artifact(), "artifact.jar");
    drop(second);
    drop(first);
}

#[test]
fn simultaneous_readers_converge_on_one_new_lock_file() {
    let dir = temp_test_dir("simultaneous-reader-creators");
    let lock_path = dir.path().join("nested").join("artifact.jar.lock");
    let ready = Arc::new(Barrier::new(9));
    let release = Arc::new(Barrier::new(9));
    let mut readers = Vec::new();

    for _ in 0..8 {
        let lock_path = lock_path.clone();
        let ready = Arc::clone(&ready);
        let release = Arc::clone(&release);
        readers.push(thread::spawn(move || {
            let lock = ArtifactReadLock::acquire_with_policy(
                &lock_path,
                "artifact.jar",
                &ArtifactLockWaitPolicy::new(Duration::from_millis(2), Duration::from_millis(5))
                    .with_max_wait(Duration::from_secs(2)),
            )
            .expect("reader should create/open and share-lock the common file");
            ready.wait();
            release.wait();
            drop(lock);
        }));
    }

    ready.wait();
    assert!(lock_path.is_file());
    assert!(
        ArtifactLock::try_acquire(&lock_path, "artifact.jar")
            .expect("writer probe")
            .is_none()
    );
    release.wait();
    for reader in readers {
        reader.join().expect("reader thread should finish");
    }
}

#[test]
fn distinct_worktree_outputs_share_only_the_exact_artifact_lock() {
    let dir = temp_test_dir("three-worktree-fixture");
    let shared_lock_path = dir.path().join("shared").join("artifact.jar.lock");
    let overlap = Arc::new(Barrier::new(4));
    let release = Arc::new(Barrier::new(4));
    let mut targets = Vec::new();

    for name in ["action-hotkeys", "panel-observation", "review-diff"] {
        let local_cache = dir
            .path()
            .join("worktrees")
            .join(name)
            .join("sfm-toolchain");
        let shared_lock_path = shared_lock_path.clone();
        let overlap = Arc::clone(&overlap);
        let release = Arc::clone(&release);
        targets.push(thread::spawn(move || {
            std::fs::create_dir_all(local_cache.join(".locks")).expect("local cache tree");
            let local_build_lock = local_cache.join(".locks").join("build-cache.lock");
            std::fs::write(&local_build_lock, name).expect("local build marker");
            let shared_read = ArtifactReadLock::acquire(&shared_lock_path, "artifact.jar")
                .expect("shared artifact reader");
            overlap.wait();
            release.wait();
            drop(shared_read);
            local_build_lock
        }));
    }

    overlap.wait();
    assert!(
        ArtifactLock::try_acquire(&shared_lock_path, "artifact.jar")
            .expect("exclusive probe")
            .is_none()
    );
    release.wait();
    let local_locks = targets
        .into_iter()
        .map(|target| target.join().expect("target thread"))
        .collect::<Vec<_>>();
    assert_eq!(local_locks.len(), 3);
    assert!(local_locks.iter().all(|path| path.is_file()));
    assert_ne!(local_locks[0], local_locks[1]);
    assert_ne!(local_locks[1], local_locks[2]);
}

#[test]
fn cancellation_interrupts_exclusive_lock_wait() {
    let dir = temp_test_dir("cancel-writer-wait");
    let lock_path = dir.path().join("artifact.jar.lock");
    let reader = ArtifactReadLock::acquire(&lock_path, "artifact.jar").expect("read lock");
    let cancellation_token = CancellationToken::new();
    let wait_token = cancellation_token.clone();
    let wait_path = lock_path.clone();
    let waiter = thread::spawn(move || {
        ArtifactLock::acquire_with_policy(
            wait_path,
            "artifact.jar",
            &ArtifactLockWaitPolicy::new(Duration::from_millis(5), Duration::from_millis(10))
                .with_max_wait(Duration::from_secs(2))
                .with_cancellation(wait_token),
        )
    });

    thread::sleep(Duration::from_millis(25));
    cancellation_token.request_cancel("test cancellation");
    let error = waiter
        .join()
        .expect("waiter thread")
        .expect_err("cancelled waiter should fail");
    assert!(error.to_string().contains("test cancellation"));
    drop(reader);
}

#[cfg(windows)]
#[test]
fn transient_windows_open_sharing_violation_is_retried() {
    use std::fs::OpenOptions;
    use std::os::windows::fs::OpenOptionsExt;

    let dir = temp_test_dir("windows-open-sharing-race");
    let lock_path = dir.path().join("artifact.jar.lock");
    std::fs::write(&lock_path, []).expect("seed lock file");
    let denying_handle = OpenOptions::new()
        .read(true)
        .write(true)
        .share_mode(0)
        .open(&lock_path)
        .expect("exclusive-share handle");

    let before_error = super::artifact_lock::open_lock_file_once(&lock_path)
        .expect_err("one-shot open must reproduce the sharing race");
    assert_eq!(before_error.raw_os_error(), Some(32));

    let wait_path = lock_path.clone();
    let waiter = thread::spawn(move || {
        ArtifactReadLock::acquire_with_policy(
            wait_path,
            "artifact.jar",
            &ArtifactLockWaitPolicy::new(Duration::from_millis(5), Duration::from_millis(10))
                .with_max_wait(Duration::from_secs(2)),
        )
    });
    thread::sleep(Duration::from_millis(40));
    drop(denying_handle);

    let read_lock = waiter
        .join()
        .expect("waiter thread")
        .expect("open retry should converge after the denying handle closes");
    drop(read_lock);
}

#[cfg(windows)]
#[test]
fn terminal_open_error_reports_path_and_os_error() {
    use std::fs::OpenOptions;
    use std::os::windows::fs::OpenOptionsExt;

    let dir = temp_test_dir("windows-open-error-detail");
    let lock_path = dir.path().join("artifact.jar.lock");
    std::fs::write(&lock_path, []).expect("seed lock file");
    let _denying_handle = OpenOptions::new()
        .read(true)
        .write(true)
        .share_mode(0)
        .open(&lock_path)
        .expect("exclusive-share handle");
    let error = ArtifactLock::acquire_with_policy(
        &lock_path,
        "artifact.jar",
        &ArtifactLockWaitPolicy::new(Duration::from_millis(2), Duration::from_millis(2))
            .with_max_wait(Duration::from_millis(15)),
    )
    .expect_err("bounded retry must eventually fail");
    let rendered = format!("{error:?}");
    assert!(rendered.contains(&lock_path.display().to_string()));
    assert!(rendered.contains("os_error=Some(32)"), "{rendered}");
}

#[cfg(windows)]
#[test]
fn access_denied_open_retry_has_short_grace_but_sharing_violation_uses_policy() {
    let dir = temp_test_dir("windows-open-retry-classification");
    let lock_path = dir.path().join("artifact.jar.lock");
    std::fs::write(&lock_path, []).expect("persisted lock file");
    let policy_wait = Duration::from_mins(15);
    let access_denied = std::io::Error::from_raw_os_error(5);
    let sharing_violation = std::io::Error::from_raw_os_error(32);
    let invalid_path = std::io::Error::from_raw_os_error(123);

    assert_eq!(
        super::artifact_lock::open_retry_budget(&access_denied, &lock_path, policy_wait),
        Some(Duration::from_mins(1))
    );
    assert_eq!(
        super::artifact_lock::open_retry_budget(&sharing_violation, &lock_path, policy_wait),
        Some(policy_wait)
    );
    assert_eq!(
        super::artifact_lock::open_retry_budget(&invalid_path, &lock_path, policy_wait),
        None
    );
}

#[cfg(windows)]
#[test]
fn transient_access_denied_open_recovers_with_shortened_policy() {
    let dir = temp_test_dir("windows-access-denied-recovery");
    let lock_path = dir.path().join("artifact.jar.lock");
    std::fs::write(&lock_path, []).expect("persisted lock file");
    let policy = ArtifactLockWaitPolicy::new(Duration::from_millis(2), Duration::from_millis(2))
        .with_max_wait(Duration::from_millis(100));
    let started = Instant::now();
    let mut last_log = started;
    let mut attempts = 0;

    let file = super::artifact_lock::open_lock_file_with_policy_using(
        &lock_path,
        "artifact.jar",
        "shared_read",
        &policy,
        started,
        &mut last_log,
        |path| {
            attempts += 1;
            if attempts <= 2 {
                Err(std::io::Error::from_raw_os_error(5))
            } else {
                super::artifact_lock::open_lock_file_once(path)
            }
        },
    )
    .expect("transient access denied should recover");

    assert_eq!(attempts, 3);
    drop(file);
}

#[cfg(windows)]
#[test]
fn persistent_access_denied_open_returns_preserved_error_after_shortened_policy() {
    let dir = temp_test_dir("windows-access-denied-timeout");
    let lock_path = dir.path().join("artifact.jar.lock");
    std::fs::write(&lock_path, []).expect("persisted lock file");
    let policy = ArtifactLockWaitPolicy::new(Duration::from_millis(2), Duration::from_millis(2))
        .with_max_wait(Duration::from_millis(12));
    let started = Instant::now();
    let mut last_log = started;

    let error = super::artifact_lock::open_lock_file_with_policy_using(
        &lock_path,
        "artifact.jar",
        "shared_read",
        &policy,
        started,
        &mut last_log,
        |_| Err(std::io::Error::from_raw_os_error(5)),
    )
    .expect_err("persistent access denied should reach its bound");
    let rendered = format!("{error:?}");
    assert!(rendered.contains(&lock_path.display().to_string()));
    assert!(rendered.contains("os_error=Some(5)"), "{rendered}");
}

#[test]
fn write_lock_waits_until_read_lock_is_released() {
    let dir = temp_test_dir("writer-waits-for-reader");
    let lock_path = dir.path().join("artifact.jar.lock");
    let reader = ArtifactReadLock::acquire(&lock_path, "artifact.jar").expect("read lock");
    let writer_try =
        ArtifactLock::try_acquire(&lock_path, "artifact.jar").expect("writer try should not fail");
    assert!(writer_try.is_none());

    let release_thread = thread::spawn(move || {
        thread::sleep(Duration::from_millis(40));
        drop(reader);
    });

    let started = Instant::now();
    let writer = ArtifactLock::acquire_with_policy(
        &lock_path,
        "artifact.jar",
        &ArtifactLockWaitPolicy::new(Duration::from_millis(5), Duration::from_millis(10)),
    )
    .expect("writer should acquire after reader drops");

    assert!(started.elapsed() >= Duration::from_millis(30));
    drop(writer);
    release_thread.join().expect("release thread should finish");
}

#[test]
fn waits_until_contended_lock_is_released() {
    let dir = temp_test_dir("waits");
    let lock_path = dir.path().join("artifact.jar.lock");
    let first = ArtifactLock::acquire(&lock_path, "artifact.jar").expect("first lock");
    let release_thread = thread::spawn(move || {
        thread::sleep(Duration::from_millis(40));
        drop(first);
    });

    let started = Instant::now();
    let second = ArtifactLock::acquire_with_policy(
        &lock_path,
        "artifact.jar",
        &ArtifactLockWaitPolicy::new(Duration::from_millis(5), Duration::from_millis(10)),
    )
    .expect("second lock should eventually acquire");

    assert!(started.elapsed() >= Duration::from_millis(30));
    drop(second);
    release_thread.join().expect("release thread should finish");
}

fn temp_test_dir(name: &str) -> TempDir {
    tempfile::Builder::new()
        .prefix(&format!("sfm-artifact-lock-{name}-"))
        .tempdir()
        .expect("test temp dir should be created")
}
