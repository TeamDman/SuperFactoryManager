fn resolve_build_targets(options: &BuildOptions) -> eyre::Result<Vec<WorktreeTarget>> {
    select_required_worktree_targets(&options.branch)
}

fn common_toolchain_cache_dir() -> PathBuf {
    CACHE_DIR.0.join("minecraft-toolchain")
}

#[expect(
    clippy::too_many_lines,
    reason = "The planner is a single orchestration pass over project inputs."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %options.branch,
        target = %target.branch,
        refresh = options.refresh,
        allow_local_artifact_cache = options.allow_local_artifact_cache,
        require_portable_artifacts = options.require_portable_artifacts,
    )
)]
fn create_plan_for_target(
    options: &BuildOptions,
    target: &WorktreeTarget,
    cancellation_token: &CancellationToken,
) -> eyre::Result<BuildPlan> {
    cancellation_token.bail_if_cancelled()?;
    let (worktree_path, minecraft_dir, properties_path) = {
        let _span = tracing::debug_span!("plan_resolve_project_paths").entered();
        let worktree_path = target.worktree_path.as_path().to_path_buf();
        let minecraft_dir = worktree_path.join("platform").join("minecraft");
        let properties_path = minecraft_dir.join("gradle.properties");
        (worktree_path, minecraft_dir, properties_path)
    };
    let properties = {
        let _span = tracing::debug_span!("plan_read_properties").entered();
        read_properties(&properties_path)?
    };

    let (
        minecraft_version,
        loader_version,
        mapping_channel,
        mapping_version,
        mod_name,
        mod_version,
    ) = {
        let _span = tracing::debug_span!("plan_read_project_settings").entered();
        let minecraft_version = required_property(&properties, "minecraft_version")?;
        let loader_version = required_property(&properties, "neo_version")?;
        let (mapping_channel, mapping_version) =
            resolve_mapping_settings(&properties, minecraft_version);
        let mod_name = required_property(&properties, "mod_name")?;
        let mod_version = required_property(&properties, "mod_version")?;
        (
            minecraft_version,
            loader_version,
            mapping_channel,
            mapping_version,
            mod_name,
            mod_version,
        )
    };
    let warnings = Vec::new();

    let (
        gradle_output_jar,
        rust_output_jar,
        cache_dir,
        common_cache_dir,
        state_dir,
        maven_cache_dir,
        minecraft_cache_dir,
        minecraft_version_cache_dir,
        minecraft_assets_dir,
        minecraft_libraries_dir,
        lockfile_path,
    ) = {
        let _span = tracing::debug_span!("plan_compute_cache_paths").entered();
        let gradle_output_jar =
            gradle_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version);
        let rust_output_jar =
            rust_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version);
        let cache_dir = minecraft_dir.join("build").join("sfm-toolchain");
        let common_cache_dir = common_toolchain_cache_dir();
        let state_dir = cache_dir.join("state");
        let maven_cache_dir = common_cache_dir.join("maven");
        let minecraft_cache_dir = common_cache_dir.join("minecraft");
        let minecraft_version_cache_dir =
            minecraft_cache_dir.join("versions").join(minecraft_version);
        let minecraft_assets_dir = minecraft_cache_dir.join("assets");
        let minecraft_libraries_dir = minecraft_cache_dir.join("libraries");
        let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
        (
            gradle_output_jar,
            rust_output_jar,
            cache_dir,
            common_cache_dir,
            state_dir,
            maven_cache_dir,
            minecraft_cache_dir,
            minecraft_version_cache_dir,
            minecraft_assets_dir,
            minecraft_libraries_dir,
            lockfile_path,
        )
    };
    {
        let _span = tracing::debug_span!("plan_create_cache_dirs").entered();
        fs::create_dir_all(&state_dir)?;
        fs::create_dir_all(&maven_cache_dir)?;
        fs::create_dir_all(&minecraft_version_cache_dir)?;
        fs::create_dir_all(&minecraft_assets_dir)?;
        fs::create_dir_all(&minecraft_libraries_dir)?;
    };
    cancellation_token.bail_if_cancelled()?;
    let v3_lockfile = {
        let input = fs::read_to_string(&lockfile_path)
            .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;
        crate::toolchain_lockfile_schema::read_current(&input)
            .wrap_err_with(|| format!("Failed to load schema v3 lockfile {}", lockfile_path.display()))?
    };
    let existing_lockfile = {
        let _span = tracing::debug_span!("plan_project_v3_artifact_lockfile", refresh = options.refresh).entered();
        Some(project_v3_artifact_lockfile(
            &v3_lockfile,
            minecraft_version,
            &maven_cache_dir,
        )?)
    };
    let lockfile = if options.refresh {
        None
    } else {
        existing_lockfile.clone()
    };

    let repositories = {
        let _span = tracing::debug_span!("plan_load_repositories").entered();
        v3_lockfile
            .repositories
            .iter()
            .map(|repository| Repository {
                name: repository.id.clone(),
                url: repository.url.clone(),
            })
            .collect::<Vec<_>>()
    };
    let resolver = {
        let _span = tracing::debug_span!(
            "plan_create_resolver",
            repository_count = repositories.len(),
            has_lockfile = lockfile.is_some(),
            has_materialization_lockfile = existing_lockfile.is_some(),
            artifact_source_count = options.artifact_sources.len(),
        )
        .entered();
        Resolver::new(
            maven_cache_dir.clone(),
            repositories.clone(),
            options.refresh,
            options.allow_local_artifact_cache,
            options.artifact_sources.clone(),
            lockfile.clone(),
            existing_lockfile.clone(),
            cancellation_token.clone(),
        )?
    };
    cancellation_token.bail_if_cancelled()?;

    let dependencies = {
        let _span = tracing::debug_span!("plan_project_v3_dependencies").entered();
        project_v3_dependencies(&v3_lockfile)?
    };
    let loader_toolchain = {
        let _span = tracing::debug_span!(
            "plan_resolve_loader_toolchain",
            dependency_count = dependencies.len(),
        )
        .entered();
        resolve_loader_toolchain(&dependencies, minecraft_version, loader_version)?
    };
    cancellation_token.bail_if_cancelled()?;
    let (java_release, java) = {
        let _span = tracing::debug_span!(
            "plan_resolve_java",
            has_java_home = options.java_home.is_some(),
        )
        .entered();
        let java_release = read_java_toolchain_release(&minecraft_dir, minecraft_version)?;
        let required_java = required_java_runtime_major(&loader_toolchain, java_release);

        let java = {
            let jdk_resolution =
                crate::jdk::resolve_java(options.java_home.as_deref(), required_java)?;
            JavaPlan {
                executable: jdk_resolution.executable,
                home: jdk_resolution.home,
                version_output: jdk_resolution.version_output,
                major_version: jdk_resolution.major_version,
            }
        };
        (java_release, java)
    };

    let (forge_userdev, mcp_config) = {
        let _span =
            tracing::debug_span!("plan_resolve_loader_artifacts", loader_kind = ?loader_toolchain.kind)
                .entered();
        let forge_userdev_coordinate =
            MavenCoordinate::parse(&loader_toolchain.userdev_coordinate)?;
        let forge_userdev_artifact = resolver.resolve_artifact(
            ArtifactId::from("forge-userdev"),
            &forge_userdev_coordinate,
            ArtifactPurpose::from("Loader userdev configuration and patches"),
        )?;
        cancellation_token.bail_if_cancelled()?;
        let forge_userdev = read_forge_userdev(&forge_userdev_artifact)?;

        let mcp_config = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            None
        } else if let Some(mcp) = &forge_userdev.mcp {
            let mcp_coordinate = MavenCoordinate::parse(mcp)?;
            let mcp_artifact = resolver.resolve_artifact(
                ArtifactId::from("mcp-config"),
                &mcp_coordinate,
                ArtifactPurpose::from("MCPConfig clean-slate Minecraft pipeline"),
            )?;
            cancellation_token.bail_if_cancelled()?;
            Some(read_mcp_config(&mcp_artifact)?)
        } else {
            None
        };
        (forge_userdev, mcp_config)
    };

    let mut artifacts = Vec::new();
    artifacts.push(forge_userdev.artifact.clone());
    if let Some(mcp_config) = &mcp_config {
        artifacts.push(mcp_config.artifact.clone());
    }

    let core_coordinates = {
        let _span = tracing::debug_span!(
            "plan_select_core_artifacts",
            loader_kind = ?loader_toolchain.kind,
            mapping_channel = mapping_channel.as_str(),
        )
        .entered();
        core_coordinates(
            &loader_toolchain,
            &mapping_channel,
            &mapping_version,
            &forge_userdev,
            mcp_config.as_ref(),
            &dependencies,
        )?
    };
    {
        let _span = tracing::debug_span!(
            "plan_resolve_core_artifacts",
            artifact_count = core_coordinates.len(),
        )
        .entered();
        artifacts.extend(resolver.resolve_artifacts(core_coordinates)?);
    };

    let minecraft = {
        let _span = tracing::debug_span!("plan_resolve_minecraft_inputs").entered();
        resolve_minecraft_plan(
            &minecraft_cache_dir,
            &resolver.client,
            minecraft_version,
            cancellation_token,
        )?
    };
    artifacts.push(minecraft.version_manifest.clone());
    artifacts.push(minecraft.version_json.clone());
    cancellation_token.bail_if_cancelled()?;

    let dependency_plans = {
        let _span = tracing::debug_span!(
            "plan_resolve_project_dependencies",
            parsed_dependency_count = dependencies.len(),
            loader_kind = ?loader_toolchain.kind,
        )
        .entered();
        let selected = dependencies
            .iter()
            .filter(|dependency| should_plan_project_dependency(&loader_toolchain, dependency))
            .collect::<Vec<_>>();
        let mut dependency_plans = resolver.resolve_dependencies(selected.iter().map(|dependency| {
            (
                dependency.configuration.clone(),
                dependency.coordinate.clone(),
            )
        }))?;
        for (plan, dependency) in dependency_plans.iter_mut().zip(selected) {
            plan.bundle.clone_from(&dependency.bundle);
            plan.artifact_treatment = dependency.artifact_treatment;
            plan.data_run_policy = dependency.data_run_policy;
        }
        if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            let _span = tracing::debug_span!(
                "plan_resolve_transitive_runtime_dependencies",
                root_dependency_count = dependency_plans.len(),
            )
            .entered();
            add_transitive_runtime_dependency_plans(&resolver, dependency_plans)?
        } else {
            dependency_plans
        }
    };
    cancellation_token.bail_if_cancelled()?;

    let graph = {
        let _span = tracing::debug_span!(
            "plan_build_graph",
            dependency_count = dependency_plans.len(),
        )
        .entered();
        build_graph(
            minecraft_version,
            &rust_output_jar,
            &dependency_plans,
            &loader_toolchain,
        )
    };

    let mut plan = {
        let _span = tracing::debug_span!(
            "plan_assemble",
            artifact_count = artifacts.len(),
            dependency_count = dependency_plans.len(),
            graph_node_count = graph.len(),
        )
        .entered();
        BuildPlan {
            schema_version: 1,
            mode: match options.mode {
                BuildMode::Plan => "plan".to_string(),
                BuildMode::Build => "build".to_string(),
            },
            branch_name: target.branch.clone(),
            minecraft_version: MinecraftVersion::parse(minecraft_version)?,
            worktree_path,
            minecraft_dir,
            gradle_output_jar,
            rust_output_jar,
            cache_dir,
            common_cache_dir,
            state_dir,
            maven_cache_dir,
            minecraft_cache_dir,
            minecraft_version_cache_dir,
            minecraft_assets_dir,
            minecraft_libraries_dir,
            lockfile_path,
            lockfile,
            java,
            java_release,
            refresh: options.refresh,
            allow_local_artifact_cache: options.allow_local_artifact_cache,
            artifact_sources: options.artifact_sources.clone(),
            properties,
            repositories,
            loader_toolchain,
            artifacts,
            minecraft,
            forge_userdev: Some(forge_userdev),
            mcp_config,
            dependencies: dependency_plans,
            graph,
            artifact_portability: ArtifactPortabilityAudit::default(),
            warnings,
        }
    };
    {
        let _span = tracing::debug_span!(
            "plan_audit_artifact_portability",
            artifact_count = plan.artifacts.len(),
            dependency_count = plan.dependencies.len(),
        )
        .entered();
        plan.artifact_portability = artifact_portability_audit(&plan)?;
    };
    cancellation_token.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!(
            "plan_finalize",
            require_portable_artifacts = options.require_portable_artifacts,
            fresh_slate_portable = plan.artifact_portability.fresh_slate_portable,
        )
        .entered();
        if !plan.artifact_portability.fresh_slate_portable {
            plan.warnings.push(format!(
                "Artifact portability audit found {} non-portable artifact(s); use --require-portable-artifacts to make this a hard failure.",
                plan.artifact_portability.non_portable_artifacts
            ));
        }
        if options.require_portable_artifacts {
            enforce_portable_artifacts(&plan)?;
        }
    }
    Ok(plan)
}

