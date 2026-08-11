use std::time::Duration;
use std::time::Instant;

#[derive(Debug)]
pub struct CancellationState {
    cancelled: bool,
    last_ctrl_c: Option<Instant>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CtrlCAction {
    RequestGracefulShutdown,
    ForceExit,
}

impl CancellationState {
    #[must_use]
    pub const fn new() -> Self {
        Self {
            cancelled: false,
            last_ctrl_c: None,
        }
    }

    pub fn record_ctrl_c(&mut self, now: Instant) -> CtrlCAction {
        let force_exit = self
            .last_ctrl_c
            .is_some_and(|last| now.duration_since(last) <= Duration::from_secs(1));
        self.last_ctrl_c = Some(now);
        self.cancelled = true;
        if force_exit {
            CtrlCAction::ForceExit
        } else {
            CtrlCAction::RequestGracefulShutdown
        }
    }

    #[cfg(test)]
    #[must_use]
    pub const fn is_cancelled(&self) -> bool {
        self.cancelled
    }
}

impl Default for CancellationState {
    fn default() -> Self {
        Self::new()
    }
}
