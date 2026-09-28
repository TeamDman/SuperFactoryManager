use crate::cancellation::CancellationToken;
use crate::jar_build::SourceCatalogAction;
use crate::jar_build::SourceCatalogCategory;
use crate::jar_build::SourceCatalogCommand;
use crate::jar_build::SourceCatalogQuery;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

/// Read a static Java catalog from one generated standalone Gradle project.
#[derive(Facet, Debug)]
pub struct ProjectCatalogArgs {
    /// Generated project root containing `.sfm-source-projection-manifest.json`.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Catalog category: test, game-test, or puppet.
    #[facet(args::named)]
    pub category: String,
    /// Show one canonical catalog id instead of listing all entries.
    #[facet(default, args::named)]
    pub id: Option<String>,
}

impl ProjectCatalogArgs {
    /// # Errors
    ///
    /// Returns an error for an invalid category or generated project root.
    pub fn invoke(
        self,
        cancellation_token: CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<()> {
        let category = match self.category.as_str() {
            "test" => SourceCatalogCategory::Test,
            "game-test" => SourceCatalogCategory::GameTest,
            "puppet" => SourceCatalogCategory::Puppet,
            other => eyre::bail!(
                "Unknown project catalog category `{other}`; expected test, game-test, or puppet"
            ),
        };
        let project_root = if self.project_root.is_absolute() {
            self.project_root
        } else {
            invocation_dir.join(self.project_root)
        };
        let action = self
            .id
            .map_or(SourceCatalogAction::List, |id| SourceCatalogAction::Show {
                id,
            });
        SourceCatalogCommand::new_project(
            project_root,
            SourceCatalogQuery { category, action },
            cancellation_token,
        )
        .invoke()
    }
}
