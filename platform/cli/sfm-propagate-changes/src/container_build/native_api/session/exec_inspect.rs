use facet::Facet;
#[derive(Debug, Facet)]
pub struct ExecInspect {
    #[facet(rename = "Running")]
    pub running: bool,
    #[facet(rename = "ExitCode")]
    pub exit_code: i64,
}
