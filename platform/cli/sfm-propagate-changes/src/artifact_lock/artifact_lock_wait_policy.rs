use std::time::Duration;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ArtifactLockWaitPolicy {
    pub retry_interval: Duration,
    pub log_interval: Duration,
}

impl ArtifactLockWaitPolicy {
    #[must_use]
    pub const fn new(retry_interval: Duration, log_interval: Duration) -> Self {
        Self {
            retry_interval,
            log_interval,
        }
    }
}

impl Default for ArtifactLockWaitPolicy {
    fn default() -> Self {
        Self {
            retry_interval: Duration::from_millis(250),
            log_interval: Duration::from_secs(10),
        }
    }
}
