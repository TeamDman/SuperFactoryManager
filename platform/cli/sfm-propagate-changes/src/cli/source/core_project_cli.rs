//! Named-project generation from the one authored Liquid tree.
//!
//! This route deliberately has no overlay, snapshot, preset, source-root or
//! arbitrary output-root argument. Historical imports remain comparison tools,
//! not production inputs to this collector.

use super::source_cli::GradleRunOptions;
use super::source_cli::development_mod_version;
use super::source_cli::game_test_exit_override;
use super::source_cli::gradle_property;
use super::source_cli::report_build_artifacts;
use super::source_cli::run_project_gradle;
use super::source_cli::validate_gradle_profile;
use super::source_cli::validate_gradle_task;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::jdk::resolve_exact_java_for_minecraft_dir;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::core_inputs::CORE_METADATA_PATH;
use crate::source_projection::core_inputs::CORE_ROOT;
use crate::source_projection::core_inputs::CoreProjectInputs;
use crate::source_projection::core_inputs::MAX_CORE_METADATA_BYTES;
use crate::source_projection::core_inputs::collect_core_artifacts;
use crate::source_projection::core_inputs::discover_core_source_files;
use crate::source_projection::core_inputs::select_core_inputs;
use crate::source_projection::named_root::catalog_projection_root;
use crate::source_projection::projection_catalog::ProjectionEnvironment;
use crate::source_projection::provenance::sha256;
use crate::source_projection::sync::CatalogProjectionIdentity;
use crate::source_projection::sync::ProjectedArtifact;
use crate::source_projection::sync::SyncMode;
use crate::source_projection::sync::sync_catalog_projection;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use std::collections::BTreeMap;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Debug, Facet)]
pub struct CoreProjectArgs {
    #[facet(args::subcommand)]
    pub command: CoreProjectCommand,
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum CoreProjectCommand {
    /// Render selected core files into selected projections using Git dirty protection.
    Manifest(CoreManifestArgs),
    /// Preview core-owned generation and conflict checks without writing.
    DryRun(CoreProjectSelectionArgs),
    /// Require a named project's sources and ownership to match current inputs.
    Check(CoreProjectSelectionArgs),
    /// Synchronize an exact catalog destination after contributor-edit checks.
    Sync(CoreProjectSelectionArgs),
    /// Build a selected project; release inputs must already be synchronized.
    Build(CoreProjectGradleArgs),
    /// Native compile of an exact released recipe or declared development profile.
    Compile(CoreProjectCompileArgs),
    /// Package an exact release recipe or declared development profile; no app launch.
    Jar(CoreProjectCompileArgs),
    /// Run a selected project without changing another projection or its saves.
    Run(CoreProjectGradleArgs),
}

#[derive(Debug, Facet)]
pub struct CoreManifestArgs {
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Repeat for exact core-relative paths; omitted or * means all files.
    #[facet(default, args::named)]
    pub file: Vec<String>,
    /// Repeat for exact projection keys; omitted or * means all projections.
    #[facet(default, args::named)]
    pub projection: Vec<String>,
    /// Allow dirty selected outputs, optionally restricted to a repository-relative path.
    #[facet(default, args::named)]
    #[expect(
        clippy::option_option,
        reason = "Figue distinguishes absent, bare and path-scoped --allow-dirty"
    )]
    pub allow_dirty: Option<Option<String>>,
    #[facet(default = false, args::named)]
    pub dry_run: bool,
}

#[cfg(test)]
mod manifestation_argument_tests {
    use super::CoreManifestArgs;

    #[test]
    fn dirty_override_distinguishes_absent_bare_and_scoped() {
        let absent = figue::from_slice::<CoreManifestArgs>(&["--repo-root", "."]).unwrap();
        assert_eq!(absent.allow_dirty, None);
        let bare =
            figue::from_slice::<CoreManifestArgs>(&["--repo-root", ".", "--allow-dirty"]).unwrap();
        assert_eq!(bare.allow_dirty, Some(None));
        let scoped = figue::from_slice::<CoreManifestArgs>(&[
            "--repo-root",
            ".",
            "--allow-dirty",
            "platform/minecraft/projections",
            "--file",
            "src/A.java",
            "--projection",
            "example/mc-1.19.2",
        ])
        .unwrap();
        assert_eq!(
            scoped.allow_dirty,
            Some(Some("platform/minecraft/projections".into()))
        );
        assert_eq!(scoped.file, ["src/A.java"]);
        assert_eq!(scoped.projection, ["example/mc-1.19.2"]);
    }
}

#[derive(Debug, Facet)]
pub struct CoreProjectSelectionArgs {
    /// Repository root containing projections.json and the core Liquid tree.
    #[facet(args::named)]
    pub repo_root: PathBuf,
    /// Exact catalog key; its values, not its spelling, select the context.
    #[facet(args::named)]
    pub projection: String,
}

#[derive(Debug, Facet)]
pub struct CoreProjectGradleArgs {
    #[facet(flatten)]
    pub project: CoreProjectSelectionArgs,
    /// Explicit JDK home; otherwise use the projected exact toolchain lock.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
    /// Defaults to jar for build or runClient for run.
    #[facet(default, args::named)]
    pub task: Option<String>,
    /// Optional declared Gradle profile, such as rust-toolchain.
    #[facet(default, args::named)]
    pub gradle_profile: Option<String>,
    /// Use only previously cached Gradle dependencies and Minecraft assets.
    #[facet(default = false, args::named)]
    pub offline: bool,
}

#[derive(Debug, Facet)]
pub struct CoreProjectCompileArgs {
    #[facet(flatten)]
    pub project: CoreProjectSelectionArgs,
    /// Explicit reviewed frozen recipe identity; never a dependency profile.
    #[facet(default, args::named)]
    pub released_recipe: Option<String>,
    /// Exact declared schema-4 development profile; excludes released-recipe.
    #[facet(default, args::named)]
    pub dependency_profile: Option<String>,
    /// Explicit SDK home; otherwise use the selected lock pin or legacy exact-major JDK.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
    /// Guarded native root override; defaults to this checkout's sfm-toolchain-data.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
    /// Compile in a fresh mutable project cache under the same shared root.
    /// Stable names can be reused for an unchanged reconstruction check.
    #[facet(default, args::named)]
    pub isolated_project_cache: Option<String>,
    /// Create-only alternate JAR basename for an exact released recipe; preserves an existing canonical output.
    #[facet(default, args::named)]
    pub output_name: Option<String>,
    #[facet(default = false, args::named)]
    pub explain_rebuild: bool,
    #[facet(default = false, args::named)]
    pub wait_for_build_lock: bool,
}

