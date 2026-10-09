#[derive(Clone, Copy)]
enum ProjectionOperation<'a> {
    Build,
    Run(RunKind, &'a RunOptions),
    Test(&'a RunTestOptions),
    Catalog(&'a SourceCatalogQuery),
}

fn checked_projection_identity(root: &Path, key: &str) -> eyre::Result<BuildProjectIdentity> {
    use crate::source_projection::catalog_owned_project::collect_catalog_project;
    use crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE;
    use crate::source_projection::projection_catalog::ProjectionEnvironment;
    let collected = collect_catalog_project(root, &std::env::current_dir()?, key)?;
    match collected.environment() {
        ProjectionEnvironment::Dev => prepare_development_identity(
            collected.prepare_development()?,
            NATIVE_DEPENDENCY_PROFILE,
        ),
        ProjectionEnvironment::Release => {
            let project = collected.check_current()?;
            let recipe = format!(
                "sfm:released-native-inputs/4.34.0/{}@1",
                project.receipt().target_id
            );
            let frozen = prepare_named_frozen_recipe_project(project, &recipe)?;
            validate_first_named_compile(&frozen)?;
            Ok(BuildProjectIdentity::Catalog {
                frozen: Arc::new(frozen),
            })
        }
    }
}

fn invoke_projection_operation(
    options: &BuildOptions,
    operation: ProjectionOperation<'_>,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    use rayon::prelude::*;
    let super::BuildSelection::Projection {
        selector,
        repo_root,
    } = &options.selection
    else {
        eyre::bail!("projection operation requires projection selection");
    };
    let (root, keys) =
        super::projection_selection::select_projections(repo_root.as_deref(), selector)?;
    let limit = options.parallelism.limit().unwrap_or(1).min(keys.len());
    let pool = options
        .parallelism
        .is_parallel()
        .then(|| rayon::ThreadPoolBuilder::new().num_threads(limit).build())
        .transpose()?;
    let mut plans = Vec::new();
    let mut failures = Vec::new();
    for batch in keys.chunks(limit) {
        cancellation.bail_if_cancelled()?;
        let execute = |key: &String| {
            (
                key.clone(),
                execute_projection_operation(options, &root, key, operation, cancellation),
            )
        };
        let results = if let Some(pool) = &pool {
            pool.install(|| batch.par_iter().map(execute).collect::<Vec<_>>())
        } else {
            batch.iter().map(execute).collect::<Vec<_>>()
        };
        for (key, result) in results {
            match result {
                Ok(Some(plan)) => plans.push(plan),
                Ok(None) => {}
                Err(error) => failures.push(format!("{key}: {error:#}")),
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
    Ok(())
}

fn execute_projection_operation(
    options: &BuildOptions,
    root: &Path,
    key: &str,
    operation: ProjectionOperation<'_>,
    cancellation: &CancellationToken,
) -> eyre::Result<Option<BuildPlan>> {
    cancellation.bail_if_cancelled()?;
    let identity = checked_projection_identity(root, key)?;
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
        return Ok(None);
    }
    let plan = create_plan_for_project(
        &NativePlanningOptions::from(options),
        &identity,
        cancellation,
    )?;
    let mut lock = Some(acquire_build_cache_lock(
        options,
        &plan,
        "projection build/run/test",
    )?);
    write_last_plan_output(&plan)?;
    print_plan_summary(&plan);
    if matches!(options.mode, BuildMode::Plan)
        || (options.dry_run && matches!(operation, ProjectionOperation::Build))
    {
        return Ok(Some(plan));
    }
    let target = if matches!(operation, ProjectionOperation::Build) {
        BuildTarget::Jar
    } else {
        BuildTarget::Run
    };
    let mut consume = |context: &ExecutionContext<'_>| -> eyre::Result<()> {
        match operation {
            ProjectionOperation::Build | ProjectionOperation::Catalog(_) => Ok(()),
            ProjectionOperation::Run(kind, run_options) => {
                if run_options.client_hotswap_port.is_some() {
                    crate::jdk::ensure_hotswap_runtime(&plan.java.version_output)?;
                }
                if releases_build_cache_lock_before_launch(kind, run_options) {
                    drop(lock.take());
                }
                let result = if run_options.game_test_bisect.is_some() {
                    execute_game_test_bisect(context, kind, run_options, options.dry_run)
                } else {
                    execute_run_with_context(context, kind, run_options, options.dry_run)
                };
                if matches!(kind, RunKind::GameTestPreview) && !options.dry_run {
                    let log = run_state_dir(&plan, kind).join("console.log");
                    if log.is_file() {
                        let artifacts =
                            super::projection_preview_artifact_root(&plan.minecraft_dir);
                        fs::create_dir_all(&artifacts)?;
                        fs::copy(log, artifacts.join("console.log"))?;
                    }
                }
                result
            }
            ProjectionOperation::Test(test_options) => {
                execute_junit_tests_with_context(context, options.dry_run, test_options)
            }
        }
    };
    if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let context = ExecutionContext::new(&plan, cancellation.clone())?;
        with_named_neoform_context(&context, |held| {
            execute_project_build(held, target, Instant::now())?;
            consume(held)
        })?;
    } else {
        execute_build(&plan, options.explain_rebuild, target, cancellation)?;
        consume(&ExecutionContext::new(&plan, cancellation.clone())?)?;
    }
    Ok(Some(plan))
}
