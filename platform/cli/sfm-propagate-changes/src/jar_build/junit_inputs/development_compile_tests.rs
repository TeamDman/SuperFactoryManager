use super::super::BuildPlan;
use super::super::BuildProjectIdentity;
use super::super::LoaderToolchainKind;
use super::CancellationToken;
use super::TestPin;
use super::approved_pins::BASE_JSR305;
use super::approved_pins::COMMON_PINS;
use super::development_compile::development_owner_identity;
use super::development_compile::prepare_development_with;
use super::development_compile::verify_development_roots_and_pins;
use super::identity;
use super::preflight;
use crate::source_projection::catalog_owned_project::tests::Fixture;
use crate::source_projection::core_inputs::CORE_ROOT;
use crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE;
use crate::source_projection::native_project_target::NativeProjectTarget;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3;
use crate::toolchain_lockfile_schema::version::v4::ArtifactLockfileV4;
use std::sync::Arc;

fn fixture(
    version: &str,
    edit: impl FnOnce(&mut ArtifactLockfileV4),
) -> eyre::Result<(Fixture, BuildPlan)> {
    let (raw, slot, loader): (&[u8], usize, &str) = match version {
        "1.20.3" => (
            include_bytes!(
                "../../../../../minecraft/core-liquid-template/build/lockfiles/1.20.3/schema-4.json"
            ),
            5,
            "20.3.8-beta",
        ),
        "1.20.4" => (
            include_bytes!(
                "../../../../../minecraft/core-liquid-template/build/lockfiles/1.20.4/schema-4.json"
            ),
            6,
            "20.4.231",
        ),
        _ => eyre::bail!("unknown fixture target"),
    };
    let ToolchainLockfileDocument::V4(mut lock) = parse_document(std::str::from_utf8(raw)?)? else {
        eyre::bail!("fixture requires schema 4");
    };
    edit(&mut lock);
    let raw = facet_json::to_string(&lock)?;
    let mut fixture = Fixture::new_for_target(version);
    let properties =
        format!("minecraft_version={version}\nneo_version={loader}\nmod_version=4.34.0\n");
    fixture.set_project_files_for(
        version,
        [
            (
                "sfm-toolchain.lock.json",
                "build/proof/lock.json",
                raw.as_bytes(),
                false,
            ),
            (
                "gradle.properties",
                "build/proof/properties",
                properties.as_bytes(),
                false,
            ),
        ],
    )?;
    let key = format!("sfm-dev/mc-{version}");
    let catalog = fixture
        .repository()
        .join(crate::source_projection::projection_catalog::CATALOG_PATH);
    let before = std::fs::read_to_string(&catalog)?;
    let old = facet_json::to_string(&Fixture::key(slot, "dev"))?;
    assert_eq!(before.matches(&old).count(), 1);
    std::fs::write(
        catalog,
        before.replacen(&old, &facet_json::to_string(&key)?, 1),
    )?;
    std::fs::write(
        fixture.repository().join(".gitignore"),
        "/platform/minecraft/projections/scratch/\n/platform/minecraft/projections/sfm-dev/\n",
    )?;
    fixture.publish(&key);
    let project = Arc::new(fixture.collect(&key)?.check_current()?);
    let target = Arc::new(NativeProjectTarget::from_checked_project(
        &project,
        NATIVE_DEPENDENCY_PROFILE,
    )?);
    let mut plan = super::super::tests::minimal_plan_for_paths();
    plan.branch_name = None;
    plan.worktree_path = None;
    plan.minecraft_version = crate::branch_targets::MinecraftVersion::parse(version)?;
    plan.minecraft_dir = target.minecraft_dir.clone();
    plan.cache_dir = target.cache_dir.clone();
    plan.state_dir = plan.cache_dir.join("state");
    plan.maven_cache_dir = fixture.repository().join("maven");
    plan.lockfile_path = plan.minecraft_dir.join("sfm-toolchain.lock.json");
    plan.loader_toolchain.kind = LoaderToolchainKind::NeoGradleUserdev;
    plan.identity = BuildProjectIdentity::Development { project, target };
    Ok((fixture, plan))
}

