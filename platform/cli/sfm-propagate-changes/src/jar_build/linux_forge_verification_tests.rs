//! Boundary regressions for shared invocation-local evidence, not producer qualification.
use super::*;
use crate::jar_build::verification_metrics as metrics;
use std::cell::RefCell;
use std::rc::Rc;

fn exercise(
    action: impl FnOnce(&ExecutionContext<'_>, &Path, &Path) -> eyre::Result<()>,
) -> eyre::Result<()> {
    let (fixture, slot, recipe, _) =
        super::super::named_forge_compile_cohort_tests::fixture("1.19.2")?;
    let project = super::super::named_forge_compile_cohort_tests::prepared(
        &fixture, slot, "release", &recipe,
    )?;
    let temporary = tempfile::tempdir()?;
    let home = temporary.path().join("sdk");
    for name in ["bin", "conf", "lib", "lib/server"] {
        fs::create_dir_all(home.join(name))?;
    }
    for (name, bytes) in [
        ("bin/java", "java"),
        ("bin/javac", "javac"),
        ("release", "release"),
        ("lib/modules", "modules"),
        ("lib/server/libjvm.so", "runtime"),
        ("conf/jvm.cfg", "config"),
    ] {
        fs::write(home.join(name), bytes)?;
    }
    let cancel = CancellationToken::new();
    let root = super::super::native_cache_root::NativeCacheRoot::acquire_for_source(
        &temporary.path().join("native"),
        fixture.repository(),
        project.project().project_root(),
        &cancel,
    )?;
    let mut plan = super::super::tests::minimal_plan_for_paths();
    plan.minecraft_dir = project.project().project_root().to_path_buf();
    plan.identity = BuildProjectIdentity::Catalog {
        frozen: Arc::new(project),
    };
    plan.derived_cache_root = Some(root.path().to_path_buf());
    plan.java.home = Some(home.clone());
    plan.java.executable = home.join("bin/java");
    plan.java.selection = "explicit-java-home".to_owned();
    plan.java.major_version = 17;
    plan.java.version_output = "fixture SDK, never executed".to_owned();
    plan.java.execution_identity = Some(super::super::named_sdk_identity(&plan.java)?);
    let context = super::super::all_hit_compile_tests::context(&plan);
    let extra = temporary.path().join("common-input");
    fs::write(&extra, b"original")?;
    let owner = ForgePreparationInputs::performance_fixture(&context, "forge", &extra)?;
    let mut store = Store::begin(
        root.path(),
        "forge",
        owner.fingerprint(),
        FORGE_ROLES,
        &cancel,
    )?;
    store.produce(
        &cancel,
        |attempt| {
            FORGE_ROLES
                .iter()
                .map(|role| {
                    let path = attempt.path.join(role);
                    fs::write(&path, b"original")?;
                    Ok(path)
                })
                .collect()
        },
        || Ok(()),
    )?;
    store.release_producer();
    context
        .linux_forge
        .lock()
        .unwrap()
        .preparations
        .push(("forge".to_owned(), store, owner));
    let mapping = context.forge_mapping_path("srg_to_official.tsrg");
    let common = Arc::new(ForgePreparationInputs::performance_fixture(
        &context, "remap", &mapping,
    )?);
    bind_remap_common(&context, common)?;
    action(&context, &home, &mapping)
}

#[test]
fn linux_forge_shared_boundary_rejects_common_mutation_and_namespace_changes() -> eyre::Result<()> {
    use std::os::unix::fs::PermissionsExt as _;
    for attack in ["same-size", "replace", "hardlink", "symlink", "mode"] {
        exercise(|context, _, extra| {
            context.recheck_compiler_image()?;
            fs::set_permissions(extra, fs::Permissions::from_mode(0o600))?;
            match attack {
                "same-size" => fs::write(extra, b"modified")?,
                "replace" => {
                    fs::rename(extra, extra.with_extension("retained"))?;
                    fs::write(extra, b"original")?;
                }
                "hardlink" => fs::hard_link(extra, extra.with_extension("alias"))?,
                "symlink" => {
                    fs::rename(extra, extra.with_extension("retained"))?;
                    std::os::unix::fs::symlink(extra.with_extension("retained"), extra)?;
                }
                "mode" => fs::set_permissions(extra, fs::Permissions::from_mode(0o666))?,
                _ => unreachable!(),
            }
            if attack == "same-size" {
                fs::set_permissions(extra, fs::Permissions::from_mode(0o444))?;
            }
            assert!(recheck_remap_inputs(context).is_err(), "{attack}");
            assert!(context.recheck_compiler_image().is_err(), "{attack}");
            Ok(())
        })?;
    }
    Ok(())
}

#[test]
fn linux_forge_shared_boundary_rejects_sdk_native_bytes_membership_and_replacement()
-> eyre::Result<()> {
    for attack in ["same-size", "membership", "replace", "ancestor"] {
        exercise(|context, home, _| {
            let path = home.join("lib/server/libjvm.so");
            match attack {
                "same-size" => fs::write(&path, b"mutated")?,
                "membership" => fs::write(home.join("conf/extra"), b"new")?,
                "replace" => {
                    fs::rename(&path, path.with_extension("retained"))?;
                    fs::write(&path, b"runtime")?;
                }
                "ancestor" => {
                    fs::rename(home.join("lib/server"), home.join("lib/retained"))?;
                    fs::create_dir(home.join("lib/server"))?;
                    fs::write(&path, b"runtime")?;
                }
                _ => unreachable!(),
            }
            // Merely retrieving an owner does not confer content authority.
            context.held_forge_sdk_image()?;
            assert!(recheck_remap_inputs(context).is_err(), "{attack}");
            assert!(context.recheck_compiler_image().is_err(), "{attack}");
            Ok(())
        })?;
    }
    Ok(())
}

#[test]
fn linux_forge_completed_consumer_boundary_checks_shared_authorities_once_at_scale()
-> eyre::Result<()> {
    exercise(|context, _, _| {
        let root = context.plan.derived_cache_root.as_deref().unwrap();
        let common = context
            .linux_forge
            .lock()
            .unwrap()
            .remap_common
            .as_ref()
            .map(Arc::clone)
            .unwrap();
        for index in 0..38 {
            let path = root.parent().unwrap().join(format!("artifact-{index}"));
            fs::write(&path, b"artifact")?;
            let artifact = DependencyRemapArtifactInputs::performance_fixture(
                &path,
                common.fingerprint(),
                &context.cancellation_token,
            )?;
            let mut store = Store::begin(
                root,
                "remap",
                &format!("sha256:{index:064x}"),
                REMAP_ROLES,
                &context.cancellation_token,
            )?;
            store.produce(
                &context.cancellation_token,
                |attempt| {
                    REMAP_ROLES
                        .iter()
                        .map(|role| {
                            let output = attempt.path.join(role);
                            fs::write(&output, b"fixture output")?;
                            Ok(output)
                        })
                        .collect()
                },
                || recheck_remap_inputs(context),
            )?;
            store.release_producer();
            context
                .linux_forge
                .lock()
                .unwrap()
                .remaps
                .push((store, artifact));
        }
        let counts = Rc::new(RefCell::new((0u64, 0u64)));
        let seen = Rc::clone(&counts);
        let _scope =
            crate::source_projection::catalog_owned_project::source_check_observation::install(
                move |kind| {
                    if kind == "authored" {
                        seen.borrow_mut().0 += 1;
                    }
                    if kind == "recheck" {
                        seen.borrow_mut().1 += 1;
                    }
                },
            );
        metrics::reset();
        context.recheck_compiler_image()?;
        assert_eq!(metrics::snapshot().sdk_rechecks, 1);
        assert_eq!(*counts.borrow(), (2, 2));
        // Prior unrelated outputs are not inputs of the next producer, but a
        // mutated member cannot escape the mandatory completed-consumer gate.
        let path = context.linux_forge.lock().unwrap().remaps[0]
            .0
            .path("remapped.jar")?;
        fs::set_permissions(&path, std::os::unix::fs::PermissionsExt::from_mode(0o600))?;
        fs::write(&path, b"mutated output")?;
        recheck_remap_inputs(context)?;
        assert!(context.recheck_compiler_image().is_err());
        Ok(())
    })
}

#[test]
fn linux_forge_shared_boundary_rejects_source_edit_and_cancellation() -> eyre::Result<()> {
    exercise(|context, _, _| {
        let path = context.plan.minecraft_dir.join("gradle.properties");
        let mut bytes = fs::read(&path)?;
        bytes.push(b'\n');
        fs::write(&path, bytes)?;
        assert!(recheck_remap_inputs(context).is_err());
        assert!(context.recheck_compiler_image().is_err());
        Ok(())
    })?;
    exercise(|context, _, _| {
        let scans = Rc::new(RefCell::new(0));
        let seen = Rc::clone(&scans);
        let _scope =
            crate::source_projection::catalog_owned_project::source_check_observation::install(
                move |_| *seen.borrow_mut() += 1,
            );
        context
            .cancellation_token
            .request_cancel("shared boundary cancelled");
        assert_eq!(
            recheck_remap_inputs(context).unwrap_err().to_string(),
            "shared boundary cancelled"
        );
        assert_eq!(
            context.recheck_compiler_image().unwrap_err().to_string(),
            "shared boundary cancelled"
        );
        assert_eq!(
            *scans.borrow(),
            0,
            "cancelled seals must not begin source scans"
        );
        Ok(())
    })
}

#[test]
fn linux_forge_empty_remap_batch_keeps_no_unneeded_common_owner() -> eyre::Result<()> {
    exercise(|context, _, _| {
        context.linux_forge.lock().unwrap().remap_common = None;
        assert!(remaps(context, &[], true)?.is_empty());
        assert!(context.linux_forge.lock().unwrap().remap_common.is_none());
        Ok(())
    })
}

#[test]
fn linux_forge_common_capture_cannot_admit_changed_derived_mapping() -> eyre::Result<()> {
    use std::os::unix::fs::PermissionsExt as _;
    exercise(|context, _, mapping| {
        context.recheck_compiler_image()?;
        context.linux_forge.lock().unwrap().remap_common = None;
        fs::set_permissions(mapping, fs::Permissions::from_mode(0o600))?;
        fs::write(mapping, b"modified")?;
        fs::set_permissions(mapping, fs::Permissions::from_mode(0o444))?;
        let common = Arc::new(ForgePreparationInputs::performance_fixture(
            context, "remap", mapping,
        )?);
        assert!(bind_remap_common(context, common).is_err());
        assert!(
            !context
                .plan
                .derived_cache_root
                .as_ref()
                .unwrap()
                .join("forge-linux-v1/remap")
                .exists()
        );
        Ok(())
    })
}

#[test]
fn linux_forge_scoped_mapping_gate_retains_production_metadata_and_namespace_checks()
-> eyre::Result<()> {
    use std::os::unix::fs::PermissionsExt as _;
    for attack in [
        "head",
        "manifest",
        "key-mode",
        "kind-mode",
        "missing-export",
        "linked-export",
        "symlink-export",
        "writable-export",
    ] {
        exercise(|context, _, mapping| {
            let entry = mapping.parent().unwrap();
            let key = entry.parent().unwrap();
            match attack {
                "head" | "manifest" => {
                    let path = if attack == "head" {
                        key.join("current.json")
                    } else {
                        entry.join("production.json")
                    };
                    let bytes = fs::read(&path)?;
                    fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?;
                    fs::write(&path, vec![b'x'; bytes.len()])?;
                    fs::set_permissions(&path, fs::Permissions::from_mode(0o444))?;
                }
                "missing-export" => fs::remove_file(entry.join("official_sources.jar"))?,
                "linked-export" => fs::hard_link(
                    entry.join("official_sources.jar"),
                    key.parent().unwrap().join("alias.jar"),
                )?,
                "symlink-export" => {
                    fs::rename(
                        entry.join("official_sources.jar"),
                        key.parent().unwrap().join("retained.jar"),
                    )?;
                    std::os::unix::fs::symlink(
                        key.parent().unwrap().join("retained.jar"),
                        entry.join("official_sources.jar"),
                    )?;
                }
                "writable-export" => fs::set_permissions(
                    entry.join("official_sources.jar"),
                    fs::Permissions::from_mode(0o600),
                )?,
                "key-mode" => fs::set_permissions(key, fs::Permissions::from_mode(0o777))?,
                "kind-mode" => {
                    fs::set_permissions(key.parent().unwrap(), fs::Permissions::from_mode(0o777))?
                }
                _ => unreachable!(),
            }
            let common = context
                .linux_forge
                .lock()
                .unwrap()
                .remap_common
                .as_ref()
                .map(Arc::clone)
                .unwrap();
            common.recheck(context)?; // Mapping bytes themselves still match.
            assert!(recheck_remap_inputs(context).is_err(), "{attack}");
            assert!(context.recheck_compiler_image().is_err(), "{attack}");
            Ok(())
        })?;
    }
    Ok(())
}

#[test]
fn linux_forge_unrelated_export_content_is_still_mandatory_at_completed_consumer_gate()
-> eyre::Result<()> {
    use std::os::unix::fs::PermissionsExt as _;
    exercise(|context, _, mapping| {
        let path = mapping.parent().unwrap().join("official_sources.jar");
        fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?;
        fs::write(&path, b"modified")?;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o444))?;
        recheck_remap_inputs(context)?; // Not an input of a new mapping-only remap.
        assert!(context.recheck_compiler_image().is_err());
        Ok(())
    })
}

#[test]
fn linux_forge_cancellation_during_final_source_sweep_refuses_completed_seal() -> eyre::Result<()> {
    exercise(|context, _, _| {
        let count = Rc::new(RefCell::new(0));
        let seen = Rc::clone(&count);
        let token = context.cancellation_token.clone();
        let _scope =
            crate::source_projection::catalog_owned_project::source_check_observation::install(
                move |kind| {
                    if kind == "recheck" {
                        *seen.borrow_mut() += 1;
                        if *seen.borrow() == 2 {
                            token.request_cancel("cancelled during final source sweep");
                        }
                    }
                },
            );
        assert_eq!(
            context.recheck_compiler_image().unwrap_err().to_string(),
            "cancelled during final source sweep"
        );
        assert_eq!(*count.borrow(), 2);
        Ok(())
    })
}
