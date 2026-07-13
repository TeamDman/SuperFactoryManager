fn ensure_forge_gradle_execution_supported(plan: &BuildPlan) -> eyre::Result<()> {
    if plan.forge_userdev.is_none() || plan.mcp_config.is_none() {
        eyre::bail!(
            "Minecraft {} did not resolve the ForgeGradle userdev plus MCPConfig inputs required by the current executor.",
            plan.minecraft_version
        );
    }

    Ok(())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum BuildTarget {
    Jar,
    Run,
    SourceOutputs,
}

#[expect(
    clippy::too_many_lines,
    reason = "build orchestration keeps the node order and timing output visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        loader = ?plan.loader_toolchain.kind,
        graph_nodes = plan.graph.len(),
        explain_rebuild,
        target = ?target,
    )
)]
fn execute_build(
    plan: &BuildPlan,
    explain_rebuild: bool,
    target: BuildTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let context = ExecutionContext::new(plan, cancellation_token.clone())?;
    let total_started = Instant::now();

    if explain_rebuild {
        for node in &plan.graph {
            tracing::info!("{}: {}", node.id, node.rebuild_reason);
        }
    }

    tracing::info!("Build node resolve-project-config: recording plan state");
    context.bail_if_cancelled()?;
    context.write_node_state(
        "resolve-project-config",
        &["gradle.properties", "versioned Gradle fragments"],
        &[plan.state_dir.join("last-plan.json")],
        "complete",
    )?;
    tracing::info!("Build node resolve-maven-and-minecraft-inputs: recording resolved artifacts");
    context.bail_if_cancelled()?;
    context.write_node_state(
        "resolve-maven-and-minecraft-inputs",
        &[
            "Maven repositories",
            VERSION_MANIFEST_URL,
            "sfm-toolchain.lock.json",
        ],
        &[
            plan.maven_cache_dir.clone(),
            plan.minecraft_version_cache_dir.clone(),
            plan.minecraft_libraries_dir.clone(),
            plan.minecraft_assets_dir.clone(),
        ],
        "complete",
    )?;

    if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let started = Instant::now();
        tracing::info!("Build node execute-neoform-userdev: start");
        context.bail_if_cancelled()?;
        execute_neoform_userdev(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node execute-neoform-userdev: done in {} ms",
            started.elapsed().as_millis()
        );
    } else {
        ensure_forge_gradle_execution_supported(plan)?;
        let started = Instant::now();
        tracing::info!("Build node execute-mcp-config-joined: start");
        context.bail_if_cancelled()?;
        execute_mcp_config_joined(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node execute-mcp-config-joined: done in {} ms",
            started.elapsed().as_millis()
        );

        let started = Instant::now();
        tracing::info!("Build node execute-forge-userdev: start");
        context.bail_if_cancelled()?;
        execute_forge_userdev(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node execute-forge-userdev: done in {} ms",
            started.elapsed().as_millis()
        );
    }
    if target == BuildTarget::SourceOutputs {
        tracing::info!(
            "Rust transformed source outputs prepared in {} ms",
            total_started.elapsed().as_millis()
        );
        return Ok(());
    }
    let started = Instant::now();
    tracing::info!("Build node deobfuscate-mod-dependencies: start");
    context.bail_if_cancelled()?;
    execute_dependency_deobf(&context)?;
    context.bail_if_cancelled()?;
    tracing::info!(
        "Build node deobfuscate-mod-dependencies: done in {} ms",
        started.elapsed().as_millis()
    );
    let started = Instant::now();
    tracing::info!("Build node compile-project: start");
    context.bail_if_cancelled()?;
    execute_project_compile(&context)?;
    context.bail_if_cancelled()?;
    tracing::info!(
        "Build node compile-project: done in {} ms",
        started.elapsed().as_millis()
    );
    if target == BuildTarget::Jar {
        let started = Instant::now();
        tracing::info!("Build node package-and-reobfuscate-jar: start");
        context.bail_if_cancelled()?;
        execute_package_and_reobfuscate(&context)?;
        context.bail_if_cancelled()?;
        tracing::info!(
            "Build node package-and-reobfuscate-jar: done in {} ms",
            started.elapsed().as_millis()
        );

        if !plan.rust_output_jar.is_file() {
            eyre::bail!(
                "Build finished without producing Rust output jar: {}",
                plan.rust_output_jar.display()
            );
        }

        tracing::info!(
            "Rust jar build completed in {} ms",
            total_started.elapsed().as_millis()
        );
    } else {
        tracing::info!(
            "Rust run build outputs prepared in {} ms",
            total_started.elapsed().as_millis()
        );
    }

    Ok(())
}

impl RunKind {
    const fn userdev_name(self) -> &'static str {
        match self {
            Self::Client | Self::ClientSmoke | Self::ClientPuppet => "client",
            Self::Server => "server",
            Self::Data => "data",
            Self::GameTestServer => "gameTestServer",
            Self::Test => "test",
        }
    }

    const fn userdev_names(self) -> &'static [&'static str] {
        match self {
            Self::Data => &["data", "clientData"],
            Self::Client | Self::ClientSmoke | Self::ClientPuppet => &["client"],
            Self::Server => &["server"],
            Self::GameTestServer => &["gameTestServer"],
            Self::Test => &["test"],
        }
    }

    const fn command_name(self) -> &'static str {
        match self {
            Self::Client => "runClient",
            Self::ClientSmoke => "runClientSmoke",
            Self::ClientPuppet => "runClientPuppet",
            Self::Server => "runServer",
            Self::Data => "runData",
            Self::GameTestServer => "runGameTestServer",
            Self::Test => "runTest",
        }
    }

    const fn working_dir_name(self) -> &'static str {
        match self {
            Self::Client => "run",
            Self::ClientSmoke => "runClientSmoke",
            Self::ClientPuppet => "runClientPuppet",
            Self::Server => "runServer",
            Self::Data => "runData",
            Self::GameTestServer => "runGameTest",
            Self::Test => "runTest",
        }
    }

    const fn optional_source_set(self) -> &'static str {
        match self {
            Self::Client
            | Self::ClientSmoke
            | Self::ClientPuppet
            | Self::Server
            | Self::GameTestServer => "gametest",
            Self::Data => "datagen",
            Self::Test => "test",
        }
    }

    const fn enables_game_tests(self) -> bool {
        matches!(
            self,
            Self::Client
                | Self::ClientSmoke
                | Self::ClientPuppet
                | Self::Server
                | Self::GameTestServer
        )
    }

    const fn game_test_max_program_run_millis(self) -> &'static str {
        match self {
            Self::Client | Self::ClientSmoke | Self::ClientPuppet => "1000",
            Self::Server | Self::GameTestServer | Self::Data | Self::Test => "150",
        }
    }

    const fn automation_mode(self) -> Option<&'static str> {
        match self {
            Self::ClientSmoke => Some("smoke"),
            Self::ClientPuppet => Some("puppet"),
            _ => None,
        }
    }

    fn launch_timeout(self, run_options: &RunOptions) -> Option<Duration> {
        match self {
            Self::ClientSmoke => Some(Duration::from_mins(2)),
            Self::ClientPuppet => client_puppet_launch_timeout(run_options),
            _ => None,
        }
    }
}

fn client_puppet_launch_timeout(run_options: &RunOptions) -> Option<Duration> {
    let keep_open_seconds = run_options.client_puppet_keep_open.countdown_seconds()?;
    Some(
        Duration::from_mins(15)
            .checked_add(Duration::from_secs(keep_open_seconds))
            .unwrap_or(Duration::MAX),
    )
}

const fn run_max_launch_attempts(kind: RunKind) -> usize {
    match kind {
        RunKind::GameTestServer => 3,
        _ => 1,
    }
}

#[derive(Debug, Clone, Default, Facet)]
#[facet(rename_all = "camelCase")]
struct ForgeRunConfig {
    #[facet(default)]
    main: String,
    #[facet(default)]
    args: Vec<String>,
    #[facet(default)]
    jvm_args: Vec<String>,
    #[facet(default)]
    env: BTreeMap<String, String>,
    #[facet(default)]
    props: BTreeMap<String, String>,
}

#[derive(Debug)]
struct MinecraftAssets {
    root: PathBuf,
    index_id: String,
}

#[derive(Debug)]
struct RunClasspath {
    legacy: Vec<PathBuf>,
    userdev_mods: Vec<PathBuf>,
}

