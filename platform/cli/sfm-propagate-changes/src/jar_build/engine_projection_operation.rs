use crate::source_projection::ProjectionName;
use crate::source_projection::catalog_owned_project::ProjectionPreparationPhase;
use crate::source_projection::catalog_owned_project::collect_catalog_project_from_metadata;
use crate::source_projection::catalog_project_metadata::CatalogProjectMetadata;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::projection_catalog::ProjectionEnvironment;

#[derive(Clone, Copy)]
enum ProjectionOperation<'a> {
    Build,
    Run(RunKind, &'a RunOptions),
    Test(&'a RunTestOptions),
    Catalog(&'a SourceCatalogQuery),
    SourceOutputs(SourceOutputLayout),
    CatalogPlatformSources(&'a Path, &'a str),
}

fn checked_projection_identity(
    metadata: &CatalogProjectMetadata,
    key: &str,
    skip_source_checks: bool,
    cache_home: Option<&CacheHome>,
) -> eyre::Result<BuildProjectIdentity> {
    use crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE;
    let phase = ProjectionPreparationPhase::start(key, "prepare_projection_identity");
    let environment = metadata.projection_environment(key)?;
    eyre::ensure!(
        !skip_source_checks || environment == ProjectionEnvironment::Dev,
        "--skip-source-checks requires development projections only"
    );
    let collected = collect_catalog_project_from_metadata(
        metadata,
        key,
        environment == ProjectionEnvironment::Release,
    )?;
    let identity = match collected.environment() {
        ProjectionEnvironment::Dev => {
            let project = collected.prepare_development_invocation(skip_source_checks)?;
            let fingerprint_phase =
                ProjectionPreparationPhase::start(key, "derive_native_source_fingerprint");
            let identity = prepare_development_identity(project, NATIVE_DEPENDENCY_PROFILE)?;
            fingerprint_phase.complete();
            identity
        }
        ProjectionEnvironment::Release => {
            let project = collected.check_current()?;
            let recipe =
                source_projection::frozen_recipe_project::select_frozen_recipe_for_project(
                    &project,
                )?;
            let frozen = prepare_named_frozen_recipe_project(project, &recipe, cache_home)?;
            validate_first_named_compile(&frozen)?;
            BuildProjectIdentity::Catalog {
                frozen: Arc::new(frozen),
            }
        }
    };
    phase.complete();
    Ok(identity)
}

fn invoke_projection_operation(
    options: &BuildOptions,
    operation: ProjectionOperation<'_>,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    use rayon::prelude::*;
    let super::BuildSelection::Projection {
        repo_root,
        projections,
    } = &options.selection
    else {
        eyre::bail!("projection operation requires projection selection");
    };
    let label = match projections.as_slice() {
        [] => eyre::bail!("specify at least one projection"),
        [projection] => projection.as_str(),
        _ => "multiple projections",
    };
    let phase = ProjectionPreparationPhase::start(label, "projection_operation");
    preflight_projection_jar_output(options, operation, projections.len())?;
    let loaded = CoreCatalog::load_path(repo_root.clone())?;
    preflight_projection_source_checks(&loaded, projections, options.skip_source_checks)?;
    let metadata = CatalogProjectMetadata::load(loaded)?;
    let mut effective = options.clone();
    effective.cache_root = Some(catalog_native_cache_path(
        repo_root.as_path(),
        options.cache_root.as_deref(),
    ));
    let options = &effective;
    let limit = options
        .parallelism
        .limit()
        .unwrap_or(1)
        .min(projections.len());
    let pool = options
        .parallelism
        .is_parallel()
        .then(|| rayon::ThreadPoolBuilder::new().num_threads(limit).build())
        .transpose()?;
    let mut plans = Vec::new();
    let mut failures = Vec::new();
    for batch in projections.chunks(limit) {
        cancellation.bail_if_cancelled()?;
        let execute = |projection: &ProjectionName| {
            execute_projection_operation(
                options,
                &metadata,
                projection.as_str(),
                operation,
                cancellation,
            )
        };
        let results = if let Some(pool) = &pool {
            pool.install(|| batch.par_iter().map(execute).collect::<Vec<_>>())
        } else {
            batch.iter().map(execute).collect::<Vec<_>>()
        };
        for (projection, result) in batch.iter().zip(results) {
            match result {
                Ok(Some(plan)) => plans.push(plan),
                Ok(None) => {}
                Err(error) if error.downcast_ref::<CacheHomeRequired>().is_some() => {
                    return Err(error);
                }
                Err(error) => failures.push(format!("{}: {error:#}", projection.as_str())),
            }
        }
        if !failures.is_empty() && !options.error_action.should_continue() {
            break;
        }
    }
    write_requested_plan_outputs(&plans, options.plan_json.as_deref())?;
    eyre::ensure!(
        failures.is_empty(),
        "projection operations failed:\n{}",
        failures.join("\n")
    );
    if let ProjectionOperation::SourceOutputs(layout) = operation {
        write_source_outputs(&plans, layout)?;
    }
    if let ProjectionOperation::CatalogPlatformSources(tree, hash) = operation {
        publish_catalog_platform_source(&plans, tree, hash)?;
    }
    phase.complete();
    Ok(())
}

fn preflight_projection_jar_output(
    options: &BuildOptions,
    operation: ProjectionOperation<'_>,
    selected_count: usize,
) -> eyre::Result<()> {
    if let Some(name) = options.output_name.as_deref() {
        fresh_jar_output::validate_basename(name)?;
        eyre::ensure!(
            matches!(operation, ProjectionOperation::Build) && selected_count == 1,
            "--output-name requires exactly one projection and a jar build/plan operation"
        );
    }
    Ok(())
}

fn execute_projection_operation(
    options: &BuildOptions,
    metadata: &CatalogProjectMetadata,
    key: &str,
    operation: ProjectionOperation<'_>,
    cancellation: &CancellationToken,
) -> eyre::Result<Option<BuildPlan>> {
    let phase = ProjectionPreparationPhase::start(key, "prepare_native_operation");
    cancellation.bail_if_cancelled()?;
    let identity = checked_projection_identity(
        metadata,
        key,
        options.skip_source_checks,
        options.cache_home.as_ref(),
    )?;
    if let ProjectionOperation::Run(kind, _) = operation {
        ensure_projection_smoke_supported(
            kind,
            identity
                .minecraft_dir()
                .join("src/main/java/ca/teamdman/sfm/client/handler/SFMClientSmokeRunHarness.java")
                .is_file(),
        )?;
    }
    if let ProjectionOperation::Catalog(query) = operation {
        print_static_java_catalog_for_project(&identity.minecraft_dir(), query)?;
        identity.recheck()?;
        phase.complete();
        return Ok(None);
    }
    if let BuildProjectIdentity::Catalog { frozen } = &identity
        && ordinary_release_compile_eligible(
            cfg!(windows),
            options.mode,
            options.dry_run,
            operation,
            &frozen.receipt().released_inputs.loader_kind,
            &frozen.receipt().ownership.minecraft_version,
        )
    {
        let inputs = Arc::new(retained_compile_inputs::RetainedCompileInputs::admit(
            Arc::clone(frozen),
        )?);
        let admitted = BuildProjectIdentity::CatalogAdmitted { inputs };
        tracing::info!(
            projection_key = key,
            outcome = "admitted_attempt",
            "ordinary_release_compile_cache"
        );
        let result = all_hit_or_strict_compile(
            frozen,
            || {
                execute_projection_identity_operation(
                    options,
                    key,
                    operation,
                    cancellation,
                    admitted,
                    phase,
                )
            },
            || {
                tracing::info!(
                    projection_key = key,
                    outcome = "strict_retry",
                    "ordinary_release_compile_cache"
                );
                execute_projection_identity_operation(
                    options,
                    key,
                    operation,
                    cancellation,
                    BuildProjectIdentity::Catalog {
                        frozen: Arc::clone(frozen),
                    },
                    ProjectionPreparationPhase::start(key, "prepare_native_operation"),
                )
            },
        )?;
        tracing::info!(
            projection_key = key,
            outcome = "completed",
            "ordinary_release_compile_cache"
        );
        return Ok(result);
    }
    execute_projection_identity_operation(options, key, operation, cancellation, identity, phase)
}

fn ordinary_release_compile_eligible(
    windows: bool,
    mode: BuildMode,
    dry_run: bool,
    operation: ProjectionOperation<'_>,
    loader: &str,
    minecraft: &str,
) -> bool {
    windows
        && matches!(mode, BuildMode::Build)
        && !dry_run
        && matches!(
            operation,
            ProjectionOperation::Test(RunTestOptions {
                action: RunTestAction::Compile,
                ..
            })
        )
        && loader == "neogradle_userdev"
        && minecraft == "1.20.3"
}

fn execute_projection_identity_operation(
    options: &BuildOptions,
    key: &str,
    operation: ProjectionOperation<'_>,
    cancellation: &CancellationToken,
    identity: BuildProjectIdentity,
    phase: ProjectionPreparationPhase,
) -> eyre::Result<Option<BuildPlan>> {
    let plan_phase = ProjectionPreparationPhase::start(key, "resolve_native_build_plan");
    let mut planning = NativePlanningOptions::from(options);
    if let Some(path) = options.cache_root.as_deref() {
        let minecraft_dir = identity.minecraft_dir();
        if let Some(java_home) = options.java_home.as_deref() {
            planning.derived_cache_root = Some(native_cache_root::NativeCacheRoot::acquire(
                path,
                identity.repository_root(),
                &minecraft_dir,
                java_home,
                cancellation,
            )?);
        } else {
            let required_major = if let Some(project) = identity.named_project() {
                u32::from(project.receipt().tool_jvm_minimum)
            } else if let Some(target) = identity.development_target() {
                source_projection::native_project_target::native_tool_java_major(
                    &target.receipt.target_id,
                    &target.receipt.minecraft_version,
                    target.receipt.java_major,
                )?
            } else {
                eyre::bail!("selected projection has no checked Java toolchain requirement")
            };
            if let Some(selected) = select_named_pinned_sdk(
                path,
                identity.repository_root(),
                &minecraft_dir,
                required_major,
                cancellation,
            )? {
                planning.java_home = Some(selected.home().to_path_buf());
                planning.derived_cache_root = Some(selected.owner());
                planning.pinned_sdk = Some(selected);
            } else {
                let java = jdk::resolve_exact_java_for_minecraft_dir(
                    &minecraft_dir,
                    required_major,
                    None,
                    &CacheHome(path.to_path_buf()),
                )?;
                let home = java.home.ok_or_else(|| {
                    eyre::eyre!(
                        "selected Java {} has no JDK home; install a checked JDK or pass --java-home",
                        java.executable.display()
                    )
                })?;
                planning.derived_cache_root = Some(native_cache_root::NativeCacheRoot::acquire(
                    path,
                    identity.repository_root(),
                    &minecraft_dir,
                    &home,
                    cancellation,
                )?);
                planning.java_home = Some(home);
            }
        }
    }
    let plan = create_plan_for_project(&planning, &identity, cancellation)?;
    // A failed admitted attempt drops its plan/capability before strict retry.
    drop(identity);
    tracing::info!(
        projection_key = key,
        minecraft_version = %plan.minecraft_version,
        cache_dir = %plan.cache_dir.display(),
        "Native projection build plan prepared"
    );
    plan_phase.complete();
    phase.complete();
    execute_prepared_projection_operation(options, key, operation, cancellation, plan)
}

#[expect(
    clippy::too_many_lines,
    reason = "Keep the selected projection operation and held consumer lifetimes visible through their dispatch."
)]
fn execute_prepared_projection_operation(
    options: &BuildOptions,
    key: &str,
    operation: ProjectionOperation<'_>,
    cancellation: &CancellationToken,
    plan: BuildPlan,
) -> eyre::Result<Option<BuildPlan>> {
    write_last_plan_output(&plan)?;
    print_plan_summary(&plan);
    if matches!(options.mode, BuildMode::Plan)
        || (options.dry_run
            && matches!(
                operation,
                ProjectionOperation::Build | ProjectionOperation::Run(_, _)
            ))
    {
        return Ok(Some(plan));
    }
    let lock_phase = ProjectionPreparationPhase::start(key, "acquire_native_build_cache_lock");
    let mut lock = Some(acquire_build_cache_lock(
        options,
        &plan,
        "projection build/run/test",
    )?);
    lock_phase.complete();
    // Retain destination ancestry while this invocation may build and publish.
    let _fresh_output_admission = prepare_fresh_jar_output(&plan)?;
    let target = match operation {
        ProjectionOperation::Build => BuildTarget::Jar,
        ProjectionOperation::SourceOutputs(_)
        | ProjectionOperation::CatalogPlatformSources(_, _) => BuildTarget::SourceOutputs,
        ProjectionOperation::Run(_, _)
        | ProjectionOperation::Test(_)
        | ProjectionOperation::Catalog(_) => BuildTarget::Run,
    };
    let mut consume = |context: &ExecutionContext<'_>,
                       mut build_holds: Option<&mut ForgeBuildHolds>|
     -> eyre::Result<()> {
        match operation {
            ProjectionOperation::Build
            | ProjectionOperation::Catalog(_)
            | ProjectionOperation::SourceOutputs(_)
            | ProjectionOperation::CatalogPlatformSources(_, _) => Ok(()),
            ProjectionOperation::Run(kind, run_options) => {
                if run_options.client_hotswap_port.is_some() {
                    jdk::ensure_hotswap_runtime(&plan.java.version_output)?;
                }
                let snapshot_started = Instant::now();
                let snapshot = publish_run_snapshot(&plan, cancellation)?;
                tracing::info!(
                    phase = "warm_run_snapshot_publish",
                    elapsed_ms = snapshot_started.elapsed().as_millis(),
                    "warm_run_stage"
                );
                let mut owned_log = None;
                let result = if run_options.game_test_bisect.is_some() {
                    if let Some(held) = build_holds.as_deref_mut() {
                        held.release_prelaunch_input_owners(context)?;
                    }
                    execute_game_test_bisect(
                        context,
                        &snapshot,
                        kind,
                        run_options,
                        options.dry_run,
                        &mut lock,
                    )
                } else {
                    execute_run_with_context(
                        context,
                        &snapshot,
                        kind,
                        run_options,
                        options.dry_run,
                        RunLaunchState {
                            build_cache_lock: &mut lock,
                            owned_console_log: &mut owned_log,
                            build_holds,
                        },
                    )
                };
                if matches!(kind, RunKind::GameTestPreview)
                    && !options.dry_run
                    && let Some(log) = owned_log
                {
                    publish_owned_preview_console(&log, &plan.minecraft_dir)?;
                }
                tracing::info!(
                    phase = "warm_run_execute_run",
                    elapsed_ms = snapshot_started.elapsed().as_millis(),
                    "warm_run_stage"
                );
                result
            }
            ProjectionOperation::Test(test_options) => {
                execute_junit_tests_with_context(context, options.dry_run, test_options)?;
                if !options.dry_run {
                    publish_run_snapshot(&plan, cancellation)?;
                }
                Ok(())
            }
        }
    };
    if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let context = ExecutionContext::new(&plan, cancellation.clone())?;
        with_named_neoform_context(&context, |held, sources, _origin| {
            match operation {
                ProjectionOperation::CatalogPlatformSources(tree, hash) => {
                    publish_checked_catalog_source_archive(sources, tree, hash)?;
                }
                ProjectionOperation::SourceOutputs(SourceOutputLayout::Jar) => {
                    stdout_line(sources.display())?;
                }
                ProjectionOperation::SourceOutputs(SourceOutputLayout::Filetree) => {
                    print_neoform_source_filetree(held, sources)?;
                }
                _ => execute_project_build(held, target, Instant::now())?,
            }
            consume(held, None)
        })?;
    } else {
        let build_started = Instant::now();
        let context = ExecutionContext::new(&plan, cancellation.clone())?;
        let held = execute_build_with_context(
            &plan,
            &context,
            options.explain_rebuild,
            target,
            cancellation,
        )?;
        tracing::info!(
            phase = "warm_run_build",
            elapsed_ms = build_started.elapsed().as_millis(),
            "warm_run_stage"
        );
        let mut held = held;
        consume(&context, Some(&mut held))?;
    }
    Ok(Some(plan))
}

