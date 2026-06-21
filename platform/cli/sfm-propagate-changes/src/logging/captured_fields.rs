use std::fmt::Debug;
use tracing::field::Field;
use tracing::field::Visit;

#[derive(Debug, Default)]
pub struct CapturedFields {
    pub branch: Option<String>,
    pub source: Option<String>,
    pub process: Option<String>,
    pub stream: Option<String>,
    pub test: Option<String>,
    pub test_uri: Option<String>,
    pub message: Option<String>,
    pub extra_fields: Vec<(String, String)>,
}

impl CapturedFields {
    fn record_value(&mut self, field: &Field, value: String) {
        match field.name() {
            "branch" => self.branch = Some(value),
            "source" => self.source = Some(value),
            "process" => self.process = Some(value),
            "stream" => self.stream = Some(value),
            "test" => self.test = Some(value),
            "test_uri" => self.test_uri = Some(value),
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
