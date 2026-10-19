//! Synthetic ordinary-release consumers; inert SDK files are never executed.
#![cfg(windows)]

use super::*;
use crate::jar_build::local_dependency_image::LocalDependencyImage;
use crate::source_projection::catalog_owned_project::tests::Fixture;
use crate::source_projection::released_native_inputs::BUILD_CONFIGURATION_PATH;
use eyre::Result;
use std::cell::Cell;

const TARGET: &str = "1.20.3";
const OPTIONAL_SOURCE: &str = "src/test/java/OrdinaryReleaseOptional.java";
const UPSTREAM: &str = "ordinary-release-synthetic-upstream";

fn release_inputs() -> Result<(Fixture, Arc<retained_compile_inputs::RetainedCompileInputs>)> {
    let (owner, slot, recipe) =
        crate::source_projection::frozen_recipe_project::tests::fixture_for_target(TARGET)?;
    let test = owner
        .repository()
        .join(crate::source_projection::core_inputs::CORE_ROOT)
        .join(OPTIONAL_SOURCE);
    fs::create_dir_all(test.parent().unwrap())?;
    fs::write(&test, b"class OrdinaryReleaseOptional {}\n")?;
    let frozen = named_forge_compile_cohort_tests::prepared(&owner, slot, "release", &recipe)?;
    assert_eq!(
        frozen.receipt().released_inputs.loader_kind,
        "neogradle_userdev"
    );
    assert_eq!(frozen.receipt().ownership.minecraft_version, TARGET);
    assert_eq!(frozen.receipt().ownership.target_id, TARGET);
    assert_eq!(frozen.receipt().role_bindings.len(), 13);
    let inputs = retained_compile_inputs::RetainedCompileInputs::admit(Arc::new(frozen))?;
    Ok((owner, Arc::new(inputs)))
}

fn release_plan(
    inputs: &Arc<retained_compile_inputs::RetainedCompileInputs>,
    scratch: &Path,
) -> Result<BuildPlan> {
    let mut plan = all_hit_compile_tests::plan(inputs, scratch);
    let receipt = inputs.project().receipt();
    plan.mode = "build".to_owned();
    plan.schema_version = 2;
    plan.branch_name = None;
    plan.worktree_path = None;
    plan.minecraft_version = MinecraftVersion::parse(TARGET)?;
    plan.common_cache_dir = scratch.join("common-cache");
    plan.maven_cache_dir = scratch.join("maven-cache");
    plan.minecraft_cache_dir = scratch.join("minecraft-cache");
    plan.loader_toolchain.kind = LoaderToolchainKind::NeoGradleUserdev;
    plan.loader_toolchain.base_coordinate = receipt.released_inputs.loader_coordinate.clone();
    plan.java_release = u32::from(receipt.compiler_release);
    plan.catalog_project = Some(receipt.clone());
    plan.lockfile_path = plan.minecraft_dir.join("sfm-toolchain.lock.json");
    Ok(plan)
}

fn capture_inert_sdk(plan: &mut BuildPlan, home: &Path) -> Result<LocalDependencyImage> {
    for directory in ["bin", "lib", "conf"] {
        fs::create_dir_all(home.join(directory))?;
    }
    for relative in [
        "bin/java.exe",
        "bin/javac.exe",
        "release",
        "lib/modules",
        "bin/jvm.dll",
        "conf/jvm.cfg",
    ] {
        fs::write(
            home.join(relative),
            format!("inert fixture input: {relative}"),
        )?;
    }
    plan.java.home = Some(home.to_path_buf());
    plan.java.executable = home.join("bin/java.exe");
    plan.java.version_output = "synthetic tool Java 21; never executed".to_owned();
    plan.java.major_version = 21;
    plan.java.selection = "synthetic-test-only".to_owned();
    let identity = named_sdk_identity(&plan.java)?;
    plan.java.execution_identity = Some(identity.clone());
    verify_named_sdk_identity(&plan.java)?;
    LocalDependencyImage::capture_home(home, &identity, &CancellationToken::new())
}

fn bound_context<'a>(plan: &'a BuildPlan, image: &'a LocalDependencyImage) -> ExecutionContext<'a> {
    let mut context = all_hit_compile_tests::context(plan);
    context.verify_compilation_outputs = true;
    context.compiler_image_identity = Some(image.identity().to_owned());
    context.compiler_image = Some(image);
    context
}

