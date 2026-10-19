use super::super::ContentHash;
use super::super::ContentHashAlgorithm;
use super::approved_pins::COMMON_PINS;
use super::approved_pins::LAUNCHER;
use super::source_artifact::SourceArtifact;
use super::source_dependency::SourceDependency;
use super::source_lock::SourceLock;
use super::supplement::Supplement;
use super::target_recipe::TargetRecipe;
use super::target_report::TargetReport;
use super::*;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering;

fn synthetic_pin(coordinate: &str, bytes: &[u8]) -> TestPin {
    TestPin {
        coordinate: coordinate.to_owned(),
        content_hash: ContentHash::from_bytes(bytes, ContentHashAlgorithm::Blake3).to_string(),
        full_sha256: Some(sha256(bytes)),
        bytes: Some(bytes.len() as u64),
        origin: None,
        authority: "reviewed_test_provision".to_owned(),
    }
}

fn target(pins: Vec<TestPin>, id: &str) -> TargetReport {
    TargetReport {
        projection_key: format!("sfm-4.35.0/mc-{id}"),
        target_id: id.to_owned(),
        minecraft_version: id.to_owned(),
        released_recipe_id: "synthetic".to_owned(),
        raw_source_lock_sha256: "synthetic".to_owned(),
        ordered_test_roots: Vec::new(),
        role_selection_identity: "synthetic".to_owned(),
        role_bindings: Vec::new(),
        selected_pin_identity: identity(&pins).unwrap(),
        selected_pins: pins,
        production_base_witnesses: Vec::new(),
        classpath_bindings: super::bindings::for_target(id),
        closure_complete: false,
    }
}

fn fixture() -> (TargetRecipe, SourceLock, Vec<TestPin>) {
    let pins: Vec<_> = COMMON_PINS
        .iter()
        .map(|(coordinate, hash)| TestPin {
            coordinate: (*coordinate).to_owned(),
            content_hash: (*hash).to_owned(),
            full_sha256: None,
            bytes: None,
            origin: None,
            authority: "unchanged_released_test_pin".to_owned(),
        })
        .collect();
    let mut lock = SourceLock {
        artifacts: pins
            .iter()
            .map(|p| SourceArtifact {
                coordinate: Some(p.coordinate.clone()),
                hash: p.content_hash.clone(),
            })
            .collect(),
        dependencies: Vec::new(),
    };
    lock.artifacts.push(SourceArtifact {
        coordinate: Some(super::approved_pins::BASE_JSR305.0.to_owned()),
        hash: super::approved_pins::BASE_JSR305.1.to_owned(),
    });
    let recipe = TargetRecipe {
        target: "1.19.2".to_owned(),
        minecraft_version: "1.19.2".to_owned(),
        recipe_id: "synthetic".to_owned(),
        source_lock: super::source_lock_reference::SourceLockReference {
            path: "lock.json".to_owned(),
            sha256: "synthetic".to_owned(),
        },
        role_input_hashes: Vec::new(),
        source_excludes: Vec::new(),
        released_gradle_supplements: roots::expected_roots("1.19.2")
            .into_iter()
            .map(|(role, coordinate)| Supplement {
                configuration: role.to_owned(),
                coordinate: coordinate.to_owned(),
                artifact_hash: pins
                    .iter()
                    .find(|p| p.coordinate == coordinate)
                    .unwrap()
                    .content_hash
                    .clone(),
            })
            .collect(),
    };
    (recipe, lock, pins)
}

#[test]
fn exact_roots_and_flat_closure_control() -> eyre::Result<()> {
    let (recipe, lock, mut pins) = fixture();
    let mut report = preflight::empty_report();
    let roots = roots::validate_target(&mut report, &recipe, &lock, &mut pins)?;
    assert_eq!(roots.len(), 7);
    assert_eq!(pins.len(), 24);
    assert!(report.issues.is_empty(), "{:?}", report.issues);
    Ok(())
}

#[test]
fn unknown_test_root_and_missing_test_pins_stay_test_only() -> eyre::Result<()> {
    let (mut recipe, mut lock, mut pins) = fixture();
    recipe.released_gradle_supplements[0].coordinate =
        "com.github.javaparser:javaparser-symbol-solver-core:99".to_owned();
    lock.artifacts.remove(0);
    lock.artifacts.remove(0);
    let mut report = preflight::empty_report();
    roots::validate_target(&mut report, &recipe, &lock, &mut pins)?;
    assert!(report.issues.len() >= 4, "{:?}", report.issues);
    assert!(report.issues.iter().all(|i| i.scope == "test_only"));
    assert!(
        report
            .issues
            .iter()
            .any(|i| i.code == "test_only_root_mismatch")
    );
    assert_eq!(
        report
            .issues
            .iter()
            .filter(|i| i.code == "test_only_missing_pin")
            .count(),
        2
    );
    Ok(())
}