fn report(version: &str) -> super::preflight_report::PreflightReport {
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
    let mut report = preflight::empty_report();
    report.targets.push(super::target_report::TargetReport {
        projection_key: format!("sfm-4.35.0/mc-{version}"),
        target_id: version.to_owned(),
        minecraft_version: version.to_owned(),
        released_recipe_id: "unchanged-reviewed-release-authority".to_owned(),
        raw_source_lock_sha256: "unchanged-authority-lock".to_owned(),
        ordered_test_roots: super::roots::expected_roots(version)
            .into_iter()
            .map(|(configuration, coordinate)| super::test_root::TestRoot {
                configuration: configuration.to_owned(),
                coordinate: coordinate.to_owned(),
                content_hash: pins
                    .iter()
                    .find(|pin| pin.coordinate == coordinate)
                    .unwrap()
                    .content_hash
                    .clone(),
            })
            .collect(),
        role_selection_identity: "released-role-authority-only".to_owned(),
        role_bindings: Vec::new(),
        selected_pin_identity: identity(&pins).unwrap(),
        selected_pins: pins,
        production_base_witnesses: vec![TestPin {
            coordinate: BASE_JSR305.0.to_owned(),
            content_hash: BASE_JSR305.1.to_owned(),
            full_sha256: None,
            bytes: None,
            origin: None,
            authority: "existing_production_base_classpath".to_owned(),
        }],
        classpath_bindings: super::bindings::for_target(version),
        closure_complete: true,
    });
    report.closure_complete = true;
    report
}

fn install_hash_only_sdk(plan: &mut BuildPlan, root: &std::path::Path) -> eyre::Result<()> {
    let home = root.join("fake-sdk");
    let java = if cfg!(windows) {
        "bin/java.exe"
    } else {
        "bin/java"
    };
    let javac = if cfg!(windows) {
        "bin/javac.exe"
    } else {
        "bin/javac"
    };
    for relative in [java, javac, "release", "lib/modules"] {
        let path = home.join(relative);
        std::fs::create_dir_all(path.parent().unwrap())?;
        std::fs::write(path, relative.as_bytes())?;
    }
    plan.java.home = Some(home.clone());
    plan.java.executable = home.join(java);
    plan.java.major_version = 21;
    plan.java.version_output = "hash-only fixture SDK; never executed".to_owned();
    plan.java.execution_identity = Some(super::super::named_sdk_identity(&plan.java)?);
    Ok(())
}

#[test]
fn development_junit_compile_actual_owners_keep_release_authority_and_dev_source_binding()
-> eyre::Result<()> {
    for version in ["1.20.3", "1.20.4"] {
        let (fixture, mut plan) = fixture(version, |_| {})?;
        let source = development_owner_identity(&plan)?.unwrap();
        assert_eq!(
            source,
            identity(&plan.identity.development_target().unwrap().receipt)?
        );
        assert!(super::runtime::prepare_for_plan(&plan, &CancellationToken::new())?.is_none());
        verify_development_roots_and_pins(&plan, &report(version).targets[0])?;
        install_hash_only_sdk(&mut plan, fixture.repository())?;
        let prepared =
            prepare_development_with(&plan, &CancellationToken::new(), || Ok(report(version)))?;
        assert_eq!(prepared.source_snapshot_identity, source);
        assert_eq!(
            prepared.target.projection_key,
            format!("sfm-4.35.0/mc-{version}")
        );
        assert_eq!(
            prepared.target.released_recipe_id,
            "unchanged-reviewed-release-authority"
        );
        assert_eq!(
            prepared.target.role_selection_identity,
            "released-role-authority-only"
        );
        assert_eq!(
            prepared.java_identity,
            identity(&(
                plan.java.cache_identity(),
                plan.java.execution_identity.clone().unwrap()
            ))?
        );
        assert!(!prepared.annotation_processing_enabled);
    }
    Ok(())
}

