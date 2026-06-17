use facet::Facet;
use figue as args;

/// Arguments for listing captured Gradle log files.
#[derive(Facet, Debug)]
pub struct GradleLogListArgs {
    /// Inspect the latest run directory under `cache/gradle-runs`.
    #[facet(args::named, default = false)]
    pub latest: bool,
}

impl GradleLogListArgs {
    /// # Errors
    ///
    /// Returns an error if log files cannot be listed.
    pub fn invoke(self) -> eyre::Result<()> {
        super::gradle_cli::gradle_logs_list(self.latest)
    }
}
