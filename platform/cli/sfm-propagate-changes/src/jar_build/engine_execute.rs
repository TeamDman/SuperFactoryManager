#[path = "project_compilation_cache.rs"]
mod project_compilation_cache;
#[path = "project_compile_identity.rs"]
mod project_compile_identity;

#[path = "dependency_deobf_producer.rs"]
mod dependency_deobf_producer;
#[cfg(test)]
#[path = "dependency_deobf_producer_tests.rs"]
mod dependency_deobf_producer_tests;
#[cfg(test)]
#[path = "dependency_remap_mapping_tests.rs"]
mod dependency_remap_mapping_tests;

use dependency_deobf_producer::DependencyDeobfProducer;

#[cfg(windows)]
#[path = "dependency_remap_cache.rs"]
mod dependency_remap_cache;

#[derive(Debug, Facet)]
struct NodeState {
    schema_version: u32,
    id: String,
    status: String,
    started_at_unix_ms: u128,
    duration_ms: u128,
    #[facet(proxy = JsonPath)]
    java_executable: PathBuf,
    java_version: String,
    inputs: Vec<String>,
    outputs: Vec<NodeOutputState>,
}

#[derive(Debug, Facet)]
struct NodeOutputState {
    #[facet(proxy = JsonPath)]
    path: PathBuf,
    exists: bool,
    sha1: Option<ContentHash>,
}

fn java_tool_argfile_argument(argfile: &Path) -> eyre::Result<std::ffi::OsString> {
    #[cfg(windows)]
    use std::os::windows::ffi::OsStrExt as _;

    eyre::ensure!(
        argfile.is_absolute(),
        "Java tool transport path must be absolute"
    );
    #[cfg(windows)]
    eyre::ensure!(
        argfile.as_os_str().encode_wide().count() < 260
            && !matches!(
                argfile.components().next(),
                Some(std::path::Component::Prefix(prefix))
                    if matches!(prefix.kind(), std::path::Prefix::Verbatim(_)
                        | std::path::Prefix::VerbatimDisk(_)
                        | std::path::Prefix::VerbatimUNC(_, _))
            ),
        "Java tool transport path must use a normal Windows spelling below 260 UTF-16 units"
    );
    let mut argument = std::ffi::OsString::from("@");
    argument.push(argfile);
    Ok(argument)
}

fn java_tool_argfile_transport(contents: &[u8]) -> eyre::Result<tempfile::NamedTempFile> {
    let mut transport = tempfile::Builder::new()
        .prefix("sfm-java-args-")
        .suffix(".args")
        .tempfile()
        .wrap_err("cannot create owned Java argument-file transport")?;
    java_tool_argfile_argument(transport.path())?;
    transport.as_file_mut().write_all(contents)?;
    transport.as_file_mut().flush()?;
    Ok(transport)
}

fn java_tool_launch_cwd<'a>(
    diagnostic_dir: &'a Path,
    scratch: Option<&'a RetainedSpecialSourceScratch>,
) -> eyre::Result<&'a Path> {
    if let Some(scratch) = scratch {
        scratch.recheck_empty()?;
        Ok(&scratch.path)
    } else {
        Ok(diagnostic_dir)
    }
}

impl<'a> ExecutionContext<'a> {
    fn require_all_hit_compilation_binding(&self) -> eyre::Result<()> {
        if self.plan.retained_inputs().is_some() {
            let bound = self.verify_compilation_outputs && self.compiler_image_identity.is_some();
            #[cfg(any(windows, target_os = "linux"))]
            let bound = bound && self.compiler_image.is_some();
            if !bound {
                self.plan
                    .require_strict("verified compilation manifest and held SDK image required")?;
            }
        }
        Ok(())
    }

    fn new(plan: &'a BuildPlan, cancellation_token: CancellationToken) -> eyre::Result<Self> {
        cancellation_token.bail_if_cancelled()?;
        plan.recheck_named_inputs()?;
        plan.recheck_named_sdk()?;
        let forbidden_input_roots = [
            plan.minecraft_dir.join("build").join("fg_cache"),
            plan.minecraft_dir.join("build").join("classpath"),
            plan.minecraft_dir.join("build").join("classes"),
            plan.minecraft_dir.join("build").join("resources"),
            plan.minecraft_dir.join("build").join("tmp").join("jar"),
        ]
        .into_iter()
        .map(|path| canonicalize_lenient(&path))
        .collect::<eyre::Result<Vec<_>>>()?;

        Ok(Self {
            plan,
            forbidden_input_roots,
            cancellation_token,
            minecraft_libraries_cache: Mutex::new(None),
            held_neoform_compile_jar: None,
            immutable_neoform_profile: false,
            verify_compilation_outputs: false,
            compiler_image_identity: None,
            #[cfg(any(windows, target_os = "linux"))]
            compiler_image: None,
        })
    }

    fn bail_if_cancelled(&self) -> eyre::Result<()> {
        self.cancellation_token.bail_if_cancelled()
    }

    fn recheck_compiler_image(&self) -> eyre::Result<()> {
        #[cfg(any(windows, target_os = "linux"))]
        if let Some(image) = self.compiler_image {
            image.recheck(&self.cancellation_token)?;
        }
        Ok(())
    }

    fn write_node_state(
        &self,
        id: &str,
        inputs: &[&str],
        outputs: &[PathBuf],
        status: &str,
    ) -> eyre::Result<()> {
        self.bail_if_cancelled()?;
        let started = Instant::now();
        let _span = tracing::debug_span!(
            "write_node_state",
            id,
            status,
            inputs = inputs.len(),
            outputs = outputs.len()
        )
        .entered();
        let started_at_unix_ms = {
            let _span = tracing::debug_span!("write_node_state_timestamp").entered();
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map_or(0, |duration| duration.as_millis())
        };
        let output_states = {
            let _span =
                tracing::debug_span!("write_node_state_output_states", outputs = outputs.len())
                    .entered();
            let cancellation_token = self.cancellation_token.clone();
            outputs
                .par_iter()
                .map(|path| {
                    cancellation_token.bail_if_cancelled()?;
                    let _span = tracing::debug_span!(
                        "write_node_state_output_state",
                        path = %path.display()
                    )
                    .entered();
                    let exists = path.exists();
                    let sha1 = path
                        .is_file()
                        .then(|| ContentHash::from_path(path, ContentHashAlgorithm::Blake3))
                        .transpose()?;
                    Ok(NodeOutputState {
                        path: path.clone(),
                        exists,
                        sha1,
                    })
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()?
        };
        self.bail_if_cancelled()?;
        let state = {
            let _span = tracing::debug_span!("write_node_state_build").entered();
            NodeState {
                schema_version: 1,
                id: id.to_string(),
                status: status.to_string(),
                started_at_unix_ms,
                duration_ms: started.elapsed().as_millis(),
                java_executable: self.plan.java.executable.clone(),
                java_version: self.plan.java.version_output.clone(),
                inputs: inputs.iter().map(|input| (*input).to_string()).collect(),
                outputs: output_states,
            }
        };

        {
            let _span = tracing::debug_span!(
                "write_node_state_create_dir",
                dir = %self.plan.state_dir.display()
            )
            .entered();
            fs::create_dir_all(&self.plan.state_dir)?;
        };
        let state_path = self.plan.state_dir.join(format!("{id}.json"));
        let state_json = {
            let _span =
                tracing::debug_span!("write_node_state_encode", path = %state_path.display())
                    .entered();
            facet_json::to_string_pretty(&state)?
        };
        {
            let _span =
                tracing::debug_span!("write_node_state_write", path = %state_path.display())
                    .entered();
            fs::write(&state_path, state_json)
                .wrap_err_with(|| format!("Failed to write {}", state_path.display()))?;
        };
        Ok(())
    }

    fn assert_allowed_input(&self, path: &Path) -> eyre::Result<()> {
        let canonical = canonicalize_lenient(path)?;
        for forbidden in &self.forbidden_input_roots {
            if super::build_snapshot_receipt::relative_spelling(forbidden, &canonical).is_some() {
                eyre::bail!(
                    "Clean-slate jar build attempted to read forbidden Gradle output path: {}",
                    path.display()
                );
            }
        }
        Ok(())
    }

    #[expect(
        clippy::needless_pass_by_value,
        reason = "ArtifactId call sites construct ids inline and this lookup API owns that boundary."
    )]
    fn artifact(&self, id: ArtifactId) -> eyre::Result<&ArtifactPlan> {
        self.plan
            .artifacts
            .iter()
            .find(|artifact| artifact.id == id)
            .ok_or_else(|| eyre::eyre!("Resolved plan did not include artifact id {id}"))
    }

    #[expect(
        clippy::needless_pass_by_value,
        reason = "ArtifactId call sites construct ids inline and this lookup API owns that boundary."
    )]
    fn maybe_artifact(&self, id: ArtifactId) -> Option<&ArtifactPlan> {
        self.plan
            .artifacts
            .iter()
            .find(|artifact| artifact.id == id)
    }

    fn run_java_tool(
        &self,
        tool_id: &str,
        jvm_args: &[&str],
        args: &[String],
        work_dir: &Path,
    ) -> eyre::Result<()> {
        self.run_java_tool_with_classpath(tool_id, jvm_args, &[], args, work_dir)
    }

    #[tracing::instrument(level = "info", skip_all, fields(tool_id))]
    fn run_java_tool_with_classpath(
        &self,
        tool_id: &str,
        jvm_args: &[&str],
        extra_classpath: &[PathBuf],
        args: &[String],
        work_dir: &Path,
    ) -> eyre::Result<()> {
        self.run_java_tool_with_classpath_and_scratch(
            tool_id,
            jvm_args,
            extra_classpath,
            args,
            work_dir,
            None,
        )
    }

    #[tracing::instrument(level = "info", skip_all, fields(tool_id))]
    fn run_java_tool_with_classpath_and_scratch(
        &self,
        tool_id: &str,
        jvm_args: &[&str],
        extra_classpath: &[PathBuf],
        args: &[String],
        work_dir: &Path,
        launch_scratch: Option<&RetainedSpecialSourceScratch>,
    ) -> eyre::Result<()> {
        self.plan.require_strict("Java transform tool")?;
        let tool = self.artifact(ArtifactId::from(tool_id))?;
        self.assert_allowed_input(&tool.cache_path)?;
        for path in extra_classpath {
            self.assert_allowed_input(path)?;
        }
        fs::create_dir_all(work_dir)?;
        let main_class = read_main_class(&tool.cache_path)?;
        let classpath = dedup_paths_preserve_order(
            std::iter::once(tool.cache_path.clone())
                .chain(extra_classpath.iter().cloned())
                .collect(),
        );
        let classpath_arg = join_classpath(&classpath);
        let java_argfile = work_dir.join(format!("{tool_id}.java.args"));
        let mut java_args = jvm_args
            .iter()
            .map(|arg| (*arg).to_string())
            .collect::<Vec<_>>();
        java_args.extend(["-cp".to_string(), classpath_arg.clone(), main_class.clone()]);
        java_args.extend(args.iter().cloned());
        let argfile_contents = java_args
            .into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n");
        fs::write(&java_argfile, &argfile_contents)
            .wrap_err_with(|| format!("Failed to write {}", java_argfile.display()))?;
        // Retain the original file for inspection. Windows' Java launcher uses
        // a short-path reader even when Java/NIO accepts long output paths.
        // Keep this exact-byte transport owner alive until its child finishes.
        let launch_argfile = java_tool_argfile_transport(argfile_contents.as_bytes())?;
        let started = Instant::now();
        let log_path = work_dir.join("console.log");
        tracing::info!(
            "Java tool {tool_id}: start main={main_class} argfile={} log={}",
            java_argfile.display(),
            log_path.display()
        );
        let mut command = Command::new(&self.plan.java.executable);
        command.arg(java_tool_argfile_argument(launch_argfile.path())?);
        let source_check_started = Instant::now();
        self.plan.recheck_named_inputs()?;
        let source_validation_ms = source_check_started.elapsed().as_millis();
        let sdk_check_started = Instant::now();
        self.plan.recheck_named_sdk()?;
        let sdk_validation_ms = sdk_check_started.elapsed().as_millis();
        command.current_dir(java_tool_launch_cwd(work_dir, launch_scratch)?);
        let child_started = Instant::now();
        let output = run_command_capture_output(&self.cancellation_token, &mut command, tool_id)
            .wrap_err_with(|| {
                format!(
                    "Failed to run Java tool {tool_id} using {}",
                    self.plan.java.executable.display()
                )
            })?;
        let child_execution_ms = child_started.elapsed().as_millis();
        trace_subprocess_bytes(self.plan, "java-tool", tool_id, "stdout", &output.stdout);
        trace_subprocess_bytes(self.plan, "java-tool", tool_id, "stderr", &output.stderr);

        let duration_ms = started.elapsed().as_millis();
        write_java_tool_console_log(
            &log_path,
            tool_id,
            &main_class,
            duration_ms,
            &classpath_arg,
            args,
            &output,
        )?;

        if output.cancelled {
            eyre::bail!(
                "Java tool {tool_id} was cancelled by Ctrl+C. See {}",
                log_path.display()
            );
        }
        if !output.status.success() {
            tracing::warn!(
                tool_id,
                status = %output.status,
                duration_ms,
                source_validation_ms,
                sdk_validation_ms,
                child_execution_ms,
                log_path = %log_path.display(),
                "java_tool_failed"
            );
            eyre::bail!(
                "Java tool {tool_id} failed with {}. See {}",
                output.status,
                log_path.display()
            );
        }
        tracing::info!(
            tool_id,
            duration_ms,
            source_validation_ms,
            sdk_validation_ms,
            child_execution_ms,
            log_path = %log_path.display(),
            "java_tool_completed"
        );
        tracing::info!("Java tool {tool_id}: done in {duration_ms} ms");
        Ok(())
    }
}