/// Validate every selected key before the first projection can synchronize or
/// acquire anything. A mixed selector cannot partially prepare development.
fn preflight_projection_source_checks(
    loaded: &CoreCatalog,
    projections: &[ProjectionName],
    skip: bool,
) -> eyre::Result<()> {
    if !skip {
        return Ok(());
    }
    for projection in projections {
        let key = projection.as_str();
        let entry = loaded
            .catalog
            .0
            .get(key)
            .ok_or_else(|| eyre::eyre!("projection not found: {key}"))?;
        eyre::ensure!(
            entry.environment == ProjectionEnvironment::Dev,
            "--skip-source-checks requires development projections only; selected release projection: {key}"
        );
    }
    Ok(())
}

#[cfg(test)]
mod invocation_source_policy_tests {
    use super::*;
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    use crate::source_projection::sfm_repo_path::SfmRepoPath;

    #[test]
    fn fresh_jar_output_invalid_selection_and_names_fail_before_preparation() -> eyre::Result<()> {
        let fixture = Fixture::new_for_target("1.19.2");
        let repo_root = SfmRepoPath::try_from(fixture.repository())?;
        let dev = ProjectionName::try_from(Fixture::key(0, "dev"))?;
        let release = ProjectionName::try_from(Fixture::key(0, "release"))?;
        let plan_json = fixture.repository().join("must-not-write-output-plan.json");
        let mut options = BuildOptions {
            app_home: AppHome(fixture.repository().join("app-home")),
            cache_home: None,
            selection: super::super::BuildSelection::Projection {
                repo_root: repo_root.clone(),
                projections: vec![dev.clone(), release],
            },
            cache_root: None,
            refresh: false,
            skip_source_checks: false,
            explain_rebuild: false,
            plan_json: Some(plan_json.clone()),
            output_name: Some("fresh.jar".to_owned()),
            java_home: None,
            dry_run: true,
            allow_local_artifact_cache: false,
            artifact_sources: Vec::new(),
            require_portable_artifacts: false,
            error_action: super::super::ErrorAction::Bail,
            parallelism: super::super::Parallelism::Sequential,
            wait_for_build_lock: false,
            mode: BuildMode::Plan,
        };
        let error = invoke_projection_operation(
            &options,
            ProjectionOperation::Build,
            &CancellationToken::new(),
        )
        .unwrap_err();
        assert!(
            error.to_string().contains("exactly one projection"),
            "{error:?}"
        );
        assert!(!plan_json.exists());
        assert!(
            !fixture
                .repository()
                .join("platform/minecraft/projections")
                .exists()
        );
        options.selection = super::super::BuildSelection::Projection {
            repo_root,
            projections: vec![dev],
        };
        for name in ["../escape.jar", "CON.jar", "fresh.jar:stream"] {
            options.output_name = Some(name.to_owned());
            let error = invoke_projection_operation(
                &options,
                ProjectionOperation::Build,
                &CancellationToken::new(),
            )
            .unwrap_err();
            assert!(error.to_string().contains("fresh JAR name"), "{error:?}");
            assert!(!plan_json.exists());
            assert!(
                !fixture
                    .repository()
                    .join("platform/minecraft/projections")
                    .exists()
            );
        }
        Ok(())
    }

