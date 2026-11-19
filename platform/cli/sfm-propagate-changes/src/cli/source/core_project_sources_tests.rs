use super::super::CoreProjectArgs;
use super::super::CoreProjectCommand;
use super::*;
use crate::paths::AppHome;
use crate::paths::CacheHome;

fn test_homes(root: &Path) -> InvocationHomes {
    InvocationHomes::new(
        AppHome(root.join("settings")),
        Ok(CacheHome(root.join("canonical"))),
    )
}

fn source_command(
    repo: &Path,
    projection: &str,
    recipe: &str,
    cache_root: PathBuf,
    java_home: PathBuf,
) -> CoreProjectArgs {
    CoreProjectArgs {
        command: CoreProjectCommand::Sources(CoreProjectSourcesArgs {
            project: CoreProjectSelectionArgs {
                repo_root: Some(repo.to_path_buf()),
                projection: projection.to_owned(),
            },
            released_recipe: recipe.to_owned(),
            java_home: Some(java_home),
            cache_root: Some(cache_root),
            layout: SourceOutputLayout::Jar,
        }),
    }
}

#[test]
fn public_sources_rejects_unavailable_or_development_targets_before_effects() -> Result<()> {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(3)
        .unwrap();
    let temp = tempfile::tempdir()?;
    let recipe_error = if cfg!(target_os = "linux") {
        "requires its exact reviewed Windows/Linux recipe"
    } else {
        "released durable source views currently require Linux"
    };
    for (projection, recipe, expected) in [
        (
            "sfm-4.34.0/mc-1.20.2",
            "sfm:released-native-inputs/4.34.0/1.20.3@1",
            recipe_error,
        ),
        (
            "sfm-dev/mc-1.20.2",
            "sfm:released-native-inputs/4.34.0/1.20.2@1",
            "source project sources requires a released projection",
        ),
    ] {
        let command = source_command(
            repo,
            projection,
            recipe,
            temp.path().join("must-not-create"),
            temp.path().join("must-not-inspect-sdk"),
        );
        let error = command
            .invoke_in(&CancellationToken::new(), repo, &test_homes(temp.path()))
            .err()
            .expect("sources must refuse the request");
        assert!(error.to_string().contains(expected), "{error:#}");
        assert!(!temp.path().join("must-not-create").exists());
    }
    Ok(())
}