#[derive(Debug)]
struct LaunchOutput {
    status: ExitStatus,
    combined: String,
    timed_out: bool,
    cancelled: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum LaunchOutputMode {
    Terminal,
    LogFile,
}

#[derive(Debug)]
struct CancellableOutput {
    status: ExitStatus,
    stdout: Vec<u8>,
    stderr: Vec<u8>,
    cancelled: bool,
}

#[derive(Debug)]
struct GameTestBisectAttempt {
    run_number: usize,
    selected_tests: Vec<String>,
    failed_tests: Vec<String>,
    target_failed: bool,
    saved_log: PathBuf,
}

fn run_state_dir(plan: &BuildPlan, kind: RunKind) -> PathBuf {
    plan.cache_dir.join("run").join(kind.command_name())
}

#[expect(
    clippy::too_many_lines,
    reason = "The bisection loop is easier to audit when the target/subset/complement flow stays together."
)]
fn execute_game_test_bisect(
    plan: &BuildPlan,
    kind: RunKind,
    run_options: &RunOptions,
    dry_run: bool,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    if !matches!(kind, RunKind::GameTestServer) {
        eyre::bail!("Game-test bisection is only supported for runGameTestServer.");
    }
    if dry_run {
        eyre::bail!("Game-test bisection launches Minecraft and does not support --dry-run.");
    }
    let bisect_options = run_options
        .game_test_bisect
        .as_ref()
        .ok_or_else(|| eyre::eyre!("Missing game-test bisection options"))?;
    if matches!(bisect_options.max_runs, Some(0 | 1)) {
        eyre::bail!("Game-test bisection needs at least 2 runs.");
    }

    let target = normalize_sfm_game_test_name(&bisect_options.target);
    if target.is_empty() {
        eyre::bail!("Game-test bisection target must not be empty.");
    }

    tracing::info!(
        "Starting game-test bisection for {} on {}.",
        qualify_sfm_game_test_name(&target),
        plan.branch_name
    );

    let mut run_count = 0usize;
    let baseline_selection =
        initial_game_test_bisect_selection(&target, run_options.game_test_filter.as_deref());
    let baseline = run_game_test_bisect_attempt(
        plan,
        kind,
        run_options,
        baseline_selection.as_deref(),
        &target,
        "baseline",
        &mut run_count,
        cancellation_token,
    )?;
    if !baseline
        .selected_tests
        .iter()
        .any(|test| game_test_name_matches(test, &target))
    {
        eyre::bail!(
            "Baseline game-test run did not discover target {}. See {}",
            qualify_sfm_game_test_name(&target),
            baseline.saved_log.display()
        );
    }
    if !baseline.target_failed {
        eyre::bail!(
            "Baseline game-test run did not fail target {} (failed tests: {}). See {}",
            qualify_sfm_game_test_name(&target),
            format_game_test_list(&baseline.failed_tests),
            baseline.saved_log.display()
        );
    }

    let target_only_selection = exact_game_test_selection(&target, &[]);
    let target_only = run_game_test_bisect_attempt(
        plan,
        kind,
        run_options,
        Some(&target_only_selection),
        &target,
        "target-only",
        &mut run_count,
        cancellation_token,
    )?;
    if target_only.target_failed {
        eyre::bail!(
            "Target {} also fails when run alone. See {}",
            qualify_sfm_game_test_name(&target),
            target_only.saved_log.display()
        );
    }

    let mut current = baseline
        .selected_tests
        .iter()
        .map(|name| normalize_sfm_game_test_name(name))
        .filter(|name| !name.is_empty() && !game_test_name_matches(name, &target))
        .collect::<Vec<_>>();
    current = dedup_strings_preserve_order(current);
    if current.is_empty() {
        eyre::bail!(
            "Baseline failed {}, but no other SFM game tests were discovered to bisect.",
            qualify_sfm_game_test_name(&target)
        );
    }

    tracing::info!(
        "Baseline reproduced target failure with {} other SFM game test(s); target-only passed.",
        current.len()
    );

    let mut granularity = 2usize;
    let mut stopped_by_budget = false;
    while current.len() > 1 {
        if game_test_bisect_budget_exhausted(run_count, bisect_options.max_runs) {
            stopped_by_budget = true;
            break;
        }

        let partitions = partition_game_test_candidates(&current, granularity);
        let mut reduced = false;

        for subset in &partitions {
            if game_test_bisect_budget_exhausted(run_count, bisect_options.max_runs) {
                stopped_by_budget = true;
                break;
            }
            let selection = exact_game_test_selection(&target, subset);
            let attempt = run_game_test_bisect_attempt(
                plan,
                kind,
                run_options,
                Some(&selection),
                &target,
                &format!("subset-{}-of-{}", subset.len(), current.len()),
                &mut run_count,
                cancellation_token,
            )?;
            if attempt.target_failed {
                tracing::info!(
                    "Reduced inducing set from {} to {} test(s) using subset run #{}.",
                    current.len(),
                    subset.len(),
                    attempt.run_number
                );
                current.clone_from(subset);
                granularity = granularity.saturating_sub(1).max(2);
                reduced = true;
                break;
            }
        }
        if stopped_by_budget || reduced {
            continue;
        }

        for subset in &partitions {
            if game_test_bisect_budget_exhausted(run_count, bisect_options.max_runs) {
                stopped_by_budget = true;
                break;
            }
            let complement = game_test_candidate_complement(&current, subset);
            if complement.is_empty() {
                continue;
            }
            let selection = exact_game_test_selection(&target, &complement);
            let attempt = run_game_test_bisect_attempt(
                plan,
                kind,
                run_options,
                Some(&selection),
                &target,
                &format!("complement-{}-of-{}", complement.len(), current.len()),
                &mut run_count,
                cancellation_token,
            )?;
            if attempt.target_failed {
                tracing::info!(
                    "Reduced inducing set from {} to {} test(s) using complement run #{}.",
                    current.len(),
                    complement.len(),
                    attempt.run_number
                );
                current = complement;
                granularity = granularity.saturating_sub(1).max(2);
                reduced = true;
                break;
            }
        }
        if stopped_by_budget || reduced {
            continue;
        }

        if granularity >= current.len() {
            break;
        }
        granularity = (granularity * 2).min(current.len());
        tracing::info!(
            "No reduction at previous granularity; increasing to {} partition(s).",
            granularity
        );
    }

    write_game_test_bisect_result(plan, kind, &target, &current, run_count, stopped_by_budget)?;
    if stopped_by_budget {
        tracing::warn!(
            "Stopped game-test bisection after {run_count} run(s) due to --max-runs. Current inducing set has {} test(s).",
            current.len()
        );
    } else {
        tracing::info!(
            "Game-test bisection finished after {run_count} run(s). Inducing set has {} test(s).",
            current.len()
        );
    }
    tracing::info!(
        "Reproduce with --filter {}",
        exact_game_test_selection(&target, &current)
    );
    for test in &current {
        tracing::info!(
            "Inducing companion test: {}",
            qualify_sfm_game_test_name(test)
        );
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "Run launch orchestration intentionally mirrors Forge userdev config shape."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        kind = kind.command_name(),
        loader = ?plan.loader_toolchain.kind,
        dry_run,
    )
)]
fn execute_run(
    plan: &BuildPlan,
    kind: RunKind,
    run_options: &RunOptions,
    dry_run: bool,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    if matches!(kind, RunKind::Test) {
        return execute_junit_tests(
            plan,
            dry_run,
            &RunTestOptions::default(),
            cancellation_token,
        );
    }

    let context = ExecutionContext::new(plan, cancellation_token.clone())?;
    let run_config = read_forge_run_config(&context, kind)?;
    context.bail_if_cancelled()?;
    if run_config.main.is_empty() {
        eyre::bail!(
            "Forge userdev run config {} did not declare a main class",
            kind.userdev_name()
        );
    }
    let launch_main = run_config.main.clone();

    let run_state_dir = run_state_dir(plan, kind);
    fs::create_dir_all(&run_state_dir)?;
    let working_dir = plan.minecraft_dir.join(kind.working_dir_name());
    fs::create_dir_all(&working_dir)?;
    if matches!(kind, RunKind::GameTestServer) {
        clean_gametest_server_world(&plan.minecraft_dir, &working_dir)?;
    }
    if matches!(kind, RunKind::ClientPuppet) {
        clean_client_puppet_world(&plan.minecraft_dir, &working_dir)?;
    }
    let automation_options_path =
        prepare_client_automation_options(&plan.minecraft_dir, &working_dir, kind)?;

    let run_lockfile = if plan.refresh {
        None
    } else {
        plan.lockfile.clone()
    };
    let resolver = Resolver::new(
        plan.maven_cache_dir.clone(),
        plan.repositories.clone(),
        false,
        plan.allow_local_artifact_cache,
        plan.artifact_sources.clone(),
        run_lockfile.clone(),
        plan.lockfile.clone(),
        context.cancellation_token.clone(),
    )?;
    let modules = resolve_forge_userdev_modules(&context, &resolver)?;
    context.bail_if_cancelled()?;
    let launch_classpath = resolve_run_classpath(&context, &resolver, kind, run_options)?;
    context.bail_if_cancelled()?;
    let run_cache_artifact_paths = run_lockfile_cache_artifact_paths(&launch_classpath, &modules);
    write_artifact_lockfile_with_extra_cache_paths(plan, &run_cache_artifact_paths)?;
    let minecraft_classpath_file = run_state_dir.join("minecraftClasspath.txt");
    write_classpath_file(&minecraft_classpath_file, &launch_classpath.legacy)?;

    let assets = if run_config_requires_assets(&run_config) {
        Some(prepare_minecraft_assets(&context)?)
    } else {
        None
    };
    context.bail_if_cancelled()?;

    let source_roots = run_source_roots(&context, kind, run_options)?;
    let mcp_mappings = run_mcp_mappings(plan);
    let module_path = join_classpath(&modules);
    let minecraft_classpath_file_text = minecraft_classpath_file.display().to_string();
    let assets_root = assets
        .as_ref()
        .map_or_else(String::new, |assets| assets.root.display().to_string());
    let asset_index = assets
        .as_ref()
        .map_or_else(String::new, |assets| assets.index_id.clone());
    let replacements = [
        ("{modules}", module_path.as_str()),
        ("{source_roots}", source_roots.as_str()),
        ("{mcp_mappings}", mcp_mappings.as_str()),
        (
            "{minecraft_classpath_file}",
            minecraft_classpath_file_text.as_str(),
        ),
        ("{asset_index}", asset_index.as_str()),
        ("{assets_root}", assets_root.as_str()),
    ];

    let mut properties = run_config.props.clone();
    properties.insert(
        "forge.logging.markers".to_string(),
        "REGISTRIES".to_string(),
    );
    properties.insert(
        "forge.logging.console.level".to_string(),
        "info".to_string(),
    );
    properties.insert("mixin.env.remapRefMap".to_string(), "true".to_string());
    if plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        let refmap_remapping_file = ensure_run_refmap_remapping_file(&context)?;
        properties.insert(
            "mixin.env.refMapRemappingFile".to_string(),
            refmap_remapping_file.display().to_string(),
        );
        properties.insert(
            "net.minecraftforge.gradle.GradleStart.srg.srg-mcp".to_string(),
            refmap_remapping_file.display().to_string(),
        );
    }
    if kind.enables_game_tests() {
        let game_test_property = game_test_namespace_property(&context)?;
        properties.insert(
            game_test_property,
            required_property(&plan.properties, "mod_id")?.to_string(),
        );
        properties.insert(
            "sfm.gametest.maxProgramRunMillis".to_string(),
            kind.game_test_max_program_run_millis().to_string(),
        );
    }
    apply_game_test_filter_property(&mut properties, kind, run_options);
    if let Some(automation_mode) = kind.automation_mode() {
        properties.insert(
            "sfm.clientRun.mode".to_string(),
            automation_mode.to_string(),
        );
    }
    apply_client_title_screen_property(&mut properties, kind, run_options);
    apply_client_puppet_keep_open_property(&mut properties, kind, run_options);
    let launch_timeout = kind.launch_timeout(run_options);

    let mut jvm_args = properties
        .into_iter()
        .map(|(key, value)| format!("-D{key}={}", replace_placeholders(&value, &replacements)))
        .collect::<Vec<_>>();
    jvm_args.extend(
        run_config
            .jvm_args
            .iter()
            .map(|arg| replace_placeholders(arg, &replacements)),
    );
    jvm_args.extend([
        "-XX:+IgnoreUnrecognizedVMOptions".to_string(),
        "-XX:+AllowEnhancedClassRedefinition".to_string(),
        "-XX:+AllowRedefinitionToAddDeleteMethods".to_string(),
    ]);
    if matches!(kind, RunKind::Client) && let Some(port) = run_options.client_hotswap_port {
        jvm_args.push(format!(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:{port}"
        ));
        tracing::info!(port, "client hotswap JDWP enabled");
    }

    let mut program_args = run_config
        .args
        .iter()
        .map(|arg| replace_placeholders(arg, &replacements))
        .collect::<Vec<_>>();
    program_args.extend(kind_extra_program_args(plan, kind)?);
    if !matches!(kind, RunKind::Data) {
        program_args.extend(["--mixin.config".to_string(), "sfm.mixins.json".to_string()]);
    }

    let mut env = BTreeMap::new();
    for (key, value) in &run_config.env {
        env.insert(key.clone(), replace_placeholders(value, &replacements));
    }
    env.insert("MOD_CLASSES".to_string(), source_roots);
    env.insert("MCP_MAPPINGS".to_string(), mcp_mappings);

    let mut java_classpath_inputs = launch_classpath
        .legacy
        .iter()
        .cloned()
        .chain(launch_classpath.userdev_mods.iter().cloned())
        .chain(modules.iter().cloned())
        .collect::<Vec<_>>();
    if launch_main.starts_with("net.neoforged.fml.startup.") {
        java_classpath_inputs.extend(run_source_root_paths(&context, kind, run_options)?);
    }
    let java_classpath = dedup_paths_preserve_order(java_classpath_inputs);
    let mut java_args = Vec::new();
    java_args.extend(jvm_args);
    java_args.extend(["-cp".to_string(), join_classpath(&java_classpath)]);
    java_args.push(launch_main);
    java_args.extend(program_args);

    let argfile = run_state_dir.join("launch.java.args");
    fs::write(
        &argfile,
        java_args
            .into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;

    tracing::info!(
        "Launching {} from {}",
        kind.command_name(),
        working_dir.display()
    );
    tracing::info!("Launch args: {}", argfile.display());
    tracing::info!(
        "MOD_CLASSES={}",
        env.get("MOD_CLASSES").map_or("", String::as_str)
    );
    tracing::info!(
        "Userdev mod jars on launch classpath: {}",
        launch_classpath.userdev_mods.len()
    );
    tracing::info!(
        kind = kind.command_name(),
        argfile = %argfile.display(),
        legacy_classpath_entries = launch_classpath.legacy.len(),
        userdev_mods = launch_classpath.userdev_mods.len(),
        "run_setup_complete"
    );

    let launch_log = run_state_dir.join("console.log");
    if dry_run {
        let mut run_outputs = vec![argfile.clone(), minecraft_classpath_file.clone()];
        if let Some(path) = automation_options_path {
            run_outputs.push(path);
        }
        context.write_node_state(
            &format!("run-{}", kind.userdev_name()),
            &["Forge userdev run config", "Rust-owned build outputs"],
            &run_outputs,
            "dry-run",
        )?;
        tracing::info!(
            "Dry run prepared {} launch setup and skipped Minecraft JVM launch.",
            kind.command_name()
        );
        tracing::info!(
            kind = kind.command_name(),
            argfile = %argfile.display(),
            "run_dry_run_skip_launch"
        );
        return Ok(());
    }
    let max_launch_attempts = run_max_launch_attempts(kind);
    let mut launch_output = None;
    for attempt in 1..=max_launch_attempts {
        context.bail_if_cancelled()?;
        if matches!(kind, RunKind::GameTestServer) {
            clean_gametest_server_world(&plan.minecraft_dir, &working_dir)?;
            tracing::info!("Game-test server attempt {attempt}/{max_launch_attempts}");
        }
        if matches!(kind, RunKind::ClientPuppet) {
            clean_client_puppet_world(&plan.minecraft_dir, &working_dir)?;
        }
        let attempt_output = run_launch_command(
            &context.cancellation_token,
            plan,
            &argfile,
            &working_dir,
            &env,
            &launch_log,
            launch_timeout,
        )
        .wrap_err_with(|| {
            format!(
                "Failed to launch {} using {}",
                kind.command_name(),
                plan.java.executable.display()
            )
        })?;
        if attempt_output.status.success() || attempt == max_launch_attempts {
            launch_output = Some(attempt_output);
            break;
        }
        tracing::info!(
            "{} attempt {attempt}/{max_launch_attempts} exited with {}; retrying. See {}",
            kind.command_name(),
            attempt_output.status,
            launch_log.display()
        );
    }
    let launch_output = launch_output.ok_or_else(|| {
        eyre::eyre!(
            "Failed to launch {} using {}",
            kind.command_name(),
            plan.java.executable.display()
        )
    })?;

    let mut run_outputs = vec![
        argfile.clone(),
        minecraft_classpath_file.clone(),
        launch_log.clone(),
    ];
    if let Some(path) = automation_options_path {
        run_outputs.push(path);
    }
    context.write_node_state(
        &format!("run-{}", kind.userdev_name()),
        &["Forge userdev run config", "Rust-owned build outputs"],
        &run_outputs,
        if launch_output.status.success() {
            "complete"
        } else {
            "failed"
        },
    )?;

    if launch_output.timed_out {
        eyre::bail!(
            "{} timed out after {} seconds. See {}",
            kind.command_name(),
            launch_timeout.map_or(0, |timeout| timeout.as_secs()),
            launch_log.display()
        );
    }
    if launch_output.cancelled {
        eyre::bail!(
            "{} was cancelled by Ctrl+C. See {}",
            kind.command_name(),
            launch_log.display()
        );
    }
    if !launch_output.status.success() {
        eyre::bail!(
            "{} exited with {}. See {}",
            kind.command_name(),
            launch_output.status,
            launch_log.display()
        );
    }
    if matches!(kind, RunKind::GameTestServer) {
        let Some(pass_count) = extract_required_gametest_pass_count(&launch_output.combined) else {
            let running_count = extract_running_gametest_count(&launch_output.combined)
                .map_or_else(|| "unknown".to_string(), |count| count.to_string());
            eyre::bail!(
                "{} exited successfully but did not report a required game-test pass count (running count: {}). See {}",
                kind.command_name(),
                running_count,
                launch_log.display()
            );
        };
        if pass_count == 0 {
            eyre::bail!(
                "{} reported 0 required game tests passed. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        tracing::info!("Validated {pass_count} required game tests passed.");
    }
    if matches!(kind, RunKind::ClientSmoke) {
        if !launch_output.combined.contains("SFM_CLIENT_SMOKE_READY") {
            eyre::bail!(
                "{} exited successfully but did not report title-screen readiness. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        tracing::info!("Validated client reached the title screen.");
    }
    if matches!(kind, RunKind::ClientPuppet) {
        if let Some(failure) = extract_client_puppet_failure(&launch_output.combined) {
            eyre::bail!(
                "{} reported {} required and {} optional game-test failures (required count: {}, total: {}). See {}",
                kind.command_name(),
                failure.required_failed,
                failure.optional_failed,
                failure.required_count,
                failure.total_count,
                launch_log.display()
            );
        }
        let Some(pass_count) = extract_client_puppet_pass_count(&launch_output.combined) else {
            eyre::bail!(
                "{} exited successfully but did not report a client puppet pass count. See {}",
                kind.command_name(),
                launch_log.display()
            );
        };
        if pass_count == 0 {
            eyre::bail!(
                "{} reported 0 required game tests passed. See {}",
                kind.command_name(),
                launch_log.display()
            );
        }
        tracing::info!("Validated client puppet completed {pass_count} required game tests.");
    }
    Ok(())
}

const JUNIT_EVENT_PREFIX: &str = "SFM_JUNIT\t";
const JUNIT_EVENT_RUNNER_MAIN_CLASS: &str = "dev.teamdman.sfm.toolchain.SfmJUnitRunner";
const JUNIT_EVENT_RUNNER_SOURCE: &str = include_str!("junit_event_runner.java");

#[expect(
    clippy::too_many_lines,
    reason = "JUnit execution mirrors the run setup flow while avoiding Gradle."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        dry_run,
        action = ?test_options.action,
        filter = test_options.filter.as_deref().unwrap_or(""),
        no_capture = test_options.no_capture,
    )
)]
fn execute_junit_tests(
    plan: &BuildPlan,
    dry_run: bool,
    test_options: &RunTestOptions,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let context = ExecutionContext::new(plan, cancellation_token.clone())?;
    let project_root = plan.cache_dir.join("project");
    let classes_dir = project_root.join("classes");
    let staged_resources_dir = project_root.join("staged-resources");
    let test_classes_dir = project_root.join("test").join("classes");
    let test_resources_dir = project_root.join("test").join("resources");
    let run_state_dir = plan
        .cache_dir
        .join("run")
        .join(RunKind::Test.command_name());
    fs::create_dir_all(&run_state_dir)?;

    let locked_resolver = Resolver::new(
        plan.maven_cache_dir.clone(),
        plan.repositories.clone(),
        plan.refresh,
        plan.allow_local_artifact_cache,
        plan.artifact_sources.clone(),
        plan.lockfile.clone(),
        plan.lockfile.clone(),
        context.cancellation_token.clone(),
    )?;
    let test_resolver = Resolver::new(
        plan.maven_cache_dir.clone(),
        plan.repositories.clone(),
        plan.refresh,
        plan.allow_local_artifact_cache,
        plan.artifact_sources.clone(),
        None,
        None,
        context.cancellation_token.clone(),
    )?;
    context.bail_if_cancelled()?;

    let antlr_classpath = resolve_antlr_classpath(&context, &locked_resolver)?;
    let base_classpath =
        resolve_project_compile_classpath(&context, &locked_resolver, &antlr_classpath)?;
    let test_compile_dependencies =
        resolve_test_dependency_classpath(&context, &test_resolver, TestClasspathKind::Compile)?;
    context.bail_if_cancelled()?;

    let mut test_compile_classpath = base_classpath.clone();
    test_compile_classpath.extend(test_compile_dependencies.iter().cloned());
    test_compile_classpath = dedup_paths_preserve_order(test_compile_classpath);

    let mut upstream_fingerprint_paths = vec![classes_dir.clone(), staged_resources_dir.clone()];
    upstream_fingerprint_paths.extend(test_compile_classpath.iter().cloned());
    let upstream_fingerprint = input_fingerprint(
        &context,
        "javac-test-upstream",
        &upstream_fingerprint_paths,
        &[
            plan.java.version_output.clone(),
            plan.java_release.to_string(),
            format!("{:?}", plan.loader_toolchain.kind),
        ],
    )?;

    let started = Instant::now();
    tracing::info!("Build node compile-test: start");
    compile_optional_java_source_set(
        &context,
        "test",
        &test_compile_classpath,
        &classes_dir,
        &test_classes_dir,
        &upstream_fingerprint,
    )
    .wrap_err("Failed to compile test source set")?;
    stage_optional_resource_source_set(&context, "test", &test_resources_dir, &[])
        .wrap_err("Failed to stage test resources")?;
    tracing::info!(
        "Build node compile-test: done in {} ms",
        started.elapsed().as_millis()
    );
    context.bail_if_cancelled()?;

    if matches!(test_options.action, RunTestAction::Compile) {
        write_artifact_lockfile_with_extra_cache_paths(plan, &test_compile_dependencies)?;
        context.write_node_state(
            "run-compile",
            &["Rust-owned main/gametest/datagen/test outputs"],
            &[test_classes_dir, test_resources_dir],
            if dry_run { "dry-run" } else { "complete" },
        )?;
        tracing::info!(
            "Compiled all Java source sets (main, gametest, datagen, test) without launch."
        );
        return Ok(());
    }

    let test_runtime_dependencies =
        resolve_test_dependency_classpath(&context, &test_resolver, TestClasspathKind::Runtime)?;
    let console_launcher = resolve_junit_console_standalone(&context, &test_resolver)?.cache_path;
    context.bail_if_cancelled()?;

    let mut test_runtime_classpath = vec![
        test_classes_dir.clone(),
        test_resources_dir.clone(),
        staged_resources_dir.clone(),
        classes_dir.clone(),
    ];
    test_runtime_classpath.extend(base_classpath);
    test_runtime_classpath.extend(test_compile_dependencies.iter().cloned());
    test_runtime_classpath.extend(test_runtime_dependencies.iter().cloned());
    test_runtime_classpath = dedup_paths_preserve_order(test_runtime_classpath);

    let runtime_classpath_file = run_state_dir.join("testRuntimeClasspath.txt");
    write_classpath_file(&runtime_classpath_file, &test_runtime_classpath)?;
    let runner_classes_dir = compile_junit_event_runner(&context, &console_launcher)?;
    let argfile = run_state_dir.join("junit.java.args");
    let test_source_root = plan.minecraft_dir.join("src").join("test").join("java");
    write_junit_runner_argfile(
        &argfile,
        &runner_classes_dir,
        &console_launcher,
        &test_runtime_classpath,
        &test_classes_dir,
        &test_source_root,
        test_options,
    )?;

    let mut extra_cache_paths = Vec::new();
    extra_cache_paths.extend(test_compile_dependencies);
    extra_cache_paths.extend(test_runtime_dependencies);
    extra_cache_paths.push(console_launcher.clone());
    write_artifact_lockfile_with_extra_cache_paths(plan, &extra_cache_paths)?;

    tracing::info!(
        "Launching JUnit tests from {}",
        plan.minecraft_dir.display()
    );
    tracing::info!("JUnit args: {}", argfile.display());
    tracing::info!(
        runtime_classpath_entries = test_runtime_classpath.len(),
        argfile = %argfile.display(),
        "junit_test_setup_complete"
    );

    let console_log = run_state_dir.join("console.log");
    if dry_run {
        context.write_node_state(
            "run-test",
            &["SFM JUnit event runner", "Rust-owned test outputs"],
            &[
                argfile.clone(),
                runtime_classpath_file,
                runner_classes_dir,
                test_classes_dir,
                test_resources_dir,
            ],
            "dry-run",
        )?;
        tracing::info!("Dry run prepared runTest launch setup and skipped JUnit execution.");
        tracing::info!(argfile = %argfile.display(), "run_test_dry_run_skip_launch");
        return Ok(());
    }

    let mut command = Command::new(&plan.java.executable);
    command
        .arg(format!("@{}", argfile.display()))
        .current_dir(&plan.minecraft_dir);
    let started = Instant::now();
    let output =
        run_command_capture_output(&context.cancellation_token, &mut command, "junit-test")
            .wrap_err_with(|| {
                format!(
                    "Failed to launch JUnit tests using {}",
                    plan.java.executable.display()
                )
            })?;
    let report = process_junit_event_output(
        plan,
        test_options,
        &console_log,
        &output,
        started.elapsed().as_millis(),
    )?;

    context.write_node_state(
        "run-test",
        &["SFM JUnit event runner", "Rust-owned test outputs"],
        &[
            argfile,
            runtime_classpath_file,
            console_log.clone(),
            runner_classes_dir,
            test_classes_dir,
            test_resources_dir,
        ],
        if output.status.success() {
            "complete"
        } else {
            "failed"
        },
    )?;

    if output.cancelled {
        eyre::bail!(
            "runTest was cancelled by Ctrl+C. See {}",
            console_log.display()
        );
    }
    if !output.status.success() {
        eyre::bail!(
            "runTest exited with {} ({}). See {}",
            output.status,
            report.summary_message(),
            console_log.display()
        );
    }
    if !report.protocol_errors.is_empty() {
        eyre::bail!(
            "runTest produced {} malformed protocol event(s). See {}",
            report.protocol_errors.len(),
            console_log.display()
        );
    }
    tracing::info!("JUnit tests completed successfully.");
    Ok(())
}

fn compile_junit_event_runner(
    context: &ExecutionContext<'_>,
    console_launcher: &Path,
) -> eyre::Result<PathBuf> {
    context.bail_if_cancelled()?;
    let runner_root = context.plan.cache_dir.join("junit-runner");
    let source_dir = runner_root
        .join("src")
        .join("dev")
        .join("teamdman")
        .join("sfm")
        .join("toolchain");
    let source_file = source_dir.join("SfmJUnitRunner.java");
    let classes_dir = runner_root.join("classes");
    fs::create_dir_all(&source_dir)?;
    write_file_if_changed(&source_file, JUNIT_EVENT_RUNNER_SOURCE.as_bytes())?;

    let argfile = runner_root.join("javac-junit-runner.args");
    write_javac_no_ap_argfile(
        context,
        &argfile,
        &[console_launcher.to_path_buf()],
        std::slice::from_ref(&source_file),
        &classes_dir,
    )?;
    let fingerprint = input_fingerprint(
        context,
        "javac-junit-runner",
        &[
            source_file.clone(),
            argfile.clone(),
            console_launcher.to_path_buf(),
        ],
        &[
            context.plan.java.version_output.clone(),
            context.plan.java_release.to_string(),
        ],
    )?;
    let state_path = runner_root.join("javac-junit-runner.inputs.sha1");
    if cache_state_matches(context, &state_path, &fingerprint, &[&classes_dir])? {
        tracing::info!(
            "javac junit-runner: reused cached outputs at {}",
            classes_dir.display()
        );
        return Ok(classes_dir);
    }

    reset_cache_directory(&context.plan.cache_dir, &classes_dir)?;
    let mut command = Command::new(javac_executable(&context.plan.java));
    command.arg(format!("@{}", argfile.display()));
    let output = run_command_capture_output(
        &context.cancellation_token,
        &mut command,
        "javac-junit-runner",
    )
    .wrap_err("Failed to run javac for SFM JUnit event runner")?;
    trace_subprocess_bytes(
        context.plan,
        "java-tool",
        "javac-junit-runner",
        "stdout",
        &output.stdout,
    );
    trace_subprocess_bytes(
        context.plan,
        "java-tool",
        "javac-junit-runner",
        "stderr",
        &output.stderr,
    );
    let log_path = runner_root.join("javac-junit-runner.log");
    let mut log = Vec::new();
    log.extend_from_slice(b"--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(&log_path, log)
        .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    if output.cancelled {
        eyre::bail!(
            "javac junit-runner was cancelled by Ctrl+C. See {}",
            log_path.display()
        );
    }
    if !output.status.success() {
        eyre::bail!(
            "javac junit-runner failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    write_cache_state(&state_path, &fingerprint)?;
    Ok(classes_dir)
}

fn write_file_if_changed(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
    if path.is_file() && fs::read(path).is_ok_and(|existing| existing == bytes) {
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, bytes).wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn write_junit_runner_argfile(
    argfile: &Path,
    runner_classes_dir: &Path,
    console_launcher: &Path,
    runtime_classpath: &[PathBuf],
    test_classes_dir: &Path,
    test_source_root: &Path,
    test_options: &RunTestOptions,
) -> eyre::Result<()> {
    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut command_classpath = vec![
        runner_classes_dir.to_path_buf(),
        console_launcher.to_path_buf(),
    ];
    command_classpath.extend(runtime_classpath.iter().cloned());
    let mut args = vec![
        "-cp".to_string(),
        join_classpath(&command_classpath),
        JUNIT_EVENT_RUNNER_MAIN_CLASS.to_string(),
        "--mode".to_string(),
        match test_options.action {
            RunTestAction::Run => "run".to_string(),
            RunTestAction::List => "list".to_string(),
            RunTestAction::Compile => "compile".to_string(),
        },
        "--classpath-root".to_string(),
        test_classes_dir.display().to_string(),
        "--source-root".to_string(),
        test_source_root.display().to_string(),
    ];
    if let Some(filter) = test_options
        .filter
        .as_ref()
        .filter(|filter| !filter.trim().is_empty())
    {
        args.extend(["--filter".to_string(), filter.clone()]);
    }
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))
}

#[derive(Clone, Debug, Default)]
struct JunitTestInfo {
    unique_id: String,
    display_name: String,
    legacy_name: String,
    source_path: String,
}

impl JunitTestInfo {
    fn label(&self) -> &str {
        if !self.legacy_name.is_empty() {
            &self.legacy_name
        } else if !self.display_name.is_empty() {
            &self.display_name
        } else {
            "unknown-test"
        }
    }

    fn key(&self) -> String {
        if self.unique_id.is_empty() {
            self.label().to_string()
        } else {
            self.unique_id.clone()
        }
    }

    fn uri(&self) -> String {
        vscode_file_uri_for_path(&self.source_path)
    }
}

#[derive(Clone, Debug)]
struct JunitCapturedOutput {
    stream: String,
    line: String,
}

#[derive(Clone, Debug)]
struct JunitFinishedTest {
    info: JunitTestInfo,
    status: String,
    throwable_type: String,
    throwable_message: String,
    stack_trace: String,
}

#[derive(Clone, Debug, Default)]
struct JunitSummary {
    tests_found: u64,
    tests_started: u64,
    tests_succeeded: u64,
    tests_failed: u64,
    tests_skipped: u64,
    tests_aborted: u64,
    containers_found: u64,
    containers_failed: u64,
}

#[derive(Clone, Debug)]
enum JunitProtocolEvent {
    Test(JunitTestInfo),
    Started(JunitTestInfo),
    Skipped {
        info: JunitTestInfo,
        reason: String,
    },
    Output {
        stream: String,
        info: JunitTestInfo,
        line: String,
    },
    Finished(JunitFinishedTest),
    Summary(JunitSummary),
    ListSummary {
        count: u64,
    },
    RunnerError {
        message: String,
        stack_trace: String,
    },
}

#[derive(Debug, Default)]
struct JunitExecutionReport {
    summary: Option<JunitSummary>,
    list_count: Option<u64>,
    listed_tests: Vec<JunitTestInfo>,
    failures: Vec<JunitFinishedTest>,
    runner_errors: Vec<String>,
    protocol_errors: Vec<String>,
    non_protocol_stdout: Vec<String>,
    child_stderr: Vec<String>,
}

impl JunitExecutionReport {
    fn summary_message(&self) -> String {
        if let Some(summary) = self.summary.as_ref() {
            return format!(
                "{} found, {} started, {} passed, {} failed, {} skipped, {} aborted",
                summary.tests_found,
                summary.tests_started,
                summary.tests_succeeded,
                summary.tests_failed,
                summary.tests_skipped,
                summary.tests_aborted
            );
        }
        if let Some(count) = self.list_count {
            return format!("{count} discovered");
        }
        "no JUnit summary received".to_string()
    }
}

fn process_junit_event_output(
    plan: &BuildPlan,
    test_options: &RunTestOptions,
    console_log: &Path,
    output: &CancellableOutput,
    duration_ms: u128,
) -> eyre::Result<JunitExecutionReport> {
    let mut report = JunitExecutionReport::default();
    let mut events = Vec::new();
    let mut captured_outputs: BTreeMap<String, Vec<JunitCapturedOutput>> = BTreeMap::new();
    let stdout = String::from_utf8_lossy(&output.stdout);
    for line in stdout.lines() {
        match parse_junit_protocol_line(line) {
            Some(Ok(event)) => {
                process_junit_event(
                    plan,
                    test_options,
                    &mut report,
                    &mut captured_outputs,
                    &event,
                );
                events.push(event);
            }
            Some(Err(error)) => report.protocol_errors.push(error.to_string()),
            None => report.non_protocol_stdout.push(line.to_string()),
        }
    }

    let stderr = String::from_utf8_lossy(&output.stderr);
    report
        .child_stderr
        .extend(stderr.lines().map(std::string::ToString::to_string));

    if test_options.no_capture || !output.status.success() {
        for line in &report.non_protocol_stdout {
            trace_subprocess_line("java-tool", "junit-test", "stdout", line);
        }
        for line in &report.child_stderr {
            trace_subprocess_line("java-tool", "junit-test", "stderr", line);
        }
    }

    emit_junit_terminal_summary(plan, test_options, &report);
    write_junit_event_console_log(console_log, output, duration_ms, &events, &report)?;
    Ok(report)
}

fn process_junit_event(
    plan: &BuildPlan,
    test_options: &RunTestOptions,
    report: &mut JunitExecutionReport,
    captured_outputs: &mut BTreeMap<String, Vec<JunitCapturedOutput>>,
    event: &JunitProtocolEvent,
) {
    match event {
        JunitProtocolEvent::Test(info) => {
            report.listed_tests.push(info.clone());
            trace_junit_test_list_entry(plan, info);
        }
        JunitProtocolEvent::Started(_) => {}
        JunitProtocolEvent::Skipped { info, reason } => {
            if test_options.no_capture {
                trace_junit_test_line(plan, info, "stdout", &format!("skipped: {reason}"));
            }
        }
        JunitProtocolEvent::Output { stream, info, line } => {
            captured_outputs
                .entry(info.key())
                .or_default()
                .push(JunitCapturedOutput {
                    stream: stream.clone(),
                    line: line.clone(),
                });
            if test_options.no_capture {
                trace_junit_test_line(plan, info, stream, line);
            }
        }
        JunitProtocolEvent::Finished(finished) => {
            if finished.status != "SUCCESSFUL" {
                if !test_options.no_capture {
                    for captured in captured_outputs
                        .get(&finished.info.key())
                        .into_iter()
                        .flat_map(|outputs| outputs.iter())
                    {
                        trace_junit_test_line(
                            plan,
                            &finished.info,
                            &captured.stream,
                            &captured.line,
                        );
                    }
                }
                trace_junit_test_failure(plan, finished);
                report.failures.push(finished.clone());
            }
        }
        JunitProtocolEvent::Summary(summary) => {
            report.summary = Some(summary.clone());
        }
        JunitProtocolEvent::ListSummary { count } => {
            report.list_count = Some(*count);
        }
        JunitProtocolEvent::RunnerError {
            message,
            stack_trace,
        } => {
            report.runner_errors.push(message.clone());
            let info = JunitTestInfo::default();
            trace_junit_test_line(plan, &info, "stderr", message);
            for line in stack_trace.lines() {
                trace_junit_test_line(plan, &info, "stderr", line);
            }
        }
    }
}

fn emit_junit_terminal_summary(
    plan: &BuildPlan,
    test_options: &RunTestOptions,
    report: &JunitExecutionReport,
) {
    let _span = tracing::info_span!("junit_terminal_summary", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    match test_options.action {
        RunTestAction::Compile => {
            tracing::info!(
                source,
                process,
                "Compiled test source set; no JUnit launch requested."
            );
        }
        RunTestAction::List => {
            let count = report
                .list_count
                .unwrap_or(report.listed_tests.len() as u64);
            tracing::info!(source, process, "Discovered {count} JUnit tests.");
        }
        RunTestAction::Run => {
            if let Some(summary) = report.summary.as_ref() {
                tracing::info!(
                    source,
                    process,
                    "JUnit tests: {} passed, {} failed, {} skipped, {} aborted ({} found).",
                    summary.tests_succeeded,
                    summary.tests_failed,
                    summary.tests_skipped,
                    summary.tests_aborted,
                    summary.tests_found
                );
            }
        }
    }
}

fn trace_junit_test_list_entry(plan: &BuildPlan, info: &JunitTestInfo) {
    let _span = tracing::info_span!("junit_test_list_entry", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    let test = info.label();
    let test_uri = info.uri();
    tracing::info!(
        source,
        process,
        test = %test,
        test_uri = %test_uri,
        "{test}"
    );
}

fn trace_junit_test_line(plan: &BuildPlan, info: &JunitTestInfo, stream: &str, line: &str) {
    let _span = tracing::info_span!("junit_test_output", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    let test = info.label();
    let test_uri = info.uri();
    tracing::info!(
        source,
        process,
        stream = %stream,
        test = %test,
        test_uri = %test_uri,
        "{line}"
    );
}

fn trace_junit_test_failure(plan: &BuildPlan, failure: &JunitFinishedTest) {
    let _span = tracing::info_span!("junit_test_failure", branch = %plan.branch_name).entered();
    let source = "java-tool";
    let process = "junit-test";
    let stream = "stderr";
    let test = failure.info.label();
    let test_uri = failure.info.uri();
    let message = if failure.throwable_message.is_empty() {
        failure.throwable_type.as_str()
    } else {
        failure.throwable_message.as_str()
    };
    tracing::error!(
        source,
        process,
        stream,
        test = %test,
        test_uri = %test_uri,
        "JUnit test failed ({}) {message}",
        failure.status
    );
    for line in failure.stack_trace.lines() {
        tracing::error!(
            source,
            process,
            stream,
            test = %test,
            test_uri = %test_uri,
            "{line}"
        );
    }
}

fn parse_junit_protocol_line(line: &str) -> Option<eyre::Result<JunitProtocolEvent>> {
    let rest = line.strip_prefix(JUNIT_EVENT_PREFIX)?;
    Some(parse_junit_protocol_event(rest))
}

fn parse_junit_protocol_event(rest: &str) -> eyre::Result<JunitProtocolEvent> {
    let mut parts = rest.split('\t');
    let event = parts
        .next()
        .ok_or_else(|| eyre::eyre!("JUnit protocol line did not include an event name"))?;
    let fields = parts
        .map(decode_junit_protocol_field)
        .collect::<eyre::Result<Vec<_>>>()?;
    match event {
        "test" => {
            expect_junit_field_count(event, &fields, 4)?;
            Ok(JunitProtocolEvent::Test(junit_info_from_fields(&fields, 0)))
        }
        "started" => {
            expect_junit_field_count(event, &fields, 4)?;
            Ok(JunitProtocolEvent::Started(junit_info_from_fields(
                &fields, 0,
            )))
        }
        "skipped" => {
            expect_junit_field_count(event, &fields, 5)?;
            Ok(JunitProtocolEvent::Skipped {
                info: junit_info_from_fields(&fields, 0),
                reason: fields[4].clone(),
            })
        }
        "output" => {
            expect_junit_field_count(event, &fields, 6)?;
            Ok(JunitProtocolEvent::Output {
                stream: fields[0].clone(),
                info: junit_info_from_fields(&fields, 1),
                line: fields[5].clone(),
            })
        }
        "finished" => {
            expect_junit_field_count(event, &fields, 8)?;
            Ok(JunitProtocolEvent::Finished(JunitFinishedTest {
                info: junit_info_from_fields(&fields, 0),
                status: fields[4].clone(),
                throwable_type: fields[5].clone(),
                throwable_message: fields[6].clone(),
                stack_trace: fields[7].clone(),
            }))
        }
        "summary" => {
            expect_junit_field_count(event, &fields, 8)?;
            Ok(JunitProtocolEvent::Summary(JunitSummary {
                tests_found: parse_junit_u64(event, "tests_found", &fields[0])?,
                tests_started: parse_junit_u64(event, "tests_started", &fields[1])?,
                tests_succeeded: parse_junit_u64(event, "tests_succeeded", &fields[2])?,
                tests_failed: parse_junit_u64(event, "tests_failed", &fields[3])?,
                tests_skipped: parse_junit_u64(event, "tests_skipped", &fields[4])?,
                tests_aborted: parse_junit_u64(event, "tests_aborted", &fields[5])?,
                containers_found: parse_junit_u64(event, "containers_found", &fields[6])?,
                containers_failed: parse_junit_u64(event, "containers_failed", &fields[7])?,
            }))
        }
        "list_summary" => {
            expect_junit_field_count(event, &fields, 1)?;
            Ok(JunitProtocolEvent::ListSummary {
                count: parse_junit_u64(event, "count", &fields[0])?,
            })
        }
        "runner_error" => {
            expect_junit_field_count(event, &fields, 2)?;
            Ok(JunitProtocolEvent::RunnerError {
                message: fields[0].clone(),
                stack_trace: fields[1].clone(),
            })
        }
        _ => eyre::bail!("Unknown JUnit protocol event: {event}"),
    }
}

fn decode_junit_protocol_field(field: &str) -> eyre::Result<String> {
    let bytes = BASE64_STANDARD
        .decode(field)
        .wrap_err("Failed to decode JUnit protocol field as base64")?;
    String::from_utf8(bytes).wrap_err("JUnit protocol field was not UTF-8")
}

fn expect_junit_field_count(event: &str, fields: &[String], expected: usize) -> eyre::Result<()> {
    if fields.len() != expected {
        eyre::bail!(
            "JUnit protocol event {event} expected {expected} field(s), got {}",
            fields.len()
        );
    }
    Ok(())
}

fn junit_info_from_fields(fields: &[String], offset: usize) -> JunitTestInfo {
    JunitTestInfo {
        unique_id: fields[offset].clone(),
        display_name: fields[offset + 1].clone(),
        legacy_name: fields[offset + 2].clone(),
        source_path: fields[offset + 3].clone(),
    }
}

fn parse_junit_u64(event: &str, field: &str, value: &str) -> eyre::Result<u64> {
    value
        .parse::<u64>()
        .wrap_err_with(|| format!("JUnit protocol event {event} field {field} was not a number"))
}

fn write_junit_event_console_log(
    log_path: &Path,
    output: &CancellableOutput,
    duration_ms: u128,
    events: &[JunitProtocolEvent],
    report: &JunitExecutionReport,
) -> eyre::Result<()> {
    let mut log = String::new();
    writeln!(log, "tool=junit-test")?;
    writeln!(log, "main={JUNIT_EVENT_RUNNER_MAIN_CLASS}")?;
    writeln!(log, "status={}", output.status)?;
    writeln!(log, "cancelled={}", output.cancelled)?;
    writeln!(log, "duration_ms={duration_ms}")?;
    writeln!(log, "summary={}", report.summary_message())?;
    writeln!(log)?;
    writeln!(log, "--- events ---")?;
    for event in events {
        write_junit_event_log_line(&mut log, event)?;
    }
    if !report.non_protocol_stdout.is_empty() {
        writeln!(log)?;
        writeln!(log, "--- non-protocol stdout ---")?;
        for line in &report.non_protocol_stdout {
            writeln!(log, "{line}")?;
        }
    }
    if !report.child_stderr.is_empty() {
        writeln!(log)?;
        writeln!(log, "--- child stderr ---")?;
        for line in &report.child_stderr {
            writeln!(log, "{line}")?;
        }
    }
    fs::write(log_path, log).wrap_err_with(|| format!("Failed to write {}", log_path.display()))
}

fn write_junit_event_log_line(log: &mut String, event: &JunitProtocolEvent) -> eyre::Result<()> {
    match event {
        JunitProtocolEvent::Test(info) => {
            writeln!(log, "test {}", info.label())?;
        }
        JunitProtocolEvent::Started(info) => {
            writeln!(log, "started {}", info.label())?;
        }
        JunitProtocolEvent::Skipped { info, reason } => {
            writeln!(log, "skipped {}: {reason}", info.label())?;
        }
        JunitProtocolEvent::Output { stream, info, line } => {
            writeln!(log, "{stream} {}: {line}", info.label())?;
        }
        JunitProtocolEvent::Finished(finished) => {
            writeln!(
                log,
                "finished {}: {}",
                finished.info.label(),
                finished.status
            )?;
            if !finished.throwable_type.is_empty() {
                writeln!(
                    log,
                    "  failure_type={}\n  failure_message={}",
                    finished.throwable_type, finished.throwable_message
                )?;
                writeln!(log, "{}", finished.stack_trace)?;
            }
        }
        JunitProtocolEvent::Summary(summary) => {
            writeln!(
                log,
                "summary: {} found, {} passed, {} failed, {} skipped, {} aborted",
                summary.tests_found,
                summary.tests_succeeded,
                summary.tests_failed,
                summary.tests_skipped,
                summary.tests_aborted
            )?;
            writeln!(
                log,
                "containers: {} found, {} failed",
                summary.containers_found, summary.containers_failed
            )?;
        }
        JunitProtocolEvent::ListSummary { count } => {
            writeln!(log, "list_summary: {count} discovered")?;
        }
        JunitProtocolEvent::RunnerError {
            message,
            stack_trace,
        } => {
            writeln!(log, "runner_error: {message}")?;
            writeln!(log, "{stack_trace}")?;
        }
    }
    Ok(())
}

fn vscode_file_uri_for_path(path: &str) -> String {
    if path.is_empty() {
        return String::new();
    }
    let path = percent_encode_uri_path_for_junit(&path.replace('\\', "/"));
    format!("vscode://file/{path}:1:1")
}

fn percent_encode_uri_path_for_junit(path: &str) -> String {
    let mut output = String::with_capacity(path.len());
    for byte in path.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'/' | b':' | b'-' | b'_' | b'.' | b'~' => {
                output.push(char::from(byte));
            }
            byte => {
                const HEX: &[u8; 16] = b"0123456789ABCDEF";
                output.push('%');
                output.push(char::from(HEX[usize::from(byte >> 4)]));
                output.push(char::from(HEX[usize::from(byte & 0x0F)]));
            }
        }
    }
    output
}

#[derive(Clone, Copy, Debug)]
enum TestClasspathKind {
    Compile,
    Runtime,
}

fn resolve_test_dependency_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: TestClasspathKind,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let dependencies = read_projected_dependencies(&context.plan.lockfile_path)?;
    let configurations: &[&str] = match kind {
        TestClasspathKind::Compile => &["testImplementation", "testCompileOnly"],
        TestClasspathKind::Runtime => &["testImplementation", "testRuntimeOnly"],
    };
    let roots = dependencies
        .iter()
        .filter(|dependency| {
            !dependency.loader_managed()
                && configurations.contains(&dependency.configuration.as_str())
        })
        .map(|dependency| {
            (
                dependency.configuration.as_str(),
                dependency.coordinate.clone(),
            )
        })
        .collect::<Vec<_>>();
    resolve_dependency_artifact_closure(context, resolver, "test-dependency", roots)
}

fn resolve_dependency_artifact_closure(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    artifact_prefix: &str,
    roots: Vec<(&str, MavenCoordinate)>,
) -> eyre::Result<Vec<PathBuf>> {
    let mut seen = BTreeSet::new();
    let mut queue = VecDeque::new();
    let mut paths = Vec::new();

    for (configuration, coordinate) in roots {
        context.bail_if_cancelled()?;
        let dependency = resolver.resolve_dependency(configuration, &coordinate)?;
        let resolved_coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        if seen.insert(resolved_coordinate.to_string()) {
            paths.push(dependency.cache_path);
            queue.push_back(resolved_coordinate);
        }
    }

    while let Some(parent) = queue.pop_front() {
        context.bail_if_cancelled()?;
        for coordinate in resolver.resolve_pom_runtime_dependencies(&parent)? {
            context.bail_if_cancelled()?;
            if !seen.insert(coordinate.to_string()) {
                continue;
            }
            let artifact = resolver.resolve_artifact(
                ArtifactId::from(format!("{artifact_prefix}-{}", paths.len())),
                &coordinate,
                ArtifactPurpose::from("JUnit test classpath"),
            )?;
            paths.push(artifact.cache_path);
            queue.push_back(coordinate);
        }
    }

    Ok(paths)
}

fn resolve_junit_console_standalone(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<ArtifactPlan> {
    let dependencies = read_projected_dependencies(&context.plan.lockfile_path)?;
    let platform_version = junit_platform_version(&dependencies)?;
    let coordinate = MavenCoordinate::parse(&format!(
        "org.junit.platform:junit-platform-console-standalone:{platform_version}"
    ))?;
    resolver.resolve_artifact(
        ArtifactId::from("junit-platform-console-standalone"),
        &coordinate,
        ArtifactPurpose::from("JUnit Platform ConsoleLauncher"),
    )
}

fn junit_platform_version(dependencies: &[ParsedDependency]) -> eyre::Result<String> {
    for artifact in [
        "junit-platform-console-standalone",
        "junit-platform-launcher",
        "junit-platform-engine",
        "junit-platform-commons",
    ] {
        if let Some(dependency) = dependencies.iter().find(|dependency| {
            dependency.coordinate.group == "org.junit.platform"
                && dependency.coordinate.artifact == artifact
        }) {
            return Ok(dependency.coordinate.version.clone());
        }
    }
    if let Some(dependency) = dependencies.iter().find(|dependency| {
        dependency.coordinate.group == "org.junit.jupiter"
            && dependency.coordinate.artifact.starts_with("junit-jupiter")
    }) {
        return platform_version_from_jupiter_version(&dependency.coordinate.version);
    }
    eyre::bail!("Could not infer a JUnit Platform version from test dependencies")
}

fn platform_version_from_jupiter_version(version: &str) -> eyre::Result<String> {
    let Some(rest) = version.strip_prefix("5.") else {
        eyre::bail!("Unsupported JUnit Jupiter version for platform inference: {version}");
    };
    Ok(format!("1.{rest}"))
}

fn run_mcp_mappings(plan: &BuildPlan) -> String {
    if let (Some(channel), Some(version)) = (
        plan.properties.get("mapping_channel"),
        plan.properties.get("mapping_version"),
    ) {
        return format!("{channel}_{version}");
    }
    format!("official_{}", plan.minecraft_version)
}

fn run_lockfile_cache_artifact_paths(
    launch_classpath: &RunClasspath,
    modules: &[PathBuf],
) -> Vec<PathBuf> {
    dedup_paths_preserve_order(
        launch_classpath
            .legacy
            .iter()
            .cloned()
            .chain(launch_classpath.userdev_mods.iter().cloned())
            .chain(modules.iter().cloned())
            .collect(),
    )
}

fn game_test_namespace_property(context: &ExecutionContext<'_>) -> eyre::Result<String> {
    let run_config = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("run-configurations")
        .join(context.plan.minecraft_version.as_str())
        .join("run-configurations.gradle");
    if run_config.is_file() {
        let text = fs::read_to_string(&run_config)
            .wrap_err_with(|| format!("Failed to read {}", run_config.display()))?;
        if text.contains("neoforge.enabledGameTestNamespaces") {
            return Ok("neoforge.enabledGameTestNamespaces".to_string());
        }
    }
    Ok("forge.enabledGameTestNamespaces".to_string())
}

fn apply_game_test_filter_property(
    properties: &mut BTreeMap<String, String>,
    kind: RunKind,
    run_options: &RunOptions,
) {
    if !matches!(kind, RunKind::ClientPuppet | RunKind::GameTestServer) {
        return;
    }
    let Some(selection) = run_options
        .game_test_filter
        .as_deref()
        .map(str::trim)
        .filter(|selection| !selection.is_empty())
    else {
        return;
    };
    properties.insert("sfm.gametestSelection".to_string(), selection.to_string());
}

fn apply_client_puppet_keep_open_property(
    properties: &mut BTreeMap<String, String>,
    kind: RunKind,
    run_options: &RunOptions,
) {
    if !matches!(kind, RunKind::ClientPuppet) {
        return;
    }
    properties.insert(
        "sfm.clientRun.keepOpenSeconds".to_string(),
        run_options.client_puppet_keep_open.property_seconds(),
    );
}

fn apply_client_title_screen_property(
    properties: &mut BTreeMap<String, String>,
    kind: RunKind,
    run_options: &RunOptions,
) {
    if !matches!(kind, RunKind::Client) {
        return;
    }
    let Some(title_screen) = run_options.client_title_screen else {
        return;
    };
    properties.insert(
        "sfm.clientRun.titleScreen".to_string(),
        title_screen.property_value().to_string(),
    );
}

#[expect(
    clippy::too_many_arguments,
    reason = "The attempt runner carries explicit bisect context for clear trace/log labels."
)]
fn run_game_test_bisect_attempt(
    plan: &BuildPlan,
    kind: RunKind,
    base_run_options: &RunOptions,
    selection: Option<&str>,
    target: &str,
    label: &str,
    run_count: &mut usize,
    cancellation_token: &CancellationToken,
) -> eyre::Result<GameTestBisectAttempt> {
    *run_count += 1;
    let run_number = *run_count;
    let attempt_options = RunOptions {
        game_test_filter: selection.map(str::to_string),
        game_test_bisect: base_run_options.game_test_bisect.clone(),
        ..base_run_options.clone()
    };
    tracing::info!(
        "Game-test bisect run #{run_number}: {label}; selected {}.",
        selection.unwrap_or("(all SFM game tests)")
    );

    let launch_result = execute_run(plan, kind, &attempt_options, false, cancellation_token);
    let launch_error = launch_result
        .as_ref()
        .err()
        .map(std::string::ToString::to_string);
    if let Some(error) = launch_error.as_deref() {
        tracing::debug!("Game-test bisect run #{run_number} launch returned error: {error}");
    }
    let console_log = run_state_dir(plan, kind).join("console.log");
    let log_text = fs::read_to_string(&console_log).unwrap_or_else(|error| {
        format!(
            "Failed to read game-test bisect console log {}: {error}",
            console_log.display()
        )
    });

    let selected_tests = extract_sfm_game_test_names(&log_text);
    let failed_tests = extract_failed_gametest_names(&log_text);
    let target_failed = failed_tests
        .iter()
        .any(|test| game_test_name_matches(test, target))
        || log_text.contains(&format!("Test failed: {target}"));
    let saved_log = save_game_test_bisect_attempt_log(plan, kind, run_number, label, &log_text)?;

    tracing::info!(
        "Game-test bisect run #{run_number}: target_failed={}, failed_tests={}, log={}",
        target_failed,
        format_game_test_list(&failed_tests),
        saved_log.display()
    );

    Ok(GameTestBisectAttempt {
        run_number,
        selected_tests,
        failed_tests,
        target_failed,
        saved_log,
    })
}

fn save_game_test_bisect_attempt_log(
    plan: &BuildPlan,
    kind: RunKind,
    run_number: usize,
    label: &str,
    log_text: &str,
) -> eyre::Result<PathBuf> {
    let dir = run_state_dir(plan, kind).join("bisect");
    fs::create_dir_all(&dir)?;
    let path = dir.join(format!(
        "{run_number:03}-{}.log",
        sanitize_filename_component(label)
    ));
    fs::write(&path, log_text).wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    Ok(path)
}

fn write_game_test_bisect_result(
    plan: &BuildPlan,
    kind: RunKind,
    target: &str,
    inducing_tests: &[String],
    run_count: usize,
    stopped_by_budget: bool,
) -> eyre::Result<()> {
    let dir = run_state_dir(plan, kind).join("bisect");
    fs::create_dir_all(&dir)?;
    let path = dir.join("result.txt");
    let selection = exact_game_test_selection(target, inducing_tests);
    let mut result = String::new();
    writeln!(result, "target={}", qualify_sfm_game_test_name(target))?;
    writeln!(result, "runs={run_count}")?;
    writeln!(result, "stopped_by_budget={stopped_by_budget}")?;
    writeln!(result, "companion_count={}", inducing_tests.len())?;
    writeln!(result, "filter={selection}")?;
    writeln!(result)?;
    for test in inducing_tests {
        writeln!(result, "{}", qualify_sfm_game_test_name(test))?;
    }
    fs::write(&path, result).wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    tracing::info!("Game-test bisect result: {}", path.display());
    Ok(())
}

fn game_test_bisect_budget_exhausted(run_count: usize, max_runs: Option<usize>) -> bool {
    max_runs.is_some_and(|max_runs| run_count >= max_runs)
}

fn initial_game_test_bisect_selection(target: &str, filter: Option<&str>) -> Option<String> {
    filter
        .map(str::trim)
        .filter(|filter| !filter.is_empty())
        .map(|filter| format!("{},{}", qualify_sfm_game_test_name(target), filter))
}

fn exact_game_test_selection(target: &str, companion_tests: &[String]) -> String {
    std::iter::once(qualify_sfm_game_test_name(target))
        .chain(
            companion_tests
                .iter()
                .map(|test| qualify_sfm_game_test_name(test)),
        )
        .collect::<Vec<_>>()
        .join(",")
}

fn qualify_sfm_game_test_name(name: &str) -> String {
    let normalized = normalize_sfm_game_test_name(name);
    format!("sfm:{normalized}")
}

fn normalize_sfm_game_test_name(name: &str) -> String {
    let mut normalized = name.trim();
    if let Some(stripped) = normalized.strip_prefix("- ") {
        normalized = stripped.trim();
    }
    if let Some(stripped) = normalized.strip_prefix("sfm:") {
        normalized = stripped.trim();
    }
    normalized.to_string()
}

fn game_test_name_matches(candidate: &str, target: &str) -> bool {
    let candidate = normalize_sfm_game_test_name(candidate);
    candidate == target
        || candidate.ends_with(&format!(".{target}"))
        || candidate.ends_with(&format!(":{target}"))
}

fn partition_game_test_candidates(candidates: &[String], granularity: usize) -> Vec<Vec<String>> {
    if candidates.is_empty() {
        return Vec::new();
    }
    let partitions = granularity.clamp(1, candidates.len());
    let base_size = candidates.len() / partitions;
    let oversized_count = candidates.len() % partitions;
    let mut start = 0usize;
    let mut output = Vec::with_capacity(partitions);
    for index in 0..partitions {
        let size = base_size + usize::from(index < oversized_count);
        let end = start + size;
        output.push(candidates[start..end].to_vec());
        start = end;
    }
    output
}

fn game_test_candidate_complement(candidates: &[String], subset: &[String]) -> Vec<String> {
    let subset = subset.iter().map(String::as_str).collect::<BTreeSet<_>>();
    candidates
        .iter()
        .filter(|candidate| !subset.contains(candidate.as_str()))
        .cloned()
        .collect()
}

fn extract_sfm_game_test_names(output: &str) -> Vec<String> {
    let mut names = Vec::new();
    let mut selected_names = Vec::new();
    for line in output.lines() {
        let content = strip_minecraft_log_prefix(line);
        for marker in ["Discovered SFM game test: ", "Generated SFM game test: "] {
            if let Some(name) = content.split_once(marker).map(|(_, name)| name.trim()) {
                push_unique_string(&mut names, normalize_sfm_game_test_name(name));
            }
        }
        if let Some(name) = content
            .split_once("Selected SFM game test: ")
            .map(|(_, name)| name.trim())
        {
            push_unique_string(&mut selected_names, normalize_sfm_game_test_name(name));
        }
    }
    if selected_names.is_empty() {
        names
    } else {
        selected_names
    }
}

fn extract_failed_gametest_names(output: &str) -> Vec<String> {
    let mut names = Vec::new();
    let mut in_failed_section = false;

    for line in output.lines() {
        let content = strip_minecraft_log_prefix(line);
        if content.contains("required tests failed :(") {
            in_failed_section = true;
            continue;
        }

        if in_failed_section {
            if content.contains("====") {
                break;
            }
            let stripped = content.trim();
            if let Some(name) = stripped.strip_prefix("- ") {
                push_unique_string(&mut names, normalize_sfm_game_test_name(name));
            }
        }
    }

    names
}

fn strip_minecraft_log_prefix(line: &str) -> &str {
    line.rfind("]: ")
        .map_or(line, |index| &line[index + "]: ".len()..])
}

fn dedup_strings_preserve_order(input: Vec<String>) -> Vec<String> {
    let mut output = Vec::new();
    for item in input {
        push_unique_string(&mut output, item);
    }
    output
}

fn push_unique_string(items: &mut Vec<String>, item: String) {
    if !item.is_empty() && !items.iter().any(|existing| existing == &item) {
        items.push(item);
    }
}

fn format_game_test_list(tests: &[String]) -> String {
    if tests.is_empty() {
        "(none)".to_string()
    } else {
        tests
            .iter()
            .map(|test| qualify_sfm_game_test_name(test))
            .collect::<Vec<_>>()
            .join(", ")
    }
}

fn sanitize_filename_component(input: &str) -> String {
    let sanitized = input
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() || matches!(character, '-' | '_' | '.') {
                character
            } else {
                '_'
            }
        })
        .collect::<String>();
    if sanitized.is_empty() {
        "attempt".to_string()
    } else {
        sanitized
    }
}

