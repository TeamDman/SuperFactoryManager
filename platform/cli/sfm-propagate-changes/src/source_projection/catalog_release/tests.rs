use super::CatalogReleasePlan;
use super::catalog_release_fixture::CatalogReleaseFixture;
use super::catalog_release_fixture::OTHER_RELEASE_FEATURE;
use super::catalog_release_fixture::RELEASE_FEATURE;
use super::catalog_release_fixture::SECTION;
use super::catalog_release_fixture::inventory;
use super::catalog_release_plan::CHANGELOG;
use crate::cancellation::CancellationToken;
use crate::cli::Cli;
use crate::cli::InvocationHomes;
use crate::cli::output::OutputFormat;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use crate::source_projection::catalog_owned_project::recheck_observation;
use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
use crate::source_projection::core_inputs::CORE_ROOT;
use crate::source_projection::projection_definitions::CATALOG_PATH;
use crate::source_projection::projection_definitions::ProjectionDefinitions;
use crate::source_projection::provenance::sha256;
use crate::source_projection::sfm_projection_feature_registry::SfmProjectionFeatureRegistry;
use eyre::Result;
use std::cell::Cell;
use std::fs;
use std::path::Path;
use std::rc::Rc;

fn assert_json_string(json: &str, key: &str, value: &str) {
    let expected = format!("\"{key}\": {}", facet_json::to_string(value).unwrap());
    assert!(json.contains(&expected), "missing {expected} in {json}");
}

fn invoke_cli(
    fixture: &CatalogReleaseFixture,
    invocation_dir: &Path,
    root: Option<&str>,
) -> Result<String> {
    let mut args = vec![
        "source",
        "release",
        "plan",
        "--projection",
        fixture.key.as_str(),
        "--expected-mod-version",
        "4.35.0",
    ];
    if let Some(root) = root {
        args.extend(["--repo-root", root]);
    }
    let cli = figue::from_slice::<Cli>(&args).into_result()?.get_silent();
    let temporary = tempfile::tempdir()?;
    let homes = InvocationHomes::new(
        AppHome(temporary.path().join("settings")),
        Ok(CacheHome(temporary.path().join("cache"))),
    );
    let output = cli.invoke_in(CancellationToken::new(), invocation_dir, &homes)?;
    assert_eq!(output.exit_code(), 0);
    output
        .render(Some(OutputFormat::Json), false)?
        .ok_or_else(|| eyre::eyre!("missing release plan output"))
}

#[test]
fn arbitrary_catalog_key_retains_upstream_target_and_metadata_only_scope() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    let before = inventory(fixture.repository())?;
    let first = facet_json::to_string_pretty(&fixture.prepare()?)?;
    let second = facet_json::to_string_pretty(&fixture.prepare()?)?;
    assert_eq!(first, second);
    assert_eq!(inventory(fixture.repository())?, before);
    let section_sha256 = sha256(SECTION.as_bytes());
    for (key, value) in [
        ("schema", "sfm:catalog_release_plan@1"),
        ("tool_package_name", env!("CARGO_PKG_NAME")),
        ("tool_package_version", env!("CARGO_PKG_VERSION")),
        ("tool_repository", env!("CARGO_PKG_REPOSITORY")),
        ("tool_base_revision", env!("GIT_REVISION")),
        ("projection_key", fixture.key.as_str()),
        ("environment", "release"),
        ("target_id", "1.21.0"),
        ("minecraft_version", "1.21"),
        ("package_loader", "neoforge"),
        ("mod_version", "4.35.0"),
        ("release_feature", RELEASE_FEATURE),
        ("artifact_readiness", "not_assessed"),
        ("content", SECTION),
        ("sha256", section_sha256.as_str()),
    ] {
        assert_json_string(&first, key, value);
    }
    for value in [
        "\"tool_source_identity\": null",
        "\"java_major\": 21",
        "\"metadata_verified\": true",
        "\"provider_readiness\": \"not_prepared\"",
        "\"publication_authorized\": false",
        "\"source_writes_performed\": false",
        "\"network_requests_performed\": 0",
    ] {
        assert!(first.contains(value), "{first}");
    }
    assert!(!first.contains("Historical change"));
    assert!(!first.contains("Preamble stays"));
    assert!(!first.contains(fixture.repository().to_str().unwrap()));
    assert!(!first.contains("sfm owned project fixture"));
    assert!(!fixture.root().join("build").exists());
    Ok(())
}

