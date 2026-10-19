use super::compilation_output_entry::CompilationOutputEntry;
use facet::Facet;

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
pub(super) struct CompilationOutputManifest {
    pub schema: String,
    pub input_fingerprint: String,
    pub outputs: Vec<CompilationOutputEntry>,
}
