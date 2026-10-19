use super::*;
use crate::source_projection::catalog_owned_project::tests::Fixture;
use std::cell::Cell;

fn fixture() -> eyre::Result<(Fixture, Arc<retained_compile_inputs::RetainedCompileInputs>)> {
    let (fixture, slot, recipe, _) = named_forge_compile_cohort_tests::fixture("1.19.2")?;
    for (relative, bytes) in [
        (
            "src/main/resources/retained.txt",
            b"admitted resource".as_slice(),
        ),
        (
            "src/gametest/resources/test.txt",
            b"admitted optional".as_slice(),
        ),
        (
            "src/gametest/java/Gametest.java",
            b"class Gametest {}".as_slice(),
        ),
        (
            "src/datagen/java/Datagen.java",
            b"class Datagen {}".as_slice(),
        ),
        ("src/main/antlr/sfml/SFML.g4", b"grammar SFML;".as_slice()),
        (
            "src/main/antlr/toml/TomlLexer.g4",
            b"lexer grammar TomlLexer;".as_slice(),
        ),
        (
            "src/main/antlr/toml/TomlParser.g4",
            b"parser grammar TomlParser;".as_slice(),
        ),
    ] {
        write_fixture_source(&fixture, relative, bytes)?;
    }
    // This consumer admits only the three baseline grammars and the optional
    // complete ANTLRv4 pair. Remove the generic fixture's unrelated toy grammar.
    fs::remove_file(
        fixture
            .repository()
            .join(crate::source_projection::core_inputs::CORE_ROOT)
            .join("src/main/antlr/fixture/Fixture.g4"),
    )?;
    let frozen = named_forge_compile_cohort_tests::prepared(&fixture, slot, "release", &recipe)?;
    Ok((
        fixture,
        Arc::new(retained_compile_inputs::RetainedCompileInputs::admit(
            Arc::new(frozen),
        )?),
    ))
}

fn write_fixture_source(fixture: &Fixture, relative: &str, bytes: &[u8]) -> eyre::Result<()> {
    // src/... is selected by the existing source inventory rules. Explicit
    // project_files entries must never shadow that source namespace.
    let path = fixture
        .repository()
        .join(crate::source_projection::core_inputs::CORE_ROOT)
        .join(relative);
    fs::create_dir_all(path.parent().unwrap())?;
    fs::write(path, bytes)?;
    Ok(())
}

pub(super) fn plan(
    inputs: &Arc<retained_compile_inputs::RetainedCompileInputs>,
    scratch: &Path,
) -> BuildPlan {
    let mut plan = super::tests::minimal_plan_for_paths();
    plan.minecraft_dir = dunce::simplified(inputs.project().project().project_root()).to_path_buf();
    plan.cache_dir = scratch.join("cache");
    plan.state_dir = plan.cache_dir.join("state");
    plan.properties = inputs.properties().unwrap();
    plan.identity = BuildProjectIdentity::CatalogAdmitted {
        inputs: Arc::clone(inputs),
    };
    plan
}

pub(super) fn context(plan: &BuildPlan) -> ExecutionContext<'_> {
    // No SDK discovery or child is part of these isolated consumer tests.
    ExecutionContext {
        plan,
        forbidden_input_roots: Vec::new(),
        cancellation_token: CancellationToken::new(),
        minecraft_libraries_cache: Mutex::new(None),
        held_neoform_compile_jar: None,
        immutable_neoform_profile: false,
        verify_compilation_outputs: false,
        compiler_image_identity: None,
        #[cfg(any(windows, target_os = "linux"))]
        compiler_image: None,
    }
}

#[test]
fn all_hit_typed_miss_rechecks_live_source_before_strict_effect() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let strict = Cell::new(0);
    let result = all_hit_or_strict_compile(
        inputs.project(),
        || Err(all_hit_cache_miss::AllHitCacheMiss::new("synthetic real cache miss").into()),
        || {
            strict.set(strict.get() + 1);
            Ok(7)
        },
    )?;
    assert_eq!(result, 7);
    assert_eq!(strict.get(), 1);

    let source = inputs
        .project()
        .project()
        .project_root()
        .join("src/main/java/Shared.java");
    fs::write(source, b"class ChangedBeforeStrictFallback {}")?;
    let result = all_hit_or_strict_compile(
        inputs.project(),
        || Err(all_hit_cache_miss::AllHitCacheMiss::new("cache miss after edit").into()),
        || {
            strict.set(strict.get() + 1);
            Ok(9)
        },
    );
    assert!(result.is_err());
    assert_eq!(
        strict.get(),
        1,
        "stale admitted input cannot authorize strict launch"
    );
    Ok(())
}