fn clean_gametest_server_world(minecraft_dir: &Path, working_dir: &Path) -> eyre::Result<()> {
    if working_dir.file_name().and_then(|name| name.to_str()) != Some("runGameTest")
        || !working_dir.starts_with(minecraft_dir)
    {
        eyre::bail!(
            "Refusing to clean unexpected game-test working directory: {}",
            working_dir.display()
        );
    }

    let world_dir = working_dir.join("gametestserver");
    if world_dir.exists() {
        fs::remove_dir_all(&world_dir)
            .wrap_err_with(|| format!("Failed to remove {}", world_dir.display()))?;
    }
    Ok(())
}

fn clean_client_puppet_world(minecraft_dir: &Path, working_dir: &Path) -> eyre::Result<()> {
    if working_dir.file_name().and_then(|name| name.to_str()) != Some("runClientPuppet")
        || !working_dir.starts_with(minecraft_dir)
    {
        eyre::bail!(
            "Refusing to clean unexpected client puppet working directory: {}",
            working_dir.display()
        );
    }

    let world_dir = working_dir.join("saves").join("sfm_client_puppet");
    if world_dir.exists() {
        fs::remove_dir_all(&world_dir)
            .wrap_err_with(|| format!("Failed to remove {}", world_dir.display()))?;
    }
    Ok(())
}

