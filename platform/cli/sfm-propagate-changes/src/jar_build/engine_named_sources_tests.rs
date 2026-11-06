//! Parser/admission and synthetic consumer mechanics, not public real-game receipts.
use super::*;
use crate::source_projection::frozen_recipe_project::FrozenRecipeProject;

fn plan(project: FrozenRecipeProject, root: &Path) -> BuildPlan {
    let mut plan = super::tests::minimal_plan_for_paths();
    plan.minecraft_dir = project.project().project_root().to_path_buf();
    plan.cache_dir = root.join("project-cache");
    plan.identity = BuildProjectIdentity::Catalog {
        frozen: Arc::new(project),
    };
    plan
}

#[test]
#[cfg(target_os = "linux")]
fn released_sources_admission_preserves_exact_recipe_owner_and_lock() -> eyre::Result<()> {
    let recipe = "sfm:released-native-inputs/4.34.0/1.20.2@1";
    preflight_named_source_output_recipe("1.20.2", "1.20.2", recipe)?;
    for (target, minecraft, selected) in [
        ("1.20.2", "1.20.3", recipe),
        ("1.20.3", "1.20.3", recipe),
        ("1.19.2", "1.19.2", recipe),
        (
            "1.20.2",
            "1.20.2",
            "sfm:released-native-inputs/4.34.0/1.20.2@2",
        ),
    ] {
        assert!(preflight_named_source_output_recipe(target, minecraft, selected).is_err());
    }
    for environment in ["release", "dev"] {
        let (_fixture, project) =
            super::super::nfrt_launch_contract::tests::project_fixture("1.20.2", environment)?;
        assert_eq!(
            validate_named_source_output_project(&project).is_ok(),
            environment == "release"
        );
    }
    for changed in ["sfm-toolchain.lock.json", "src/main/java/Shared.java"] {
        let (_fixture, project) =
            super::super::nfrt_launch_contract::tests::project_fixture("1.20.2", "release")?;
        validate_named_source_output_project(&project)?;
        fs::write(
            project.project().project_root().join(changed),
            b"changed after admission",
        )?;
        assert!(validate_named_source_output_project(&project).is_err());
    }
    // Source admission must not widen generic native build targets.
    assert!(require_named_native_target(true, BuildTarget::SourceOutputs).is_err());
    Ok(())
}

#[cfg(target_os = "linux")]
#[test]
fn released_source_consumer_reuses_verified_archive_and_tree_without_project_outputs()
-> eyre::Result<()> {
    use super::super::local_dependency_cache::linux::LocalDependencyCache;
    use super::super::nfrt_launch_contract::local_dependency_linux_tests as model;
    let (_fixture, project) =
        super::super::nfrt_launch_contract::tests::project_fixture("1.20.2", "release")?;
    let temp = tempfile::tempdir()?;
    let input = model::prepared("source-view-model");
    let cancel = CancellationToken::new();
    let mut cache =
        LocalDependencyCache::begin(&temp.path().join("cache"), model::key(&input), &cancel)?;
    model::produce(&mut cache, &input, temp.path())?;
    let archive = cache.paths(&cancel)?.1;
    cache.release_producer();
    let plan = plan(project, temp.path());
    let mut context = super::all_hit_compile_tests::context(&plan);
    context.held_linux_dependencies = Some(&cache);
    let jar = named_source_output_report(
        &context,
        &archive,
        SourceOutputLayout::Jar,
        NamedSourceOrigin::HistoricalLocalCache,
    )?;
    assert_eq!(jar.output, archive);
    assert!(!plan.cache_dir.exists());
    let first = named_source_output_report(
        &context,
        &archive,
        SourceOutputLayout::Filetree,
        NamedSourceOrigin::HistoricalLocalCache,
    )?;
    let java = first.output.join("net/minecraft/Example.java");
    let before = (fs::read(&java)?, fs::metadata(&java)?.modified()?);
    let next = named_source_output_report(
        &context,
        &archive,
        SourceOutputLayout::Filetree,
        NamedSourceOrigin::HistoricalLocalCache,
    )?;
    assert_eq!(first.output, next.output);
    assert_eq!(first.source_payload_sha256, next.source_payload_sha256);
    assert_eq!(before, (fs::read(&java)?, fs::metadata(&java)?.modified()?));
    assert_eq!(first.sfm_compilation, "not_performed");
    let json = facet_json::to_string(&first)?;
    assert!(json.contains("\"historical_local_cache\""));
    assert!(json.contains("\"sources_archive\""));
    assert!(json.contains(&first.output.display().to_string()));
    assert!(!plan.cache_dir.exists());
    let outside = temp.path().join("foreign.jar");
    std::os::unix::fs::symlink(&archive, &outside)?;
    assert!(
        named_source_output_report(
            &context,
            &outside,
            SourceOutputLayout::Jar,
            NamedSourceOrigin::HistoricalLocalCache
        )
        .is_err()
    );
    use std::os::unix::fs::PermissionsExt as _;
    fs::set_permissions(&java, fs::Permissions::from_mode(0o600))?;
    fs::write(&java, b"foreign edit")?;
    assert!(
        named_source_output_report(
            &context,
            &archive,
            SourceOutputLayout::Filetree,
            NamedSourceOrigin::HistoricalLocalCache
        )
        .is_err()
    );
    assert_eq!(fs::read(&java)?, b"foreign edit");
    context.cancellation_token.request_cancel("source test");
    assert!(
        named_source_output_report(
            &context,
            &archive,
            SourceOutputLayout::Jar,
            NamedSourceOrigin::HistoricalLocalCache
        )
        .is_err()
    );
    Ok(())
}