    #[test]
    fn skip_source_checks_rejects_release_and_mixed_selection_before_writes() -> eyre::Result<()> {
        let fixture = Fixture::new_for_target("1.19.2");
        let repo_root = SfmRepoPath::try_from(fixture.repository())?;
        let loaded = CoreCatalog::load_path(repo_root.clone())?;
        let dev = ProjectionName::try_from(Fixture::key(0, "dev"))?;
        let release = ProjectionName::try_from(Fixture::key(0, "release"))?;
        assert!(
            preflight_projection_source_checks(&loaded, std::slice::from_ref(&dev), true).is_ok()
        );
        for keys in [vec![release.clone()], vec![dev.clone(), release.clone()]] {
            assert!(preflight_projection_source_checks(&loaded, &keys, true).is_err());
        }
        let plan_json = fixture.repository().join("must-not-write-plan.json");
        let options = BuildOptions {
            app_home: AppHome(fixture.repository().join("app-home")),
            cache_home: None,
            selection: super::super::BuildSelection::Projection {
                repo_root: repo_root.clone(),
                projections: vec![dev.clone(), release.clone()],
            },
            cache_root: None,
            refresh: false,
            skip_source_checks: true,
            explain_rebuild: false,
            plan_json: Some(plan_json.clone()),
            output_name: None,
            java_home: None,
            dry_run: true,
            allow_local_artifact_cache: false,
            artifact_sources: Vec::new(),
            require_portable_artifacts: false,
            error_action: super::super::ErrorAction::Bail,
            parallelism: super::super::Parallelism::Sequential,
            wait_for_build_lock: false,
            mode: BuildMode::Plan,
        };
        let error = invoke_projection_operation(
            &options,
            ProjectionOperation::Build,
            &CancellationToken::new(),
        )
        .unwrap_err();
        assert!(
            error.to_string().contains("selected release projection"),
            "{error:?}"
        );
        assert!(!plan_json.exists());
        assert!(
            !fixture
                .repository()
                .join("platform/minecraft/projections")
                .exists()
        );
        assert!(preflight_projection_source_checks(&loaded, &[dev, release], false).is_ok());
        Ok(())
    }
}

fn print_neoform_source_filetree(held: &ExecutionContext<'_>, sources: &Path) -> eyre::Result<()> {
    stdout_line(neoform_source_filetree_path(held, sources)?.display())
}

fn neoform_source_filetree_path(
    held: &ExecutionContext<'_>,
    sources: &Path,
) -> eyre::Result<PathBuf> {
    #[cfg(target_os = "linux")]
    if let Some(cache) = held.held_linux_dependencies {
        return cache.materialize_sources(sources, &held.cancellation_token);
    }
    let tree = super::source_output_filetree::materialize(
        &SourceOutputCacheRoot::new(held.plan.cache_dir.clone()),
        &SourceJarPath::new(sources.to_path_buf()),
    )?;
    Ok(tree.into_path_buf())
}
