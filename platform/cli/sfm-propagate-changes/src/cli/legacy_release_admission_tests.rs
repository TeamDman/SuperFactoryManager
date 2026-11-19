use super::Cli;
use super::InvocationHomes;
use super::legacy_release_admission::LegacyReleaseAdmission;
use crate::cancellation::CancellationToken;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use crate::paths::InvocationDirectory;
use eyre::Result;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use walkdir::WalkDir;

fn release_commands() -> Vec<Vec<&'static str>> {
    let mut commands = Vec::new();
    for provider in ["github", "curseforge", "modrinth"] {
        let verbs: &[&str] = if provider == "github" {
            &["now", "amend"]
        } else {
            &["now", "amend", "check", "validate"]
        };
        for verb in verbs {
            commands.push(vec![provider, "release", verb, "--branch", "popular"]);
        }
    }
    commands.push(vec!["jar", "collect"]);
    commands.push(vec!["git", "tag"]);
    commands
}

fn parse(arguments: &[&str]) -> Result<Cli> {
    figue::from_slice::<Cli>(arguments)
        .into_result()
        .map(|parsed| parsed.get_silent())
        .map_err(|error| eyre::eyre!("release admission fixture did not parse: {error:?}"))
}

fn homes(root: &Path) -> InvocationHomes {
    InvocationHomes::new(
        AppHome(root.join("settings")),
        Ok(CacheHome(root.join("unused-cache"))),
    )
}

fn catalog(root: &Path) -> Result<()> {
    fs::create_dir_all(root.join("platform/minecraft"))?;
    // Presence, rather than successful catalog parsing, retires this route.
    fs::write(
        root.join("platform/minecraft/projections.json"),
        b"invalid catalog\xff",
    )?;
    Ok(())
}

fn configure_root(homes: &InvocationHomes, root: &Path) -> Result<()> {
    homes.app_home().ensure_dir()?;
    fs::write(
        homes.app_home().file_path("repo_root.txt"),
        root.to_string_lossy().as_bytes(),
    )?;
    Ok(())
}

fn snapshot(root: &Path) -> Result<BTreeMap<PathBuf, Vec<u8>>> {
    let mut result = BTreeMap::new();
    for entry in WalkDir::new(root) {
        let entry = entry?;
        let relative = entry.path().strip_prefix(root)?.to_path_buf();
        let bytes = if entry.file_type().is_file() {
            fs::read(entry.path())?
        } else {
            Vec::new()
        };
        result.insert(relative, bytes);
    }
    Ok(result)
}

#[test]
fn every_legacy_release_route_rejects_catalog_cwd_before_configuration_or_selectors() -> Result<()>
{
    let temporary = tempfile::tempdir()?;
    let invocation = temporary.path().join("catalog");
    catalog(&invocation)?;
    let homes = homes(temporary.path());
    let invocation_dir = InvocationDirectory::new(&invocation)?;
    let before = snapshot(temporary.path())?;
    let commands = release_commands();
    assert_eq!(commands.len(), 12);
    for arguments in commands {
        let cli = parse(&arguments)?;
        assert!(!cli.defers_file_logging());
        let logging = cli.logging_config(&invocation_dir, &homes)?;
        assert_eq!(
            logging.json_log_directory,
            Some(homes.cache_home()?.join("logs"))
        );
        let error = cli
            .invoke_in(CancellationToken::new(), &invocation, &homes)
            .unwrap_err();
        assert!(
            error.to_string().contains("invocation checkout"),
            "{arguments:?}: {error:#}"
        );
        assert!(
            error.to_string().contains("source release plan"),
            "{error:#}"
        );
    }
    assert_eq!(snapshot(temporary.path())?, before);
    Ok(())
}

#[test]
fn legacy_release_rejects_configured_catalog_from_outside_git() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let invocation = temporary.path().join("outside");
    fs::create_dir(&invocation)?;
    let configured = temporary.path().join("catalog");
    catalog(&configured)?;
    let homes = homes(temporary.path());
    configure_root(&homes, &configured)?;
    let before = snapshot(temporary.path())?;
    let error = parse(&["jar", "collect"])?
        .invoke_in(CancellationToken::new(), &invocation, &homes)
        .unwrap_err();
    assert!(
        error.to_string().contains("configured legacy repository"),
        "{error:#}"
    );
    assert_eq!(snapshot(temporary.path())?, before);
    Ok(())
}

