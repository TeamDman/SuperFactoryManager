use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_comparison::file_reference_query::FileReferenceQuery;
use crate::source_comparison::input::ComparisonInput;
use crate::source_comparison::snapshot as source_comparison_snapshot;
use crate::source_projection::promotion::validate_relative_path;
use facet::Facet;
use figue::{self as args};
use std::fs::File;
use std::io::Read as _;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SourceReferencesArgs {
    /// Explicit directory, pinned-Git or current-projection input JSON.
    #[facet(args::named)]
    pub input: PathBuf,
    /// Exact portable generated/captured output path, including namespace.
    #[facet(args::named)]
    pub path: String,
}
impl SourceReferencesArgs {
    pub(super) fn invoke(self, cancellation: &CancellationToken) -> eyre::Result<CliOutput> {
        validate_relative_path(&self.path)?;
        let mut raw = Vec::new();
        File::open(&self.input)?
            .take(1024 * 1024 + 1)
            .read_to_end(&mut raw)?;
        eyre::ensure!(
            raw.len() <= 1024 * 1024,
            "reference query input exceeds 1 MiB"
        );
        let input: ComparisonInput = facet_json::from_slice(&raw)?;
        let snapshot = source_comparison_snapshot(&input, cancellation)?;
        Ok(CliOutput::facet(FileReferenceQuery::from_snapshot(
            &snapshot, &self.path,
        )))
    }
}
