use crate::cancellation::CancellationToken;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use tracing::Id;
use tracing::Metadata;
use tracing::Subscriber;
use tracing_subscriber::Layer;
use tracing_subscriber::layer::Context;
use tracing_subscriber::registry::LookupSpan;

#[derive(Debug)]
pub struct StopAfterLayer {
    spec: StopAfterSpec,
    cancellation_token: CancellationToken,
    triggered: AtomicBool,
}

impl StopAfterLayer {
    #[must_use]
    pub fn new(raw: impl Into<String>, cancellation_token: CancellationToken) -> Self {
        Self {
            spec: StopAfterSpec::parse(raw),
            cancellation_token,
            triggered: AtomicBool::new(false),
        }
    }
}

impl<S> Layer<S> for StopAfterLayer
where
    S: Subscriber + for<'lookup> LookupSpan<'lookup>,
{
    fn on_close(&self, id: Id, ctx: Context<'_, S>) {
        if self.triggered.load(Ordering::Acquire) {
            return;
        }

        let Some(span) = ctx.span(&id) else {
            return;
        };

        if !self.spec.matches(span.metadata()) {
            return;
        }

        if self.triggered.swap(true, Ordering::AcqRel) {
            return;
        }

        self.cancellation_token.request_cancel(format!(
            "Operation cancelled after --stop-after matched tracing span `{}`",
            span.metadata().name()
        ));
    }
}

#[derive(Debug)]
struct StopAfterSpec {
    span_name: Option<String>,
    location: Option<StopAfterLocation>,
}

impl StopAfterSpec {
    fn parse(raw: impl Into<String>) -> Self {
        let raw = raw.into();
        let normalized = normalize_copied_value(&raw);
        let location = parse_location(&normalized).or_else(|| parse_location(&raw));
        let span_name = if location.is_some() {
            None
        } else {
            let name = strip_tracy_fields(&normalized).trim();
            (!name.is_empty()).then(|| name.to_string())
        };

        Self {
            span_name,
            location,
        }
    }

    fn matches(&self, metadata: &Metadata<'_>) -> bool {
        if self
            .span_name
            .as_deref()
            .is_some_and(|span_name| metadata.name() == span_name)
        {
            return true;
        }

        let Some(location) = &self.location else {
            return false;
        };
        let Some(file) = metadata.file() else {
            return false;
        };
        if metadata.line() != Some(location.line) {
            return false;
        }

        let metadata_file = normalize_path(file);
        metadata_file.ends_with(&location.file) || location.file.ends_with(&metadata_file)
    }
}

#[derive(Debug)]
struct StopAfterLocation {
    file: String,
    line: u32,
}

fn normalize_copied_value(raw: &str) -> String {
    raw.trim()
        .trim_matches('`')
        .trim_matches('"')
        .trim_matches('\'')
        .trim()
        .to_string()
}

fn strip_tracy_fields(raw: &str) -> &str {
    raw.split_once('{').map_or(raw, |(name, _fields)| name)
}

fn parse_location(raw: &str) -> Option<StopAfterLocation> {
    let raw = normalize_copied_value(strip_tracy_fields(raw));
    let (file, line) = raw.rsplit_once(':')?;
    let line = line.parse::<u32>().ok()?;
    let file = normalize_path(file);
    (!file.is_empty()).then_some(StopAfterLocation { file, line })
}

fn normalize_path(path: &str) -> String {
    path.replace('\\', "/").to_ascii_lowercase()
}

#[cfg(test)]
mod tests {
    use super::StopAfterSpec;

    #[test]
    fn parses_copied_tracy_span_name_without_fields() {
        let spec = StopAfterSpec::parse(
            "create_plan_for_target{branch=1.19.2 target=1.19.2 refresh=false}",
        );

        assert_eq!(spec.span_name.as_deref(), Some("create_plan_for_target"));
        assert!(spec.location.is_none());
    }

    #[test]
    fn parses_copied_location() {
        let spec = StopAfterSpec::parse(r"src\jar_build\engine.rs:3044");

        let location = spec.location.expect("location should parse");
        assert_eq!(location.file, "src/jar_build/engine.rs");
        assert_eq!(location.line, 3044);
        assert!(spec.span_name.is_none());
    }
}