#[expect(
    clippy::too_many_lines,
    reason = "Artifact portability audit is a linear report builder; splitting it would obscure the ordering."
)]
fn artifact_portability_audit(plan: &BuildPlan) -> eyre::Result<ArtifactPortabilityAudit> {
    let mut inputs = BTreeMap::<(Option<String>, PathBuf), ArtifactPortabilityInput>::new();
    for artifact in &plan.artifacts {
        push_artifact_portability_input(
            plan,
            &mut inputs,
            ArtifactPortabilityInput {
                coordinate: artifact
                    .provenance
                    .coordinate
                    .clone()
                    .or_else(|| artifact.coordinate.clone()),
                source: artifact.provenance.source.clone(),
                cache_path: artifact.cache_path.clone(),
                original_path: artifact.provenance.original_path.clone(),
                source_relative_path: artifact.provenance.source_relative_path.clone(),
                source_git: artifact.provenance.source_git.clone(),
                source_build: artifact.provenance.source_build.clone(),
            },
        );
    }
    let dependency_provenance = {
        let _span = tracing::debug_span!(
            "artifact_portability_dependency_inputs",
            dependencies = plan.dependencies.len()
        )
        .entered();
        plan.dependencies
            .par_iter()
            .map(|dependency| {
                let _span = tracing::debug_span!(
                    "artifact_portability_dependency_input",
                    coordinate = %dependency.resolved_notation,
                    cache_path = %dependency.cache_path.display()
                )
                .entered();
                let actual_hash =
                    ContentHash::from_path(&dependency.cache_path, ContentHashAlgorithm::Blake3)?;
                let provenance =
                    read_artifact_provenance(&dependency.cache_path)?.unwrap_or_else(|| {
                        artifact_provenance(
                            ArtifactSource::ExistingSfmCacheUnknown,
                            Some(dependency.resolved_notation.clone()),
                            None,
                            None,
                            None,
                            None,
                            actual_hash,
                        )
                    });

                eyre::Ok((dependency, provenance))
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };

    for (dependency, provenance) in dependency_provenance {
        push_artifact_portability_input(
            plan,
            &mut inputs,
            ArtifactPortabilityInput {
                coordinate: provenance
                    .coordinate
                    .clone()
                    .or_else(|| Some(dependency.resolved_notation.clone())),
                source: provenance.source,
                cache_path: dependency.cache_path.clone(),
                original_path: provenance.original_path,
                source_relative_path: provenance.source_relative_path,
                source_git: provenance.source_git,
                source_build: provenance.source_build,
            },
        );
    }

    let total_artifacts = inputs.len();
    let mut portable_artifacts = 0usize;
    let mut explicit_source_artifacts = 0usize;
    let mut local_cache_artifacts = 0usize;
    let mut unknown_cache_artifacts = 0usize;
    let mut issues = Vec::new();

    for input in inputs.into_values() {
        if input.source.is_fresh_slate_portable() {
            portable_artifacts += 1;
            continue;
        }

        match input.source {
            ArtifactSource::ExplicitSource => explicit_source_artifacts += 1,
            ArtifactSource::ExistingSfmCacheUnknown => unknown_cache_artifacts += 1,
            ArtifactSource::SourceBuild => {}
            ref source if source.is_local_cache() => local_cache_artifacts += 1,
            _ => {}
        }
        issues.push(ArtifactPortabilityIssue {
            coordinate: input.coordinate,
            source: input.source.clone(),
            cache_path: portable_cache_path(plan, &input.cache_path),
            original_path: input.original_path,
            source_relative_path: input.source_relative_path,
            source_git: input.source_git,
            source_build: input.source_build,
            reason: artifact_portability_reason(&input.source).to_string(),
            remediation: artifact_portability_remediation(&input.source).to_string(),
        });
    }

    let non_portable_artifacts = issues.len();
    Ok(ArtifactPortabilityAudit {
        fresh_slate_portable: non_portable_artifacts == 0,
        total_artifacts,
        portable_artifacts,
        non_portable_artifacts,
        explicit_source_artifacts,
        local_cache_artifacts,
        unknown_cache_artifacts,
        issues,
    })
}

fn push_artifact_portability_input(
    plan: &BuildPlan,
    inputs: &mut BTreeMap<(Option<String>, PathBuf), ArtifactPortabilityInput>,
    input: ArtifactPortabilityInput,
) {
    let key = (
        input.coordinate.clone(),
        portable_cache_path(plan, &input.cache_path),
    );
    inputs.entry(key).or_insert(input);
}

fn artifact_portability_reason(source: &ArtifactSource) -> &'static str {
    match source {
        ArtifactSource::ExplicitSource => {
            "artifact was imported from an explicit local artifact source"
        }
        ArtifactSource::SourceBuild => {
            "artifact can be recreated from recorded source build provenance"
        }
        ArtifactSource::LocalM2Cache => "artifact was bootstrapped from the local Maven cache",
        ArtifactSource::LocalGradleModuleCache => {
            "artifact was bootstrapped from the local Gradle module cache"
        }
        ArtifactSource::ExistingSfmCacheUnknown => {
            "artifact exists in the SFM cache without a provenance sidecar"
        }
        ArtifactSource::RemoteMaven | ArtifactSource::RemoteHttp => {
            "artifact can be resolved from configured remote provenance"
        }
    }
}

fn artifact_portability_remediation(source: &ArtifactSource) -> &'static str {
    match source {
        ArtifactSource::ExplicitSource => {
            "publish or host the artifact in a configured repository, provide the same source with --artifact-source, or add a reproducible source-build/import step"
        }
        ArtifactSource::LocalM2Cache | ArtifactSource::LocalGradleModuleCache => {
            "resolve the artifact from a configured remote repository or re-import it from an explicit source so the lockfile does not depend on Maven-created local caches"
        }
        ArtifactSource::ExistingSfmCacheUnknown => {
            "refresh the artifact from remote Maven/HTTP or import it from an explicit source so SFM records provenance"
        }
        ArtifactSource::SourceBuild | ArtifactSource::RemoteMaven | ArtifactSource::RemoteHttp => {
            "no remediation required"
        }
    }
}

