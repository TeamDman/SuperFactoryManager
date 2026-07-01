fn write_source_outputs(plans: &[BuildPlan], layout: SourceOutputLayout) -> eyre::Result<()> {
    for plan in plans {
        let source_jar = transformed_source_output_jar(plan)?;
        let output = match layout {
            SourceOutputLayout::Jar => source_jar.into_path_buf(),
            SourceOutputLayout::Filetree => super::source_output_filetree::materialize(
                &SourceOutputCacheRoot::new(plan.cache_dir.clone()),
                &source_jar,
            )?
            .into_path_buf(),
        };
        stdout_line(output.display())?;
    }
    Ok(())
}

fn transformed_source_output_jar(plan: &BuildPlan) -> eyre::Result<SourceJarPath> {
    let path = match plan.loader_toolchain.kind {
        LoaderToolchainKind::NeoGradleUserdev => plan
            .cache_dir
            .join("neoform")
            .join(plan.minecraft_version.as_str())
            .join("classes")
            .join("gameSourcesWithNeoForge.jar"),
        LoaderToolchainKind::ForgeGradleForge | LoaderToolchainKind::ForgeGradleNeoForgeGroup => {
            plan.cache_dir
                .join("forge")
                .join(plan.minecraft_version.as_str())
                .join("sources")
                .join("combined-deobfuscated.jar")
        }
    };
    if !path.is_file() {
        eyre::bail!(
            "Transformed source jar was not produced for {}: {}",
            plan.branch_name,
            path.display()
        );
    }
    Ok(SourceJarPath::new(path))
}

fn zip_entry_has_extension(name: &str, extension: &str) -> bool {
    Path::new(name)
        .extension()
        .is_some_and(|actual| actual.eq_ignore_ascii_case(extension))
}

#[expect(
    clippy::needless_pass_by_value,
    reason = "The purpose is cloned once per coordinate while call sites pass freshly built labels."
)]
fn resolve_coordinates_for_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    coordinates: &[&str],
    required_for: ArtifactPurpose,
) -> eyre::Result<Vec<PathBuf>> {
    let mut artifacts = Vec::new();
    for (index, coordinate) in coordinates.iter().copied().enumerate() {
        context.bail_if_cancelled()?;
        let coordinate = MavenCoordinate::parse(coordinate)?;
        artifacts.push((
            ArtifactId::from(format!("classpath-{index}")),
            coordinate,
            required_for.clone(),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_coordinates_for_classpath",
        coordinates = artifacts.len(),
        required_for = %required_for,
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn resolve_antlr_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(context.plan.minecraft_version.as_str())
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    context.bail_if_cancelled()?;
    let antlr_version = dependencies
        .iter()
        .find(|dependency| dependency.configuration == "antlr")
        .map_or("4.9.1", |dependency| dependency.coordinate.version.as_str());
    let coordinates = antlr_classpath_coordinates(antlr_version)?;
    let coordinate_refs = coordinates.iter().map(String::as_str).collect::<Vec<_>>();
    resolve_coordinates_for_classpath(
        context,
        resolver,
        &coordinate_refs,
        ArtifactPurpose::from("ANTLR grammar generation"),
    )
}

fn antlr_classpath_coordinates(version: &str) -> eyre::Result<Vec<String>> {
    match version {
        "4.9.1" => Ok(vec![
            "org.antlr:antlr4:4.9.1".to_string(),
            "org.antlr:antlr-runtime:3.5.2".to_string(),
            "org.antlr:antlr4-runtime:4.9.1".to_string(),
            "org.antlr:ST4:4.3".to_string(),
            "org.abego.treelayout:org.abego.treelayout.core:1.0.3".to_string(),
            "org.glassfish:javax.json:1.0.4".to_string(),
        ]),
        "4.13.1" => Ok(vec![
            "org.antlr:antlr4:4.13.1".to_string(),
            "org.antlr:antlr4-runtime:4.13.1".to_string(),
            "org.antlr:antlr-runtime:3.5.3".to_string(),
            "org.antlr:ST4:4.3.4".to_string(),
            "org.abego.treelayout:org.abego.treelayout.core:1.0.3".to_string(),
            "com.ibm.icu:icu4j:72.1".to_string(),
        ]),
        _ => eyre::bail!("Unsupported ANTLR tool version: {version}"),
    }
}

fn resolve_project_compile_classpath(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
    antlr_classpath: &[PathBuf],
) -> eyre::Result<Vec<PathBuf>> {
    let _classpath_span = tracing::debug_span!("resolve_project_compile_classpath").entered();
    context.bail_if_cancelled()?;
    let mut classpath = Vec::new();
    {
        let _span = tracing::debug_span!("resolve_project_compile_loader_jar").entered();
        classpath.push(loader_dev_compile_jar(context));
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("resolve_project_compile_minecraft_libraries").entered();
        classpath.extend(resolve_current_minecraft_libraries(
            context,
            &resolver.client,
        )?);
    };
    context.bail_if_cancelled()?;
    {
        let _span =
            tracing::debug_span!("resolve_project_compile_forge_userdev_libraries").entered();
        classpath.extend(resolve_forge_userdev_libraries(context, resolver)?);
    };
    context.bail_if_cancelled()?;
    {
        let _span = tracing::debug_span!("resolve_project_compile_declared_dependencies").entered();
        classpath.extend(resolve_compile_dependencies(context, resolver)?);
    };
    context.bail_if_cancelled()?;
    {
        let dependency_deobf_dir = context.plan.cache_dir.join("dependencies");
        let _span = tracing::debug_span!(
            "resolve_project_compile_deobf_dependency_jars",
            root = %dependency_deobf_dir.display()
        )
        .entered();
        classpath.extend(collect_jars(context, &dependency_deobf_dir)?);
    };
    context.bail_if_cancelled()?;
    let annotation_coordinates = PROJECT_COMPILE_ANNOTATION_COORDINATES
        .iter()
        .map(|(_, coordinate)| *coordinate)
        .collect::<Vec<_>>();
    {
        let _span = tracing::debug_span!("resolve_project_compile_annotations").entered();
        classpath.extend(resolve_coordinates_for_classpath(
            context,
            resolver,
            &annotation_coordinates,
            ArtifactPurpose::from("Project compile annotations"),
        )?);
    };
    context.bail_if_cancelled()?;
    {
        let _span =
            tracing::debug_span!("resolve_project_compile_append_antlr_classpath").entered();
        classpath.extend(antlr_classpath.iter().cloned());
    };
    let classpath = {
        let _span = tracing::debug_span!(
            "resolve_project_compile_dedup_classpath",
            entries = classpath.len()
        )
        .entered();
        dedup_paths_preserve_order(classpath)
    };
    Ok(classpath)
}

fn dedup_paths_preserve_order(paths: Vec<PathBuf>) -> Vec<PathBuf> {
    let mut seen = BTreeSet::new();
    let mut deduped = Vec::new();
    for path in paths {
        let key = path
            .to_string_lossy()
            .replace('\\', "/")
            .to_ascii_lowercase();
        if seen.insert(key) {
            deduped.push(path);
        }
    }
    deduped
}

fn safe_path_segment(value: &str) -> String {
    value
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() || matches!(ch, '.' | '-' | '_') {
                ch
            } else {
                '_'
            }
        })
        .collect()
}

fn run_antlr(
    context: &ExecutionContext<'_>,
    classpath: &[PathBuf],
    output_dir: &Path,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let grammar_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join("main")
        .join("antlr");
    let grammars = [
        grammar_root.join("sfml").join("SFML.g4"),
        grammar_root.join("toml").join("TomlLexer.g4"),
        grammar_root.join("toml").join("TomlParser.g4"),
    ];
    for grammar in &grammars {
        context.bail_if_cancelled()?;
        context.assert_allowed_input(grammar)?;
    }

    let mut fingerprint_paths = classpath.to_vec();
    fingerprint_paths.extend(grammars.iter().cloned());
    context.bail_if_cancelled()?;
    let fingerprint = input_fingerprint(
        context,
        "antlr-main",
        &fingerprint_paths,
        &[
            context.plan.java.version_output.clone(),
            "-visitor -Xexact-output-dir".to_string(),
        ],
    )?;
    context.bail_if_cancelled()?;
    let state_path = output_dir.with_extension("inputs.sha1");
    let started = Instant::now();
    tracing::info!(
        "ANTLR main: start grammars={} output={}",
        grammars.len(),
        output_dir.display()
    );
    if cache_state_matches(context, &state_path, &fingerprint, &[output_dir])? {
        tracing::info!(
            "ANTLR main: reused cached outputs in {} ms",
            started.elapsed().as_millis()
        );
        return Ok(());
    }

    context.bail_if_cancelled()?;
    reset_cache_directory(&context.plan.cache_dir, output_dir)?;
    context.bail_if_cancelled()?;
    let mut command = Command::new(&context.plan.java.executable);
    command
        .arg("-cp")
        .arg(join_classpath(classpath))
        .arg("org.antlr.v4.Tool")
        .arg("-visitor")
        .arg("-Xexact-output-dir")
        .arg("-o")
        .arg(output_dir)
        .args(grammars);
    context.bail_if_cancelled()?;
    let output = run_command_capture_output(&context.cancellation_token, &mut command, "antlr")
        .wrap_err("Failed to run ANTLR")?;
    context.bail_if_cancelled()?;
    trace_subprocess_bytes(context.plan, "java-tool", "antlr", "stdout", &output.stdout);
    trace_subprocess_bytes(context.plan, "java-tool", "antlr", "stderr", &output.stderr);
    context.bail_if_cancelled()?;
    let log_path = output_dir.parent().unwrap_or(output_dir).join("antlr.log");
    let mut log = Vec::new();
    log.extend_from_slice(b"--- stdout ---\n");
    log.extend_from_slice(&output.stdout);
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&output.stderr);
    fs::write(&log_path, log)
        .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    context.bail_if_cancelled()?;
    if output.cancelled {
        eyre::bail!("ANTLR was cancelled by Ctrl+C. See {}", log_path.display());
    }
    if !output.status.success() {
        eyre::bail!(
            "ANTLR failed with {}. See {}",
            output.status,
            log_path.display()
        );
    }
    write_cache_state(&state_path, &fingerprint)?;
    context.bail_if_cancelled()?;
    tracing::info!("ANTLR main: done in {} ms", started.elapsed().as_millis());
    Ok(())
}

