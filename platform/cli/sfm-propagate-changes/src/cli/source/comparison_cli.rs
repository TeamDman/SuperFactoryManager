use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::paths::InvocationDirectory;
use crate::source_comparison::preparation_receipt::ComparisonPreparationReceipt;
use crate::source_comparison::prepare;
use crate::source_comparison::request::ComparisonRequest;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::fs::File;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct SourceComparisonArgs {
    /// JSON request selecting two inputs and a byte policy. Relative paths in
    /// the request use the command's invocation directory.
    #[facet(args::named)]
    pub request: PathBuf,
    /// Fresh absolute bundle directory; parent must exist outside input roots.
    #[facet(args::named)]
    pub output: PathBuf,
}
impl SourceComparisonArgs {
    pub(super) fn invoke(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        let invocation_dir = InvocationDirectory::new(invocation_dir)?;
        let mut bytes = Vec::new();
        File::open(invocation_dir.resolve(&self.request)?)?
            .take(1024 * 1024 + 1)
            .read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() <= 1024 * 1024,
            "comparison request exceeds 1 MiB"
        );
        let mut request: ComparisonRequest = facet_json::from_slice(&bytes)?;
        request.left.resolve_root(&invocation_dir)?;
        request.right.resolve_root(&invocation_dir)?;
        let report = prepare(&request, &self.output, cancellation)?;
        Ok(CliOutput::facet(ComparisonPreparationReceipt {
            schema: "sfm:source-comparison-prepared@1".into(),
            output_directory: self.output.to_string_lossy().into_owned(),
            comparison_identity: report.comparison_identity,
            logical_files: report.differences.len(),
            changed_logical_files: report
                .differences
                .iter()
                .filter(|diff| diff.status != "exact")
                .count(),
        }))
    }
}

#[cfg(test)]
mod tests {
    use super::SourceComparisonArgs;
    use crate::cancellation::CancellationToken;
    use crate::source_comparison::input::ComparisonInput;
    use crate::source_comparison::request::ComparisonRequest;
    use std::fs;

    #[test]
    fn comparison_resolves_relative_request_and_inputs_from_invocation() -> eyre::Result<()> {
        let invocation = tempfile::tempdir()?;
        for (side, bytes) in [("left", b"left\n".as_slice()), ("right", b"right\n")] {
            let root = invocation.path().join(side);
            fs::create_dir(&root)?;
            fs::write(root.join("example.txt"), bytes)?;
        }
        let request = ComparisonRequest {
            schema: "sfm:source-comparison-request@1".to_owned(),
            left: ComparisonInput::Directory {
                root: "left".to_owned(),
            },
            right: ComparisonInput::Directory {
                root: "right".to_owned(),
            },
            policy: Default::default(),
        };
        fs::write(
            invocation.path().join("request.json"),
            facet_json::to_vec(&request)?,
        )?;
        let output = invocation.path().join("comparison");
        SourceComparisonArgs {
            request: "request.json".into(),
            output: output.clone(),
        }
        .invoke(&CancellationToken::new(), invocation.path())?;
        assert_eq!(fs::read(output.join("left/example.txt"))?, b"left\n");
        assert_eq!(fs::read(output.join("right/example.txt"))?, b"right\n");
        Ok(())
    }
}