#[cfg(target_os = "linux")]
#[test]
fn released_source_consumer_rejects_unsafe_archive_without_extraction() -> eyre::Result<()> {
    use super::super::local_dependency_cache::linux::LocalDependencyCache;
    use super::super::nfrt_launch_contract::local_dependency_linux_tests as model;
    let (_fixture, project) =
        super::super::nfrt_launch_contract::tests::project_fixture("1.20.2", "release")?;
    let temp = tempfile::tempdir()?;
    let input = model::prepared("unsafe-source-view-model");
    let cancel = CancellationToken::new();
    let mut cache =
        LocalDependencyCache::begin(&temp.path().join("cache"), model::key(&input), &cancel)?;
    model::produce_with_source_entries(
        &mut cache,
        &input,
        temp.path(),
        &[("../escape.java", b"unsafe")],
    )?;
    let archive = cache.paths(&cancel)?.1;
    cache.release_producer();
    let plan = plan(project, temp.path());
    let mut context = super::all_hit_compile_tests::context(&plan);
    context.held_linux_dependencies = Some(&cache);
    for layout in [SourceOutputLayout::Jar, SourceOutputLayout::Filetree] {
        assert!(
            named_source_output_report(
                &context,
                &archive,
                layout,
                NamedSourceOrigin::HistoricalLocalCache
            )
            .is_err()
        );
    }
    assert!(
        !archive
            .parent()
            .unwrap()
            .join("source-filetree.json")
            .exists()
    );
    assert!(!temp.path().join("escape.java").exists());
    assert!(
        archive.exists(),
        "failed view preserves admitted model evidence"
    );
    Ok(())
}

#[cfg(target_os = "linux")]
#[test]
fn released_source_consumers_concurrently_publish_one_persistent_tree() -> eyre::Result<()> {
    use super::super::local_dependency_cache::linux::LocalDependencyCache;
    use super::super::nfrt_launch_contract::local_dependency_linux_tests as model;
    let (_fixture, project) =
        super::super::nfrt_launch_contract::tests::project_fixture("1.20.2", "release")?;
    let temp = tempfile::tempdir()?;
    let input = model::prepared("concurrent-source-view-model");
    let cancel = CancellationToken::new();
    let root = temp.path().join("cache");
    let mut producer = LocalDependencyCache::begin(&root, model::key(&input), &cancel)?;
    model::produce(&mut producer, &input, temp.path())?;
    producer.release_producer();
    drop(producer);
    // Finish fallible setup before the barrier: failures cannot strand a peer.
    let mut readers = Vec::new();
    for _ in 0..2 {
        let mut cache = LocalDependencyCache::begin(&root, model::key(&input), &cancel)?;
        let sources = cache.lookup(&input, &cancel)?.unwrap().1;
        cache.release_producer();
        readers.push((cache, sources));
    }
    let plan = plan(project, temp.path());
    let barrier = std::sync::Barrier::new(2);
    let results = std::thread::scope(|scope| {
        let tasks = readers
            .into_iter()
            .map(|(cache, sources)| {
                let plan = &plan;
                let barrier = &barrier;
                scope.spawn(move || {
                    let mut context = super::all_hit_compile_tests::context(plan);
                    context.held_linux_dependencies = Some(&cache);
                    barrier.wait();
                    named_source_output_report(
                        &context,
                        &sources,
                        SourceOutputLayout::Filetree,
                        NamedSourceOrigin::HistoricalLocalCache,
                    )
                })
            })
            .collect::<Vec<_>>();
        tasks
            .into_iter()
            .map(|task| task.join().unwrap())
            .collect::<eyre::Result<Vec<_>>>()
    })?;
    assert_eq!(results[0].output, results[1].output);
    assert_eq!(
        results[0].source_payload_sha256,
        results[1].source_payload_sha256
    );
    assert!(
        results[0]
            .output
            .join("net/minecraft/Example.java")
            .is_file()
    );
    assert!(!plan.cache_dir.exists());
    Ok(())
}