fn enforce_portable_artifacts(plan: &BuildPlan) -> eyre::Result<()> {
    if plan.artifact_portability.fresh_slate_portable {
        return Ok(());
    }

    let mut message = format!(
        "Artifact portability audit failed for {}: {} non-portable artifact(s)",
        plan.branch_name, plan.artifact_portability.non_portable_artifacts
    );
    for issue in plan.artifact_portability.issues.iter().take(10) {
        let coordinate = issue.coordinate.as_deref().unwrap_or("<unknown>");
        write!(
            message,
            "\n- {coordinate} [{}] at {}: {}",
            issue.source.label(),
            issue.cache_path.display(),
            issue.remediation
        )?;
    }
    if plan.artifact_portability.issues.len() > 10 {
        write!(
            message,
            "\n- ... and {} more",
            plan.artifact_portability.issues.len() - 10
        )?;
    }
    eyre::bail!(message)
}

fn core_coordinates(
    loader_toolchain: &LoaderToolchainPlan,
    mapping_channel: &str,
    mapping_version: &str,
    userdev: &ForgeUserdevPlan,
    mcp_config: Option<&McpConfigPlan>,
    dependencies: &[ParsedDependency],
) -> eyre::Result<Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>> {
    let mut coordinates = Vec::new();

    if let Some(sources) = userdev
        .sources
        .as_deref()
        .or(loader_toolchain.sources_coordinate.as_deref())
    {
        let artifact_id = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            "neoforge-sources"
        } else {
            "forge-sources"
        };
        coordinates.push((
            ArtifactId::from(artifact_id),
            MavenCoordinate::parse(sources)?,
            ArtifactPurpose::from("Loader source patch application"),
        ));
    }

    if let Some(universal) = userdev
        .universal
        .as_deref()
        .or(loader_toolchain.universal_coordinate.as_deref())
    {
        let artifact_id = if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            "neoforge-universal"
        } else {
            "forge-universal"
        };
        coordinates.push((
            ArtifactId::from(artifact_id),
            MavenCoordinate::parse(universal)?,
            ArtifactPurpose::from("Loader userdev resource merge"),
        ));
    }

    let neoform_coordinate = userdev.neo_form.as_ref().or_else(|| {
        (loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev)
            .then_some(userdev.mcp.as_ref())
            .flatten()
    });
    if let Some(neo_form) = neoform_coordinate {
        coordinates.push((
            ArtifactId::from("neoform-config"),
            MavenCoordinate::parse(neo_form)?,
            ArtifactPurpose::from("NeoForm clean-slate Minecraft pipeline"),
        ));
    }

    if mapping_channel == "parchment" {
        coordinates.push((
            ArtifactId::from("parchment-data"),
            parchment_coordinate(mapping_version)?,
            ArtifactPurpose::from("Parchment names layered over official mappings"),
        ));
    }

    add_userdev_library_coordinates(&mut coordinates, userdev)?;

    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        coordinates.push((
            ArtifactId::from("tool-neoform-runtime"),
            MavenCoordinate::parse(NEOFORM_RUNTIME_COORDINATE)?,
            ArtifactPurpose::from("NeoForm Runtime userdev execution"),
        ));
        add_userdev_test_library_coordinates(&mut coordinates, userdev)?;
        add_project_tool_coordinates(&mut coordinates, dependencies)?;
        return Ok(coordinates);
    }

    if let Some(binpatcher) = &userdev.binpatcher {
        coordinates.push((
            ArtifactId::from("forge-binarypatcher"),
            MavenCoordinate::parse(binpatcher)?,
            ArtifactPurpose::from("Forge binary patch application"),
        ));
    }

    add_mcp_tool_coordinates(&mut coordinates, mcp_config)?;

    add_project_tool_coordinates(&mut coordinates, dependencies)?;

    Ok(coordinates)
}

