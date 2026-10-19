use crate::source_projection::catalog_owned_project::ProjectionPreparationPhase;

#[derive(Clone, Copy)]
enum ProjectionOperation<'a> {
    Build,
    Run(RunKind, &'a RunOptions),
    Test(&'a RunTestOptions),
    Catalog(&'a SourceCatalogQuery),
}

fn checked_projection_identity(
    root: &Path,
    key: &str,
    skip_source_checks: bool,
) -> eyre::Result<BuildProjectIdentity> {
    use crate::source_projection::catalog_owned_project::collect_catalog_project;
    use crate::source_projection::catalog_owned_project::collect_development_invocation;
    use crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE;
    use crate::source_projection::projection_catalog::ProjectionEnvironment;
    let phase = ProjectionPreparationPhase::start(key, "prepare_projection_identity");
    let cwd = std::env::current_dir()?;
    let loaded = crate::source_projection::core_catalog::CoreCatalog::load(root, &cwd)?;
    let environment = loaded
        .catalog
        .0
        .get(key)
        .ok_or_else(|| eyre::eyre!("projection not found: {key}"))?
        .environment;
    eyre::ensure!(
        !skip_source_checks || environment == ProjectionEnvironment::Dev,
        "--skip-source-checks requires development projections only"
    );
    let collected = if environment == ProjectionEnvironment::Dev {
        collect_development_invocation(root, &cwd, key)?
    } else {
        collect_catalog_project(root, &cwd, key)?
    };
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
                crate::source_projection::frozen_recipe_project::select_frozen_recipe_for_project(
                    &project,
                )?;
            let frozen = prepare_named_frozen_recipe_project(project, &recipe)?;
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
        selector,
        repo_root,
    } = &options.selection
    else {
        eyre::bail!("projection operation requires projection selection");
    };
    let phase = ProjectionPreparationPhase::start(selector, "projection_operation");
    let selection_phase = ProjectionPreparationPhase::start(selector, "select_projection_catalog");
    let (root, keys) =
        super::projection_selection::select_projections(repo_root.as_deref(), selector)?;
    preflight_projection_jar_output(options, operation, keys.len())?;
    preflight_projection_source_checks(&root, &keys, options.skip_source_checks)?;
    tracing::info!(
        projection_selector = selector,
        matched_projections = keys.len(),
        repo_root = %root.display(),
        "Projection selection resolved"
    );
    selection_phase.complete();
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
    root: &Path,
    key: &str,
    operation: ProjectionOperation<'_>,
    cancellation: &CancellationToken,
) -> eyre::Result<Option<BuildPlan>> {
    let phase = ProjectionPreparationPhase::start(key, "prepare_native_operation");
    cancellation.bail_if_cancelled()?;
    let identity = checked_projection_identity(root, key, options.skip_source_checks)?;
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
    let plan = create_plan_for_project(
        &NativePlanningOptions::from(options),
        &identity,
        cancellation,
    )?;
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
                let snapshot = publish_run_snapshot(&plan, cancellation)?;
                let mut owned_log = None;
                let result = if run_options.game_test_bisect.is_some() {
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
                        &mut lock,
                        &mut owned_log,
                    )
                };
                if matches!(kind, RunKind::GameTestPreview)
                    && !options.dry_run
                    && let Some(log) = owned_log
                {
                    publish_owned_preview_console(&log, &plan.minecraft_dir)?;
                }
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

/// Validate every selected key before the first projection can synchronize or
/// acquire anything. A mixed selector cannot partially prepare development.
fn preflight_projection_source_checks(
    root: &Path,
    keys: &[String],
    skip: bool,
) -> eyre::Result<()> {
    if !skip {
        return Ok(());
    }
    let loaded =
        crate::source_projection::core_catalog::CoreCatalog::load(root, &std::env::current_dir()?)?;
    for key in keys {
        let entry = loaded
            .catalog
            .0
            .get(key)
            .ok_or_else(|| eyre::eyre!("projection not found: {key}"))?;
        eyre::ensure!(
            entry.environment
                == crate::source_projection::projection_catalog::ProjectionEnvironment::Dev,
            "--skip-source-checks requires development projections only; selected release projection: {key}"
        );
    }
    Ok(())
}

#[cfg(test)]
mod invocation_source_policy_tests {
    use super::*;
    use crate::source_projection::catalog_owned_project::tests::Fixture;

    #[test]
    fn fresh_jar_output_invalid_selection_and_names_fail_before_preparation() -> eyre::Result<()> {
        let fixture = Fixture::new_for_target("1.19.2");
        let plan_json = fixture.repository().join("must-not-write-output-plan.json");
        let mut options = BuildOptions {
            selection: super::super::BuildSelection::Projection {
                selector: "*".to_owned(),
                repo_root: Some(fixture.repository().to_path_buf()),
            },
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
            selector: Fixture::key(0, "dev"),
            repo_root: Some(fixture.repository().to_path_buf()),
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
        let dev = Fixture::key(0, "dev");
        let release = Fixture::key(0, "release");
        assert!(
            preflight_projection_source_checks(fixture.repository(), &[dev.clone()], true).is_ok()
        );
        for keys in [vec![release.clone()], vec![dev.clone(), release.clone()]] {
            assert!(preflight_projection_source_checks(fixture.repository(), &keys, true).is_err());
        }
        let plan_json = fixture.repository().join("must-not-write-plan.json");
        let options = BuildOptions {
            selection: super::super::BuildSelection::Projection {
                selector: "*".to_owned(),
                repo_root: Some(fixture.repository().to_path_buf()),
            },
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
        assert!(
            preflight_projection_source_checks(fixture.repository(), &[dev, release], false)
                .is_ok()
        );
        Ok(())
    }
}
