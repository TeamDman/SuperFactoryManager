//! Invocation-local preparation progress, derived only from completed work.

use humansize::DECIMAL;
use humansize::format_size;
use humansize::format_size_i;
use std::time::Duration;
use std::time::Instant;

const FILE_EVENT_INTERVAL: usize = 128;
const TIME_EVENT_INTERVAL: Duration = Duration::from_secs(2);

/// An early return reports an incomplete phase rather than successful completion.
pub(crate) struct ProjectionPreparationPhase<'a> {
    projection_key: &'a str,
    phase: &'static str,
    started: Instant,
    finished: bool,
    files_processed: usize,
    bytes_processed: u64,
    totals: Option<(usize, u64)>,
    last_reported_files: usize,
    last_reported_elapsed: Duration,
}

impl<'a> ProjectionPreparationPhase<'a> {
    pub(crate) fn start(projection_key: &'a str, phase: &'static str) -> Self {
        Self::with_totals(projection_key, phase, None)
    }

    pub(crate) fn start_with_totals(
        projection_key: &'a str,
        phase: &'static str,
        total_files: usize,
        total_bytes: u64,
    ) -> Self {
        Self::with_totals(projection_key, phase, Some((total_files, total_bytes)))
    }

    fn with_totals(
        projection_key: &'a str,
        phase: &'static str,
        totals: Option<(usize, u64)>,
    ) -> Self {
        let progress = Self {
            projection_key,
            phase,
            started: Instant::now(),
            finished: false,
            files_processed: 0,
            bytes_processed: 0,
            totals,
            last_reported_files: 0,
            last_reported_elapsed: Duration::ZERO,
        };
        progress.emit_at("started", None, Duration::ZERO);
        progress
    }

    /// Supply totals from the retained expected input buffers, without another filesystem scan.
    /// Setting a total emits no synthetic work or heartbeat; the next work event includes it.
    pub(crate) fn set_total(&mut self, files: usize, bytes: u64) {
        self.totals = Some((files, bytes));
    }

    pub(crate) fn checked_file(&mut self, bytes: usize) {
        self.checked_file_at(bytes, self.started.elapsed());
    }

    fn checked_file_at(&mut self, bytes: usize, elapsed: Duration) {
        self.files_processed = self.files_processed.saturating_add(1);
        self.bytes_processed = self.bytes_processed.saturating_add(bytes as u64);
        if self
            .files_processed
            .saturating_sub(self.last_reported_files)
            >= FILE_EVENT_INTERVAL
            || elapsed.saturating_sub(self.last_reported_elapsed) >= TIME_EVENT_INTERVAL
        {
            self.emit_at("progress", None, elapsed);
            self.last_reported_files = self.files_processed;
            self.last_reported_elapsed = elapsed;
        }
    }

    pub(crate) fn complete(mut self) {
        self.finished = true;
        let mismatch = self
            .totals
            .is_some_and(|totals| totals != (self.files_processed, self.bytes_processed));
        if mismatch {
            self.emit_at(
                "incomplete",
                Some("completed work does not match supplied totals"),
                self.started.elapsed(),
            );
        } else {
            self.emit_at("completed", None, self.started.elapsed());
        }
    }

    pub(crate) fn complete_with_counts(mut self, files: usize, bytes: u64) {
        self.files_processed = files;
        self.bytes_processed = bytes;
        // Discovery phases learn their totals from these final, explicitly measured counts.
        // Preserve earlier expected totals so a mismatch still reports an incomplete phase.
        self.totals.get_or_insert((files, bytes));
        self.complete();
    }

    /// Explicit opt-outs are observable and do not claim that validation ran successfully.
    pub(crate) fn skip(mut self, reason: &str) {
        self.finished = true;
        self.emit_at("skipped", Some(reason), self.started.elapsed());
    }

