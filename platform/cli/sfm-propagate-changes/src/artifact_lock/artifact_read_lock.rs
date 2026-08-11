use super::ArtifactLockWaitPolicy;
use super::artifact_lock::open_lock_file;
use eyre::Context;
use std::fs::File;
use std::path::Path;
use std::path::PathBuf;
use std::thread;
use std::time::Instant;

#[derive(Debug)]
pub struct ArtifactReadLock {
    file: File,
    path: PathBuf,
    artifact: String, // todo(2026-06-16) the meaning of this being a string is unclear. perhaps we need a newtype or a rename here to clarify what this represents? is this just the display name for the lock
}

impl ArtifactReadLock {
    /// # Errors
    ///
    /// Returns an error if the lock file cannot be opened or the OS lock operation fails.
    pub fn acquire(lock_path: impl AsRef<Path>, artifact: impl Into<String>) -> eyre::Result<Self> {
        let artifact = artifact.into();
        let _span = tracing::info_span!(
            "acquire_artifact_read_lock",
            artifact = %artifact,
            lock = %lock_path.as_ref().display()
        )
        .entered();
        Self::acquire_with_policy(lock_path, artifact, ArtifactLockWaitPolicy::default())
    }

    /// # Errors
    ///
    /// Returns an error if the lock file cannot be opened or the OS lock operation fails.
    pub fn acquire_with_policy(
        lock_path: impl AsRef<Path>,
        artifact: impl Into<String>,
        policy: ArtifactLockWaitPolicy,
    ) -> eyre::Result<Self> {
        let lock_path = lock_path.as_ref().to_path_buf();
        let artifact = artifact.into();
        let file = open_lock_file(&lock_path)?;
        let started = Instant::now();
        let mut last_log = Instant::now()
            .checked_sub(policy.log_interval)
            .unwrap_or_else(Instant::now);

        loop {
            if try_lock_shared_file(&file, &lock_path)? {
                if started.elapsed() > policy.retry_interval {
                    tracing::info!(
                        artifact = %artifact,
                        lock = %lock_path.display(),
                        waited_ms = started.elapsed().as_millis(),
                        "acquired artifact read lock after waiting"
                    );
                }
                return Ok(Self {
                    file,
                    path: lock_path,
                    artifact,
                });
            }

            if last_log.elapsed() >= policy.log_interval {
                tracing::info!(
                    artifact = %artifact,
                    lock = %lock_path.display(),
                    waited_ms = started.elapsed().as_millis(),
                    "waiting for artifact read lock"
                );
                last_log = Instant::now();
            }
            thread::sleep(policy.retry_interval);
        }
    }

    /// # Errors
    ///
    /// Returns an error if the lock file cannot be opened or the OS lock operation fails.
    pub fn try_acquire(
        lock_path: impl AsRef<Path>,
        artifact: impl Into<String>,
    ) -> eyre::Result<Option<Self>> {
        let lock_path = lock_path.as_ref().to_path_buf();
        let artifact = artifact.into();
        let file = open_lock_file(&lock_path)?;
        if try_lock_shared_file(&file, &lock_path)? {
            Ok(Some(Self {
                file,
                path: lock_path,
                artifact,
            }))
        } else {
            Ok(None)
        }
    }

    #[must_use]
    pub fn path(&self) -> &Path {
        &self.path
    }

    #[must_use]
    pub fn artifact(&self) -> &str {
        &self.artifact
    }
}

impl Drop for ArtifactReadLock {
    fn drop(&mut self) {
        if let Err(error) = self.file.unlock() {
            tracing::warn!(
                artifact = %self.artifact,
                lock = %self.path.display(),
                error = %error,
                "failed to unlock artifact read lock"
            );
        }
    }
}

fn try_lock_shared_file(file: &File, lock_path: &Path) -> eyre::Result<bool> {
    match file.try_lock_shared() {
        Ok(()) => Ok(true),
        Err(std::fs::TryLockError::WouldBlock) => Ok(false),
        Err(std::fs::TryLockError::Error(error)) => Err(error).wrap_err_with(|| {
            format!(
                "Failed to acquire artifact read lock on {}",
                lock_path.display()
            )
        }),
    }
}