#[cfg(windows)]
#[path = "forge_preparation_cache.rs"]
mod forge_preparation_cache;

fn execute_mcp_config_joined(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    #[cfg(windows)]
    if forge_preparation_cache::eligible(context) {
        return forge_preparation_cache::execute(
            context,
            "mcp",
            || execute_mcp_config_joined_uncached(context, true),
            |_| execute_mcp_config_joined_uncached(context, false),
        );
    }
    execute_mcp_config_joined_uncached(context, true)
}

fn execute_forge_userdev(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    #[cfg(windows)]
    if forge_preparation_cache::eligible(context) {
        return forge_preparation_cache::execute(
            context,
            "forge",
            || execute_forge_userdev_uncached(context, true, None),
            |at| execute_forge_userdev_uncached(context, false, at),
        );
    }
    execute_forge_userdev_uncached(context, true, None)
}

include!("forge_preparation_producers.rs");

#[expect(
    clippy::too_many_lines,
    reason = "NeoForm userdev orchestration keeps cache checks, arguments, and state writes visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_neoform_userdev(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let neoform_root = context
        .plan
        .cache_dir
        .join("neoform")
        .join(context.plan.minecraft_version.as_str());
    let output_root = neoform_root.join("classes");
    let nfrt_home = neoform_root.join("nfrt-home");
    let nfrt_work = neoform_root.join("nfrt-work");
    let artifact_manifest = neoform_root.join("artifact-manifest.properties");
    let problem_report = neoform_root.join("problems.json");
    let game_jar = neoform_dev_compile_jar(context);
    let game_sources = output_root.join("gameSourcesWithNeoForge.jar");

    if context.plan.refresh {
        tracing::info!("neoform_userdev refresh requested");
        reset_cache_directory(&context.plan.cache_dir, &neoform_root)?;
    }
    fs::create_dir_all(&output_root)?;
    fs::create_dir_all(&nfrt_home)?;
    fs::create_dir_all(&nfrt_work)?;
    write_neoform_artifact_manifest(context, &artifact_manifest)?;

    if game_jar.is_file() && game_sources.is_file() && !context.plan.refresh {
        tracing::info!(
            output = %game_jar.display(),
            "neoform_userdev cache hit"
        );
        context.write_node_state(
            "execute-neoform-userdev",
            &["NeoForge userdev", "NeoForm Runtime"],
            &[game_jar.clone(), game_sources.clone()],
            "complete",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %game_jar.display(),
        refresh = context.plan.refresh,
        "neoform_userdev cache miss"
    );

    let mut args = vec![
        "--home-dir".to_string(),
        nfrt_home.display().to_string(),
        "--work-dir".to_string(),
        nfrt_work.display().to_string(),
        "--artifact-manifest".to_string(),
        artifact_manifest.display().to_string(),
        "--warn-on-artifact-manifest-miss".to_string(),
        "--no-color".to_string(),
        "--no-emojis".to_string(),
    ];
    for repository in &context.plan.repositories {
        args.push(format!("--add-repository={}", repository.url));
    }
    args.extend([
        "run".to_string(),
        "--dist".to_string(),
        "joined".to_string(),
        "--neoforge".to_string(),
        context.plan.loader_toolchain.userdev_coordinate.clone(),
        "--write-result".to_string(),
        format!("gameJarWithNeoForge:{}", game_jar.display()),
        "--write-result".to_string(),
        format!("gameSourcesWithNeoForge:{}", game_sources.display()),
        "--problems-report".to_string(),
        problem_report.display().to_string(),
    ]);

    if let Some(java_home) = &context.plan.java.home {
        args.extend(["--java-home".to_string(), java_home.display().to_string()]);
    }

    if let Some(parchment) = context.maybe_artifact(ArtifactId::from("parchment-data"))
        && let Some(coordinate) = &parchment.coordinate
    {
        args.extend([
            "--parchment-data".to_string(),
            coordinate.clone(),
            "--parchment-conflict-prefix".to_string(),
            "p_".to_string(),
        ]);
    }

    let project_at = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("resources")
        .join("META-INF")
        .join("accesstransformer.cfg");
    if project_at.is_file() {
        context.assert_allowed_input(&project_at)?;
        args.extend([
            "--access-transformer".to_string(),
            project_at.display().to_string(),
        ]);
    }

    context.run_java_tool("tool-neoform-runtime", &[], &args, &neoform_root)?;
    if !game_jar.is_file() {
        eyre::bail!(
            "NeoForm Runtime completed without producing {}",
            game_jar.display()
        );
    }

    context.write_node_state(
        "execute-neoform-userdev",
        &["NeoForge userdev", "NeoForm Runtime"],
        &[game_jar, game_sources],
        "complete",
    )
}

fn neoform_dev_compile_jar(context: &ExecutionContext<'_>) -> PathBuf {
    context
        .plan
        .cache_dir
        .join("neoform")
        .join(context.plan.minecraft_version.as_str())
        .join("classes")
        .join("gameJarWithNeoForge.jar")
}

fn required_minecraft_mapping_url<'a>(
    context: &ExecutionContext<'_>,
    url: Option<&'a str>,
    side: &str,
) -> eyre::Result<&'a str> {
    url.ok_or_else(|| {
        eyre::eyre!(
            "Minecraft {} version metadata does not include {side} mappings; this is only supported by the NeoGradle/NeoForm executor.",
            context.plan.minecraft_version
        )
    })
}

fn loader_dev_compile_jar(context: &ExecutionContext<'_>) -> PathBuf {
    if let Some(held) = context.held_neoform_compile_jar {
        return held.to_path_buf();
    }
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        neoform_dev_compile_jar(context)
    } else {
        context
            .plan
            .cache_dir
            .join("forge")
            .join(context.plan.minecraft_version.as_str())
            .join("classes")
            .join("dev-compile.jar")
    }
}