    fn emit_at(&self, status: &'static str, reason: Option<&str>, elapsed: Duration) {
        let metrics = ProgressStats::calculate(
            self.files_processed,
            self.bytes_processed,
            self.totals,
            elapsed,
            status,
        );
        let files_summary = metrics.files_summary();
        let bytes_processed_human = format_size(self.bytes_processed, DECIMAL);
        let bytes_remaining_human = metrics
            .bytes_remaining
            .map_or_else(|| "unknown".to_owned(), |bytes| format_size(bytes, DECIMAL));
        let eta_human = metrics.eta_human();
        let elapsed_human = humantime::format_duration(Duration::from_millis(
            u64::try_from(elapsed.as_millis()).unwrap_or(u64::MAX),
        ))
        .to_string();
        let rates_summary = metrics
            .files_per_second
            .zip(metrics.bytes_per_second)
            .map_or_else(String::new, |(files, bytes)| {
                format!("; {files:.1} files/s, {}/s", format_size_i(bytes, DECIMAL))
            });
        let progress_summary = format!(
            "Projection preparation {status}: {} — {files_summary}; {bytes_processed_human} processed, {bytes_remaining_human} remaining; elapsed {elapsed_human}; phase ETA {eta_human}{rates_summary}{}",
            self.phase,
            reason.map_or_else(String::new, |reason| format!("; {reason}")),
        );
        // Option<Value> records Some as its underlying numeric type and omits unknown values.
        tracing::info!(
            projection_key = self.projection_key,
            phase = self.phase,
            status,
            elapsed_ms = elapsed.as_secs_f64() * 1000.0,
            files_processed = self.files_processed,
            bytes_processed = self.bytes_processed,
            total_files = metrics.total_files,
            total_bytes = metrics.total_bytes,
            files_remaining = metrics.files_remaining,
            bytes_remaining = metrics.bytes_remaining,
            files_per_second = metrics.files_per_second,
            bytes_per_second = metrics.bytes_per_second,
            eta_ms = metrics.eta_ms,
            eta_scope = "phase",
            files_summary = %files_summary,
            bytes_processed_human = %bytes_processed_human,
            bytes_remaining_human = %bytes_remaining_human,
            eta_human = %eta_human,
            progress_summary = %progress_summary,
            reason,
            "{progress_summary}"
        );
    }
}

impl Drop for ProjectionPreparationPhase<'_> {
    fn drop(&mut self) {
        if !self.finished {
            self.emit_at("incomplete", None, self.started.elapsed());
        }
    }
}

#[derive(Debug)]
struct ProgressStats {
    files_processed: usize,
    total_files: Option<usize>,
    total_bytes: Option<u64>,
    files_remaining: Option<usize>,
    bytes_remaining: Option<u64>,
    files_per_second: Option<f64>,
    bytes_per_second: Option<f64>,
    eta_ms: Option<u64>,
}

impl ProgressStats {
    #[expect(
        clippy::cast_precision_loss,
        clippy::cast_possible_truncation,
        clippy::cast_sign_loss,
        reason = "Rates and ETA are approximate; finite nonnegative ETA is rounded upward and saturates at u64::MAX"
    )]
    fn calculate(
        files_processed: usize,
        bytes_processed: u64,
        totals: Option<(usize, u64)>,
        elapsed: Duration,
        status: &str,
    ) -> Self {
        let seconds = elapsed.as_secs_f64();
        let measured = seconds > 0.0 && (files_processed > 0 || bytes_processed > 0);
        let files_per_second = measured.then(|| files_processed as f64 / seconds);
        let bytes_per_second = measured.then(|| bytes_processed as f64 / seconds);
        let files_remaining = totals.map(|(files, _)| files.saturating_sub(files_processed));
        let bytes_remaining = totals.map(|(_, bytes)| bytes.saturating_sub(bytes_processed));
        let eta_ms = totals.and_then(|(files, bytes)| {
            // An overrun means the supplied total is stale, not that there is zero work left.
            if files_processed > files || bytes_processed > bytes {
                return None;
            }
            if !matches!(status, "progress" | "completed") {
                return None;
            }
            let remaining_files = files - files_processed;
            let remaining_bytes = bytes - bytes_processed;
            if remaining_files == 0 && remaining_bytes == 0 {
                return Some(0);
            }
            // A dimension with outstanding work and no measured rate cannot supply an ETA.
            let files_eta = remaining_seconds(remaining_files as f64, files_per_second)?;
            let bytes_eta = remaining_seconds(remaining_bytes as f64, bytes_per_second)?;
            let milliseconds = (files_eta.max(bytes_eta) * 1000.0).ceil();
            (milliseconds.is_finite() && milliseconds >= 0.0).then_some(milliseconds as u64)
        });
        Self {
            files_processed,
            total_files: totals.map(|(files, _)| files),
            total_bytes: totals.map(|(_, bytes)| bytes),
            files_remaining,
            bytes_remaining,
            files_per_second,
            bytes_per_second,
            eta_ms,
        }
    }

    fn files_summary(&self) -> String {
        match (self.total_files, self.files_remaining) {
            (Some(total), Some(remaining)) => {
                format!(
                    "{}/{total} files ({remaining} remaining)",
                    self.files_processed
                )
            }
            _ => format!("{} files (total unknown)", self.files_processed),
        }
    }

    fn eta_human(&self) -> String {
        self.eta_ms.map_or_else(
            || "unknown".to_owned(),
            |milliseconds| {
                // Round upward for readable, conservative whole-second terminal estimates.
                humantime::format_duration(Duration::from_secs(milliseconds.div_ceil(1000)))
                    .to_string()
            },
        )
    }
}