fn prepare_client_automation_options(
    minecraft_dir: &Path,
    working_dir: &Path,
    kind: RunKind,
) -> eyre::Result<Option<PathBuf>> {
    if !matches!(kind, RunKind::ClientSmoke | RunKind::ClientPuppet) {
        return Ok(None);
    }
    if !working_dir.starts_with(minecraft_dir) {
        eyre::bail!(
            "Refusing to prepare client automation options outside minecraft dir: {}",
            working_dir.display()
        );
    }

    let options_path = working_dir.join("options.txt");
    let existing = match fs::read_to_string(&options_path) {
        Ok(content) => content,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => String::new(),
        Err(err) => {
            return Err(err).wrap_err_with(|| format!("Failed to read {}", options_path.display()));
        }
    };
    let mut updated = set_minecraft_option(&existing, "onboardAccessibility", "false");
    updated = set_minecraft_option(&updated, "narrator", "0");
    updated = set_minecraft_option(&updated, "pauseOnLostFocus", "false");
    updated = set_minecraft_option(&updated, "tutorialStep", "none");
    fs::write(&options_path, updated)
        .wrap_err_with(|| format!("Failed to write {}", options_path.display()))?;
    Ok(Some(options_path))
}

fn set_minecraft_option(content: &str, key: &str, value: &str) -> String {
    let prefix = format!("{key}:");
    let mut found = false;
    let mut lines = content
        .lines()
        .map(|line| {
            if line.starts_with(&prefix) {
                found = true;
                format!("{prefix}{value}")
            } else {
                line.to_string()
            }
        })
        .collect::<Vec<_>>();
    if !found {
        lines.push(format!("{prefix}{value}"));
    }
    let mut output = lines.join("\n");
    output.push('\n');
    output
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        java = %plan.java.executable.display(),
        argfile = %argfile.display(),
        working_dir = %working_dir.display(),
        env_vars = env.len(),
        timeout_seconds = timeout.map_or(0, |timeout| timeout.as_secs()),
    )
)]
fn run_launch_command(
    cancellation_token: &CancellationToken,
    plan: &BuildPlan,
    argfile: &Path,
    working_dir: &Path,
    env: &BTreeMap<String, String>,
    log_path: &Path,
    timeout: Option<Duration>,
) -> eyre::Result<LaunchOutput> {
    cancellation_token.bail_if_cancelled()?;
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let output_mode = launch_output_mode();
    let launch_log = prepare_launch_log_file(output_mode, argfile, working_dir, log_path)?;
    let mut child = Command::new(&plan.java.executable)
        .arg(format!("@{}", argfile.display()))
        .current_dir(working_dir)
        .envs(env)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .wrap_err_with(|| format!("Failed to spawn {}", plan.java.executable.display()))?;
    tracing::info!("minecraft_jvm_spawned");

    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture launch stdout"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture launch stderr"))?;
    let stdout_branch = plan.branch_name.clone();
    let stderr_branch = plan.branch_name.clone();
    let stdout_launch_log = launch_log.clone();
    let stderr_launch_log = launch_log.clone();
    let stdout_thread = thread::spawn(move || {
        read_launch_stream(
            stdout,
            &stdout_branch,
            "minecraft",
            "minecraft",
            "stdout",
            output_mode,
            stdout_launch_log,
        )
    });
    let stderr_thread = thread::spawn(move || {
        read_launch_stream(
            stderr,
            &stderr_branch,
            "minecraft",
            "minecraft",
            "stderr",
            output_mode,
            stderr_launch_log,
        )
    });
    let started = Instant::now();
    let mut timed_out = false;
    let mut cancelled = false;
    let status = loop {
        if let Some(status) = child.try_wait().wrap_err("Failed to poll launched JVM")? {
            break status;
        }
        if cancellation_token.is_cancelled() {
            cancelled = true;
            tracing::warn!("Cancellation requested; killing launched Minecraft JVM");
            kill_child_for_cancellation(&mut child, "launched Minecraft JVM")?;
            break child
                .wait()
                .wrap_err("Failed to wait for cancelled launched JVM")?;
        }
        if let Some(timeout) = timeout
            && started.elapsed() >= timeout
        {
            timed_out = true;
            child
                .kill()
                .wrap_err("Failed to kill timed-out launched JVM")?;
            break child
                .wait()
                .wrap_err("Failed to wait for timed-out launched JVM")?;
        }
        thread::sleep(Duration::from_millis(250));
    };
    let stdout_text = join_launch_stream(stdout_thread, "stdout")?;
    let stderr_text = join_launch_stream(stderr_thread, "stderr")?;
    let combined = format!("{stdout_text}{stderr_text}");
    finish_launch_log(
        output_mode,
        launch_log,
        log_path,
        argfile,
        working_dir,
        status,
        timed_out,
        cancelled,
        &combined,
    )?;
    Ok(LaunchOutput {
        status,
        combined,
        timed_out,
        cancelled,
    })
}

