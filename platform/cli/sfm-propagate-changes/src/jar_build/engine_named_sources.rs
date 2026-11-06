// Explicit source-only release admission, separate from native build targets
// and from the locked platform-source-provider authority.

#[derive(Clone, Copy, Debug, facet::Facet)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub(crate) enum NamedSourceOrigin {
    HistoricalLocalCache,
    FreshOwnedExecution,
    ImmutableProfile,
}

#[derive(Debug, facet::Facet)]
pub(crate) struct NamedSourceOutputReport {
    schema: String,
    scope: String,
    preparation: crate::source_projection::frozen_recipe_project::FrozenRecipeProjectReceipt,
    dependency_origin: NamedSourceOrigin,
    #[facet(proxy = JsonPath)]
    sources_archive: PathBuf,
    source_archive_sha256: String,
    source_payload_sha256: String,
    layout: SourceOutputLayout,
    #[facet(proxy = JsonPath)]
    output: PathBuf,
    sfm_compilation: String,
    source_provider_admission: String,
}

pub(crate) fn preflight_named_source_output_recipe(
    target: &str,
    minecraft: &str,
    recipe: &str,
) -> eyre::Result<()> {
    eyre::ensure!(
        cfg!(target_os = "linux"),
        "released durable source views currently require Linux"
    );
    eyre::ensure!(
        preflight_named_neoform_recipe(target, minecraft, recipe)?,
        "released source outputs require an exact reviewed NeoForm recipe"
    );
    Ok(())
}

pub(crate) fn validate_named_source_output_project(
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
) -> eyre::Result<()> {
    // This is intentionally a separate operation, not an exception to the
    // Compile/Jar-only generic build guard or a loader-dispatch workaround.
    let receipt = project.receipt();
    preflight_named_source_output_recipe(
        &receipt.ownership.target_id,
        &receipt.ownership.minecraft_version,
        &receipt.released_inputs.recipe_id,
    )?;
    eyre::ensure!(
        project.project().receipt().environment
            == crate::source_projection::projection_catalog::ProjectionEnvironment::Release,
        "released source view requires a released owner"
    );
    validate_named_neoform_compile(project)?;
    project.recheck(false)
}

pub(crate) fn invoke_named_source_outputs(
    project: crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
    options: &NamedCompileOptions,
    layout: SourceOutputLayout,
    cancellation: &CancellationToken,
) -> eyre::Result<NamedSourceOutputReport> {
    cancellation.bail_if_cancelled()?;
    validate_named_source_output_project(&project)?;
    eyre::ensure!(
        options.cache_root.is_some()
            && options.isolated_project_cache.is_none()
            && !options.rebuild_shared_mcp
            && !options.explain_rebuild
            && !options.wait_for_build_lock
            && super::local_dependency_cache::environment_allows_reuse(),
        "released sources require a managed root and no project-build overrides"
    );
    let identity = BuildProjectIdentity::Catalog {
        frozen: Arc::new(project),
    };
    let owner = selected_named_cache_owner(options, &identity, cancellation)?;
    let planning = NativePlanningOptions {
        output_name: None,
        derived_cache_root: owner,
        pinned_sdk: options.pinned_sdk.clone(),
        isolated_project_cache: None,
        mode: BuildMode::Build,
        java_home: Some(options.java_home.clone()),
        refresh: false,
        allow_local_artifact_cache: false,
        artifact_sources: Vec::new(),
        require_portable_artifacts: true,
    };
    let plan = create_plan_for_project(&planning, &identity, cancellation)?;
    drop(identity);
    validate_named_source_output_project(
        plan.named_project()
            .ok_or_else(|| eyre::eyre!("released source plan lost its owner"))?,
    )?;
    plan.recheck_named_sdk()?;
    // No mutable SFM output is consumed or written. Only the anchored
    // dependency producer and source-tree publication leases are needed.
    let context = ExecutionContext::new(&plan, cancellation.clone())?;
    let report = with_named_neoform_context(&context, |held, sources, origin| {
        named_source_output_report(held, sources, layout, origin)
    })?;
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    cancellation.bail_if_cancelled()?;
    Ok(report)
}

fn named_source_output_report(
    context: &ExecutionContext<'_>,
    sources: &Path,
    layout: SourceOutputLayout,
    origin: NamedSourceOrigin,
) -> eyre::Result<NamedSourceOutputReport> {
    context.bail_if_cancelled()?;
    let project = context
        .plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("source view requires its checked released owner"))?;
    validate_named_source_output_project(project)?;
    context.recheck_compiler_image()?;
    #[cfg(target_os = "linux")]
    {
        let cache = context.held_linux_dependencies.ok_or_else(|| {
            eyre::eyre!("released source view requires a held durable Linux dependency entry")
        })?;
        eyre::ensure!(
            cache.paths(&context.cancellation_token)?.1 == sources,
            "source view differs from held dependency archive"
        );
    };
    let source_archive_sha256 = named_source_archive_sha256(context, sources)?;
    let source_payload_sha256 = crate::source_archive::archive_payload_sha256(sources)?;
    let output = match layout {
        SourceOutputLayout::Jar => sources.to_path_buf(),
        SourceOutputLayout::Filetree => neoform_source_filetree_path(context, sources)?,
    };
    context.recheck_compiler_image()?;
    validate_named_source_output_project(project)?;
    context.bail_if_cancelled()?;
    Ok(NamedSourceOutputReport {
        schema: "sfm:released_native_sources@1".to_owned(),
        scope: "verified source view; no SFM javac, package, launch or provider admission"
            .to_owned(),
        preparation: project.receipt().clone(),
        dependency_origin: origin,
        sources_archive: sources.to_path_buf(),
        source_archive_sha256,
        source_payload_sha256,
        layout,
        output,
        sfm_compilation: "not_performed".to_owned(),
        source_provider_admission: "not_performed".to_owned(),
    })
}

fn named_source_archive_sha256(
    context: &ExecutionContext<'_>,
    sources: &Path,
) -> eyre::Result<String> {
    use sha2::Digest as _;
    let parent = sources
        .parent()
        .ok_or_else(|| eyre::eyre!("source archive has no parent"))?;
    let leaf = sources
        .file_name()
        .and_then(|s| s.to_str())
        .ok_or_else(|| eyre::eyre!("source archive has no filename"))?;
    let checked = crate::source_projection::candidate_lock::checked_file(parent, leaf)?;
    let mut file = fs::File::open(checked)?;
    let before = file.metadata()?;
    eyre::ensure!(
        before.is_file() && before.len() <= 512 * 1024 * 1024,
        "source archive is not a bounded regular file"
    );
    let mut hash = sha2::Sha256::new();
    let mut total = 0u64;
    let mut buffer = vec![0u8; 64 * 1024];
    loop {
        context.bail_if_cancelled()?;
        let count = file.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        total += u64::try_from(count)?;
        eyre::ensure!(total <= before.len(), "source archive grew while hashing");
        hash.update(&buffer[..count]);
    }
    eyre::ensure!(
        total == before.len() && file.metadata()?.len() == before.len(),
        "source archive changed length while hashing"
    );
    Ok(format!("sha256:{:x}", hash.finalize()))
}

#[cfg(all(test, target_os = "linux"))]
#[path = "engine_named_sources_tests.rs"]
mod named_source_output_tests;
