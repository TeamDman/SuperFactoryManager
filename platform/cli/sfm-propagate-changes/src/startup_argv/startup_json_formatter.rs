use super::startup_arguments::StartupArguments;
use super::startup_event_visitor::StartupEventVisitor;
use super::startup_log_record::StartupLogRecord;
use tracing::Event;
use tracing::Subscriber;
use tracing_subscriber::fmt::FmtContext;
use tracing_subscriber::fmt::FormatEvent;
use tracing_subscriber::fmt::FormatFields;
use tracing_subscriber::fmt::format::Format;
use tracing_subscriber::fmt::format::Json;
use tracing_subscriber::fmt::format::Writer;
use tracing_subscriber::registry::LookupSpan;

/// Only the reserved startup event has an explicitly typed JSON payload.
#[derive(Debug)]
pub(crate) struct StartupJsonFormatter {
    fallback: Format<Json>,
}

impl StartupJsonFormatter {
    pub(crate) fn new(include_target: bool) -> Self {
        Self {
            fallback: tracing_subscriber::fmt::format()
                .json()
                .with_file(true)
                .with_line_number(true)
                .with_target(include_target),
        }
    }
}

impl<S, N> FormatEvent<S, N> for StartupJsonFormatter
where
    S: Subscriber + for<'lookup> LookupSpan<'lookup>,
    N: for<'writer> FormatFields<'writer> + 'static,
{
    fn format_event(
        &self,
        ctx: &FmtContext<'_, S, N>,
        mut writer: Writer<'_>,
        event: &Event<'_>,
    ) -> std::fmt::Result {
        if event.metadata().target() != super::TARGET {
            return self.fallback.format_event(ctx, writer, event);
        }
        let mut visitor = StartupEventVisitor::default();
        event.record(&mut visitor);
        let Some(encoded) = visitor.encoded else {
            return Err(std::fmt::Error);
        };
        // Decode through Facet rather than splicing unchecked JSON into an event.
        let fields: StartupArguments =
            facet_json::from_str(&encoded).map_err(|_redacted_encoding_error| std::fmt::Error)?;
        let record = StartupLogRecord {
            timestamp: chrono::Utc::now().to_rfc3339(),
            level: event.metadata().level().to_string(),
            fields,
            target: event.metadata().target().to_owned(),
            filename: event.metadata().file().map(str::to_owned),
            line_number: event.metadata().line(),
        };
        let encoded =
            facet_json::to_string(&record).map_err(|_redacted_encoding_error| std::fmt::Error)?;
        writeln!(writer, "{encoded}")
    }
}