#[derive(Debug, Facet)]
struct CoreArtifactReport {
    authored_input: String,
    source_sha256: String,
    output_sha256: String,
    output_bytes: u64,
}

#[derive(Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
enum GenerationValidationStatus {
    NotPerformed,
}

#[derive(Debug, Facet)]
struct CoreProjectReport {
    schema: String,
    scope: String,
    operation: String,
    projection_key: String,
    target_id: String,
    minecraft_version: String,
    environment: String,
    context_identity: String,
    project_dir: String,
    catalog_sha256: String,
    feature_definitions_sha256: String,
    project_inputs_sha256: String,
    enabled_features: Vec<String>,
    omitted_source_paths: Vec<String>,
    artifacts: BTreeMap<String, CoreArtifactReport>,
    created: Vec<String>,
    updated: Vec<String>,
    unchanged: Vec<String>,
    manifest_changed: bool,
    writes_performed: bool,
    compilation: GenerationValidationStatus,
    release_compatibility: GenerationValidationStatus,
}

#[derive(Clone, Copy)]
enum GenerationPolicy {
    Requested(SyncMode),
    BuildRun,
}

impl CoreProjectArgs {
    pub(super) fn invoke_in(
        self,
        cancellation: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        let (args, mode, operation) = match self.command {
            CoreProjectCommand::Manifest(args) => {
                let (root, rendered) = crate::source_projection::manifestation::render_selected(
                    &args.repo_root,
                    invocation_dir,
                    &args.file,
                    &args.projection,
                    cancellation,
                )?;
                return Ok(CliOutput::facet(
                    crate::source_projection::manifestation::manifest_selected(
                        &root,
                        &rendered,
                        &args.allow_dirty,
                        args.dry_run,
                    )?,
                ));
            }
            CoreProjectCommand::Compile(args) => {
                return invoke_native_compile(&args, cancellation, invocation_dir);
            }
            CoreProjectCommand::Jar(args) => {
                return invoke_native_jar(&args, cancellation, invocation_dir);
            }
            CoreProjectCommand::Build(args) => {
                return invoke_gradle(args, false, cancellation, invocation_dir);
            }
            CoreProjectCommand::Run(args) => {
                return invoke_gradle(args, true, cancellation, invocation_dir);
            }
            CoreProjectCommand::DryRun(args) => (args, SyncMode::DryRun, "dry_run"),
            CoreProjectCommand::Check(args) => (args, SyncMode::Check, "check"),
            CoreProjectCommand::Sync(args) => (args, SyncMode::Apply, "sync"),
        };
        let report = project_report(
            &args,
            GenerationPolicy::Requested(mode),
            operation,
            cancellation,
            invocation_dir,
        )?;
        if mode == SyncMode::Check {
            // Match native compile: generated source scans must reject unowned files.
            let checked = crate::source_projection::catalog_owned_project::collect_catalog_project(
                &args.repo_root,
                invocation_dir,
                &args.projection,
            )?
            .check_current()?;
            ensure!(
                checked.receipt().project_inputs_sha256 == report.project_inputs_sha256,
                "named source check metadata changed after generation",
            );
        }
        Ok(CliOutput::facet(report))
    }
}

