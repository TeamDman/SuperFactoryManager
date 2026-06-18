use std::fmt;
use std::sync::Arc;

#[derive(Clone)]
pub struct CancellationToken {
    is_cancelled: Arc<dyn Fn() -> bool + Send + Sync + 'static>,
}

impl CancellationToken {
    #[must_use]
    pub fn new(is_cancelled: impl Fn() -> bool + Send + Sync + 'static) -> Self {
        Self {
            is_cancelled: Arc::new(is_cancelled),
        }
    }

    #[must_use]
    pub fn process() -> Self {
        Self::new(super::is_cancelled)
    }

    #[must_use]
    pub fn is_cancelled(&self) -> bool {
        (self.is_cancelled)()
    }

    /// Return an error if cancellation has been requested.
    ///
    /// # Errors
    ///
    /// Returns an error after cancellation has been requested.
    #[track_caller]
    pub fn bail_if_cancelled(&self) -> eyre::Result<()> {
        if self.is_cancelled() {
            eyre::bail!("Operation cancelled by Ctrl+C");
        }
        Ok(())
    }
}

impl fmt::Debug for CancellationToken {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("CancellationToken").finish_non_exhaustive()
    }
}
