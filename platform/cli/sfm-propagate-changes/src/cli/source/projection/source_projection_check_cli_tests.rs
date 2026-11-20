use crate::cancellation::CancellationToken;
use crate::cli::Cli;
use crate::cli::InvocationHomes;
use crate::cli::output::CliOutput;
use crate::cli::output::OutputFormat;
use crate::paths::AppHome;
use crate::source_projection::catalog_owned_project::source_check_observation;
use crate::source_projection::catalog_owned_project::tests::Fixture;
use crate::source_projection::core_features::FEATURE_DEFINITIONS_PATH;
use crate::source_projection::projection_catalog::CATALOG_PATH;
use eyre::Result;
use facet_value::Value;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::time::SystemTime;
use walkdir::WalkDir;

fn invoke(repo_root: &Path, selector: &str, invocation_dir: &Path) -> Result<CliOutput> {
    let root_argument = repo_root
        .to_str()
        .ok_or_else(|| eyre::eyre!("fixture root is not UTF-8"))?;
    let cli = figue::from_slice::<Cli>(&[
        "--output-format",
        "json",
        "source",
        "projection",
        "check",
        "--repo-root",
        root_argument,
        "--projection",
        selector,
    ])
    .into_result()
    .map_err(|error| eyre::eyre!("projection check arguments did not parse: {error:?}"))?
    .get_silent();
    let homes = InvocationHomes::new(
        AppHome(invocation_dir.join("unused-application-home")),
        Err(eyre::eyre!(
            "source checks must not require a toolchain cache"
        )),
    );
    cli.invoke_in(CancellationToken::new(), invocation_dir, &homes)
}

fn rows(output: &CliOutput) -> Result<Vec<BTreeMap<String, Value>>> {
    let json = output
        .render(Some(OutputFormat::Json), false)?
        .ok_or_else(|| eyre::eyre!("projection check returned no report"))?;
    Ok(facet_json::from_str(&json)?)
}

fn text<'a>(row: &'a BTreeMap<String, Value>, field: &str) -> &'a str {
    row.get(field)
        .and_then(Value::as_string)
        .map(|value| value.as_str())
        .expect("the report field must be a string")
}

type FixtureSnapshot = BTreeMap<PathBuf, Option<(Vec<u8>, SystemTime)>>;

fn snapshot(root: &Path) -> Result<FixtureSnapshot> {
    WalkDir::new(root)
        .into_iter()
        .map(|entry| {
            let entry = entry?;
            let metadata = entry.metadata()?;
            // NTFS can defer updating directory timestamps after file creation.
            let file = if metadata.is_file() {
                Some((fs::read(entry.path())?, metadata.modified()?))
            } else {
                None
            };
            Ok((entry.path().strip_prefix(root)?.to_path_buf(), file))
        })
        .collect()
}

fn assert_snapshot(root: &Path, before: FixtureSnapshot) -> Result<()> {
    let after = snapshot(root)?;
    assert_eq!(
        after.keys().collect::<Vec<_>>(),
        before.keys().collect::<Vec<_>>()
    );
    for (path, previous) in before {
        assert!(
            after.get(&path) == Some(&previous),
            "file changed: {}",
            path.display()
        );
    }
    Ok(())
}

#[test]
fn exact_projection_check_uses_explicit_invocation_root_and_returns_one_current_row() -> Result<()>
{
    let fixture = Fixture::new_for_target("1.19.2");
    let projection = Fixture::key(0, "release");
    let project = fixture.publish(&projection);
    let before = snapshot(fixture.repository())?;

    let output = invoke(Path::new("."), &projection, fixture.repository())?;
    assert_eq!(output.exit_code(), 0);
    let report = rows(&output)?;
    assert_eq!(report.len(), 1);
    assert_eq!(text(&report[0], "status"), "current");
    assert_eq!(text(&report[0], "projection"), projection);
    assert_eq!(report[0].len(), 2);
    assert_snapshot(fixture.repository(), before)?;
    assert!(!project.join("build").exists());
    Ok(())
}