fn invoke_native_compile(
    args: &CoreProjectCompileArgs,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<CliOutput> {
    ensure!(
        args.output_name.is_none(),
        "--output-name is only supported by source project jar with a released recipe"
    );
    if args.dependency_profile.is_some() {
        return invoke_native_development(args, false, cancellation, invocation_dir);
    }
    ensure!(
        args.isolated_project_cache.is_none(),
        "--isolated-project-cache requires a development dependency profile"
    );
    let recipe = args.released_recipe.as_deref().ok_or_else(|| {
        eyre::eyre!(
            "native compile requires exactly one of --released-recipe or --dependency-profile"
        )
    })?;
    cancellation.bail_if_cancelled()?;
    // Refuse unsupported target/recipe before dev generation or external effects.
    let initial = CoreCatalog::load(&args.project.repo_root, invocation_dir)?;
    let entry = initial.catalog.entry(&args.project.projection)?;
    preflight_named_compile_recipe(entry.target_id()?, &entry.minecraft_version, recipe)?;
    let sdk = select_native_sdk(args, &initial, invocation_dir, cancellation)?;
    let report = project_report(
        &args.project,
        GenerationPolicy::BuildRun,
        "prepare_native_compile",
        cancellation,
        invocation_dir,
    )?;
    let loaded = CoreCatalog::load(&args.project.repo_root, invocation_dir)?;
    ensure!(
        loaded.catalog_sha256 == report.catalog_sha256
            && loaded.feature_definitions_sha256 == report.feature_definitions_sha256
            && loaded.catalog.context_identity(&args.project.projection)?
                == report.context_identity,
        "named compile catalog changed after generation"
    );
    let collected = crate::source_projection::catalog_owned_project::collect_catalog_project(
        &args.project.repo_root,
        invocation_dir,
        &args.project.projection,
    )?;
    let checked = collected.check_current()?;
    ensure!(
        checked.receipt().project_inputs_sha256 == report.project_inputs_sha256,
        "named compile build metadata changed after generation"
    );
    ensure!(
        sdk.recheck(args, &loaded, invocation_dir, cancellation)?,
        "selected Java changed after generation; retry with the current lock"
    );
    let frozen = crate::jar_build::prepare_named_frozen_recipe_project(checked, recipe)?;
    let result = crate::jar_build::invoke_named_compile(
        frozen,
        &crate::jar_build::NamedCompileOptions {
            java_home: sdk.home,
            cache_root: Some(sdk.cache_root),
            pinned_sdk: sdk.pinned,
            isolated_project_cache: None,
            explain_rebuild: args.explain_rebuild,
            wait_for_build_lock: args.wait_for_build_lock,
        },
        cancellation,
    )?;
    Ok(CliOutput::facet(result))
}

fn invoke_native_jar(
    args: &CoreProjectCompileArgs,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<CliOutput> {
    ensure!(
        args.isolated_project_cache.is_none(),
        "--isolated-project-cache is compile-only; packaging writes the projection JAR"
    );
    if args.dependency_profile.is_some() {
        ensure!(
            args.output_name.is_none(),
            "--output-name requires a released recipe"
        );
        return invoke_native_development(args, true, cancellation, invocation_dir);
    }
    let recipe = args.released_recipe.as_deref().ok_or_else(|| {
        eyre::eyre!("native jar requires exactly one of --released-recipe or --dependency-profile")
    })?;
    crate::jar_build::engine::preflight_named_jar_output_name(args.output_name.as_deref())?;
    cancellation.bail_if_cancelled()?;
    // Refuse unsupported target/recipe before dev generation or external effects.
    let initial = CoreCatalog::load(&args.project.repo_root, invocation_dir)?;
    let entry = initial.catalog.entry(&args.project.projection)?;
    preflight_named_compile_recipe(entry.target_id()?, &entry.minecraft_version, recipe)?;
    let sdk = select_native_sdk(args, &initial, invocation_dir, cancellation)?;
    let report = project_report(
        &args.project,
        GenerationPolicy::BuildRun,
        "prepare_native_jar",
        cancellation,
        invocation_dir,
    )?;
    let loaded = CoreCatalog::load(&args.project.repo_root, invocation_dir)?;
    ensure!(
        loaded.catalog_sha256 == report.catalog_sha256
            && loaded.feature_definitions_sha256 == report.feature_definitions_sha256
            && loaded.catalog.context_identity(&args.project.projection)?
                == report.context_identity,
        "named jar catalog changed after generation"
    );
    let collected = crate::source_projection::catalog_owned_project::collect_catalog_project(
        &args.project.repo_root,
        invocation_dir,
        &args.project.projection,
    )?;
    let checked = collected.check_current()?;
    ensure!(
        checked.receipt().project_inputs_sha256 == report.project_inputs_sha256,
        "named jar build metadata changed after generation"
    );
    ensure!(
        sdk.recheck(args, &loaded, invocation_dir, cancellation)?,
        "selected Java changed after generation; retry with the current lock"
    );
    let frozen = crate::jar_build::prepare_named_frozen_recipe_project(checked, recipe)?;
    frozen.recheck(false)?;
    let result = crate::jar_build::invoke_named_jar(
        frozen,
        &crate::jar_build::NamedCompileOptions {
            java_home: sdk.home,
            cache_root: Some(sdk.cache_root),
            pinned_sdk: sdk.pinned,
            isolated_project_cache: None,
            explain_rebuild: args.explain_rebuild,
            wait_for_build_lock: args.wait_for_build_lock,
        },
        args.output_name.as_deref(),
        cancellation,
    )?;
    Ok(CliOutput::facet(result))
}

fn invoke_native_development(
    args: &CoreProjectCompileArgs,
    package: bool,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<CliOutput> {
    let profile = args
        .dependency_profile
        .as_deref()
        .ok_or_else(|| eyre::eyre!("missing development profile"))?;
    ensure!(
        args.released_recipe.is_none(),
        "--released-recipe and --dependency-profile are mutually exclusive"
    );
    ensure!(
        profile == crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE,
        "native development build requires the declared rust-toolchain profile"
    );
    if let Some(label) = args.isolated_project_cache.as_deref() {
        ensure!(
            !package
                && !label.is_empty()
                && label.len() <= 64
                && label
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_')),
            "--isolated-project-cache requires a compile and a 1-64 character ASCII label"
        );
    }
    let initial = CoreCatalog::load(&args.project.repo_root, invocation_dir)?;
    let entry = initial.catalog.entry(&args.project.projection)?;
    ensure!(
        entry.environment == ProjectionEnvironment::Dev,
        "development native admission requires a development projection"
    );
    crate::source_projection::native_project_target::development_native_loader(
        entry.target_id()?,
        &entry.minecraft_version,
    )?;
    let collected = crate::source_projection::catalog_owned_project::collect_development_invocation(
        &args.project.repo_root,
        invocation_dir,
        &args.project.projection,
    )?;
    crate::source_projection::native_project_target::validate_collected_native_profile(
        &collected, profile,
    )?;
    let sdk = select_native_sdk(args, &initial, invocation_dir, cancellation)?;
    cancellation.bail_if_cancelled()?;
    // Verify the complete source snapshot once after guarded sync. This owner
    // can safely recheck held remap inputs while a per-key cache lock is held.
    let checked = collected.prepare_development_invocation(false)?;
    ensure!(
        sdk.recheck(args, &initial, invocation_dir, cancellation)?,
        "selected Java changed after generation; retry with the current lock"
    );
    Ok(CliOutput::facet(
        crate::jar_build::invoke_development_project(
            checked,
            profile,
            &crate::jar_build::NamedCompileOptions {
                java_home: sdk.home,
                cache_root: Some(sdk.cache_root),
                pinned_sdk: sdk.pinned,
                isolated_project_cache: args.isolated_project_cache.clone(),
                explain_rebuild: args.explain_rebuild,
                wait_for_build_lock: args.wait_for_build_lock,
            },
            package,
            cancellation,
        )?,
    ))
}

struct NativeSdkSelection {
    home: PathBuf,
    cache_root: PathBuf,
    pinned: Option<Arc<crate::jar_build::engine::NamedPinnedSdk>>,
    required_major: u32,
}

impl NativeSdkSelection {
    fn recheck(
        &self,
        args: &CoreProjectCompileArgs,
        selected: &CoreCatalog,
        invocation_dir: &Path,
        cancellation: &CancellationToken,
    ) -> Result<bool> {
        let current_root = crate::jar_build::engine::catalog_native_cache_path(
            &selected.repo_root,
            args.cache_root.as_deref(),
        );
        if current_root != self.cache_root {
            return Ok(false);
        }
        if let Some(pinned) = &self.pinned {
            let project = selected
                .repo_root
                .join(selected.catalog.project_dir(&args.project.projection)?);
            pinned.recheck_lock(
                &project.join("sfm-toolchain.lock.json"),
                self.required_major,
                cancellation,
            )?;
            return Ok(pinned.home() == self.home);
        }
        Ok(resolve_native_sdk_home(args, selected, invocation_dir)? == self.home)
    }
}

fn select_native_sdk(
    args: &CoreProjectCompileArgs,
    selected: &CoreCatalog,
    invocation_dir: &Path,
    cancellation: &CancellationToken,
) -> Result<NativeSdkSelection> {
    let entry = selected.catalog.entry(&args.project.projection)?;
    let metadata_bytes = read_project_metadata(&selected.repo_root)?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &selected.registered_features,
    )?;
    let required_major = u32::from(
        metadata
            .targets
            .get(entry.target_id()?)
            .ok_or_else(|| eyre::eyre!("missing named build target"))?
            .java_major,
    );
    let project = selected
        .repo_root
        .join(selected.catalog.project_dir(&args.project.projection)?);
    let root = crate::jar_build::engine::catalog_native_cache_path(
        &selected.repo_root,
        args.cache_root.as_deref(),
    );
    if args.java_home.is_none() {
        if let Some(pinned) = crate::jar_build::engine::select_named_pinned_sdk(
            &root,
            &selected.repo_root,
            &project,
            required_major,
            cancellation,
        )? {
            return Ok(NativeSdkSelection {
                home: pinned.home().to_path_buf(),
                cache_root: pinned.cache_root().to_path_buf(),
                pinned: Some(pinned),
                required_major,
            });
        }
    }
    let home = resolve_native_sdk_home(args, selected, invocation_dir)?;
    let cache_root = crate::jar_build::engine::preflight_named_cache_root(
        &root,
        &selected.repo_root,
        &project,
        &home,
    )?;
    Ok(NativeSdkSelection {
        home,
        cache_root,
        pinned: None,
        required_major,
    })
}

fn resolve_native_sdk_home(
    args: &CoreProjectCompileArgs,
    selected: &CoreCatalog,
    invocation_dir: &Path,
) -> Result<PathBuf> {
    if let Some(explicit) = args.java_home.as_deref() {
        return preflight_native_sdk_home(explicit, invocation_dir);
    }
    let entry = selected.catalog.entry(&args.project.projection)?;
    let metadata_bytes = read_project_metadata(&selected.repo_root)?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &selected.registered_features,
    )?;
    let java_major = u32::from(
        metadata
            .targets
            .get(entry.target_id()?)
            .ok_or_else(|| eyre::eyre!("missing named build target"))?
            .java_major,
    );
    let project_root = selected
        .repo_root
        .join(selected.catalog.project_dir(&args.project.projection)?);
    let resolved = resolve_exact_java_for_minecraft_dir(&project_root, java_major, None)
        .wrap_err("cannot select the projection's JDK; synchronize its lock or pass --java-home")?;
    let home = resolved.home.as_deref().ok_or_else(|| {
        eyre::eyre!(
            "selected Java {} has no JDK home; install a verified JDK or pass --java-home",
            resolved.executable.display()
        )
    })?;
    preflight_native_sdk_home(home, invocation_dir)
}

fn preflight_native_sdk_home(path: &Path, invocation_dir: &Path) -> Result<PathBuf> {
    use crate::source_projection::candidate_lock::checked_directory;
    let absolute = if path.is_absolute() {
        path.to_path_buf()
    } else {
        invocation_dir.join(path)
    };
    let home = checked_directory(&absolute)?;
    for relative in [
        if cfg!(windows) {
            "bin/java.exe"
        } else {
            "bin/java"
        },
        if cfg!(windows) {
            "bin/javac.exe"
        } else {
            "bin/javac"
        },
        "release",
        "lib/modules",
    ] {
        checked_file(&home, relative)?;
    }
    Ok(home)
}

fn preflight_named_forge_recipe(target: &str, minecraft_version: &str, recipe: &str) -> Result<()> {
    ensure!(
        matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1")
            && minecraft_version == target
            && recipe == format!("sfm:released-native-inputs/4.34.0/{target}@1"),
        "named native Compile requires an exact released Forge-family recipe; NeoForm, packaging, JUnit and launch remain separate gates"
    );
    Ok(())
}

fn preflight_named_compile_recipe(
    target: &str,
    minecraft_version: &str,
    recipe: &str,
) -> Result<()> {
    if crate::jar_build::preflight_named_neoform_recipe(target, minecraft_version, recipe)? {
        return Ok(());
    }
    preflight_named_forge_recipe(target, minecraft_version, recipe)
}

#[cfg(test)]
mod named_native_sdk_preflight_tests {
    use super::*;

    #[test]
    fn exact_neoform_compile_preflight_shares_the_engine_recipe_gate() {
        let recipe = "sfm:released-native-inputs/4.34.0/1.20.2@1";
        assert_eq!(
            preflight_named_compile_recipe("1.20.2", "1.20.2", recipe).is_ok(),
            cfg!(windows)
        );
        assert!(preflight_named_compile_recipe("1.20.2", "1.20.3", recipe).is_err());
        assert!(preflight_named_compile_recipe("1.20.2", "1.20.2", "unreviewed").is_err());
        assert!(preflight_named_forge_recipe("1.20.2", "1.20.2", recipe).is_err());
        for (target, minecraft) in [
            ("1.20.2", "1.20.2"),
            ("1.20.3", "1.20.3"),
            ("1.20.4", "1.20.4"),
            ("1.21.0", "1.21"),
            ("1.21.1", "1.21.1"),
            ("26.1.2", "26.1.2"),
        ] {
            let recipe = format!("sfm:released-native-inputs/4.34.0/{target}@1");
            assert_eq!(
                preflight_named_compile_recipe(target, minecraft, &recipe).is_ok(),
                cfg!(windows)
            );
            assert!(preflight_named_compile_recipe(target, "unknown", &recipe).is_err());
            assert!(preflight_named_compile_recipe(target, minecraft, "unreviewed").is_err());
            assert!(preflight_named_forge_recipe(target, minecraft, &recipe).is_err());
        }
        assert!(
            preflight_named_compile_recipe(
                "1.21.0",
                "1.21.0",
                "sfm:released-native-inputs/4.34.0/1.21.0@1"
            )
            .is_err()
        );
    }

    #[test]
    fn original_forge_recipe_gate_accepts_only_its_exact_target_before_sdk_or_generation() {
        for target in ["1.19.2", "1.19.4", "1.20", "1.20.1"] {
            let recipe = format!("sfm:released-native-inputs/4.34.0/{target}@1");
            preflight_named_forge_recipe(target, target, &recipe).unwrap();
            assert!(preflight_named_forge_recipe(target, "different", &recipe).is_err());
            assert!(preflight_named_forge_recipe(target, target, "unreviewed").is_err());
        }
        for target in [
            "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "unknown",
        ] {
            let recipe = format!("sfm:released-native-inputs/4.34.0/{target}@1");
            assert!(preflight_named_forge_recipe(target, target, &recipe).is_err());
        }
    }

    #[test]
    fn named_native_missing_sdk_refuses_before_any_generation() -> Result<()> {
        let directory = tempfile::tempdir()?;
        let missing = directory.path().join("missing-sdk");
        assert!(preflight_native_sdk_home(&missing, directory.path()).is_err());
        assert_eq!(fs::read_dir(directory.path())?.count(), 0);
        Ok(())
    }

    #[test]
    fn named_native_sdk_preflight_requires_all_regular_execution_inputs() -> Result<()> {
        let directory = tempfile::tempdir()?;
        let home = directory.path().join("sdk");
        fs::create_dir_all(home.join("bin"))?;
        fs::create_dir_all(home.join("lib"))?;
        let paths = [
            if cfg!(windows) {
                "bin/java.exe"
            } else {
                "bin/java"
            },
            if cfg!(windows) {
                "bin/javac.exe"
            } else {
                "bin/javac"
            },
            "release",
            "lib/modules",
        ];
        for relative in paths {
            assert!(preflight_native_sdk_home(&home, directory.path()).is_err());
            fs::write(home.join(relative), b"fixture")?;
        }
        assert_eq!(
            preflight_native_sdk_home(&home, directory.path())?,
            fs::canonicalize(home)?
        );
        Ok(())
    }
}

fn invoke_gradle(
    args: CoreProjectGradleArgs,
    run: bool,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<CliOutput> {
    cancellation.bail_if_cancelled()?;
    let task = args
        .task
        .as_deref()
        .unwrap_or(if run { "runClient" } else { "jar" });
    validate_gradle_task(task)?;
    if let Some(profile) = &args.gradle_profile {
        validate_gradle_profile(profile)?;
    }
    // Release builds and runs cannot silently regenerate checked-in sources.
    // Development writes are confined to a declared, ignored catalog root.
    let report = project_report(
        &args.project,
        GenerationPolicy::BuildRun,
        "prepare_build_run",
        cancellation,
        invocation_dir,
    )?;
    let loaded = CoreCatalog::load(&args.project.repo_root, invocation_dir)?;
    ensure!(
        loaded.catalog_sha256 == report.catalog_sha256
            && loaded.feature_definitions_sha256 == report.feature_definitions_sha256
            && loaded.catalog.context_identity(&args.project.projection)?
                == report.context_identity,
        "named build inputs changed after generation; retry with the current catalog"
    );
    let entry = loaded.catalog.entry(&args.project.projection)?;
    let target = entry.target_id()?;
    let project_root = loaded.repo_root.join(&report.project_dir);
    let metadata_bytes = read_project_metadata(&loaded.repo_root)?;
    ensure!(
        sha256(&metadata_bytes) == report.project_inputs_sha256,
        "core build metadata changed after generation"
    );
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &loaded.registered_features,
    )?;
    let java_major = u32::from(
        metadata
            .targets
            .get(target)
            .ok_or_else(|| eyre::eyre!("missing named build target"))?
            .java_major,
    );
    let explicit_home = args.java_home.map(|path| {
        if path.is_absolute() {
            path
        } else {
            invocation_dir.join(path)
        }
    });
    let resolved =
        resolve_exact_java_for_minecraft_dir(&project_root, java_major, explicit_home.as_deref())?;
    let home = resolved
        .home
        .as_deref()
        .ok_or_else(|| eyre::eyre!("selected JDK has no home; specify --java-home"))?;
    let version = match entry.environment {
        ProjectionEnvironment::Release => gradle_property(&project_root, "mod_version")?,
        ProjectionEnvironment::Dev => development_mod_version(&project_root)?,
    };
    let init_script = game_test_exit_override(&loaded.repo_root, target, task);
    cancellation.bail_if_cancelled()?;
    // JDK acquisition can take long enough for an editor to change inputs.
    // Refuse stale or edited output immediately before launching Gradle.
    project_report(
        &args.project,
        GenerationPolicy::Requested(SyncMode::Check),
        "pre_launch_check",
        cancellation,
        invocation_dir,
    )?;
    run_project_gradle(
        &project_root,
        task,
        &version,
        home,
        &GradleRunOptions {
            profile: args.gradle_profile.as_deref(),
            offline: args.offline,
            init_script: init_script.as_deref(),
        },
        cancellation,
    )?;
    if !run {
        report_build_artifacts(&project_root, task, &version)?;
    }
    Ok(CliOutput::none())
}

fn project_report(
    args: &CoreProjectSelectionArgs,
    policy: GenerationPolicy,
    operation: &str,
    cancellation: &CancellationToken,
    invocation_dir: &Path,
) -> Result<CoreProjectReport> {
    cancellation.bail_if_cancelled()?;
    let loaded = CoreCatalog::load(&args.repo_root, invocation_dir)?;
    let context = loaded.context(&args.projection)?;
    let entry = loaded.catalog.entry(&args.projection)?;
    let mode = match policy {
        GenerationPolicy::Requested(mode) => mode,
        GenerationPolicy::BuildRun => match entry.environment {
            ProjectionEnvironment::Release => SyncMode::Check,
            ProjectionEnvironment::Dev => SyncMode::Apply,
        },
    };
    let metadata_bytes = read_project_metadata(&loaded.repo_root)?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes).wrap_err("core project metadata is not UTF-8")?,
        &loaded.registered_features,
    )?;
    let core = loaded.repo_root.join(CORE_ROOT);
    let inventory = discover_core_source_files(&core)?;
    let selection = select_core_inputs(&metadata, &context, &inventory)?;
    let artifacts = collect_core_artifacts(&core, &selection, &context)?;
    cancellation.bail_if_cancelled()?;
    let root = catalog_projection_root(
        &loaded.repo_root,
        &loaded.catalog,
        &args.projection,
        &artifacts,
    )?;
    let context_identity = loaded.catalog.context_identity(&args.projection)?;
    let identity = CatalogProjectionIdentity {
        target_id: entry.target_id()?.to_owned(),
        minecraft_version: entry.minecraft_version.clone(),
        projection_key: args.projection.clone(),
        environment: entry.environment,
        context_identity: context_identity.clone(),
    };
    // The transaction repeats conflict checks before applying. Cancellation is
    // observed before the transaction, not mid-replacement of an owned project.
    let sync = sync_catalog_projection(&root, &identity, &artifacts, mode)?;
    let mut enabled_features = entry.features.clone();
    enabled_features.sort();
    Ok(CoreProjectReport {
        schema: "sfm:core_project_generation@1".to_owned(),
        scope: "core_owned_source_generation_not_compilation_or_release_acceptance".to_owned(),
        operation: operation.to_owned(),
        projection_key: args.projection.clone(),
        target_id: identity.target_id,
        minecraft_version: entry.minecraft_version.clone(),
        environment: entry.environment.as_str().to_owned(),
        context_identity,
        project_dir: loaded
            .catalog
            .project_dir(&args.projection)?
            .to_string_lossy()
            .replace('\\', "/"),
        catalog_sha256: loaded.catalog_sha256,
        feature_definitions_sha256: loaded.feature_definitions_sha256,
        project_inputs_sha256: sha256(&metadata_bytes),
        enabled_features,
        omitted_source_paths: selection.omitted_paths.into_iter().collect(),
        artifacts: artifact_reports(&artifacts),
        writes_performed: sync.needs_write()
            && matches!(mode, SyncMode::Apply | SyncMode::Reconcile),
        created: sync.created,
        updated: sync.updated,
        unchanged: sync.unchanged,
        manifest_changed: sync.manifest_changed,
        compilation: GenerationValidationStatus::NotPerformed,
        release_compatibility: GenerationValidationStatus::NotPerformed,
    })
}

