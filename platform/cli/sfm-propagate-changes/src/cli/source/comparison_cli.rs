use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_comparison::request::ComparisonRequest;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::io::Read as _;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SourceComparisonArgs {
    /// Facet JSON request selecting exactly two inputs and a byte policy.
    #[facet(args::named)]
    pub request: PathBuf,
    /// Fresh absolute bundle directory; parent must exist outside input roots.
    #[facet(args::named)]
    pub output: PathBuf,
}
impl SourceComparisonArgs {
    pub(super) fn invoke(self, cancellation: &CancellationToken) -> Result<CliOutput> {
        let mut bytes = Vec::new();
        std::fs::File::open(&self.request)?
            .take(1024 * 1024 + 1)
            .read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() <= 1024 * 1024,
            "comparison request exceeds 1 MiB"
        );
        let request: ComparisonRequest = facet_json::from_slice(&bytes)?;
        let report = crate::source_comparison::prepare(&request, &self.output, cancellation)?;
        Ok(CliOutput::facet(
            crate::source_comparison::preparation_receipt::ComparisonPreparationReceipt {
                schema: "sfm:source-comparison-prepared@1".into(),
                output_directory: self.output.to_string_lossy().into_owned(),
                comparison_identity: report.comparison_identity,
                logical_files: report.differences.len(),
                changed_logical_files: report
                    .differences
                    .iter()
                    .filter(|diff| diff.status != "exact")
                    .count(),
            },
        ))
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn compare_prepare_cli_requires_explicit_request_and_output() {
        let parsed = figue::from_slice::<crate::cli::Cli>(&[
            "source",
            "compare-prepare",
            "--request",
            "comparison.json",
            "--output",
            "bundle",
        ])
        .into_result()
        .unwrap()
        .get_silent();
        assert!(matches!(
            parsed.command,
            crate::cli::Command::Source(super::super::SourceArgs {
                command: super::super::SourceCommand::ComparePrepare(_)
            })
        ));
        assert!(
            figue::from_slice::<crate::cli::Cli>(&["source", "compare-prepare"])
                .into_result()
                .is_err()
        );
    }
}
