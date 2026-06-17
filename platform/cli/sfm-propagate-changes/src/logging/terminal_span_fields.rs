use crate::logging::captured_fields::CapturedFields;
use tracing::Event;
use tracing::Subscriber;
use tracing_subscriber::layer::Context;
use tracing_subscriber::registry::LookupSpan;

#[derive(Debug, Default, Clone)]
pub struct TerminalSpanFields {
    pub branch: Option<String>,
    pub source: Option<String>,
    pub process: Option<String>,
    pub stream: Option<String>,
}

impl TerminalSpanFields {
    pub fn from_captured(fields: &CapturedFields) -> Self {
        Self {
            branch: fields.branch.clone(),
            source: fields.source.clone(),
            process: fields.process.clone(),
            stream: fields.stream.clone(),
        }
    }

    pub fn from_scope<S>(ctx: &Context<'_, S>, event: &Event<'_>) -> Self
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

    pub fn update_from_captured(&mut self, fields: &CapturedFields) {
        self.branch.clone_from(&fields.branch);
        self.source.clone_from(&fields.source);
        self.process.clone_from(&fields.process);
        self.stream.clone_from(&fields.stream);
    }

    pub fn update_from_span(&mut self, fields: &Self) {
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