fn remaining_seconds(remaining: f64, rate: Option<f64>) -> Option<f64> {
    if remaining == 0.0 {
        return Some(0.0);
    }
    rate.filter(|rate| rate.is_finite() && *rate > 0.0)
        .map(|rate| remaining / rate)
}

#[cfg(test)]
mod preparation_progress_tests {
    use super::*;
    use std::collections::BTreeMap;
    use std::collections::BTreeSet;
    use std::sync::Arc;
    use std::sync::Mutex;
    use tracing::field::Visit;
    use tracing_subscriber::Layer;
    use tracing_subscriber::layer::Context;
    use tracing_subscriber::prelude::*;

    #[derive(Default)]
    struct RecordedEvent {
        values: BTreeMap<String, String>,
        numeric_fields: BTreeSet<String>,
    }

    type RecordedEvents = Arc<Mutex<Vec<RecordedEvent>>>;
    struct CaptureEvents(RecordedEvents);

    impl Visit for RecordedEvent {
        fn record_debug(&mut self, field: &tracing::field::Field, value: &dyn std::fmt::Debug) {
            self.values
                .insert(field.name().to_owned(), format!("{value:?}"));
        }

        fn record_str(&mut self, field: &tracing::field::Field, value: &str) {
            self.values
                .insert(field.name().to_owned(), value.to_owned());
        }

        fn record_u64(&mut self, field: &tracing::field::Field, value: u64) {
            self.numeric_fields.insert(field.name().to_owned());
            self.values
                .insert(field.name().to_owned(), value.to_string());
        }

        fn record_f64(&mut self, field: &tracing::field::Field, value: f64) {
            self.numeric_fields.insert(field.name().to_owned());
            self.values
                .insert(field.name().to_owned(), value.to_string());
        }
    }

    impl<S: tracing::Subscriber> Layer<S> for CaptureEvents {
        fn on_event(&self, event: &tracing::Event<'_>, _context: Context<'_, S>) {
            let mut fields = RecordedEvent::default();
            event.record(&mut fields);
            self.0.lock().unwrap().push(fields);
        }
    }

    fn capture(run: impl FnOnce()) -> RecordedEvents {
        let events = RecordedEvents::default();
        let subscriber = tracing_subscriber::registry().with(CaptureEvents(Arc::clone(&events)));
        tracing::subscriber::with_default(subscriber, run);
        events
    }

    #[test]
    fn preparation_progress_reports_completed_work_in_bounded_batches() {
        let events = capture(|| {
            let mut phase = ProjectionPreparationPhase::start("fixture/mc-1.19.2", "read_inputs");
            for _ in 0..128 {
                phase.checked_file_at(2, Duration::ZERO);
            }
            phase.complete();
        });
        let events = events.lock().unwrap();
        let statuses = events
            .iter()
            .map(|event| event.values["status"].as_str())
            .collect::<Vec<_>>();
        assert_eq!(statuses, ["started", "progress", "completed"]);
        assert_eq!(events[2].values["projection_key"], "fixture/mc-1.19.2");
        assert_eq!(events[2].values["files_processed"], "128");
        assert_eq!(events[2].values["bytes_processed"], "256");
        assert!(events[2].numeric_fields.contains("elapsed_ms"));
    }

    #[test]
    fn preparation_progress_never_calls_an_early_exit_complete() {
        let events = capture(|| {
            let mut phase = ProjectionPreparationPhase::start("fixture/mc-1.19.2", "read_inputs");
            phase.checked_file_at(7, Duration::ZERO);
        });
        let events = events.lock().unwrap();
        let statuses = events
            .iter()
            .map(|event| event.values["status"].as_str())
            .collect::<Vec<_>>();
        assert_eq!(statuses, ["started", "incomplete"]);
        assert_eq!(events[1].values["files_processed"], "1");
        assert_eq!(events[1].values["bytes_processed"], "7");
        assert_eq!(events[1].values["eta_human"], "unknown");
    }