fn write_neoform_artifact_manifest(
    context: &ExecutionContext<'_>,
    output: &Path,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let mut lines = Vec::new();
    for artifact in &context.plan.artifacts {
        let Some(coordinate) = &artifact.coordinate else {
            continue;
        };
        context.assert_allowed_input(&artifact.cache_path)?;
        lines.push(format!(
            "{}={}",
            java_properties_escape(coordinate),
            java_properties_escape(&artifact.cache_path.display().to_string())
        ));
    }
    lines.sort();
    lines.dedup();
    fs::write(output, lines.join("\n"))
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

fn java_properties_escape(input: &str) -> String {
    let mut output = String::new();
    for character in input.chars() {
        match character {
            '\\' => output.push_str("\\\\"),
            ':' => output.push_str("\\:"),
            '=' => output.push_str("\\="),
            ' ' => output.push_str("\\ "),
            _ => output.push(character),
        }
    }
    output
}

#[expect(
    clippy::too_many_lines,
    reason = "dependency deobf orchestration keeps per-dependency cache and remap behavior visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        mc = %context.plan.minecraft_version,
        dependencies = context.plan.dependencies.len(),
    )
)]
fn execute_dependency_deobf(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let output = context.plan.cache_dir.join("dependencies");
    {
        let _span = tracing::debug_span!(
            "dependency_deobf_prepare_output_dir",
            refresh = context.plan.refresh,
            output = %output.display(),
        )
        .entered();
        if context.plan.refresh {
            reset_cache_directory(&context.plan.cache_dir, &output)?;
        } else {
            fs::create_dir_all(&output)?;
            remove_stale_dependency_outputs(&output)?;
        }
    }
    let resolver = {
        let _span = tracing::debug_span!("dependency_deobf_create_resolver").entered();
        context.plan.resolver(&context.cancellation_token)?
    };
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let _span = tracing::debug_span!("dependency_deobf_copy_neogradle_jars").entered();
        copy_neogradle_dependency_jars(context, &resolver, &output)?;
        return Ok(());
    }

    let mapping_path = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings/srg_to_official.tsrg");
    if !mapping_path.is_file() {
        eyre::bail!(
            "Dependency deobf requires generated mapping first: {}",
            mapping_path.display()
        );
    }
    #[cfg(windows)]
    let remap_authority = if forge_preparation_cache::eligible(context) {
        super::nfrt_preparation_phase::NfrtPreparationPhase::run(
            "dependency_remap.shared_admission",
            || {
                forge_preparation_cache::ForgePreparationInputs::prepare_remap(
                    context,
                    &mapping_path,
                )
            },
        )?
    } else {
        None
    };
    #[cfg(windows)]
    let remap_key = remap_authority
        .as_ref()
        .map(|owner| owner.fingerprint().to_owned());
    #[cfg(windows)]
    let remap_check = remap_authority
        .as_ref()
        .map(forge_preparation_cache::ForgePreparationInputs::remap_recheck);
    #[cfg(windows)]
    let (mapping_hash, member_mappings) = if let Some(owner) = &remap_authority {
        tracing::info!(outcome = "admitted", "dependency_remap_shared_authority");
        let raw = owner.mapping_bytes();
        let mapping_text =
            std::str::from_utf8(raw).wrap_err("Captured remap mapping is not UTF-8")?;
        (
            ContentHash::from_bytes(raw, ContentHashAlgorithm::Blake3),
            parse_unique_srg_member_mappings(mapping_text),
        )
    } else {
        if forge_preparation_cache::eligible(context) {
            tracing::info!(outcome = "declined", "dependency_remap_shared_authority");
        }
        (
            ContentHash::from_path(&mapping_path, ContentHashAlgorithm::Blake3)?,
            read_unique_srg_member_mappings(&mapping_path)?,
        )
    };
    #[cfg(not(windows))]
    let (mapping_hash, member_mappings) = (
        ContentHash::from_path(&mapping_path, ContentHashAlgorithm::Blake3)?,
        read_unique_srg_member_mappings(&mapping_path)?,
    );
    let outputs: Vec<PathBuf> = {
        let _span = tracing::debug_span!(
            "dependency_deobf_process_dependencies",
            dependency_count = context.plan.dependencies.len(),
            workers = rayon::current_num_threads()
        )
        .entered();
        // Resolve every original row through the strict resolver before grouping.
        // Duplicate configurations share a physical producer, never a second lock
        // acquisition nested in another producer's cross-pool source recheck.
        let mut rows = Vec::new();
        for (index, dependency) in context.plan.dependencies.iter().enumerate() {
            if !requires_forge_dependency_deobf(dependency) {
                continue;
            }
            context.bail_if_cancelled()?;
            let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
            let artifact = resolver.resolve_artifact(
                ArtifactId::from(format!("dependency-{index}")),
                &coordinate,
                ArtifactPurpose::from(format!("{} dependency", dependency.configuration)),
            )?;
            context.assert_allowed_input(&artifact.cache_path)?;
            rows.push(DependencyDeobfProducer::new(
                index,
                coordinate,
                artifact,
                &output,
                mapping_hash,
            )?);
        }
        let original_rows = rows.len();
        let producers = DependencyDeobfProducer::select(rows)?;
        tracing::info!(
            dependency_rows = original_rows,
            physical_producers = producers.len(),
            "Selected distinct dependency deobf producers; all configuration bindings retained"
        );
        let producer_results = producers
            .par_iter()
            .map(|producer| {
                context.bail_if_cancelled()?;
                #[cfg(windows)]
                if let (Some(key), Some(check)) = (&remap_key, &remap_check) {
                    return dependency_remap_cache::execute(
                        context,
                        key,
                        producer,
                        &mapping_path,
                        &member_mappings,
                        &|| check(context),
                        || {
                            execute_dependency_deobf_dependency(
                                context,
                                &resolver,
                                producer,
                                &mapping_path,
                                &member_mappings,
                            )
                        },
                    );
                }
                execute_dependency_deobf_dependency(
                    context,
                    &resolver,
                    producer,
                    &mapping_path,
                    &member_mappings,
                )
            })
            .collect::<Vec<eyre::Result<_>>>();
        all_hit_cache_miss::collect_results(producer_results)
            .wrap_err("Failed to deobfuscate dependency")?;
        DependencyDeobfProducer::output_bindings(&producers)
    };

    #[cfg(windows)]
    if let Some(owner) = &remap_authority {
        owner.recheck(context)?;
    }
    {
        let _span =
            tracing::debug_span!("dependency_deobf_write_node_state", outputs = outputs.len())
                .entered();
        context.write_node_state(
            "deobfuscate-mod-dependencies",
            &["active fg.deobf dependency jars"],
            &outputs,
            "complete",
        )?;
    };
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "Dependency deobfuscation is orchestration-heavy and kept linear for cache/debug tracing."
)]
fn execute_dependency_deobf_dependency(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    producer: &DependencyDeobfProducer,
    mapping_path: &Path,
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<()> {
    let dependency_index = producer.dependency_indexes[0];
    let dependency = &context.plan.dependencies[dependency_index];
    let coordinate = &producer.coordinate;
    let artifact = &producer.artifact;
    let remapped = &producer.remapped;
    let specialsource_output = &producer.specialsource_output;
    let _dependency_span = tracing::debug_span!(
        "dependency_deobf_dependency",
        index = dependency_index,
        configuration = %dependency.configuration,
        coordinate = %coordinate,
        source = ?dependency.source,
    )
    .entered();
    let cache_hit = {
        let _span = tracing::debug_span!(
            "dependency_deobf_check_remapped_cache",
            output = %remapped.display()
        )
        .entered();
        remapped.is_file()
    };
    if cache_hit {
        tracing::debug!(
            coordinate = %coordinate,
            output = %remapped.display(),
            "dependency_deobf cache hit"
        );
    } else {
        let _output_lock = {
            let _span = tracing::debug_span!(
                "dependency_deobf_acquire_output_lock",
                output = %remapped.display()
            )
            .entered();
            acquire_artifact_path_lock_cancellable(remapped, &resolver.cancellation_token)?
        };
        if remapped.is_file() {
            tracing::debug!(
                coordinate = %coordinate,
                output = %remapped.display(),
                "dependency_deobf cache hit"
            );
        } else {
            let _span = info_span!(
                "dependency_deobf cache miss",
                %coordinate,
                output = %remapped.display(),
            )
            .entered();
            let specialsource_cache_hit = {
                let _span = tracing::debug_span!(
                    "dependency_deobf_check_specialsource_cache",
                    output = %specialsource_output.display()
                )
                .entered();
                specialsource_output.is_file()
            };
            if specialsource_cache_hit {
                tracing::debug!(
                    coordinate = %coordinate,
                    output = %specialsource_output.display(),
                    "dependency_deobf specialsource cache hit"
                );
            } else {
                if let Some(parent) = specialsource_output.parent() {
                    let _span = tracing::debug_span!(
                        "dependency_deobf_create_specialsource_output_dir",
                        output = %specialsource_output.display()
                    )
                    .entered();
                    fs::create_dir_all(parent)?;
                }
                {
                    let _span = tracing::debug_span!(
                        "dependency_deobf_run_specialsource",
                        input = %artifact.cache_path.display(),
                        output = %specialsource_output.display(),
                    )
                    .entered();
                    context.run_dependency_specialsource(
                        artifact,
                        specialsource_output,
                        mapping_path,
                        producer.mapping_hash(),
                        coordinate,
                        &context
                            .plan
                            .cache_dir
                            .join("dependencies")
                            .join("remap-work")
                            .join(safe_path_segment(&coordinate.file_name())),
                    )?;
                }
            }
            {
                let _span = tracing::debug_span!(
                    "dependency_deobf_rewrite_member_constants",
                    input = %specialsource_output.display(),
                    output = %remapped.display(),
                )
                .entered();
                rewrite_srg_member_constants_in_jar(
                    specialsource_output,
                    remapped,
                    member_mappings,
                )?;
            }
        }
    }
    {
        let _span = tracing::debug_span!(
            "dependency_deobf_verify_remapped_output",
            output = %remapped.display()
        )
        .entered();
        if !remapped.is_file() {
            eyre::bail!(
                "SpecialSource completed without producing remapped dependency jar {}",
                remapped.display()
            );
        }
    }
    Ok(())
}

fn copy_neogradle_dependency_jars(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    output: &Path,
) -> eyre::Result<()> {
    let mut outputs = Vec::new();
    for dependency in &context.plan.dependencies {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let artifact = resolver.resolve_artifact(
            ArtifactId::from(format!("dependency-{}", outputs.len())),
            &coordinate,
            ArtifactPurpose::from(format!("{} dependency", dependency.configuration)),
        )?;
        context.assert_allowed_input(&artifact.cache_path)?;
        let copied =
            copied_neogradle_dependency_output_path(output, &dependency.configuration, &coordinate);
        fs::copy(&artifact.cache_path, &copied).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                artifact.cache_path.display(),
                copied.display()
            )
        })?;
        outputs.push(copied);
    }
    context.write_node_state(
        "deobfuscate-mod-dependencies",
        &["active NeoGradle dependency jars"],
        &outputs,
        "complete",
    )?;
    Ok(())
}

fn copied_neogradle_dependency_output_path(
    output_dir: &Path,
    configuration: &str,
    coordinate: &MavenCoordinate,
) -> PathBuf {
    output_dir.join(format!(
        "{}-{}",
        safe_path_segment(configuration),
        coordinate.file_name()
    ))
}

fn remapped_dependency_output_path(
    output_dir: &Path,
    input_hash: &ContentHash,
    mapping_hash: &ContentHash,
    coordinate: &MavenCoordinate,
) -> PathBuf {
    output_dir.join(format!(
        "{}-{}-named-mixin-{}",
        input_hash.short_hex(12),
        mapping_hash.short_hex(12),
        coordinate.file_name()
    ))
}

fn specialsource_dependency_output_path(
    output_dir: &Path,
    input_hash: &ContentHash,
    mapping_hash: &ContentHash,
    coordinate: &MavenCoordinate,
) -> PathBuf {
    output_dir.join("specialsource").join(format!(
        "{}-{}-specialsource-{}",
        input_hash.short_hex(12),
        mapping_hash.short_hex(12),
        coordinate.file_name()
    ))
}

fn resolved_artifact_hash(artifact: &ArtifactPlan) -> eyre::Result<ContentHash> {
    artifact.sha1.ok_or_else(|| {
        eyre::eyre!(
            "Resolved artifact {} did not record a content hash",
            artifact.cache_path.display()
        )
    })
}

#[instrument(
    level = "debug",
    skip_all,
    fields(mapping = %mapping_path.display())
)]
fn read_unique_srg_member_mappings(mapping_path: &Path) -> eyre::Result<BTreeMap<String, String>> {
    let content = fs::read_to_string(mapping_path)
        .wrap_err_with(|| format!("Failed to read {}", mapping_path.display()))?;
    Ok(parse_unique_srg_member_mappings(&content))
}

fn parse_unique_srg_member_mappings(content: &str) -> BTreeMap<String, String> {
    let candidates = {
        let _span = tracing::debug_span!(
            "read_unique_srg_member_mappings_parse",
            bytes = content.len()
        )
        .entered();
        content
            .par_lines()
            .filter_map(parse_srg_member_mapping_line)
            .fold(BTreeMap::new, |mut candidates, (srg, named)| {
                insert_unique_member_mapping(&mut candidates, srg, named);
                candidates
            })
            .reduce(BTreeMap::new, merge_unique_member_mapping_candidates)
    };

    candidates
        .into_iter()
        .filter_map(|(srg, named)| named.map(|named| (srg, named)))
        .collect()
}

fn parse_srg_member_mapping_line(line: &str) -> Option<(&str, &str)> {
    if !line.starts_with('\t') && !line.starts_with(' ') {
        return None;
    }
    if line.starts_with("\t\t") || line.starts_with("  ") {
        return None;
    }
    let parts = line.split_whitespace().collect::<Vec<_>>();
    match parts.as_slice() {
        [srg, named] if is_srg_member_name(srg) => Some((*srg, *named)),
        [srg, _descriptor, named] if is_srg_member_name(srg) => Some((*srg, *named)),
        _ => None,
    }
}

fn insert_unique_member_mapping(
    candidates: &mut BTreeMap<String, Option<String>>,
    srg: &str,
    named: &str,
) {
    match candidates.get_mut(srg) {
        Some(existing) if existing.as_deref() == Some(named) => {}
        Some(existing) => *existing = None,
        None => {
            candidates.insert(srg.to_string(), Some(named.to_string()));
        }
    }
}

fn merge_unique_member_mapping_candidates(
    mut left: BTreeMap<String, Option<String>>,
    right: BTreeMap<String, Option<String>>,
) -> BTreeMap<String, Option<String>> {
    for (srg, right_named) in right {
        match (left.get_mut(&srg), right_named) {
            (None, named) => {
                left.insert(srg, named);
            }
            (Some(left_named), Some(right_named))
                if left_named.as_deref() == Some(right_named.as_str()) => {}
            (Some(left_named), _) => *left_named = None,
        }
    }
    left
}

fn is_srg_member_name(name: &str) -> bool {
    let Some(rest) = name.strip_prefix("f_").or_else(|| name.strip_prefix("m_")) else {
        return false;
    };
    let Some(number) = rest.strip_suffix('_') else {
        return false;
    };
    !number.is_empty() && number.chars().all(|character| character.is_ascii_digit())
}

fn rewrite_srg_member_constants_in_jar(
    input: &Path,
    output: &Path,
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let input_bytes =
        fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(input_bytes))
        .wrap_err_with(|| format!("Failed to open {}", input.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    let mut replacements = 0usize;

    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') || is_signature_file(&name) {
            continue;
        }
        if !zip_entry_has_extension(&name, "class") {
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
            continue;
        }
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .wrap_err_with(|| format!("Failed to read entry {name} from {}", input.display()))?;
        let (patched, patched_count) = rewrite_class_srg_member_constants(&bytes, member_mappings)
            .wrap_err_with(|| format!("Failed to patch class entry {name}"))?;
        bytes = patched;
        replacements += patched_count;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    if replacements > 0 {
        tracing::info!(
            "Patched {replacements} SRG member constants in {}",
            output.display()
        );
    }
    Ok(())
}