fn launch_output_mode() -> LaunchOutputMode {
    if std::io::stdout().is_terminal() {
        LaunchOutputMode::Terminal
    } else {
        LaunchOutputMode::LogFile
    }
}

fn prepare_launch_log_file(
    output_mode: LaunchOutputMode,
    argfile: &Path,
    working_dir: &Path,
    log_path: &Path,
) -> eyre::Result<Option<Arc<Mutex<File>>>> {
    if output_mode == LaunchOutputMode::Terminal {
        return Ok(None);
    }

    let mut log =
        File::create(log_path).wrap_err_with(|| format!("Failed to create {}", log_path.display()))?;
    writeln!(log, "argfile={}", argfile.display())?;
    writeln!(log, "working_dir={}", working_dir.display())?;
    writeln!(log)?;
    tracing::info!("Minecraft JVM output will be written to {}", log_path.display());
    Ok(Some(Arc::new(Mutex::new(log))))
}

#[expect(
    clippy::too_many_arguments,
    reason = "The launch footer records the child process outcome and its launch context."
)]
fn finish_launch_log(
    output_mode: LaunchOutputMode,
    launch_log: Option<Arc<Mutex<File>>>,
    log_path: &Path,
    argfile: &Path,
    working_dir: &Path,
    status: ExitStatus,
    timed_out: bool,
    cancelled: bool,
    combined: &str,
) -> eyre::Result<()> {
    match output_mode {
        LaunchOutputMode::Terminal => {
            let mut log = String::new();
            writeln!(log, "status={status}")?;
            writeln!(log, "timed_out={timed_out}")?;
            writeln!(log, "cancelled={cancelled}")?;
            writeln!(log, "argfile={}", argfile.display())?;
            writeln!(log, "working_dir={}", working_dir.display())?;
            writeln!(log)?;
            log.push_str(combined);
            fs::write(log_path, log)
                .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
        }
        LaunchOutputMode::LogFile => {
            let launch_log =
                launch_log.ok_or_else(|| eyre::eyre!("Missing launch log file handle"))?;
            let mut log = launch_log
                .lock()
                .map_err(|_poisoned| eyre::eyre!("Launch log file lock was poisoned"))?;
            writeln!(log)?;
            writeln!(log, "status={status}")?;
            writeln!(log, "timed_out={timed_out}")?;
            writeln!(log, "cancelled={cancelled}")?;
            log.flush()?;
            tracing::info!("Minecraft JVM output written to {}", log_path.display());
        }
    }
    Ok(())
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(branch = %branch)
)]
fn read_launch_stream<R>(
    stream: R,
    branch: &str,
    source: &'static str,
    process: &str,
    stream_name: &'static str,
    output_mode: LaunchOutputMode,
    launch_log: Option<Arc<Mutex<File>>>,
) -> std::io::Result<String>
where
    R: Read,
{
    let mut captured = String::new();
    match launch_log {
        Some(launch_log) => {
            for line in BufReader::new(stream).lines() {
                let line = line?;
                if output_mode == LaunchOutputMode::Terminal {
                    trace_subprocess_line(source, process, stream_name, &line);
                }
                write_launch_log_line(&launch_log, &line)?;
                captured.push_str(&line);
                captured.push('\n');
            }
        }
        None => {
            for line in BufReader::new(stream).lines() {
                let line = line?;
                if output_mode == LaunchOutputMode::Terminal {
                    trace_subprocess_line(source, process, stream_name, &line);
                }
                captured.push_str(&line);
                captured.push('\n');
            }
        }
    }
    Ok(captured)
}

