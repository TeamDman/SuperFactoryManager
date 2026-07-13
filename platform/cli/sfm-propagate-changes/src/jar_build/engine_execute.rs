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

impl<'a> ExecutionContext<'a> {
    fn new(plan: &'a BuildPlan, cancellation_token: CancellationToken) -> eyre::Result<Self> {
        cancellation_token.bail_if_cancelled()?;
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
        })
    }

    fn bail_if_cancelled(&self) -> eyre::Result<()> {
        self.cancellation_token.bail_if_cancelled()
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
            if canonical.starts_with(forbidden) {
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
        fs::write(
            &java_argfile,
            java_args
                .into_iter()
                .map(escape_argfile_arg)
                .collect::<Vec<_>>()
                .join("\n"),
        )
        .wrap_err_with(|| format!("Failed to write {}", java_argfile.display()))?;
        let started = Instant::now();
        let log_path = work_dir.join("console.log");
        tracing::info!(
            "Java tool {tool_id}: start main={main_class} argfile={} log={}",
            java_argfile.display(),
            log_path.display()
        );
        let mut command = Command::new(&self.plan.java.executable);
        command
            .arg(format!("@{}", java_argfile.display()))
            .current_dir(work_dir);
        let output = run_command_capture_output(&self.cancellation_token, &mut command, tool_id)
            .wrap_err_with(|| {
                format!(
                    "Failed to run Java tool {tool_id} using {}",
                    self.plan.java.executable.display()
                )
            })?;
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
            log_path = %log_path.display(),
            "java_tool_completed"
        );
        tracing::info!("Java tool {tool_id}: done in {duration_ms} ms");
        Ok(())
    }
}