fn rewrite_class_srg_member_constants(
    bytes: &[u8],
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<(Vec<u8>, usize)> {
    if bytes.len() < 10 || bytes.get(..4) != Some(&[0xCA, 0xFE, 0xBA, 0xBE]) {
        return Ok((bytes.to_vec(), 0));
    }

    let constant_pool_count = read_u16(bytes, 8)? as usize;
    let mut output = Vec::with_capacity(bytes.len());
    output.extend_from_slice(&bytes[..10]);
    let mut cursor = 10usize;
    let mut index = 1usize;
    let mut replacements = 0usize;

    while index < constant_pool_count {
        let tag = *bytes
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Class constant pool is truncated"))?;
        output.push(tag);
        cursor += 1;
        match tag {
            1 => {
                let length = read_u16(bytes, cursor)? as usize;
                let content_start = cursor + 2;
                let content_end = content_start
                    .checked_add(length)
                    .ok_or_else(|| eyre::eyre!("Utf8 constant length overflow"))?;
                if content_end > bytes.len() {
                    eyre::bail!("Utf8 constant extends past end of class file");
                }
                let content = &bytes[content_start..content_end];
                if let Ok(text) = std::str::from_utf8(content)
                    && let Some(replacement) = member_mappings.get(text)
                {
                    let replacement_bytes = replacement.as_bytes();
                    let replacement_len = u16::try_from(replacement_bytes.len())
                        .wrap_err("Replacement member name is too long for classfile UTF8")?;
                    output.extend_from_slice(&replacement_len.to_be_bytes());
                    output.extend_from_slice(replacement_bytes);
                    replacements += 1;
                } else {
                    output.extend_from_slice(&bytes[cursor..content_end]);
                }
                cursor = content_end;
            }
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 4)?;
            }
            5 | 6 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 8)?;
                index += 1;
            }
            7 | 8 | 16 | 19 | 20 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 2)?;
            }
            15 => {
                copy_constant_pool_payload(bytes, &mut output, &mut cursor, 3)?;
            }
            _ => eyre::bail!("Unsupported class constant pool tag {tag}"),
        }
        index += 1;
    }

    output.extend_from_slice(
        bytes
            .get(cursor..)
            .ok_or_else(|| eyre::eyre!("Class constant pool extends past end of file"))?,
    );
    Ok((output, replacements))
}

fn copy_constant_pool_payload(
    input: &[u8],
    output: &mut Vec<u8>,
    cursor: &mut usize,
    length: usize,
) -> eyre::Result<()> {
    let end = cursor
        .checked_add(length)
        .ok_or_else(|| eyre::eyre!("Constant pool payload length overflow"))?;
    let payload = input
        .get(*cursor..end)
        .ok_or_else(|| eyre::eyre!("Class constant pool extends past end of file"))?;
    output.extend_from_slice(payload);
    *cursor = end;
    Ok(())
}