#[test]
fn invocation_catalog_takes_precedence_over_unrelated_configured_legacy_root() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let invocation = temporary.path().join("catalog");
    catalog(&invocation)?;
    let legacy = temporary.path().join("legacy");
    fs::create_dir(&legacy)?;
    let homes = homes(temporary.path());
    configure_root(&homes, &legacy)?;
    let error = LegacyReleaseAdmission::new(&invocation, homes.app_home()).unwrap_err();
    assert!(error.to_string().contains("invocation checkout"));
    Ok(())
}

#[test]
fn nested_catalog_directories_resolve_the_containing_checkout() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let checkout = temporary.path().join("catalog");
    catalog(&checkout)?;
    gix::init(&checkout)?;
    let nested = checkout.join("platform/minecraft/projections/fixture");
    fs::create_dir_all(&nested)?;
    let homes = homes(temporary.path());
    let error = LegacyReleaseAdmission::new(&nested, homes.app_home()).unwrap_err();
    assert!(error.to_string().contains("invocation checkout"));
    assert!(error.to_string().contains("uses projections"));
    Ok(())
}

#[test]
fn malformed_git_marker_cannot_hide_an_ancestor_catalog() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let checkout = temporary.path().join("catalog");
    catalog(&checkout)?;
    let nested = checkout.join("nested");
    fs::create_dir(&nested)?;
    fs::write(nested.join(".git"), b"malformed Git marker")?;
    let homes = homes(temporary.path());
    let error = LegacyReleaseAdmission::new(&nested, homes.app_home()).unwrap_err();
    assert!(error.to_string().contains("invocation checkout"));
    Ok(())
}

#[test]
fn noncatalog_legacy_checkout_is_admitted_without_changes() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let legacy = temporary.path().join("legacy");
    fs::create_dir(&legacy)?;
    gix::init(&legacy)?;
    let invocation = legacy.join("nested");
    fs::create_dir(&invocation)?;
    let homes = homes(temporary.path());
    configure_root(&homes, &legacy)?;
    let before = snapshot(temporary.path())?;
    let _admission = LegacyReleaseAdmission::new(&invocation, homes.app_home())?;
    assert_eq!(snapshot(temporary.path())?, before);
    Ok(())
}

#[test]
fn catalog_marker_directory_cannot_bypass_release_admission() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    fs::create_dir_all(temporary.path().join("platform/minecraft/projections.json"))?;
    let homes = homes(temporary.path());
    let error = LegacyReleaseAdmission::new(temporary.path(), homes.app_home()).unwrap_err();
    assert!(error.to_string().contains("invocation checkout"));
    Ok(())
}

#[test]
fn catalog_checkout_retains_metadata_and_configuration_repair_commands() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    catalog(temporary.path())?;
    let homes = homes(temporary.path());
    configure_root(&homes, temporary.path())?;
    parse(&["home", "path"])?.invoke_in(CancellationToken::new(), temporary.path(), &homes)?;
    parse(&["repo-root", "show"])?.invoke_in(CancellationToken::new(), temporary.path(), &homes)?;
    let jar_error = parse(&["jar", "list"])?
        .invoke_in(CancellationToken::new(), temporary.path(), &homes)
        .unwrap_err();
    assert!(jar_error.to_string().contains("Jar dir not set"));
    parse(&["repo-root", "unset"])?.invoke_in(
        CancellationToken::new(),
        temporary.path(),
        &homes,
    )?;
    assert!(!homes.app_home().file_path("repo_root.txt").exists());
    Ok(())
}

#[test]
fn relative_legacy_root_is_rejected_before_ambient_target_resolution() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let invocation = temporary.path().join("outside");
    fs::create_dir(&invocation)?;
    let configured = invocation.join("relative-selected-catalog");
    catalog(&configured)?;
    let homes = homes(temporary.path());
    configure_root(&homes, Path::new("relative-selected-catalog"))?;
    let before = snapshot(temporary.path())?;
    let error = parse(&["jar", "collect"])?
        .invoke_in(CancellationToken::new(), &invocation, &homes)
        .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("absolute configured repository root"),
        "{error:#}"
    );
    assert_eq!(snapshot(temporary.path())?, before);
    Ok(())
}
