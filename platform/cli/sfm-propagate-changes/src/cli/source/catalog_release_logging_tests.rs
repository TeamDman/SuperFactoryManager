use crate::cancellation::CancellationToken;
use crate::cli::Cli;
use crate::logging::init_logging;
use crate::paths::APP_HOME_DIR_NAME;
use crate::paths::APP_HOME_ENV_VAR;
use crate::paths::AppHome;
use directories_next::ProjectDirs;
use eyre::Result;
use eyre::WrapErr;
use std::fs;
use std::path::PathBuf;
use std::process::Command;
use std::process::Output;
use std::process::Stdio;
use std::time::Duration;
use std::time::Instant;

const WORKER_HOME: &str = "SFM_CATALOG_RELEASE_LOGGING_TEST_HOME";
const WORKER_PLATFORM_DEFAULT: &str = "SFM_CATALOG_RELEASE_LOGGING_TEST_PLATFORM_DEFAULT";
const WORKER_TEST: &str = "cli::source::catalog_release_logging_tests::release_plan_logging_worker";
const LOG_MARKER: &str = "catalog release plan logging fixture";
const CONFIGURATION_MARKER: &str = "catalog release log destination checked";

fn parse(arguments: &[&str]) -> Result<Cli> {
    figue::from_slice::<Cli>(arguments)
        .into_result()
        .map(|parsed| parsed.get_silent())
        .map_err(|_| eyre::eyre!("release plan fixture arguments did not parse"))
}

fn bounded_output(command: &mut Command) -> Result<Output> {
    let mut child = command
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()?;
    let deadline = Instant::now() + Duration::from_secs(15);
    let completed = loop {
        match child.try_wait() {
            Ok(Some(_)) => break Ok(()),
            Ok(None) if Instant::now() < deadline => {
                std::thread::sleep(Duration::from_millis(10));
            }
            Ok(None) => {
                break Err(eyre::eyre!(
                    "logging worker exceeded its 15-second deadline"
                ));
            }
            Err(error) => break Err(error).wrap_err("cannot poll logging worker"),
        }
    };
    if let Err(error) = completed {
        let killed = child.kill();
        let reaped = child.wait();
        return Err(error).wrap_err(format!(
            "exact logging worker cleanup: kill={killed:?}, wait={reaped:?}"
        ));
    }
    Ok(child.wait_with_output()?)
}

#[test]
#[ignore = "Runs only in an isolated child with a test application home"]
fn release_plan_logging_worker() -> Result<()> {
    let platform_default = std::env::var_os(WORKER_PLATFORM_DEFAULT).is_some();
    let expected_home = match std::env::var_os(WORKER_HOME) {
        Some(path) if !platform_default => PathBuf::from(path),
        _ if platform_default => {
            assert!(std::env::var_os(APP_HOME_ENV_VAR).is_none());
            ProjectDirs::from("", "teamdman", APP_HOME_DIR_NAME)
                .ok_or_else(|| eyre::eyre!("platform application home is unavailable"))?
                .config_dir()
                .to_path_buf()
        }
        _ => return Ok(()),
    };
    assert!(expected_home.is_absolute());
    let cli = parse(&[
        "source",
        "release",
        "plan",
        "--projection",
        "arbitrary/release",
        "--expected-mod-version",
        "4.35.0",
    ])?;
    assert!(!cli.defers_file_logging());
    assert_eq!(AppHome::resolve()?.0, expected_home);
    let logging = cli.logging_config()?;
    assert_eq!(logging.json_log_directory, Some(expected_home.join("logs")));
    assert!(logging.json_log_path.is_none());
    println!("{CONFIGURATION_MARKER}");
    if platform_default {
        return Ok(()); // Configuration-only: preserve the user's actual application home.
    }
    init_logging(&logging, &CancellationToken::new())?;
    tracing::info!("{LOG_MARKER}");
    Ok(())
}

#[test]
fn release_plan_default_logs_use_app_home_from_distinct_invocation_directories() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let app_home = fixture.path().join("application-home");
    fs::create_dir(&app_home)?;
    for name in ["checkout", "projection"] {
        let invocation = fixture.path().join(name);
        fs::create_dir(&invocation)?;
        // Environment and CWD are scoped to the child, leaving parallel tests alone.
        let output = bounded_output(
            Command::new(std::env::current_exe()?)
                .args([
                    "--exact",
                    WORKER_TEST,
                    "--ignored",
                    "--nocapture",
                    "--test-threads=1",
                ])
                .current_dir(&invocation)
                .env(APP_HOME_ENV_VAR, &app_home)
                .env(WORKER_HOME, &app_home)
                .env_remove(WORKER_PLATFORM_DEFAULT)
                .env_remove("RUST_LOG"),
        )?;
        assert!(
            output.status.success(),
            "logging worker failed: stdout={} stderr={}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
        assert!(String::from_utf8_lossy(&output.stdout).contains(CONFIGURATION_MARKER));
        assert!(fs::read_dir(&invocation)?.next().is_none());
    }
    let logs = app_home.join("logs");
    let runs = fs::read_dir(&logs)?
        .map(|entry| entry.map(|entry| entry.path()))
        .collect::<std::io::Result<Vec<_>>>()?
        .into_iter()
        .filter(|path| {
            path.extension()
                .is_some_and(|extension| extension == "ndjson")
        })
        .collect::<Vec<_>>();
    assert_eq!(runs.len(), 2);
    for run in runs {
        assert!(fs::read_to_string(run)?.contains(LOG_MARKER));
    }
    assert!(logs.join("latest.json").is_file());
    Ok(())
}

#[test]
fn release_plan_platform_default_logs_ignore_invocation_directory() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    for name in ["checkout", "projection"] {
        let invocation = fixture.path().join(name);
        fs::create_dir(&invocation)?;
        let output = bounded_output(
            Command::new(std::env::current_exe()?)
                .args([
                    "--exact",
                    WORKER_TEST,
                    "--ignored",
                    "--nocapture",
                    "--test-threads=1",
                ])
                .current_dir(&invocation)
                .env_remove(APP_HOME_ENV_VAR)
                .env_remove(WORKER_HOME)
                .env(WORKER_PLATFORM_DEFAULT, "1"),
        )?;
        assert!(
            output.status.success(),
            "platform logging configuration worker failed: stdout={} stderr={}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
        assert!(String::from_utf8_lossy(&output.stdout).contains(CONFIGURATION_MARKER));
        assert!(fs::read_dir(&invocation)?.next().is_none());
    }
    Ok(())
}

#[test]
fn release_plan_keeps_explicit_file_logging() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let destination = fixture.path().join("requested.ndjson");
    let destination_argument = destination.to_str().unwrap();
    let cli = parse(&[
        "--log-file",
        destination_argument,
        "source",
        "release",
        "plan",
        "--projection",
        "arbitrary/release",
        "--expected-mod-version",
        "4.35.0",
    ])?;
    assert!(!cli.defers_file_logging());
    let logging = cli.logging_config()?;
    assert_eq!(logging.json_log_path, Some(destination.clone()));
    assert!(logging.json_log_directory.is_none());
    assert!(!destination.exists());
    Ok(())
}