fn optional_classes(plan: &BuildPlan) -> PathBuf {
    plan.cache_dir.join("project/test/classes")
}

fn optional_compile(context: &ExecutionContext<'_>) -> Result<()> {
    compile_optional_java_source_set(
        context,
        "test",
        &[],
        &context.plan.cache_dir.join("project/classes"),
        &optional_classes(context.plan),
        UPSTREAM,
        None,
    )
}

fn optional_fingerprint(context: &ExecutionContext<'_>) -> Result<String> {
    // The first actual optional compile writes this argfile before its typed
    // cache miss. Reuse production selectors and framing, then prove the exact
    // result with an actual successful optional cache-hit control.
    let mut paths = collect_source_set_java_sources(context, "test")?;
    assert_eq!(
        paths,
        vec![context.plan.minecraft_dir.join(OPTIONAL_SOURCE)]
    );
    paths.push(compilation_argfile_fingerprint_path(
        context,
        &context.plan.cache_dir.join("project/javac-test.args"),
    ));
    if let Some(exclusion) = source_exclusion_fingerprint_path(context, "test")? {
        paths.push(exclusion);
    }
    input_fingerprint(
        context,
        "javac-test",
        &paths,
        &[
            context.plan.java.cache_identity(),
            context.plan.java_release.to_string(),
            "test".to_owned(),
            UPSTREAM.to_owned(),
        ],
    )
}

fn record_optional_hit(context: &ExecutionContext<'_>) -> Result<()> {
    assert!(
        project_java_cache_reuse_allowed(context),
        "This fixture requires the production inherited-JVM reuse policy to admit its environment"
    );
    context.require_all_hit_compilation_binding()?;
    let classes = optional_classes(context.plan);
    fs::create_dir_all(&classes)?;
    fs::write(
        classes.join("retained.sentinel"),
        b"prior optional output evidence",
    )?;
    fs::write(
        context.plan.cache_dir.join("project/javac-test.log"),
        b"prior javac log evidence",
    )?;
    let miss = optional_compile(context).unwrap_err();
    assert!(
        miss.downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_some(),
        "{miss:#}"
    );
    let state = context
        .plan
        .cache_dir
        .join("project/javac-test.inputs.sha1");
    let fingerprint = optional_fingerprint(context)?;
    project_compilation_cache::record(
        &context.plan.cache_dir,
        &state,
        &fingerprint,
        &[("classes", &classes)],
        || context.bail_if_cancelled(),
    )?;
    optional_compile(context)?;
    Ok(())
}

fn assert_optional_evidence(plan: &BuildPlan) -> Result<()> {
    assert_eq!(
        fs::read(optional_classes(plan).join("retained.sentinel"))?,
        b"prior optional output evidence"
    );
    assert_eq!(
        fs::read(plan.cache_dir.join("project/javac-test.log"))?,
        b"prior javac log evidence"
    );
    Ok(())
}

fn authority_path(
    inputs: &retained_compile_inputs::RetainedCompileInputs,
    role: &str,
    authored: bool,
) -> Result<PathBuf> {
    let project = inputs.project().project();
    if role == "configuration" {
        assert!(authored);
        return Ok(project.repo_root().join(BUILD_CONFIGURATION_PATH));
    }
    let output = match role {
        "lock" => "sfm-toolchain.lock.json".to_owned(),
        "properties" => "gradle.properties".to_owned(),
        "exclusion" => format!("gradle/source-excludes/{TARGET}/test-java.txt"),
        _ => eyre::bail!("Unknown synthetic authority role"),
    };
    let selected = project.selected_input(&output)?;
    Ok(if authored {
        project.repo_root().join(selected.source_path())
    } else {
        project.project_root().join(selected.output_path())
    })
}

