// Captured schema-4 development inputs are not released schema-2 recipes.
// Keep the checked owner alive throughout planning and execution.
#[derive(Debug, Facet)]
pub(crate) struct DevelopmentBuildReport {
    schema: String,
    preparation: crate::source_projection::native_project_target::NativeProjectReceipt,
    java_release: u32,
    tool_jvm_major: u32,
    actual_sdk_identity: String,
    #[facet(proxy = JsonPath)]
    cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    output_jar: PathBuf,
    compilation: String,
    jar_packaging: String,
    application_execution: String,
    immutable_source_lock: bool,
}

fn prepare_development_identity(
    project: crate::source_projection::catalog_owned_project::CatalogOwnedProject,
    profile: &str,
) -> eyre::Result<BuildProjectIdentity> {
    use crate::source_projection::native_project_target::NativeProjectTarget;
    use crate::source_projection::native_project_target::development_native_loader;
    use crate::source_projection::projection_catalog::ProjectionEnvironment;
    eyre::ensure!(
        project.receipt().environment == ProjectionEnvironment::Dev,
        "development native admission requires a development project"
    );
    let loader = development_native_loader(
        &project.receipt().target_id,
        &project.receipt().minecraft_version,
    )?;
    eyre::ensure!(
        project.receipt().loader == loader,
        "development loader mismatch"
    );
    let target = NativeProjectTarget::from_checked_project(&project, profile)?;
    #[cfg(windows)]
    if !matches!(project.receipt().target_id.as_str(), "1.19.2" | "1.19.4" | "1.20" | "1.20.1") {
        // Validate the actual development profile/tool parents before planning effects.
        development_neoform_source(&project, profile)?;
    }
    eyre::ensure!(
        target.receipt.project_inputs_sha256 == project.receipt().project_inputs_sha256
            && target.receipt.context_identity == project.receipt().context_identity,
        "development preflight lost its checked source owner"
    );
    Ok(BuildProjectIdentity::Development {
        project: Arc::new(project),
        target: Arc::new(target),
    })
}

#[expect(clippy::too_many_arguments, reason = "Native client adapter keeps launch and planning controls explicit")]
pub(crate) fn invoke_projection_client(
    project: crate::source_projection::catalog_owned_project::CatalogOwnedProject,
    profile: &str,
    java_home: Option<PathBuf>,
    explain_rebuild: bool,
    wait_for_build_lock: bool,
    dry_run: bool,
    kind: RunKind,
    run_options: &RunOptions,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    ensure_projection_smoke_supported(kind, project.receipt().files.contains_key(
        "src/main/java/ca/teamdman/sfm/client/handler/SFMClientSmokeRunHarness.java"
    ))?;
    let identity = match project.receipt().environment {
        crate::source_projection::projection_catalog::ProjectionEnvironment::Dev =>
            prepare_development_identity(project, profile)?,
        crate::source_projection::projection_catalog::ProjectionEnvironment::Release => {
            let recipe = format!("sfm:released-native-inputs/4.34.0/{}@1", project.receipt().target_id);
            let frozen = prepare_named_frozen_recipe_project(project, &recipe)?;
            validate_first_named_compile(&frozen)?;
            BuildProjectIdentity::Catalog { frozen: Arc::new(frozen) }
        }
    };
    let planning = NativePlanningOptions {
        mode: BuildMode::Build, java_home, refresh: false,
        allow_local_artifact_cache: false, artifact_sources: Vec::new(),
        require_portable_artifacts: true,
    };
    let plan = create_plan_for_project(&planning, &identity, cancellation)?;
    ensure_projection_client_plan(&plan)?;
    if run_options.client_hotswap_port.is_some() {
        crate::jdk::ensure_hotswap_runtime(&plan.java.version_output)?;
    }
    let path = build_cache_lock_path(&plan);
    let label = format!("{} client cache", plan.target_label());
    let lock = if wait_for_build_lock {
        ArtifactLock::acquire(&path, label)?
    } else {
        ArtifactLock::try_acquire(&path, label)?
            .ok_or_else(|| eyre::eyre!("projection client cache is already locked"))?
    };
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    write_last_plan_output(&plan)?;
    if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        let context = ExecutionContext::new(&plan, cancellation.clone())?;
        return with_named_neoform_context(&context, |consumer| {
            execute_project_build(consumer, BuildTarget::Run, Instant::now())?;
            execute_run_with_context(consumer, kind, run_options, dry_run)
        });
    }
    execute_build(&plan, explain_rebuild, BuildTarget::Run, cancellation)?;
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    if releases_build_cache_lock_before_launch(kind, run_options) {
        drop(lock);
    }
    execute_run(&plan, kind, run_options, dry_run, cancellation)
}

fn ensure_projection_client_plan(plan: &BuildPlan) -> eyre::Result<()> {
    eyre::ensure!(plan.identity.is_catalog_owned(), "client launch requires a catalog projection");
    if plan.loader_toolchain.kind != LoaderToolchainKind::NeoGradleUserdev {
        ensure_forge_gradle_execution_supported(plan)?;
    }
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()
}

