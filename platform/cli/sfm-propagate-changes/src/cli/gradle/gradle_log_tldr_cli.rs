use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for summarizing captured Gradle logs.
#[derive(Facet, Debug)]
pub struct GradleLogTldrArgs {
    /// Summarize the latest run directory under `cache/gradle-runs`.
    #[facet(args::named, default = false)]
    pub latest: bool,

    /// Run directory to summarize (if set, `--latest` is not required).
    #[facet(default, args::positional)]
    pub path: Option<PathBuf>,
}

impl GradleLogTldrArgs {
    /// # Errors
    ///
    /// Returns an error if the selected log directory cannot be summarized.
    pub fn invoke(self) -> eyre::Result<()> {
        super::gradle_cli::gradle_logs_tldr(self.latest, self.path)
    }
}
