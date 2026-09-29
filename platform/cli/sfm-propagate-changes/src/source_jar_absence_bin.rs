//! Read-only disabled-feature absence check for an already-built release JAR.

use eyre::WrapErr;
use sfm_propagate_changes::source_projection::manifest::SourceProjectionManifest;
use sfm_propagate_changes::source_projection::release_jar_absence::check_jar_reader;
use sfm_propagate_changes::terminal_output::stdout_line;
use std::collections::BTreeMap;
use std::ffi::OsString;
use std::fs;
use std::fs::File;
use std::path::PathBuf;

const USAGE: &str = "Usage: source-jar-absence --manifest <source-projection.json> --target <id> --preset <id> --jar <built.jar>";

fn required_arg(args: &mut BTreeMap<String, OsString>, key: &str) -> eyre::Result<OsString> {
    args.remove(key)
        .ok_or_else(|| eyre::eyre!("missing {key}; {USAGE}"))
}

fn string_arg(args: &mut BTreeMap<String, OsString>, key: &str) -> eyre::Result<String> {
    required_arg(args, key)?
        .into_string()
        .map_err(|value| eyre::eyre!("{key} must be UTF-8: {value:?}"))
}

fn run() -> eyre::Result<()> {
    let mut values = BTreeMap::new();
    let mut input = std::env::args_os().skip(1);
    while let Some(option) = input.next() {
        if option == "--help" || option == "-h" {
            stdout_line(USAGE)?;
            return Ok(());
        }
        let key = option
            .into_string()
            .map_err(|value| eyre::eyre!("option names must be UTF-8: {value:?}; {USAGE}"))?;
        eyre::ensure!(
            matches!(
                key.as_str(),
                "--manifest" | "--target" | "--preset" | "--jar"
            ),
            "unknown option {key}; {USAGE}"
        );
        let value = input
            .next()
            .ok_or_else(|| eyre::eyre!("missing value for {key}; {USAGE}"))?;
        eyre::ensure!(
            values.insert(key.clone(), value).is_none(),
            "duplicate {key}"
        );
    }

    let manifest_path = PathBuf::from(required_arg(&mut values, "--manifest")?);
    let target_id = string_arg(&mut values, "--target")?;
    let preset_id = string_arg(&mut values, "--preset")?;
    let jar_path = PathBuf::from(required_arg(&mut values, "--jar")?);
    let manifest_json = fs::read_to_string(&manifest_path)
        .wrap_err_with(|| format!("could not read manifest {}", manifest_path.display()))?;
    let manifest = SourceProjectionManifest::from_json(&manifest_json)?;
    let jar = File::open(&jar_path)
        .wrap_err_with(|| format!("could not open JAR {}", jar_path.display()))?;
    let report = check_jar_reader(&manifest, &target_id, &preset_id, jar)?;
    let status = if report.checked_features.is_empty() {
        "NO_APPLICABLE_FEATURES"
    } else {
        "PASS"
    };
    stdout_line(format!(
        "{status}: target={} preset={} disabled_features={} forbidden_rules={} inspected_jar_entries={}",
        report.target_id,
        report.preset_id,
        report.checked_features.join(","),
        report.forbidden_entry_rules,
        report.inspected_entries
    ))?;
    Ok(())
}

fn main() -> eyre::Result<()> {
    run()
}