fn remove_stale_dependency_outputs(output: &Path) -> eyre::Result<()> {
    for entry in
        fs::read_dir(output).wrap_err_with(|| format!("Failed to read {}", output.display()))?
    {
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", output.display()))?;
        let path = entry.path();
        if !path.is_file() || !zip_entry_has_extension(&path.to_string_lossy(), "jar") {
            continue;
        }
        let file_name = path
            .file_name()
            .and_then(std::ffi::OsStr::to_str)
            .map_or("", |name| name);
        if !file_name.contains("-named-mixin-") {
            fs::remove_file(&path).wrap_err_with(|| {
                format!(
                    "Failed to remove stale dependency output {}",
                    path.display()
                )
            })?;
        }
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "project compile orchestration keeps generated sources, resources, and javac inputs visible."
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_project_compile(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    context.require_all_hit_compilation_binding()?;
    let (
        project_root,
        generated_sources,
        classes_dir,
        resources_dir,
        staged_resources_dir,
        gametest_classes_dir,
        gametest_resources_dir,
        datagen_classes_dir,
        datagen_resources_dir,
    ) = {
        let _span = tracing::debug_span!("project_compile_resolve_paths").entered();
        let project_root = context.plan.cache_dir.join("project");
        let generated_sources = project_root
            .join("generated-src")
            .join("antlr")
            .join("main")
            .join("ca")
            .join("teamdman")
            .join("langs");
        let classes_dir = project_root.join("classes");
        let resources_dir = project_root.join("resources");
        let staged_resources_dir = project_root.join("staged-resources");
        let gametest_classes_dir = project_root.join("gametest").join("classes");
        let gametest_resources_dir = project_root.join("gametest").join("resources");
        let datagen_classes_dir = project_root.join("datagen").join("classes");
        let datagen_resources_dir = project_root.join("datagen").join("resources");
        (
            project_root,
            generated_sources,
            classes_dir,
            resources_dir,
            staged_resources_dir,
            gametest_classes_dir,
            gametest_resources_dir,
            datagen_classes_dir,
            datagen_resources_dir,
        )
    };
    tracing::info!(
        classes_dir = %classes_dir.display(),
        resources_dir = %resources_dir.display(),
        gametest_classes_dir = %gametest_classes_dir.display(),
        datagen_classes_dir = %datagen_classes_dir.display(),
        "project_compile_outputs_will_be_recreated"
    );
    {
        let _span = tracing::debug_span!(
            "project_compile_prepare_generated_sources",
            output = %generated_sources.display()
        )
        .entered();
        fs::create_dir_all(&generated_sources)?;
    };
    context.bail_if_cancelled()?;

    let resolver = {
        let _span = tracing::debug_span!("project_compile_create_resolver").entered();
        context.plan.resolver(&context.cancellation_token)?
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("project_compile_write_minecraft_libraries_cfg").entered();
        write_minecraft_libraries_cfg(
            context,
            &resolver.client,
            &project_root.join("minecraft-libraries.cfg"),
        )?;
    };
    context.bail_if_cancelled()?;
    let antlr_classpath = {
        let _span = tracing::debug_span!("project_compile_resolve_antlr_classpath").entered();
        resolve_antlr_classpath(context, &resolver)?
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!(
            "project_compile_run_antlr",
            generated_sources = %generated_sources.display()
        )
        .entered();
        run_antlr(context, &antlr_classpath, &generated_sources)?;
    };
    context.bail_if_cancelled()?;

    let (classpath, sources) = {
        let resolver = resolver.clone();
        let generated_sources = generated_sources.as_path();
        let (classpath, sources) = rayon::join(
            || {
                let _span =
                    tracing::debug_span!("project_compile_resolve_main_classpath").entered();
                context.bail_if_cancelled()?;
                resolve_project_compile_classpath(context, &resolver, &antlr_classpath)
                    .wrap_err("Failed to resolve project compile classpath")
            },
            || {
                let _span = tracing::debug_span!(
                    "project_compile_collect_main_sources",
                    generated_sources = %generated_sources.display()
                )
                .entered();
                context.bail_if_cancelled()?;
                collect_project_java_sources(context, generated_sources)
                    .wrap_err("Failed to collect project Java sources")
            },
        );
        all_hit_cache_miss::pair_results(classpath, sources)?
    };
    context.bail_if_cancelled()?;
    let argfile = project_root.join("javac-main.args");
    {
        let _span = tracing::debug_span!(
            "project_compile_write_main_argfile",
            argfile = %argfile.display(),
            sources = sources.len(),
            classpath = classpath.len(),
        )
        .entered();
        write_javac_argfile(context, &argfile, &classpath, &sources, &classes_dir)?;
    };
    context.bail_if_cancelled()?;

    let started = Instant::now();
    tracing::info!(
        "javac main: start sources={} argfile={}",
        sources.len(),
        argfile.display()
    );
    let mut main_fingerprint_paths = classpath.clone();
    main_fingerprint_paths.extend(sources.iter().cloned());
    main_fingerprint_paths.push(compilation_argfile_fingerprint_path(context, &argfile));
    if let Some(main_source_excludes) = source_exclusion_fingerprint_path(context, "main")? {
        main_fingerprint_paths.push(main_source_excludes);
    }
    context.bail_if_cancelled()?;
    let main_fingerprint = {
        let _span = tracing::debug_span!(
            "project_compile_fingerprint_main",
            inputs = main_fingerprint_paths.len()
        )
        .entered();
        input_fingerprint(
            context,
            "javac-main",
            &main_fingerprint_paths,
            &[
                context.plan.java.cache_identity(),
                context.plan.java_release.to_string(),
                format!("{:?}", context.plan.loader_toolchain.kind),
            ],
        )?
    };
    context.bail_if_cancelled()?;
    let main_state_path = project_root.join("javac-main.inputs.sha1");
    let main_refmap = resources_dir.join("sfm.refmap.json");
    let main_cache_hit = {
        let _span = tracing::debug_span!(
            "project_compile_check_main_cache",
            state = %main_state_path.display(),
            classes = %classes_dir.display(),
            refmap = %main_refmap.display(),
        )
        .entered();
        main_compilation_cache_matches(
            context,
            &main_state_path,
            &main_fingerprint,
            &classes_dir,
            &main_refmap,
        )?
    };
    context.bail_if_cancelled()?;
    if main_cache_hit {
        tracing::info!(
            "javac main: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
    } else {
        context.bail_if_cancelled()?;
        context.plan.require_strict("main javac cache miss")?;
        {
            let _span = tracing::debug_span!(
                "project_compile_reset_main_classes",
                output = %classes_dir.display()
            )
            .entered();
            reset_cache_directory(&context.plan.cache_dir, &classes_dir)?;
        };
        context.bail_if_cancelled()?;
        {
            let _span = tracing::debug_span!(
                "project_compile_reset_javac_resources",
                output = %resources_dir.display()
            )
            .entered();
            reset_cache_directory(&context.plan.cache_dir, &resources_dir)?;
        };
        context.bail_if_cancelled()?;
        let mut command = Command::new(javac_executable(&context.plan.java));
        command.arg(format!("@{}", argfile.display()));
        context.bail_if_cancelled()?;
        let output = {
            let _span = tracing::debug_span!(
                "project_compile_run_javac_main",
                sources = sources.len(),
                argfile = %argfile.display()
            )
            .entered();
            context.plan.recheck_named_inputs()?;
            context.plan.recheck_named_sdk()?;
            context.recheck_compiler_image()?;
            run_command_capture_output(&context.cancellation_token, &mut command, "javac-main")
                .wrap_err("Failed to run javac")?
        };
        {
            let _span = tracing::debug_span!("project_compile_trace_javac_main_output").entered();
            trace_subprocess_bytes(
                context.plan,
                "java-tool",
                "javac-main",
                "stdout",
                &output.stdout,
            );
        };
        context.bail_if_cancelled()?;
        {
            let _span = tracing::debug_span!("project_compile_trace_javac_main_error").entered();
            trace_subprocess_bytes(
                context.plan,
                "java-tool",
                "javac-main",
                "stderr",
                &output.stderr,
            );
        };
        let log_path = project_root.join("javac-main.log");
        {
            let _span = tracing::debug_span!(
                "project_compile_write_javac_main_log",
                log = %log_path.display()
            )
            .entered();
            let mut log = Vec::new();
            log.extend_from_slice(b"--- stdout ---\n");
            log.extend_from_slice(&output.stdout);
            log.extend_from_slice(b"\n--- stderr ---\n");
            log.extend_from_slice(&output.stderr);
            fs::write(&log_path, log)
                .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
        };
        context.bail_if_cancelled()?;
        if output.cancelled {
            eyre::bail!("javac was cancelled by Ctrl+C. See {}", log_path.display());
        }
        if !output.status.success() {
            eyre::bail!(
                "javac failed with {}. See {}",
                output.status,
                log_path.display()
            );
        }
        {
            let _span = tracing::debug_span!(
                "project_compile_write_main_cache_state",
                state = %main_state_path.display()
            )
            .entered();
            if !context.verify_compilation_outputs {
                write_cache_state(&main_state_path, &main_fingerprint)?;
            }
        };
        context.bail_if_cancelled()?;
        tracing::info!("javac main: done in {} ms", started.elapsed().as_millis());
    }

    context.bail_if_cancelled()?;
    if context.verify_compilation_outputs {
        let patches = if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev
        {
            let _span =
                tracing::debug_span!("project_compile_patch_neogradle_debug_names").entered();
            patch_neogradle_anonymous_constructor_debug_names(context, &classes_dir)?
        } else {
            0
        };
        if !main_cache_hit || patches > 0 {
            write_project_java_cache(
                context,
                &main_state_path,
                &main_fingerprint,
                &[("classes", &classes_dir), ("resources", &resources_dir)],
            )?;
        }
    }
    {
        let classes_dir = classes_dir.as_path();
        let gametest_classes_dir = gametest_classes_dir.as_path();
        let gametest_resources_dir = gametest_resources_dir.as_path();
        let main_fingerprint = main_fingerprint.as_str();
        let (gametest_compile, gametest_resources) = rayon::join(
            || {
                let _span = tracing::debug_span!("project_compile_gametest_javac").entered();
                compile_optional_java_source_set(
                    context,
                    "gametest",
                    &classpath,
                    classes_dir,
                    gametest_classes_dir,
                    main_fingerprint,
                    None,
                )
                .wrap_err("Failed to compile gametest source set")
            },
            || {
                let _span = tracing::debug_span!("project_compile_gametest_resources").entered();
                stage_optional_resource_source_set(
                    context,
                    "gametest",
                    gametest_resources_dir,
                    &["README.md"],
                )
                .wrap_err("Failed to stage gametest resources")
            },
        );
        all_hit_cache_miss::pair_results(gametest_compile, gametest_resources)?;
    };
    context.bail_if_cancelled()?;
    {
        let classes_dir = classes_dir.as_path();
        let datagen_classes_dir = datagen_classes_dir.as_path();
        let datagen_resources_dir = datagen_resources_dir.as_path();
        let main_fingerprint = main_fingerprint.as_str();
        let (datagen_compile, datagen_resources) = rayon::join(
            || {
                let _span = tracing::debug_span!("project_compile_datagen_javac").entered();
                compile_optional_java_source_set(
                    context,
                    "datagen",
                    &classpath,
                    classes_dir,
                    datagen_classes_dir,
                    main_fingerprint,
                    None,
                )
                .wrap_err("Failed to compile datagen source set")
            },
            || {
                let _span = tracing::debug_span!("project_compile_datagen_resources").entered();
                stage_optional_resource_source_set(context, "datagen", datagen_resources_dir, &[])
                    .wrap_err("Failed to stage datagen resources")
            },
        );
        all_hit_cache_miss::pair_results(datagen_compile, datagen_resources)?;
    };
    context.bail_if_cancelled()?;
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        if !context.verify_compilation_outputs {
            let _span =
                tracing::debug_span!("project_compile_patch_neogradle_debug_names").entered();
            patch_neogradle_anonymous_constructor_debug_names(context, &classes_dir)?;
        }
    } else {
        let _span = tracing::debug_span!("project_compile_ensure_run_refmap_remap").entered();
        ensure_run_refmap_remapping_file(context)?;
    }
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!(
            "project_compile_stage_main_resources",
            output = %staged_resources_dir.display()
        )
        .entered();
        stage_project_resources(context, &staged_resources_dir, &resources_dir)?;
    };
    context.bail_if_cancelled()?;

    {
        let _span = tracing::debug_span!("project_compile_write_node_state").entered();
        context.write_node_state(
            "compile-project",
            &[
                "src/main/java",
                "src/main/antlr",
                "src/gametest/java",
                "src/datagen/java",
                "src/datagen/resources",
                "mapped Forge/Minecraft jar",
            ],
            &[
                classes_dir,
                resources_dir,
                staged_resources_dir,
                gametest_classes_dir,
                gametest_resources_dir,
                datagen_classes_dir,
                datagen_resources_dir,
                project_root.join("run-refmap-remap.srg"),
            ],
            "complete",
        )?;
    };
    Ok(())
}

fn project_java_cache_reuse_allowed(context: &ExecutionContext<'_>) -> bool {
    #[cfg(any(windows, target_os = "linux"))]
    {
        context.compiler_image_identity.is_some()
            && super::local_dependency_cache::environment_allows_reuse()
    }
    #[cfg(not(any(windows, target_os = "linux")))]
    {
        let _ = context;
        false
    }
}

fn write_project_java_cache(
    context: &ExecutionContext<'_>,
    state: &Path,
    fingerprint: &str,
    outputs: &[(&str, &Path)],
) -> eyre::Result<()> {
    context.recheck_compiler_image()?;
    if project_java_cache_reuse_allowed(context) {
        project_compilation_cache::record(
            &context.plan.cache_dir,
            state,
            fingerprint,
            outputs,
            || context.bail_if_cancelled(),
        )
    } else {
        // Do not leave an older valid marker authorizing outputs overwritten
        // by a compiler whose execution image/environment was unbound.
        project_compilation_cache::invalidate(&context.plan.cache_dir, state, || {
            context.bail_if_cancelled()
        })
    }
}

fn verified_java_cache_matches(
    context: &ExecutionContext<'_>,
    state: &Path,
    fingerprint: &str,
    outputs: &[(&str, &Path)],
) -> eyre::Result<bool> {
    context.bail_if_cancelled()?;
    context.plan.recheck_named_sdk()?;
    context.recheck_compiler_image()?;
    Ok(!context.plan.refresh
        && project_java_cache_reuse_allowed(context)
        && project_compilation_cache::matches(
            &context.plan.cache_dir,
            state,
            fingerprint,
            outputs,
            || context.bail_if_cancelled(),
        )?)
}

fn requires_forge_dependency_deobf(dependency: &DependencyPlan) -> bool {
    dependency.artifact_treatment
        == crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::LoaderManagedMod
}

fn required_main_class_output(classes_dir: &Path) -> PathBuf {
    classes_dir
        .join("ca")
        .join("teamdman")
        .join("sfm")
        .join("SFM.class")
}

fn main_compilation_cache_matches(
    context: &ExecutionContext<'_>,
    state_path: &Path,
    expected_state: &str,
    classes_dir: &Path,
    main_refmap: &Path,
) -> eyre::Result<bool> {
    let required_main_class = required_main_class_output(classes_dir);
    if context.verify_compilation_outputs {
        context.bail_if_cancelled()?;
        context.plan.recheck_named_sdk()?;
        context.recheck_compiler_image()?;
        return Ok(!context.plan.refresh
            && project_java_cache_reuse_allowed(context)
            && required_main_class.is_file()
            && (context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev
                || main_refmap.is_file())
            && project_compilation_cache::matches(
                &context.plan.cache_dir,
                state_path,
                expected_state,
                &[
                    ("classes", classes_dir),
                    ("resources", main_refmap.parent().expect("refmap parent")),
                ],
                || context.bail_if_cancelled(),
            )?);
    }
    Ok(!context.immutable_neoform_profile
        && cache_state_matches_outputs(
            context,
            state_path,
            expected_state,
            &[classes_dir],
            &[&required_main_class],
        )?
        && (context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev
            || main_refmap.is_file()))
}

fn patch_neogradle_anonymous_constructor_debug_names(
    context: &ExecutionContext<'_>,
    classes_dir: &Path,
) -> eyre::Result<usize> {
    let debug_name_patches: &[(&str, &[(&str, &str)])] = &[
        (
            "ca/teamdman/sfm/client/text_styling/ProgramSyntaxHighlightingHelper$1.class",
            &[("arg0", "tokenSource")],
        ),
        (
            "ca/teamdman/sfm/common/containermenu/ManagerContainerMenu$1.class",
            &[
                ("arg0", "container"),
                ("arg1", "slot"),
                ("arg2", "x"),
                ("arg3", "y"),
            ],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/FluidResourceType$1.class",
            &[("arg0", "size"), ("arg1", "capacity")],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/ForgeEnergyResourceType$1.class",
            &[("arg0", "capacity")],
        ),
        (
            "ca/teamdman/sfm/common/resourcetype/ItemResourceType$1.class",
            &[("arg0", "size")],
        ),
    ];

    let mut total_replacements = 0usize;
    for (class_name, replacements) in debug_name_patches {
        context.bail_if_cancelled()?;
        let class_path = zip_name_to_path(classes_dir, class_name);
        if !class_path.is_file() {
            continue;
        }
        let mapping = replacements
            .iter()
            .map(|(from, to)| ((*from).to_string(), (*to).to_string()))
            .collect::<BTreeMap<_, _>>();
        let bytes = fs::read(&class_path)
            .wrap_err_with(|| format!("Failed to read {}", class_path.display()))?;
        context.bail_if_cancelled()?;
        let (patched_bytes, replacement_count) =
            rewrite_class_srg_member_constants(&bytes, &mapping).wrap_err_with(|| {
                format!("Failed to patch debug names in {}", class_path.display())
            })?;
        context.bail_if_cancelled()?;
        if replacement_count == 0 {
            continue;
        }
        fs::write(&class_path, patched_bytes)
            .wrap_err_with(|| format!("Failed to write {}", class_path.display()))?;
        total_replacements += replacement_count;
    }

    if total_replacements > 0 {
        tracing::info!("Patched {total_replacements} NeoGradle anonymous constructor debug names");
    }
    Ok(total_replacements)
}

#[expect(
    clippy::too_many_lines,
    reason = "Optional source-set compilation keeps all javac cache, trace, and log handling together."
)]
fn compile_optional_java_source_set(
    context: &ExecutionContext<'_>,
    source_set: &str,
    base_classpath: &[PathBuf],
    main_classes_dir: &Path,
    classes_dir: &Path,
    upstream_fingerprint: &str,
    prepared_junit: Option<&junit_inputs::PreparedJunitInputs>,
) -> eyre::Result<()> {
    let _source_set_span =
        tracing::debug_span!("compile_optional_java_source_set", source_set).entered();
    context.bail_if_cancelled()?;
    context.require_all_hit_compilation_binding()?;
    let project_root = context.plan.cache_dir.join("project");
    let source_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("java");
    if context.plan.retained_inputs().is_none() && !source_root.exists() {
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
        return Ok(());
    }

    let sources = {
        let _span = tracing::debug_span!(
            "compile_optional_collect_sources",
            source_set,
            root = %source_root.display()
        )
        .entered();
        collect_source_set_java_sources(context, source_set)?
    };
    context.bail_if_cancelled()?;
    if sources.is_empty() {
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
        return Ok(());
    }

    let classpath = {
        let _span = tracing::debug_span!(
            "compile_optional_build_classpath",
            source_set,
            base_entries = base_classpath.len()
        )
        .entered();
        dedup_paths_preserve_order(
            std::iter::once(main_classes_dir.to_path_buf())
                .chain(base_classpath.iter().cloned())
                .collect(),
        )
    };
    let argfile = project_root.join(format!("javac-{source_set}.args"));
    {
        let _span = tracing::debug_span!(
            "compile_optional_write_argfile",
            source_set,
            argfile = %argfile.display(),
            sources = sources.len(),
            classpath = classpath.len()
        )
        .entered();
        write_javac_no_ap_argfile(context, &argfile, &classpath, &sources, classes_dir)?;
    };
    context.bail_if_cancelled()?;

    let started = Instant::now();
    tracing::info!(
        "javac {source_set}: start sources={} argfile={}",
        sources.len(),
        argfile.display()
    );
    let mut fingerprint_paths = sources.clone();
    fingerprint_paths.push(compilation_argfile_fingerprint_path(context, &argfile));
    if let Some(source_excludes) = source_exclusion_fingerprint_path(context, source_set)? {
        fingerprint_paths.push(source_excludes);
    }
    context.bail_if_cancelled()?;
    let fingerprint = {
        let _span = tracing::debug_span!(
            "compile_optional_fingerprint",
            source_set,
            inputs = fingerprint_paths.len()
        )
        .entered();
        input_fingerprint(
            context,
            &format!("javac-{source_set}"),
            &fingerprint_paths,
            &[
                context.plan.java.cache_identity(),
                context.plan.java_release.to_string(),
                source_set.to_string(),
                upstream_fingerprint.to_string(),
            ],
        )?
    };
    context.bail_if_cancelled()?;
    let state_path = project_root.join(format!("javac-{source_set}.inputs.sha1"));
    let cache_hit = {
        let _span = tracing::debug_span!(
            "compile_optional_check_cache",
            source_set,
            state = %state_path.display(),
            output = %classes_dir.display()
        )
        .entered();
        if context.verify_compilation_outputs {
            verified_java_cache_matches(
                context,
                &state_path,
                &fingerprint,
                &[("classes", classes_dir)],
            )?
        } else {
            !context.immutable_neoform_profile
                && cache_state_matches(context, &state_path, &fingerprint, &[classes_dir])?
        }
    };
    if cache_hit {
        if let Some(prepared) = prepared_junit {
            prepared.recheck(context.plan, &context.cancellation_token)?;
        }
        tracing::info!(
            "javac {source_set}: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    context.bail_if_cancelled()?;
    context.plan.require_strict("optional javac cache miss")?;
    {
        let _span = tracing::debug_span!(
            "compile_optional_reset_classes",
            source_set,
            output = %classes_dir.display()
        )
        .entered();
        reset_cache_directory(&context.plan.cache_dir, classes_dir)?;
    };
    context.bail_if_cancelled()?;
    let mut command = Command::new(javac_executable(&context.plan.java));
    command.arg(format!("@{}", argfile.display()));
    let source = format!("javac-{source_set}");
    context.bail_if_cancelled()?;
    let output = {
        let _span = tracing::debug_span!(
            "compile_optional_run_javac",
            source_set,
            sources = sources.len(),
            argfile = %argfile.display()
        )
        .entered();
        context.plan.recheck_named_inputs()?;
        context.plan.recheck_named_sdk()?;
        let mut launch = || {
            context.recheck_compiler_image()?;
            run_command_capture_output(&context.cancellation_token, &mut command, &source)
                .wrap_err_with(|| format!("Failed to run javac for {source_set}"))
        };
        if let Some(prepared) = prepared_junit {
            prepared.with_rechecked(context.plan, &context.cancellation_token, launch)?
        } else {
            launch()?
        }
    };
    context.bail_if_cancelled()?;
    {
        let _span =
            tracing::debug_span!("compile_optional_trace_javac_output", source_set).entered();
        trace_subprocess_bytes(context.plan, "java-tool", &source, "stdout", &output.stdout);
        trace_subprocess_bytes(context.plan, "java-tool", &source, "stderr", &output.stderr);
    };
    context.bail_if_cancelled()?;
    let log_path = project_root.join(format!("javac-{source_set}.log"));
    {
        let _span = tracing::debug_span!(
            "compile_optional_write_javac_log",
            source_set,
            log = %log_path.display()
        )
        .entered();
        let mut log = Vec::new();
        log.extend_from_slice(b"--- stdout ---\n");
        log.extend_from_slice(&output.stdout);
        log.extend_from_slice(b"\n--- stderr ---\n");
        log.extend_from_slice(&output.stderr);
        fs::write(&log_path, log)
            .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    };
    context.bail_if_cancelled()?;
    if output.cancelled {
        eyre::bail!(
            "javac {source_set} was cancelled by Ctrl+C. See {}",
            log_path.display()
        );
    }
    if !output.status.success() {
        eyre::bail!(
            "javac {source_set} failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    {
        let _span = tracing::debug_span!(
            "compile_optional_write_cache_state",
            source_set,
            state = %state_path.display()
        )
        .entered();
        if context.verify_compilation_outputs {
            write_project_java_cache(
                context,
                &state_path,
                &fingerprint,
                &[("classes", classes_dir)],
            )?;
        } else {
            write_cache_state(&state_path, &fingerprint)?;
        }
    };
    context.bail_if_cancelled()?;
    tracing::info!(
        "javac {source_set}: done in {} ms",
        started.elapsed().as_millis()
    );
    Ok(())
}

fn stage_optional_resource_source_set(
    context: &ExecutionContext<'_>,
    source_set: &str,
    output: &Path,
    excludes: &[&str],
) -> eyre::Result<()> {
    let _source_set_span = tracing::debug_span!(
        "stage_optional_resource_source_set",
        source_set,
        output = %output.display()
    )
    .entered();
    context.bail_if_cancelled()?;
    if let Some(inputs) = context.plan.retained_inputs() {
        inputs.recheck()?;
    }
    {
        let _span = tracing::debug_span!("stage_optional_resources_reset_output").entered();
        reset_cache_directory(&context.plan.cache_dir, output)?;
    };
    context.bail_if_cancelled()?;
    let root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("resources");
    let retained = context
        .plan
        .retained_inputs()
        .map(|inputs| inputs.entries_under(&root))
        .transpose()?
        .flatten();
    if retained.is_none() && !root.exists() {
        return Ok(());
    }
    if retained.is_none() {
        context.assert_allowed_input(&root)?;
    }

    let files = if let Some(entries) = &retained {
        entries.iter().map(|(path, _)| path.clone()).collect()
    } else {
        let _span = tracing::debug_span!(
            "stage_optional_resources_collect_files",
            root = %root.display()
        )
        .entered();
        collect_files_under_cancellable(context, &root)?
    };
    for path in files {
        context.bail_if_cancelled()?;
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(&root, &path)?;
        if excludes.iter().any(|exclude| *exclude == name) {
            continue;
        }
        let output_path = zip_name_to_path(output, &name);
        if let Some(parent) = output_path.parent() {
            fs::create_dir_all(parent)?;
        }
        let bytes = if let Some(entries) = &retained {
            entries
                .iter()
                .find(|(selected, _)| selected == &path)
                .ok_or_else(|| eyre::eyre!("retained resource membership changed"))?
                .1
                .to_vec()
        } else {
            fs::read(&path)?
        };
        fs::write(&output_path, bytes).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                path.display(),
                output_path.display()
            )
        })?;
        context.bail_if_cancelled()?;
    }
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "packaging executor keeps orchestration visible while toolchain is incomplete"
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        mc = %context.plan.minecraft_version,
        output = %context.plan.rust_output_jar.display(),
    )
)]
fn execute_package_and_reobfuscate(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let mut fresh_output = prepare_fresh_jar_output(context.plan)?;
    let named_admission = context.prepare_named_package_admission()?;
    if context.plan.rust_output_jar == context.plan.gradle_output_jar {
        eyre::bail!(
            "Refusing to write Rust jar over Gradle jar: {}",
            context.plan.rust_output_jar.display()
        );
    }

    let project_root = context.plan.cache_dir.join("project");
    let classes_dir = project_root.join("classes");
    let javac_resources_dir = project_root.join("resources");
    let staged_resources_dir = project_root.join("staged-resources");
    let ProjectPackageInputPaths {
        development_jar,
        reobf_mapping,
        mixin_reobf_mapping,
    } = project_package_input_paths(
        &context.plan.cache_dir,
        context.plan.minecraft_version.as_str(),
    );
    tracing::info!(
        development_jar = %development_jar.display(),
        staged_resources_dir = %staged_resources_dir.display(),
        reobf_mapping = %reobf_mapping.display(),
        "package_inputs_resolved"
    );

    if !classes_dir.is_dir() {
        eyre::bail!(
            "Project classes directory is missing: {}",
            classes_dir.display()
        );
    }
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev
        && !javac_resources_dir.join("sfm.refmap.json").is_file()
    {
        eyre::bail!(
            "Mixin annotation processor did not produce {}",
            javac_resources_dir.join("sfm.refmap.json").display()
        );
    }
    stage_project_resources(context, &staged_resources_dir, &javac_resources_dir)?;
    let mut package_fingerprint_paths = vec![classes_dir.clone(), staged_resources_dir.clone()];
    package_fingerprint_paths.extend(
        context
            .plan
            .dependencies
            .iter()
            .filter(|dependency| dependency.configuration == "jarJar")
            .map(|dependency| dependency.cache_path.clone()),
    );
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        package_fingerprint_paths.push(mixin_reobf_mapping.clone());
        package_fingerprint_paths.push(reobf_mapping.clone());
    }
    let package_fingerprint = input_fingerprint(
        context,
        "package-and-reobfuscate-jar",
        &package_fingerprint_paths,
        &package_fingerprint_extras(context),
    )?;
    let package_state_path = project_root.join("package-and-reobfuscate.inputs.sha1");
    let started = Instant::now();
    tracing::info!(
        "package-and-reobfuscate-jar: start output={}",
        context.plan.rust_output_jar.display()
    );
    if package_cache_matches(context, &package_state_path, &package_fingerprint)? {
        tracing::info!(
            "package-and-reobfuscate-jar: reused cached Rust jar in {} ms",
            started.elapsed().as_millis()
        );
        context.write_node_state(
            "package-and-reobfuscate-jar",
            &[
                "compiled classes",
                "expanded resources",
                "reobfuscation mappings",
            ],
            std::slice::from_ref(&context.plan.rust_output_jar),
            "cached",
        )?;
        return Ok(());
    }

    context.recheck_named_package_admission(named_admission.as_ref())?;
    write_project_development_jar(
        context,
        &classes_dir,
        &staged_resources_dir,
        &development_jar,
    )?;

    if let Some(parent) = context.plan.rust_output_jar.parent() {
        fs::create_dir_all(parent)?;
    }
    if named_package_previous_output_removal_allowed(context.plan.named_project().is_some())
        && fresh_output.is_none()
        && context.plan.rust_output_jar.exists()
    {
        fs::remove_file(&context.plan.rust_output_jar).wrap_err_with(|| {
            format!(
                "Failed to remove previous Rust jar {}",
                context.plan.rust_output_jar.display()
            )
        })?;
    }
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        if let Some(output) = &mut fresh_output {
            context.recheck_named_package_admission(named_admission.as_ref())?;
            output.publish_copy(&development_jar, &context.cancellation_token)?;
        } else if context.plan.named_project().is_some() {
            context.recheck_named_package_admission(named_admission.as_ref())?;
            copy_named_neoform_package(&development_jar, &context.plan.rust_output_jar)?;
        } else {
            fs::copy(&development_jar, &context.plan.rust_output_jar).wrap_err_with(|| {
                format!(
                    "Failed to copy {} to {}",
                    development_jar.display(),
                    context.plan.rust_output_jar.display()
                )
            })?;
        }
        context.write_node_state(
            "package-and-reobfuscate-jar",
            &["compiled classes", "expanded resources"],
            &[
                staged_resources_dir,
                development_jar,
                context.plan.rust_output_jar.clone(),
            ],
            "complete",
        )?;
        write_package_cache_state(context, &package_state_path, &package_fingerprint)?;
        tracing::info!(
            "package-and-reobfuscate-jar: done in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    if !reobf_mapping.is_file() {
        eyre::bail!(
            "Reobfuscation mapping is missing: {}",
            reobf_mapping.display()
        );
    }
    if !mixin_reobf_mapping.is_file() {
        eyre::bail!(
            "Mixin reobfuscation mapping is missing: {}",
            mixin_reobf_mapping.display()
        );
    }

    let resolver = context.plan.resolver(&context.cancellation_token)?;
    context.recheck_named_package_admission(named_admission.as_ref())?;
    let reobf_classpath = context.package_reobfuscation_classpath(&resolver)?;
    let private_output_directory = fresh_output
        .as_mut()
        .map(|output| output.private_output_directory(&project_root))
        .transpose()?;
    let writer_output =
        project_package_writer_output(context.plan, fresh_output.as_ref())?.to_path_buf();
    context.run_project_package_specialsource(
        &reobf_classpath,
        &[
            "--in-jar".to_string(),
            development_jar.display().to_string(),
            "--out-jar".to_string(),
            writer_output.display().to_string(),
            "--srg-in".to_string(),
            reobf_mapping.display().to_string(),
            "--srg-in".to_string(),
            mixin_reobf_mapping.display().to_string(),
            "--live".to_string(),
        ],
        &project_root.join("reobf"),
        fresh_output.as_ref(),
    )?;
    if !writer_output.is_file() {
        eyre::bail!(
            "SpecialSource completed without producing Rust jar {}",
            writer_output.display()
        );
    }
    if let Some(output) = &mut fresh_output {
        output.publish_copy(&writer_output, &context.cancellation_token)?;
    }
    drop(private_output_directory);

    write_package_cache_state(context, &package_state_path, &package_fingerprint)?;
    tracing::info!(
        "package-and-reobfuscate-jar: done in {} ms",
        started.elapsed().as_millis()
    );
    context.write_node_state(
        "package-and-reobfuscate-jar",
        &[
            "compiled classes",
            "expanded resources",
            "reobfuscation mappings",
        ],
        &[
            staged_resources_dir,
            development_jar,
            context.plan.rust_output_jar.clone(),
        ],
        "complete",
    )
}

