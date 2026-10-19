use facet::Facet;

#[derive(Debug, Facet)]
pub(crate) struct ExecutionReceipt {
    pub schema: String,
    pub preparation_identity: String,
    pub projection_key: String,
    pub cli_sha256: String,
    pub java_identity: String,
    pub filter: Option<String>,
    pub summary: String,
    pub exit_status: String,
    pub raw_stdout_sha256: String,
    pub raw_stderr_sha256: String,
    pub runtime_classpath_sha256: String,
    pub argfile_sha256: String,
    pub scope: String,
}
