use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::catalog_release::CatalogReleasePlan;
use crate::source_projection::catalog_release::CatalogReleaseRequest;
use crate::source_projection::catalog_release::CatalogReleaseVersion;
use eyre::Result;
use eyre::WrapErr;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct CatalogReleasePlanArgs {
    /// Absolute or non-traversing relative catalog root; otherwise discover the containing checkout.
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,
    /// One exact catalog key; wildcards and branch/global-root fallback are rejected.
    #[facet(args::named)]
    pub projection: String,
    /// Literal major.minor.patch; the matching `release_version_*` feature must be enabled.
    #[facet(args::named)]
    pub expected_mod_version: String,
}

impl CatalogReleasePlanArgs {

    pub(super) fn invoke(
        self,
        invocation_dir: &Path,
        cancellation: &CancellationToken,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let request = {
            let version = CatalogReleaseVersion::parse(&self.expected_mod_version)?;
            let repository = match self.repo_root {
                Some(root) if root.is_absolute() => root,
                Some(root) => invocation_dir.join(root),
                None => gix::discover(invocation_dir)
                    .wrap_err("release planning requires a containing Git checkout or --repo-root")?
                    .workdir()
                    .ok_or_else(|| eyre::eyre!("release planning requires a worktree or --repo-root"))?
                    .to_path_buf(),
            };
            CatalogReleaseRequest::new(&repository, self.projection, version)
        }?;
        Ok(CliOutput::facet(CatalogReleasePlan::prepare(
            &request,
            cancellation,
        )?))
    }
}