fn package_cache_matches(
    context: &ExecutionContext<'_>,
    state: &Path,
    fingerprint: &str,
) -> eyre::Result<bool> {
    if context.plan.output_name.is_some() {
        context.bail_if_cancelled()?;
        return Ok(false);
    }
    Ok(
        named_package_cache_reuse_allowed(context.plan.named_project().is_some())
            && cache_state_matches_outputs(
                context,
                state,
                fingerprint,
                &[],
                &[&context.plan.rust_output_jar],
            )?,
    )
}

fn write_package_cache_state(
    context: &ExecutionContext<'_>,
    state: &Path,
    fingerprint: &str,
) -> eyre::Result<()> {
    // The legacy marker does not bind a final-output path. An alternate output
    // must never make an older default final qualify by existence alone.
    if context.plan.output_name.is_none() {
        write_cache_state(state, fingerprint)?;
    }
    Ok(())
}

fn package_fingerprint_extras(context: &ExecutionContext<'_>) -> Vec<String> {
    let mut extras = vec![
        "package-and-reobfuscate-v1".to_string(),
        format!("{:?}", context.plan.loader_toolchain.kind),
        format!("exclude:{CLIENT_SMOKE_RUN_HARNESS_CLASS}"),
        context
            .plan
            .worktree_path
            .as_deref()
            .and_then(Path::file_name)
            .and_then(|name| name.to_str())
            .unwrap_or("sfm")
            .to_string(),
    ];
    extras.extend(
        context
            .plan
            .properties
            .iter()
            .map(|(key, value)| format!("property:{key}={value}")),
    );
    extras.extend(context.plan.artifacts.iter().map(|artifact| {
        format!(
            "artifact:{}:{:?}:{:?}:{:?}",
            artifact.id, artifact.coordinate, artifact.url, artifact.sha1
        )
    }));
    extras.extend(context.plan.dependencies.iter().map(|dependency| {
        format!(
            "dependency:{}:{}:{}:{:?}",
            dependency.configuration,
            dependency.notation,
            dependency.resolved_notation,
            dependency.bundle
        )
    }));
    extras
}

