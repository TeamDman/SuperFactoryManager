use crate::branch_targets::WorktreeTarget;
use crate::jar_build::json_path::JsonOptionalPath;
use crate::jar_build::json_path::JsonPath;
use crate::prism::meta::PrismLoaderSelection;
use crate::prism::meta::resolve_loader_component;
use crate::prism::meta::resolve_lwjgl_component;
use eyre::Context;
use facet::Facet;
use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct PrismInstancePlan {
    pub branch: String,
    pub minecraft_version: String,
    pub loader: PrismLoaderComponent,
    pub java: PrismJavaPlan,
    pub lwjgl: Option<PrismComponent>,
}

#[derive(Clone, Debug)]
pub struct PrismLoaderReport {
    pub branch: String,
    pub minecraft_version: String,
    pub loader_uid: String,
    pub loader_name: String,
    pub pinned: String,
    pub recommended: Option<String>,
    pub latest: Option<String>,
    pub selected: String,
    pub java_major: u32,
    pub java_path: PathBuf,
}

#[derive(Clone, Debug)]
pub struct PrismLoaderComponent {
    pub cached_name: String,
    pub uid: String,
    pub version: String,
    pub pinned_version: String,
    pub recommended_version: Option<String>,
    pub latest_version: Option<String>,
}

#[derive(Clone, Debug)]
pub struct PrismJavaPlan {
    pub executable: PathBuf,
    pub home: Option<PathBuf>,
    pub major_version: u32,
    pub version_output: String,
}

#[derive(Clone, Debug)]
pub struct PrismComponent {
    pub cached_name: String,
    pub uid: String,
    pub version: String,
    pub dependency_only: bool,
}

#[derive(Debug, Facet)]
struct LastBuildPlan {
    branch_name: String,
    minecraft_version: String,
    java: LastBuildJavaPlan,
    loader_toolchain: LastBuildLoaderToolchainPlan,
}

#[derive(Debug, Facet)]
struct LastBuildJavaPlan {
    #[facet(proxy = JsonPath)]
    executable: PathBuf,
    #[facet(default)]
    #[facet(proxy = JsonOptionalPath)]
    home: Option<PathBuf>,
    version_output: String,
    major_version: u32,
}

#[derive(Debug, Facet)]
struct LastBuildLoaderToolchainPlan {
    base_coordinate: String,
}

/// Build the Prism instance plan for a worktree from the last clean-slate build plan.
///
/// # Errors
///
/// Returns an error if the worktree has no build plan or Prism metadata cannot be resolved.
pub fn instance_plan_for_target(
    target: &WorktreeTarget,
    loader_selection: PrismLoaderSelection,
) -> eyre::Result<PrismInstancePlan> {
    let plan = read_last_build_plan(target.worktree_path.as_path())?;
    let loader = resolve_loader_component(
        &plan.minecraft_version,
        &plan.loader_toolchain.base_coordinate,
        loader_selection,
    )?;
    let lwjgl = resolve_lwjgl_component(&plan.minecraft_version)?;
    Ok(PrismInstancePlan {
        branch: plan.branch_name,
        minecraft_version: plan.minecraft_version,
        loader,
        java: PrismJavaPlan {
            executable: plan.java.executable,
            home: plan.java.home,
            major_version: plan.java.major_version,
            version_output: plan.java.version_output,
        },
        lwjgl,
    })
}

/// Resolve loader report rows for the selected worktree.
///
/// # Errors
///
/// Returns an error if build plans or Prism metadata cannot be read.
pub fn loader_report_for_target(
    target: &WorktreeTarget,
    loader_selection: PrismLoaderSelection,
) -> eyre::Result<PrismLoaderReport> {
    let plan = instance_plan_for_target(target, loader_selection)?;
    Ok(PrismLoaderReport {
        branch: plan.branch,
        minecraft_version: plan.minecraft_version,
        loader_uid: plan.loader.uid,
        loader_name: plan.loader.cached_name,
        pinned: plan.loader.pinned_version,
        recommended: plan.loader.recommended_version,
        latest: plan.loader.latest_version,
        selected: plan.loader.version,
        java_major: plan.java.major_version,
        java_path: plan.java.executable,
    })
}

fn read_last_build_plan(worktree_path: &Path) -> eyre::Result<LastBuildPlan> {
    let plan_path = worktree_path
        .join("platform")
        .join("minecraft")
        .join("build")
        .join("sfm-toolchain")
        .join("state")
        .join("last-plan.json");
    let content = std::fs::read_to_string(&plan_path)
        .wrap_err_with(|| format!("Failed to read build plan: {}", plan_path.display()))?;
    facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse build plan: {}", plan_path.display()))
}