#[test]
fn production_omission_is_distinct_from_test_pin_conflict() -> eyre::Result<()> {
    let (recipe, mut lock, mut pins) = fixture();
    lock.artifacts[0].hash = "blake3:0000000000000000000000000000000000000000".to_owned();
    lock.dependencies.push(SourceDependency {
        configuration: "implementation".to_owned(),
        resolved_notation: "example:main:1".to_owned(),
    });
    let mut report = preflight::empty_report();
    roots::validate_target(&mut report, &recipe, &lock, &mut pins)?;
    assert!(
        report
            .issues
            .iter()
            .any(|i| i.scope == "production" && i.code == "production_unpinned_dependency")
    );
    assert!(
        report
            .issues
            .iter()
            .any(|i| i.scope == "test_only" && i.code == "test_only_pin_conflict")
    );
    lock.artifacts.push(SourceArtifact {
        coordinate: lock.artifacts[0].coordinate.clone(),
        hash: "different".to_owned(),
    });
    assert!(
        authority::pin_map(&lock)
            .unwrap_err()
            .to_string()
            .contains("contradictory")
    );
    Ok(())
}

#[test]
fn target_26_launcher_retains_exact_existing_pin() -> eyre::Result<()> {
    let (mut recipe, mut lock, mut pins) = fixture();
    recipe.target = "26.1.2".to_owned();
    recipe.released_gradle_supplements.push(Supplement {
        configuration: "testRuntimeOnly".to_owned(),
        coordinate: LAUNCHER.0.to_owned(),
        artifact_hash: LAUNCHER.1.to_owned(),
    });
    lock.artifacts.push(SourceArtifact {
        coordinate: Some(LAUNCHER.0.to_owned()),
        hash: LAUNCHER.1.to_owned(),
    });
    let mut report = preflight::empty_report();
    roots::validate_target(&mut report, &recipe, &lock, &mut pins)?;
    assert!(report.issues.is_empty(), "{:?}", report.issues);
    assert_eq!(pins.len(), 25);
    Ok(())
}

#[test]
fn corrupt_source_authority_fails_before_cache_access() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let source = temp.path().join(SOURCE);
    std::fs::create_dir_all(source.parent().unwrap())?;
    std::fs::write(&source, b"mutated authority")?;
    let cache = temp.path().join("cache-must-not-exist");
    let report = preflight::run(temp.path(), &cache, None, &CancellationToken::default())?;
    assert!(
        report
            .issues
            .iter()
            .any(|i| i.code == "test_authority_invalid" && i.message.contains("hash mismatch"))
    );
    assert!(!report.closure_complete);
    assert!(!cache.exists());
    assert!(!report.writes_performed && !report.java_launched);
    Ok(())
}

#[test]
fn missing_and_corrupt_cache_files_are_all_reported_deterministically() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let pins = vec![
        synthetic_pin("fixture:missing:1", b"one"),
        synthetic_pin("fixture:corrupt:1", b"two"),
        synthetic_pin("fixture:valid:1", b"three"),
    ];
    for (pin, bytes) in [
        (&pins[1], b"bad".as_slice()),
        (&pins[2], b"three".as_slice()),
    ] {
        let path = cache::cache_path(temp.path(), &pin.coordinate)?;
        std::fs::create_dir_all(path.parent().unwrap())?;
        std::fs::write(path, bytes)?;
    }
    let mut report = preflight::empty_report();
    report.targets.push(target(pins.clone(), "first"));
    report.targets.push(target(pins, "last"));
    cache::verify_all(&mut report, temp.path(), &CancellationToken::default())?;
    assert_eq!(report.issues.len(), 2);
    assert_eq!(report.artifacts.len(), 1);
    assert_eq!(report.files_checked, 2); // unique reads, not repeated per target
    assert_eq!(
        report.issues[0].coordinate.as_deref(),
        Some("fixture:corrupt:1")
    );
    assert!(
        report
            .issues
            .iter()
            .all(|i| i.target_ids == ["first", "last"])
    );
    assert!(!temp.path().join("fixture/missing/1/missing-1.jar").exists());
    Ok(())
}

