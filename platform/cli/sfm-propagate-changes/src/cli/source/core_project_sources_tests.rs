use super::super::CoreProjectArgs;
use super::super::CoreProjectCommand;
use super::*;

fn parsed(extra: &[&str]) -> CoreProjectArgs {
    let mut args = vec![
        "sources",
        "--projection",
        "sfm-4.34.0/mc-1.20.2",
        "--released-recipe",
        "sfm:released-native-inputs/4.34.0/1.20.2@1",
    ];
    args.extend_from_slice(extra);
    figue::from_slice::<CoreProjectArgs>(&args)
        .into_result()
        .unwrap()
        .get_silent()
}

#[test]
fn public_sources_parser_has_only_explicit_recipe_and_managed_layout() {
    for (extra, layout) in [
        (vec![], SourceOutputLayout::Jar),
        (vec!["--layout", "filetree"], SourceOutputLayout::Filetree),
    ] {
        let CoreProjectCommand::Sources(args) = parsed(&extra).command else {
            panic!("sources variant")
        };
        assert_eq!(args.layout, layout);
    }
    for extra in [
        vec!["--source-jar", "foreign.jar"],
        vec!["--output-root", "foreign"],
        vec!["--dependency-profile", "dev"],
        vec!["--skip-source-checks"],
        vec!["--refresh"],
        vec!["--wait-for-build-lock"],
    ] {
        let mut args = vec![
            "sources",
            "--projection",
            "sfm-4.34.0/mc-1.20.2",
            "--released-recipe",
            "sfm:released-native-inputs/4.34.0/1.20.2@1",
        ];
        args.extend(extra);
        assert!(
            figue::from_slice::<CoreProjectArgs>(&args)
                .into_result()
                .is_err()
        );
    }
    assert!(
        figue::from_slice::<CoreProjectArgs>(&["sources", "--projection", "sfm-4.34.0/mc-1.20.2"])
            .into_result()
            .is_err()
    );
}

#[test]
fn public_sources_rejects_wrong_recipe_unsupported_loader_and_dev_before_effects() -> Result<()> {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .unwrap();
    let temp = tempfile::tempdir()?;
    for (projection, recipe) in [
        (
            "sfm-4.34.0/mc-1.20.2",
            "sfm:released-native-inputs/4.34.0/1.20.3@1",
        ),
        (
            "sfm-4.34.0/mc-1.19.2",
            "sfm:released-native-inputs/4.34.0/1.19.2@1",
        ),
        (
            "sfm-dev/mc-1.20.2",
            "sfm:released-native-inputs/4.34.0/1.20.2@1",
        ),
    ] {
        let mut command = parsed(&[]);
        let CoreProjectCommand::Sources(args) = &mut command.command else {
            unreachable!()
        };
        args.project.repo_root = Some(repo.to_path_buf());
        args.project.projection = projection.to_owned();
        args.released_recipe = recipe.to_owned();
        args.cache_root = Some(temp.path().join("must-not-create"));
        args.java_home = Some(temp.path().join("must-not-inspect-sdk"));
        assert!(command.invoke_in(&CancellationToken::new(), repo).is_err());
        assert!(!temp.path().join("must-not-create").exists());
    }
    Ok(())
}

#[test]
#[cfg(target_os = "linux")]
fn public_sources_valid_recipe_reaches_sdk_guard_without_compiling() -> Result<()> {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .unwrap();
    let temp = tempfile::tempdir()?;
    let mut command = parsed(&[]);
    let CoreProjectCommand::Sources(args) = &mut command.command else {
        unreachable!()
    };
    args.project.repo_root = Some(repo.to_path_buf());
    args.cache_root = Some(temp.path().join("native"));
    args.java_home = Some(temp.path().join("missing-sdk"));
    let error = command
        .invoke_in(&CancellationToken::new(), repo)
        .err()
        .unwrap();
    assert!(!format!("{error:#}").contains("requires an exact reviewed NeoForm"));
    assert!(!temp.path().join("native").exists());
    Ok(())
}