#[test]
fn development_junit_compile_missing_and_additional_actual_roots_are_rejected() -> eyre::Result<()>
{
    for remove in [true, false] {
        let (_fixture, plan) = fixture("1.20.3", |lock| {
            let dependency = lock
                .dependencies
                .iter_mut()
                .find(|row| {
                    row.id
                        == if remove {
                            "javaparser"
                        } else {
                            "junit-jupiter-engine"
                        }
                })
                .unwrap();
            let scopes = &mut dependency.components[0].declaration.scopes;
            if remove {
                scopes.retain(|scope| *scope != DependencyScopeV3::TestCompile);
            } else {
                scopes.push(DependencyScopeV3::TestCompile);
            }
        })?;
        let error =
            verify_development_roots_and_pins(&plan, &report("1.20.3").targets[0]).unwrap_err();
        assert!(error.to_string().contains("roots differ"), "{error:#}");
    }
    Ok(())
}

#[test]
fn development_junit_compile_conflicting_declared_root_pin_is_rejected() -> eyre::Result<()> {
    let (_fixture, plan) = fixture("1.20.4", |lock| {
        let dependency = lock
            .dependencies
            .iter_mut()
            .find(|row| row.id == "javaparser")
            .unwrap();
        let hash = super::super::ContentHash::from_bytes(
            b"contrary declared pin",
            super::super::ContentHashAlgorithm::Blake3,
        );
        dependency.components[0].derived_checks.expected_hash = hash;
        let id = dependency.components[0].derived_checks.artifact_id.clone();
        lock.artifacts
            .iter_mut()
            .find(|row| row.id == id)
            .unwrap()
            .hash = hash;
    })?;
    let error = verify_development_roots_and_pins(&plan, &report("1.20.4").targets[0]).unwrap_err();
    assert!(
        error.to_string().contains("declared pin conflicts"),
        "{error:#}"
    );
    Ok(())
}

#[test]
fn development_junit_compile_changed_owner_profile_loader_and_files_are_bound() -> eyre::Result<()>
{
    let (_fixture, mut plan) = fixture("1.20.3", |_| {})?;
    let original = development_owner_identity(&plan)?.unwrap();
    let BuildProjectIdentity::Development { target, .. } = &mut plan.identity else {
        unreachable!()
    };
    let target = Arc::make_mut(target);
    target
        .receipt
        .files
        .values_mut()
        .next()
        .unwrap()
        .output_sha256
        .push('0');
    assert_ne!(original, identity(&target.receipt)?);
    assert!(plan.recheck_named_inputs().is_err());
    let BuildProjectIdentity::Development { target, .. } = &mut plan.identity else {
        unreachable!()
    };
    Arc::make_mut(target).receipt.dependency_profile = "gradle".to_owned();
    assert!(development_owner_identity(&plan).is_err());
    let BuildProjectIdentity::Development { target, .. } = &mut plan.identity else {
        unreachable!()
    };
    Arc::make_mut(target).receipt.dependency_profile = NATIVE_DEPENDENCY_PROFILE.to_owned();
    plan.loader_toolchain.kind = LoaderToolchainKind::ForgeGradleForge;
    assert!(development_owner_identity(&plan).is_err());
    Ok(())
}

#[test]
fn development_junit_compile_source_change_fails_before_authority_or_java() -> eyre::Result<()> {
    let (fixture, plan) = fixture("1.20.4", |_| {})?;
    let path = fixture
        .repository()
        .join(CORE_ROOT)
        .join("src/main/java/Shared.java");
    std::fs::write(path, b"class Changed {}")?;
    let called = std::cell::Cell::new(false);
    let result = prepare_development_with(&plan, &CancellationToken::new(), || {
        called.set(true);
        Ok(report("1.20.4"))
    });
    assert!(result.is_err());
    assert!(!called.get());
    Ok(())
}