#[test]
fn full_digest_length_and_path_guards_fail_closed() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let mut pin = synthetic_pin("fixture:valid:1", b"three");
    let path = cache::cache_path(temp.path(), &pin.coordinate)?;
    std::fs::create_dir_all(path.parent().unwrap())?;
    std::fs::write(path, b"three")?;
    let mut report = preflight::empty_report();
    pin.full_sha256 = Some("wrong".to_owned());
    assert!(
        cache::verify_one(&mut report, temp.path(), &pin)
            .unwrap_err()
            .to_string()
            .contains("full digest")
    );
    assert!(cache::cache_path(temp.path(), "fixture:../../escape:1").is_err());
    assert!(cache::cache_path(temp.path(), "fixture:bad\\path:1").is_err());
    Ok(())
}

fn validated_ten_targets(mutate_last: bool) -> eyre::Result<PreflightReport> {
    let mut report = preflight::empty_report();
    for (id, _) in crate::source_projection::projection_catalog::SUPPORTED_TARGETS {
        let (mut recipe, mut lock, mut pins) = fixture();
        recipe.target = id.to_owned();
        if id == "26.1.2" {
            recipe.released_gradle_supplements.push(Supplement {
                configuration: "testRuntimeOnly".to_owned(),
                coordinate: LAUNCHER.0.to_owned(),
                artifact_hash: LAUNCHER.1.to_owned(),
            });
            lock.artifacts.push(SourceArtifact {
                coordinate: Some(LAUNCHER.0.to_owned()),
                hash: LAUNCHER.1.to_owned(),
            });
            if mutate_last {
                lock.artifacts[0].hash =
                    "blake3:0000000000000000000000000000000000000000".to_owned();
            }
        }
        let base_witnesses = roots::production_base_witnesses(&mut report, &recipe, &lock)?;
        let roots = roots::validate_target(&mut report, &recipe, &lock, &mut pins)?;
        let mut record = target(pins, id);
        record.production_base_witnesses = base_witnesses;
        record.ordered_test_roots = roots;
        report.targets.push(record);
    }
    preflight::finish(&mut report);
    Ok(report)
}

#[test]
fn all_target_admission_is_a_barrier_before_any_effect() -> eyre::Result<()> {
    let effect_count = AtomicUsize::new(0);
    let complete = validated_ten_targets(false)?;
    assert!(complete.closure_complete && complete.issues.is_empty());
    require_complete(&complete)?;
    effect_count.fetch_add(1, Ordering::SeqCst);
    assert_eq!(effect_count.load(Ordering::SeqCst), 1);

    effect_count.store(0, Ordering::SeqCst);
    let incomplete = validated_ten_targets(true)?;
    assert_eq!(
        incomplete
            .targets
            .iter()
            .filter(|target| target.closure_complete)
            .count(),
        9
    );
    assert_eq!(incomplete.issues.len(), 1);
    assert_eq!(incomplete.issues[0].target_ids, ["26.1.2"]);
    assert_eq!(incomplete.issues[0].code, "test_only_pin_conflict");
    let result =
        require_complete(&incomplete).map(|()| effect_count.fetch_add(1, Ordering::SeqCst));
    assert!(result.is_err());
    assert_eq!(effect_count.load(Ordering::SeqCst), 0);
    assert!(
        result
            .unwrap_err()
            .to_string()
            .contains("before build/Java")
    );
    Ok(())
}

#[test]
fn extra_and_duplicate_selected_pins_are_refused() -> eyre::Result<()> {
    for duplicate in [true, false] {
        let mut report = validated_ten_targets(false)?;
        let pin = if duplicate {
            report.targets[9].selected_pins[0].clone()
        } else {
            synthetic_pin("fixture:unexpected:1", b"extra")
        };
        report.targets[9].selected_pins.push(pin);
        preflight::finish(&mut report);
        assert!(!report.closure_complete);
        assert!(report.targets[..9].iter().all(|t| t.closure_complete));
        assert!(!report.targets[9].closure_complete);
        assert!(
            report
                .issues
                .iter()
                .any(|i| i.code == "test_only_flat_closure_mismatch")
        );
    }
    Ok(())
}

