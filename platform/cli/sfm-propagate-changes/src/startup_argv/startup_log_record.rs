use super::startup_arguments::StartupArguments;
use facet::Facet;

#[derive(Debug, Facet)]
pub(super) struct StartupLogRecord {
    pub timestamp: String,
    pub level: String,
    pub fields: StartupArguments,
    pub target: String,
    pub filename: Option<String>,
    pub line_number: Option<u32>,
}
