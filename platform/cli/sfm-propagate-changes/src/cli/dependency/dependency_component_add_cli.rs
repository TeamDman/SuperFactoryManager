use super::DependencyAddArgs;
use super::dependency_add_cli::add_component;
use super::dependency_add_cli::http_fetcher;
use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencyComponentAddArgs {
    /// Existing logical dependency ID.
    #[facet(args::positional)]
    pub dependency: String,
    /// Stable component ID.
    #[facet(args::positional)]
    pub component: String,
    /// Branch selector to update. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Exact Maven coordinate (`group:artifact:version[:classifier][@extension]`).
    #[facet(args::named)]
    pub maven: String,
    /// Semantic scope. Repeat for every required scope.
    #[facet(args::named)]
    pub(crate) scope: Vec<DependencyScopeV3>,
    /// Configured repository ID. When omitted, each configured repository is tried.
    #[facet(default, args::named)]
    pub repository: Option<String>,
    /// Artifact treatment. Defaults to loader-managed-mod.
    #[facet(default, args::named)]
    pub(crate) artifact_treatment: Option<ArtifactTreatmentV3>,
}

impl DependencyComponentAddArgs {
    /// # Errors
    ///
    /// Returns an error when resolution, validation, cache writing, or lockfile writing fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let inventory = load_inventory(self.branch.clone(), cache_home)?;
        let add_args = DependencyAddArgs {
            id: self.dependency,
            branch: self.branch,
            maven: Some(self.maven),
            curseforge_project: None,
            curseforge_file: None,
            curseforge_api_key: None,
            curseforge_token: None,
            curseforge_op_secret: None,
            scope: self.scope,
            repository: self.repository,
            artifact_treatment: self.artifact_treatment,
            display_name: None,
            project_url: None,
            notes: None,
        };
        let report = add_component(
            inventory,
            &add_args,
            &self.component,
            cancellation_token,
            &http_fetcher()?,
        )?;
        stdout_line(format!(
            "Added {}/{}: {} from {} ({})",
            report.dependency_id,
            report.component_id,
            report.coordinate,
            report.repository_id,
            report.hash
        ))?;
        Ok(())
    }
}

#[cfg(test)]
#[path = "dependency_component_add_tests.rs"]
mod tests;