fn add_userdev_library_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    userdev: &ForgeUserdevPlan,
) -> eyre::Result<()> {
    let mut userdev_coordinates = userdev
        .libraries
        .iter()
        .chain(userdev.modules.iter())
        .cloned()
        .collect::<Vec<_>>();
    userdev_coordinates.sort();
    userdev_coordinates.dedup();

    for (index, coordinate) in userdev_coordinates.iter().enumerate() {
        coordinates.push((
            ArtifactId::from(format!("forge-userdev-library-{index}")),
            MavenCoordinate::parse(coordinate)?,
            ArtifactPurpose::from("Forge userdev compile classpath"),
        ));
    }
    Ok(())
}

fn add_userdev_test_library_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    userdev: &ForgeUserdevPlan,
) -> eyre::Result<()> {
    for (index, coordinate) in userdev.test_libraries.iter().enumerate() {
        coordinates.push((
            ArtifactId::from(format!("forge-userdev-test-library-{index}")),
            MavenCoordinate::parse(coordinate)?,
            ArtifactPurpose::from("Forge userdev game-test runtime classpath"),
        ));
    }
    Ok(())
}

fn should_plan_project_dependency(
    loader_toolchain: &LoaderToolchainPlan,
    dependency: &ParsedDependency,
) -> bool {
    if dependency.coordinate.to_string() == loader_toolchain.base_coordinate {
        return false;
    }

    if !is_planned_project_dependency_configuration(&dependency.configuration) {
        return false;
    }

    true
}

fn is_planned_project_dependency_configuration(configuration: &str) -> bool {
    matches!(
        configuration,
        "implementation"
            | "compileOnly"
            | "runtimeOnly"
            | "jarJar"
            | "annotationProcessor"
            | "gametestImplementation"
            | "gametestCompileOnly"
            | "gametestRuntimeOnly"
    )
}

fn add_transitive_runtime_dependency_plans(
    resolver: &Resolver,
    mut dependencies: Vec<DependencyPlan>,
) -> eyre::Result<Vec<DependencyPlan>> {
    let mut seen = dependencies
        .iter()
        .map(|dependency| dependency.resolved_notation.clone())
        .collect::<BTreeSet<_>>();
    let mut queue = dependencies
        .iter()
        .filter(|dependency| is_runtime_transitive_root(&dependency.configuration))
        .filter_map(|dependency| MavenCoordinate::parse(&dependency.resolved_notation).ok())
        .collect::<Vec<_>>();

    while let Some(root) = queue.pop() {
        resolver.cancellation_token.bail_if_cancelled()?;
        for coordinate in resolver.resolve_pom_runtime_dependencies(&root)? {
            let key = coordinate.to_string();
            if !seen.insert(key) {
                continue;
            }
            let dependency = resolver.resolve_dependency("transitiveRuntime", &coordinate)?;
            queue.push(MavenCoordinate::parse(&dependency.resolved_notation)?);
            dependencies.push(dependency);
        }
    }

    Ok(dependencies)
}

fn is_runtime_transitive_root(configuration: &str) -> bool {
    matches!(
        configuration,
        "implementation" | "runtimeOnly" | "gametestImplementation" | "gametestRuntimeOnly"
    )
}

fn add_project_tool_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    dependencies: &[ParsedDependency],
) -> eyre::Result<()> {
    add_project_compile_annotation_coordinates(coordinates)?;
    for dependency in dependencies {
        if dependency.configuration == "annotationProcessor" {
            coordinates.push((
                ArtifactId::from("mixin-annotation-processor"),
                dependency.coordinate.clone(),
                ArtifactPurpose::from("Mixin refmap generation"),
            ));
        }
    }
    for (index, coordinate) in antlr_classpath_coordinates(antlr_tool_version(dependencies))?
        .into_iter()
        .enumerate()
    {
        let artifact_id = if index == 0 {
            "antlr-tool".to_string()
        } else {
            format!("antlr-tool-dependency-{index}")
        };
        coordinates.push((
            ArtifactId::from(artifact_id),
            MavenCoordinate::parse(&coordinate)?,
            ArtifactPurpose::from("ANTLR grammar generation"),
        ));
    }
    Ok(())
}

fn antlr_tool_version(dependencies: &[ParsedDependency]) -> &str {
    dependencies
        .iter()
        .find(|dependency| dependency.configuration == "antlr")
        .map_or("4.9.1", |dependency| dependency.coordinate.version.as_str())
}

fn add_project_compile_annotation_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
) -> eyre::Result<()> {
    for (artifact_id, coordinate) in PROJECT_COMPILE_ANNOTATION_COORDINATES {
        coordinates.push((
            ArtifactId::from(artifact_id),
            MavenCoordinate::parse(coordinate)?,
            ArtifactPurpose::from("Project compile annotations"),
        ));
    }
    Ok(())
}

