#[derive(Clone, Debug, Default, facet::Facet)]
pub struct JunitSummary {
    pub tests_found: u64,
    pub tests_started: u64,
    pub tests_succeeded: u64,
    pub tests_failed: u64,
    pub tests_skipped: u64,
    pub tests_aborted: u64,
    pub containers_found: u64,
    pub containers_failed: u64,
}
