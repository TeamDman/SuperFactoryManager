use super::BuildOptions;
use super::SourceCatalogQuery;
use crate::cancellation::CancellationToken;
use std::path::PathBuf;

#[derive(Debug)]
enum SourceCatalogTarget {
    Branch(BuildOptions),
    GeneratedProject(PathBuf),
}

#[derive(Debug)]
pub struct SourceCatalogCommand {
    target: SourceCatalogTarget,
    query: SourceCatalogQuery,
    cancellation_token: CancellationToken,
}

impl SourceCatalogCommand {
    #[must_use]
    pub fn new(
        options: BuildOptions,
        query: SourceCatalogQuery,
        cancellation_token: CancellationToken,
    ) -> Self {
        Self {
            target: SourceCatalogTarget::Branch(options),
            query,
            cancellation_token,
        }
    }

    #[must_use]
    pub fn new_project(
        project_root: PathBuf,
        query: SourceCatalogQuery,
        cancellation_token: CancellationToken,
    ) -> Self {
        Self {
            target: SourceCatalogTarget::GeneratedProject(project_root),
            query,
            cancellation_token,
        }
    }

    /// # Errors
    ///
    /// Returns an error if the selected branch or generated project cannot be catalogued.
    pub fn invoke(self) -> eyre::Result<()> {
        match self.target {
            SourceCatalogTarget::Branch(options) => super::engine::invoke_source_catalog(
                &options,
                &self.query,
                &self.cancellation_token,
            ),
            SourceCatalogTarget::GeneratedProject(project_root) => {
                super::engine::invoke_project_source_catalog(
                    &project_root,
                    &self.query,
                    &self.cancellation_token,
                )
            }
        }
    }
}