fn resolve_forge_userdev_libraries(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let config: ForgeUserdevConfig = {
        let _span = tracing::debug_span!("resolve_forge_userdev_libraries_read_config").entered();
        read_zip_json_entry(
            &context
                .artifact(ArtifactId::from("forge-userdev"))?
                .cache_path,
            "config.json",
        )?
    };
    context.bail_if_cancelled()?;
    let mut coordinates = Vec::new();
    coordinates.extend(config.libraries);
    coordinates.extend(config.modules);
    coordinates.sort();
    coordinates.dedup();

    let mut artifacts = Vec::new();
    for (index, coordinate) in coordinates.into_iter().enumerate() {
        context.bail_if_cancelled()?;
        artifacts.push((
            ArtifactId::from(format!("forge-userdev-library-{index}")),
            MavenCoordinate::parse(&coordinate)?,
            ArtifactPurpose::from("Forge userdev compile classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_forge_userdev_libraries",
        libraries = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn resolve_forge_userdev_test_libraries(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let config: ForgeUserdevConfig = {
        let _span =
            tracing::debug_span!("resolve_forge_userdev_test_libraries_read_config").entered();
        read_zip_json_entry(
            &context
                .artifact(ArtifactId::from("forge-userdev"))?
                .cache_path,
            "config.json",
        )?
    };
    context.bail_if_cancelled()?;

    let mut artifacts = Vec::new();
    for (index, coordinate) in config.test_libraries.into_iter().enumerate() {
        context.bail_if_cancelled()?;
        artifacts.push((
            ArtifactId::from(format!("forge-userdev-test-library-{index}")),
            MavenCoordinate::parse(&coordinate)?,
            ArtifactPurpose::from("Forge userdev game-test runtime classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_forge_userdev_test_libraries_resolve_artifacts",
        libraries = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn resolve_compile_dependencies(
    context: &ExecutionContext<'_>,
    resolver: &Resolver,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let dependency_script = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("dependencies")
        .join(context.plan.minecraft_version.as_str())
        .join("dependencies.gradle");
    let dependencies = parse_dependency_script(&dependency_script, &context.plan.properties)?;
    context.bail_if_cancelled()?;
    let mut artifacts = Vec::new();
    for dependency in dependencies
        .iter()
        .filter(|dependency| {
            !dependency.fg_deobf
                && matches!(
                    dependency.configuration.as_str(),
                    "implementation" | "compileOnly" | "annotationProcessor"
                )
                && (context.plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev
                    || dependency.configuration == "annotationProcessor")
        })
        .enumerate()
    {
        context.bail_if_cancelled()?;
        let (index, dependency) = dependency;
        artifacts.push((
            ArtifactId::from(format!("compile-dependency-{index}")),
            dependency.coordinate.clone(),
            ArtifactPurpose::from("Project compile classpath"),
        ));
    }
    let _span = tracing::debug_span!(
        "resolve_compile_dependencies",
        dependencies = artifacts.len()
    )
    .entered();
    Ok(resolver
        .resolve_artifacts(artifacts)?
        .into_iter()
        .map(|artifact| artifact.cache_path)
        .collect())
}

fn collect_project_java_sources(
    context: &ExecutionContext<'_>,
    generated_sources: &Path,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut sources = collect_source_set_java_sources(context, "main")?;
    context.bail_if_cancelled()?;
    sources.extend(collect_java_sources_under(context, generated_sources)?);
    sources.sort();
    Ok(sources)
}

fn collect_source_set_java_sources(
    context: &ExecutionContext<'_>,
    source_set: &str,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let source_root = context
        .plan
        .minecraft_dir
        .join("src")
        .join(source_set)
        .join("java");
    let excludes = read_source_excludes(context, source_set)?;
    let mut sources = Vec::new();
    for path in collect_java_sources_under(context, &source_root)? {
        context.bail_if_cancelled()?;
        let relative = relative_zip_name(&source_root, &path).unwrap_or_default();
        if !is_excluded_source(&relative, &excludes) {
            sources.push(path);
        }
    }
    sources.sort();
    Ok(sources)
}

fn read_source_excludes(
    context: &ExecutionContext<'_>,
    source_set: &str,
) -> eyre::Result<Vec<String>> {
    context.bail_if_cancelled()?;
    let path = context
        .plan
        .minecraft_dir
        .join("gradle")
        .join("source-excludes")
        .join(context.plan.minecraft_version.as_str())
        .join(format!("{source_set}-java.txt"));
    if !path.exists() {
        return Ok(Vec::new());
    }
    let excludes_text =
        fs::read_to_string(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    context.bail_if_cancelled()?;
    Ok(excludes_text
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| line.replace('\\', "/"))
        .collect())
}

fn is_excluded_source(relative: &str, excludes: &[String]) -> bool {
    excludes.iter().any(|exclude| {
        if let Some(prefix) = exclude.strip_suffix("/**") {
            relative.starts_with(prefix)
        } else if Path::new(exclude)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            relative == exclude
        } else {
            relative == exclude || relative.starts_with(&format!("{exclude}/"))
        }
    })
}

fn collect_java_sources_under(
    context: &ExecutionContext<'_>,
    root: &Path,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut sources = Vec::new();
    if !root.exists() {
        return Ok(sources);
    }
    for entry in
        fs::read_dir(root).wrap_err_with(|| format!("Failed to read {}", root.display()))?
    {
        context.bail_if_cancelled()?;
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", root.display()))?;
        let path = entry.path();
        if path.is_dir() {
            sources.extend(collect_java_sources_under(context, &path)?);
        } else if path.extension().and_then(|extension| extension.to_str()) == Some("java") {
            sources.push(path);
        }
    }
    Ok(sources)
}

fn collect_jars(context: &ExecutionContext<'_>, root: &Path) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut jars = Vec::new();
    if !root.exists() {
        return Ok(jars);
    }
    for entry in
        fs::read_dir(root).wrap_err_with(|| format!("Failed to read {}", root.display()))?
    {
        context.bail_if_cancelled()?;
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", root.display()))?;
        let path = entry.path();
        if path.is_dir() {
            jars.extend(collect_jars(context, &path)?);
        } else if path.extension().and_then(|extension| extension.to_str()) == Some("jar") {
            jars.push(path);
        }
    }
    Ok(jars)
}

fn write_javac_argfile(
    context: &ExecutionContext<'_>,
    argfile: &Path,
    classpath: &[PathBuf],
    sources: &[PathBuf],
    classes_dir: &Path,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let refmap = context
        .plan
        .cache_dir
        .join("project")
        .join("resources")
        .join("sfm.refmap.json");
    let out_tsrg = context
        .plan
        .cache_dir
        .join("project")
        .join("compileJava-mappings.tsrg");
    let reobf_tsrg = context
        .plan
        .cache_dir
        .join("forge")
        .join(context.plan.minecraft_version.as_str())
        .join("mappings")
        .join("official_to_srg.tsrg");
    if let Some(parent) = refmap.parent() {
        fs::create_dir_all(parent)?;
    }
    context.bail_if_cancelled()?;

    let mut args = Vec::new();
    args.extend([
        "-encoding".to_string(),
        "UTF-8".to_string(),
        "-g".to_string(),
        "-Xmaxerrs".to_string(),
        "0".to_string(),
        "-d".to_string(),
        classes_dir.display().to_string(),
        "-classpath".to_string(),
        join_classpath(classpath),
        "-sourcepath".to_string(),
        String::new(),
        "-AoutRefMapFile=".to_string() + &refmap.display().to_string(),
    ]);
    append_javac_release_args(
        &mut args,
        context.plan.java_release,
        context.plan.java.major_version,
    );
    if context.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        args.extend([
            "-AdefaultObfuscationEnv=named".to_string(),
            "-AdisableTargetValidator=true".to_string(),
        ]);
    } else {
        args.extend([
            "-AoutTsrgFile=".to_string() + &out_tsrg.display().to_string(),
            "-AreobfTsrgFile=".to_string() + &reobf_tsrg.display().to_string(),
            "-AmappingTypes=tsrg".to_string(),
            "-AdefaultObfuscationEnv=searge".to_string(),
        ]);
    }
    for source in sources {
        context.bail_if_cancelled()?;
        args.push(source.display().to_string());
    }

    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    context.bail_if_cancelled()?;
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;
    Ok(())
}

fn write_javac_no_ap_argfile(
    context: &ExecutionContext<'_>,
    argfile: &Path,
    classpath: &[PathBuf],
    sources: &[PathBuf],
    classes_dir: &Path,
) -> eyre::Result<()> {
    context.bail_if_cancelled()?;
    let mut args = Vec::new();
    args.extend([
        "-encoding".to_string(),
        "UTF-8".to_string(),
        "-g".to_string(),
        "-Xmaxerrs".to_string(),
        "0".to_string(),
        "-proc:none".to_string(),
        "-d".to_string(),
        classes_dir.display().to_string(),
        "-classpath".to_string(),
        join_classpath(classpath),
        "-sourcepath".to_string(),
        String::new(),
    ]);
    append_javac_release_args(
        &mut args,
        context.plan.java_release,
        context.plan.java.major_version,
    );
    for source in sources {
        context.bail_if_cancelled()?;
        args.push(source.display().to_string());
    }

    if let Some(parent) = argfile.parent() {
        fs::create_dir_all(parent)?;
    }
    context.bail_if_cancelled()?;
    fs::write(
        argfile,
        args.into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .wrap_err_with(|| format!("Failed to write {}", argfile.display()))?;
    Ok(())
}

fn append_javac_release_args(args: &mut Vec<String>, java_release: u32, java_major_version: u32) {
    if java_major_version != java_release {
        args.extend(["--release".to_string(), java_release.to_string()]);
    }
}

fn join_classpath(classpath: &[PathBuf]) -> String {
    let separator = if cfg!(windows) { ";" } else { ":" };
    classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(separator)
}

fn escape_argfile_arg(arg: String) -> String {
    if arg.is_empty() {
        "\"\"".to_string()
    } else if arg.contains(' ') || arg.contains('(') || arg.contains(')') {
        format!("\"{}\"", arg.replace('\\', "\\\\").replace('"', "\\\""))
    } else {
        arg
    }
}

fn read_main_class(path: &Path) -> eyre::Result<String> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut manifest = archive
        .by_name("META-INF/MANIFEST.MF")
        .wrap_err_with(|| format!("Tool jar {} has no manifest", path.display()))?;
    let mut content = String::new();
    manifest
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read manifest from {}", path.display()))?;
    manifest_attribute(&content, "Main-Class")
        .ok_or_else(|| eyre::eyre!("Tool jar {} has no Main-Class", path.display()))
}

fn manifest_attribute(content: &str, key: &str) -> Option<String> {
    let mut unfolded: Vec<String> = Vec::new();
    for line in content.lines() {
        if let Some(continuation) = line.strip_prefix(' ') {
            if let Some(last) = unfolded.last_mut() {
                last.push_str(continuation);
            }
        } else {
            unfolded.push(line.to_string());
        }
    }

    let prefix = format!("{key}:");
    unfolded
        .iter()
        .find_map(|line| line.strip_prefix(&prefix).map(str::trim))
        .map(str::to_string)
}

fn extract_zip_entry_to_path(zip_path: &Path, entry_name: &str, output: &Path) -> eyre::Result<()> {
    let bytes =
        fs::read(zip_path).wrap_err_with(|| format!("Failed to read {}", zip_path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", zip_path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", zip_path.display()))?;
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    std::io::copy(&mut entry, &mut file)
        .wrap_err_with(|| format!("Failed to extract {entry_name} to {}", output.display()))?;
    Ok(())
}

fn read_zip_entry(zip_path: &Path, entry_name: &str) -> eyre::Result<Vec<u8>> {
    let bytes =
        fs::read(zip_path).wrap_err_with(|| format!("Failed to read {}", zip_path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", zip_path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", zip_path.display()))?;
    let mut output = Vec::new();
    entry
        .read_to_end(&mut output)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", zip_path.display()))?;
    Ok(output)
}

fn read_tsrg_original_classes(path: &Path) -> eyre::Result<BTreeSet<String>> {
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut classes = BTreeSet::new();
    for line in content.lines() {
        if line.trim().is_empty()
            || line.starts_with('\t')
            || line.starts_with(' ')
            || line.starts_with('#')
            || line.starts_with("tsrg")
        {
            continue;
        }
        if let Some(class_name) = line.split_whitespace().next() {
            classes.insert(format!("{}.class", class_name.replace('.', "/")));
        }
    }
    Ok(classes)
}

fn copy_filtered_jar(
    input: &Path,
    output: &Path,
    allowed_entries: &BTreeSet<String>,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to open jar {}", input.display()))?;
    let file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(file);

    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read jar entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if !allowed_entries.contains(&name) {
            continue;
        }
        writer
            .raw_copy_file_rename(entry, name)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn raw_copy_zip_entry_rename<R, W>(
    archive: &mut ZipArchive<R>,
    writer: &mut ZipWriter<W>,
    source_name: &str,
    output_name: &str,
    output: &Path,
) -> eyre::Result<()>
where
    R: Read + Seek,
    W: Write + Seek,
{
    let entry = archive
        .by_name(source_name)
        .wrap_err_with(|| format!("Failed to read zip entry {source_name}"))?;
    writer
        .raw_copy_file_rename(entry, output_name)
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

fn write_minecraft_libraries_cfg(
    context: &ExecutionContext<'_>,
    client: &Client,
    output: &Path,
) -> eyre::Result<()> {
    let library_paths = resolve_current_minecraft_libraries(context, client)?;
    let mut lines = library_paths
        .iter()
        .map(|library_path| {
            dunce::canonicalize(library_path)
                .map(|path| format!("-e={}", path.display()))
                .wrap_err_with(|| format!("Failed to canonicalize {}", library_path.display()))
        })
        .collect::<eyre::Result<Vec<_>>>()?;

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    lines.sort();
    fs::write(output, format!("{}\n", lines.join("\n")))
        .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(mc = %context.plan.minecraft_version)
)]
fn resolve_current_minecraft_libraries(
    context: &ExecutionContext<'_>,
    client: &Client,
) -> eyre::Result<Vec<PathBuf>> {
    context.bail_if_cancelled()?;
    let mut cached_libraries = {
        let _span =
            tracing::debug_span!("resolve_current_minecraft_libraries_cache_lock").entered();
        context
            .minecraft_libraries_cache
            .lock()
            .map_err(|_poisoned| eyre::eyre!("Minecraft library cache lock poisoned"))?
    };
    if let Some(libraries) = cached_libraries.as_ref() {
        tracing::debug!(
            libraries = libraries.len(),
            "resolve_current_minecraft_libraries cache hit"
        );
        return Ok(libraries.clone());
    }

    let version_json: MinecraftVersionJson = {
        let _span = tracing::debug_span!(
            "resolve_current_minecraft_libraries_read_version_json",
            path = %context.plan.minecraft.version_json.cache_path.display()
        )
        .entered();
        read_json_file(&context.plan.minecraft.version_json.cache_path)?
    };
    let libraries = {
        let _span = tracing::debug_span!("resolve_current_minecraft_libraries_select").entered();
        minecraft_library_jars_from_version_json(
            &context.plan.minecraft_libraries_dir,
            &version_json,
        )
    };

    let library_paths = {
        let _span = tracing::debug_span!(
            "resolve_current_minecraft_libraries_download",
            libraries = libraries.len()
        )
        .entered();
        libraries
            .par_iter()
            .enumerate()
            .map(|(index, library)| {
                context.bail_if_cancelled()?;
                let _span = tracing::debug_span!(
                    "resolve_current_minecraft_library",
                    index,
                    path = %library.path.display(),
                    url = %library.url,
                    has_expected_hash = library.sha1.is_some()
                )
                .entered();
                if let Some(expected_hash) = library.sha1.as_ref() {
                    let _span = tracing::debug_span!(
                        "resolve_current_minecraft_library_download_checked",
                        expected_hash = %expected_hash
                    )
                    .entered();
                    download_to_path_overwrite_with_expected_hash(
                        &context.cancellation_token,
                        client,
                        &library.url,
                        &library.path,
                        false,
                        expected_hash,
                    )?;
                } else {
                    let _span = tracing::debug_span!("resolve_current_minecraft_library_download")
                        .entered();
                    download_to_path(
                        &context.cancellation_token,
                        client,
                        &library.url,
                        &library.path,
                    )?;
                }
                {
                    let _span =
                        tracing::debug_span!("resolve_current_minecraft_library_assert_input")
                            .entered();
                    context.assert_allowed_input(&library.path)?;
                };
                Ok(library.path.clone())
            })
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };

    let _span = tracing::debug_span!(
        "resolve_current_minecraft_libraries_dedup",
        libraries = library_paths.len()
    )
    .entered();
    let library_paths = dedup_paths_preserve_order(library_paths);
    *cached_libraries = Some(library_paths.clone());
    Ok(library_paths)
}

#[derive(Debug)]
struct MinecraftLibraryJar {
    path: PathBuf,
    url: String,
    sha1: Option<ContentHash>,
}

fn minecraft_library_jars_from_version_json(
    libraries_root: &Path,
    version_json: &MinecraftVersionJson,
) -> Vec<MinecraftLibraryJar> {
    version_json
        .libraries
        .iter()
        .filter_map(|library| {
            let artifact = library.downloads.as_ref()?.artifact.as_ref()?;
            Some(MinecraftLibraryJar {
                path: minecraft_library_path(libraries_root, &artifact.path),
                url: artifact.url.clone(),
                sha1: artifact.sha1,
            })
        })
        .collect()
}

fn minecraft_library_path(libraries_root: &Path, artifact_path: &str) -> PathBuf {
    let mut path = libraries_root.to_path_buf();
    for segment in artifact_path
        .split('/')
        .filter(|segment| !segment.is_empty())
    {
        path.push(segment);
    }
    path
}

fn inject_mcp_sources(mcp_zip: &Path, source_jar: &Path, output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let source_bytes = fs::read(source_jar)
        .wrap_err_with(|| format!("Failed to read {}", source_jar.display()))?;
    let mut source_archive = ZipArchive::new(Cursor::new(source_bytes))
        .wrap_err_with(|| format!("Failed to open {}", source_jar.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let mut written = BTreeSet::new();

    for index in 0..source_archive.len() {
        let entry = source_archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read source entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }
        writer
            .raw_copy_file_rename(entry, &name)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        written.insert(name);
    }

    let mcp_bytes =
        fs::read(mcp_zip).wrap_err_with(|| format!("Failed to read {}", mcp_zip.display()))?;
    let mut mcp_archive = ZipArchive::new(Cursor::new(mcp_bytes))
        .wrap_err_with(|| format!("Failed to open {}", mcp_zip.display()))?;
    for index in 0..mcp_archive.len() {
        let entry = mcp_archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read MCP entry #{index}"))?;
        let name = entry.name().replace('\\', "/");
        let Some(output_name) = name.strip_prefix("config/inject/") else {
            continue;
        };
        if output_name.is_empty() || output_name.ends_with('/') || written.contains(output_name) {
            continue;
        }
        writer
            .raw_copy_file_rename(entry, output_name)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        written.insert(output_name.to_string());
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn apply_mcp_joined_patches(
    context: &ExecutionContext<'_>,
    mcp_zip: &Path,
    source_jar: &Path,
    output: &Path,
    mcp_root: &Path,
) -> eyre::Result<()> {
    let patch_root = mcp_root.join("patch");
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let rejects = patch_root.join("rejects.zip");
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
            "patches/joined/".to_string(),
            "--output".to_string(),
            output.display().to_string(),
            "--reject".to_string(),
            rejects.display().to_string(),
            source_jar.display().to_string(),
            mcp_zip.display().to_string(),
        ],
        &patch_root,
    )?;
    Ok(())
}

fn merge_zip_archives(inputs: &[PathBuf], output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let mut written = BTreeSet::new();

    for input in inputs {
        let bytes =
            fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
        let mut archive = ZipArchive::new(Cursor::new(bytes))
            .wrap_err_with(|| format!("Failed to open {}", input.display()))?;
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
            let name = entry.name().replace('\\', "/");
            if name.ends_with('/')
                || name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
                || zip_entry_has_extension(&name, "SF")
                || zip_entry_has_extension(&name, "RSA")
                || !written.insert(name.clone())
            {
                continue;
            }
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        input = %input.display(),
        universal = %forge_universal_jar.display(),
        output = %output.display(),
    )
)]
fn write_run_forge_dev_jar(
    input: &Path,
    forge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    let manifest = forge_runtime_manifest(forge_universal_jar)?;
    write_run_loader_dev_jar(input, &manifest, output)?;
    tracing::info!("Generated Forge userdev runtime jar: {}", output.display());
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        input = %input.display(),
        universal = %neoforge_universal_jar.display(),
        output = %output.display(),
    )
)]
fn write_run_neoforge_dev_jar(
    input: &Path,
    neoforge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    let manifest = neoforge_runtime_manifest(neoforge_universal_jar)?;
    write_run_loader_dev_jar(input, &manifest, output)?;
    tracing::info!(
        "Generated NeoForge userdev runtime jar: {}",
        output.display()
    );
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        input = %input.display(),
        universal = %neoforge_universal_jar.display(),
        output = %output.display(),
    )
)]
fn write_run_neoforge_minecraft_dev_jar(
    input: &Path,
    neoforge_universal_jar: &Path,
    output: &Path,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let manifest = minecraft_runtime_manifest(input)?;
    let neoforge_entries = zip_entry_names(neoforge_universal_jar)?;
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes)).wrap_err_with(|| {
        format!(
            "Failed to open NeoForge Minecraft runtime jar {}",
            input.display()
        )
    })?;
    let mut names = BTreeSet::new();
    {
        let _span = tracing::debug_span!(
            "write_run_neoforge_minecraft_dev_jar_scan_entries",
            archive_entries = archive.len()
        )
        .entered();
        for index in 0..archive.len() {
            let entry = archive.by_index(index).wrap_err_with(|| {
                format!("Failed to read NeoForge Minecraft runtime jar entry #{index}")
            })?;
            let name = entry.name().replace('\\', "/");
            if should_keep_split_minecraft_runtime_entry(&name, &neoforge_entries) {
                names.insert(name);
            }
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(&manifest)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    {
        let _span = tracing::debug_span!(
            "write_run_neoforge_minecraft_dev_jar_write_entries",
            entries = names.len(),
            method = "raw_copy"
        )
        .entered();
        for name in names {
            raw_copy_zip_entry_rename(&mut archive, &mut writer, &name, &name, output)?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    tracing::info!(
        "Generated NeoForge Minecraft runtime jar: {}",
        output.display()
    );
    Ok(())
}

fn should_keep_split_minecraft_runtime_entry(
    name: &str,
    neoforge_entries: &BTreeSet<String>,
) -> bool {
    if name.ends_with('/')
        || name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
        || is_signature_file(name)
        || is_neoforge_specific_runtime_entry(name)
    {
        return false;
    }

    if is_neoforge_mod_marker(name) {
        return true;
    }

    if neoforge_entries.contains(name) && zip_entry_has_extension(name, "class") {
        return false;
    }

    true
}

#[instrument(
    level = "debug",
    skip_all,
    fields(input = %input.display(), output = %output.display())
)]
fn write_run_loader_dev_jar(input: &Path, manifest: &[u8], output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes.as_slice()))
        .wrap_err_with(|| format!("Failed to open loader runtime jar {}", input.display()))?;
    let mut names = BTreeSet::new();
    {
        let _span = tracing::debug_span!(
            "write_run_loader_dev_jar_scan_entries",
            archive_entries = archive.len()
        )
        .entered();
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read loader runtime jar entry #{index}"))?;
            let name = entry.name().replace('\\', "/");
            if !name.ends_with('/')
                && !name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
                && !is_signature_file(&name)
            {
                names.insert(name);
            }
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(manifest)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    let names = names.into_iter().collect::<Vec<_>>();
    {
        let _span = tracing::debug_span!(
            "write_run_loader_dev_jar_write_entries",
            entries = names.len(),
            method = "raw_copy"
        )
        .entered();
        for name in names {
            let entry = archive
                .by_name(&name)
                .wrap_err_with(|| format!("Failed to read loader runtime jar entry {name}"))?;
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

#[instrument(level = "debug", skip_all, fields(path = %path.display()))]
fn zip_entry_names(path: &Path) -> eyre::Result<BTreeSet<String>> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open jar {}", path.display()))?;
    let mut names = BTreeSet::new();
    {
        let _span =
            tracing::debug_span!("zip_entry_names_scan", archive_entries = archive.len()).entered();
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read {} entry #{index}", path.display()))?;
            let name = entry.name().replace('\\', "/");
            if !name.ends_with('/') {
                names.insert(name);
            }
        }
    }
    Ok(names)
}

#[instrument(
    level = "debug",
    skip_all,
    fields(universal = %forge_universal_jar.display())
)]
fn forge_runtime_manifest(forge_universal_jar: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = read_zip_entry(forge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "Forge universal manifest was not UTF-8: {}",
            forge_universal_jar.display()
        )
    })?;
    let normalized = manifest.replace("\r\n", "\n");
    let sections = normalized.split("\n\n").collect::<Vec<_>>();
    let main_section = sections
        .first()
        .copied()
        .ok_or_else(|| eyre::eyre!("Forge universal manifest was empty"))?;
    let required_sections = [
        "net/minecraftforge/fml/loading/",
        "net/minecraftforge/versions/forge/",
        "net/minecraftforge/versions/mcp/",
    ];
    let mut output_sections = vec![strip_manifest_digests(main_section)];

    for required in required_sections {
        let Some(section) = sections
            .iter()
            .copied()
            .find(|section| manifest_section_name(section) == Some(required))
        else {
            if manifest_attribute(&manifest, "FML-System-Mods").as_deref() == Some("forge") {
                let output_sections = sections
                    .iter()
                    .copied()
                    .map(strip_manifest_digests)
                    .filter(|section| !section.trim().is_empty())
                    .collect::<Vec<_>>();
                return Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes());
            }
            eyre::bail!(
                "Forge universal manifest {} did not contain package section {required}",
                forge_universal_jar.display()
            );
        };
        output_sections.push(strip_manifest_digests(section));
    }

    Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes())
}