fn write_launch_log_line(launch_log: &Arc<Mutex<File>>, line: &str) -> std::io::Result<()> {
    let mut log = launch_log
        .lock()
        .map_err(|_poisoned| std::io::Error::other("launch log file lock was poisoned"))?;
    writeln!(log, "{line}")?;
    Ok(())
}

fn join_launch_stream(
    handle: thread::JoinHandle<std::io::Result<String>>,
    name: &str,
) -> eyre::Result<String> {
    handle
        .join()
        .map_err(|_panic| eyre::eyre!("Launch {name} reader thread panicked"))?
        .wrap_err_with(|| format!("Failed to read launch {name}"))
}

fn run_command_capture_output(
    cancellation_token: &CancellationToken,
    command: &mut Command,
    process_name: &str,
) -> eyre::Result<CancellableOutput> {
    cancellation_token.bail_if_cancelled()?;
    command.stdout(Stdio::piped()).stderr(Stdio::piped());
    let mut child = command
        .spawn()
        .wrap_err_with(|| format!("Failed to spawn {process_name}"))?;
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture {process_name} stdout"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| eyre::eyre!("Failed to capture {process_name} stderr"))?;
    let stdout_thread = thread::spawn(move || read_stream_bytes(stdout));
    let stderr_thread = thread::spawn(move || read_stream_bytes(stderr));
    let mut cancelled = false;
    let status = loop {
        if let Some(status) = child
            .try_wait()
            .wrap_err_with(|| format!("Failed to poll {process_name}"))?
        {
            break status;
        }
        if cancellation_token.is_cancelled() {
            cancelled = true;
            tracing::warn!(
                process = process_name,
                "Cancellation requested; killing JVM child"
            );
            kill_child_for_cancellation(&mut child, process_name)?;
            break child
                .wait()
                .wrap_err_with(|| format!("Failed to wait for cancelled {process_name}"))?;
        }
        thread::sleep(Duration::from_millis(250));
    };
    Ok(CancellableOutput {
        status,
        stdout: join_output_stream(stdout_thread, process_name, "stdout")?,
        stderr: join_output_stream(stderr_thread, process_name, "stderr")?,
        cancelled,
    })
}

fn kill_child_for_cancellation(
    child: &mut std::process::Child,
    process_name: &str,
) -> eyre::Result<()> {
    match child.kill() {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::InvalidInput => Ok(()),
        Err(error) => {
            Err(error).wrap_err_with(|| format!("Failed to kill cancelled {process_name}"))
        }
    }
}

fn read_stream_bytes<R>(mut stream: R) -> std::io::Result<Vec<u8>>
where
    R: Read,
{
    let mut bytes = Vec::new();
    stream.read_to_end(&mut bytes)?;
    Ok(bytes)
}

fn join_output_stream(
    handle: thread::JoinHandle<std::io::Result<Vec<u8>>>,
    process_name: &str,
    stream_name: &str,
) -> eyre::Result<Vec<u8>> {
    handle
        .join()
        .map_err(|_panic| eyre::eyre!("{process_name} {stream_name} reader thread panicked"))?
        .wrap_err_with(|| format!("Failed to read {process_name} {stream_name}"))
}

fn trace_subprocess_bytes(
    plan: &BuildPlan,
    source: &'static str,
    process: &str,
    stream: &'static str,
    bytes: &[u8],
) {
    let _span =
        tracing::info_span!("forward_subprocess_bytes", branch = %plan.branch_name).entered();
    let content = String::from_utf8_lossy(bytes);
    for line in content.lines() {
        trace_subprocess_line(source, process, stream, line);
    }
}

