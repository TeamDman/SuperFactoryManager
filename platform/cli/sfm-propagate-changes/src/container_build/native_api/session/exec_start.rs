use facet::Facet;
#[derive(Debug, Facet)]
pub struct ExecStart {
    #[facet(rename = "Detach")]
    pub detach: bool,
    #[facet(rename = "Tty")]
    pub tty: bool,
}
