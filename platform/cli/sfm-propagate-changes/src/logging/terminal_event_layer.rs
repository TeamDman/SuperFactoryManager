use std::fmt::Debug;
use std::io::Write as _;
use tracing::Event;
use tracing::Id;
use tracing::Subscriber;
use tracing::field::Field;
use tracing::field::Visit;
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
        let span_fields = TerminalSpanFields::from_scope(&ctx, event);
        let rendered = RenderedTerminalEvent::new(event, fields, span_fields);
        let mut stderr = std::io::stderr().lock();
        let _ = writeln!(stderr, "{rendered}");
    }
}

#[derive(Debug, Default, Clone)]
struct TerminalSpanFields {
    branch: Option<String>,
    source: Option<String>,
    process: Option<String>,
    stream: Option<String>,
}

impl TerminalSpanFields {
    fn from_captured(fields: &CapturedFields) -> Self {
        Self {
            branch: fields.branch.clone(),
            source: fields.source.clone(),
            process: fields.process.clone(),
            stream: fields.stream.clone(),
        }
    }

    fn from_scope<S>(ctx: &Context<'_, S>, event: &Event<'_>) -> Self
    where
        S: Subscriber + for<'lookup> LookupSpan<'lookup>,
    {
        let mut output = Self::default();
        let Some(scope) = ctx.event_scope(event) else {
            return output;
        };

        for span in scope.from_root() {
            let extensions = span.extensions();
            if let Some(fields) = extensions.get::<Self>() {
                output.update_from_span(fields);
            }
        }
        output
    }

    fn update_from_captured(&mut self, fields: &CapturedFields) {
        self.branch.clone_from(&fields.branch);
        self.source.clone_from(&fields.source);
        self.process.clone_from(&fields.process);
        self.stream.clone_from(&fields.stream);
    }

    fn update_from_span(&mut self, fields: &Self) {
        if fields.branch.is_some() {
            self.branch.clone_from(&fields.branch);
        }
        if fields.source.is_some() {
            self.source.clone_from(&fields.source);
        }
        if fields.process.is_some() {
            self.process.clone_from(&fields.process);
        }
        if fields.stream.is_some() {
            self.stream.clone_from(&fields.stream);
        }
    }
}

#[derive(Debug, Default)]
struct CapturedFields {
    branch: Option<String>,
    source: Option<String>,
    process: Option<String>,
    stream: Option<String>,
    message: Option<String>,
    extra_fields: Vec<(String, String)>,
}

impl CapturedFields {
    fn record_value(&mut self, field: &Field, value: String) {
        match field.name() {
            "branch" => self.branch = Some(value),
            "source" => self.source = Some(value),
            "process" => self.process = Some(value),
            "stream" => self.stream = Some(value),
            "message" => self.message = Some(value),
            name => self.extra_fields.push((name.to_string(), value)),
        }
    }
}

impl Visit for CapturedFields {
    fn record_debug(&mut self, field: &Field, value: &dyn Debug) {
        self.record_value(field, format!("{value:?}"));
    }

    fn record_str(&mut self, field: &Field, value: &str) {
        self.record_value(field, value.to_string());
    }

    fn record_bool(&mut self, field: &Field, value: bool) {
        self.record_value(field, value.to_string());
    }

    fn record_i64(&mut self, field: &Field, value: i64) {
        self.record_value(field, value.to_string());
    }

    fn record_u64(&mut self, field: &Field, value: u64) {
        self.record_value(field, value.to_string());
    }
}

#[derive(Debug)]
struct RenderedTerminalEvent {
    prefix: String,
    message: String,
    extra_fields: Vec<(String, String)>,
}

impl RenderedTerminalEvent {
    fn new(event: &Event<'_>, fields: CapturedFields, span_fields: TerminalSpanFields) -> Self {
        let source = fields
            .source
            .or(span_fields.source)
            .unwrap_or_else(|| "rust".to_string());
        let process = fields
            .process
            .or(span_fields.process)
            .unwrap_or_else(|| "sfm".to_string());
        let stream = fields
            .stream
            .or(span_fields.stream)
            .unwrap_or_else(|| "stdout".to_string());
        let branch = fields.branch.or(span_fields.branch);
        let prefix = render_prefix(event, branch.as_deref(), &source, &process, &stream);
        Self {
            prefix,
            message: fields
                .message
                .unwrap_or_else(|| event.metadata().name().to_string()),
            extra_fields: fields.extra_fields,
        }
    }
}

impl std::fmt::Display for RenderedTerminalEvent {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "[{}] {}", self.prefix, self.message)?;
        for (name, value) in &self.extra_fields {
            write!(f, " {name}={value}")?;
        }
        Ok(())
    }
}

fn render_prefix(
    event: &Event<'_>,
    branch: Option<&str>,
    source: &str,
    process: &str,
    stream: &str,
) -> String {
    let mut parts = Vec::new();
    if let Some(branch) = branch {
        parts.push(branch.to_string());
    }
    parts.push(source_label(source).to_string());
    if should_show_process(source, process) {
        parts.push(process.to_string());
    }
    if stream == "stderr" {
        parts.push("stderr".to_string());
    }
    let level = event.metadata().level().as_str();
    if level != "INFO" {
        parts.push(level.to_ascii_lowercase());
    }
    parts.join(" ")
}

fn should_show_process(source: &str, process: &str) -> bool {
    !process.is_empty()
        && !matches!(
            (source, process),
            ("rust", "sfm") | ("minecraft", "minecraft")
        )
}

fn source_label(source: &str) -> &str {
    match source {
        "minecraft" => "mc",
        "java-tool" => "java",
        other => other,
    }
}
