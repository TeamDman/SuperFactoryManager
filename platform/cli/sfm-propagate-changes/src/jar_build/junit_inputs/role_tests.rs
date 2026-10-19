use super::configuration::Configuration;
use super::role_selection::RoleSelection;
use super::target_recipe::TargetRecipe;
use super::*;

fn repo() -> &'static Path {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .unwrap()
}

fn configuration(report: &mut PreflightReport) -> eyre::Result<Configuration> {
    Ok(facet_json::from_slice(&read_pinned(
        report,
        repo(),
        CONFIGURATION,
        CONFIGURATION_SHA,
    )?)?)
}

#[test]
fn current_all_ten_rendered_role_bindings_match_original_pins() -> eyre::Result<()> {
    let mut report = preflight::empty_report();
    let selected = RoleSelection::load(&mut report, repo())?;
    let config = configuration(&mut report)?;
    let mut raw_differs = 0;
    for recipe in &config.targets {
        let (selection_identity, bindings) = roles::check(&mut report, repo(), recipe, &selected)?;
        assert!(!selection_identity.is_empty());
        assert_eq!(bindings.len(), recipe.role_input_hashes.len());
        assert_eq!(
            bindings
                .iter()
                .filter(|b| b.binding_kind == "current_selected_source_exclusion")
                .count(),
            3
        );
        for binding in bindings {
            assert_eq!(binding.projected_sha256, binding.recipe_role_sha256);
            if binding.authored_sha256 != binding.projected_sha256 {
                assert!(binding.declared_template);
                assert!(matches!(
                    recipe.target.as_str(),
                    "1.20.2" | "1.20.3" | "26.1.2"
                ));
                raw_differs += 1;
            }
        }
    }
    assert_eq!(raw_differs, 6);
    assert!(report.issues.is_empty(), "{:?}", report.issues);
    Ok(())
}

#[test]
fn rendered_role_rejects_wrong_pin_context_path_output_mode_and_duplicate() -> eyre::Result<()> {
    let mut report = preflight::empty_report();
    let selected = RoleSelection::load(&mut report, repo())?;
    let recipe: TargetRecipe = configuration(&mut report)?
        .targets
        .into_iter()
        .find(|r| r.target == "1.20.2")
        .unwrap();
    let witness = recipe
        .source_excludes
        .iter()
        .find(|w| w.output.ends_with("main-java.txt"))
        .unwrap();
    let role = recipe
        .role_input_hashes
        .iter()
        .find(|r| r.path == witness.source_path)
        .unwrap();
    let (context, selection, _) = selected.for_target(&recipe.target)?;
    let good = roles::exclusion_role(&mut report, repo(), role, witness, &selection, &context)?;
    assert!(good.declared_template && good.authored_sha256 != good.projected_sha256);
    let mut bad_witness = witness.clone();
    bad_witness.sha256 = "wrong".to_owned();
    assert!(
        roles::exclusion_role(
            &mut report,
            repo(),
            role,
            &bad_witness,
            &selection,
            &context
        )
        .is_err()
    );
    let mut bad_context = context.clone();
    bad_context.minecraft_version = "1.20.3".to_owned();
    assert!(
        roles::exclusion_role(&mut report, repo(), role, witness, &selection, &bad_context)
            .is_err()
    );
    let mut bad_context = context.clone();
    bad_context
        .features
        .insert("computercraft".to_owned(), true);
    assert!(
        roles::exclusion_role(&mut report, repo(), role, witness, &selection, &bad_context)
            .is_err()
    );
    let mut bad_witness = witness.clone();
    bad_witness.output = "gradle/arbitrary.txt".to_owned();
    assert!(
        roles::exclusion_role(
            &mut report,
            repo(),
            role,
            &bad_witness,
            &selection,
            &context
        )
        .is_err()
    );
    let mut bad_witness = witness.clone();
    bad_witness.source_path = "../escape".to_owned();
    assert!(
        roles::exclusion_role(
            &mut report,
            repo(),
            role,
            &bad_witness,
            &selection,
            &context
        )
        .is_err()
    );
    let mut wrong_mode = selection.clone();
    wrong_mode.inputs.get_mut(&witness.output).unwrap().template = false;
    assert!(
        roles::exclusion_role(&mut report, repo(), role, witness, &wrong_mode, &context).is_err()
    );
    let mut duplicate = selection.clone();
    duplicate.inputs.insert(
        "gradle/duplicate.txt".to_owned(),
        selection.inputs[&witness.output].clone(),
    );
    assert!(
        roles::exclusion_role(&mut report, repo(), role, witness, &duplicate, &context).is_err()
    );
    let mut wrong_patterns = witness.clone();
    wrong_patterns.patterns.push("unexpected/**".to_owned());
    assert!(
        roles::exclusion_role(
            &mut report,
            repo(),
            role,
            &wrong_patterns,
            &selection,
            &context
        )
        .is_err()
    );
    Ok(())
}

