use crate::colour::stable_color;
use crate::logging::captured_fields::CapturedFields;
use crate::logging::terminal_hyperlink::TerminalTextExt;
use crate::logging::terminal_hyperlink::vscode_file_uri_for_path;
use crate::logging::terminal_span_fields::TerminalSpanFields;
use color_eyre::owo_colors::OwoColorize;
use std::fmt::Debug;
use std::io::Write;
use std::path::Path;
use tracing::Event;
use tracing::Id;
use tracing::Level;
use tracing::Subscriber;
use tracing::span::Attributes;
use tracing::span::Record;
use tracing_subscriber::Layer;
use tracing_subscriber::layer::Context;
use tracing_subscriber::registry::LookupSpan;

#[derive(Debug, Default)]
pub struct TerminalEventLayer;

impl<S> Layer<S> for TerminalEventLayer
where
    S: Subscriber + for<'lookup> LookupSpan<'lookup>,
{
    fn on_new_span(&self, attrs: &Attributes<'_>, id: &Id, ctx: Context<'_, S>) {
        let Some(span) = ctx.span(id) else {
            return;
        };

        let mut fields = CapturedFields::default();
        attrs.record(&mut fields);
        span.extensions_mut()
            .insert(TerminalSpanFields::from_captured(&fields));
    }

    fn on_record(&self, id: &Id, values: &Record<'_>, ctx: Context<'_, S>) {
        let Some(span) = ctx.span(id) else {
            return;
        };

        let mut fields = CapturedFields::default();
        values.record(&mut fields);
        let mut extensions = span.extensions_mut();
        if let Some(existing) = extensions.get_mut::<TerminalSpanFields>() {
            existing.update_from_captured(&fields);
        } else {
            extensions.insert(TerminalSpanFields::from_captured(&fields));
        }
    }

    fn on_event(&self, event: &Event<'_>, ctx: Context<'_, S>) {
        let mut fields = CapturedFields::default();
        event.record(&mut fields);

        let decorate = {
            use std::io::IsTerminal as _;
            std::io::stderr().is_terminal() && std::env::var_os("NO_COLOR").is_none()
        };
        let event_fields = &fields;
        let prefix = render_prefix(event, &ctx, event_fields, decorate);
        let mut stderr = std::io::stderr().lock();
        let e: std::io::Result<()> = (move || {
            write!(stderr, "[")?;
            prefix(&mut stderr)?;
            write!(stderr, "] ")?;
            write_event_body(&mut stderr, event_fields, event.metadata().name())
        })();
        if let Err(error) = e {
            eprintln!("Failed to write log event to terminal: {error}");
        }
    }
}

fn write_event_body(
    writer: &mut impl Write,
    fields: &CapturedFields,
    fallback_message: &str,
) -> std::io::Result<()> {
    let progress_summary = fields
        .extra_fields
        .iter()
        .find_map(|(name, value)| (name == "progress_summary").then_some(value.as_str()));
    let message = progress_summary
        .or(fields.message.as_deref())
        .unwrap_or(fallback_message);
    writeln!(writer, "{message}")?;
    for (name, value) in &fields.extra_fields {
        if progress_summary.is_some() && is_preparation_progress_field(name) {
            continue;
        }
        writeln!(writer, "  {name}={value}")?;
    }
    Ok(())
}

// These fields duplicate the summary only in preparation progress events.
// Retain projection_key and unrelated fields, including similarly named keys.
fn is_preparation_progress_field(name: &str) -> bool {
    matches!(
        name,
        "phase"
            | "status"
            | "elapsed_ms"
            | "files_processed"
            | "bytes_processed"
            | "total_files"
            | "total_bytes"
            | "files_remaining"
            | "bytes_remaining"
            | "files_per_second"
            | "bytes_per_second"
            | "eta_ms"
            | "eta_scope"
            | "files_summary"
            | "bytes_processed_human"
            | "bytes_remaining_human"
            | "eta_human"
            | "progress_summary"
            | "reason"
    )
}