#[instrument(level = "debug", skip_all, fields(input = %input.display()))]
fn minecraft_runtime_manifest(input: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = match read_zip_entry(input, "META-INF/MANIFEST.MF") {
        Ok(manifest) => String::from_utf8(manifest).wrap_err_with(|| {
            format!(
                "Minecraft runtime manifest was not UTF-8: {}",
                input.display()
            )
        })?,
        Err(_) => "Manifest-Version: 1.0\n".to_string(),
    };
    let normalized = manifest.replace("\r\n", "\n");
    let sections = normalized
        .split("\n\n")
        .map(strip_manifest_digests)
        .map(|section| strip_manifest_attribute(&section, "FML-System-Mods"))
        .filter(|section| !section.trim().is_empty())
        .collect::<Vec<_>>();
    Ok(format!("{}\r\n\r\n", sections.join("\r\n\r\n")).into_bytes())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(universal = %neoforge_universal_jar.display())
)]
fn neoforge_runtime_manifest(neoforge_universal_jar: &Path) -> eyre::Result<Vec<u8>> {
    let manifest = read_zip_entry(neoforge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "NeoForge universal manifest was not UTF-8: {}",
            neoforge_universal_jar.display()
        )
    })?;
    if manifest_attribute(&manifest, "FML-System-Mods").as_deref() != Some("neoforge") {
        eyre::bail!(
            "NeoForge universal manifest {} did not declare FML-System-Mods: neoforge",
            neoforge_universal_jar.display()
        );
    }
    let normalized = manifest.replace("\r\n", "\n");
    let output_sections = normalized
        .split("\n\n")
        .map(strip_manifest_digests)
        .filter(|section| !section.trim().is_empty())
        .collect::<Vec<_>>();

    Ok(format!("{}\r\n\r\n", output_sections.join("\r\n\r\n")).into_bytes())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(universal = %neoforge_universal_jar.display())
)]
fn neoforge_requires_split_runtime(neoforge_universal_jar: &Path) -> eyre::Result<bool> {
    let manifest = read_zip_entry(neoforge_universal_jar, "META-INF/MANIFEST.MF")?;
    let manifest = String::from_utf8(manifest).wrap_err_with(|| {
        format!(
            "NeoForge universal manifest was not UTF-8: {}",
            neoforge_universal_jar.display()
        )
    })?;
    Ok(
        manifest_attribute(&manifest, "FML-System-Mods").as_deref() == Some("neoforge")
            && !manifest.contains("\nName: net/neoforged/neoforge/versions/neoform/"),
    )
}