fn write_java_tool_console_log(
    log_path: &Path,
    tool_id: &str,
    main_class: &str,
    duration_ms: u128,
    classpath_arg: &str,
    args: &[String],
    output: &CancellableOutput,
) -> eyre::Result<()> {
    let mut log = Vec::new();
    writeln!(
        log,
        "tool={tool_id}\nmain={main_class}\nstatus={}\ncancelled={}\nduration_ms={}\nclasspath={}\nargs={:?}\n",
        output.status, output.cancelled, duration_ms, classpath_arg, args
    )?;
    log.extend_from_slice(b"\n--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(log_path, log).wrap_err_with(|| format!("Failed to write {}", log_path.display()))
}

fn trace_subprocess_line(source: &'static str, process: &str, stream: &'static str, line: &str) {
    tracing::info!(
        source,
        process = %process,
        stream,
        "{line}"
    );
}

fn extract_required_gametest_pass_count(output: &str) -> Option<usize> {
    extract_number_between(output, "All ", " required tests passed :)")
}

fn extract_running_gametest_count(output: &str) -> Option<usize> {
    extract_number_between(output, "Running all ", " tests")
}

fn extract_client_puppet_pass_count(output: &str) -> Option<usize> {
    extract_number_between(
        output,
        "SFM_CLIENT_PUPPET_TESTS_PASSED required=",
        " total=",
    )
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct ClientPuppetFailure {
    required_failed: usize,
    optional_failed: usize,
    required_count: usize,
    total_count: usize,
}

fn extract_client_puppet_failure(output: &str) -> Option<ClientPuppetFailure> {
    let prefix = "SFM_CLIENT_PUPPET_TESTS_FAILED required_failed=";
    for (start, _) in output.match_indices(prefix) {
        let after_prefix = &output[start + prefix.len()..];
        let Some((required_failed, after_required_failed)) =
            take_usize_before(after_prefix, " optional_failed=")
        else {
            continue;
        };
        let Some((optional_failed, after_optional_failed)) =
            take_usize_before(after_required_failed, " required=")
        else {
            continue;
        };
        let Some((required_count, after_required_count)) =
            take_usize_before(after_optional_failed, " total=")
        else {
            continue;
        };
        let total_count = take_leading_usize(after_required_count)?;
        return Some(ClientPuppetFailure {
            required_failed,
            optional_failed,
            required_count,
            total_count,
        });
    }
    None
}

fn extract_number_between(output: &str, prefix: &str, suffix: &str) -> Option<usize> {
    for (start, _) in output.match_indices(prefix) {
        let after_prefix = &output[start + prefix.len()..];
        let Some(end) = after_prefix.find(suffix) else {
            continue;
        };
        let number = after_prefix[..end].trim();
        if !number.is_empty() && number.chars().all(|character| character.is_ascii_digit()) {
            return number.parse().ok();
        }
    }
    None
}

fn take_usize_before<'a>(text: &'a str, delimiter: &str) -> Option<(usize, &'a str)> {
    let end = text.find(delimiter)?;
    let number = text[..end].trim();
    if number.is_empty() || !number.chars().all(|character| character.is_ascii_digit()) {
        return None;
    }
    Some((number.parse().ok()?, &text[end + delimiter.len()..]))
}

fn take_leading_usize(text: &str) -> Option<usize> {
    let end = text
        .char_indices()
        .find_map(|(index, character)| (!character.is_ascii_digit()).then_some(index))
        .unwrap_or(text.len());
    if end == 0 {
        return None;
    }
    text[..end].parse().ok()
}

fn read_forge_run_config(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<ForgeRunConfig> {
    let config: ForgeUserdevConfig = read_zip_json_entry(
        &context
            .artifact(ArtifactId::from("forge-userdev"))?
            .cache_path,
        "config.json",
    )?;
    kind.userdev_names()
        .iter()
        .find_map(|name| config.runs.get(*name))
        .cloned()
        .ok_or_else(|| {
            eyre::eyre!(
                "Forge userdev config does not define any run config for {} (tried: {})",
                kind.command_name(),
                kind.userdev_names().join(", ")
            )
        })
}

fn run_config_requires_assets(config: &ForgeRunConfig) -> bool {
    config
        .args
        .iter()
        .chain(config.jvm_args.iter())
        .chain(config.env.values())
        .chain(config.props.values())
        .any(|value| value.contains("{asset_index}") || value.contains("{assets_root}"))
}

fn replace_placeholders(input: &str, replacements: &[(&str, &str)]) -> String {
    replacements
        .iter()
        .fold(input.to_string(), |output, (needle, replacement)| {
            output.replace(needle, replacement)
        })
}

fn resolve_forge_userdev_modules(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    let _span = tracing::debug_span!("resolve_forge_userdev_modules").entered();
    let config: ForgeUserdevConfig = {
        let _span = tracing::debug_span!("resolve_forge_userdev_modules_read_config").entered();
        read_zip_json_entry(
            &context
                .artifact(ArtifactId::from("forge-userdev"))?
                .cache_path,
            "config.json",
        )?
    };
    let mut artifacts = Vec::new();
    for (index, coordinate) in config.modules.into_iter().enumerate() {
        context.bail_if_cancelled()?;
        artifacts.push((
            ArtifactId::from(format!("forge-userdev-module-{index}")),
            MavenCoordinate::parse(&coordinate)?,
            ArtifactPurpose::from("Forge userdev module path"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_forge_userdev_modules_resolve_artifacts",
        modules = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        mc = %context.plan.minecraft_version,
        kind = kind.command_name(),
    )
)]
fn resolve_run_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
    run_options: &RunOptions,
) -> eyre::Result<RunClasspath> {
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let _span = tracing::debug_span!("resolve_run_classpath_neogradle").entered();
        return resolve_neogradle_run_classpath(context, resolver, kind, run_options);
    }

    let mut legacy = Vec::new();
    {
        let _span = tracing::debug_span!("resolve_run_classpath_ensure_forge_dev_jar").entered();
        legacy.push(ensure_run_forge_dev_jar(context)?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_ensure_client_extra_jar").entered();
        legacy.push(ensure_client_extra_jar(context)?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_ensure_mcp_csv_mappings").entered();
        legacy.push(ensure_runtime_mcp_csv_mappings(context)?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_minecraft_libraries").entered();
        legacy.extend(resolve_current_minecraft_libraries(
            context,
            &resolver.client,
        )?);
    };
    {
        let _span = tracing::debug_span!("resolve_run_classpath_forge_userdev_libraries").entered();
        legacy.extend(resolve_forge_userdev_libraries(context, resolver)?);
    };
    if should_include_project_run_dependencies(kind, run_options) {
        let _span = tracing::debug_span!("resolve_run_classpath_plain_dependencies").entered();
        legacy.extend(resolve_run_plain_dependencies(context, resolver, kind)?);
    } else {
        tracing::info!(
            kind = kind.command_name(),
            "solo client launch: skipping plain dependency jars"
        );
    }

    let userdev_mods = if should_include_project_run_dependencies(kind, run_options) {
        let _span = tracing::debug_span!("resolve_run_classpath_deobf_dependencies").entered();
        resolve_run_deobf_dependencies(context, resolver, kind)?
    } else {
        tracing::info!(
            kind = kind.command_name(),
            "solo client launch: skipping deobfuscated dependency mod jars"
        );
        Vec::new()
    };
    let legacy = {
        let _span =
            tracing::debug_span!("resolve_run_classpath_dedup_legacy", entries = legacy.len())
                .entered();
        dedup_paths_preserve_order(legacy)
    };
    let userdev_mods = {
        let _span = tracing::debug_span!(
            "resolve_run_classpath_dedup_userdev_mods",
            entries = userdev_mods.len()
        )
        .entered();
        dedup_paths_preserve_order(userdev_mods)
    };
    tracing::info!(
        legacy_entries = legacy.len(),
        userdev_mods = userdev_mods.len(),
        "run_classpath resolved"
    );
    Ok(RunClasspath {
        legacy,
        userdev_mods,
    })
}

fn resolve_neogradle_run_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
    run_options: &RunOptions,
) -> eyre::Result<RunClasspath> {
    let mut legacy = Vec::new();
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_minecraft_libraries").entered();
        legacy.extend(resolve_current_minecraft_libraries(
            context,
            &resolver.client,
        )?);
    };
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_userdev_libraries").entered();
        legacy.extend(resolve_forge_userdev_libraries(context, resolver)?);
    };
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_client_extra_jar").entered();
        legacy.push(ensure_client_extra_jar(context)?);
    };
    {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_dev_jars", kind = %kind.command_name())
                .entered();
        legacy.extend(ensure_run_neoforge_dev_jars(context, kind)?);
    };

    let mut userdev_mods = Vec::new();
    if matches!(kind, RunKind::GameTestServer) {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_test_libraries").entered();
        userdev_mods.extend(resolve_forge_userdev_test_libraries(context, resolver)?);
    }
    if should_include_project_run_dependencies(kind, run_options) {
        let _span =
            tracing::debug_span!("resolve_neogradle_run_classpath_run_dependencies").entered();
        userdev_mods.extend(resolve_neogradle_run_dependencies(context, kind)?);
    } else {
        tracing::info!(
            kind = kind.command_name(),
            "solo client launch: skipping NeoGradle dependency mod jars"
        );
    }

    let legacy = {
        let _span = tracing::debug_span!(
            "resolve_neogradle_run_classpath_dedup_legacy",
            entries = legacy.len()
        )
        .entered();
        dedup_paths_preserve_order(legacy)
    };
    let userdev_mods = {
        let _span = tracing::debug_span!(
            "resolve_neogradle_run_classpath_dedup_userdev_mods",
            entries = userdev_mods.len()
        )
        .entered();
        dedup_paths_preserve_order(userdev_mods)
    };
    tracing::info!(
        legacy_entries = legacy.len(),
        userdev_mods = userdev_mods.len(),
        "neogradle_run_classpath resolved"
    );
    Ok(RunClasspath {
        legacy,
        userdev_mods,
    })
}

pub(super) fn should_include_project_run_dependencies(
    kind: RunKind,
    run_options: &RunOptions,
) -> bool {
    !is_solo_client_like_launch(kind, run_options)
}

fn is_solo_client_like_launch(
    kind: RunKind,
    run_options: &RunOptions,
) -> bool {
    run_options.client_solo && matches!(kind, RunKind::Client | RunKind::ClientSmoke)
}

#[expect(
    clippy::too_many_lines,
    reason = "NeoGradle dev jar setup mirrors the userdev config steps in order."
)]
fn ensure_run_neoforge_dev_jars(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span =
        tracing::debug_span!("ensure_run_neoforge_dev_jars", kind = %kind.command_name()).entered();
    let input = loader_dev_compile_jar(context);
    if !input.is_file() {
        eyre::bail!(
            "{} requires the Rust-owned NeoForm dev jar first: {}",
            kind.command_name(),
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;
    let neoforge_universal = context.artifact(ArtifactId::from("neoforge-universal"))?;
    context.assert_allowed_input(&neoforge_universal.cache_path)?;
    let neoforge_version = required_property(&context.plan.properties, "neo_version")?;
    let requires_split_runtime = {
        let _span = tracing::debug_span!(
            "ensure_run_neoforge_check_split_runtime",
            universal = %neoforge_universal.cache_path.display()
        )
        .entered();
        neoforge_requires_split_runtime(&neoforge_universal.cache_path)?
    };
    if requires_split_runtime {
        let minecraft_output = context
            .plan
            .cache_dir
            .join("run")
            .join(format!("minecraft-{neoforge_version}.jar"));
        let minecraft_input_state = {
            let _span = tracing::debug_span!(
                "ensure_run_neoforge_hash_split_runtime_inputs",
                input = %input.display(),
                universal = %neoforge_universal.cache_path.display()
            )
            .entered();
            format!(
                "{}\n{}\nsplit-minecraft-v3\n",
                ContentHash::from_path(&input, ContentHashAlgorithm::Blake3)?,
                ContentHash::from_path(
                    &neoforge_universal.cache_path,
                    ContentHashAlgorithm::Blake3
                )?
            )
        };
        let minecraft_input_state_path = minecraft_output.with_extension("inputs.sha1");
        let current_minecraft_input_state =
            fs::read_to_string(&minecraft_input_state_path).unwrap_or_default();
        if !minecraft_output.is_file()
            || context.plan.refresh
            || current_minecraft_input_state != minecraft_input_state
        {
            let _span = tracing::debug_span!(
                "ensure_run_neoforge_write_split_minecraft_jar",
                output = %minecraft_output.display()
            )
            .entered();
            write_run_neoforge_minecraft_dev_jar(
                &input,
                &neoforge_universal.cache_path,
                &minecraft_output,
            )?;
            fs::write(&minecraft_input_state_path, minecraft_input_state).wrap_err_with(|| {
                format!(
                    "Failed to write NeoForge Minecraft run jar input state {}",
                    minecraft_input_state_path.display()
                )
            })?;
        }
        return Ok(vec![
            minecraft_output,
            neoforge_universal.cache_path.clone(),
        ]);
    }

    let output = context
        .plan
        .cache_dir
        .join("run")
        .join(format!("neoforge-{neoforge_version}.jar"));
    let input_state = {
        let _span = tracing::debug_span!(
            "ensure_run_neoforge_hash_runtime_inputs",
            input = %input.display(),
            universal = %neoforge_universal.cache_path.display()
        )
        .entered();
        format!(
            "{}\n{}\n",
            ContentHash::from_path(&input, ContentHashAlgorithm::Blake3)?,
            ContentHash::from_path(&neoforge_universal.cache_path, ContentHashAlgorithm::Blake3)?
        )
    };
    let input_state_path = output.with_extension("inputs.sha1");
    let current_input_state = fs::read_to_string(&input_state_path).unwrap_or_default();
    if !output.is_file() || context.plan.refresh || current_input_state != input_state {
        let _span = tracing::debug_span!(
            "ensure_run_neoforge_write_runtime_jar",
            output = %output.display()
        )
        .entered();
        write_run_neoforge_dev_jar(&input, &neoforge_universal.cache_path, &output)?;
        fs::write(&input_state_path, input_state).wrap_err_with(|| {
            format!(
                "Failed to write NeoForge run jar input state {}",
                input_state_path.display()
            )
        })?;
    }
    Ok(vec![output])
}

fn ensure_run_forge_dev_jar(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let _span = tracing::debug_span!("ensure_run_forge_dev_jar").entered();
    let forge_version = required_property(&context.plan.properties, "neo_version")?;
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("classes")
        .join("dev-compile.jar");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires the Rust-owned mapped dev compile jar first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;
    let forge_universal = context.artifact(ArtifactId::from("forge-universal"))?;
    context.assert_allowed_input(&forge_universal.cache_path)?;

    let output = context.plan.cache_dir.join("run").join(format!(
        "forge-{}-{}-dev-compile.jar",
        context.plan.minecraft_version, forge_version
    ));
    let _span = tracing::debug_span!(
        "ensure_run_forge_write_dev_jar",
        input = %input.display(),
        output = %output.display()
    )
    .entered();
    write_run_forge_dev_jar(&input, &forge_universal.cache_path, &output)?;
    Ok(output)
}

fn ensure_client_extra_jar(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let _span = tracing::debug_span!("ensure_client_extra_jar").entered();
    let client_jar = context.plan.minecraft_version_cache_dir.join("client.jar");
    if !client_jar.is_file() {
        let _span = tracing::debug_span!(
            "ensure_client_extra_download_client_jar",
            output = %client_jar.display()
        )
        .entered();
        let client = Client::builder()
            .user_agent("sfm-propagate-changes/no-gradle-toolchain")
            .build()
            .wrap_err("Failed to create HTTP client")?;
        download_to_path(
            &context.cancellation_token,
            &client,
            &context.plan.minecraft.client_jar_url,
            &client_jar,
        )?;
    }
    context.assert_allowed_input(&client_jar)?;

    let output = context.plan.cache_dir.join("run").join("client-extra.jar");
    if output.is_file() && !context.plan.refresh {
        return Ok(output);
    }

    let _span = tracing::debug_span!(
        "ensure_client_extra_write_jar",
        input = %client_jar.display(),
        output = %output.display()
    )
    .entered();
    write_client_extra_jar(&client_jar, &output)?;
    Ok(output)
}

fn ensure_runtime_mcp_csv_mappings(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let _span = tracing::debug_span!("ensure_runtime_mcp_csv_mappings").entered();
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires SRG-to-named mappings first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;

    let output = context.plan.cache_dir.join("run").join("mcp-mappings");
    let _span = tracing::debug_span!(
        "ensure_runtime_mcp_write_csv_mappings",
        input = %input.display(),
        output = %output.display()
    )
    .entered();
    write_runtime_mcp_csv_mappings(&input, &output)?;
    Ok(output)
}

fn ensure_run_refmap_remapping_file(context: &ExecutionContext<'_>) -> eyre::Result<PathBuf> {
    let input = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !input.is_file() {
        eyre::bail!(
            "Forge userdev launch requires SRG-to-named mappings first: {}",
            input.display()
        );
    }
    context.assert_allowed_input(&input)?;

    let output = context
        .plan
        .cache_dir
        .join("project")
        .join("run-refmap-remap.srg");
    write_srg_to_named_mapping_file(&input, &output)?;
    Ok(output)
}

fn resolve_run_plain_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span = tracing::debug_span!("resolve_run_plain_dependencies", kind = %kind.command_name())
        .entered();
    let dependencies = read_projected_dependencies(&context.plan.lockfile_path)?;
    let mut artifacts = Vec::new();
    for (index, dependency) in dependencies
        .iter()
        .filter(|dependency| {
            !dependency.loader_managed()
                && dependency_selected_for_run(
                    &dependency.configuration,
                    dependency.data_run_policy,
                    kind,
                )
                && !is_api_classifier(&dependency.coordinate)
        })
        .enumerate()
    {
        artifacts.push((
            ArtifactId::from(format!("run-plain-dependency-{index}")),
            dependency.coordinate.clone(),
            ArtifactPurpose::from("Forge userdev run classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_run_plain_dependencies_resolve_artifacts",
        dependencies = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn is_api_classifier(coordinate: &MavenCoordinate) -> bool {
    coordinate.classifier.as_deref() == Some("api")
}

fn resolve_run_deobf_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span = tracing::debug_span!("resolve_run_deobf_dependencies", kind = %kind.command_name())
        .entered();
    let dependency_output = context.plan.cache_dir.join("dependencies");
    let mapping_path = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("srg_to_official.tsrg");
    if !mapping_path.is_file() {
        eyre::bail!(
            "{} requires generated dependency mappings first: {}",
            kind.command_name(),
            mapping_path.display()
        );
    }
    let mapping_hash = {
        let _span = tracing::debug_span!(
            "resolve_run_deobf_dependencies_hash_mapping",
            mapping = %mapping_path.display()
        )
        .entered();
        ContentHash::from_path(&mapping_path, ContentHashAlgorithm::Blake3)?
    };
    let mut selected = Vec::new();
    for dependency in context
        .plan
        .dependencies
        .iter()
        .filter(|dependency| {
            dependency.artifact_treatment
                == crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::LoaderManagedMod
                && dependency_selected_for_run(
                    &dependency.configuration,
                    dependency.data_run_policy,
                    kind,
                )
        })
    {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        if is_api_classifier(&coordinate) {
            continue;
        }
        let index = selected.len();
        selected.push((dependency, coordinate, index));
    }
    let artifacts = selected
        .iter()
        .map(|(dependency, coordinate, index)| {
            (
                ArtifactId::from(format!("run-deobf-dependency-{index}")),
                coordinate.clone(),
                ArtifactPurpose::from(format!("{} runtime dependency", dependency.configuration)),
            )
        })
        .collect::<Vec<_>>();
    let resolved_artifacts = {
        let _span = tracing::debug_span!(
            "resolve_run_deobf_dependencies_resolve_artifacts",
            dependencies = artifacts.len()
        )
        .entered();
        resolver.resolve_artifacts(artifacts)?
    };
    let mut output = Vec::new();
    for ((_, coordinate, _), artifact) in selected.into_iter().zip(resolved_artifacts) {
        let _span = tracing::debug_span!(
            "resolve_run_deobf_dependency_output",
            coordinate = %coordinate,
            artifact = %artifact.cache_path.display()
        )
        .entered();
        context.assert_allowed_input(&artifact.cache_path)?;
        let artifact_hash = resolved_artifact_hash(&artifact)?;
        let remapped = remapped_dependency_output_path(
            &dependency_output,
            &artifact_hash,
            &mapping_hash,
            &coordinate,
        );
        if !remapped.is_file() {
            eyre::bail!(
                "{} requires remapped dependency jar {}. Run jar build first.",
                kind.command_name(),
                remapped.display()
            );
        }
        context.assert_allowed_input(&remapped)?;
        output.push(remapped);
    }

    Ok(output)
}

fn resolve_neogradle_run_dependencies(
    context: &ExecutionContext<'_>,
    kind: RunKind,
) -> eyre::Result<Vec<PathBuf>> {
    let _span =
        tracing::debug_span!("resolve_neogradle_run_dependencies", kind = %kind.command_name())
            .entered();
    let dependency_output = context.plan.cache_dir.join("dependencies");
    let mut output = Vec::new();
    for dependency in context.plan.dependencies.iter().filter(|dependency| {
        dependency_selected_for_run(
            &dependency.configuration,
            dependency.data_run_policy,
            kind,
        )
            && MavenCoordinate::parse(&dependency.resolved_notation)
                .is_ok_and(|coordinate| !is_api_classifier(&coordinate))
    }) {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let _span = tracing::debug_span!(
            "resolve_neogradle_run_dependency_output",
            coordinate = %coordinate,
            configuration = %dependency.configuration
        )
        .entered();
        let copied = copied_neogradle_dependency_output_path(
            &dependency_output,
            &dependency.configuration,
            &coordinate,
        );
        if !copied.is_file() {
            eyre::bail!(
                "{} requires copied NeoGradle dependency jar {}. Run jar build first.",
                kind.command_name(),
                copied.display()
            );
        }
        context.assert_allowed_input(&copied)?;
        output.push(copied);
    }

    Ok(output)
}

fn run_dependency_configurations(kind: RunKind) -> &'static [&'static str] {
    match kind {
        RunKind::Data => &["jarJar"],
        RunKind::Test => &[
            "implementation",
            "compileOnly",
            "runtimeOnly",
            "testImplementation",
            "testCompileOnly",
            "testRuntimeOnly",
            "transitiveRuntime",
        ],
        RunKind::Client
        | RunKind::ClientSmoke
        | RunKind::ClientPuppet
        | RunKind::Server
        | RunKind::GameTestServer => &[
            "implementation",
            "jarJar",
            "runtimeOnly",
            "gametestImplementation",
            "gametestRuntimeOnly",
            "transitiveRuntime",
        ],
    }
}

fn write_classpath_file(path: &Path, classpath: &[PathBuf]) -> eyre::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let lines = classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>();
    fs::write(path, format!("{}\n", lines.join("\n")))
        .wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn run_source_roots(
    context: &ExecutionContext<'_>,
    kind: RunKind,
    run_options: &RunOptions,
) -> eyre::Result<String> {
    let mod_id = required_property(&context.plan.properties, "mod_id")?;
    let existing_roots = run_source_root_paths(context, kind, run_options)?;
    let roots = existing_roots
        .into_iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(";");
    Ok(format!("{mod_id}%%{roots}"))
}

fn dependency_selected_for_run(
    configuration: &str,
    data_run_policy: crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3,
    kind: RunKind,
) -> bool {
    if matches!(kind, RunKind::Data) {
        return data_run_policy
            == crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3::Include
            && matches!(
                configuration,
                "implementation" | "compileOnly" | "runtimeOnly" | "jarJar"
            );
    }
    run_dependency_configurations(kind).contains(&configuration)
}

fn run_source_root_paths(
    context: &ExecutionContext<'_>,
    kind: RunKind,
    run_options: &RunOptions,
) -> eyre::Result<Vec<PathBuf>> {
    let source_roots = run_project_source_roots(context, kind, run_options)?;
    let combined_root = context
        .plan
        .cache_dir
        .join("project")
        .join("run-mod-root")
        .join(kind.command_name());
    reset_cache_directory(&context.plan.cache_dir, &combined_root)?;
    for source_root in source_roots {
        copy_directory_contents(context, &source_root, &combined_root)?;
    }
    Ok(vec![combined_root])
}

fn run_project_source_roots(
    context: &ExecutionContext<'_>,
    kind: RunKind,
    run_options: &RunOptions,
) -> eyre::Result<Vec<PathBuf>> {
    let project_root = context.plan.cache_dir.join("project");
    let mut roots = vec![
        project_root.join("staged-resources"),
        project_root.join("classes"),
    ];
    if !is_solo_client_like_launch(kind, run_options) {
        let source_set = kind.optional_source_set();
        roots.push(project_root.join(source_set).join("resources"));
        roots.push(project_root.join(source_set).join("classes"));
    }

    let existing_roots = roots
        .into_iter()
        .filter(|path| path.exists())
        .collect::<Vec<_>>();
    if existing_roots.is_empty() {
        eyre::bail!("No Rust-owned project class/resource roots are available for launch");
    }

    Ok(existing_roots)
}

fn copy_directory_contents(
    context: &ExecutionContext<'_>,
    source: &Path,
    destination: &Path,
) -> eyre::Result<()> {
    context.assert_allowed_input(source)?;
    fs::create_dir_all(destination)
        .wrap_err_with(|| format!("Failed to create {}", destination.display()))?;
    for file in collect_files_under_cancellable(context, source)? {
        context.bail_if_cancelled()?;
        let relative = file
            .strip_prefix(source)
            .wrap_err_with(|| format!("Failed to relativize {}", file.display()))?;
        let output = destination.join(relative);
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)
                .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
        }
        fs::copy(&file, &output).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                file.display(),
                output.display()
            )
        })?;
    }
    Ok(())
}