fn stage_project_resources(
    context: &ExecutionContext<'_>,
    staging_dir: &Path,
    javac_resources_dir: &Path,
) -> eyre::Result<()> {
    let _stage_span = tracing::debug_span!(
        "stage_project_resources",
        output = %staging_dir.display(),
        javac_resources = %javac_resources_dir.display()
    )
    .entered();
    context.bail_if_cancelled()?;
    if let Some(inputs) = context.plan.retained_inputs() {
        inputs.recheck()?;
    }
    let release_policy = checked_catalog_release_packaging_policy(context.plan)?;
    {
        let _span = tracing::debug_span!("stage_project_resources_reset_output").entered();
        reset_cache_directory(&context.plan.cache_dir, staging_dir)?;
    };
    let mut written = BTreeSet::new();
    for root in [
        context
            .plan
            .minecraft_dir
            .join("src")
            .join("main")
            .join("resources"),
        context
            .plan
            .minecraft_dir
            .join("src")
            .join("generated")
            .join("resources"),
        javac_resources_dir.to_path_buf(),
    ] {
        context.bail_if_cancelled()?;
        let _span = tracing::debug_span!(
            "stage_project_resource_root",
            root = %root.display(),
            output = %staging_dir.display()
        )
        .entered();
        stage_resource_root(context, &root, staging_dir, &mut written)?;
    }
    if stage_synthesized_antlr_grammar_resources(release_policy) {
        stage_antlr_grammar_resources(context, staging_dir, &mut written)?;
    }
    Ok(())
}

fn stage_antlr_grammar_resources(
    context: &ExecutionContext<'_>,
    staging_dir: &Path,
    written: &mut BTreeSet<String>,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    if let Some(inputs) = context.plan.retained_inputs() {
        inputs.recheck()?;
    }
    let root = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("antlr");
    let retained = context
        .plan
        .retained_inputs()
        .map(|inputs| inputs.entries_under(&root))
        .transpose()?
        .flatten();
    if retained.is_none() && !root.exists() {
        return Ok(());
    }
    if retained.is_none() {
        context.assert_allowed_input(&root)?;
    }

    let files = if let Some(entries) = &retained {
        entries.iter().map(|(path, _)| path.clone()).collect()
    } else {
        let _span = tracing::debug_span!(
            "stage_antlr_grammar_resources_collect_files",
            root = %root.display()
        )
        .entered();
        collect_files_under_cancellable(context, &root)?
    };
    for path in files {
        context.bail_if_cancelled()?;
        if path.extension().and_then(|ext| ext.to_str()) != Some("g4") {
            continue;
        }
        context.assert_allowed_input(&path)?;
        let relative_name = relative_zip_name(&root, &path)?.to_ascii_lowercase();
        let name = format!("assets/sfm/grammar/{relative_name}");
        if !written.insert(name.clone()) {
            continue;
        }
        let output = zip_name_to_path(staging_dir, &name);
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)?;
        }
        let bytes = if let Some(entries) = &retained {
            entries
                .iter()
                .find(|(selected, _)| selected == &path)
                .ok_or_else(|| eyre::eyre!("retained grammar membership changed"))?
                .1
                .to_vec()
        } else {
            fs::read(&path)?
        };
        fs::write(&output, bytes).wrap_err_with(|| {
            format!(
                "Failed to stage grammar resource {} to {}",
                path.display(),
                output.display()
            )
        })?;
    }
    Ok(())
}

