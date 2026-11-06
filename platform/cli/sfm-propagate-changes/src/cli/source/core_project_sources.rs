//! Explicit released source view; not source-provider configuration/admission.
use super::CoreProjectCompileArgs;
use super::CoreProjectSelectionArgs;
use super::GenerationPolicy;
use super::project_report;
use super::select_native_sdk;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::jar_build::SourceOutputLayout;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::sync::SyncMode;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct CoreProjectSourcesArgs {
    #[facet(flatten)]
    pub project: CoreProjectSelectionArgs,
    /// Exact reviewed released `NeoForm` or MCP/Forge recipe; never a development profile.
    #[facet(args::named)]
    pub released_recipe: String,
    /// Explicit SDK home; otherwise use the selected lock pin.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
    /// Guarded native root; defaults to this checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
    /// Return the verified source archive or a persistent, verified filetree.
    #[facet(default, args::named)]
    pub layout: SourceOutputLayout,
}

pub(super) fn invoke(
    args: &CoreProjectSourcesArgs,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<CliOutput> {
    cancellation.bail_if_cancelled()?;
    let initial = CoreCatalog::load(args.project.root()?, invocation_dir)?;
    let entry = initial.catalog.entry(&args.project.projection)?;
    ensure!(
        entry.environment
            == crate::source_projection::projection_catalog::ProjectionEnvironment::Release,
        "source project sources requires a released projection"
    );
    crate::jar_build::engine::preflight_named_source_output_recipe(
        entry.target_id()?,
        &entry.minecraft_version,
        &args.released_recipe,
    )?;
    // Reuse the compile route's SDK/root/current-source admission. There are no
    // source-root, extraction-destination, profile or build-output overrides.
    let compile = CoreProjectCompileArgs {
        project: CoreProjectSelectionArgs {
            repo_root: args.project.repo_root.clone(),
            projection: args.project.projection.clone(),
        },
        released_recipe: Some(args.released_recipe.clone()),
        dependency_profile: None,
        java_home: args.java_home.clone(),
        cache_root: args.cache_root.clone(),
        isolated_project_cache: None,
        rebuild_shared_mcp: false,
        output_name: None,
        explain_rebuild: false,
        wait_for_build_lock: false,
    };
    let report = project_report(
        &args.project,
        GenerationPolicy::Requested(SyncMode::Check),
        "prepare_native_sources",
        cancellation,
        invocation_dir,
    )?;
    let loaded = CoreCatalog::load(args.project.root()?, invocation_dir)?;
    ensure!(
        loaded.catalog.context_identity(&args.project.projection)? == report.context_identity,
        "named sources catalog changed after preparation"
    );
    let checked = crate::source_projection::catalog_owned_project::collect_catalog_project(
        args.project.root()?,
        invocation_dir,
        &args.project.projection,
    )?
    .check_current()?;
    ensure!(
        checked.receipt().project_inputs_sha256 == report.project_inputs_sha256,
        "named sources metadata changed after preparation"
    );
    let frozen =
        crate::jar_build::prepare_named_frozen_recipe_project(checked, &args.released_recipe)?;
    crate::jar_build::engine::validate_named_source_output_project(&frozen)?;
    let sdk = select_native_sdk(&compile, &loaded, invocation_dir, cancellation)?;
    ensure!(
        sdk.recheck(&compile, &loaded, invocation_dir, cancellation)?,
        "selected Java changed after source preparation"
    );
    Ok(CliOutput::facet(
        crate::jar_build::engine::invoke_named_source_outputs(
            frozen,
            &crate::jar_build::NamedCompileOptions {
                java_home: sdk.home,
                cache_root: Some(sdk.cache_root),
                pinned_sdk: sdk.pinned,
                isolated_project_cache: None,
                rebuild_shared_mcp: false,
                explain_rebuild: false,
                wait_for_build_lock: false,
            },
            args.layout,
            cancellation,
        )?,
    ))
}

#[cfg(test)]
#[path = "core_project_sources_tests.rs"]
mod tests;
