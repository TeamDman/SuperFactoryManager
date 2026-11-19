use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::BuildSelection;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use crate::paths::AppHome;
use crate::paths::InvocationDirectory;
use crate::source_projection::ProjectionName;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::sfm_repo_path::SfmRepoPath;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

/// Options shared by clean-slate jar build and run commands.
#[derive(Facet, Debug, Clone)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "This type is a thin CLI flag container; each bool maps directly to a named flag."
)]
pub struct JarBuildOptionsArgs {
    /// Projection key or glob from projections.json.
    #[facet(args::named)]
    pub projection: String,

    /// Repository containing the core templates; defaults to the current Git root.
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,

    /// Guarded native-root override; defaults to the selected checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,

    /// Ignore reusable SFM-owned cache state and recompute resolved metadata.
    #[facet(default = false, args::named)]
    pub refresh: bool,

    /// Skip initial comprehensive source checks for development projections.
    #[facet(default = false, args::named)]
    pub skip_source_checks: bool,

    /// Print the reason each graph node is considered dirty.
    #[facet(default = false, args::named)]
    pub explain_rebuild: bool,

    /// Optional path to write the resolved build plan JSON.
    #[facet(default, args::named)]
    pub plan_json: Option<PathBuf>,

    /// Explicit Java home; otherwise select the projection's pinned JBRSDK or legacy exact-major JDK.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,

    /// Resolve and prepare as much as possible, then skip the final build or launch action.
    #[facet(default = false, args::named)]
    pub dry_run: bool,

    /// Allow bootstrapping missing artifacts from local .m2 or Gradle module caches.
    #[facet(default = false, args::named)]
    pub allow_local_artifact_cache: bool,

    /// Explicit local artifact sources, such as Maven repository roots, project roots, or build/libs directories.
    #[facet(default, args::named, args::alias = "artifact-source")]
    pub artifact_sources: Vec<PathBuf>,

    /// Fail if any locked artifact depends on local-only or unknown provenance.
    #[facet(default = false, args::named)]
    pub require_portable_artifacts: bool,

    /// Failure behavior for multi-target selectors: `bail` or `continue`.
    #[facet(default, args::named)]
    pub error_action: ErrorAction,

    /// Run matching targets in parallel. Bare `--parallel` defaults to 10.
    #[facet(default, args::named)]
    pub parallel: Option<Option<usize>>,

    /// Wait for another SFM build/run using the same projection cache instead of failing fast.
    #[facet(default = false, args::named)]
    pub wait_for_build_lock: bool,
}

impl JarBuildOptionsArgs {
    pub(crate) fn into_options(
        self,
        mode: BuildMode,
        app_home: &AppHome,
        invocation_dir: &Path,
    ) -> eyre::Result<BuildOptions> {
        let parallelism = Parallelism::from_cli(self.parallel)?;
        let (repo_root, projections) = self.projection_targets(invocation_dir)?;
        self.build_options(
            mode,
            app_home,
            invocation_dir,
            repo_root,
            projections,
            parallelism,
        )
    }

    pub(crate) fn projection_targets(
        &self,
        invocation_dir: &Path,
    ) -> eyre::Result<(SfmRepoPath, Vec<ProjectionName>)> {
        let catalog = CoreCatalog::load_for_invocation(self.repo_root.as_deref(), invocation_dir)?;
        let projections = catalog
            .catalog
            .matching_glob_projections(&self.projection)?;
        tracing::info!(
            projection_selector = self.projection,
            matched_projections = projections.len(),
            repo_root = %catalog.repo_root.display(),
            "Projection selector resolved"
        );
        Ok((catalog.repo_root, projections))
    }

    pub(crate) fn into_options_for_projections(
        self,
        mode: BuildMode,
        app_home: &AppHome,
        invocation_dir: &Path,
        repo_root: SfmRepoPath,
        projections: Vec<ProjectionName>,
    ) -> eyre::Result<BuildOptions> {
        let parallelism = Parallelism::from_cli(self.parallel)?;
        self.build_options(
            mode,
            app_home,
            invocation_dir,
            repo_root,
            projections,
            parallelism,
        )
    }

    fn build_options(
        self,
        mode: BuildMode,
        app_home: &AppHome,
        invocation_dir: &Path,
        repo_root: SfmRepoPath,
        projections: Vec<ProjectionName>,
        parallelism: Parallelism,
    ) -> eyre::Result<BuildOptions> {
        let directory = InvocationDirectory::new(invocation_dir)?;
        Ok(BuildOptions {
            app_home: app_home.clone(),
            cache_home: None,
            selection: BuildSelection::Projection {
                repo_root,
                projections,
            },
            cache_root: self.cache_root,
            refresh: self.refresh,
            skip_source_checks: self.skip_source_checks,
            explain_rebuild: self.explain_rebuild,
            plan_json: self
                .plan_json
                .as_deref()
                .map(|path| directory.resolve(path))
                .transpose()?,
            output_name: None,
            java_home: self.java_home,
            dry_run: self.dry_run,
            allow_local_artifact_cache: self.allow_local_artifact_cache,
            artifact_sources: self
                .artifact_sources
                .iter()
                .map(|path| directory.resolve(path))
                .collect::<eyre::Result<Vec<_>>>()?,
            require_portable_artifacts: self.require_portable_artifacts,
            error_action: self.error_action,
            parallelism,
            wait_for_build_lock: self.wait_for_build_lock,
            mode,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::core_catalog::write_project_catalog_fixture;

    #[test]
    fn projection_options_resolve_checkout_paths_and_expand_glob() -> eyre::Result<()> {
        let app_home = AppHome(PathBuf::from("fixture-app-home"));
        let base_directory = tempfile::tempdir()?;
        let invocation_dir = base_directory.path();
        let repo = invocation_dir.join("checkout");
        let projection = "sfm-4.35.0-extended/mc-1.19.2";
        write_project_catalog_fixture(&repo, projection, "1.19.2")?;
        let options = JarBuildOptionsArgs {
            projection: "sfm-4.35.0-extended/*".to_owned(),
            repo_root: Some("checkout".into()),
            cache_root: None,
            refresh: false,
            skip_source_checks: false,
            explain_rebuild: false,
            plan_json: Some("reports/plan.json".into()),
            java_home: None,
            dry_run: false,
            allow_local_artifact_cache: false,
            artifact_sources: vec!["local-maven".into()],
            require_portable_artifacts: false,
            error_action: ErrorAction::Bail,
            parallel: None,
            wait_for_build_lock: false,
        }
        .into_options(BuildMode::Build, &app_home, invocation_dir)?;
        assert_eq!(
            options.plan_json,
            Some(invocation_dir.join("reports/plan.json"))
        );
        assert_eq!(
            options.artifact_sources,
            [invocation_dir.join("local-maven")]
        );
        let BuildSelection::Projection {
            repo_root,
            projections,
        } = options.selection
        else {
            panic!("expected projection build options");
        };
        assert_eq!(repo_root, SfmRepoPath::try_from(repo.as_path())?);
        assert_eq!(
            projections
                .iter()
                .map(ProjectionName::as_str)
                .collect::<Vec<_>>(),
            [projection]
        );
        Ok(())
    }
}