fn stage_resource_root(
    context: &ExecutionContext<'_>,
    root: &Path,
    staging_dir: &Path,
    written: &mut BTreeSet<String>,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    if let Some(inputs) = context.plan.retained_inputs() {
        inputs.recheck()?;
    }
    let retained = context
        .plan
        .retained_inputs()
        .map(|inputs| inputs.entries_under(root))
        .transpose()?
        .flatten();
    if retained.is_none() && !root.exists() {
        return Ok(());
    }
    if retained.is_none() {
        context.assert_allowed_input(root)?;
    }

    let files = if let Some(entries) = &retained {
        entries.iter().map(|(path, _)| path.clone()).collect()
    } else {
        let _span = tracing::debug_span!(
            "stage_resource_root_collect_files",
            root = %root.display()
        )
        .entered();
        collect_files_under_cancellable(context, root)?
    };
    for path in files {
        context.bail_if_cancelled()?;
        if path
            .components()
            .any(|component| component.as_os_str() == ".cache")
        {
            continue;
        }
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(root, &path)?;
        if !written.insert(name.clone()) {
            continue;
        }

        let raw = if let Some(entries) = &retained {
            entries
                .iter()
                .find(|(selected, _)| selected == &path)
                .ok_or_else(|| eyre::eyre!("retained resource membership changed"))?
                .1
                .to_vec()
        } else {
            fs::read(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?
        };
        let bytes = if matches!(
            name.as_str(),
            "META-INF/mods.toml" | "META-INF/neoforge.mods.toml" | "pack.mcmeta"
        ) {
            let template_text = std::str::from_utf8(&raw)
                .wrap_err_with(|| format!("Failed to read resource {}", path.display()))?;
            expand_gradle_resource_template(template_text, &context.plan.properties)?.into_bytes()
        } else {
            raw
        };
        context.bail_if_cancelled()?;
        let output = zip_name_to_path(staging_dir, &name);
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(&output, bytes)
            .wrap_err_with(|| format!("Failed to write staged resource {}", output.display()))?;
        context.bail_if_cancelled()?;
    }

    Ok(())
}

fn write_project_development_jar(
    context: &ExecutionContext<'_>,
    classes_dir: &Path,
    resources_dir: &Path,
    output: &Path,
) -> eyre::Result<()> {
    let manifest = build_project_manifest(context)?;
    context.assert_allowed_input(classes_dir)?;
    context.assert_allowed_input(resources_dir)?;
    let mut entries = BTreeMap::new();
    add_directory_to_jar_entries(context, &mut entries, classes_dir)?;
    add_directory_to_jar_entries(context, &mut entries, resources_dir)?;
    add_loader_jarjar_entries(context, &mut entries)?;

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(manifest.as_bytes())
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    for (name, bytes) in entries {
        if name.eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
            continue;
        }
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn add_loader_jarjar_entries(
    context: &ExecutionContext<'_>,
    entries: &mut BTreeMap<String, Vec<u8>>,
) -> eyre::Result<()> {
    if !matches!(
        context.plan.loader_toolchain.kind,
        LoaderToolchainKind::ForgeGradleForge | LoaderToolchainKind::NeoGradleUserdev
    ) {
        return Ok(());
    }

    let mut metadata_entries = Vec::new();
    let mut dependencies = context
        .plan
        .dependencies
        .iter()
        .filter(|dependency| dependency.configuration == "jarJar")
        .collect::<Vec<_>>();
    dependencies.sort_by(|left, right| {
        left.resolved_notation
            .cmp(&right.resolved_notation)
            .then_with(|| left.cache_path.cmp(&right.cache_path))
    });
    for dependency in dependencies {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        let bundle = dependency.bundle.as_ref().ok_or_else(|| {
            eyre::eyre!(
                "JarJar dependency `{}` is missing its schema v3 bundle policy",
                dependency.resolved_notation
            )
        })?;
        if coordinate.version != bundle.artifact_version {
            eyre::bail!(
                "JarJar dependency `{}` resolved version does not match bundle artifact_version `{}`",
                dependency.resolved_notation,
                bundle.artifact_version
            );
        }
        context.assert_allowed_input(&dependency.cache_path)?;
        let path = format!("META-INF/jarjar/{}", coordinate.file_name());
        let bytes = fs::read(&dependency.cache_path)
            .wrap_err_with(|| format!("Failed to read {}", dependency.cache_path.display()))?;
        if entries.insert(path.clone(), bytes).is_some() {
            eyre::bail!("Duplicate JarJar nested output path `{path}`");
        }
        metadata_entries.push(JarJarMetadataEntry {
            identifier: JarJarIdentifier {
                group: coordinate.group,
                artifact: coordinate.artifact,
            },
            version: JarJarVersion {
                range: bundle.accepted_version_range.clone(),
                artifact_version: bundle.artifact_version.clone(),
            },
            path,
            is_obfuscated: bundle.is_obfuscated,
        });
    }

    if metadata_entries.is_empty() {
        return Ok(());
    }

    let metadata = JarJarMetadata {
        jars: metadata_entries,
    };
    let mut metadata_json = facet_json::to_string_pretty(&metadata)?.replace('\n', "\r\n");
    metadata_json.push_str("\r\n");
    entries.insert(
        "META-INF/jarjar/metadata.json".to_string(),
        metadata_json.into_bytes(),
    );
    Ok(())
}

fn add_directory_to_jar_entries(
    context: &ExecutionContext<'_>,
    entries: &mut BTreeMap<String, Vec<u8>>,
    root: &Path,
) -> eyre::Result<()> {
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(root)?;
    for path in collect_files_under(root)? {
        context.assert_allowed_input(&path)?;
        let name = relative_zip_name(root, &path)?;
        if !should_package_project_entry(&name) {
            continue;
        }
        if entries.contains_key(&name) {
            continue;
        }
        let bytes =
            fs::read(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        entries.insert(name, bytes);
    }
    Ok(())
}

const CLIENT_SMOKE_RUN_HARNESS_CLASS: &str =
    "ca/teamdman/sfm/client/handler/SFMClientSmokeRunHarness.class";

pub(super) fn should_package_project_entry(name: &str) -> bool {
    !matches!(
        name,
        "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"
            | CLIENT_SMOKE_RUN_HARNESS_CLASS
    )
}

fn build_project_manifest(context: &ExecutionContext<'_>) -> eyre::Result<String> {
    let properties = &context.plan.properties;
    let mod_id = properties.get("mod_id").map_or("sfm", String::as_str);
    let mod_authors = properties.get("mod_authors").map_or("", String::as_str);
    let project_name = project_manifest_implementation_title(context)?;
    let mod_version = properties.get("mod_version").map_or("", String::as_str);
    let timestamp = Local::now().format("%Y-%m-%dT%H:%M:%S%z").to_string();
    let mut manifest = String::new();
    let attributes = [
        ("Manifest-Version", "1.0"),
        ("Specification-Title", mod_id),
        ("Specification-Vendor", mod_authors),
        ("Specification-Version", "1"),
        ("Implementation-Title", project_name.as_str()),
        ("Implementation-Version", mod_version),
        ("Implementation-Vendor", mod_authors),
        ("Implementation-Timestamp", timestamp.as_str()),
    ];
    for (key, value) in attributes {
        append_manifest_attribute(&mut manifest, key, value);
    }
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        append_manifest_attribute(&mut manifest, "MixinConfigs", "sfm.mixins.json");
    }
    manifest.push_str("\r\n");
    Ok(manifest)
}

fn append_manifest_attribute(manifest: &mut String, key: &str, value: &str) {
    manifest.push_str(key);
    manifest.push_str(": ");
    manifest.push_str(value);
    manifest.push_str("\r\n");
}

fn expand_gradle_resource_template(
    content: &str,
    properties: &BTreeMap<String, String>,
) -> eyre::Result<String> {
    let mut output = String::new();
    let mut remaining = content;
    while let Some(start) = remaining.find("${") {
        output.push_str(&remaining[..start]);
        let after_start = &remaining[start + 2..];
        let Some(end) = after_start.find('}') else {
            eyre::bail!("Unclosed resource expansion placeholder in {content:?}");
        };
        let key = after_start[..end].trim();
        let value = properties
            .get(key)
            .ok_or_else(|| eyre::eyre!("Missing resource expansion property: {key}"))?;
        output.push_str(&decode_gradle_property_value(value));
        remaining = &after_start[end + 1..];
    }
    output.push_str(remaining);
    Ok(output)
}

fn decode_gradle_property_value(value: &str) -> String {
    let mut output = String::new();
    let mut chars = value.chars();
    while let Some(character) = chars.next() {
        if character != '\\' {
            output.push(character);
            continue;
        }
        match chars.next() {
            Some('n') => output.push('\n'),
            Some('r') => output.push('\r'),
            Some('t') => output.push('\t'),
            Some('\\') | None => output.push('\\'),
            Some(other) => {
                output.push('\\');
                output.push(other);
            }
        }
    }
    output
}

fn reset_cache_directory(cache_dir: &Path, path: &Path) -> eyre::Result<()> {
    // Do not simplify just one Windows path: long children retain a verbatim
    // prefix even when their short cache root can use the legacy spelling.
    let canonical_cache = canonicalize_cache_reset_path(cache_dir)?;
    let canonical_path = canonicalize_cache_reset_path(path)?;
    if !canonical_path.starts_with(&canonical_cache) {
        eyre::bail!("Refusing to reset non-cache directory {}", path.display());
    }
    if path.exists() {
        fs::remove_dir_all(path)
            .wrap_err_with(|| format!("Failed to remove {}", path.display()))?;
    }
    fs::create_dir_all(path).wrap_err_with(|| format!("Failed to create {}", path.display()))?;
    Ok(())
}

fn canonicalize_cache_reset_path(path: &Path) -> eyre::Result<PathBuf> {
    if path.exists() {
        return fs::canonicalize(path)
            .wrap_err_with(|| format!("Failed to canonicalize cache path {}", path.display()));
    }
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Cache path has no parent: {}", path.display()))?;
    let canonical_parent = canonicalize_cache_reset_path(parent)?;
    let file_name = path
        .file_name()
        .ok_or_else(|| eyre::eyre!("Cache path has no file name: {}", path.display()))?;
    Ok(canonical_parent.join(file_name))
}

fn cache_state_matches(
    context: &ExecutionContext<'_>,
    state_path: &Path,
    expected_state: &str,
    required_output_dirs: &[&Path],
) -> eyre::Result<bool> {
    cache_state_matches_outputs(
        context,
        state_path,
        expected_state,
        required_output_dirs,
        &[],
    )
}

fn cache_state_matches_outputs(
    context: &ExecutionContext<'_>,
    state_path: &Path,
    expected_state: &str,
    required_output_dirs: &[&Path],
    required_output_files: &[&Path],
) -> eyre::Result<bool> {
    context.bail_if_cancelled()?;
    context.plan.recheck_named_sdk()?;
    if context.plan.refresh {
        return Ok(false);
    }
    if fs::read_to_string(state_path).unwrap_or_default() != expected_state {
        return Ok(false);
    }
    for output_dir in required_output_dirs {
        context.bail_if_cancelled()?;
        if !directory_has_files(context, output_dir)? {
            return Ok(false);
        }
    }
    for output_file in required_output_files {
        context.bail_if_cancelled()?;
        if !output_file.is_file() {
            return Ok(false);
        }
    }
    Ok(true)
}

fn directory_has_files(context: &ExecutionContext<'_>, path: &Path) -> eyre::Result<bool> {
    context.bail_if_cancelled()?;
    Ok(path.is_dir() && !collect_files_under_cancellable(context, path)?.is_empty())
}

fn input_fingerprint(
    context: &ExecutionContext<'_>,
    label: &str,
    paths: &[PathBuf],
    extras: &[String],
) -> eyre::Result<String> {
    context.bail_if_cancelled()?;
    let mut input = Vec::new();
    input.extend_from_slice(b"sfm-input-fingerprint-v2\n");
    input.extend_from_slice(label.as_bytes());
    input.extend_from_slice(b"\n");
    if context.verify_compilation_outputs
        && (label.starts_with("javac-") || label == "antlr-main")
        && let Some(image) = &context.compiler_image_identity
    {
        input.extend_from_slice(b"compiler-image:");
        input.extend_from_slice(image.as_bytes());
        input.extend_from_slice(b"\n");
    }
    for extra in extras {
        context.bail_if_cancelled()?;
        input.extend_from_slice(b"extra:");
        input.extend_from_slice(extra.as_bytes());
        input.extend_from_slice(b"\n");
    }
    let path_inputs = {
        let _span =
            tracing::debug_span!("input_fingerprint_paths", label, paths = paths.len()).entered();
        let hashed = paths
            .par_iter()
            .map(|path| hash_path_input(context, path))
            .collect::<Vec<eyre::Result<_>>>();
        all_hit_cache_miss::collect_results(hashed)?
    };
    for path_input in path_inputs {
        input.extend_from_slice(&path_input);
    }
    Ok(ContentHash::from_bytes(&input, ContentHashAlgorithm::Blake3).to_string())
}

fn hash_path_input(context: &ExecutionContext<'_>, path: &Path) -> eyre::Result<Vec<u8>> {
    context.bail_if_cancelled()?;
    let _span = tracing::debug_span!("hash_path_input", path = %path.display()).entered();
    if let Some(inputs) = context.plan.retained_inputs()
        && let Some(input) = inputs.path_input(path)?
    {
        return Ok(input);
    }
    context.assert_allowed_input(path)?;
    let normalized = if context.verify_compilation_outputs
        && context
            .held_neoform_compile_jar
            .is_some_and(|held| project_compile_identity::is_bound(path, held))
    {
        "sfm:bound_neoform_classes@1".to_owned()
    } else {
        path.to_string_lossy().replace('\\', "/")
    };
    let mut input = Vec::new();
    input.extend_from_slice(b"path:");
    input.extend_from_slice(normalized.as_bytes());
    input.extend_from_slice(b"\n");

    if path.is_file() {
        context.bail_if_cancelled()?;
        let _span = tracing::debug_span!("hash_path_input_file").entered();
        input.extend_from_slice(b"file:");
        input.extend_from_slice(
            ContentHash::from_path(path, ContentHashAlgorithm::Blake3)?
                .to_string()
                .as_bytes(),
        );
        input.extend_from_slice(b"\n");
        return Ok(input);
    }

    if path.is_dir() {
        let files = {
            let _span = tracing::debug_span!("hash_path_input_dir_collect").entered();
            collect_files_under_cancellable(context, path)?
        };
        input.extend_from_slice(b"dir\n");
        let entry_inputs = {
            let _span =
                tracing::debug_span!("hash_path_input_dir_entries", files = files.len()).entered();
            files
                .par_iter()
                .map(|file| hash_directory_entry_input(context, path, file))
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()?
        };
        for entry_input in entry_inputs {
            input.extend_from_slice(&entry_input);
        }
        return Ok(input);
    }

    input.extend_from_slice(b"missing\n");
    Ok(input)
}

fn hash_directory_entry_input(
    context: &ExecutionContext<'_>,
    root: &Path,
    file: &Path,
) -> eyre::Result<Vec<u8>> {
    context.bail_if_cancelled()?;
    let _span =
        tracing::debug_span!("hash_directory_entry_input", file = %file.display()).entered();
    context.assert_allowed_input(file)?;
    let relative = relative_zip_name(root, file)?;
    let hash = ContentHash::from_path(file, ContentHashAlgorithm::Blake3)?;
    let mut input = Vec::new();
    input.extend_from_slice(b"entry:");
    input.extend_from_slice(relative.as_bytes());
    input.extend_from_slice(b":");
    input.extend_from_slice(hash.to_string().as_bytes());
    input.extend_from_slice(b"\n");
    Ok(input)
}

fn write_cache_state(path: &Path, state: &str) -> eyre::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, state).wrap_err_with(|| format!("Failed to write {}", path.display()))
}

fn collect_files_under(root: &Path) -> eyre::Result<Vec<PathBuf>> {
    let mut files = Vec::new();
    if !root.exists() {
        return Ok(files);
    }
    let mut entries = fs::read_dir(root)
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?
        .collect::<Result<Vec<_>, _>>()
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?;
    entries.sort_by_key(std::fs::DirEntry::path);
    for entry in entries {
        let path = entry.path();
        if path.is_dir() {
            files.extend(collect_files_under(&path)?);
        } else if path.is_file() {
            files.push(path);
        }
    }
    Ok(files)
}

fn collect_files_under_cancellable(
    context: &ExecutionContext<'_>,
    root: &Path,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut files = Vec::new();
    if !root.exists() {
        return Ok(files);
    }
    let mut entries = fs::read_dir(root)
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?
        .collect::<Result<Vec<_>, _>>()
        .wrap_err_with(|| format!("Failed to read {}", root.display()))?;
    entries.sort_by_key(std::fs::DirEntry::path);
    for entry in entries {
        context.bail_if_cancelled()?;
        let path = entry.path();
        if path.is_dir() {
            files.extend(collect_files_under_cancellable(context, &path)?);
        } else if path.is_file() {
            files.push(path);
        }
    }
    Ok(files)
}

fn zip_name_to_path(root: &Path, name: &str) -> PathBuf {
    name.split('/')
        .filter(|part| !part.is_empty())
        .fold(root.to_path_buf(), |path, part| path.join(part))
}
