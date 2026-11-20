use super::core_catalog::write_project_catalog_fixture;
use super::core_features::FEATURE_DEFINITIONS_PATH;
use super::projection_definitions::CATALOG_PATH;
use super::sfm_repo_path::SfmRepoPath;
use crate::cancellation::CancellationToken;
use crate::cli::InvocationHomes;
use crate::cli::source::SourceArgs;
use crate::cli::source::SourceCommand;
use crate::cli::source::SourceProjectionArgs;
use crate::cli::source::SourceProjectionCheckArgs;
use crate::cli::source::SourceProjectionCommand;
use crate::cli::source::SourceRenderArgs;
use crate::paths::AppHome;
use eyre::Result;
use std::fs;
use tempfile::tempdir;

const PROJECTION: &str = "custom/mc-1.19.2";

#[test]
fn reverse_lookup_resolves_an_exact_project_without_feature_definitions() -> Result<()> {
    let fixture = tempdir()?;
    let project = write_project_catalog_fixture(fixture.path(), PROJECTION, "1.21")?;
    let registry_path = fixture.path().join(FEATURE_DEFINITIONS_PATH);
    let expected_repo = SfmRepoPath::try_from(fixture.path())?;
    fs::remove_file(&registry_path)?;
    for invalid_registry in [None, Some(b"not JSON".as_slice())] {
        if let Some(bytes) = invalid_registry {
            fs::write(&registry_path, bytes)?;
        }
        let (repo, key) = SfmRepoPath::for_projection(&project)?;
        assert_eq!(repo, expected_repo);
        assert_eq!(key, PROJECTION);
    }
    let child = project.join("src");
    fs::create_dir(&child)?;
    assert!(SfmRepoPath::for_projection(&child).is_err());
    Ok(())
}

#[test]
fn render_and_check_reject_invalid_enabled_features() -> Result<()> {
    let fixture = tempdir()?;
    let repo = fixture.path();
    let projections_path = repo.join(CATALOG_PATH);
    let registry_path = repo.join(FEATURE_DEFINITIONS_PATH);
    fs::create_dir_all(registry_path.parent().unwrap())?;
    fs::write(
        &registry_path,
        r#"{
            "base": {"supported_targets": ["1.19.2"], "requires": []},
            "dependent": {"supported_targets": ["1.19.2"], "requires": ["base"]},
            "other_target": {"supported_targets": ["1.21.0"], "requires": []}
        }"#,
    )?;
    let homes = InvocationHomes::new(
        AppHome(repo.join("unused-application-home")),
        Err(eyre::eyre!("projection render and check need no cache")),
    );
    for (enabled, diagnostic) in [
        ("unknown", "unknown enabled feature `unknown`"),
        (
            "other_target",
            "enabled feature `other_target` does not support target `1.19.2`",
        ),
        (
            "dependent",
            "enabled feature `dependent` requires enabled feature `base`",
        ),
    ] {
        fs::write(
            &projections_path,
            format!(
                r#"{{"{PROJECTION}": {{"minecraft_version": "1.19.2", "environment": "dev", "features": ["{enabled}"]}}}}"#
            ),
        )?;
        for command in [
            SourceCommand::Render(SourceRenderArgs {
                repo_root: repo.to_path_buf(),
                projection: PROJECTION.to_owned(),
                file: "src/main/java/Example.java".to_owned(),
            }),
            SourceCommand::Projection(SourceProjectionArgs {
                command: SourceProjectionCommand::Check(SourceProjectionCheckArgs {
                    repo_root: Some(repo.to_path_buf()),
                    projection: PROJECTION.to_owned(),
                }),
            }),
        ] {
            let error = SourceArgs { command }
                .invoke_in(&CancellationToken::new(), repo, &homes)
                .unwrap_err();
            assert!(format!("{error:#}").contains(diagnostic), "{error:#}");
        }
    }
    Ok(())
}
