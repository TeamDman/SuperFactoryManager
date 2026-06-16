use super::ArtifactLockWaitPolicy;
use eyre::Context;
use std::fs::File;
use std::fs::OpenOptions;
use std::path::Path;
use std::path::PathBuf;
use std::thread;
use std::time::Instant;

#[derive(Debug)]
pub struct ArtifactLock {
    file: File,
    path: PathBuf,
    artifact: String,
}

impl ArtifactLock {
    /// # Errors
    ///
    /// Returns an error if the lock file cannot be opened or the OS lock operation fails.
    pub fn acquire(lock_path: impl AsRef<Path>, artifact: impl Into<String>) -> eyre::Result<Self> {
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
            if try_lock_file(&file, &lock_path)? {
                if started.elapsed() > policy.retry_interval {
                    tracing::info!(
                        artifact = %artifact,
                        lock = %lock_path.display(),
                        waited_ms = started.elapsed().as_millis(),
                        "acquired artifact lock after waiting"
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
                    "waiting for artifact lock"
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
        if try_lock_file(&file, &lock_path)? {
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

impl Drop for ArtifactLock {
    fn drop(&mut self) {
        if let Err(error) = self.file.unlock() {
            tracing::warn!(
                artifact = %self.artifact,
                lock = %self.path.display(),
                error = %error,
                "failed to unlock artifact lock"
            );
        }
    }
}

fn open_lock_file(lock_path: &Path) -> eyre::Result<File> {
    if let Some(parent) = lock_path.parent() {
        std::fs::create_dir_all(parent)
            .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    }
    OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(lock_path)
        .wrap_err_with(|| format!("Failed to open artifact lock {}", lock_path.display()))
}

fn try_lock_file(file: &File, lock_path: &Path) -> eyre::Result<bool> {
    match file.try_lock() {
        Ok(()) => Ok(true),
        Err(std::fs::TryLockError::WouldBlock) => Ok(false),
        Err(std::fs::TryLockError::Error(error)) => Err(error).wrap_err_with(|| {
            format!("Failed to acquire artifact lock on {}", lock_path.display())
        }),
    }
}