    #[test]
    fn determinate_stats_use_both_real_rates_and_the_slower_remaining_estimate() {
        let metrics = ProgressStats::calculate(
            25,
            250,
            Some((100, 2000)),
            Duration::from_secs(5),
            "progress",
        );
        assert_eq!(metrics.files_remaining, Some(75));
        assert_eq!(metrics.bytes_remaining, Some(1750));
        assert_eq!(metrics.files_per_second, Some(5.0));
        assert_eq!(metrics.bytes_per_second, Some(50.0));
        assert_eq!(metrics.eta_ms, Some(35_000));
        assert_eq!(metrics.eta_human(), "35s");
        assert_eq!(metrics.files_summary(), "25/100 files (75 remaining)");
        let files_slower =
            ProgressStats::calculate(1, 900, Some((10, 1000)), Duration::from_secs(2), "progress");
        assert_eq!(files_slower.eta_ms, Some(18_000));
    }

    #[test]
    fn unknown_totals_and_unmeasured_or_failed_work_do_not_fabricate_eta() {
        for (files, bytes, totals, elapsed, status) in [
            (0, 0, None, Duration::ZERO, "started"),
            (1, 7, None, Duration::from_secs(1), "progress"),
            (0, 0, Some((3, 9)), Duration::from_secs(1), "progress"),
            (1, 3, Some((3, 9)), Duration::ZERO, "progress"),
            (1, 0, Some((3, 9)), Duration::from_secs(1), "progress"),
            (1, 3, Some((3, 9)), Duration::from_secs(1), "incomplete"),
            (1, 3, Some((3, 9)), Duration::from_secs(1), "skipped"),
            (0, 0, Some((0, 0)), Duration::ZERO, "started"),
        ] {
            let metrics = ProgressStats::calculate(files, bytes, totals, elapsed, status);
            assert_eq!(
                metrics.eta_ms, None,
                "{status}: {files} files/{bytes} bytes"
            );
            assert_eq!(metrics.eta_human(), "unknown");
        }
    }

    #[test]
    fn completed_and_zero_byte_totals_have_zero_remaining_and_zero_eta() {
        for (files, bytes, elapsed) in [
            (3, 9, Duration::from_secs(1)),
            (0, 0, Duration::ZERO),
            (3, 0, Duration::from_secs(1)),
        ] {
            let metrics =
                ProgressStats::calculate(files, bytes, Some((files, bytes)), elapsed, "completed");
            assert_eq!(metrics.files_remaining, Some(0));
            assert_eq!(metrics.bytes_remaining, Some(0));
            assert_eq!(metrics.eta_ms, Some(0));
            assert_eq!(metrics.eta_human(), "0s");
        }
    }

    #[test]
    fn stale_totals_saturate_remaining_without_claiming_an_eta() {
        for (files, bytes) in [(4, 9), (3, 10)] {
            let metrics = ProgressStats::calculate(
                files,
                bytes,
                Some((3, 9)),
                Duration::from_secs(1),
                "progress",
            );
            assert_eq!(metrics.files_remaining, Some(0));
            assert_eq!(metrics.bytes_remaining, Some(0));
            assert_eq!(metrics.eta_ms, None);
        }
        let events = capture(|| {
            let mut phase = ProjectionPreparationPhase::start("fixture", "read_inputs");
            phase.set_total(3, 9);
            phase.complete_with_counts(2, 6);
        });
        let events = events.lock().unwrap();
        assert_eq!(events.len(), 2);
        assert_eq!(events[1].values["status"], "incomplete");
        assert_eq!(events[1].values["files_processed"], "2");
        assert_eq!(events[1].values["files_remaining"], "1");
        assert_eq!(events[1].values["bytes_remaining"], "3");
        assert!(events[1].values["reason"].contains("does not match"));
    }