#[test]
fn all_hit_authority_or_cancellation_errors_do_not_retry_strict() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let strict = Cell::new(false);
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("synthetic cancellation before cache probe");
    let result = all_hit_or_strict_compile(
        inputs.project(),
        || cancellation.bail_if_cancelled(),
        || {
            strict.set(true);
            Ok(())
        },
    );
    assert!(result.is_err());
    assert!(!strict.get());
    let properties = inputs
        .project()
        .project()
        .project_root()
        .join("gradle.properties");
    fs::write(properties, b"minecraft_version=changed\n")?;
    let result = all_hit_or_strict_compile(
        inputs.project(),
        || inputs.recheck(),
        || {
            strict.set(true);
            Ok(())
        },
    );
    assert!(result.is_err());
    assert!(!strict.get());
    Ok(())
}

#[test]
fn all_hit_result_needs_final_full_currentness_before_success() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let strict = Cell::new(false);
    let result = all_hit_or_strict_compile(
        inputs.project(),
        || {
            fs::write(
                inputs
                    .project()
                    .project()
                    .project_root()
                    .join("src/main/java/Shared.java"),
                b"class LateEdit {}",
            )?;
            inputs.recheck()?;
            Ok("validated cached result")
        },
        || {
            strict.set(true);
            Ok("strict result")
        },
    );
    assert!(
        result.is_err(),
        "cached result cannot publish current-source success after a body edit"
    );
    assert!(!strict.get());
    Ok(())
}

#[test]
fn all_hit_transform_refuses_before_artifact_lookup_or_work_directory() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let scratch = tempfile::tempdir()?;
    let plan = plan(&inputs, scratch.path());
    let context = context(&plan);
    let work = scratch.path().join("must-not-create");
    let error = context
        .run_java_tool("missing-tool", &[], &[], &work)
        .unwrap_err();
    assert!(
        error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_some()
    );
    assert!(!work.exists());
    Ok(())
}

#[test]
fn all_hit_unbound_optional_compile_preserves_output_and_never_launches() -> eyre::Result<()> {
    let (owner, _, recipe, _) = named_forge_compile_cohort_tests::fixture("1.19.2")?;
    write_fixture_source(&owner, "src/gametest/java/Test.java", b"class Test {}")?;
    let slot = crate::source_projection::projection_catalog::SUPPORTED_TARGETS
        .iter()
        .position(|(version, _)| *version == "1.19.2")
        .unwrap();
    let frozen = named_forge_compile_cohort_tests::prepared(&owner, slot, "release", &recipe)?;
    let inputs = Arc::new(retained_compile_inputs::RetainedCompileInputs::admit(
        Arc::new(frozen),
    )?);
    let scratch = tempfile::tempdir()?;
    let plan = plan(&inputs, scratch.path());
    let context = context(&plan);
    let output = plan.cache_dir.join("project/gametest-classes");
    fs::create_dir_all(&output)?;
    fs::write(output.join("retained.sentinel"), b"old cache evidence")?;
    let error = compile_optional_java_source_set(
        &context,
        "gametest",
        &[],
        &plan.cache_dir.join("project/classes"),
        &output,
        "synthetic-upstream",
        None,
    )
    .unwrap_err();
    assert!(
        error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_some(),
        "{error:#}"
    );
    assert_eq!(
        fs::read(output.join("retained.sentinel"))?,
        b"old cache evidence"
    );
    Ok(())
}