#[test]
fn ordinary_release_compile_scope_is_only_windows_build_run_compile_for_the_selected_recipe() {
    let compile = RunTestOptions {
        action: RunTestAction::Compile,
        ..Default::default()
    };
    let list = RunTestOptions {
        action: RunTestAction::List,
        ..Default::default()
    };
    let run = RunTestOptions::default();
    let operation = ProjectionOperation::Test(&compile);
    assert!(ordinary_release_compile_eligible(
        true,
        BuildMode::Build,
        false,
        operation,
        "neogradle_userdev",
        TARGET,
    ));
    for (windows, mode, dry_run) in [
        (false, BuildMode::Build, false),
        (true, BuildMode::Plan, false),
        (true, BuildMode::Build, true),
    ] {
        assert!(!ordinary_release_compile_eligible(
            windows,
            mode,
            dry_run,
            operation,
            "neogradle_userdev",
            TARGET,
        ));
    }
    for operation in [
        ProjectionOperation::Build,
        ProjectionOperation::Test(&list),
        ProjectionOperation::Test(&run),
    ] {
        assert!(!ordinary_release_compile_eligible(
            true,
            BuildMode::Build,
            false,
            operation,
            "neogradle_userdev",
            TARGET,
        ));
    }
    for target in ["1.20.2", "1.20.4", "1.21.0", "1.21.1", "26.1.2"] {
        assert!(!ordinary_release_compile_eligible(
            true,
            BuildMode::Build,
            false,
            operation,
            "neogradle_userdev",
            target,
        ));
    }
    for loader in ["forgegradle_forge", "forgegradle_neoforge", "unknown"] {
        assert!(!ordinary_release_compile_eligible(
            true,
            BuildMode::Build,
            false,
            operation,
            loader,
            TARGET,
        ));
    }
}

#[test]
fn ordinary_release_body_edits_keep_retained_inputs_but_refuse_snapshot_before_latest() -> Result<()>
{
    for authored in [true, false] {
        let (_owner, inputs) = release_inputs()?;
        let scratch = tempfile::tempdir()?;
        let plan = release_plan(&inputs, scratch.path())?;
        let context = all_hit_compile_tests::context(&plan);
        let source = plan.minecraft_dir.join("src/main/java/Shared.java");
        let selected = inputs
            .project()
            .project()
            .selected_input("src/main/java/Shared.java")?;
        let retained = selected.output_bytes().to_vec();
        let fingerprint =
            input_fingerprint(&context, "javac-main", std::slice::from_ref(&source), &[])?;
        let changed = if authored {
            inputs
                .project()
                .project()
                .repo_root()
                .join(selected.source_path())
        } else {
            source.clone()
        };
        fs::create_dir_all(&plan.state_dir)?;
        let latest = plan.state_dir.join("latest-successful-build.json");
        fs::write(&latest, b"prior successful release receipt")?;
        recheck_run_snapshot_source(&plan)?;
        fs::write(changed, b"class ChangedDuringReleaseAttempt {}\n")?;
        assert_eq!(inputs.bytes_at(&source)?, Some(retained.as_slice()));
        assert_eq!(
            input_fingerprint(&context, "javac-main", &[source], &[])?,
            fingerprint
        );
        plan.recheck_named_inputs()?;
        assert!(recheck_run_snapshot_source(&plan).is_err());
        assert!(publish_run_snapshot(&plan, &CancellationToken::new()).is_err());
        assert_eq!(fs::read(latest)?, b"prior successful release receipt");
        assert!(!plan.cache_dir.join("build-snapshots").exists());
    }
    Ok(())
}

#[test]
fn ordinary_release_all_hit_success_still_requires_full_currentness_without_a_snapshot()
-> Result<()> {
    let (_owner, inputs) = release_inputs()?;
    let scratch = tempfile::tempdir()?;
    let plan = release_plan(&inputs, scratch.path())?;
    let source = plan.minecraft_dir.join(OPTIONAL_SOURCE);
    let retained = inputs.bytes_at(&source)?.unwrap().to_vec();
    let strict = Cell::new(0);
    let result = all_hit_or_strict_compile(
        inputs.project(),
        || {
            fs::write(&source, b"class ChangedAfterCachedOptionalResult {}\n")?;
            plan.recheck_named_inputs()?;
            assert_eq!(inputs.bytes_at(&source)?, Some(retained.as_slice()));
            Ok("synthetic completed cache result without snapshot")
        },
        || {
            strict.set(strict.get() + 1);
            Ok("strict must not run")
        },
    );
    let error = result.unwrap_err();
    assert!(
        error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_none()
    );
    assert_eq!(strict.get(), 0);
    assert!(!plan.cache_dir.exists());
    Ok(())
}