#[test]
fn corrupt_provenance_is_refused_after_valid_source_before_cache() -> eyre::Result<()> {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .unwrap();
    let temp = tempfile::tempdir()?;
    let path = temp.path().join(SOURCE);
    std::fs::create_dir_all(path.parent().unwrap())?;
    std::fs::copy(repo.join(SOURCE), &path)?;
    std::fs::write(temp.path().join(PROVENANCE), b"mutated reviewed receipt")?;
    let mut report = preflight::empty_report();
    let error = authority::load(&mut report, temp.path()).unwrap_err();
    assert!(
        error.to_string().contains(PROVENANCE) && error.to_string().contains("hash mismatch"),
        "{error:#}"
    );
    assert_eq!(report.files_checked, 2);
    assert!(!temp.path().join("cache-must-not-exist").exists());
    Ok(())
}

#[test]
fn reviewed_real_authority_and_all_ten_static_recipes() -> eyre::Result<()> {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .unwrap();
    let mut report = preflight::empty_report();
    let pins = authority::load(&mut report, repo)?;
    let raw = read_pinned(&mut report, repo, CONFIGURATION, CONFIGURATION_SHA)?;
    let config: super::configuration::Configuration = facet_json::from_slice(&raw)?;
    assert_eq!(pins.len(), 24);
    assert_eq!(config.targets.len(), 10);
    for recipe in config.targets {
        let raw = read_pinned(
            &mut report,
            repo,
            &recipe.source_lock.path,
            &recipe.source_lock.sha256,
        )?;
        let lock: SourceLock = facet_json::from_slice(&raw)?;
        let mut selected = pins.clone();
        roots::validate_target(&mut report, &recipe, &lock, &mut selected)?;
        assert_eq!(
            selected.len(),
            if recipe.target == "26.1.2" { 25 } else { 24 }
        );
    }
    assert!(report.issues.is_empty(), "{:?}", report.issues);
    Ok(())
}

#[test]
fn real_engine_entry_refuses_before_projection_planning_or_java() -> eyre::Result<()> {
    use crate::jar_build::BuildMode;
    use crate::jar_build::BuildOptions;
    use crate::jar_build::BuildSelection;
    use crate::jar_build::ErrorAction;
    use crate::jar_build::Parallelism;
    use crate::jar_build::RunTestAction;
    use crate::jar_build::RunTestOptions;
    let temp = tempfile::tempdir()?;
    crate::source_projection::core_catalog::write_project_catalog_fixture(
        temp.path(),
        "sfm-4.35.0/mc-1.19.2",
        "1.19.2",
    )?;
    let options = BuildOptions {
        selection: BuildSelection::Projection {
            selector: "sfm-4.35.0/*".to_owned(),
            repo_root: Some(temp.path().to_owned()),
        },
        refresh: false,
        skip_source_checks: false,
        explain_rebuild: false,
        plan_json: None,
        output_name: None,
        java_home: None,
        dry_run: false,
        allow_local_artifact_cache: false,
        artifact_sources: Vec::new(),
        require_portable_artifacts: true,
        error_action: ErrorAction::Bail,
        parallelism: Parallelism::default(),
        wait_for_build_lock: false,
        mode: BuildMode::Build,
    };
    let test_options = RunTestOptions {
        action: RunTestAction::Run,
        filter: None,
        no_capture: false,
    };
    let error =
        super::super::invoke_run_test(&options, &test_options, &CancellationToken::default())
            .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("JUnit dependency preflight refused before build/Java"),
        "{error:#}"
    );
    assert!(
        !temp
            .path()
            .join("platform/minecraft/projections/sfm-4.35.0/mc-1.19.2/build")
            .exists()
    );
    Ok(())
}

#[test]
fn serialized_launch_inputs_are_bound_before_effect_and_receipt() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let classpath = temp.path().join("testRuntimeClasspath.txt");
    let argfile = temp.path().join("junit.java.args");
    let effects = AtomicUsize::new(0);
    for changed in [&classpath, &argfile] {
        std::fs::write(&classpath, b"reviewed/classpath")?;
        std::fs::write(&argfile, b"@reviewed-java-args")?;
        let admitted = LaunchInputs::capture(temp.path())?;
        admitted.recheck(temp.path())?;
        effects.fetch_add(1, Ordering::SeqCst);
        assert_eq!(
            admitted.runtime_classpath_sha256,
            sha256(b"reviewed/classpath")
        );
        assert_eq!(admitted.argfile_sha256, sha256(b"@reviewed-java-args"));
        std::fs::write(changed, b"changed-after-admission")?;
        let previous = effects.load(Ordering::SeqCst);
        let result = admitted
            .recheck(temp.path())
            .map(|()| effects.fetch_add(1, Ordering::SeqCst));
        assert!(result.is_err());
        assert_eq!(effects.load(Ordering::SeqCst), previous);
        assert!(
            result
                .unwrap_err()
                .to_string()
                .contains("refusing Java effect or success receipt")
        );
    }
    std::fs::remove_file(&argfile)?;
    assert!(LaunchInputs::capture(temp.path()).is_err());
    Ok(())
}