#[test]
fn transitional_package_loader_does_not_choose_a_provider_compatibility_policy() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.20.1")?;
    fixture.publish();
    let report = facet_json::to_string_pretty(&fixture.prepare()?)?;
    assert_json_string(&report, "package_loader", "neoforge");
    assert_json_string(&report, "minecraft_version", "1.20.1");
    assert!(report.contains("\"provider_readiness\": \"not_prepared\""));
    assert!(!report.contains("modrinth_loaders"));
    assert!(!report.contains("dual-forge-neoforge"));
    Ok(())
}

#[test]
fn exact_unknown_and_unsafe_selectors_never_write_fixture_state() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    fixture.publish();
    let before = inventory(fixture.repository())?;
    for key in [
        "*",
        "published/*",
        "../outside",
        "/absolute",
        "published/../slot",
    ] {
        assert!(fixture.request(key).is_err(), "{key}");
    }
    let error = CatalogReleasePlan::prepare(
        &fixture.request("published/missing")?,
        &CancellationToken::new(),
    )
    .unwrap_err()
    .to_string();
    assert!(error.contains("unknown projection key"), "{error}");
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[test]
fn release_looking_contexts_cannot_replace_environment_and_feature_admission() -> Result<()> {
    let mut fixture = CatalogReleaseFixture::new("1.19.2")?;
    let definitions = SfmProjectionFeatureRegistry::from_json(&fs::read_to_string(
        fixture.repository().join(FEATURE_DEFINITIONS_PATH),
    )?)?;
    let catalog_path = fixture.repository().join(CATALOG_PATH);
    let mut catalog =
        ProjectionDefinitions::from_json(&fs::read_to_string(&catalog_path)?, &definitions)?;
    let dev_entry = catalog.0.remove(&fixture.dev_key).unwrap();
    fixture.dev_key = "sfm-4.35.0/mc-1.19.2".to_owned();
    catalog.0.insert(fixture.dev_key.clone(), dev_entry);
    fs::write(catalog_path, catalog.to_json(&definitions)?)?;
    let before = inventory(fixture.repository())?;
    let error = CatalogReleasePlan::prepare(
        &fixture.request(&fixture.dev_key)?,
        &CancellationToken::new(),
    )
    .unwrap_err()
    .to_string();
    assert!(error.contains("environment=release"), "{error}");
    assert_eq!(inventory(fixture.repository())?, before);
    for features in [
        vec![],
        vec![OTHER_RELEASE_FEATURE],
        vec![RELEASE_FEATURE, OTHER_RELEASE_FEATURE],
    ] {
        fixture.set_features(&fixture.key, &features)?;
        fixture.assert_rejected_without_writes("exactly the expected release feature")?;
    }
    assert!(!fixture.root().exists());
    Ok(())
}

#[test]
fn current_generated_ownership_does_not_excuse_metadata_disagreement() -> Result<()> {
    for (properties, diagnostic) in [
        (
            "mod_id=sfm\nmod_version=4.36.0\nminecraft_version=1.21\n",
            "mod_version disagrees",
        ),
        (
            "mod_id=another\nmod_version=4.35.0\nminecraft_version=1.21\n",
            "mod_id must be sfm",
        ),
        (
            "mod_id=sfm\nmod_version=4.35.0\nminecraft_version=1.21\nmod_version:4.35.0\n",
            "literal",
        ),
    ] {
        let mut fixture = CatalogReleaseFixture::new("1.21.0")?;
        fixture.set_properties(properties)?;
        fixture.publish();
        fixture.owned.collect(&fixture.key)?.check_current()?;
        fixture.assert_rejected_without_writes(diagnostic)?;
    }
    Ok(())
}

#[test]
fn stale_and_unowned_source_outputs_are_refused_without_repairs() -> Result<()> {
    let stale = CatalogReleaseFixture::new("1.19.2")?;
    stale.publish();
    fs::write(
        stale.root().join("gradle.properties"),
        "mod_id=sfm\nmod_version=4.35.0\n",
    )?;
    stale.assert_rejected_without_writes("generated output changed")?;

    let extra = CatalogReleaseFixture::new("1.19.2")?;
    extra.publish();
    fs::write(
        extra
            .root()
            .join("src/main/resources/obsolete-resource.json"),
        "{}\n",
    )?;
    extra.assert_rejected_without_writes("missing or extra unowned source inputs")?;
    Ok(())
}