fn ensure_projection_smoke_supported(kind: RunKind, has_harness: bool) -> eyre::Result<()> {
    eyre::ensure!(
        !matches!(kind, RunKind::ClientSmoke) || has_harness,
        "--smoke requires the smoke harness in the selected projection; launch this projection without --smoke"
    );
    Ok(())
}

#[cfg(test)]
mod projection_client_launch_tests {
    use super::*;

    #[test]
    fn historical_clients_need_no_smoke_hook_but_smoke_mode_does() {
        assert!(ensure_projection_smoke_supported(RunKind::Client, false).is_ok());
        assert!(ensure_projection_smoke_supported(RunKind::ClientSmoke, false).is_err());
        assert!(ensure_projection_smoke_supported(RunKind::ClientSmoke, true).is_ok());
    }
}

pub(crate) fn invoke_development_project(
    project: crate::source_projection::catalog_owned_project::CatalogOwnedProject,
    profile: &str,
    options: &NamedCompileOptions,
    package: bool,
    cancellation: &CancellationToken,
) -> eyre::Result<DevelopmentBuildReport> {
    let identity = prepare_development_identity(project, profile)?;
    let preparation = identity
        .development_target()
        .ok_or_else(|| eyre::eyre!("development identity lost its profile receipt"))?
        .receipt
        .clone();
    let planning = NativePlanningOptions {
        mode: BuildMode::Build,
        java_home: Some(options.java_home.clone()),
        refresh: false,
        allow_local_artifact_cache: false,
        artifact_sources: Vec::new(),
        require_portable_artifacts: true,
    };
    let plan = create_plan_for_project(&planning, &identity, cancellation)?;
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    let path = build_cache_lock_path(&plan);
    let label = format!("{} development native cache", plan.target_label());
    let _lock = if options.wait_for_build_lock {
        ArtifactLock::acquire(&path, label)?
    } else {
        ArtifactLock::try_acquire(&path, label)?
            .ok_or_else(|| eyre::eyre!("development native cache is already locked"))?
    };
    plan.recheck_named_inputs()?;
    write_last_plan_output(&plan)?;
    execute_build(
        &plan,
        options.explain_rebuild,
        if package {
            BuildTarget::Jar
        } else {
            BuildTarget::Compile
        },
        cancellation,
    )?;
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    Ok(DevelopmentBuildReport {
        schema: "sfm:development_native_build@1".to_owned(),
        preparation,
        java_release: plan.java_release,
        tool_jvm_major: plan.java.major_version,
        actual_sdk_identity: plan.java.cache_identity(),
        cache_dir: plan.cache_dir.clone(),
        output_jar: plan.rust_output_jar.clone(),
        compilation: "completed".to_owned(),
        jar_packaging: if package {
            "completed"
        } else {
            "not_performed"
        }
        .to_owned(),
        application_execution: "not_performed".to_owned(),
        immutable_source_lock: true,
    })
}

#[cfg(test)]
mod development_project_tests {
    use super::*;
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    use crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE;

    #[cfg(windows)]
    #[test]
    #[ignore = "manual tracing capture; the normal matrix test covers this workflow"]
    fn profile_development_matrix() -> eyre::Result<()> {
        crate::logging::init_logging(
            &crate::logging::LoggingConfig::new(
                tracing::level_filters::LevelFilter::INFO,
                std::env::var_os("SFM_TEST_LOG_FILE").map(std::path::PathBuf::from),
            ),
            &crate::cancellation::CancellationToken::new(),
        )?;
        six_neoform_development_targets_enter_their_own_checked_native_identity()
    }