#[test]
fn missing_existing_base_edge_blocks_effects_with_production_scope() -> eyre::Result<()> {
    let (recipe, mut lock, _) = fixture();
    lock.artifacts
        .retain(|pin| pin.coordinate.as_deref() != Some(super::approved_pins::BASE_JSR305.0));
    let mut report = validated_ten_targets(false)?;
    roots::production_base_witnesses(&mut report, &recipe, &lock)?;
    preflight::finish(&mut report);
    assert!(!report.closure_complete);
    assert!(report.issues.iter().any(|issue| issue.scope == "production"
        && issue.code == "production_base_witness_missing_or_conflicting"));
    let effects = AtomicUsize::new(0);
    let result = require_complete(&report).map(|()| effects.fetch_add(1, Ordering::SeqCst));
    assert!(result.is_err());
    assert_eq!(effects.load(Ordering::SeqCst), 0);
    Ok(())
}

#[test]
fn ordered_classpaths_keep_old_root_first_tail_and_base_precedence() -> eyre::Result<()> {
    let expected_compile = [
        "com.github.javaparser:javaparser-symbol-solver-core:3.26.4",
        "org.junit.jupiter:junit-jupiter-api:5.8.1",
        "org.openjdk.jmh:jmh-core:1.37",
        "org.openjdk.jmh:jmh-generator-annprocess:1.37",
        "org.openjdk.jmh:jmh-generator-bytecode:1.37",
        "com.github.javaparser:javaparser-core:3.26.4",
        "org.javassist:javassist:3.30.2-GA",
        "com.google.guava:guava:33.4.0-jre",
        "org.opentest4j:opentest4j:1.2.0",
        "org.junit.platform:junit-platform-commons:1.8.1",
        "org.apiguardian:apiguardian-api:1.1.2",
        "net.sf.jopt-simple:jopt-simple:5.0.4",
        "org.apache.commons:commons-math3:3.6.1",
        "org.openjdk.jmh:jmh-generator-reflection:1.37",
        "org.openjdk.jmh:jmh-generator-asm:1.37",
        "com.google.guava:failureaccess:1.0.2",
        "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
        "org.checkerframework:checker-qual:3.43.0",
        "com.google.errorprone:error_prone_annotations:2.36.0",
        "com.google.j2objc:j2objc-annotations:3.0.0",
        "org.ow2.asm:asm:9.0",
    ];
    let expected_runtime = [
        "com.github.javaparser:javaparser-symbol-solver-core:3.26.4",
        "org.junit.jupiter:junit-jupiter-api:5.8.1",
        "org.junit.jupiter:junit-jupiter-engine:5.8.1",
        "org.openjdk.jmh:jmh-core:1.37",
        "org.openjdk.jmh:jmh-generator-annprocess:1.37",
        "org.openjdk.jmh:jmh-generator-bytecode:1.37",
        "com.github.javaparser:javaparser-core:3.26.4",
        "org.javassist:javassist:3.30.2-GA",
        "com.google.guava:guava:33.4.0-jre",
        "org.opentest4j:opentest4j:1.2.0",
        "org.junit.platform:junit-platform-commons:1.8.1",
        "org.apiguardian:apiguardian-api:1.1.2",
        "org.junit.platform:junit-platform-engine:1.8.1",
        "net.sf.jopt-simple:jopt-simple:5.0.4",
        "org.apache.commons:commons-math3:3.6.1",
        "org.openjdk.jmh:jmh-generator-reflection:1.37",
        "org.openjdk.jmh:jmh-generator-asm:1.37",
        "com.google.guava:failureaccess:1.0.2",
        "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
        "org.checkerframework:checker-qual:3.43.0",
        "com.google.errorprone:error_prone_annotations:2.36.0",
        "com.google.j2objc:j2objc-annotations:3.0.0",
        "org.ow2.asm:asm:9.0",
    ];

    let report = validated_ten_targets(false)?;
    for target in report.targets {
        let mut artifacts: Vec<_> = target
            .selected_pins
            .iter()
            .chain(&target.production_base_witnesses)
            .map(|p| super::verified_test_artifact::VerifiedTestArtifact {
                coordinate: p.coordinate.clone(),
                content_hash: p.content_hash.clone(),
                sha256: "fixture".to_owned(),
                bytes: 1,
                cache_path: format!("fixture/{}", p.coordinate),
            })
            .collect();
        artifacts.sort_by(|a, b| a.coordinate.cmp(&b.coordinate)); // deliberately differs from binding order
        let prepared = PreparedJunitInputs {
            schema: "fixture".to_owned(),
            identity: "fixture".to_owned(),
            target,
            source_snapshot_identity: "fixture".to_owned(),
            java_identity: "fixture".to_owned(),
            cli_sha256: "fixture".to_owned(),
            test_source_lock_sha256: SOURCE_SHA.to_owned(),
            test_provenance_sha256: PROVENANCE_SHA.to_owned(),
            configuration_sha256: CONFIGURATION_SHA.to_owned(),
            artifacts,
            annotation_processing_enabled: false,
        };
        let expected: Vec<_> = expected_compile
            .iter()
            .map(|c| std::path::PathBuf::from(format!("fixture/{c}")))
            .collect();
        assert_eq!(prepared.classpath(true)?, expected);
        let mut expected: Vec<_> = expected_runtime
            .iter()
            .map(|c| std::path::PathBuf::from(format!("fixture/{c}")))
            .collect();
        if prepared.target.target_id == "26.1.2" {
            expected.insert(
                6,
                std::path::PathBuf::from(format!("fixture/{}", LAUNCHER.0)),
            );
        }
        assert_eq!(prepared.classpath(false)?, expected);
        assert!(!prepared.classpath(true)?.contains(&prepared.console()?));
        let base =
            std::path::PathBuf::from(format!("fixture/{}", super::approved_pins::BASE_JSR305.0));
        prepared.verify_base_classpath(std::slice::from_ref(&base))?;
        assert!(prepared.verify_base_classpath(&[]).is_err());
        assert!(!prepared.classpath(false)?.contains(&base));
        let mut missing = prepared.clone();
        missing
            .artifacts
            .retain(|artifact| artifact.coordinate != expected_compile[0]);
        assert!(
            missing
                .classpath(true)
                .unwrap_err()
                .to_string()
                .contains("no verified artifact")
        );
    }
    Ok(())
}

