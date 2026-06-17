use super::ArtifactLock;
use super::ArtifactLockWaitPolicy;
use super::ArtifactReadLock;
use std::path::PathBuf;
use std::sync::atomic::AtomicU64;
use std::sync::atomic::Ordering;
use std::thread;
use std::time::Duration;
use std::time::Instant;

static NEXT_TEST_DIR: AtomicU64 = AtomicU64::new(0);

#[test]
fn acquires_uncontended_lock_and_creates_lock_file() {
    let dir = temp_test_dir("uncontended");
    let lock_path = dir.join("artifact.jar.lock");

    let lock = ArtifactLock::acquire(&lock_path, "artifact.jar").expect("lock should acquire");

    assert_eq!(lock.path(), lock_path.as_path());
    assert_eq!(lock.artifact(), "artifact.jar");
    assert!(lock_path.is_file());
    drop(lock);
    let _ = std::fs::remove_dir_all(dir);
}

#[test]
fn try_acquire_returns_none_while_lock_is_held() {
    let dir = temp_test_dir("try-contended");
    let lock_path = dir.join("artifact.jar.lock");
    let first = ArtifactLock::acquire(&lock_path, "artifact.jar").expect("first lock");

    let second =
        ArtifactLock::try_acquire(&lock_path, "artifact.jar").expect("try lock should not fail");
    assert!(second.is_none());

    drop(first);
    let third =
        ArtifactLock::try_acquire(&lock_path, "artifact.jar").expect("try lock should not fail");
    assert!(third.is_some());
    drop(third);
    let _ = std::fs::remove_dir_all(dir);
}

#[test]
fn stale_lock_file_without_os_lock_does_not_block() {
    let dir = temp_test_dir("stale");
    let lock_path = dir.join("artifact.jar.lock");
    std::fs::write(&lock_path, "left behind by a crashed process").expect("write stale file");

    let lock = ArtifactLock::try_acquire(&lock_path, "artifact.jar")
        .expect("try lock should not fail")
        .expect("stale lock file should not block without an OS lock");

    drop(lock);
    let _ = std::fs::remove_dir_all(dir);
}

#[test]
fn multiple_read_locks_can_be_held_together() {
    let dir = temp_test_dir("shared-readers");
    let lock_path = dir.join("artifact.jar.lock");

    let first =
        ArtifactReadLock::acquire(&lock_path, "artifact.jar").expect("first read lock should work");
    let second = ArtifactReadLock::try_acquire(&lock_path, "artifact.jar")
        .expect("second read lock try should not fail")
        .expect("second read lock should coexist with first");

    assert_eq!(first.path(), lock_path.as_path());
    assert_eq!(first.artifact(), "artifact.jar");
    drop(second);
    drop(first);
    let _ = std::fs::remove_dir_all(dir);
}

#[test]
fn write_lock_waits_until_read_lock_is_released() {
    let dir = temp_test_dir("writer-waits-for-reader");
    let lock_path = dir.join("artifact.jar.lock");
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
        ArtifactLockWaitPolicy::new(Duration::from_millis(5), Duration::from_millis(10)),
    )
    .expect("writer should acquire after reader drops");

    assert!(started.elapsed() >= Duration::from_millis(30));
    drop(writer);
    release_thread.join().expect("release thread should finish");
    let _ = std::fs::remove_dir_all(dir);
}

#[test]
fn waits_until_contended_lock_is_released() {
    let dir = temp_test_dir("waits");
    let lock_path = dir.join("artifact.jar.lock");
    let first = ArtifactLock::acquire(&lock_path, "artifact.jar").expect("first lock");
    let release_thread = thread::spawn(move || {
        thread::sleep(Duration::from_millis(40));
        drop(first);
    });

    let started = Instant::now();
    let second = ArtifactLock::acquire_with_policy(
        &lock_path,
        "artifact.jar",
        ArtifactLockWaitPolicy::new(Duration::from_millis(5), Duration::from_millis(10)),
    )
    .expect("second lock should eventually acquire");

    assert!(started.elapsed() >= Duration::from_millis(30));
    drop(second);
    release_thread.join().expect("release thread should finish");
    let _ = std::fs::remove_dir_all(dir);
}

fn temp_test_dir(name: &str) -> PathBuf {
    let id = NEXT_TEST_DIR.fetch_add(1, Ordering::Relaxed);
    let path = std::env::temp_dir().join(format!(
        "sfm-artifact-lock-{name}-{}-{id}",
        std::process::id()
    ));
    std::fs::create_dir_all(&path).expect("test temp dir should be created");
    path
}