    #[cfg(windows)]
    #[test]
    fn six_neoform_development_targets_enter_their_own_checked_native_identity() -> eyre::Result<()> {
        let cases: [(&str, &str, &str, &[u8]); 6] = [
            ("1.20.2", "1.20.2", "20.2.86", include_bytes!("../../../../minecraft/core-liquid-template/build/lockfiles/1.20.2/schema-4.json")),
            ("1.20.3", "1.20.3", "20.3.8-beta", include_bytes!("../../../../minecraft/core-liquid-template/build/lockfiles/1.20.3/schema-4.json")),
            ("1.20.4", "1.20.4", "20.4.231", include_bytes!("../../../../minecraft/core-liquid-template/build/lockfiles/1.20.4/schema-4.json")),
            ("1.21.0", "1.21", "21.0.143", include_bytes!("../../../../minecraft/core-liquid-template/build/lockfiles/1.21.0/schema-4.json")),
            ("1.21.1", "1.21.1", "21.1.206", include_bytes!("../../../../minecraft/core-liquid-template/build/lockfiles/1.21.1/schema-4.json")),
            ("26.1.2", "26.1.2", "26.1.2.72", include_bytes!("../../../../minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-4.json")),
        ];
        let mut fixture = Fixture::new();
        let supplement_root = fixture.repository().join(
            "platform/minecraft/core-liquid-template/build/supplements",
        );
        std::fs::create_dir_all(&supplement_root)?;
        std::fs::write(supplement_root.join("nfrt-child-identities.json"), include_bytes!(
            "../../../../minecraft/core-liquid-template/build/supplements/nfrt-child-identities.json"
        ))?;
        std::fs::write(supplement_root.join("nfrt-source-library-identities.json"), include_bytes!(
            "../../../../minecraft/core-liquid-template/build/supplements/nfrt-source-library-identities.json"
        ))?;
        for (index, (target, minecraft, loader, raw)) in cases.into_iter().enumerate() {
            fixture.set_project_file_for(target, "sfm-toolchain.lock.json", &format!("build/proof/{target}/lock.json"), raw, false)?;
            fixture.set_project_file_for(target, "gradle.properties", &format!("build/proof/{target}/gradle.properties"), format!("minecraft_version={minecraft}\nneo_version={loader}\nmod_version=4.34.0\n").as_bytes(), false)?;
            let key = Fixture::key(index + 4, "dev");
            fixture.publish(&key);
            let project = fixture.collect(&key)?.check_current()?;
            let identity = prepare_development_identity(project, NATIVE_DEPENDENCY_PROFILE)?;
            identity.recheck()?;
            assert!(identity.named_project().is_none());
            assert!(identity.legacy_branch().is_none());
            assert_eq!(identity.development_target().unwrap().receipt.minecraft_version, minecraft);
            assert_eq!(identity.development_target().unwrap().receipt.lockfile_sha256, crate::source_projection::provenance::sha256(raw));
            assert!(!identity.minecraft_dir().join("build").exists());
        }
        assert!(crate::source_projection::native_project_target::development_native_loader("1.21.0", "1.21.0").is_err());
        Ok(())
    }

    #[test]
    fn remaining_forge_development_targets_keep_their_captured_locks() -> eyre::Result<()> {
        let cases: [(&str, &str, &[u8]); 3] = [
            (
                "1.19.4",
                "45.0.42",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.4/schema-4.json"
                ),
            ),
            (
                "1.20",
                "46.0.10",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.20/schema-4.json"
                ),
            ),
            (
                "1.20.1",
                "47.1.65",
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.1/schema-4.json"
                ),
            ),
        ];
        let mut fixture = Fixture::new();
        for (index, (target, loader_version, lock)) in cases.into_iter().enumerate() {
            fixture.set_project_file_for(
                target,
                "sfm-toolchain.lock.json",
                &format!("build/proof/{target}/dev-lock.json"),
                lock,
                false,
            )?;
            fixture.set_project_file_for(
                target,
                "gradle.properties",
                &format!("build/proof/{target}/gradle.properties"),
                format!(
                    "minecraft_version={target}\nneo_version={loader_version}\nmod_version=4.34.0\n"
                )
                .as_bytes(),
                false,
            )?;
            let key = Fixture::key(index + 1, "dev");
            fixture.publish(&key);
            let checked = fixture.collect(&key)?.check_current()?;
            let identity = prepare_development_identity(checked, NATIVE_DEPENDENCY_PROFILE)?;
            identity.recheck()?;
            let target_receipt = &identity.development_target().unwrap().receipt;
            assert_eq!(target_receipt.minecraft_version, target);
            assert_eq!(
                target_receipt.lockfile_sha256,
                crate::source_projection::provenance::sha256(lock)
            );
            assert!(identity.legacy_branch().is_none());
            assert!(identity.named_project().is_none());
            assert!(!identity.minecraft_dir().join("build").exists());
        }
        Ok(())
    }

    #[test]
    fn checked_development_owner_keeps_profile_cache_and_mutation_guards() -> eyre::Result<()> {
        let fixture = Fixture::new();
        let raw = include_bytes!(
            "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-4.json"
        );
        let mut fixture = fixture;
        fixture.set_project_file_for(
            "1.19.2",
            "sfm-toolchain.lock.json",
            "build/proof/dev-lock.json",
            raw,
            false,
        )?;
        let key = Fixture::key(0, "dev");
        fixture.publish(&key);
        let checked = fixture.collect(&key)?.check_current()?;
        let identity = prepare_development_identity(checked, NATIVE_DEPENDENCY_PROFILE)?;
        assert!(identity.is_catalog_owned());
        assert!(identity.legacy_branch().is_none());
        assert!(identity.named_project().is_none());
        assert!(identity.development_target().is_some());
        identity.recheck()?;
        if let BuildProjectIdentity::Development { project, target } = &identity {
            let mut changed = target.as_ref().clone();
            changed.cache_identity.push('0');
            assert!(changed.recheck_with_project(project).is_err());
        }
        let root = identity.minecraft_dir();
        assert!(!root.join("build").exists());
        fs::write(root.join("sfm-toolchain.lock.json"), b"{}")?;
        assert!(identity.recheck().is_err());
        Ok(())
    }

    #[test]
    fn release_owner_cannot_enter_development_execution() -> eyre::Result<()> {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        fixture.publish(&key);
        let checked = fixture.collect(&key)?.check_current()?;
        assert!(prepare_development_identity(checked, NATIVE_DEPENDENCY_PROFILE).is_err());
        Ok(())
    }
}
