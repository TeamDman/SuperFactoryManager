#[expect(
    clippy::too_many_lines,
    reason = "clean-slate MCP executor is being split incrementally"
)]
#[tracing::instrument(
    level = "info",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn execute_mcp_config_joined_uncached(
    context: &ExecutionContext<'_>,
    reuse_project_outputs: bool,
) -> eyre::Result<()> {
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
    if reuse_project_outputs && output.is_file() && !context.plan.refresh {
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

    download_compile_child(
        context,
        &client,
        authenticated_minecraft_inputs::MinecraftCompileDownload::ClientJar,
        &client_jar,
    )?;
    context.bail_if_cancelled()?;
    download_compile_child(
        context,
        &client,
        authenticated_minecraft_inputs::MinecraftCompileDownload::ServerJar,
        &server_bundle,
    )?;
    context.bail_if_cancelled()?;
    download_compile_child(
        context,
        &client,
        authenticated_minecraft_inputs::MinecraftCompileDownload::ClientMappings,
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
fn execute_forge_userdev_uncached(
    context: &ExecutionContext<'_>,
    reuse_project_outputs: bool,
    project_at_override: Option<&Path>,
) -> eyre::Result<()> {
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
    if reuse_project_outputs
        && output.is_file()
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
    download_compile_child(
        context,
        &client,
        authenticated_minecraft_inputs::MinecraftCompileDownload::ClientMappings,
        &client_mappings,
    )?;
    context.bail_if_cancelled()?;
    download_compile_child(
        context,
        &client,
        authenticated_minecraft_inputs::MinecraftCompileDownload::ServerMappings,
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
    let project_at = project_at_override.unwrap_or(&project_at);
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