#[test]
fn all_hit_per_set_and_grammar_fingerprints_match_live_then_use_admitted_bytes() -> eyre::Result<()>
{
    let (_owner, inputs) = fixture()?;
    let scratch = tempfile::tempdir()?;
    let admitted_plan = plan(&inputs, scratch.path());
    let mut live_plan = plan(&inputs, scratch.path());
    live_plan.identity = BuildProjectIdentity::Worktree {
        branch: BranchName::from("1.19.2"),
        root: inputs.project().project().repo_root().to_path_buf(),
    };
    let admitted = context(&admitted_plan);
    let live = context(&live_plan);
    let grammar_paths = antlr_grammars(&admitted)?;
    let grammar_before = input_fingerprint(&live, "antlr-main", &grammar_paths, &[])?;
    assert_eq!(
        input_fingerprint(&admitted, "antlr-main", &grammar_paths, &[])?,
        grammar_before
    );
    fs::write(&grammar_paths[0], b"grammar ChangedAfterAdmission;")?;
    assert_eq!(
        input_fingerprint(&admitted, "antlr-main", &grammar_paths, &[])?,
        grammar_before
    );
    assert_ne!(
        input_fingerprint(&live, "antlr-main", &grammar_paths, &[])?,
        grammar_before
    );
    for source_set in ["main", "gametest", "datagen"] {
        let root = admitted_plan
            .minecraft_dir
            .join("src")
            .join(source_set)
            .join("java");
        let sources = inputs.selected_paths(&root, "java")?.unwrap();
        assert!(!sources.is_empty());
        let label = format!("javac-{source_set}");
        let before = input_fingerprint(&live, &label, &sources, &[])?;
        assert_eq!(input_fingerprint(&admitted, &label, &sources, &[])?, before);
        fs::write(&sources[0], format!("class Changed{source_set} {{}}"))?;
        assert_eq!(input_fingerprint(&admitted, &label, &sources, &[])?, before);
        assert_ne!(input_fingerprint(&live, &label, &sources, &[])?, before);
    }
    assert!(inputs.project().recheck(false).is_err());
    Ok(())
}

#[test]
fn all_hit_resolver_miss_does_not_create_artifact_lock_or_cache() -> eyre::Result<()> {
    let scratch = tempfile::tempdir()?;
    let cache = scratch.path().join("must-not-create");
    let resolver = Resolver::new(
        cache.clone(),
        Vec::new(),
        false,
        false,
        Vec::new(),
        None,
        None,
        CancellationToken::new(),
    )?
    .existing_only(true);
    for coordinate in ["example.invalid:absent:1", "example.invalid:absent:1.+"] {
        let error = resolver
            .resolve_artifact(
                ArtifactId::from("unavailable"),
                &MavenCoordinate::parse(coordinate)?,
                ArtifactPurpose::from("existing-only synthetic refusal"),
            )
            .unwrap_err();
        assert!(
            error
                .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
                .is_some(),
            "{error:#}"
        );
        assert!(!cache.exists());
    }
    Ok(())
}

#[test]
fn all_hit_unbound_antlr_refuses_before_legacy_marker_or_output_changes() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let scratch = tempfile::tempdir()?;
    let plan = plan(&inputs, scratch.path());
    let context = context(&plan);
    let output = plan.cache_dir.join("generated-antlr");
    fs::create_dir_all(&output)?;
    fs::write(output.join("retained.sentinel"), b"old output evidence")?;
    let state = output.with_extension("inputs.sha1");
    fs::write(&state, b"legacy-marker")?;
    let error = run_antlr(&context, &[], &output).unwrap_err();
    assert!(
        error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_some()
    );
    assert_eq!(fs::read(&state)?, b"legacy-marker");
    assert_eq!(
        fs::read(output.join("retained.sentinel"))?,
        b"old output evidence"
    );
    Ok(())
}

#[test]
fn all_hit_parallel_errors_preserve_authority_over_wrapped_misses() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let effects = Cell::new(0);
    let miss = || -> eyre::Result<()> {
        Err::<(), eyre::Report>(
            all_hit_cache_miss::AllHitCacheMiss::new("parallel compile cache miss").into(),
        )
        .wrap_err("wrapped compiler result")
    };
    let authority = || -> eyre::Result<()> {
        Err(eyre::eyre!("fresh resource authority refused")).wrap_err("wrapped resource result")
    };
    for joined in [false, true] {
        for miss_first in [false, true] {
            let result = all_hit_or_strict_compile(
                inputs.project(),
                || {
                    let (first, second) = if miss_first {
                        (miss(), authority())
                    } else {
                        (authority(), miss())
                    };
                    if joined {
                        all_hit_cache_miss::pair_results(first, second).map(|_| ())
                    } else {
                        all_hit_cache_miss::collect_results(vec![first, second]).map(|_| ())
                    }
                },
                || {
                    effects.set(effects.get() + 1);
                    Ok(())
                },
            );
            let error = result.unwrap_err();
            assert!(
                error
                    .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
                    .is_none()
            );
            assert!(format!("{error:#}").contains("fresh resource authority refused"));
            assert_eq!(
                effects.get(),
                0,
                "parallel authority error must forbid strict retry"
            );
        }
    }
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("parallel cancellation must forbid retry");
    let result = all_hit_or_strict_compile(
        inputs.project(),
        || {
            all_hit_cache_miss::collect_results(vec![miss(), cancellation.bail_if_cancelled()])
                .map(|_| ())
        },
        || {
            effects.set(effects.get() + 1);
            Ok(())
        },
    );
    assert!(result.is_err());
    assert_eq!(effects.get(), 0);
    Ok(())
}

