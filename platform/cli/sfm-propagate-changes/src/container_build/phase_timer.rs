use std::time::Instant;
/// Timings are diagnostics, never part of a reproducible input identity.
pub(super) struct PhaseTimer {
    name: &'static str,
    started: Instant,
    bytes: u64,
}
impl PhaseTimer {
    pub(super) fn start(name: &'static str, bytes: u64) -> Self {
        Self {
            name,
            started: Instant::now(),
            bytes,
        }
    }
}
impl Drop for PhaseTimer {
    fn drop(&mut self) {
        tracing::info!(
            phase = self.name,
            seconds = self.started.elapsed().as_secs_f64(),
            bytes = self.bytes,
            "container_phase"
        );
    }
}