#[test]
fn missing_projection_is_not_synchronized_by_release_planning() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    assert!(!fixture.root().exists());
    let before = inventory(fixture.repository())?;
    assert!(fixture.prepare().is_err());
    assert_eq!(inventory(fixture.repository())?, before);
    assert!(!fixture.root().exists());
    Ok(())
}

#[test]
fn flat_metadata_decoys_cannot_replace_an_unselected_changelog() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    fixture.publish();
    let flat = fixture.repository().join("platform/minecraft");
    fs::write(
        flat.join("gradle.properties"),
        "mod_id=decoy\nmod_version=9.99.99\nminecraft_version=26.1.2\n",
    )?;
    let flat_changelog = flat.join(CHANGELOG);
    fs::create_dir_all(flat_changelog.parent().unwrap())?;
    fs::write(&flat_changelog, "---- 9.99.99 ----\n-- Wrong flat notes.\n")?;
    let before = inventory(fixture.repository())?;
    let selected = facet_json::to_string_pretty(&fixture.prepare()?)?;
    assert_json_string(&selected, "mod_version", "4.35.0");
    assert_json_string(&selected, "minecraft_version", "1.19.2");
    assert_json_string(&selected, "content", SECTION);
    assert!(!selected.contains("Wrong flat notes"));
    assert_eq!(inventory(fixture.repository())?, before);

    fs::write(
        flat.join("gradle.properties"),
        "mod_id=sfm\nmod_version=4.35.0\nminecraft_version=1.19.2\n",
    )?;
    fs::write(flat_changelog, SECTION)?;
    fs::remove_file(fixture.repository().join(CORE_ROOT).join(CHANGELOG))?;
    fs::remove_file(fixture.root().join(CHANGELOG))?;
    fixture.owned.collect(&fixture.key)?.check_current()?;
    fixture.assert_rejected_without_writes("does not select")?;
    Ok(())
}

#[test]
fn precancelled_request_has_no_source_or_build_effects() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("release fixture cancelled before inspection");
    let before = inventory(fixture.repository())?;
    let error = CatalogReleasePlan::prepare(&fixture.request(&fixture.key)?, &cancellation)
        .unwrap_err()
        .to_string();
    assert!(error.contains("cancelled before inspection"), "{error}");
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[test]
fn final_recheck_refuses_changes_after_metadata_preparation() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.19.2")?;
    fixture.publish();
    let changed = b"changed after metadata preparation\n";
    let mut expected = inventory(fixture.repository())?;
    let relative = fixture
        .root()
        .join("gradle.properties")
        .strip_prefix(fixture.repository())?
        .to_path_buf();
    expected.insert(relative, Some(changed.to_vec()));
    let observed = Rc::new(Cell::new(false));
    let callback_observed = Rc::clone(&observed);
    let _scope = recheck_observation::install(move |project| {
        callback_observed.set(true);
        fs::write(project.project_root().join("gradle.properties"), changed).unwrap();
    });
    let error = fixture.prepare().unwrap_err().to_string();
    assert!(observed.get(), "final ownership recheck was skipped");
    assert!(error.contains("generated output changed"), "{error}");
    assert_eq!(inventory(fixture.repository())?, expected);
    Ok(())
}

#[test]
fn cli_dispatch_discovers_project_checkout_and_resolves_explicit_relative_root() -> Result<()> {
    let fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.publish();
    let before = inventory(fixture.repository())?;
    let discovered = invoke_cli(&fixture, &fixture.root(), None)?;
    let invocation_dir = fixture.repository().parent().unwrap();
    let relative_root = fixture.repository().file_name().unwrap().to_str().unwrap();
    let explicit = invoke_cli(&fixture, invocation_dir, Some(relative_root))?;
    assert_eq!(discovered, explicit);
    assert_json_string(&discovered, "target_id", "1.21.0");
    assert_json_string(&discovered, "minecraft_version", "1.21");
    assert_eq!(inventory(fixture.repository())?, before);
    Ok(())
}

#[test]
fn matrix_target_id_cannot_replace_upstream_minecraft_property() -> Result<()> {
    let mut fixture = CatalogReleaseFixture::new("1.21.0")?;
    fixture.set_properties("mod_id=sfm\nmod_version=4.35.0\nminecraft_version=1.21.0\n")?;
    fixture.assert_rejected_without_writes("needs exactly one minecraft_version=1.21")?;
    assert!(!fixture.root().exists());
    Ok(())
}
