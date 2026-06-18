use crate::cli::jar::BranchSelector;
use crate::jar_build::BuildMode;
use crate::jar_build::BuildOptions;
use crate::jar_build::ErrorAction;
use crate::jar_build::Parallelism;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Options shared by clean-slate jar build and run commands.
#[derive(Facet, Debug, Clone)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "This type is a thin CLI flag container; each bool maps directly to a named flag."
)]
pub struct JarBuildOptionsArgs {
    /// Branch selector to build. Defaults to `core`.
    #[facet(default, args::named)]
    pub branch: BranchSelector,

    /// Ignore reusable SFM-owned cache state and recompute resolved metadata.
    #[facet(default = false, args::named)]
    pub refresh: bool,

    /// Print the reason each graph node is considered dirty.
    #[facet(rename = "explain-rebuild", default = false, args::named)]
    pub explain_rebuild: bool,

    /// Optional path to write the resolved build plan JSON.
    #[facet(rename = "plan-json", default, args::named)]
    pub plan_json: Option<PathBuf>,

    /// Optional Java home to use for tool execution. Defaults to `JAVA_HOME`, then java on PATH.
    #[facet(rename = "java-home", default, args::named)]
    pub java_home: Option<PathBuf>,

    /// Resolve and prepare as much as possible, then skip the final build or launch action.
    #[facet(rename = "dry-run", default = false, args::named)]
    pub dry_run: bool,

    /// Allow bootstrapping missing artifacts from local .m2 or Gradle module caches.
    #[facet(rename = "allow-local-artifact-cache", default = false, args::named)]
    pub allow_local_artifact_cache: bool,

    /// Explicit local artifact sources, such as Maven repository roots, project roots, or build/libs directories.
    #[facet(rename = "artifact-source", default, args::named)]
    pub artifact_sources: Vec<PathBuf>,

    /// Fail if any locked artifact depends on local-only or unknown provenance.
    #[facet(rename = "require-portable-artifacts", default = false, args::named)]
    pub require_portable_artifacts: bool,

    /// Failure behavior for multi-target selectors: `bail` or `continue`.
    #[facet(rename = "error-action", default, args::named)]
    pub error_action: ErrorAction,

    /// Run matching targets in parallel. Bare `--parallel` defaults to 10.
    #[facet(default, args::named)]
    #[expect(
        clippy::option_option,
        reason = "figue uses Option<Option<T>> to model absent, bare, and valued optional-value flags."
    )]
    pub parallel: Option<Option<usize>>,
}

impl JarBuildOptionsArgs {
    pub(crate) fn into_options(self, mode: BuildMode) -> eyre::Result<BuildOptions> {
        Ok(BuildOptions {
            branch: self.branch.into_query()?,
            refresh: self.refresh,
            explain_rebuild: self.explain_rebuild,
            plan_json: self.plan_json,
            java_home: self.java_home,
            dry_run: self.dry_run,
            allow_local_artifact_cache: self.allow_local_artifact_cache,
            artifact_sources: self.artifact_sources,
            require_portable_artifacts: self.require_portable_artifacts,
            error_action: self.error_action,
            parallelism: Parallelism::from_cli(self.parallel)?,
            mode,
        })
    }
}