fn render_prefix<S: Subscriber + for<'lookup> LookupSpan<'lookup>, W: Write>(
    event: &Event<'_>,
    ctx: &Context<'_, S>,
    fields: &CapturedFields,
    decorate: bool,
) -> impl FnOnce(&mut W) -> std::io::Result<()> {
    move |f| {
        let span_fields = TerminalSpanFields::from_scope(ctx, event);

        // BRANCH
        let branch = fields.branch.as_deref().or(span_fields.branch.as_deref());
        if let Some(branch) = branch {
            if decorate {
                f.write_fmt(format_args!("{}", branch.color(stable_color(branch))))?;
            } else {
                f.write_fmt(format_args!("{branch}"))?;
            }
            f.write_all(b" ")?;
        }

        // SOURCE (JAVA, RUST, ETC)
        let source = fields
            .source
            .as_deref()
            .or(span_fields.source.as_deref())
            .unwrap_or("rust");
        let label = {
            match source {
                "minecraft" => "mc",
                "java-tool" => "java",
                other => other,
            }
        };
        let linked_label = event
            .metadata()
            .file()
            .zip(event.metadata().line())
            .map_or_else(
                || label.to_string(),
                |(file, line)| {
                    let uri = vscode_file_uri(file, line);
                    label.hyperlink(&uri)
                },
            );
        if label == "rust" && decorate {
            f.write_fmt(format_args!("{}", linked_label.truecolor(255, 165, 0)))?;
        } else if label == "mc" && decorate {
            f.write_fmt(format_args!("{}", linked_label.truecolor(154, 205, 50)))?;
        } else {
            f.write_fmt(format_args!("{label}"))?;
        }

        // todo(2026-06-17) probably want to fold SOURCE and PROCESS into one unit, requires identifying where logs with these fields are being emitted
        // PROCESS (CLI, MINECRAFT, ETC)
        let process = fields
            .process
            .as_deref()
            .or(span_fields.process.as_deref())
            .unwrap_or("cli");
        let should_show_process = !process.is_empty()
            && !matches!(
                (source, process),
                ("rust", "cli") | ("minecraft", "minecraft")
            );
        if should_show_process {
            f.write_fmt(format_args!(" {process}"))?;
        }

        // TEST
        let test = fields.test.as_deref().or(span_fields.test.as_deref());
        if let Some(test) = test.filter(|test| !test.is_empty()) {
            let test_uri = fields
                .test_uri
                .as_deref()
                .or(span_fields.test_uri.as_deref())
                .filter(|uri| !uri.is_empty());
            let label = test_uri.map_or_else(|| test.to_string(), |uri| test.hyperlink(uri));
            if decorate {
                f.write_fmt(format_args!(" {}", label.color(stable_color(test))))?;
            } else {
                f.write_fmt(format_args!(" {test}"))?;
            }
        }

        // STREAM (STDOUT, STDERR, ETC)
        let stream = fields
            .stream
            .as_deref()
            .or(span_fields.stream.as_deref())
            .unwrap_or("stdout");
        if stream == "stderr" {
            f.write_fmt(format_args!(" stderr"))?;
        }

        // LEVEL (INFO, DEBUG, ETC)
        let level = event.metadata().level();
        match *level {
            Level::TRACE => {
                f.write_fmt(format_args!(" {}", level.as_str().purple()))?;
            }
            Level::DEBUG => {
                f.write_fmt(format_args!(" {}", level.as_str().blue()))?;
            }
            Level::INFO => {}
            Level::WARN => {
                f.write_fmt(format_args!(" {}", level.as_str().yellow()))?;
            }
            Level::ERROR => {
                f.write_fmt(format_args!(" {}", level.as_str().red()))?;
            }
        }
        Ok(())
    }
}

fn vscode_file_uri(file: &str, line: u32) -> String {
    let path = Path::new(file);
    let path = if path.is_absolute() {
        path.to_path_buf()
    } else {
        Path::new(env!("CARGO_MANIFEST_DIR")).join(path)
    };
    vscode_file_uri_for_path(&path, usize::try_from(line).unwrap_or(1), 1)
}

#[cfg(test)]
mod tests {
    use super::vscode_file_uri;
    use super::write_event_body;
    use crate::logging::captured_fields::CapturedFields;
    use std::sync::Arc;
    use std::sync::Mutex;
    use tracing_subscriber::Layer;
    use tracing_subscriber::layer::Context;
    use tracing_subscriber::prelude::*;

    fn render_body(fields: &CapturedFields) -> String {
        let mut output = Vec::new();
        write_event_body(&mut output, fields, "fallback event").unwrap();
        String::from_utf8(output).unwrap()
    }