#[test]
fn ordinary_release_raw_authority_changes_fail_cheap_checks_without_strict_retry() -> Result<()> {
    for (role, authored) in [
        ("lock", true),
        ("lock", false),
        ("configuration", true),
        ("exclusion", true),
        ("exclusion", false),
        ("properties", true),
        ("properties", false),
    ] {
        let (_owner, inputs) = release_inputs()?;
        let scratch = tempfile::tempdir()?;
        let plan = release_plan(&inputs, scratch.path())?;
        let path = authority_path(&inputs, role, authored)?;
        let mut changed = fs::read(&path)?;
        changed.push(b'\n');
        plan.recheck_named_inputs()?;
        fs::write(&path, &changed)?;
        assert!(
            plan.recheck_named_inputs().is_err(),
            "{role}, authored={authored}"
        );
        let strict = Cell::new(0);
        let error = all_hit_or_strict_compile(
            inputs.project(),
            || plan.recheck_named_inputs(),
            || {
                strict.set(strict.get() + 1);
                Ok(())
            },
        )
        .unwrap_err();
        assert!(
            error
                .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
                .is_none(),
            "{error:#}"
        );
        assert_eq!(strict.get(), 0);
        assert_eq!(fs::read(path)?, changed);
        assert!(!plan.cache_dir.exists());
    }
    Ok(())
}

#[test]
fn ordinary_release_optional_test_missing_or_corrupt_manifest_is_typed_miss_before_reset()
-> Result<()> {
    for corrupt in [false, true] {
        let (_owner, inputs) = release_inputs()?;
        let scratch = tempfile::tempdir()?;
        let mut plan = release_plan(&inputs, scratch.path())?;
        let image = capture_inert_sdk(&mut plan, &scratch.path().join("inert-sdk"))?;
        let context = bound_context(&plan, &image);
        record_optional_hit(&context)?;
        let state = plan.cache_dir.join("project/javac-test.inputs.sha1");
        let original_state = fs::read(&state)?;
        let manifest = state.with_extension("outputs.json");
        if corrupt {
            fs::write(&manifest, b"{ retained invalid output manifest")?;
        } else {
            fs::remove_file(&manifest)?;
        }
        let error = optional_compile(&context).unwrap_err();
        assert!(
            error
                .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
                .is_some(),
            "{error:#}"
        );
        assert_optional_evidence(&plan)?;
        assert_eq!(fs::read(state)?, original_state);
        if corrupt {
            assert_eq!(fs::read(manifest)?, b"{ retained invalid output manifest");
        } else {
            assert!(!manifest.exists());
        }
    }
    Ok(())
}

#[test]
fn ordinary_release_optional_manifest_miss_rechecks_body_before_any_strict_effect() -> Result<()> {
    let (_owner, inputs) = release_inputs()?;
    let scratch = tempfile::tempdir()?;
    let mut plan = release_plan(&inputs, scratch.path())?;
    let image = capture_inert_sdk(&mut plan, &scratch.path().join("inert-sdk"))?;
    let context = bound_context(&plan, &image);
    record_optional_hit(&context)?;
    fs::remove_file(
        plan.cache_dir
            .join("project/javac-test.inputs.outputs.json"),
    )?;
    let strict = Cell::new(0);
    let error = all_hit_or_strict_compile(
        inputs.project(),
        || {
            let miss = optional_compile(&context).unwrap_err();
            assert!(
                miss.downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
                    .is_some()
            );
            fs::write(
                plan.minecraft_dir.join(OPTIONAL_SOURCE),
                b"class EditBeforeRetry {}\n",
            )?;
            Err::<(), _>(miss)
        },
        || {
            strict.set(strict.get() + 1);
            Ok(())
        },
    )
    .unwrap_err();
    assert!(
        error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_none(),
        "{error:#}"
    );
    assert_eq!(strict.get(), 0);
    assert_optional_evidence(&plan)
}

#[test]
fn ordinary_release_optional_sdk_membership_failure_is_nonmiss_without_strict_retry() -> Result<()>
{
    let (_owner, inputs) = release_inputs()?;
    let scratch = tempfile::tempdir()?;
    let mut plan = release_plan(&inputs, scratch.path())?;
    let sdk = scratch.path().join("inert-sdk");
    let image = capture_inert_sdk(&mut plan, &sdk)?;
    let context = bound_context(&plan, &image);
    record_optional_hit(&context)?;
    fs::write(
        sdk.join("conf/added-during-attempt.cfg"),
        b"new actual selected SDK member",
    )?;
    let strict = Cell::new(0);
    let error = all_hit_or_strict_compile(
        inputs.project(),
        || optional_compile(&context),
        || {
            strict.set(strict.get() + 1);
            Ok(())
        },
    )
    .unwrap_err();
    assert!(
        error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_none(),
        "{error:#}"
    );
    assert!(format!("{error:#}").contains("SDK image gained a selected entry"));
    assert_eq!(strict.get(), 0);
    assert_optional_evidence(&plan)
}

