use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::release_report::request::ReleaseReportRequest;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Render a local, single HTML release report from explicit reviewed evidence.
#[derive(Debug, Facet)]
pub struct ReleaseReportArgs {
    /// Typed sfm:release-report-input@1 JSON; no automatic evidence acquisition.
    #[facet(args::named)]
    pub manifest: PathBuf,
    /// Root containing hash-bound PNGs named by relative manifest paths.
    #[facet(args::named)]
    pub asset_root: PathBuf,
    /// Fresh HTML destination; existing files are never overwritten.
    #[facet(args::named)]
    pub output: PathBuf,
}
impl ReleaseReportArgs {
    pub(super) fn invoke(self, cancellation: &CancellationToken) -> eyre::Result<CliOutput> {
        let request = ReleaseReportRequest {
            manifest: self.manifest,
            asset_root: self.asset_root,
            output: self.output,
        };
        crate::release_report::render(&request, cancellation).map(CliOutput::facet)
    }
}