fn add_mcp_tool_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    mcp_config: Option<&McpConfigPlan>,
) -> eyre::Result<()> {
    for (id, function_name, fallback_coordinate, required_for) in [
        (
            "tool-installer-tools-1-2",
            "mergeMappings",
            "net.minecraftforge:installertools:1.2.0:fatjar",
            "MCPConfig MERGE_MAPPING function",
        ),
        (
            "tool-installer-tools-1-3",
            "bundleExtractJar",
            "net.minecraftforge:installertools:1.3.0:fatjar",
            "MCPConfig server bundle extraction",
        ),
        (
            "tool-forgeflower",
            "decompile",
            "net.minecraftforge:forgeflower:1.5.605.9",
            "MCPConfig decompile function",
        ),
        (
            "tool-mergetool-1-1-5",
            "merge",
            "net.minecraftforge:mergetool:1.1.5:fatjar",
            "MCPConfig client/server merge function",
        ),
        (
            "tool-fart",
            "rename",
            "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all",
            "MCPConfig rename and jar remapping",
        ),
        (
            "tool-diffpatch",
            "patch",
            "net.minecraftforge:DiffPatch:2.0.12:all",
            "MCPConfig and Forge source patch application",
        ),
        (
            "tool-access-transformers",
            "accessTransformers",
            "net.minecraftforge:accesstransformers:8.0.4:fatjar",
            "Forge access transformer application",
        ),
        (
            "tool-specialsource",
            "reobfuscate",
            "net.md-5:SpecialSource:1.11.0:shaded",
            "Forge-style jar reobfuscation",
        ),
    ] {
        coordinates.push((
            ArtifactId::from(id),
            MavenCoordinate::parse(&mcp_function_coordinate(
                mcp_config,
                function_name,
                fallback_coordinate,
            ))?,
            ArtifactPurpose::from(required_for),
        ));
    }

    Ok(())
}

fn mcp_function_coordinate(
    mcp_config: Option<&McpConfigPlan>,
    function_name: &str,
    fallback_coordinate: &str,
) -> String {
    mcp_config
        .and_then(|config| config.function_coordinates.get(function_name))
        .cloned()
        .unwrap_or_else(|| fallback_coordinate.to_string())
}

fn parchment_coordinate(mapping_version: &str) -> eyre::Result<MavenCoordinate> {
    let parts = mapping_version.split('-').collect::<Vec<_>>();
    let (mc_version, date) = match parts.as_slice() {
        [date, mc_version] if looks_like_parchment_date(date) => (*mc_version, *date),
        [mc_version, date] if looks_like_parchment_date(date) => (*mc_version, *date),
        [mc_version, date, _target_version] if looks_like_parchment_date(date) => {
            (*mc_version, *date)
        }
        _ => {
            eyre::bail!("Unsupported parchment mapping_version: {mapping_version}");
        }
    };
    MavenCoordinate::parse(&format!(
        "org.parchmentmc.data:parchment-{mc_version}:{date}@zip"
    ))
}

fn looks_like_parchment_date(value: &str) -> bool {
    let mut parts = value.split('.');
    let Some(year) = parts.next() else {
        return false;
    };
    year.len() == 4
        && year.chars().all(|character| character.is_ascii_digit())
        && parts.all(|part| {
            !part.is_empty() && part.chars().all(|character| character.is_ascii_digit())
        })
}

fn resolve_mapping_settings(
    properties: &BTreeMap<String, String>,
    minecraft_version: &str,
) -> (String, String) {
    if let (Some(channel), Some(version)) = (
        properties.get("mapping_channel"),
        properties.get("mapping_version"),
    ) {
        return (channel.clone(), version.clone());
    }

    if let (Some(parchment_minecraft), Some(parchment_version)) = (
        properties.get("neogradle.subsystems.parchment.minecraftVersion"),
        properties.get("neogradle.subsystems.parchment.mappingsVersion"),
    ) {
        return (
            "parchment".to_string(),
            format!("{parchment_version}-{parchment_minecraft}"),
        );
    }

    ("official".to_string(), minecraft_version.to_string())
}

fn resolve_loader_toolchain(
    dependencies: &[ParsedDependency],
    minecraft_version: &str,
    loader_version: &str,
) -> eyre::Result<LoaderToolchainPlan> {
    if let Some(dependency) = dependencies
        .iter()
        .find(|dependency| dependency.configuration == "minecraft")
    {
        let coordinate = dependency.coordinate.clone();
        let kind = if coordinate.group == "net.minecraftforge" && coordinate.artifact == "forge" {
            LoaderToolchainKind::ForgeGradleForge
        } else if coordinate.group == "net.neoforged" && coordinate.artifact == "forge" {
            LoaderToolchainKind::ForgeGradleNeoForgeGroup
        } else if coordinate.group == "net.neoforged" && coordinate.artifact == "neoforge" {
            LoaderToolchainKind::NeoGradleUserdev
        } else {
            eyre::bail!(
                "Unsupported ForgeGradle minecraft dependency: {}",
                coordinate
            );
        };

        return Ok(loader_toolchain_plan(kind, &coordinate));
    }

    if let Some(dependency) = dependencies.iter().find(|dependency| {
        dependency.coordinate.group == "net.neoforged"
            && dependency.coordinate.artifact == "neoforge"
    }) {
        return Ok(loader_toolchain_plan(
            LoaderToolchainKind::NeoGradleUserdev,
            &dependency.coordinate,
        ));
    }

    let fallback = MavenCoordinate::parse(&format!(
        "net.minecraftforge:forge:{minecraft_version}-{loader_version}"
    ))?;
    Ok(loader_toolchain_plan(
        LoaderToolchainKind::ForgeGradleForge,
        &fallback,
    ))
}

fn loader_toolchain_plan(
    kind: LoaderToolchainKind,
    base_coordinate: &MavenCoordinate,
) -> LoaderToolchainPlan {
    let userdev_coordinate = base_coordinate.with_classifier("userdev");
    let sources_coordinate = base_coordinate.with_classifier("sources");
    let universal_coordinate = base_coordinate.with_classifier("universal");

    LoaderToolchainPlan {
        kind,
        base_coordinate: base_coordinate.to_string(),
        userdev_coordinate: userdev_coordinate.to_string(),
        sources_coordinate: Some(sources_coordinate.to_string()),
        universal_coordinate: Some(universal_coordinate.to_string()),
    }
}