#[test]
fn development_junit_compile_sdk_change_fails_before_authority() -> eyre::Result<()> {
    let (fixture, mut plan) = fixture("1.20.3", |_| {})?;
    install_hash_only_sdk(&mut plan, fixture.repository())?;
    std::fs::write(
        plan.java.home.as_ref().unwrap().join("lib/modules"),
        b"changed SDK",
    )?;
    let called = std::cell::Cell::new(false);
    assert!(
        prepare_development_with(&plan, &CancellationToken::new(), || {
            called.set(true);
            Ok(report("1.20.3"))
        })
        .is_err()
    );
    assert!(!called.get());
    Ok(())
}

#[test]
fn development_junit_compile_recheck_gate_rejects_new_valid_preparation_identity()
-> eyre::Result<()> {
    let (fixture, mut plan) = fixture("1.20.4", |_| {})?;
    install_hash_only_sdk(&mut plan, fixture.repository())?;
    let prepared =
        prepare_development_with(&plan, &CancellationToken::new(), || Ok(report("1.20.4")))?;
    let effect = std::cell::Cell::new(false);
    let result = super::runtime::checked_effect(
        &prepared.identity,
        || {
            let mut changed = prepared.clone();
            changed.java_identity.push('x');
            changed.identity.clear();
            identity(&changed)
        },
        || {
            effect.set(true);
            Ok(())
        },
    );
    assert!(result.is_err());
    assert!(!effect.get());
    Ok(())
}

#[test]
fn development_junit_compile_wrong_authority_target_is_rejected() -> eyre::Result<()> {
    let (fixture, mut plan) = fixture("1.20.3", |_| {})?;
    install_hash_only_sdk(&mut plan, fixture.repository())?;
    let error = prepare_development_with(&plan, &CancellationToken::new(), || Ok(report("1.20.4")))
        .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("authority target/version mismatch"),
        "{error:#}"
    );
    Ok(())
}

#[test]
fn development_junit_compile_exact_owner_scope_does_not_adopt_arbitrary_or_release_keys()
-> eyre::Result<()> {
    let (_fixture, mut plan) = fixture("1.20.3", |_| {})?;
    for key in [
        "scratch/arbitrary/slot-5",
        "sfm-4.35.0/mc-1.20.3",
        "sfm-dev/mc-1.21.1",
    ] {
        let BuildProjectIdentity::Development { target, .. } = &mut plan.identity else {
            unreachable!()
        };
        Arc::make_mut(target).receipt.projection_key = key.to_owned();
        assert!(development_owner_identity(&plan)?.is_none());
        assert!(
            super::development_compile::prepare_for_compile_plan(&plan, &CancellationToken::new())?
                .is_none()
        );
    }
    let BuildProjectIdentity::Development { target, .. } = &mut plan.identity else {
        unreachable!()
    };
    Arc::make_mut(target).receipt.projection_key = "sfm-dev/mc-1.20.3".to_owned();
    plan.minecraft_version = crate::branch_targets::MinecraftVersion::parse("1.20.4")?;
    assert!(development_owner_identity(&plan).is_err());
    Ok(())
}

#[test]
fn development_junit_compile_loader_managed_actual_test_root_is_refused() -> eyre::Result<()> {
    let (_fixture, plan) = fixture("1.20.3", |lock| {
        let dependency = lock
            .dependencies
            .iter_mut()
            .find(|row| row.id == "javaparser")
            .unwrap();
        dependency.components[0].declaration.artifact_treatment =
            crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::LoaderManagedMod;
    })?;
    let error = verify_development_roots_and_pins(&plan, &report("1.20.3").targets[0]).unwrap_err();
    assert!(
        error.to_string().contains("loader-managed test root"),
        "{error:#}"
    );
    Ok(())
}