fn artifact_reports(
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> BTreeMap<String, CoreArtifactReport> {
    artifacts
        .iter()
        .map(|(output, artifact)| {
            (
                output.clone(),
                CoreArtifactReport {
                    authored_input: artifact.source_path.clone(),
                    source_sha256: sha256(&artifact.source_bytes),
                    output_sha256: sha256(&artifact.output_bytes),
                    output_bytes: artifact.output_bytes.len() as u64,
                },
            )
        })
        .collect()
}

fn read_project_metadata(repo_root: &Path) -> Result<Vec<u8>> {
    let path = checked_file(repo_root, CORE_METADATA_PATH)?;
    let file = fs::File::open(path).wrap_err("cannot open core project metadata")?;
    ensure!(
        file.metadata()?.len() <= MAX_CORE_METADATA_BYTES,
        "core project metadata exceeds bounded byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_CORE_METADATA_BYTES + 1)
        .read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_CORE_METADATA_BYTES,
        "core project metadata grew beyond bounded byte limit"
    );
    Ok(bytes)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::core_inputs::BuildTargetMetadata;
    use crate::source_projection::core_inputs::InputPredicate;
    use crate::source_projection::core_inputs::InputVariant;
    use std::process::Command;

    #[test]
    fn native_commands_parse_cache_override_and_select_checkout_default() {
        let base = ["--repo-root", "must-not-be-read", "--projection", "unused"];
        let default = figue::from_slice::<CoreProjectCompileArgs>(&base).unwrap();
        assert!(default.java_home.is_none());
        assert!(default.cache_root.is_none());
        assert!(default.isolated_project_cache.is_none());
        assert!(default.output_name.is_none());
        assert_eq!(
            crate::jar_build::engine::catalog_native_cache_path(
                Path::new("C:/selected-checkout"),
                default.cache_root.as_deref(),
            ),
            PathBuf::from("C:/selected-checkout/sfm-toolchain-data")
        );
        let mut explicit = base.to_vec();
        explicit.extend([
            "--java-home", "C:/selected-jdk",
            "--cache-root", "C:/sfm cache fixture",
        ]);
        let parsed = figue::from_slice::<CoreProjectCompileArgs>(&explicit).unwrap();
        assert_eq!(parsed.java_home, Some(PathBuf::from("C:/selected-jdk")));
        assert_eq!(
            parsed.cache_root,
            Some(PathBuf::from("C:/sfm cache fixture"))
        );
        assert_eq!(
            crate::jar_build::engine::catalog_native_cache_path(
                Path::new("C:/selected-checkout"),
                parsed.cache_root.as_deref(),
            ),
            PathBuf::from("C:/sfm cache fixture")
        );
        let mut isolated = base.to_vec();
        isolated.extend(["--isolated-project-cache", "pinned_rebuild_1"]);
        let parsed = figue::from_slice::<CoreProjectCompileArgs>(&isolated).unwrap();
        assert_eq!(
            parsed.isolated_project_cache.as_deref(),
            Some("pinned_rebuild_1")
        );
        let mut alternate_output = base.to_vec();
        alternate_output.extend(["--output-name", "historical-check.jar"]);
        let parsed = figue::from_slice::<CoreProjectCompileArgs>(&alternate_output).unwrap();
        assert_eq!(parsed.output_name.as_deref(), Some("historical-check.jar"));
    }

    #[test]
    fn isolated_project_cache_rejects_unsafe_label_and_package_before_effects() {
        let mut args = CoreProjectCompileArgs {
            project: CoreProjectSelectionArgs {
                repo_root: PathBuf::from("must-not-be-read"),
                projection: "unused".to_owned(),
            },
            released_recipe: None,
            dependency_profile: Some("rust-toolchain".to_owned()),
            java_home: Some(PathBuf::from("must-not-be-read")),
            cache_root: None,
            isolated_project_cache: Some("../escape".to_owned()),
            output_name: None,
            explain_rebuild: false,
            wait_for_build_lock: false,
        };
        let cancellation = CancellationToken::new();
        let invalid = invoke_native_development(&args, false, &cancellation, Path::new("."))
            .unwrap_err()
            .to_string();
        assert!(invalid.contains("ASCII label"), "{invalid}");
        args.isolated_project_cache = Some("pinned_rebuild_1".to_owned());
        let package = invoke_native_jar(&args, &cancellation, Path::new("."))
            .unwrap_err()
            .to_string();
        assert!(package.contains("compile-only"), "{package}");
    }

    #[test]
    fn alternate_named_jar_rejects_unsafe_or_unsupported_routes_before_effects() {
        let mut args = CoreProjectCompileArgs {
            project: CoreProjectSelectionArgs {
                repo_root: PathBuf::from("must-not-be-read"),
                projection: "unused".to_owned(),
            },
            released_recipe: Some("historical".to_owned()),
            dependency_profile: None,
            java_home: Some(PathBuf::from("must-not-be-read")),
            cache_root: None,
            isolated_project_cache: None,
            output_name: Some("../escape.jar".to_owned()),
            explain_rebuild: false,
            wait_for_build_lock: false,
        };
        let cancellation = CancellationToken::new();
        let invalid = invoke_native_jar(&args, &cancellation, Path::new("."))
            .unwrap_err()
            .to_string();
        assert!(invalid.contains("fresh JAR name"), "{invalid}");

        args.output_name = Some("historical-check.jar".to_owned());
        let compile = invoke_native_compile(&args, &cancellation, Path::new("."))
            .unwrap_err()
            .to_string();
        assert!(compile.contains("only supported"), "{compile}");

        args.released_recipe = None;
        args.dependency_profile = Some("rust-toolchain".to_owned());
        let development = invoke_native_jar(&args, &cancellation, Path::new("."))
            .unwrap_err()
            .to_string();
        assert!(
            development.contains("requires a released recipe"),
            "{development}"
        );
    }

    #[test]
    fn native_recipe_and_development_profile_are_exclusive_before_effects() {
        let args = CoreProjectCompileArgs {
            project: CoreProjectSelectionArgs {
                repo_root: PathBuf::from("must-not-be-read"),
                projection: "unused".to_owned(),
            },
            released_recipe: Some("unused".to_owned()),
            dependency_profile: Some("rust-toolchain".to_owned()),
            java_home: Some(PathBuf::from("must-not-be-read")),
            cache_root: None,
            isolated_project_cache: None,
            output_name: None,
            explain_rebuild: false,
            wait_for_build_lock: false,
        };
        let cancellation = CancellationToken::new();
        for package in [false, true] {
            let result = if package {
                invoke_native_jar(&args, &cancellation, Path::new("."))
            } else {
                invoke_native_compile(&args, &cancellation, Path::new("."))
            };
            let error = result.unwrap_err().to_string();
            assert!(error.contains("mutually exclusive"), "{error}");
        }
        let missing = CoreProjectCompileArgs {
            released_recipe: None,
            dependency_profile: None,
            ..args
        };
        assert!(
            invoke_native_jar(&missing, &cancellation, Path::new("."))
                .unwrap_err()
                .to_string()
                .contains("requires exactly one")
        );
    }

    #[test]
    fn native_default_java_refuses_a_missing_project_lock_and_preserves_explicit_home() {
        let (temp, project) = fixture();
        let selected = CoreCatalog::load(&project.repo_root, temp.path()).unwrap();
        let mut args = CoreProjectCompileArgs {
            project,
            released_recipe: Some("historical".to_owned()),
            dependency_profile: None,
            java_home: None,
            cache_root: None,
            isolated_project_cache: None,
            output_name: None,
            explain_rebuild: false,
            wait_for_build_lock: false,
        };
        let error = resolve_native_sdk_home(&args, &selected, temp.path())
            .unwrap_err()
            .to_string();
        assert!(error.contains("synchronize its lock"), "{error}");
        let missing = select_native_sdk(
            &args,
            &selected,
            temp.path(),
            &CancellationToken::new(),
        )
        .err()
        .expect("an absent generated lock cannot select a pinned SDK");
        assert!(missing.to_string().contains("sfm-toolchain.lock.json"));
        assert!(!selected.repo_root.join("sfm-toolchain-data").exists());

        let jdk = temp.path().join("jdk");
        fs::create_dir_all(jdk.join("bin")).unwrap();
        fs::create_dir_all(jdk.join("lib")).unwrap();
        for relative in [
            if cfg!(windows) { "bin/java.exe" } else { "bin/java" },
            if cfg!(windows) { "bin/javac.exe" } else { "bin/javac" },
            "release",
            "lib/modules",
        ] {
            fs::write(jdk.join(relative), b"fixture").unwrap();
        }
        args.java_home = Some(jdk.clone());
        assert!(resolve_native_sdk_home(&args, &selected, temp.path())
            .unwrap()
            .ends_with("jdk"));
        let explicit = select_native_sdk(
            &args,
            &selected,
            temp.path(),
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(explicit.pinned.is_none());
        assert_eq!(dunce::simplified(&explicit.home), dunce::simplified(&dunce::canonicalize(jdk).unwrap()));
        assert_eq!(dunce::simplified(&explicit.cache_root), dunce::simplified(&selected.repo_root.join("sfm-toolchain-data")));
    }

    fn git(root: &Path, args: &[&str]) {
        assert!(
            Command::new("git")
                .arg("-C")
                .arg(root)
                .args(args)
                .status()
                .unwrap()
                .success()
        );
    }

    fn fixture() -> (tempfile::TempDir, CoreProjectSelectionArgs) {
        let temp = tempfile::tempdir().unwrap();
        git(temp.path(), &["init", "--quiet"]);
        fs::write(
            temp.path().join(".gitignore"),
            "/platform/minecraft/projections/ignored/\n",
        )
        .unwrap();
        let core = temp.path().join(CORE_ROOT);
        fs::create_dir_all(core.join("src/main/java")).unwrap();
        fs::write(core.join("feature-definitions.json"), "{}").unwrap();
        fs::write(
            temp.path()
                .join(crate::source_projection::projection_catalog::CATALOG_PATH),
            r#"{
            "released/nested":{"minecraft_version":"1.19.2","environment":"release","features":[]},
            "ignored/nested":{"minecraft_version":"1.19.2","environment":"dev","features":[]}
        }"#,
        )
        .unwrap();
        fs::write(core.join("src/main/java/Shared.java"), b"class Shared {}\n").unwrap();
        let mut metadata = CoreProjectInputs {
            schema_version: 1,
            targets: BTreeMap::from([(
                "1.19.2".to_owned(),
                BuildTargetMetadata {
                    java_major: 17,
                    loader: "forge".to_owned(),
                },
            )]),
            source_rules: BTreeMap::new(),
            project_files: BTreeMap::new(),
        };
        for (output, bytes) in [
            ("build.gradle", &b"// fixture\n"[..]),
            ("settings.gradle", &b"rootProject.name = 'sfm-1.19.2'\n"[..]),
            (
                "gradle.properties",
                &b"minecraft_version=1.19.2\nmod_version=4.34.0\n"[..],
            ),
            ("gradlew", &b"fixture\n"[..]),
            ("gradlew.bat", &b"fixture\n"[..]),
            ("sfm-toolchain.lock.json", &b"{}\n"[..]),
            (
                "gradle/wrapper/gradle-wrapper.properties",
                &b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n"[..],
            ),
            ("gradle/wrapper/gradle-wrapper.jar", &b"\x00\xffwrapper"[..]),
        ] {
            let input = format!("build/common/{output}");
            let path = core.join(&input);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, bytes).unwrap();
            metadata.project_files.insert(
                output.to_owned(),
                vec![InputVariant {
                    reference_template: None,
                    input,
                    when: InputPredicate::default(),
                    template: false,
                }],
            );
        }
        fs::write(
            temp.path().join(CORE_METADATA_PATH),
            facet_json::to_string_pretty(&metadata).unwrap(),
        )
        .unwrap();
        let args = CoreProjectSelectionArgs {
            repo_root: PathBuf::from("."),
            projection: "released/nested".to_owned(),
        };
        (temp, args)
    }

    fn invoke(
        args: &CoreProjectSelectionArgs,
        mode: SyncMode,
        root: &Path,
    ) -> Result<CoreProjectReport> {
        project_report(
            args,
            GenerationPolicy::Requested(mode),
            "test",
            &CancellationToken::new(),
            root,
        )
    }

    #[test]
    fn named_dry_run_is_core_owned_and_does_not_write_or_claim_compilation() {
        let (temp, args) = fixture();
        let report = invoke(&args, SyncMode::DryRun, temp.path()).unwrap();
        assert!(!report.writes_performed);
        assert_eq!(report.compilation, GenerationValidationStatus::NotPerformed);
        assert_eq!(
            report.release_compatibility,
            GenerationValidationStatus::NotPerformed
        );
        assert_eq!(
            report.project_dir,
            "platform/minecraft/projections/released/nested"
        );
        assert!(
            report
                .artifacts
                .values()
                .all(|file| file.authored_input.starts_with(CORE_ROOT))
        );
        assert!(!temp.path().join(&report.project_dir).exists());
    }

    #[test]
    fn exact_named_sync_check_and_contributor_hydration_preserve_edits() {
        let (temp, args) = fixture();
        let first = invoke(&args, SyncMode::Apply, temp.path()).unwrap();
        assert!(first.writes_performed);
        let project = temp.path().join(&first.project_dir);
        assert!(
            !project
                .join(".sfm-source-projection-manifest.json")
                .exists()
        );
        assert!(
            !invoke(&args, SyncMode::Check, temp.path())
                .unwrap()
                .writes_performed
        );
        let output = project.join("src/main/java/Shared.java");
        let replacement = b"// GENERATED by sfm-propagate-changes; edit the primary source or reconcile this file.\nclass Shared { int contributed; }\n";
        fs::write(&output, replacement).unwrap();
        assert!(invoke(&args, SyncMode::Apply, temp.path()).is_err());
        assert_eq!(fs::read(&output).unwrap(), replacement);
        fs::write(
            temp.path()
                .join(CORE_ROOT)
                .join("src/main/java/Shared.java"),
            b"class Shared { int contributed; }\n",
        )
        .unwrap();
        invoke(&args, SyncMode::Apply, temp.path()).unwrap();
        assert_eq!(fs::read(&output).unwrap(), replacement);
        invoke(&args, SyncMode::Check, temp.path()).unwrap();
    }

    #[test]
    fn named_check_rejects_unowned_generated_source_like_native_compile() {
        let (temp, args) = fixture();
        let report = invoke(&args, SyncMode::Apply, temp.path()).unwrap();
        let extra = temp
            .path()
            .join(&report.project_dir)
            .join("src/main/java/Extra.java");
        fs::write(&extra, b"class Extra {}\n").unwrap();
        let error = CoreProjectArgs {
            command: CoreProjectCommand::Check(args),
        }
        .invoke_in(&CancellationToken::new(), temp.path())
        .unwrap_err()
        .to_string();
        assert!(
            error.contains("missing or extra unowned source inputs"),
            "{error}"
        );
        assert_eq!(fs::read(extra).unwrap(), b"class Extra {}\n");
    }

    #[test]
    fn ignored_development_and_trackable_release_destinations_do_not_collide() {
        let (temp, mut args) = fixture();
        let release = invoke(&args, SyncMode::Apply, temp.path()).unwrap();
        let release_manifest = fs::read(
            temp.path()
                .join(&release.project_dir)
                .join("src/main/java/Shared.java"),
        )
        .unwrap();
        args.projection = "ignored/nested".to_owned();
        let dev = invoke(&args, SyncMode::Apply, temp.path()).unwrap();
        assert_eq!(dev.environment, "dev");
        assert_eq!(
            fs::read(
                temp.path()
                    .join(&release.project_dir)
                    .join("src/main/java/Shared.java")
            )
            .unwrap(),
            release_manifest
        );
        git(
            temp.path(),
            &[
                "add",
                "--force",
                "platform/minecraft/projections/ignored/nested/src/main/java/Shared.java",
            ],
        );
        assert!(invoke(&args, SyncMode::DryRun, temp.path()).is_err());
    }

    #[test]
    fn obsolete_manifests_and_missing_or_invalid_core_inputs_cannot_supply_fallbacks() {
        let (temp, args) = fixture();
        fs::write(
            temp.path()
                .join("platform/minecraft/source-projection.json"),
            b"not JSON",
        )
        .unwrap();
        invoke(&args, SyncMode::DryRun, temp.path()).unwrap();
        fs::write(temp.path().join(CORE_METADATA_PATH), b"not JSON").unwrap();
        assert!(invoke(&args, SyncMode::DryRun, temp.path()).is_err());
        assert!(
            !temp
                .path()
                .join("platform/minecraft/projections/released")
                .exists()
        );
    }

    #[test]
    fn build_run_policy_never_implicitly_syncs_release_sources() {
        let (temp, mut args) = fixture();
        assert!(
            project_report(
                &args,
                GenerationPolicy::BuildRun,
                "prepare",
                &CancellationToken::new(),
                temp.path()
            )
            .is_err()
        );
        assert!(
            !temp
                .path()
                .join("platform/minecraft/projections/released")
                .exists()
        );
        invoke(&args, SyncMode::Apply, temp.path()).unwrap();
        let release = project_report(
            &args,
            GenerationPolicy::BuildRun,
            "prepare",
            &CancellationToken::new(),
            temp.path(),
        )
        .unwrap();
        assert!(!release.writes_performed);
        let before = fs::read(
            temp.path()
                .join(&release.project_dir)
                .join("src/main/java/Shared.java"),
        )
        .unwrap();
        args.projection = "ignored/nested".to_owned();
        let dev = project_report(
            &args,
            GenerationPolicy::BuildRun,
            "prepare",
            &CancellationToken::new(),
            temp.path(),
        )
        .unwrap();
        assert!(dev.writes_performed);
        assert_eq!(
            fs::read(
                temp.path()
                    .join(&release.project_dir)
                    .join("src/main/java/Shared.java")
            )
            .unwrap(),
            before
        );
    }

    #[test]
    fn invalid_task_or_profile_is_rejected_before_generation_or_jdk_acquisition() {
        for (task, profile) in [("jar --scan", None), ("jar", Some("unsafe profile"))] {
            let temp = tempfile::tempdir().unwrap();
            let args = CoreProjectGradleArgs {
                project: CoreProjectSelectionArgs {
                    repo_root: temp.path().to_path_buf(),
                    projection: "anything".to_owned(),
                },
                java_home: None,
                task: Some(task.to_owned()),
                gradle_profile: profile.map(str::to_owned),
                offline: true,
            };
            let error =
                invoke_gradle(args, false, &CancellationToken::new(), temp.path()).unwrap_err();
            assert!(error.to_string().contains("Gradle"));
            assert_eq!(fs::read_dir(temp.path()).unwrap().count(), 0);
        }
    }
}