fn manifest_section_name(section: &str) -> Option<&str> {
    section.lines().next()?.strip_prefix("Name: ")
}

fn strip_manifest_digests(section: &str) -> String {
    let mut output = Vec::new();
    let mut dropping_attribute = false;
    for line in section.lines() {
        if line.starts_with(' ') {
            if !dropping_attribute {
                output.push(line);
            }
            continue;
        }
        dropping_attribute = line.contains("-Digest:");
        if !dropping_attribute {
            output.push(line);
        }
    }
    output.join("\r\n")
}

fn strip_manifest_attribute(section: &str, attribute: &str) -> String {
    let mut output = Vec::new();
    let mut dropping_attribute = false;
    let prefix = format!("{attribute}:");
    for line in section.lines() {
        if line.starts_with(' ') {
            if !dropping_attribute {
                output.push(line);
            }
            continue;
        }
        dropping_attribute = line.starts_with(&prefix);
        if !dropping_attribute {
            output.push(line);
        }
    }
    output.join("\r\n")
}

fn is_neoforge_specific_runtime_entry(name: &str) -> bool {
    name.starts_with("net/neoforged/neoforge/")
        || name.starts_with("META-INF/services/")
        || name.eq_ignore_ascii_case("META-INF/neoforged.mods.toml")
        || name.eq_ignore_ascii_case("META-INF/mods.toml")
        || name.starts_with("data/neoforge/")
        || name.starts_with("assets/neoforge/")
}