#[test]
fn ordinary_release_optional_cancellation_preserves_manifest_outputs_and_argfile_without_retry()
-> Result<()> {
    let (_owner, inputs) = release_inputs()?;
    let scratch = tempfile::tempdir()?;
    let mut plan = release_plan(&inputs, scratch.path())?;
    let image = capture_inert_sdk(&mut plan, &scratch.path().join("inert-sdk"))?;
    let context = bound_context(&plan, &image);
    record_optional_hit(&context)?;
    let manifest = plan
        .cache_dir
        .join("project/javac-test.inputs.outputs.json");
    let before = fs::read(&manifest)?;
    let argfile = plan.cache_dir.join("project/javac-test.args");
    fs::write(
        &argfile,
        b"sentinel proving cancelled attempt cannot rewrite arguments",
    )?;
    context
        .cancellation_token
        .request_cancel("ordinary release optional cancellation");
    let strict = Cell::new(0);
    let error = all_hit_or_strict_compile(
        inputs.project(),
        || optional_compile(&context),
        || {
            strict.set(strict.get() + 1);
            Ok(())
        },
    )
    .unwrap_err();
    assert!(
        error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_none()
    );
    assert!(format!("{error:#}").contains("ordinary release optional cancellation"));
    assert_eq!(strict.get(), 0);
    assert_eq!(fs::read(manifest)?, before);
    assert_eq!(
        fs::read(argfile)?,
        b"sentinel proving cancelled attempt cannot rewrite arguments"
    );
    assert_optional_evidence(&plan)
}

#[test]
fn ordinary_release_retained_junit_run_and_list_refuse_before_any_run_state_or_sdk_binding()
-> Result<()> {
    let (_owner, inputs) = release_inputs()?;
    let scratch = tempfile::tempdir()?;
    let plan = release_plan(&inputs, scratch.path())?;
    let context = all_hit_compile_tests::context(&plan);
    assert!(plan.java.execution_identity.is_none());
    assert!(context.compiler_image.is_none());
    for action in [RunTestAction::Run, RunTestAction::List] {
        let options = RunTestOptions {
            action,
            ..Default::default()
        };
        for dry_run in [false, true] {
            let error = execute_junit_tests_with_context(&context, dry_run, &options).unwrap_err();
            assert!(
                error
                    .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
                    .is_some(),
                "{error:#}"
            );
            assert!(
                format!("{error:#}")
                    .contains("JUnit execution outside the compile-only cache attempt")
            );
            assert!(!plan.cache_dir.exists());
            assert!(!plan.maven_cache_dir.exists());
        }
    }
    Ok(())
}

#[test]
fn ordinary_release_optional_test_changed_output_is_typed_miss_before_compiler_effects()
-> Result<()> {
    for missing in [false, true] {
        let (_owner, inputs) = release_inputs()?;
        let scratch = tempfile::tempdir()?;
        let mut plan = release_plan(&inputs, scratch.path())?;
        let image = capture_inert_sdk(&mut plan, &scratch.path().join("inert-sdk"))?;
        let context = bound_context(&plan, &image);
        record_optional_hit(&context)?;
        let state = plan.cache_dir.join("project/javac-test.inputs.sha1");
        let manifest = state.with_extension("outputs.json");
        let evidence = (fs::read(&state)?, fs::read(&manifest)?);
        let class = optional_classes(&plan).join("retained.sentinel");
        if missing {
            fs::remove_file(&class)?;
        } else {
            fs::write(&class, b"edited real optional output")?;
        }
        let error = optional_compile(&context).unwrap_err();
        assert!(
            error
                .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
                .is_some(),
            "{error:#}"
        );
        assert_eq!(fs::read(state)?, evidence.0);
        assert_eq!(fs::read(manifest)?, evidence.1);
        assert_eq!(
            fs::read(plan.cache_dir.join("project/javac-test.log"))?,
            b"prior javac log evidence"
        );
        if missing {
            assert!(!class.exists());
        } else {
            assert_eq!(fs::read(class)?, b"edited real optional output");
        }
    }
    Ok(())
}
