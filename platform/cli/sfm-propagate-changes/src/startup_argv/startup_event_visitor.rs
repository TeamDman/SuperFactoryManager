use std::fmt::Debug;
use tracing::field::Field;
use tracing::field::Visit;

#[derive(Debug, Default)]
pub(super) struct StartupEventVisitor {
    pub encoded: Option<String>,
}

impl Visit for StartupEventVisitor {
    fn record_debug(&mut self, _field: &Field, _value: &dyn Debug) {}
    fn record_str(&mut self, field: &Field, value: &str) {
        if field.name() == "startup_arguments" {
            self.encoded = Some(value.to_owned());
        }
    }
}
