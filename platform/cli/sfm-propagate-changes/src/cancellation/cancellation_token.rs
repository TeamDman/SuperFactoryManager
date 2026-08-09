use std::fmt;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;

#[derive(Clone, Default)]
pub struct CancellationToken {
    inner: Arc<CancellationInner>,
}

#[derive(Default)]
struct CancellationInner {
    cancelled: AtomicBool,
    reason: Mutex<Option<String>>,
}

impl CancellationToken {
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Request cancellation for this token.
    ///
    /// The first cancellation reason is retained, so independent callers can race safely without
    /// overwriting the cause that originally requested shutdown.
    pub fn request_cancel(&self, reason: impl Into<String>) {
        {
            let mut stored_reason = self
                .inner
                .reason
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            if stored_reason.is_none() {
                *stored_reason = Some(reason.into());
            }
        }
        self.inner.cancelled.store(true, Ordering::Release);
    }

    #[must_use]
    pub fn is_cancelled(&self) -> bool {
        self.inner.cancelled.load(Ordering::Acquire)
    }

    #[must_use]
    pub fn cancellation_reason(&self) -> Option<String> {
        self.inner
            .reason
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .clone()
    }

    /// Return an error if cancellation has been requested.
    ///
    /// # Errors
    ///
    /// Returns an error after cancellation has been requested.
    #[track_caller]
    pub fn bail_if_cancelled(&self) -> eyre::Result<()> {
        if self.is_cancelled() {
            let reason = self
                .cancellation_reason()
                .unwrap_or_else(|| "Operation cancelled".to_string());
            eyre::bail!("{reason}");
        }
        Ok(())
    }
}

impl fmt::Debug for CancellationToken {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("CancellationToken")
            .field("is_cancelled", &self.is_cancelled())
            .finish_non_exhaustive()
    }
}