#[test]
fn compiler_effect_guard_rechecks_original_pin_and_receipt_after_setup() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let pin = synthetic_pin("fixture:compile:1", b"original-artifact");
    let artifact_path = cache::cache_path(temp.path(), &pin.coordinate)?;
    std::fs::create_dir_all(artifact_path.parent().unwrap())?;
    let receipt_path = temp.path().join("reviewed-receipt.json");
    let receipt_digest = sha256(b"original-receipt");
    let effects = AtomicUsize::new(0);
    for mutation in ["none", "artifact", "receipt", "identity"] {
        std::fs::write(&artifact_path, b"original-artifact")?;
        std::fs::write(&receipt_path, b"original-receipt")?;
        let mut report = preflight::empty_report();
        let artifact = cache::verify_one(&mut report, temp.path(), &pin)?;
        let original = identity(&(artifact, receipt_digest.clone()))?;
        if mutation == "artifact" {
            std::fs::write(&artifact_path, b"changed-during-compiler-setup")?;
        }
        if mutation == "receipt" {
            std::fs::write(&receipt_path, b"changed-during-compiler-setup")?;
        }
        effects.store(0, Ordering::SeqCst);
        let result = runtime::checked_effect(
            &original,
            || {
                let mut report = preflight::empty_report();
                let current = cache::verify_one(&mut report, temp.path(), &pin)?;
                let proof = read_pinned(
                    &mut report,
                    temp.path(),
                    "reviewed-receipt.json",
                    &receipt_digest,
                )?;
                if mutation == "identity" {
                    return Ok("different-original-admission".to_owned());
                }
                identity(&(current, sha256(&proof)))
            },
            || {
                effects.fetch_add(1, Ordering::SeqCst);
                Ok(())
            },
        );
        if mutation == "none" {
            result?;
            assert_eq!(effects.load(Ordering::SeqCst), 1);
        } else {
            assert!(
                result.is_err(),
                "mutation {mutation} must refuse before compiler effect"
            );
            assert_eq!(effects.load(Ordering::SeqCst), 0);
        }
    }
    Ok(())
}