fn resolve_minecraft_plan(
    minecraft_cache: &Path,
    client: &Client,
    minecraft_version: &str,
    cancellation_token: &CancellationToken,
) -> eyre::Result<MinecraftPlan> {
    cancellation_token.bail_if_cancelled()?;
    fs::create_dir_all(minecraft_cache)?;
    let metadata_cache = minecraft_cache.join("metadata");
    fs::create_dir_all(&metadata_cache)?;
    let manifest_path = metadata_cache.join("version_manifest_v2.json");
    download_to_path(
        cancellation_token,
        client,
        VERSION_MANIFEST_URL,
        &manifest_path,
    )?;
    cancellation_token.bail_if_cancelled()?;
    let manifest: MojangVersionManifest = read_json_file(&manifest_path)?;
    let version_url = manifest
        .versions
        .iter()
        .find_map(|version| (version.id == minecraft_version).then_some(version.url.as_str()))
        .ok_or_else(|| {
            eyre::eyre!("Minecraft version {minecraft_version} not found in Mojang manifest")
        })?;

    let version_cache = minecraft_cache.join("versions").join(minecraft_version);
    fs::create_dir_all(&version_cache)?;
    let version_json_path = version_cache.join("version.json");
    download_to_path(cancellation_token, client, version_url, &version_json_path)?;
    cancellation_token.bail_if_cancelled()?;
    let version_json: MinecraftVersionJson = read_json_file(&version_json_path)?;
    let libraries_count = version_json.libraries.len();

    Ok(MinecraftPlan {
        version_manifest: plain_artifact(
            ArtifactId::from("minecraft-version-manifest"),
            VERSION_MANIFEST_URL,
            manifest_path,
            ArtifactPurpose::from("Minecraft version discovery"),
        )?,
        version_json: plain_artifact(
            ArtifactId::from("minecraft-version-json"),
            version_url,
            version_json_path,
            ArtifactPurpose::from("Minecraft libraries and downloads"),
        )?,
        client_jar_url: version_json.downloads.client.url,
        server_jar_url: version_json.downloads.server.url,
        client_mappings_url: version_json
            .downloads
            .client_mappings
            .map(|download| download.url),
        server_mappings_url: version_json
            .downloads
            .server_mappings
            .map(|download| download.url),
        libraries_count,
    })
}

fn read_forge_userdev(artifact: &ArtifactPlan) -> eyre::Result<ForgeUserdevPlan> {
    let config: ForgeUserdevConfig = read_zip_json_entry(&artifact.cache_path, "config.json")?;
    let binpatcher = config
        .binpatcher
        .as_ref()
        .and_then(|binpatcher| binpatcher.version.clone());
    let modules = config.modules;
    let libraries = config.libraries;
    let module_count = modules.len();
    let library_count = libraries.len();
    let test_libraries = config.test_libraries;
    let run_configs = config.runs.keys().cloned().collect();

    Ok(ForgeUserdevPlan {
        artifact: ArtifactPlan {
            id: artifact.id.clone(),
            coordinate: artifact.coordinate.clone(),
            repository: artifact.repository.clone(),
            url: artifact.url.clone(),
            cache_path: artifact.cache_path.clone(),
            sha1: artifact.sha1,
            downloaded: artifact.downloaded,
            required_for: artifact.required_for.clone(),
            provenance: artifact.provenance.clone(),
        },
        spec: config.spec,
        mcp: config.mcp,
        neo_form: config.neo_form,
        sources: config.sources,
        universal: config.universal,
        binpatcher,
        patches: config.patches,
        patches_original_prefix: config.patches_original_prefix,
        patches_modified_prefix: config.patches_modified_prefix,
        access_transformers: config.ats.map_or_else(Vec::new, StringList::into_vec),
        side_strippers: config.sass.map_or_else(Vec::new, StringList::into_vec),
        modules,
        libraries,
        module_count,
        library_count,
        test_libraries,
        run_configs,
    })
}

fn read_mcp_config(artifact: &ArtifactPlan) -> eyre::Result<McpConfigPlan> {
    let config: McpConfigJson = read_zip_json_entry(&artifact.cache_path, "config.json")?;
    let joined_steps = config
        .steps
        .joined
        .iter()
        .filter_map(|step| step.name.as_ref().or(step.step_type.as_ref()).cloned())
        .collect();
    let mut data_keys = Vec::new();
    if config.data.inject.is_some() {
        data_keys.push("inject".to_string());
    }
    if config.data.mappings.is_some() {
        data_keys.push("mappings".to_string());
    }
    if config
        .data
        .patches
        .as_ref()
        .is_some_and(McpPatchData::has_any_patch_root)
    {
        data_keys.push("patches".to_string());
    }
    let function_count = config
        .functions
        .values()
        .filter(|function| function.has_declared_config())
        .count();
    let function_coordinates = config
        .functions
        .iter()
        .filter_map(|(name, function)| {
            function
                .version
                .as_ref()
                .map(|version| (name.clone(), version.clone()))
        })
        .collect();

    Ok(McpConfigPlan {
        artifact: ArtifactPlan {
            id: artifact.id.clone(),
            coordinate: artifact.coordinate.clone(),
            repository: artifact.repository.clone(),
            url: artifact.url.clone(),
            cache_path: artifact.cache_path.clone(),
            sha1: artifact.sha1,
            downloaded: artifact.downloaded,
            required_for: artifact.required_for.clone(),
            provenance: artifact.provenance.clone(),
        },
        joined_steps,
        function_coordinates,
        function_count,
        data_keys,
        library_count: config.libraries.values().map(Vec::len).sum(),
    })
}

#[derive(Clone, Debug)]
struct ParsedDependency {
    configuration: String,
    coordinate: MavenCoordinate,
    bundle: Option<crate::toolchain_lockfile_schema::version::v3::BundlePolicyV3>,
    artifact_treatment:
        crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3,
    data_run_policy: crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3,
}

impl ParsedDependency {
    fn loader_managed(&self) -> bool {
        self.artifact_treatment
            == crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::LoaderManagedMod
    }
}

fn project_v3_dependencies(
    lockfile: &crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3,
) -> eyre::Result<Vec<ParsedDependency>> {
    let mut projected = Vec::new();
    for dependency in &lockfile.dependencies {
        if dependency.id == lockfile.platform.minecraft_dependency {
            continue;
        }
        for component in &dependency.components {
            let coordinate = component
                .derived_checks
                .resolved_coordinate
                .as_deref()
                .ok_or_else(|| {
                    eyre::eyre!(
                        "Dependency component '{}/{}' has no resolved coordinate.",
                        dependency.id,
                        component.id
                    )
                })?;
            let mut coordinate = MavenCoordinate::parse(coordinate)?;
            let loader_component = dependency.id == lockfile.platform.loader_dependency;
            let configurations = if loader_component {
                vec!["minecraft"]
            } else {
                projected_configurations(&component.declaration.scopes, &coordinate)
            };
            if loader_component {
                coordinate.classifier = None;
            }
            for configuration in configurations {
                projected.push(ParsedDependency {
                    configuration: configuration.to_owned(),
                    coordinate: coordinate.clone(),
                    bundle: (configuration == "jarJar")
                        .then(|| component.declaration.bundle.clone())
                        .flatten(),
                    artifact_treatment: component.declaration.artifact_treatment,
                    data_run_policy: component.declaration.data_run_policy,
                });
            }
        }
    }
    projected.sort_by(|left, right| {
        left.configuration
            .cmp(&right.configuration)
            .then_with(|| left.coordinate.to_string().cmp(&right.coordinate.to_string()))
    });
    projected.dedup_by(|left, right| {
        left.configuration == right.configuration
            && left.coordinate == right.coordinate
            && left.bundle == right.bundle
            && left.artifact_treatment == right.artifact_treatment
            && left.data_run_policy == right.data_run_policy
    });
    Ok(projected)
}

