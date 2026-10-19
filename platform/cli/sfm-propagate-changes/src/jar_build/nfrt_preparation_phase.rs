//! Wall-duration observations only; instrumentation never reads or hashes inputs.
use eyre::Result;
use std::time::Instant;

pub(crate) struct NfrtPreparationPhase {
    phase: &'static str,
    started: Instant,
    outcome: &'static str,
    work_items: u64,
    work_bytes: u64,
    work_counts_known: bool,
}

impl NfrtPreparationPhase {
    pub(crate) fn start(phase: &'static str) -> Self {
        tracing::info!(
            event = "begin",
            phase,
            rust_pid = std::process::id(),
            measurement = "wall_duration",
            nested = true,
            "nfrt_preparation_phase"
        );
        Self {
            phase,
            started: Instant::now(),
            outcome: "error",
            work_items: 0,
            work_bytes: 0,
            work_counts_known: false,
        }
    }

    pub(crate) fn run<T>(phase: &'static str, operation: impl FnOnce() -> Result<T>) -> Result<T> {
        Self::start(phase).finish(operation())
    }

    pub(crate) fn work(&mut self, items: u64, bytes: u64) {
        self.work_items = items;
        self.work_bytes = bytes;
        self.work_counts_known = true;
    }

    pub(crate) fn success(&mut self) {
        self.outcome = "success";
    }

    pub(crate) fn finish<T>(mut self, result: Result<T>) -> Result<T> {
        if result.is_ok() {
            self.success();
        }
        result
    }

    pub(crate) fn safe_node(node: &str) -> String {
        node.chars()
            .take(128)
            .map(|character| {
                if character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | '-') {
                    character
                } else {
                    '_'
                }
            })
            .collect()
    }
}

impl Drop for NfrtPreparationPhase {
    fn drop(&mut self) {
        let elapsed = self.started.elapsed();
        let elapsed_us = u64::try_from(elapsed.as_micros()).unwrap_or(u64::MAX);
        let whole_milliseconds = u64::try_from(elapsed.as_millis()).unwrap_or(u64::MAX);
        tracing::info!(
            event = "end",
            phase = self.phase,
            outcome = self.outcome,
            elapsed_us,
            elapsed_ms = whole_milliseconds,
            rust_pid = std::process::id(),
            work_items = self.work_items,
            work_bytes = self.work_bytes,
            work_counts_known = self.work_counts_known,
            measurement = "wall_duration",
            nested = true,
            "nfrt_preparation_phase"
        );
    }
}

#[cfg(test)]
mod tests {
    use super::NfrtPreparationPhase;
    use eyre::Result;

    #[test]
    fn nfrt_preparation_phase_preserves_results_and_emits_error_and_work_observations() -> Result<()>
    {
        let log = tempfile::NamedTempFile::new()?;
        let writer = log.reopen()?;
        let subscriber = tracing_subscriber::fmt()
            .json()
            .without_time()
            .with_ansi(false)
            .with_max_level(tracing::Level::INFO)
            .with_writer(move || writer.try_clone().expect("owned fixture writer"))
            .finish();
        tracing::subscriber::with_default(subscriber, || {
            assert_eq!(
                NfrtPreparationPhase::run("fixture.success", || Ok(37)).unwrap(),
                37
            );
            let error =
                NfrtPreparationPhase::run::<()>("fixture.error", || eyre::bail!("original error"))
                    .unwrap_err();
            assert_eq!(error.to_string(), "original error");
            let mut counted = NfrtPreparationPhase::start("fixture.counts");
            counted.work(4, 29);
            counted.success();
        });
        let output = std::fs::read_to_string(log.path())?;
        for expected in [
            "fixture.success",
            "fixture.error",
            "fixture.counts",
            "\"elapsed_us\":",
            "\"elapsed_ms\":",
            "\"outcome\":\"success\"",
            "\"outcome\":\"error\"",
            "\"work_items\":4",
            "\"work_bytes\":29",
            "\"work_counts_known\":true",
            "\"measurement\":\"wall_duration\"",
            "\"nested\":true",
        ] {
            assert!(output.contains(expected), "missing {expected}: {output}");
        }
        for event in output
            .lines()
            .filter(|line| line.contains("\"event\":\"end\""))
        {
            for field in ["elapsed_us", "elapsed_ms"] {
                let key = format!("\"{field}\":");
                let value = event
                    .split_once(&key)
                    .expect("numeric duration field")
                    .1
                    .trim_start();
                assert!(
                    value
                        .chars()
                        .next()
                        .is_some_and(|character| character.is_ascii_digit()),
                    "duration must be a JSON number: {event}"
                );
            }
        }
        Ok(())
    }

    #[test]
    fn nfrt_preparation_safe_node_is_bounded_without_control_characters() {
        assert_eq!(
            NfrtPreparationPhase::safe_node("recompile\n\"/node"),
            "recompile___node"
        );
        assert_eq!(NfrtPreparationPhase::safe_node(&"x".repeat(512)).len(), 128);
    }
}
