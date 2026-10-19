use facet::Facet;
#[derive(Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Docker exec wire format defines independent boolean flags"
)]
pub struct ExecCreate {
    #[facet(rename = "Cmd")]
    pub command: Vec<String>,
    #[facet(rename = "User")]
    pub user: String,
    #[facet(rename = "Privileged")]
    pub privileged: bool,
    #[facet(rename = "Tty")]
    pub tty: bool,
    #[facet(rename = "AttachStdin")]
    pub stdin: bool,
    #[facet(rename = "AttachStdout")]
    pub stdout: bool,
    #[facet(rename = "AttachStderr")]
    pub stderr: bool,
}