    #[test]
    fn final_explicit_counts_supply_previously_unknown_totals() {
        let events = capture(|| {
            ProjectionPreparationPhase::start("fixture", "discover_inputs")
                .complete_with_counts(2, 4000);
        });
        let events = events.lock().unwrap();
        assert_eq!(events.len(), 2);
        assert!(!events[0].values.contains_key("total_files"));
        let completed = &events[1];
        assert_eq!(completed.values["status"], "completed");
        assert_eq!(completed.values["files_processed"], "2");
        assert_eq!(completed.values["total_files"], "2");
        assert_eq!(completed.values["total_bytes"], "4000");
        assert_eq!(completed.values["files_remaining"], "0");
        assert_eq!(completed.values["bytes_remaining"], "0");
        assert_eq!(completed.values["eta_ms"], "0");
        assert_eq!(completed.values["eta_human"], "0s");
        assert!(completed.numeric_fields.contains("total_files"));
        assert!(completed.numeric_fields.contains("total_bytes"));
    }

    #[test]
    fn time_cadence_requires_a_real_completed_file_and_never_repeats_idle_work() {
        let events = capture(|| {
            let mut phase = ProjectionPreparationPhase::start("fixture", "read_inputs");
            phase.set_total(3, 6);
            phase.checked_file_at(2, Duration::from_secs(2));
            phase.checked_file_at(2, Duration::from_millis(2100));
            phase.checked_file_at(2, Duration::from_secs(4));
            phase.complete();
        });
        let events = events.lock().unwrap();
        let statuses = events
            .iter()
            .map(|event| event.values["status"].as_str())
            .collect::<Vec<_>>();
        assert_eq!(statuses, ["started", "progress", "progress", "completed"]);
        assert_eq!(events[1].values["files_processed"], "1");
        assert_eq!(events[2].values["files_processed"], "3");
    }

    #[test]
    fn skipped_and_unknown_start_have_honest_human_text_and_no_fake_completion() {
        let events = capture(|| {
            let phase = ProjectionPreparationPhase::start("fixture", "validate_inputs");
            phase.skip("validation opted out");
        });
        let events = events.lock().unwrap();
        assert_eq!(events.len(), 2);
        assert!(!events[0].values.contains_key("total_files"));
        assert!(!events[0].values.contains_key("eta_ms"));
        assert!(events[0].values["progress_summary"].contains("0 files (total unknown)"));
        assert_eq!(events[1].values["status"], "skipped");
        assert!(events[1].values["progress_summary"].contains("validation opted out"));
    }

    #[test]
    fn structured_progress_retains_numeric_types_and_human_fields() {
        let events = capture(|| {
            let mut phase = ProjectionPreparationPhase::start_with_totals(
                "fixture",
                "read_inputs",
                256,
                512_000,
            );
            for _ in 0..127 {
                phase.checked_file_at(2000, Duration::ZERO);
            }
            phase.checked_file_at(2000, Duration::from_secs(2));
            phase.skip("fixture ends at a measured progress event");
        });
        let events = events.lock().unwrap();
        assert_eq!(events[0].values["total_files"], "256");
        assert_eq!(events[0].values["total_bytes"], "512000");
        assert!(!events[0].values.contains_key("eta_ms"));
        assert_eq!(events[0].values["eta_scope"], "phase");
        let progress = events
            .iter()
            .find(|event| {
                event.values["status"] == "progress" && event.values["files_processed"] == "128"
            })
            .unwrap();
        for field in [
            "total_files",
            "total_bytes",
            "files_remaining",
            "bytes_remaining",
            "files_per_second",
            "bytes_per_second",
            "eta_ms",
        ] {
            assert!(
                progress.numeric_fields.contains(field),
                "{field} must be numeric"
            );
        }
        assert_eq!(progress.values["files_remaining"], "128");
        assert_eq!(progress.values["bytes_remaining"], "256000");
        assert_eq!(progress.values["eta_ms"], "2000");
        assert_eq!(progress.values["bytes_processed_human"], "256 kB");
        assert_eq!(progress.values["bytes_remaining_human"], "256 kB");
        assert_eq!(progress.values["eta_human"], "2s");
    }

    #[test]
    fn human_eta_rounds_up_and_extreme_counters_remain_finite() {
        let metrics =
            ProgressStats::calculate(2, 2, Some((3, 3)), Duration::from_millis(1), "progress");
        assert_eq!(metrics.eta_ms, Some(1));
        assert_eq!(metrics.eta_human(), "1s");
        let extreme = ProgressStats::calculate(
            1,
            1,
            Some((usize::MAX, u64::MAX)),
            Duration::from_secs(1),
            "progress",
        );
        assert!(extreme.files_per_second.unwrap().is_finite());
        assert!(extreme.bytes_per_second.unwrap().is_finite());
        assert_eq!(extreme.eta_ms, Some(u64::MAX));
    }
}