fn read_projected_dependencies(lockfile_path: &Path) -> eyre::Result<Vec<ParsedDependency>> {
    let input = fs::read_to_string(lockfile_path)
        .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;
    let lockfile = crate::toolchain_lockfile_schema::read_current(&input)
        .wrap_err_with(|| format!("Failed to load schema v3 lockfile {}", lockfile_path.display()))?;
    project_v3_dependencies(&lockfile)
}

fn project_v3_artifact_lockfile(
    lockfile: &crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3,
    minecraft_version: &str,
    maven_cache_dir: &Path,
) -> eyre::Result<ArtifactLockfile> {
    use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;

    let projected_dependencies = project_v3_dependencies(lockfile)?;
    let loader_artifact_id = lockfile
        .dependencies
        .iter()
        .find(|dependency| dependency.id == lockfile.platform.loader_dependency)
        .and_then(|dependency| dependency.components.first())
        .map(|component| component.derived_checks.artifact_id.as_str());
    let dependencies = projected_dependencies
        .into_iter()
        .map(|dependency| {
            let coordinate = dependency.coordinate.to_string();
            let artifact = lockfile.artifacts.iter().find(|artifact| {
                if dependency.configuration == "minecraft" {
                    return loader_artifact_id == Some(artifact.id.as_str());
                }
                artifact.coordinate.as_deref() == Some(coordinate.as_str())
            })
                .ok_or_else(|| {
                    eyre::eyre!("Projected dependency artifact is missing: {coordinate}")
                })?;
            Ok(DependencyLockEntry {
                configuration: dependency.configuration,
                notation: coordinate.clone(),
                resolved_notation: coordinate,
                source: if dependency.coordinate.group == "curse.maven" {
                    DependencySource::CurseMaven
                } else {
                    DependencySource::Maven
                },
                dynamic_version: false,
                cache_path: artifact.cache_path.clone(),
            })
        })
        .collect::<eyre::Result<Vec<_>>>()?;
    let artifacts = lockfile
        .artifacts
        .iter()
        .map(|artifact| ArtifactLockEntry {
            coordinate: artifact.coordinate.clone(),
            source: match artifact.provenance {
                ArtifactProvenanceV3::RemoteMaven => ArtifactSource::RemoteMaven,
                ArtifactProvenanceV3::RemoteHttp => ArtifactSource::RemoteHttp,
                ArtifactProvenanceV3::SourceBuild => ArtifactSource::SourceBuild,
                ArtifactProvenanceV3::ToolchainGenerated => {
                    ArtifactSource::ExistingSfmCacheUnknown
                }
            },
            repository: artifact.repository_id.clone(),
            url: artifact.url.clone(),
            cache_path: artifact.cache_path.clone(),
            original_path: None,
            source_relative_path: None,
            source_git: artifact.source_git.clone(),
            source_build: artifact.source_build.clone(),
            hash: artifact.hash,
            weak: artifact.weak.as_ref().map(|weak| WeakArtifactValidation {
                metadata_path: weak.metadata_path.clone(),
                mod_id: weak.mod_id.clone(),
                version: weak.version.clone(),
            }),
        })
        .collect();
    Ok(ArtifactLockfile {
        schema_version: 2,
        minecraft_version: minecraft_version.to_owned(),
        maven_cache_dir: maven_cache_dir.to_path_buf(),
        allow_local_artifact_cache: lockfile.policy.allow_local_artifact_cache,
        repositories: lockfile
            .repositories
            .iter()
            .map(|repository| Repository {
                name: repository.id.clone(),
                url: repository.url.clone(),
            })
            .collect(),
        dependencies,
        artifacts,
    })
}

fn projected_configurations(
    scopes: &[crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3],
    coordinate: &MavenCoordinate,
) -> Vec<&'static str> {
    use crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3;

    let scopes: BTreeSet<_> = scopes.iter().copied().collect();
    let mut configurations = Vec::new();
    if scopes.contains(&DependencyScopeV3::AnnotationProcessor) {
        configurations.push("annotationProcessor");
    }
    if scopes.contains(&DependencyScopeV3::Codegen)
        && coordinate.group == "org.antlr"
        && coordinate.artifact == "antlr4"
    {
        configurations.push("antlr");
    }
    if scopes.contains(&DependencyScopeV3::Bundle) {
        configurations.push("jarJar");
    }
    project_scope_pair(
        &scopes,
        DependencyScopeV3::Compile,
        DependencyScopeV3::Runtime,
        "compileOnly",
        "runtimeOnly",
        "implementation",
        &mut configurations,
    );
    project_scope_pair(
        &scopes,
        DependencyScopeV3::GametestCompile,
        DependencyScopeV3::GametestRuntime,
        "gametestCompileOnly",
        "gametestRuntimeOnly",
        "gametestImplementation",
        &mut configurations,
    );
    project_scope_pair(
        &scopes,
        DependencyScopeV3::TestCompile,
        DependencyScopeV3::TestRuntime,
        "testCompileOnly",
        "testRuntimeOnly",
        "testImplementation",
        &mut configurations,
    );
    if scopes.contains(&DependencyScopeV3::TestAnnotationProcessor) {
        configurations.push("testAnnotationProcessor");
    }
    configurations
}

fn project_scope_pair(
    scopes: &BTreeSet<crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3>,
    compile_scope: crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3,
    runtime_scope: crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3,
    compile_only: &'static str,
    runtime_only: &'static str,
    combined: &'static str,
    configurations: &mut Vec<&'static str>,
) {
    match (
        scopes.contains(&compile_scope),
        scopes.contains(&runtime_scope),
    ) {
        (true, true) => configurations.push(combined),
        (true, false) => configurations.push(compile_only),
        (false, true) => configurations.push(runtime_only),
        (false, false) => {}
    }
}

fn parse_maven_pom_runtime_dependencies(
    pom: &str,
    parent: &MavenCoordinate,
) -> Vec<MavenCoordinate> {
    let properties = parse_maven_pom_properties(pom, parent);
    let search_start = pom
        .find("</dependencyManagement>")
        .map_or(0, |index| index + "</dependencyManagement>".len());
    let Some(dependencies_xml) = extract_xml_section(&pom[search_start..], "dependencies") else {
        return Vec::new();
    };

    let mut coordinates = Vec::new();
    for block in dependencies_xml.split("<dependency").skip(1) {
        let Some(start) = block.find('>') else {
            continue;
        };
        let Some(end) = block[start + 1..].find("</dependency>") else {
            continue;
        };
        let dependency_xml = &block[start + 1..start + 1 + end];
        let scope = extract_xml_tag_text(dependency_xml, "scope").unwrap_or_default();
        if !scope.is_empty() && scope != "compile" && scope != "runtime" {
            continue;
        }
        if extract_xml_tag_text(dependency_xml, "optional")
            .is_some_and(|optional| optional.eq_ignore_ascii_case("true"))
        {
            continue;
        }
        let dependency_type =
            extract_xml_tag_text(dependency_xml, "type").unwrap_or_else(|| "jar".to_string());
        if dependency_type != "jar" {
            continue;
        }

        let Some(group) = extract_resolved_pom_tag(dependency_xml, "groupId", &properties) else {
            continue;
        };
        let Some(artifact) = extract_resolved_pom_tag(dependency_xml, "artifactId", &properties)
        else {
            continue;
        };
        let Some(version) = extract_resolved_pom_tag(dependency_xml, "version", &properties) else {
            continue;
        };
        if version.contains('[') || version.contains('(') {
            continue;
        }
        let classifier = extract_resolved_pom_tag(dependency_xml, "classifier", &properties);
        coordinates.push(MavenCoordinate {
            group,
            artifact,
            version,
            classifier,
            extension: "jar".to_string(),
        });
    }

    coordinates
}