fn is_neoforge_mod_marker(name: &str) -> bool {
    name.eq_ignore_ascii_case("META-INF/neoforge.mods.toml")
}

#[instrument(
    level = "debug",
    skip_all,
    fields(client_jar = %client_jar.display(), output = %output.display())
)]
fn write_client_extra_jar(client_jar: &Path, output: &Path) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }

    let bytes = fs::read(client_jar)
        .wrap_err_with(|| format!("Failed to read {}", client_jar.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open client jar {}", client_jar.display()))?;
    let mut names = BTreeSet::new();
    {
        let _span = tracing::debug_span!(
            "write_client_extra_jar_scan_entries",
            archive_entries = archive.len()
        )
        .entered();
        for index in 0..archive.len() {
            let entry = archive
                .by_index(index)
                .wrap_err_with(|| format!("Failed to read client jar entry #{index}"))?;
            let name = entry.name().replace('\\', "/");
            if is_client_extra_entry(&name) {
                names.insert(name);
            }
        }
    }

    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    writer
        .start_file("META-INF/MANIFEST.MF", options)
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;
    writer
        .write_all(b"Manifest-Version: 1.0\r\nMinecraft-Dists: server client\r\n\r\n")
        .wrap_err_with(|| format!("Failed to write manifest to {}", output.display()))?;

    {
        let _span = tracing::debug_span!(
            "write_client_extra_jar_write_entries",
            entries = names.len(),
            method = "raw_copy"
        )
        .entered();
        for name in names {
            raw_copy_zip_entry_rename(&mut archive, &mut writer, &name, &name, output)?;
        }
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    tracing::info!("Generated client-extra jar: {}", output.display());
    Ok(())
}

fn is_client_extra_entry(name: &str) -> bool {
    !name.ends_with('/')
        && !zip_entry_has_extension(name, "class")
        && !name.eq_ignore_ascii_case("META-INF/MANIFEST.MF")
        && !is_signature_file(name)
}

fn is_signature_file(name: &str) -> bool {
    name.starts_with("META-INF/")
        && (zip_entry_has_extension(name, "SF")
            || zip_entry_has_extension(name, "RSA")
            || zip_entry_has_extension(name, "EC")
            || zip_entry_has_extension(name, "DSA"))
}

fn patch_inner_class_access_in_jar(
    input: &Path,
    output: &Path,
    outer_class: &str,
    inner_class: &str,
) -> eyre::Result<()> {
    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent)?;
    }
    let bytes = fs::read(input).wrap_err_with(|| format!("Failed to read {}", input.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open jar {}", input.display()))?;
    let output_file =
        File::create(output).wrap_err_with(|| format!("Failed to create {}", output.display()))?;
    let mut writer = ZipWriter::new(output_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to read {} entry #{index}", input.display()))?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }
        if !zip_entry_has_extension(&name, "class") {
            writer
                .raw_copy_file_rename(entry, name)
                .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
            continue;
        }
        let mut entry_bytes = Vec::new();
        entry
            .read_to_end(&mut entry_bytes)
            .wrap_err_with(|| format!("Failed to read entry {name} from {}", input.display()))?;
        patch_inner_class_access_in_class_file(&mut entry_bytes, outer_class, inner_class)
            .wrap_err_with(|| format!("Failed to patch class access in {name}"))?;
        writer
            .start_file(name, options)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
        writer
            .write_all(&entry_bytes)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    }

    writer
        .finish()
        .wrap_err_with(|| format!("Failed to finish {}", output.display()))?;
    Ok(())
}

fn patch_inner_class_access_in_class_file(
    bytes: &mut [u8],
    outer_class: &str,
    inner_class: &str,
) -> eyre::Result<bool> {
    const ACC_PUBLIC: u16 = 0x0001;
    const ACC_PRIVATE: u16 = 0x0002;
    const ACC_PROTECTED: u16 = 0x0004;

    if bytes.len() < 10 || read_u32(bytes, 0)? != 0xCAFE_BABE {
        return Ok(false);
    }

    let parsed = parse_class_constant_pool(bytes)?;
    let mut changed = false;
    let access_flags_offset = parsed.after_constant_pool;
    let this_class = read_u16(bytes, access_flags_offset + 2)?;
    if parsed.class_name(this_class) == Some(inner_class) {
        let flags = read_u16(bytes, access_flags_offset)?;
        let patched = (flags | ACC_PUBLIC) & !ACC_PRIVATE & !ACC_PROTECTED;
        if patched != flags {
            write_u16(bytes, access_flags_offset, patched)?;
            changed = true;
        }
    }

    let mut cursor = parsed.after_constant_pool + 6;
    let interfaces_count = read_u16(bytes, cursor)? as usize;
    cursor += 2 + interfaces_count * 2;
    cursor = skip_class_members(bytes, cursor)?;
    cursor = skip_class_members(bytes, cursor)?;

    let attributes_count = read_u16(bytes, cursor)? as usize;
    cursor += 2;
    for _ in 0..attributes_count {
        let attribute_name_index = read_u16(bytes, cursor)?;
        let attribute_length = read_u32(bytes, cursor + 2)? as usize;
        let attribute_info = cursor + 6;
        let attribute_end = attribute_info
            .checked_add(attribute_length)
            .ok_or_else(|| eyre::eyre!("Class attribute length overflow"))?;
        if attribute_end > bytes.len() {
            eyre::bail!("Class attribute extends past end of file");
        }

        if parsed.utf8(attribute_name_index) == Some("InnerClasses") {
            let inner_classes_count = read_u16(bytes, attribute_info)? as usize;
            let mut inner_cursor = attribute_info + 2;
            for _ in 0..inner_classes_count {
                let inner_class_index = read_u16(bytes, inner_cursor)?;
                let outer_class_index = read_u16(bytes, inner_cursor + 2)?;
                let access_offset = inner_cursor + 6;
                let inner_name_matches = parsed.class_name(inner_class_index) == Some(inner_class);
                let outer_name_matches = outer_class_index == 0
                    || parsed.class_name(outer_class_index) == Some(outer_class);
                if inner_name_matches && outer_name_matches {
                    let flags = read_u16(bytes, access_offset)?;
                    let patched = (flags | ACC_PUBLIC) & !ACC_PRIVATE & !ACC_PROTECTED;
                    if patched != flags {
                        write_u16(bytes, access_offset, patched)?;
                        changed = true;
                    }
                }
                inner_cursor += 8;
            }
        }
        cursor = attribute_end;
    }

    Ok(changed)
}

#[derive(Debug)]
struct ParsedClassConstantPool {
    entries: Vec<ClassConstant>,
    after_constant_pool: usize,
}

impl ParsedClassConstantPool {
    fn utf8(&self, index: u16) -> Option<&str> {
        match self.entries.get(index as usize)? {
            ClassConstant::Utf8(value) => Some(value.as_str()),
            ClassConstant::Other | ClassConstant::Class { .. } => None,
        }
    }

    fn class_name(&self, index: u16) -> Option<&str> {
        let ClassConstant::Class { name_index } = self.entries.get(index as usize)? else {
            return None;
        };
        self.utf8(*name_index)
    }
}

#[derive(Debug)]
enum ClassConstant {
    Utf8(String),
    Class { name_index: u16 },
    Other,
}

fn parse_class_constant_pool(bytes: &[u8]) -> eyre::Result<ParsedClassConstantPool> {
    let constant_pool_count = read_u16(bytes, 8)? as usize;
    let mut entries = Vec::with_capacity(constant_pool_count);
    entries.push(ClassConstant::Other);
    let mut cursor = 10;
    let mut index = 1;

    while index < constant_pool_count {
        let tag = *bytes
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Class constant pool is truncated"))?;
        cursor += 1;
        match tag {
            1 => {
                let length = read_u16(bytes, cursor)? as usize;
                cursor += 2;
                let end = cursor
                    .checked_add(length)
                    .ok_or_else(|| eyre::eyre!("Utf8 constant length overflow"))?;
                if end > bytes.len() {
                    eyre::bail!("Utf8 constant extends past end of class file");
                }
                let value = String::from_utf8_lossy(&bytes[cursor..end]).into_owned();
                entries.push(ClassConstant::Utf8(value));
                cursor = end;
            }
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                cursor += 4;
                entries.push(ClassConstant::Other);
            }
            5 | 6 => {
                cursor += 8;
                entries.push(ClassConstant::Other);
                entries.push(ClassConstant::Other);
                index += 1;
            }
            7 => {
                let name_index = read_u16(bytes, cursor)?;
                cursor += 2;
                entries.push(ClassConstant::Class { name_index });
            }
            8 | 16 | 19 | 20 => {
                cursor += 2;
                entries.push(ClassConstant::Other);
            }
            15 => {
                cursor += 3;
                entries.push(ClassConstant::Other);
            }
            _ => eyre::bail!("Unsupported class constant pool tag {tag}"),
        }
        if cursor > bytes.len() {
            eyre::bail!("Class constant pool extends past end of file");
        }
        index += 1;
    }

    Ok(ParsedClassConstantPool {
        entries,
        after_constant_pool: cursor,
    })
}

fn skip_class_members(bytes: &[u8], mut cursor: usize) -> eyre::Result<usize> {
    let member_count = read_u16(bytes, cursor)? as usize;
    cursor += 2;
    for _ in 0..member_count {
        cursor += 6;
        let attributes_count = read_u16(bytes, cursor)? as usize;
        cursor += 2;
        for _ in 0..attributes_count {
            let attribute_length = read_u32(bytes, cursor + 2)? as usize;
            cursor = cursor
                .checked_add(6)
                .and_then(|value| value.checked_add(attribute_length))
                .ok_or_else(|| eyre::eyre!("Member attribute length overflow"))?;
            if cursor > bytes.len() {
                eyre::bail!("Member attribute extends past end of class file");
            }
        }
    }
    Ok(cursor)
}

fn read_u16(bytes: &[u8], offset: usize) -> eyre::Result<u16> {
    let value = bytes
        .get(offset..offset + 2)
        .ok_or_else(|| eyre::eyre!("Class file ended before u16 at offset {offset}"))?;
    Ok(u16::from_be_bytes([value[0], value[1]]))
}

fn write_u16(bytes: &mut [u8], offset: usize, value: u16) -> eyre::Result<()> {
    let destination = bytes
        .get_mut(offset..offset + 2)
        .ok_or_else(|| eyre::eyre!("Class file ended before u16 at offset {offset}"))?;
    destination.copy_from_slice(&value.to_be_bytes());
    Ok(())
}

fn read_u32(bytes: &[u8], offset: usize) -> eyre::Result<u32> {
    let value = bytes
        .get(offset..offset + 4)
        .ok_or_else(|| eyre::eyre!("Class file ended before u32 at offset {offset}"))?;
    Ok(u32::from_be_bytes([value[0], value[1], value[2], value[3]]))
}

#[derive(Debug)]
struct MojangClassMapping {
    official_slash: String,
    obf: String,
    fields: BTreeMap<String, String>,
    methods: BTreeMap<(String, String), MojangMethodMapping>,
}
