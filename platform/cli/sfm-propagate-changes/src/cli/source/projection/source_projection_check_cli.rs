use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::source_projection::catalog_project_metadata::CatalogProjectMetadata;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::projection_check_result::ProjectionCheckResult;
use eyre::Result;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

/// Compare generated files with the core Liquid inputs. Report every matching
/// projection and exit unsuccessfully if any check fails.
#[derive(Debug, Facet)]
pub struct SourceProjectionCheckArgs {
    /// Repository containing projections.json. Defaults to the containing checkout.
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,
    /// Exact projection name or glob, for example 'sfm-dev/mc-1.19.*'.
    #[facet(args::named)]
    pub projection: String,
}

impl SourceProjectionCheckArgs {
    pub(super) fn invoke_in(
        self,
        invocation_dir: &Path,
        cancellation: &CancellationToken,
    ) -> Result<CliOutput> {
        cancellation.bail_if_cancelled()?;
        let loaded = CoreCatalog::load_for_invocation(self.repo_root.as_deref(), invocation_dir)?;
        let projections = loaded.catalog.matching_names(&self.projection)?;
        let metadata = CatalogProjectMetadata::load(loaded).map_err(|error| format!("{error:#}"));
        let mut results = Vec::with_capacity(projections.len());
        let mut failed = false;
        for projection in projections {
            cancellation.bail_if_cancelled()?;
            let check = match &metadata {
                Ok(metadata) => metadata
                    .check_projection(projection.as_str())
                    .map_err(|error| format!("{error:#}")),
                Err(error) => Err(error.clone()),
            };
            cancellation.bail_if_cancelled()?;
            results.push(match check {
                Ok(()) => ProjectionCheckResult::Current { projection },
                Err(error) => {
                    failed = true;
                    ProjectionCheckResult::Failed { projection, error }
                }
            });
        }
        Ok(CliOutput::facet_with_status(results, u8::from(failed)))
    }
}

#[cfg(test)]
#[path = "source_projection_check_cli_tests.rs"]
mod tests;