#[expect(
    clippy::too_many_lines,
    reason = "clean-slate MCP executor is being split incrementally"
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_mcp_config_joined(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let mcp_root = context
        .plan
        .cache_dir
        .join("mcp")
        .join(context.plan.minecraft_version.as_str())
        .join("joined");
    fs::create_dir_all(&mcp_root)?;
    let output = mcp_root.join("patch").join("joined-patched-sources.jar");
    if output.is_file() && !context.plan.refresh {
        tracing::info!(
            output = %output.display(),
            "mcp_config_joined cache hit"
        );
        tracing::info!(
            "Build node execute-mcp-config-joined: reusing {}",
            output.display()
        );
        context.write_node_state(
            "execute-mcp-config-joined",
            &["Minecraft client/server jars", "MCPConfig config.json"],
            &[output],
            "cached",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %output.display(),
        refresh = context.plan.refresh,
        "mcp_config_joined cache miss"
    );

    let client_jar = context.plan.minecraft_version_cache_dir.join("client.jar");
    let server_bundle = context
        .plan
        .minecraft_version_cache_dir
        .join("server-bundle.jar");
    let client_mappings = context.plan.minecraft_version_cache_dir.join("client.txt");

    download_to_path(
        &context.cancellation_token,
        &client,
        &context.plan.minecraft.client_jar_url,
        &client_jar,
    )?;
    context.bail_if_cancelled()?;
    download_to_path(
        &context.cancellation_token,
        &client,
        &context.plan.minecraft.server_jar_url,
        &server_bundle,
    )?;
    context.bail_if_cancelled()?;
    download_to_path(
        &context.cancellation_token,
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.client_mappings_url.as_deref(),
            "client",
        )?,
        &client_mappings,
    )?;
    context.bail_if_cancelled()?;

    context.assert_allowed_input(&client_jar)?;
    context.assert_allowed_input(&server_bundle)?;
    context.assert_allowed_input(&client_mappings)?;

    let mcp_config = context.artifact(ArtifactId::from("mcp-config"))?;
    context.assert_allowed_input(&mcp_config.cache_path)?;
    let data_dir = mcp_root.join("data");
    fs::create_dir_all(&data_dir)?;
    let joined_tsrg = data_dir.join("joined.tsrg");
    extract_zip_entry_to_path(&mcp_config.cache_path, "config/joined.tsrg", &joined_tsrg)?;
    context.bail_if_cancelled()?;

    let extract_server = mcp_root.join("extractServer").join("output.jar");
    context.run_java_tool(
        "tool-installer-tools-1-3",
        &[],
        &[
            "--task".to_string(),
            "bundler_extract".to_string(),
            "--input".to_string(),
            server_bundle.display().to_string(),
            "--output".to_string(),
            extract_server.display().to_string(),
            "--jar-only".to_string(),
        ],
        &mcp_root.join("extractServer"),
    )?;

    let merged_mappings = mcp_root.join("mergeMappings").join("output.tsrg");
    context.run_java_tool(
        "tool-installer-tools-1-2",
        &[],
        &[
            "--task".to_string(),
            "MERGE_MAPPING".to_string(),
            "--left".to_string(),
            joined_tsrg.display().to_string(),
            "--right".to_string(),
            client_mappings.display().to_string(),
            "--right-names".to_string(),
            "right,left".to_string(),
            "--classes".to_string(),
            "--output".to_string(),
            merged_mappings.display().to_string(),
        ],
        &mcp_root.join("mergeMappings"),
    )?;

    let mapped_classes = read_tsrg_original_classes(&joined_tsrg)?;
    let stripped_client = mcp_root.join("stripClient").join("output.jar");
    copy_filtered_jar(&client_jar, &stripped_client, &mapped_classes)?;
    let stripped_server = mcp_root.join("stripServer").join("output.jar");
    copy_filtered_jar(&extract_server, &stripped_server, &mapped_classes)?;

    let merged_jar = mcp_root.join("merge").join("output.jar");
    context.run_java_tool(
        "tool-mergetool-1-1-5",
        &[],
        &[
            "--client".to_string(),
            stripped_client.display().to_string(),
            "--server".to_string(),
            stripped_server.display().to_string(),
            "--ann".to_string(),
            context.plan.minecraft_version.to_string(),
            "--output".to_string(),
            merged_jar.display().to_string(),
            "--inject".to_string(),
            "false".to_string(),
        ],
        &mcp_root.join("merge"),
    )?;

    let libraries_file = mcp_root.join("listLibraries").join("libraries.txt");
    write_minecraft_libraries_cfg(context, &client, &libraries_file)?;

    let renamed_jar = mcp_root.join("rename").join("output.jar");
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            merged_jar.display().to_string(),
            "--output".to_string(),
            renamed_jar.display().to_string(),
            "--map".to_string(),
            merged_mappings.display().to_string(),
            "--cfg".to_string(),
            libraries_file.display().to_string(),
            "--ann-fix".to_string(),
            "--ids-fix".to_string(),
            "--src-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &mcp_root.join("rename"),
    )?;

    let decompiled_jar = mcp_root.join("decompile").join("output.jar");
    context.run_java_tool(
        "tool-forgeflower",
        &["-Xmx4G"],
        &[
            "-din=1".to_string(),
            "-rbr=1".to_string(),
            "-dgs=1".to_string(),
            "-asc=1".to_string(),
            "-rsy=1".to_string(),
            "-iec=1".to_string(),
            "-jvn=1".to_string(),
            "-isl=0".to_string(),
            "-iib=1".to_string(),
            "-bsm=1".to_string(),
            "-dcl=1".to_string(),
            "-log=TRACE".to_string(),
            "-cfg".to_string(),
            libraries_file.display().to_string(),
            renamed_jar.display().to_string(),
            decompiled_jar.display().to_string(),
        ],
        &mcp_root.join("decompile"),
    )?;

    let injected_jar = mcp_root.join("inject").join("output.jar");
    inject_mcp_sources(&mcp_config.cache_path, &decompiled_jar, &injected_jar)?;

    apply_mcp_joined_patches(
        context,
        &mcp_config.cache_path,
        &injected_jar,
        &output,
        &mcp_root,
    )?;

    context.write_node_state(
        "execute-mcp-config-joined",
        &["Minecraft client/server jars", "MCPConfig config.json"],
        &[output],
        "complete",
    )?;
    Ok(())
}