#[test]
fn check_reports_failure_when_either_document_changes_after_capture() -> Result<()> {
    let fixture = Fixture::new_for_target("1.19.2");
    let projection = Fixture::key(0, "release");
    fixture.publish(&projection);
    for relative in [CATALOG_PATH, FEATURE_DEFINITIONS_PATH] {
        let path = fixture.repository().join(relative);
        let original = fs::read(&path)?;
        let mut changed = original.clone();
        changed.push(b'\n');
        let observed_path = path.clone();
        let replacement = changed.clone();
        let observation = source_check_observation::install(move |check| {
            if check == "authored" {
                fs::write(&observed_path, &replacement).expect("edit the captured document");
            }
        });
        let output = invoke(Path::new("."), &projection, fixture.repository())?;
        drop(observation);

        assert_eq!(output.exit_code(), 1, "{relative}");
        let report = rows(&output)?;
        assert_eq!(report.len(), 1);
        assert_eq!(text(&report[0], "status"), "failed", "{relative}");
        assert_eq!(text(&report[0], "projection"), projection);
        assert!(
            text(&report[0], "error").contains("authored metadata or source inventory changed")
        );
        assert_eq!(fs::read(&path)?, changed, "{relative}");
        fs::write(path, original)?;
    }
    Ok(())
}

#[test]
fn glob_check_reports_failed_ownership_then_checks_the_later_current_projection() -> Result<()> {
    let fixture = Fixture::new_for_target("1.19.2");
    let failed = Fixture::key(0, "release");
    let current = Fixture::key(0, "dev");
    assert!(failed < current);
    let failed_root = fixture.publish(&failed);
    fixture.publish(&current);
    let extra = failed_root.join("src/main/java/Unowned.java");
    let contributed = b"class Unowned { int keepContributorEdit; }\n";
    fs::write(&extra, contributed)?;
    let outside = tempfile::tempdir()?;
    let before = snapshot(fixture.repository())?;
    let outside_before = snapshot(outside.path())?;

    let output = invoke(fixture.repository(), "*/*/slot-0", outside.path())?;
    assert_eq!(output.exit_code(), 1);
    let report = rows(&output)?;
    assert_eq!(report.len(), 2);
    assert_eq!(text(&report[0], "status"), "failed");
    assert_eq!(text(&report[0], "projection"), failed);
    assert!(text(&report[0], "error").contains("unowned source inputs"));
    assert_eq!(report[0].len(), 3);
    assert_eq!(text(&report[1], "status"), "current");
    assert_eq!(text(&report[1], "projection"), current);
    assert_eq!(report[1].len(), 2);
    assert_eq!(fs::read(extra)?, contributed);
    assert_snapshot(fixture.repository(), before)?;
    assert_snapshot(outside.path(), outside_before)?;
    Ok(())
}

#[test]
fn unmatched_projection_glob_is_an_error_and_leaves_the_fixture_unchanged() -> Result<()> {
    let fixture = Fixture::new_for_target("1.19.2");
    fixture.publish(&Fixture::key(0, "release"));
    let before = snapshot(fixture.repository())?;
    let error = invoke(
        Path::new("."),
        "not-a-catalog-projection/*",
        fixture.repository(),
    )
    .unwrap_err();
    assert!(error.to_string().contains("no projections"), "{error:?}");
    assert_snapshot(fixture.repository(), before)?;
    Ok(())
}

#[test]
fn malformed_projection_glob_is_an_error_and_leaves_the_fixture_unchanged() -> Result<()> {
    let fixture = Fixture::new_for_target("1.19.2");
    fixture.publish(&Fixture::key(0, "release"));
    let before = snapshot(fixture.repository())?;
    let error = invoke(Path::new("."), "[", fixture.repository()).unwrap_err();
    assert!(
        error.to_string().to_lowercase().contains("pattern"),
        "{error:?}"
    );
    assert_snapshot(fixture.repository(), before)?;
    Ok(())
}
