use super::CatalogReleaseAction;
use super::CatalogReleaseAmendTargets;
use super::CatalogReleasePreparation;
use super::CatalogReleaseProviderRequest;
use super::catalog_release_artifact_tests::write_fixture_artifact;
use super::catalog_release_compatibility::CatalogReleaseCompatibility;
use super::catalog_release_fixture::CatalogReleaseFixture;
use super::catalog_release_fixture::inventory;
use crate::cancellation::CancellationToken;
use crate::cli::Cli;
use crate::cli::InvocationHomes;
use crate::cli::output::OutputFormat;
use crate::curseforge::CurseforgeGameVersion;
use crate::curseforge::CurseforgeGameVersionId;
use crate::curseforge::CurseforgeGameVersionTypeId;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use crate::source_projection::projection_definition::SUPPORTED_TARGETS;
use eyre::Result;
use std::fs;

fn provider_bytes(fixture: &CatalogReleaseFixture) -> Result<Vec<u8>> {
    let metadata = fixture.prepare()?;
    let compatibility = CatalogReleaseCompatibility::for_metadata(&metadata)?;
    let rows = compatibility
        .curseforge_names
        .iter()
        .enumerate()
        .map(|(slot, name)| {
            let kind = match name.as_str() {
                "Client" | "Server" => 75_208,
                "Forge" | "NeoForge" => 68_441,
                name if name.starts_with("Java ") => 2,
                _ => 777,
            };
            CurseforgeGameVersion {
                id: CurseforgeGameVersionId(u64::try_from(slot).unwrap() + 100),
                game_version_type_id: Some(CurseforgeGameVersionTypeId(kind)),
                name: name.clone(),
                slug: None,
            }
        })
        .collect::<Vec<_>>();
    Ok(facet_json::to_string(&rows)?.into_bytes())
}

fn initialize_git(root: &std::path::Path) -> Result<()> {
    let init = std::process::Command::new("git")
        .arg("init")
        .current_dir(root)
        .output()?;
    assert!(init.status.success());
    let commit = std::process::Command::new("git")
        .args([
            "-c",
            "user.name=SFM fixture",
            "-c",
            "user.email=fixture@example.invalid",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "--allow-empty",
            "-m",
            "Fixture",
        ])
        .current_dir(root)
        .output()?;
    assert!(
        commit.status.success(),
        "{}",
        String::from_utf8_lossy(&commit.stderr)
    );
    Ok(())
}

#[test]
fn every_target_prepares_verified_artifact_and_provider_payloads_without_writes() -> Result<()> {
    for (target, upstream) in SUPPORTED_TARGETS {
        let fixture = CatalogReleaseFixture::new(target)?;
        fixture.publish();
        initialize_git(fixture.repository())?;
        let jar = "build/libs/review.jar".to_owned();
        write_fixture_artifact(&fixture, &jar)?;
        let providers = CatalogReleaseProviderRequest::from_bytes(
            CatalogReleaseAction::Create,
            &provider_bytes(&fixture)?,
        )?;
        let before = inventory(fixture.repository())?;
        let prepared = CatalogReleasePreparation::prepare(
            &fixture.request(&fixture.key)?,
            &jar,
            &providers,
            &CancellationToken::new(),
        )?;
        let json = facet_json::to_string(&prepared)?;
        assert!(json.contains("\"artifact_readiness\":\"verified_local_native_receipt\""));
        assert!(json.contains("\"create\":"));
        assert!(json.contains("\"publication_authorized\":false"));
        assert!(json.contains("\"remote_state_verified\":false"));
        assert!(json.contains("\"performed\":false"));
        assert!(json.contains("\"created\":false"));
        assert!(json.contains(&format!("\"game_versions\":[\"{upstream}\"]")));
        assert!(json.contains(&format!(
            "Super Factory Manager (SFM)-MC{target}-4.35.0.jar"
        )));
        if target == "1.20.1" {
            assert!(json.contains("\"loaders\":[\"forge\",\"neoforge\"]"));
        }
        assert_eq!(before, inventory(fixture.repository())?);
    }
    Ok(())
}

#[test]
fn amendment_targets_are_explicit_and_create_and_amend_payloads_stay_distinct() -> Result<()> {
    assert!(CatalogReleaseAmendTargets::new("latest/version".to_owned(), 42).is_err());
    assert!(CatalogReleaseAmendTargets::new("version42".to_owned(), 0).is_err());
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    initialize_git(fixture.repository())?;
    let jar = "build/libs/review.jar".to_owned();
    write_fixture_artifact(&fixture, &jar)?;
    let action = CatalogReleaseAction::Amend(CatalogReleaseAmendTargets::new(
        "version42".to_owned(),
        123,
    )?);
    let providers = CatalogReleaseProviderRequest::from_bytes(action, &provider_bytes(&fixture)?)?;
    let before = inventory(fixture.repository())?;
    let report = CatalogReleasePreparation::prepare(
        &fixture.request(&fixture.key)?,
        &jar,
        &providers,
        &CancellationToken::new(),
    )?;
    let json = facet_json::to_string(&report)?;
    assert!(json.contains("version42"));
    assert!(json.contains("\"fileID\":123"));
    assert!(!json.contains("file_parts"));
    assert!(json.contains("\"amend\":"));
    assert!(json.contains("\"github_tag_name\":"));
    for create_only in ["target_commitish", "draft", "prerelease"] {
        assert!(!json.contains(&format!("\"{create_only}\":")));
    }
    assert_eq!(before, inventory(fixture.repository())?);
    Ok(())
}