#[expect(
    clippy::too_many_lines,
    reason = "clean-slate Forge userdev executor is being split incrementally"
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_forge_userdev(context: &ExecutionContext<'_>) -> eyre::Result<()> {
    let client = Client::builder()
        .user_agent("sfm-propagate-changes/no-gradle-toolchain")
        .build()
        .wrap_err("Failed to create HTTP client")?;
    let forge_root = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str());
    fs::create_dir_all(&forge_root)?;
    let mappings_root = forge_root.join("mappings");
    let srg_to_official = mappings_root.join("srg_to_official.tsrg");
    let official_to_srg = mappings_root.join("official_to_srg.tsrg");
    let official_sources = forge_root.join("sources").join("combined-official.jar");
    let deobfuscated_sources = forge_root.join("sources").join("combined-deobfuscated.jar");
    let output = forge_root.join("classes").join("dev-compile.jar");
    if output.is_file()
        && official_sources.is_file()
        && deobfuscated_sources.is_file()
        && srg_to_official.is_file()
        && official_to_srg.is_file()
        && !context.plan.refresh
    {
        tracing::info!(
            output = %output.display(),
            "forge_userdev cache hit"
        );
        tracing::info!(
            "Build node execute-forge-userdev: reusing {}",
            output.display()
        );
        context.write_node_state(
            "execute-forge-userdev",
            &["Forge userdev", "MCPConfig joined outputs"],
            &[
                output.clone(),
                official_sources.clone(),
                deobfuscated_sources.clone(),
            ],
            "cached",
        )?;
        return Ok(());
    }
    tracing::info!(
        output = %output.display(),
        refresh = context.plan.refresh,
        "forge_userdev cache miss"
    );

    let mcp_root = context
        .plan
        .cache_dir
        .join("mcp")
        .join(context.plan.minecraft_version.as_str())
        .join("joined");
    let mcp_sources = mcp_root.join("patch").join("joined-patched-sources.jar");
    if !mcp_sources.is_file() {
        eyre::bail!(
            "Forge userdev requires MCP joined sources first: {}",
            mcp_sources.display()
        );
    }

    let userdev = context.artifact(ArtifactId::from("forge-userdev"))?;
    let forge_sources = context.artifact(ArtifactId::from("forge-sources"))?;
    let forge_universal = context.artifact(ArtifactId::from("forge-universal"))?;
    context.assert_allowed_input(&userdev.cache_path)?;
    context.assert_allowed_input(&forge_sources.cache_path)?;
    context.assert_allowed_input(&forge_universal.cache_path)?;

    let patched_minecraft_sources = forge_root.join("sourcePatches").join("minecraft-srg.jar");
    context.run_java_tool(
        "tool-diffpatch",
        &[],
        &[
            "--patch".to_string(),
            "--mode".to_string(),
            "OFFSET".to_string(),
            "--archive".to_string(),
            "ZIP".to_string(),
            "--archive-rejects".to_string(),
            "ZIP".to_string(),
            "--prefix".to_string(),
            "patches/".to_string(),
            "--output".to_string(),
            patched_minecraft_sources.display().to_string(),
            "--reject".to_string(),
            forge_root
                .join("sourcePatches")
                .join("rejects.zip")
                .display()
                .to_string(),
            mcp_sources.display().to_string(),
            userdev.cache_path.display().to_string(),
        ],
        &forge_root.join("sourcePatches"),
    )?;
    context.bail_if_cancelled()?;

    let combined_srg_sources = forge_root.join("sources").join("combined-srg.jar");
    merge_zip_archives(
        &[
            patched_minecraft_sources.clone(),
            forge_sources.cache_path.clone(),
        ],
        &combined_srg_sources,
    )?;

    let client_mappings = context.plan.minecraft_version_cache_dir.join("client.txt");
    let server_mappings = context.plan.minecraft_version_cache_dir.join("server.txt");
    download_to_path(
        &context.cancellation_token,
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.client_mappings_url.as_deref(),
            "client",
        )?,
        &client_mappings,
    )?;
    context.bail_if_cancelled()?;
    download_to_path(
        &context.cancellation_token,
        &client,
        required_minecraft_mapping_url(
            context,
            context.plan.minecraft.server_mappings_url.as_deref(),
            "server",
        )?,
        &server_mappings,
    )?;
    context.bail_if_cancelled()?;
    fs::create_dir_all(&mappings_root)?;
    let obf_to_official = mappings_root.join("obf_to_official.tsrg");
    let parchment_parameters = context
        .artifact(ArtifactId::from("parchment-data"))
        .ok()
        .map(|artifact| read_parchment_parameters(&artifact.cache_path))
        .transpose()?;
    generate_mojang_tsrg_mappings(
        &mcp_root.join("mergeMappings").join("output.tsrg"),
        &[client_mappings, server_mappings],
        parchment_parameters.as_ref(),
        &obf_to_official,
        &srg_to_official,
        &official_to_srg,
    )?;
    context.bail_if_cancelled()?;

    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            combined_srg_sources.display().to_string(),
            "--output".to_string(),
            official_sources.display().to_string(),
            "--map".to_string(),
            srg_to_official.display().to_string(),
            "--src-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &forge_root.join("remapSources"),
    )?;
    super::source_identifier_remapper::remap_source_jar_identifiers(
        &SourceJarPath::new(official_sources.clone()),
        &SourceIdentifierMappingPath::new(srg_to_official.clone()),
        &SourceJarPath::new(deobfuscated_sources.clone()),
    )?;

    let binpatches = forge_root.join("binpatch").join("joined.lzma");
    extract_zip_entry_to_path(&userdev.cache_path, "joined.lzma", &binpatches)?;
    let binpatched_minecraft = forge_root.join("classes").join("minecraft-binpatched.jar");
    context.run_java_tool(
        "forge-binarypatcher",
        &[],
        &[
            "--clean".to_string(),
            mcp_root
                .join("rename")
                .join("output.jar")
                .display()
                .to_string(),
            "--output".to_string(),
            binpatched_minecraft.display().to_string(),
            "--apply".to_string(),
            binpatches.display().to_string(),
        ],
        &forge_root.join("binpatch"),
    )?;
    if !binpatched_minecraft.is_file() {
        eyre::bail!(
            "BinaryPatcher completed without producing {}",
            binpatched_minecraft.display()
        );
    }

    let merged_output = forge_root
        .join("classes")
        .join("dev-compile-srg-untransformed.jar");
    let clean_named_minecraft = mcp_root.join("rename").join("output.jar");
    merge_zip_archives(
        &[
            binpatched_minecraft,
            clean_named_minecraft,
            forge_universal.cache_path.clone(),
        ],
        &merged_output,
    )?;

    let access_transformed = forge_root.join("classes").join("dev-compile-srg-at.jar");
    let access_transformed_patched = forge_root.join("classes").join("dev-compile-srg.jar");
    let forge_at = forge_root
        .join("accessTransform")
        .join("forge-accesstransformer.cfg");
    extract_zip_entry_to_path(&userdev.cache_path, "ats/accesstransformer.cfg", &forge_at)?;
    let project_at = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("resources")
        .join("META-INF")
        .join("accesstransformer.cfg");
    context.run_java_tool(
        "tool-access-transformers",
        &[],
        &[
            "--inJar".to_string(),
            merged_output.display().to_string(),
            "--outJar".to_string(),
            access_transformed.display().to_string(),
            "--atFile".to_string(),
            forge_at.display().to_string(),
            "--atFile".to_string(),
            project_at.display().to_string(),
        ],
        &forge_root.join("accessTransform"),
    )?;
    if !access_transformed.is_file() {
        eyre::bail!(
            "AccessTransformers completed without producing {}",
            access_transformed.display()
        );
    }
    patch_inner_class_access_in_jar(
        &access_transformed,
        &access_transformed_patched,
        "net/minecraft/client/gui/components/MultilineTextField",
        "net/minecraft/client/gui/components/MultilineTextField$StringView",
    )?;
    context.run_java_tool(
        "tool-fart",
        &[],
        &[
            "--input".to_string(),
            access_transformed_patched.display().to_string(),
            "--output".to_string(),
            output.display().to_string(),
            "--map".to_string(),
            srg_to_official.display().to_string(),
            "--ann-fix".to_string(),
            "--ids-fix".to_string(),
            "--record-fix".to_string(),
        ],
        &forge_root.join("remapDevCompile"),
    )?;
    if !output.is_file() {
        eyre::bail!(
            "FART completed without producing mapped Forge dev compile jar {}",
            output.display()
        );
    }
    context.write_node_state(
        "execute-forge-userdev",
        &["Forge userdev", "MCPConfig joined outputs"],
        &[output, official_sources],
        "complete",
    )
}

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
        Resolver::new(
            context.plan.maven_cache_dir.clone(),
            context.plan.repositories.clone(),
            context.plan.refresh,
            context.plan.allow_local_artifact_cache,
            context.plan.artifact_sources.clone(),
            context.plan.lockfile.clone(),
            context.plan.lockfile.clone(),
            context.cancellation_token.clone(),
        )?
    };
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let _span = tracing::debug_span!("dependency_deobf_copy_neogradle_jars").entered();
        copy_neogradle_dependency_jars(context, &resolver, &output)?;
        return Ok(());
    }

    let (mapping_path, mapping_hash, member_mappings) = {
        let _span = tracing::debug_span!("dependency_deobf_load_mappings").entered();
        let mapping_path = context
            .plan
            .cache_dir
            .join("forge")
            .join(context.plan.minecraft_version.as_str())
            .join("mappings")
            .join("srg_to_official.tsrg");
        if !mapping_path.is_file() {
            eyre::bail!(
                "Dependency deobf requires generated mapping first: {}",
                mapping_path.display()
            );
        }
        let mapping_hash = {
            let _span = tracing::debug_span!(
                "dependency_deobf_hash_mapping",
                mapping = %mapping_path.display()
            )
            .entered();
            ContentHash::from_path(&mapping_path, ContentHashAlgorithm::Blake3)?
        };
        let member_mappings = {
            let _span = tracing::debug_span!(
                "dependency_deobf_read_member_mappings",
                mapping = %mapping_path.display()
            )
            .entered();
            read_unique_srg_member_mappings(&mapping_path)?
        };
        (mapping_path, mapping_hash, member_mappings)
    };
    let outputs: Vec<PathBuf> = {
        let _span = tracing::debug_span!(
            "dependency_deobf_process_dependencies",
            dependency_count = context.plan.dependencies.len(),
            workers = rayon::current_num_threads()
        )
        .entered();
        let mut outputs = context
            .plan
            .dependencies
            .par_iter()
            .enumerate()
            .filter(|(_, dependency)| requires_forge_dependency_deobf(dependency))
            .map(|(dependency_index, dependency)| {
                context.bail_if_cancelled()?;
                let resolver = resolver.clone();
                execute_dependency_deobf_dependency(
                    context,
                    &resolver,
                    dependency_index,
                    dependency,
                    &output,
                    &mapping_path,
                    mapping_hash,
                    &member_mappings,
                )
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()
            .wrap_err("Failed to deobfuscate dependency")?;
        {
            let _span = tracing::debug_span!("dependency_deobf_sort_outputs").entered();
            outputs.sort_by_key(|(dependency_index, _)| *dependency_index);
        };
        outputs.into_iter().map(|(_, output)| output).collect()
    };

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
    clippy::too_many_arguments,
    clippy::too_many_lines,
    reason = "Dependency deobfuscation is orchestration-heavy and kept linear for cache/debug tracing."
)]
fn execute_dependency_deobf_dependency(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    dependency_index: usize,
    dependency: &DependencyPlan,
    output: &Path,
    mapping_path: &Path,
    mapping_hash: ContentHash,
    member_mappings: &BTreeMap<String, String>,
) -> eyre::Result<(usize, PathBuf)> {
    let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
    let _dependency_span = tracing::debug_span!(
        "dependency_deobf_dependency",
        index = dependency_index,
        configuration = %dependency.configuration,
        coordinate = %coordinate,
        source = ?dependency.source,
    )
    .entered();
    let artifact = {
        let _span = tracing::debug_span!("dependency_deobf_resolve_artifact").entered();
        resolver.resolve_artifact(
            ArtifactId::from(format!("dependency-{dependency_index}")),
            &coordinate,
            ArtifactPurpose::from(format!("{} dependency", dependency.configuration)),
        )?
    };
    {
        let _span = tracing::debug_span!(
            "dependency_deobf_validate_input",
            artifact = %artifact.cache_path.display()
        )
        .entered();
        context.assert_allowed_input(&artifact.cache_path)?;
    };
    let (remapped, specialsource_output) = {
        let _span = tracing::debug_span!(
            "dependency_deobf_compute_output_paths",
            artifact = %artifact.cache_path.display()
        )
        .entered();
        let artifact_hash = resolved_artifact_hash(&artifact)?;
        (
            remapped_dependency_output_path(output, &artifact_hash, &mapping_hash, &coordinate),
            specialsource_dependency_output_path(
                output,
                &artifact_hash,
                &mapping_hash,
                &coordinate,
            ),
        )
    };
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
            acquire_artifact_path_lock(&remapped)?
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
                    context.run_java_tool_with_classpath(
                        "tool-specialsource",
                        &[],
                        &[],
                        &[
                            "--in-jar".to_string(),
                            artifact.cache_path.display().to_string(),
                            "--out-jar".to_string(),
                            specialsource_output.display().to_string(),
                            "--srg-in".to_string(),
                            mapping_path.display().to_string(),
                            "--live".to_string(),
                        ],
                        &output
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
                    &specialsource_output,
                    &remapped,
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
    Ok((dependency_index, remapped))
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

    Ok(candidates
        .into_iter()
        .filter_map(|(srg, named)| named.map(|named| (srg, named)))
        .collect())
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
        Resolver::new(
            context.plan.maven_cache_dir.clone(),
            context.plan.repositories.clone(),
            context.plan.refresh,
            context.plan.allow_local_artifact_cache,
            context.plan.artifact_sources.clone(),
            context.plan.lockfile.clone(),
            context.plan.lockfile.clone(),
            context.cancellation_token.clone(),
        )?
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
        (classpath?, sources?)
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
    main_fingerprint_paths.push(argfile.clone());
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
                context.plan.java.version_output.clone(),
                context.plan.java_release.to_string(),
                format!("{:?}", context.plan.loader_toolchain.kind),
            ],
        )?
    };
    context.bail_if_cancelled()?;
    let main_state_path = project_root.join("javac-main.inputs.sha1");
    let main_refmap = resources_dir.join("sfm.refmap.json");
    let required_main_class = required_main_class_output(&classes_dir);
    let main_cache_hit = {
        let _span = tracing::debug_span!(
            "project_compile_check_main_cache",
            state = %main_state_path.display(),
            classes = %classes_dir.display(),
            refmap = %main_refmap.display(),
        )
        .entered();
        cache_state_matches(
            context,
            &main_state_path,
            &main_fingerprint,
            &[&classes_dir, &required_main_class],
        )? && (context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev
            || main_refmap.is_file())
    };
    context.bail_if_cancelled()?;
    if main_cache_hit {
        tracing::info!(
            "javac main: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
    } else {
        context.bail_if_cancelled()?;
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
            write_cache_state(&main_state_path, &main_fingerprint)?;
        };
        context.bail_if_cancelled()?;
        tracing::info!("javac main: done in {} ms", started.elapsed().as_millis());
    }

    context.bail_if_cancelled()?;
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
        gametest_compile?;
        gametest_resources?;
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
                )
                .wrap_err("Failed to compile datagen source set")
            },
            || {
                let _span = tracing::debug_span!("project_compile_datagen_resources").entered();
                stage_optional_resource_source_set(context, "datagen", datagen_resources_dir, &[])
                    .wrap_err("Failed to stage datagen resources")
            },
        );
        datagen_compile?;
        datagen_resources?;
    };
    context.bail_if_cancelled()?;
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let _span = tracing::debug_span!("project_compile_patch_neogradle_debug_names").entered();
        patch_neogradle_anonymous_constructor_debug_names(context, &classes_dir)?;
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

