use facet::Facet;
#[derive(Debug, Facet)]
pub struct SessionState {
    #[facet(rename = "Status")]
    pub status: String,
    #[facet(rename = "Running")]
    pub running: bool,
    #[facet(rename = "ExitCode")]
    pub exit_code: i64,
    #[facet(rename = "OOMKilled")]
    pub oom_killed: bool,
}