#[test]
fn provider_catalog_rejects_duplicate_ids_and_missing_exact_metadata() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    fixture.publish();
    let mut rows: Vec<CurseforgeGameVersion> = facet_json::from_slice(&provider_bytes(&fixture)?)?;
    rows.push(rows[0].clone());
    assert!(
        CatalogReleaseProviderRequest::from_bytes(
            CatalogReleaseAction::Create,
            facet_json::to_string(&rows)?.as_bytes()
        )
        .is_err()
    );
    assert!(
        CatalogReleaseProviderRequest::from_bytes(CatalogReleaseAction::Create, b"[]").is_err()
    );
    rows.pop();
    let mut duplicate_name = rows[0].clone();
    duplicate_name.id.0 += 10_000;
    rows.push(duplicate_name);
    let ambiguous = CatalogReleaseProviderRequest::from_bytes(
        CatalogReleaseAction::Create,
        facet_json::to_string(&rows)?.as_bytes(),
    )?;
    let compatibility = CatalogReleaseCompatibility::for_metadata(&fixture.prepare()?)?;
    assert!(
        ambiguous
            .resolve_names(&compatibility.curseforge_names)
            .is_err()
    );
    rows.pop();
    rows.remove(0);
    let incomplete = CatalogReleaseProviderRequest::from_bytes(
        CatalogReleaseAction::Create,
        facet_json::to_string(&rows)?.as_bytes(),
    )?;
    let compatibility = CatalogReleaseCompatibility::for_metadata(&fixture.prepare()?)?;
    assert!(
        incomplete
            .resolve_names(&compatibility.curseforge_names)
            .is_err()
    );
    Ok(())
}

#[test]
fn cli_prepare_reaches_verified_payload_operation_with_explicit_inputs() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    fixture.publish();
    initialize_git(fixture.repository())?;
    let jar = "build/libs/review.jar".to_owned();
    write_fixture_artifact(&fixture, &jar)?;
    fs::write(
        fixture.repository().join("offline-versions.json"),
        provider_bytes(&fixture)?,
    )?;
    let temporary = tempfile::tempdir()?;
    let homes = InvocationHomes::new(
        AppHome(temporary.path().join("app")),
        Ok(CacheHome(temporary.path().join("cache"))),
    );
    let cli = figue::from_slice::<Cli>(&[
        "source",
        "release",
        "prepare",
        "--repo-root",
        ".",
        "--projection",
        &fixture.key,
        "--expected-mod-version",
        "4.35.0",
        "--jar",
        &jar,
        "--curseforge-game-versions",
        "offline-versions.json",
    ])
    .into_result()?
    .get_silent();
    let before = inventory(fixture.repository())?;
    let output = cli.invoke_in(CancellationToken::new(), fixture.repository(), &homes)?;
    let json = output.render(Some(OutputFormat::Json), false)?.unwrap();
    assert!(json.contains("sfm:catalog_release_preparation@1"));
    assert_eq!(before, inventory(fixture.repository())?);
    Ok(())
}

#[test]
fn tag_plan_observes_clean_and_annotated_tag_states_without_changing_refs() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    fixture.publish();
    initialize_git(fixture.repository())?;
    let jar = "build/libs/review.jar";
    write_fixture_artifact(&fixture, jar)?;
    let providers = CatalogReleaseProviderRequest::from_bytes(
        CatalogReleaseAction::Create,
        &provider_bytes(&fixture)?,
    )?;
    // These mutations establish isolated temporary Git data; the operation
    // under test must leave that data, including its existing tag, unchanged.
    let git = |arguments: &[&str]| -> Result<Vec<u8>> {
        let output = std::process::Command::new("git")
            .args([
                "-c",
                "user.name=SFM fixture",
                "-c",
                "user.email=fixture@example.invalid",
                "-c",
                "commit.gpgsign=false",
                "-c",
                "tag.gpgsign=false",
                "-c",
                "core.hooksPath=.git/no-hooks",
            ])
            .args(arguments)
            .current_dir(fixture.repository())
            .output()?;
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        Ok(output.stdout)
    };
    git(&["add", "--all"])?;
    git(&["commit", "--allow-empty", "-m", "Reviewed fixture inputs"])?;
    let before = inventory(fixture.repository())?;
    let prepare = || {
        CatalogReleasePreparation::prepare(
            &fixture.request(&fixture.key)?,
            jar,
            &providers,
            &CancellationToken::new(),
        )
    };
    let first = facet_json::to_string(&prepare()?)?;
    assert!(first.contains("\"working_tree_clean\":true"));
    assert!(first.contains("\"existing_object_id\":null"));
    assert!(!first.contains("\"eligible\":"));
    assert_eq!(before, inventory(fixture.repository())?);
    git(&[
        "tag",
        "--annotate",
        "4.35.0-1.19.2",
        "-m",
        "Existing fixture tag",
    ])?;
    let tag_object = String::from_utf8(git(&["rev-parse", "refs/tags/4.35.0-1.19.2"])?)?;
    let head = String::from_utf8(git(&["rev-parse", "HEAD"])?)?;
    assert_ne!(
        tag_object, head,
        "annotated tag observation is an object ID, not a peeled commit"
    );
    let before = inventory(fixture.repository())?;
    let second = facet_json::to_string(&prepare()?)?;
    assert!(second.contains(&format!("\"existing_object_id\":\"{}\"", tag_object.trim())));
    assert!(second.contains("\"created\":false"));
    assert_eq!(before, inventory(fixture.repository())?);
    Ok(())
}