    #[test]
    fn preparation_summary_replaces_only_its_duplicate_terminal_fields() {
        let summary =
            "Projection preparation progress: read_inputs; 128/256 files; 1 MB remaining; ETA 2s";
        let mut fields = CapturedFields {
            message: Some("Projection preparation progress: read_inputs".to_owned()),
            ..CapturedFields::default()
        };
        for name in [
            "phase",
            "status",
            "elapsed_ms",
            "files_processed",
            "bytes_processed",
            "total_files",
            "total_bytes",
            "files_remaining",
            "bytes_remaining",
            "files_per_second",
            "bytes_per_second",
            "eta_ms",
            "eta_scope",
            "files_summary",
            "bytes_processed_human",
            "bytes_remaining_human",
            "eta_human",
            "reason",
        ] {
            fields
                .extra_fields
                .push((name.to_owned(), "duplicate".to_owned()));
        }
        fields.extra_fields.extend([
            ("progress_summary".to_owned(), summary.to_owned()),
            ("projection_key".to_owned(), "sfm-dev/mc-1.19.2".to_owned()),
            ("detail".to_owned(), "retained".to_owned()),
            (
                "files_remaining_hint".to_owned(),
                "also retained".to_owned(),
            ),
        ]);

        assert_eq!(
            render_body(&fields),
            format!(
                "{summary}\n  projection_key=sfm-dev/mc-1.19.2\n  detail=retained\n  files_remaining_hint=also retained\n"
            )
        );
    }

    #[test]
    fn ordinary_events_keep_their_message_and_all_extra_fields() {
        let mut fields = CapturedFields {
            message: Some("ordinary event".to_owned()),
            extra_fields: vec![
                ("status".to_owned(), "ready".to_owned()),
                ("files_processed".to_owned(), "128".to_owned()),
                ("reason".to_owned(), "retained".to_owned()),
            ],
            ..CapturedFields::default()
        };
        assert_eq!(
            render_body(&fields),
            "ordinary event\n  status=ready\n  files_processed=128\n  reason=retained\n"
        );
        fields.message = None;
        assert_eq!(
            render_body(&fields),
            "fallback event\n  status=ready\n  files_processed=128\n  reason=retained\n"
        );
    }

    #[test]
    fn condensed_terminal_rendering_preserves_captured_numeric_fields() {
        #[derive(Default)]
        struct CaptureEvents(Arc<Mutex<Vec<(CapturedFields, String)>>>);

        impl<S: tracing::Subscriber> Layer<S> for CaptureEvents {
            fn on_event(&self, event: &tracing::Event<'_>, _context: Context<'_, S>) {
                let mut fields = CapturedFields::default();
                event.record(&mut fields);
                let body = render_body(&fields);
                self.0.lock().unwrap().push((fields, body));
            }
        }

        let captured = CaptureEvents::default();
        let events = Arc::clone(&captured.0);
        let subscriber = tracing_subscriber::registry().with(captured);
        tracing::subscriber::with_default(subscriber, || {
            tracing::info!(
                files_processed = 128_u64,
                bytes_processed = 1024_u64,
                bytes_per_second = 512.5_f64,
                eta_ms = Some(2000_u64),
                progress_summary = "read_inputs: 128 files, 1.02 kB, ETA 2s",
                "machine progress event"
            );
        });

        let events = events.lock().unwrap();
        let (fields, body) = &events[0];
        for (name, value) in [
            ("files_processed", "128"),
            ("bytes_processed", "1024"),
            ("bytes_per_second", "512.5"),
            ("eta_ms", "2000"),
        ] {
            assert!(
                fields
                    .extra_fields
                    .iter()
                    .any(|(key, captured)| key == name && captured == value)
            );
        }
        assert_eq!(fields.message.as_deref(), Some("machine progress event"));
        assert_eq!(body, "read_inputs: 128 files, 1.02 kB, ETA 2s\n");
    }

    #[test]
    fn vscode_file_uri_targets_line_and_column() {
        let uri = vscode_file_uri("src/cli/git/status/git_status_cli.rs", 249);

        assert!(uri.starts_with("vscode://file/"));
        assert!(uri.ends_with("/src/cli/git/status/git_status_cli.rs:249:1"));
    }
}