fn patch_neogradle_anonymous_constructor_debug_names(
    context: &ExecutionContext<'_>,
    classes_dir: &Path,
) -> eyre::Result<()> {
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
    Ok(())
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
) -> eyre::Result<()> {
    let _source_set_span =
        tracing::debug_span!("compile_optional_java_source_set", source_set).entered();
    context.bail_if_cancelled()?;
    let project_root = context.plan.cache_dir.join("project");
    let source_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("java");
    if !source_root.exists() {
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
    fingerprint_paths.push(argfile.clone());
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
                context.plan.java.version_output.clone(),
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
        cache_state_matches(context, &state_path, &fingerprint, &[classes_dir])?
    };
    if cache_hit {
        tracing::info!(
            "javac {source_set}: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    context.bail_if_cancelled()?;
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
        run_command_capture_output(&context.cancellation_token, &mut command, &source)
            .wrap_err_with(|| format!("Failed to run javac for {source_set}"))?
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
        write_cache_state(&state_path, &fingerprint)?;
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
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(&root)?;

    let files = {
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
        fs::copy(&path, &output_path).wrap_err_with(|| {
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
    let development_jar = project_root.join("dev.jar");
    let mixin_reobf_mapping = project_root.join("compileJava-mappings.tsrg");
    let reobf_mapping = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("official_to_srg.tsrg");
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
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        package_fingerprint_paths.extend(
            context
                .plan
                .dependencies
                .iter()
                .filter(|dependency| dependency.configuration == "jarJar")
                .map(|dependency| dependency.cache_path.clone()),
        );
    } else {
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
    if cache_state_matches_outputs(
        context,
        &package_state_path,
        &package_fingerprint,
        &[],
        &[&context.plan.rust_output_jar],
    )? {
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

    write_project_development_jar(
        context,
        &classes_dir,
        &staged_resources_dir,
        &development_jar,
    )?;

    if let Some(parent) = context.plan.rust_output_jar.parent() {
        fs::create_dir_all(parent)?;
    }
    if context.plan.rust_output_jar.exists() {
        fs::remove_file(&context.plan.rust_output_jar).wrap_err_with(|| {
            format!(
                "Failed to remove previous Rust jar {}",
                context.plan.rust_output_jar.display()
            )
        })?;
    }
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        fs::copy(&development_jar, &context.plan.rust_output_jar).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                development_jar.display(),
                context.plan.rust_output_jar.display()
            )
        })?;
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
        write_cache_state(&package_state_path, &package_fingerprint)?;
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

    let resolver = Resolver::new(
        context.plan.maven_cache_dir.clone(),
        context.plan.repositories.clone(),
        context.plan.refresh,
        context.plan.allow_local_artifact_cache,
        context.plan.artifact_sources.clone(),
        context.plan.lockfile.clone(),
        context.plan.lockfile.clone(),
        context.cancellation_token.clone(),
    )?;
    let antlr_classpath = resolve_antlr_classpath(context, &resolver)?;
    let reobf_classpath = resolve_project_compile_classpath(context, &resolver, &antlr_classpath)?;
    context.run_java_tool_with_classpath(
        "tool-specialsource",
        &[],
        &reobf_classpath,
        &[
            "--in-jar".to_string(),
            development_jar.display().to_string(),
            "--out-jar".to_string(),
            context.plan.rust_output_jar.display().to_string(),
            "--srg-in".to_string(),
            reobf_mapping.display().to_string(),
            "--srg-in".to_string(),
            mixin_reobf_mapping.display().to_string(),
            "--live".to_string(),
        ],
        &project_root.join("reobf"),
    )?;
    if !context.plan.rust_output_jar.is_file() {
        eyre::bail!(
            "SpecialSource completed without producing Rust jar {}",
            context.plan.rust_output_jar.display()
        );
    }

    write_cache_state(&package_state_path, &package_fingerprint)?;
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

fn package_fingerprint_extras(context: &ExecutionContext<'_>) -> Vec<String> {
    let mut extras = vec![
        "package-and-reobfuscate-v1".to_string(),
        format!("{:?}", context.plan.loader_toolchain.kind),
        format!("exclude:{CLIENT_SMOKE_RUN_HARNESS_CLASS}"),
        context
            .plan
            .worktree_path
            .file_name()
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
            "dependency:{}:{}:{}",
            dependency.configuration, dependency.notation, dependency.resolved_notation
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
    stage_antlr_grammar_resources(context, staging_dir, &mut written)?;
    Ok(())
}

fn stage_antlr_grammar_resources(
    context: &ExecutionContext<'_>,
    staging_dir: &Path,
    written: &mut BTreeSet<String>,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let root = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("antlr");
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(&root)?;

    let files = {
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
        fs::copy(&path, &output).wrap_err_with(|| {
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
    if !root.exists() {
        return Ok(());
    }
    context.assert_allowed_input(root)?;

    let files = {
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

        let bytes = if matches!(
            name.as_str(),
            "META-INF/mods.toml" | "META-INF/neoforge.mods.toml" | "pack.mcmeta"
        ) {
            let template_text = fs::read_to_string(&path)
                .wrap_err_with(|| format!("Failed to read resource {}", path.display()))?;
            expand_gradle_resource_template(&template_text, &context.plan.properties)?.into_bytes()
        } else {
            fs::read(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?
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
    context.assert_allowed_input(classes_dir)?;
    context.assert_allowed_input(resources_dir)?;
    let mut entries = BTreeMap::new();
    add_directory_to_jar_entries(context, &mut entries, classes_dir)?;
    add_directory_to_jar_entries(context, &mut entries, resources_dir)?;
    add_neogradle_jarjar_entries(context, &mut entries)?;

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
        .write_all(build_project_manifest(context).as_bytes())
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

fn add_neogradle_jarjar_entries(
    context: &ExecutionContext<'_>,
    entries: &mut BTreeMap<String, Vec<u8>>,
) -> eyre::Result<()> {
    if context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        return Ok(());
    }

    let mut metadata_entries = Vec::new();
    for dependency in context
        .plan
        .dependencies
        .iter()
        .filter(|dependency| dependency.configuration == "jarJar")
    {
        let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
        context.assert_allowed_input(&dependency.cache_path)?;
        let path = format!("META-INF/jarjar/{}", coordinate.file_name());
        let bytes = fs::read(&dependency.cache_path)
            .wrap_err_with(|| format!("Failed to read {}", dependency.cache_path.display()))?;
        entries.insert(path.clone(), bytes);
        metadata_entries.push(JarJarMetadataEntry {
            identifier: JarJarIdentifier {
                group: coordinate.group,
                artifact: coordinate.artifact,
            },
            version: JarJarVersion {
                range: format!("[{}]", coordinate.version),
                artifact_version: coordinate.version,
            },
            path,
            is_obfuscated: false,
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

fn build_project_manifest(context: &ExecutionContext<'_>) -> String {
    let properties = &context.plan.properties;
    let mod_id = properties.get("mod_id").map_or("sfm", String::as_str);
    let mod_authors = properties.get("mod_authors").map_or("", String::as_str);
    let project_name = context
        .plan
        .worktree_path
        .file_name()
        .and_then(|name| name.to_str())
        .map_or_else(|| "sfm".to_string(), |version| format!("sfm-{version}"));
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
    manifest
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
    let canonical_cache = canonicalize_lenient(cache_dir)?;
    let canonical_path = canonicalize_lenient(path)?;
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
    for extra in extras {
        context.bail_if_cancelled()?;
        input.extend_from_slice(b"extra:");
        input.extend_from_slice(extra.as_bytes());
        input.extend_from_slice(b"\n");
    }
    let path_inputs = {
        let _span =
            tracing::debug_span!("input_fingerprint_paths", label, paths = paths.len()).entered();
        paths
            .par_iter()
            .map(|path| hash_path_input(context, path))
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };
    for path_input in path_inputs {
        input.extend_from_slice(&path_input);
    }
    Ok(ContentHash::from_bytes(&input, ContentHashAlgorithm::Blake3).to_string())
}

fn hash_path_input(context: &ExecutionContext<'_>, path: &Path) -> eyre::Result<Vec<u8>> {
    context.bail_if_cancelled()?;
    let _span = tracing::debug_span!("hash_path_input", path = %path.display()).entered();
    context.assert_allowed_input(path)?;
    let normalized = path.to_string_lossy().replace('\\', "/");
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

