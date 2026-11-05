use super::*;
use crate::source_projection::catalog_owned_project::tests::Fixture;
use crate::source_projection::frozen_recipe_project::prepare_frozen_recipe_project;
use std::collections::BTreeMap;
use std::fs;
use std::sync::Arc;

fn fixture(version: &str) -> eyre::Result<(Fixture, BuildPlan, Vec<u8>)> {
    let (fixture, slot, recipe) = if matches!(version, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") {
        let (fixture, slot, recipe, _) =
            super::super::super::named_forge_compile_cohort_tests::fixture(version)?;
        (fixture, slot, recipe)
    } else {
        let (fixture, project) =
            crate::jar_build::nfrt_launch_contract::tests::project_fixture(version, "release")?;
        let recipe = project.receipt().released_inputs.recipe_id.clone();
        drop(project);
        let slot = crate::source_projection::projection_catalog::SUPPORTED_TARGETS
            .iter()
            .position(|(id, _)| *id == version)
            .unwrap();
        (fixture, slot, recipe)
    };
    let key = format!("sfm-4.34.0/mc-{version}");
    let path = fixture
        .repository()
        .join(crate::source_projection::projection_catalog::CATALOG_PATH);
    let before = fs::read_to_string(&path)?;
    let original = facet_json::to_string(&Fixture::key(slot, "release"))?;
    assert_eq!(before.matches(&original).count(), 1);
    fs::write(
        path,
        before.replacen(&original, &facet_json::to_string(&key)?, 1),
    )?;
    fixture.publish(&key);
    let project = prepare_frozen_recipe_project(
        fixture.collect(&key)?.check_current()?,
        &recipe,
        false,
        &BTreeMap::new(),
    )?;
    let mut plan = super::super::super::tests::minimal_plan_for_paths();
    plan.branch_name = None;
    plan.worktree_path = None;
    let minecraft = crate::source_projection::projection_catalog::SUPPORTED_TARGETS
        .iter()
        .find(|(id, _)| *id == version)
        .unwrap()
        .1;
    plan.minecraft_version = crate::branch_targets::MinecraftVersion::parse(minecraft)?;
    project
        .project()
        .project_root()
        .clone_into(&mut plan.minecraft_dir);
    plan.cache_dir = plan.minecraft_dir.join("build/sfm-toolchain");
    plan.maven_cache_dir = fixture.repository().join("maven");
    plan.loader_toolchain.kind = if matches!(version, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") {
        super::super::super::named_forge_loader_kind(
            super::super::super::named_forge_compile_recipe_for_project(&project)?,
        )
    } else {
        super::super::super::LoaderToolchainKind::NeoGradleUserdev
    };
    let raw_lock = project.dependencies().raw_source_lock_bytes().to_vec();
    plan.identity = super::super::super::BuildProjectIdentity::Catalog {
        frozen: Arc::new(project),
    };
    Ok((fixture, plan, raw_lock))
}

#[test]
fn historical_junit_uses_reviewed_transitives_without_editing_product_locks() -> eyre::Result<()> {
    for version in TARGETS {
        let (_fixture, plan, original) = fixture(version)?;
        let report = super::super::development_compile_tests::report(version);
        let authority = &report.targets[0];
        verify_owner(&plan)?;
        super::super::runtime::verify_roots(&plan, authority)?;
        verify_pins(&plan, authority)?;
        let binding = bind_identity(&plan, authority)?;
        let mut changed_authority = authority.clone();
        changed_authority.selected_pin_identity.push_str("-changed");
        assert_ne!(binding, bind_identity(&plan, &changed_authority)?);
        let product = plan.named_project().unwrap().dependencies();
        let _ = product
            .coordinate("org.javassist:javassist:3.30.2-GA", false)
            .unwrap_err();
        assert!(
            authority
                .classpath_bindings
                .compile
                .iter()
                .any(|c| c == "org.javassist:javassist:3.30.2-GA")
        );
        assert_eq!(product.raw_source_lock_bytes(), original);
        assert!(!plan.maven_cache_dir.exists());
        assert!(!plan.cache_dir.exists());
        plan.recheck_named_inputs()?;
    }
    Ok(())
}

#[test]
fn historical_junit_refuses_other_owners_roots_and_conflicting_pins() -> eyre::Result<()> {
    for key in [
        "sfm-4.35.0/mc-1.19.2",
        "sfm-4.34.0-extended/mc-1.19.2",
        "sfm-4.34.0/mc-26.1.2",
        "other/mc-1.19.2",
    ] {
        assert!(target(key).is_none());
    }
    let (_fixture, mut plan, raw) = fixture("1.19.2")?;
    let mut report = super::super::development_compile_tests::report("1.19.2");
    let authority = &mut report.targets[0];
    authority.ordered_test_roots[0]
        .coordinate
        .push_str("-changed");
    assert!(super::super::runtime::verify_roots(&plan, authority).is_err());
    let mut source: SourceLock = facet_json::from_slice(&raw)?;
    source
        .artifacts
        .push(super::super::source_artifact::SourceArtifact {
            coordinate: Some("org.javassist:javassist:3.30.2-GA".to_owned()),
            hash: format!("blake3:{}", "00".repeat(20)),
        });
    assert!(verify_product_pin_overlap(&source, authority).is_err());
    let mut source: SourceLock = facet_json::from_slice(&raw)?;
    source.artifacts.retain(|artifact| {
        artifact.coordinate.as_deref() != Some(super::super::approved_pins::BASE_JSR305.0)
    });
    assert!(verify_product_pin_overlap(&source, authority).is_err());
    plan.loader_toolchain.kind = super::super::super::LoaderToolchainKind::NeoGradleUserdev;
    assert!(verify_owner(&plan).is_err());
    Ok(())
}

#[test]
fn historical_junit_neoform_preserves_target_version_and_loader_binding() -> eyre::Result<()> {
    for version in ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1"] {
        let (_fixture, mut plan, _) = fixture(version)?;
        let mut report = super::super::development_compile_tests::report(version);
        let authority = &mut report.targets[0];
        verify_owner(&plan)?;
        verify_pins(&plan, authority)?;
        authority.target_id = "1.19.2".to_owned();
        assert!(verify_pins(&plan, authority).is_err());
        authority.target_id = version.to_owned();
        authority.minecraft_version = "other".to_owned();
        assert!(verify_pins(&plan, authority).is_err());
        if version == "1.21.0" {
            assert_eq!(plan.minecraft_version.as_str(), "1.21");
            plan.minecraft_version = crate::branch_targets::MinecraftVersion::parse("1.21.0")?;
            assert!(verify_owner(&plan).is_err());
            plan.minecraft_version = crate::branch_targets::MinecraftVersion::parse("1.21")?;
        }
        plan.loader_toolchain.kind = super::super::super::LoaderToolchainKind::ForgeGradleForge;
        assert!(verify_owner(&plan).is_err());
    }
    Ok(())
}
