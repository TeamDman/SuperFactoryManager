use crate::cancellation::CancellationToken;
use std::time::Duration;

#[derive(Clone, Debug)]
pub struct ArtifactLockWaitPolicy {
    pub retry_interval: Duration,
    pub log_interval: Duration,
    pub max_wait: Duration,
    pub cancellation_token: Option<CancellationToken>,
}

impl ArtifactLockWaitPolicy {
    #[must_use]
    pub const fn new(retry_interval: Duration, log_interval: Duration) -> Self {
        Self {
            retry_interval,
            log_interval,
            max_wait: Duration::from_mins(15),
            cancellation_token: None,
        }
    }

    #[must_use]
    pub const fn with_max_wait(mut self, max_wait: Duration) -> Self {
        self.max_wait = max_wait;
        self
    }

    #[must_use]
    pub fn with_cancellation(mut self, cancellation_token: CancellationToken) -> Self {
        self.cancellation_token = Some(cancellation_token);
        self
    }

    /// # Errors
    ///
    /// Returns an error when cancellation has been requested.
    pub fn bail_if_cancelled(&self) -> eyre::Result<()> {
        if let Some(cancellation_token) = &self.cancellation_token {
            cancellation_token.bail_if_cancelled()?;
        }
        Ok(())
    }
}

impl Default for ArtifactLockWaitPolicy {
    fn default() -> Self {
        Self {
            retry_interval: Duration::from_millis(250),
            log_interval: Duration::from_secs(10),
            max_wait: Duration::from_mins(15),
            cancellation_token: None,
        }
    }
}
