use super::ArtifactLockWaitPolicy;
use crate::cancellation::CancellationToken;
use eyre::Context;
use std::fs::File;
use std::fs::OpenOptions;
use std::path::Path;
use std::path::PathBuf;
use std::thread;
use std::time::Duration;
use std::time::Instant;

/// Windows can report `ERROR_ACCESS_DENIED` for a short-lived sharing race as
/// well as for a genuinely inaccessible cache. Do not leave a command
/// waiting for the full fifteen-minute artifact-lock budget when the latter
/// is the more likely explanation.
pub(super) const WINDOWS_ACCESS_DENIED_RETRY_MAX_WAIT: Duration = Duration::from_secs(10);

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
        Self::acquire_with_policy(lock_path, artifact, &ArtifactLockWaitPolicy::default())
    }

    /// # Errors
    ///
    /// Returns an error if waiting is cancelled, times out, or the OS lock operation fails.
    pub fn acquire_with_cancellation(
        lock_path: impl AsRef<Path>,
        artifact: impl Into<String>,
        cancellation_token: CancellationToken,
    ) -> eyre::Result<Self> {
        Self::acquire_with_policy(
            lock_path,
            artifact,
            &ArtifactLockWaitPolicy::default().with_cancellation(cancellation_token),
        )
    }

    /// # Errors
    ///
    /// Returns an error if the lock file cannot be opened or the OS lock operation fails.
    pub fn acquire_with_policy(
        lock_path: impl AsRef<Path>,
        artifact: impl Into<String>,
        policy: &ArtifactLockWaitPolicy,
    ) -> eyre::Result<Self> {
        let lock_path = lock_path.as_ref().to_path_buf();
        let artifact = artifact.into();
        let started = Instant::now();
        let mut last_log = Instant::now()
            .checked_sub(policy.log_interval)
            .unwrap_or_else(Instant::now);
        let file = open_lock_file_with_policy(
            &lock_path,
            &artifact,
            "exclusive_write",
            policy,
            started,
            &mut last_log,
        )?;

        loop {
            policy.bail_if_cancelled()?;
            if try_lock_file(&file, &lock_path)? {
                if started.elapsed() > policy.retry_interval {
                    tracing::info!(
                        artifact = %artifact,
                        lock = %lock_path.display(),
                        waited_ms = started.elapsed().as_millis(),
                        pid = std::process::id(),
                        operation = "exclusive_write",
                        "acquired_exclusive_lock"
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
                    pid = std::process::id(),
                    operation = "exclusive_write",
                    "waiting_for_exclusive_lock"
                );
                last_log = Instant::now();
            }
            wait_for_retry(policy, started, &lock_path, &artifact, "exclusive_write")?;
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

pub(super) fn open_lock_file(lock_path: &Path) -> eyre::Result<File> {
    if let Some(parent) = lock_path.parent() {
        std::fs::create_dir_all(parent)
            .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    }
    open_lock_file_once(lock_path).map_err(|error| open_error(lock_path, error))
}

pub(super) fn open_lock_file_once(lock_path: &Path) -> std::io::Result<File> {
    OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(lock_path)
}

pub(super) fn open_lock_file_with_policy(
    lock_path: &Path,
    artifact: &str,
    operation: &str,
    policy: &ArtifactLockWaitPolicy,
    started: Instant,
    last_log: &mut Instant,
) -> eyre::Result<File> {
    open_lock_file_with_policy_using(
        lock_path,
        artifact,
        operation,
        policy,
        started,
        last_log,
        open_lock_file_once,
    )
}

pub(super) fn open_lock_file_with_policy_using(
    lock_path: &Path,
    artifact: &str,
    operation: &str,
    policy: &ArtifactLockWaitPolicy,
    started: Instant,
    last_log: &mut Instant,
    mut open: impl FnMut(&Path) -> std::io::Result<File>,
) -> eyre::Result<File> {
    if let Some(parent) = lock_path.parent() {
        std::fs::create_dir_all(parent)
            .wrap_err_with(|| format!("Failed to create lock directory {}", parent.display()))?;
    }
    loop {
        policy.bail_if_cancelled()?;
        match open(lock_path) {
            Ok(file) => return Ok(file),
            Err(error) if open_retry_budget(lock_path, &error, policy.max_wait).is_some() => {
                let open_retry_budget = open_retry_budget(lock_path, &error, policy.max_wait)
                    .expect("guard established an open retry budget");
                if last_log.elapsed() >= policy.log_interval {
                    let access_denied = cfg!(windows) && error.raw_os_error() == Some(5);
                    if access_denied {
                        tracing::warn!(
                            artifact,
                            lock = %lock_path.display(),
                            waited_ms = started.elapsed().as_millis(),
                            pid = std::process::id(),
                            operation,
                            os_error = ?error.raw_os_error(),
                            retry_budget_ms = open_retry_budget.as_millis(),
                            "Windows denied access while opening an artifact lock; retrying briefly before reporting the cache permission problem"
                        );
                    } else {
                        tracing::info!(
                            artifact,
                            lock = %lock_path.display(),
                            waited_ms = started.elapsed().as_millis(),
                            pid = std::process::id(),
                            operation,
                            os_error = ?error.raw_os_error(),
                            error_kind = ?error.kind(),
                            "waiting_to_open"
                        );
                    }
                    *last_log = Instant::now();
                }
                if let Err(wait_error) = wait_for_retry_with_max(
                    policy,
                    started,
                    open_retry_budget,
                    lock_path,
                    artifact,
                    operation,
                ) {
                    let wait_message = if cfg!(windows) && error.raw_os_error() == Some(5) {
                        format!(
                            "{wait_error}; {}",
                            windows_access_denied_hint(WINDOWS_ACCESS_DENIED_RETRY_MAX_WAIT)
                        )
                    } else {
                        wait_error.to_string()
                    };
                    return Err(open_error(lock_path, error).wrap_err(wait_message));
                }
            }
            Err(error) => return Err(open_error(lock_path, error)),
        }
    }
}

fn open_error(lock_path: &Path, error: std::io::Error) -> eyre::Report {
    let os_error = error.raw_os_error();
    let kind = error.kind();
    let report = eyre::Report::new(error).wrap_err(format!(
        "Failed to open artifact lock {} (os_error={os_error:?}, kind={kind:?})",
        lock_path.display()
    ));
    if cfg!(windows) && os_error == Some(5) {
        report.wrap_err(windows_access_denied_hint(
            WINDOWS_ACCESS_DENIED_RETRY_MAX_WAIT,
        ))
    } else {
        report
    }
}

fn windows_access_denied_hint(retry_budget: Duration) -> String {
    format!(
        "Windows denied access to the artifact lock. In the Codex sandbox, os_error=5 commonly means the sandbox denied access to the user-level artifact-cache lock; rerun with normal Windows cache access (or outside the sandbox) before investigating a stale lock. Outside the sandbox, check permissions for the cache and lock path and close any process that may be using it. A real concurrent process can produce the same ambiguous error. The CLI only retries this condition for {} ms.",
        retry_budget.as_millis()
    )
}

pub(super) fn open_retry_budget(
    lock_path: &Path,
    error: &std::io::Error,
    policy_max_wait: Duration,
) -> Option<Duration> {
    #[cfg(windows)]
    {
        match error.raw_os_error() {
            Some(32 | 33) => Some(policy_max_wait),
            Some(5) if access_denied_can_be_a_transient_open_race(lock_path) => Some(
                Duration::min(policy_max_wait, WINDOWS_ACCESS_DENIED_RETRY_MAX_WAIT),
            ),
            _ => None,
        }
    }
    #[cfg(not(windows))]
    {
        let _lock_path = lock_path;
        matches!(
            error.kind(),
            std::io::ErrorKind::WouldBlock | std::io::ErrorKind::Interrupted
        )
        .then_some(policy_max_wait)
    }
}

#[cfg(windows)]
fn access_denied_can_be_a_transient_open_race(lock_path: &Path) -> bool {
    match std::fs::metadata(lock_path) {
        Ok(metadata) => metadata.is_file() && !metadata.permissions().readonly(),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => lock_path
            .parent()
            .and_then(|parent| std::fs::metadata(parent).ok())
            .is_some_and(|metadata| metadata.is_dir() && !metadata.permissions().readonly()),
        Err(_) => false,
    }
}

pub(super) fn wait_for_retry(
    policy: &ArtifactLockWaitPolicy,
    started: Instant,
    lock_path: &Path,
    artifact: &str,
    operation: &str,
) -> eyre::Result<()> {
    wait_for_retry_with_max(
        policy,
        started,
        policy.max_wait,
        lock_path,
        artifact,
        operation,
    )
}

fn wait_for_retry_with_max(
    policy: &ArtifactLockWaitPolicy,
    started: Instant,
    max_wait: Duration,
    lock_path: &Path,
    artifact: &str,
    operation: &str,
) -> eyre::Result<()> {
    policy.bail_if_cancelled()?;
    let elapsed = started.elapsed();
    if elapsed >= max_wait {
        eyre::bail!(
            "Timed out after {} ms waiting for artifact lock {} (artifact={artifact}, operation={operation})",
            elapsed.as_millis(),
            lock_path.display()
        );
    }
    let remaining = max_wait
        .checked_sub(elapsed)
        .expect("elapsed was checked against max_wait");
    thread::sleep(Duration::min(policy.retry_interval, remaining));
    policy.bail_if_cancelled()
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