fn kind_extra_program_args(plan: &BuildPlan, kind: RunKind) -> eyre::Result<Vec<String>> {
    if !matches!(kind, RunKind::Data) {
        return Ok(Vec::new());
    }
    Ok(vec![
        "--mod".to_string(),
        required_property(&plan.properties, "mod_id")?.to_string(),
        "--all".to_string(),
        "--output".to_string(),
        plan.minecraft_dir
            .join("src")
            .join("generated")
            .join("resources")
            .display()
            .to_string(),
        "--existing".to_string(),
        plan.minecraft_dir
            .join("src")
            .join("main")
            .join("resources")
            .display()
            .to_string(),
    ])
}

#[expect(
    clippy::too_many_lines,
    reason = "Asset preparation follows the Minecraft version manifest shape linearly."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn prepare_minecraft_assets(context: &ExecutionContext<'_>) -> eyre::Result<MinecraftAssets> {
    context.bail_if_cancelled()?;
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let version_json: MinecraftVersionJson = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_read_version_json",
            path = %context.plan.minecraft.version_json.cache_path.display()
        )
        .entered();
        read_json_file(&context.plan.minecraft.version_json.cache_path)?
    };
    let asset_index = version_json
        .asset_index
        .ok_or_else(|| eyre::eyre!("Minecraft version JSON missing assetIndex"))?;
    let MinecraftAssetIndex {
        id: index_id,
        url: index_url,
    } = asset_index;
    let assets_root = context.plan.minecraft_assets_dir.clone();
    let index_path = assets_root.join("indexes").join(format!("{index_id}.json"));
    {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_download_index",
            index_id = index_id.as_str(),
            path = %index_path.display()
        )
        .entered();
        download_to_path(
            &context.cancellation_token,
            &client,
            &index_url,
            &index_path,
        )?;
    };
    context.bail_if_cancelled()?;

    let index_json: MinecraftAssetIndexJson = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_read_index",
            path = %index_path.display()
        )
        .entered();
        read_json_file(&index_path)?
    };
    let objects = index_json.objects;
    let total_assets = objects.len();
    let assets = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_collect_unique",
            total = total_assets
        )
        .entered();
        minecraft_asset_downloads(&assets_root, &objects)?
    };
    let unique_assets = assets.len();
    let stats = {
        let _span = tracing::debug_span!(
            "prepare_minecraft_assets_download_objects",
            unique_assets,
            workers = rayon::current_num_threads()
        )
        .entered();
        let checked = AtomicUsize::new(0);
        let downloaded = AtomicUsize::new(0);
        let stats = assets
            .par_iter()
            .map(|asset| {
                let asset_downloaded = prepare_minecraft_asset(context, &client, asset)?;
                let checked = checked.fetch_add(1, AtomicOrdering::Relaxed) + 1;
                if asset_downloaded {
                    let downloaded = downloaded.fetch_add(1, AtomicOrdering::Relaxed) + 1;
                    if downloaded.is_multiple_of(100) {
                        tracing::info!(
                            "Downloaded {downloaded} missing Minecraft assets ({checked}/{unique_assets})"
                        );
                    }
                }
                Ok(MinecraftAssetPrepareStats {
                    checked: 1,
                    downloaded: usize::from(asset_downloaded),
                })
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?;
        stats
            .into_iter()
            .fold(MinecraftAssetPrepareStats::default(), |mut total, stats| {
                total.checked += stats.checked;
                total.downloaded += stats.downloaded;
                total
            })
    };
    let downloaded = stats.downloaded;
    if downloaded > 0 {
        tracing::info!(
            "Downloaded {downloaded} Minecraft assets into {}",
            assets_root.display()
        );
    }
    tracing::info!(
        asset_index = index_id.as_str(),
        checked = stats.checked,
        downloaded,
        total = total_assets,
        unique_assets,
        workers = rayon::current_num_threads(),
        assets_root = %assets_root.display(),
        "minecraft_assets_prepared"
    );

    Ok(MinecraftAssets {
        root: assets_root,
        index_id,
    })
}

#[derive(Debug)]
struct MinecraftAssetDownload {
    hash: ContentHash,
    path: PathBuf,
    url: String,
}

#[derive(Debug, Default)]
struct MinecraftAssetPrepareStats {
    checked: usize,
    downloaded: usize,
}

fn minecraft_asset_downloads(
    assets_root: &Path,
    objects: &BTreeMap<String, MinecraftAssetObject>,
) -> eyre::Result<Vec<MinecraftAssetDownload>> {
    let mut assets = BTreeMap::new();
    for object in objects.values() {
        let hash = object.hash;
        let hash_hex = hash.hex();
        let prefix = hash_hex
            .get(..2)
            .ok_or_else(|| eyre::eyre!("Minecraft asset hash is too short: {hash}"))?;
        let key = hash.to_string();
        if assets.contains_key(&key) {
            continue;
        }
        assets.insert(
            key,
            MinecraftAssetDownload {
                hash,
                path: assets_root.join("objects").join(prefix).join(&hash_hex),
                url: format!("https://resources.download.minecraft.net/{prefix}/{hash_hex}"),
            },
        );
    }
    Ok(assets.into_values().collect())
}

fn prepare_minecraft_asset(
    context: &ExecutionContext<'_>,
    client: &Client,
    asset: &MinecraftAssetDownload,
) -> eyre::Result<bool> {
    let _span = tracing::debug_span!(
        "prepare_minecraft_asset",
        hash = %asset.hash,
        path = %asset.path.display()
    )
    .entered();
    context.bail_if_cancelled()?;
    if existing_file_matches_hash(&asset.path, &asset.hash)? {
        context.assert_allowed_input(&asset.path)?;
        return Ok(false);
    }
    download_to_path_overwrite_with_expected_hash(
        &context.cancellation_token,
        client,
        &asset.url,
        &asset.path,
        true,
        &asset.hash,
    )?;
    context.assert_allowed_input(&asset.path)?;
    Ok(true)
}

#[derive(Debug)]
struct ExecutionContext<'a> {
    plan: &'a BuildPlan,
    forbidden_input_roots: Vec<PathBuf>,
    cancellation_token: CancellationToken,
    minecraft_libraries_cache: Mutex<Option<Vec<PathBuf>>>,
}
