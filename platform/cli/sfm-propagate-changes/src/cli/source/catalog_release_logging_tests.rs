use crate::cancellation::CancellationToken;
use crate::cli::Cli;
use crate::cli::InvocationHomes;
use crate::logging::init_logging;
use crate::paths::APP_CACHE_DIR_NAME;
use crate::paths::APP_CACHE_ENV_VAR;
use crate::paths::APP_HOME_ENV_VAR;
use crate::paths::AppHome;
use crate::paths::CacheHome;
use crate::paths::InvocationDirectory;
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

const WORKER_CACHE: &str = "SFM_CATALOG_RELEASE_LOGGING_TEST_CACHE";
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
#[ignore = "Runs only in an isolated child with a test log location"]
fn release_plan_logging_worker() -> Result<()> {
    let platform_default = std::env::var_os(WORKER_PLATFORM_DEFAULT).is_some();
    let expected_cache = match std::env::var_os(WORKER_CACHE) {
        Some(path) if !platform_default => PathBuf::from(path),
        _ if platform_default => {
            assert!(std::env::var_os(APP_HOME_ENV_VAR).is_none());
            assert!(std::env::var_os(APP_CACHE_ENV_VAR).is_none());
            ProjectDirs::from("", "teamdman", APP_CACHE_DIR_NAME)
                .ok_or_else(|| eyre::eyre!("platform cache home is unavailable"))?
                .cache_dir()
                .to_path_buf()
        }
        _ => return Ok(()),
    };
    assert!(expected_cache.is_absolute());
    let cwd = std::env::current_dir()?;
    let invocation_dir = InvocationDirectory::new(&cwd)?;
    let app_home = AppHome::resolve(&invocation_dir)?;
    let homes = if platform_default {
        // Supply the platform default without reading a user's persisted override.
        InvocationHomes::new(app_home, Ok(CacheHome(expected_cache.clone())))
    } else {
        assert_eq!(app_home.0, cwd.join("application-home"));
        let cache_home = CacheHome::resolve(&app_home);
        InvocationHomes::new_selected(app_home, cache_home)
    };
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
    let logging = cli.logging_config(&invocation_dir, &homes)?;
    assert_eq!(
        logging.json_log_directory,
        Some(expected_cache.join("logs"))
    );
    assert!(logging.json_log_path.is_none());
    println!("{CONFIGURATION_MARKER}");
    if platform_default {
        return Ok(()); // Configuration-only: preserve the user's actual cache home.
    }
    init_logging(&logging, &CancellationToken::new())?;
    tracing::info!("{LOG_MARKER}");
    Ok(())
}

#[test]
fn release_plan_default_logs_use_cache_home_from_distinct_invocation_directories() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let cache_home = fixture.path().join("cache-home");
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
                .env(APP_HOME_ENV_VAR, "application-home")
                .env(APP_CACHE_ENV_VAR, &cache_home)
                .env(WORKER_CACHE, &cache_home)
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
    let logs = cache_home.join("logs");
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
                .env_remove(APP_CACHE_ENV_VAR)
                .env_remove(WORKER_CACHE)
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
    let invocation_dir = InvocationDirectory::new(fixture.path())?;
    let homes = InvocationHomes::new(
        AppHome(fixture.path().join("application-home")),
        Ok(CacheHome(fixture.path().join("cache-home"))),
    );
    let cli = parse(&[
        "--log-file",
        "requested.ndjson",
        "source",
        "release",
        "plan",
        "--projection",
        "arbitrary/release",
        "--expected-mod-version",
        "4.35.0",
    ])?;
    assert!(!cli.defers_file_logging());
    let logging = cli.logging_config(&invocation_dir, &homes)?;
    assert_eq!(logging.json_log_path.as_ref(), Some(&destination));
    assert!(logging.json_log_directory.is_none());
    assert!(!destination.exists());
    Ok(())
}