#[test]
fn all_hit_parallel_misses_only_still_retry_and_success_order_is_preserved() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let effects = Cell::new(0);
    let miss = || -> eyre::Result<()> {
        Err::<(), eyre::Report>(
            all_hit_cache_miss::AllHitCacheMiss::new("expected parallel cache miss").into(),
        )
        .wrap_err("wrapped miss")
    };
    for joined in [false, true] {
        all_hit_or_strict_compile(
            inputs.project(),
            || {
                if joined {
                    all_hit_cache_miss::pair_results(miss(), miss()).map(|_| ())
                } else {
                    all_hit_cache_miss::collect_results(vec![miss(), miss()]).map(|_| ())
                }
            },
            || {
                effects.set(effects.get() + 1);
                Ok(())
            },
        )?;
    }
    assert_eq!(effects.get(), 2);
    assert_eq!(
        all_hit_cache_miss::collect_results(vec![Ok(4), Ok(1)])?,
        vec![4, 1]
    );
    assert_eq!(
        all_hit_cache_miss::pair_results(Ok(4), Ok("second"))?,
        (4, "second")
    );
    Ok(())
}

#[test]
fn all_hit_resolver_batches_do_not_hide_later_bad_sidecar_with_earlier_miss() -> eyre::Result<()> {
    let scratch = tempfile::tempdir()?;
    let cache = scratch.path().join("cache");
    let resolver = Resolver::new(
        cache,
        Vec::new(),
        false,
        false,
        Vec::new(),
        None,
        None,
        CancellationToken::new(),
    )?
    .existing_only(true);
    let missing = MavenCoordinate::parse("example.invalid:missing:1")?;
    let invalid = MavenCoordinate::parse("example.invalid:invalid:1")?;
    let missing_path = resolver.cache_path_for(&missing);
    let invalid_path = resolver.cache_path_for(&invalid);
    fs::create_dir_all(invalid_path.parent().unwrap())?;
    fs::write(&invalid_path, b"synthetic artifact, never executed")?;
    let sidecar = artifact_provenance_path(&invalid_path)?;
    fs::write(&sidecar, b"invalid provenance JSON")?;
    let purpose = ArtifactPurpose::from("parallel existing-only refusal");
    let artifact_error = resolver
        .resolve_artifacts([
            (
                ArtifactId::from("missing"),
                missing.clone(),
                purpose.clone(),
            ),
            (ArtifactId::from("invalid"), invalid.clone(), purpose),
        ])
        .unwrap_err();
    assert!(
        artifact_error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_none(),
        "{artifact_error:#}"
    );
    let dependency_error = resolver
        .resolve_dependencies([
            ("implementation".to_owned(), missing),
            ("implementation".to_owned(), invalid),
        ])
        .unwrap_err();
    assert!(
        dependency_error
            .downcast_ref::<all_hit_cache_miss::AllHitCacheMiss>()
            .is_none(),
        "{dependency_error:#}"
    );
    assert!(!missing_path.exists());
    assert_eq!(fs::read(sidecar)?, b"invalid provenance JSON");
    Ok(())
}

#[test]
fn all_hit_resource_staging_uses_admitted_bytes_and_final_check_rejects_edit() -> eyre::Result<()> {
    let (_owner, inputs) = fixture()?;
    let scratch = tempfile::tempdir()?;
    let plan = plan(&inputs, scratch.path());
    let context = context(&plan);
    fs::create_dir_all(&plan.cache_dir)?;
    fs::write(
        plan.minecraft_dir.join("src/main/resources/retained.txt"),
        b"live edit",
    )?;
    fs::write(
        plan.minecraft_dir.join("src/gametest/resources/test.txt"),
        b"live optional edit",
    )?;
    let staged = plan.cache_dir.join("staged");
    stage_resource_root(
        &context,
        &plan.minecraft_dir.join("src/main/resources"),
        &staged,
        &mut BTreeSet::new(),
    )?;
    stage_optional_resource_source_set(
        &context,
        "gametest",
        &plan.cache_dir.join("optional"),
        &[],
    )?;
    assert_eq!(fs::read(staged.join("retained.txt"))?, b"admitted resource");
    assert_eq!(
        fs::read(plan.cache_dir.join("optional/test.txt"))?,
        b"admitted optional"
    );
    assert!(inputs.project().recheck(false).is_err());
    Ok(())
}