#[test]
fn unchanged_rendered_pin_still_rejects_raw_template_change_after_admission() -> eyre::Result<()> {
    use std::sync::atomic::AtomicUsize;
    use std::sync::atomic::Ordering;
    let mut report = preflight::empty_report();
    let selected = RoleSelection::load(&mut report, repo())?;
    let recipe: TargetRecipe = configuration(&mut report)?
        .targets
        .into_iter()
        .find(|r| r.target == "1.20.2")
        .unwrap();
    let witness = recipe
        .source_excludes
        .iter()
        .find(|w| w.output.ends_with("main-java.txt"))
        .unwrap();
    let role = recipe
        .role_input_hashes
        .iter()
        .find(|r| r.path == witness.source_path)
        .unwrap();
    let (context, selection, _) = selected.for_target(&recipe.target)?;
    let temp = tempfile::tempdir()?;
    let path = temp.path().join(&role.path);
    std::fs::create_dir_all(path.parent().unwrap())?;
    let raw = std::fs::read_to_string(repo().join(&role.path))?;
    std::fs::write(&path, &raw)?;
    let original = roles::exclusion_role(
        &mut report,
        temp.path(),
        role,
        witness,
        &selection,
        &context,
    )?;
    let original_identity = identity(&original)?;
    let effects = AtomicUsize::new(0);
    runtime::checked_effect(
        &original_identity,
        || {
            Ok(identity(&roles::exclusion_role(
                &mut report,
                temp.path(),
                role,
                witness,
                &selection,
                &context,
            )?)?)
        },
        || {
            effects.fetch_add(1, Ordering::SeqCst);
            Ok(())
        },
    )?;
    assert_eq!(effects.load(Ordering::SeqCst), 1);
    // Change only the disabled body: exact output pin remains unchanged.
    let changed = raw.replace(
        "# CC:Tweaked has no compatible 1.20.2 Forge release.",
        "# Changed inactive authored evidence.",
    );
    assert_ne!(raw, changed);
    std::fs::write(&path, changed)?;
    let current = roles::exclusion_role(
        &mut report,
        temp.path(),
        role,
        witness,
        &selection,
        &context,
    )?;
    assert_eq!(original.projected_sha256, current.projected_sha256);
    assert_ne!(original.authored_sha256, current.authored_sha256);
    effects.store(0, Ordering::SeqCst);
    let result = runtime::checked_effect(
        &original_identity,
        || identity(&current),
        || {
            effects.fetch_add(1, Ordering::SeqCst);
            Ok(())
        },
    );
    assert!(result.is_err());
    assert_eq!(effects.load(Ordering::SeqCst), 0);
    // An active-body edit must fail the unchanged recipe pin itself.
    std::fs::write(
        &path,
        raw.replace(
            "ca/teamdman/sfm/client/jei/**",
            "ca/teamdman/sfm/client/changed/**",
        ),
    )?;
    assert!(
        roles::exclusion_role(
            &mut report,
            temp.path(),
            role,
            witness,
            &selection,
            &context
        )
        .is_err()
    );
    Ok(())
}