fn parse_maven_pom_properties(pom: &str, parent: &MavenCoordinate) -> BTreeMap<String, String> {
    let mut properties = BTreeMap::from([
        ("project.groupId".to_string(), parent.group.clone()),
        ("pom.groupId".to_string(), parent.group.clone()),
        ("project.artifactId".to_string(), parent.artifact.clone()),
        ("pom.artifactId".to_string(), parent.artifact.clone()),
        ("project.version".to_string(), parent.version.clone()),
        ("pom.version".to_string(), parent.version.clone()),
        ("version".to_string(), parent.version.clone()),
    ]);
    if let Some(properties_xml) = extract_xml_section(pom, "properties") {
        for block in properties_xml.split('<').skip(1) {
            let Some((tag, rest)) = block.split_once('>') else {
                continue;
            };
            if tag.starts_with('/') || tag.contains(char::is_whitespace) {
                continue;
            }
            let end_tag = format!("</{tag}>");
            let Some(end) = rest.find(&end_tag) else {
                continue;
            };
            properties.insert(tag.to_string(), strip_xml_cdata(rest[..end].trim()));
        }
    }
    properties
}

fn extract_resolved_pom_tag(
    input: &str,
    tag: &str,
    properties: &BTreeMap<String, String>,
) -> Option<String> {
    let value = extract_xml_tag_text(input, tag)?;
    let mut resolved = value;
    for _ in 0..8 {
        let Some(start) = resolved.find("${") else {
            return Some(resolved);
        };
        let property_start = start + "${".len();
        let end = resolved[property_start..].find('}')? + property_start;
        let property_name = &resolved[property_start..end];
        let replacement = properties.get(property_name)?;
        resolved.replace_range(start..=end, replacement);
    }
    None
}

fn extract_xml_section(input: &str, tag: &str) -> Option<String> {
    let start_tag = format!("<{tag}>");
    let end_tag = format!("</{tag}>");
    let start = input.find(&start_tag)? + start_tag.len();
    let end = input[start..].find(&end_tag)? + start;
    Some(input[start..end].to_string())
}

fn extract_xml_tag_text(input: &str, tag: &str) -> Option<String> {
    let start_tag = format!("<{tag}>");
    let end_tag = format!("</{tag}>");
    let start = input.find(&start_tag)? + start_tag.len();
    let end = input[start..].find(&end_tag)? + start;
    Some(strip_xml_cdata(input[start..end].trim()))
}

fn strip_xml_cdata(input: &str) -> String {
    input
        .strip_prefix("<![CDATA[")
        .and_then(|value| value.strip_suffix("]]>"))
        .unwrap_or(input)
        .to_string()
}

fn build_graph(
    minecraft_version: &str,
    rust_output_jar: &Path,
    dependencies: &[DependencyPlan],
    loader_toolchain: &LoaderToolchainPlan,
) -> Vec<GraphNode> {
    let mut graph = vec![
        graph_ready(
            "resolve-project-config",
            vec!["gradle.properties", "versioned Gradle fragments"],
            vec!["build/sfm-toolchain/state/last-plan.json"],
        ),
        graph_ready(
            "resolve-maven-and-minecraft-inputs",
            vec![
                "Maven repositories",
                VERSION_MANIFEST_URL,
                "sfm-toolchain.lock.json",
            ],
            vec!["$sfm-cache/maven", "$sfm-cache/minecraft"],
        ),
    ];

    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        graph.push(graph_planned(
            "execute-neoform-userdev",
            "NeoForm userdev inputs are detected; execution support still needs implementation",
            vec![&loader_toolchain.userdev_coordinate],
            vec!["build/sfm-toolchain/neoform"],
        ));
    } else {
        graph.push(graph_planned(
            "execute-mcp-config-joined",
            "MCPConfig joined runtime inputs will be fingerprinted before execution",
            vec![
                &format!("Minecraft {minecraft_version} client/server jars"),
                "MCPConfig config.json",
            ],
            vec!["build/sfm-toolchain/mcp/joined"],
        ));
        graph.push(graph_planned(
            "execute-forge-userdev",
            "Forge userdev inputs will be fingerprinted before execution",
            vec![
                &loader_toolchain.userdev_coordinate,
                "MCPConfig joined outputs",
            ],
            vec!["build/sfm-toolchain/forge"],
        ));
    }

    graph.extend([
        graph_planned(
            "deobfuscate-mod-dependencies",
            "fg.deobf dependency jars will be remapped into SFM-owned cache",
            vec![&format!(
                "{} active fg.deobf dependencies",
                dependencies.len()
            )],
            vec!["build/sfm-toolchain/dependencies"],
        ),
        graph_planned(
            "compile-project",
            "Project sources, resources, classpath, and processors will be fingerprinted before javac",
            vec![
                "src/main/java",
                "src/main/antlr",
                "src/main/resources",
                "mapped Forge/Minecraft jar",
            ],
            vec!["build/sfm-toolchain/project/classes"],
        ),
        graph_planned(
            "package-and-reobfuscate-jar",
            "Development jar and reobfuscation mappings will be fingerprinted before packaging",
            vec!["compiled classes", "expanded resources", "MCP mappings"],
            vec![&rust_output_jar.display().to_string()],
        ),
    ]);

    graph
}

fn graph_ready(id: &str, inputs: Vec<&str>, outputs: Vec<&str>) -> GraphNode {
    GraphNode {
        id: id.to_string(),
        kind: "planning".to_string(),
        status: NodeStatus::Ready,
        inputs: inputs.into_iter().map(str::to_string).collect(),
        outputs: outputs.into_iter().map(str::to_string).collect(),
        rebuild_reason: "Planner inputs were read during this invocation".to_string(),
    }
}

fn graph_planned(id: &str, reason: &str, inputs: Vec<&str>, outputs: Vec<&str>) -> GraphNode {
    GraphNode {
        id: id.to_string(),
        kind: "execution".to_string(),
        status: NodeStatus::Planned,
        inputs: inputs.into_iter().map(str::to_string).collect(),
        outputs: outputs.into_iter().map(str::to_string).collect(),
        rebuild_reason: reason.to_string(),
    }
}

