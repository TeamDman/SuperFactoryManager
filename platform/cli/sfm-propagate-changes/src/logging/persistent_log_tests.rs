use super::super::logging_init::configured_subscriber;
use super::*;
use std::collections::BTreeMap;
use std::process::{Child, Command, Stdio};
use tracing::level_filters::LevelFilter;

fn config(directory: &Path) -> LoggingConfig {
    let mut config = LoggingConfig::new(LevelFilter::ERROR, None::<PathBuf>);
    config.read_env_filter = false;
    config.json_read_env_filter = false;
    config.json_default_directive = LevelFilter::DEBUG.into();
    config.json_log_directory = Some(directory.to_path_buf());
    config.private_log_directory = true;
    config
}
fn capture(
    config: &LoggingConfig,
    token: &CancellationToken,
    action: impl FnOnce(),
) -> Result<PathBuf> {
    let (subscriber, path, _) = configured_subscriber(config, token)?;
    tracing::subscriber::with_default(subscriber, action);
    Ok(path.unwrap())
}
fn events(path: &Path) -> Result<Vec<BTreeMap<String, facet_value::Value>>> {
    fs::read_to_string(path)?
        .lines()
        .map(|line| {
            let mut row: BTreeMap<String, facet_value::Value> = facet_json::from_str(line)?;
            let fields = row
                .remove("fields")
                .ok_or_else(|| eyre::eyre!("missing fields"))?;
            Ok(facet_json::from_str(&facet_json::to_string(&fields)?)?)
        })
        .collect()
}
fn message(row: &BTreeMap<String, facet_value::Value>) -> Option<&str> {
    row.get("message")?.as_string().map(|s| s.as_str())
}
fn latest(directory: &Path) -> Result<LatestLog> {
    Ok(facet_json::from_slice(&fs::read(
        directory.join("latest.json"),
    )?)?)
}

#[test]
fn default_debug_file_preserves_terminal_and_explicit_level_configuration() -> Result<()> {
    let parse = |args: &[&str]| -> Result<crate::cli::Cli> {
        Ok(figue::from_slice::<crate::cli::Cli>(args)
            .into_result()
            .map_err(|_| eyre::eyre!("fixture parse failed"))?
            .get_silent())
    };
    let default = parse(&["jdk", "list"])?.logging_config()?;
    assert_eq!(default.default_directive, LevelFilter::INFO.into());
    assert_eq!(default.json_default_directive, LevelFilter::DEBUG.into());
    assert!(default.json_log_directory.is_some() && default.json_log_path.is_none());
    assert!(default.read_env_filter && default.json_read_env_filter);
    let explicit = parse(&["--log-file", "fixture.ndjson", "jdk", "list"])?.logging_config()?;
    assert_eq!(
        explicit.json_log_path,
        Some(PathBuf::from("fixture.ndjson"))
    );
    assert!(explicit.json_log_directory.is_none());
    assert_eq!(explicit.json_default_directive, LevelFilter::INFO.into());
    let warn = parse(&["--log-filter", "warn", "jdk", "list"])?.logging_config()?;
    assert_eq!(warn.default_directive, LevelFilter::WARN.into());
    assert_eq!(warn.json_default_directive, LevelFilter::WARN.into());
    assert!(!warn.read_env_filter && !warn.json_read_env_filter);
    let debug = parse(&["--debug", "--log-filter", "error", "jdk", "list"])?.logging_config()?;
    assert_eq!(debug.default_directive, LevelFilter::DEBUG.into());
    assert_eq!(debug.json_default_directive, LevelFilter::DEBUG.into());
    assert!(!debug.read_env_filter && !debug.json_read_env_filter);
    assert!(
        LoggingConfig::new(LevelFilter::INFO, None::<PathBuf>)
            .json_log_directory
            .is_none()
    );
    Ok(())
}

#[test]
fn repeated_runs_keep_debug_ndjson_history_and_complete_latest_pointer() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let token = CancellationToken::new();
    let first = capture(&config(dir.path()), &token, || {
        tracing::debug!("unicode snowman ☃ and escaped\nnewline");
    })?;
    let before = fs::read(&first)?;
    assert_eq!(events(&first)?.len(), 1);
    let second = capture(&config(dir.path()), &token, || {
        tracing::debug!("second run");
    })?;
    assert_ne!(first, second);
    assert_eq!(fs::read(&first)?, before);
    assert_eq!(dir.path().join(latest(dir.path())?.file), second);
    assert_eq!(events(&second)?.len(), 1);
    Ok(())
}

#[test]
fn explicit_file_appends_and_never_truncates_existing_records() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let path = dir.path().join("selected.ndjson");
    let old = b"{\"level\":\"INFO\",\"fields\":{\"message\":\"retained\"}}\n";
    fs::write(&path, old)?;
    let mut cfg = config(dir.path());
    cfg.json_log_directory = None;
    cfg.json_log_path = Some(path.clone());
    capture(&cfg, &CancellationToken::new(), || {
        tracing::debug!("appended")
    })?;
    assert!(fs::read(&path)?.starts_with(old));
    assert_eq!(events(&path)?.len(), 2);
    assert!(!dir.path().join("latest.json").exists());
    Ok(())
}

#[test]
fn timestamp_collision_retries_without_replacing_existing_bytes() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let occupied = dir.path().join("run-collision.ndjson");
    fs::write(&occupied, b"retained")?;
    let mut count = 0;
    let log = create_unique(dir.path(), &CancellationToken::new(), || {
        count += 1;
        if count == 1 {
            "run-collision.ndjson"
        } else {
            "run-next.ndjson"
        }
        .to_owned()
    })?;
    assert_eq!(fs::read(&occupied)?, b"retained");
    assert_eq!(log.path.file_name().unwrap(), "run-next.ndjson");
    assert_eq!(latest(dir.path())?.file, "run-next.ndjson");
    Ok(())
}

#[test]
fn creation_pointer_and_cancel_failures_preserve_existing_data() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let blocked = dir.path().join("not-a-directory");
    fs::write(&blocked, b"retained")?;
    assert!(prepare(&config(&blocked), &CancellationToken::new()).is_err());
    assert_eq!(fs::read(&blocked)?, b"retained");
    let pointer = dir.path().join("latest.json");
    fs::write(&pointer, b"private unrecognized data")?;
    let error = prepare(&config(dir.path()), &CancellationToken::new())
        .err()
        .unwrap();
    assert!(!format!("{error:?}").contains("private unrecognized data"));
    assert_eq!(fs::read(&pointer)?, b"private unrecognized data");
    let cancelled = CancellationToken::new();
    cancelled.request_cancel("cancel before logging");
    let target = dir.path().join("cancelled");
    assert!(prepare(&config(&target), &cancelled).is_err());
    assert!(!target.exists());
    Ok(())
}

#[test]
fn cancellation_and_error_events_remain_readable_after_subscriber_shutdown() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let token = CancellationToken::new();
    let child = token.clone();
    let path = capture(&config(dir.path()), &token, || {
        tracing::debug!("before cancellation");
        child.request_cancel("fixture cancellation");
        tracing::warn!("cancelled");
        tracing::error!("fixture failure marker");
    })?;
    let rows = events(&path)?;
    assert_eq!(rows.len(), 3);
    assert!(rows.iter().any(|r| message(r) == Some("cancelled")));
    assert!(
        rows.iter()
            .any(|r| message(r) == Some("fixture failure marker"))
    );
    Ok(())
}

#[test]
fn recognized_latest_pointer_cannot_become_an_explicit_log() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let original = prepare(&config(dir.path()), &CancellationToken::new())?.unwrap();
    let pointer = dir.path().join("latest.json");
    let before = fs::read(&pointer)?;
    let mut cfg = config(dir.path());
    cfg.json_log_directory = None;
    cfg.json_log_path = Some(pointer.clone());
    assert!(prepare(&cfg, &CancellationToken::new()).is_err());
    assert_eq!(fs::read(&pointer)?, before);
    assert!(original.path.exists());
    Ok(())
}

#[cfg(unix)]
#[test]
fn linked_lock_or_pointer_refuses_without_touching_target() -> Result<()> {
    use std::os::unix::fs::symlink;
    for name in [".latest.lock", "latest.json"] {
        let dir = tempfile::tempdir()?;
        let target = dir.path().join("retained");
        fs::write(&target, b"unchanged")?;
        symlink(&target, dir.path().join(name))?;
        assert!(prepare(&config(dir.path()), &CancellationToken::new()).is_err());
        assert_eq!(fs::read(target)?, b"unchanged");
    }
    Ok(())
}

#[test]
fn persistent_log_process_worker() -> Result<()> {
    let Some(dir) = std::env::var_os("SFM_LOG_TEST_DIRECTORY") else {
        return Ok(());
    };
    let dir = PathBuf::from(dir);
    let mut cfg = config(&dir);
    if std::env::var_os("SFM_LOG_TEST_SHARED").is_some() {
        cfg.json_log_directory = None;
        cfg.json_log_path = Some(dir.join("shared.ndjson"));
    }
    if std::env::var_os("SFM_LOG_TEST_CANCEL").is_some() {
        let token = crate::cancellation::install_ctrlc_handler()?;
        crate::logging::init_logging(&cfg, &token)?;
        tracing::debug!("before cancellation signal");
        fs::write(dir.join("signal-ready"), b"ready")?;
        let started = Instant::now();
        while !token.is_cancelled() {
            ensure!(
                started.elapsed() < Duration::from_secs(10),
                "cancellation signal absent"
            );
            std::thread::sleep(Duration::from_millis(10));
        }
        tracing::warn!("cancelled_shutdown");
        return Ok(());
    }
    let token = CancellationToken::new();
    let (subscriber, path, _) = configured_subscriber(&cfg, &token)?;
    tracing::subscriber::with_default(subscriber, || {
        for sequence in 0..100 {
            tracing::debug!(sequence, "worker record");
        }
    });
    println!("LOG_TEST_PATH:{}", path.unwrap().display());
    Ok(())
}

struct TestWorker(Option<Child>);
impl Drop for TestWorker {
    fn drop(&mut self) {
        if let Some(child) = self.0.as_mut() {
            if child.try_wait().ok().flatten().is_none() {
                let _ = child.kill();
            }
            let _ = child.wait();
        }
    }
}

#[test]
fn independent_processes_keep_unique_runs_and_shared_file_records() -> Result<()> {
    for shared in [false, true] {
        let dir = tempfile::tempdir()?;
        let mut children = Vec::new();
        for _ in 0..4 {
            let mut command = Command::new(std::env::current_exe()?);
            command
                .args([
                    "--exact",
                    "logging::persistent_log::tests::persistent_log_process_worker",
                    "--nocapture",
                    "--test-threads=1",
                ])
                .env("SFM_LOG_TEST_DIRECTORY", dir.path())
                .env_remove("SFM_LOG_TEST_CANCEL")
                .stdout(Stdio::piped())
                .stderr(Stdio::piped());
            if shared {
                command.env("SFM_LOG_TEST_SHARED", "1");
            } else {
                command.env_remove("SFM_LOG_TEST_SHARED");
            }
            children.push(TestWorker(Some(command.spawn()?)));
        }
        let started = Instant::now();
        loop {
            let mut complete = true;
            for child in &mut children {
                if child.0.as_mut().unwrap().try_wait()?.is_none() {
                    complete = false;
                }
            }
            if complete {
                break;
            }
            ensure!(
                started.elapsed() < Duration::from_secs(15),
                "logging worker exceeded bounded wait"
            );
            std::thread::sleep(Duration::from_millis(10));
        }
        let mut paths = Vec::new();
        for mut worker in children {
            let output = worker.0.take().unwrap().wait_with_output()?;
            ensure!(
                output.status.success(),
                "worker failed: {}",
                String::from_utf8_lossy(&output.stderr)
            );
            let stdout = String::from_utf8(output.stdout)?;
            let path = stdout
                .lines()
                .find_map(|l| l.split_once("LOG_TEST_PATH:").map(|(_, p)| p))
                .ok_or_else(|| eyre::eyre!("missing worker result"))?;
            paths.push(PathBuf::from(path));
        }
        paths.sort();
        paths.dedup();
        assert_eq!(paths.len(), if shared { 1 } else { 4 });
        let mut records = 0;
        for path in &paths {
            records += events(path)?
                .iter()
                .filter(|r| message(r) == Some("worker record"))
                .count();
        }
        assert_eq!(records, 400);
        if !shared {
            assert!(paths.contains(&dir.path().join(latest(dir.path())?.file)));
        }
    }
    Ok(())
}

#[test]
fn explicit_append_separates_incomplete_ascii_and_utf8_tails_without_rewriting_them() -> Result<()>
{
    for (index, old) in [b"{\"fields\":".as_slice(), b"partial\xe2".as_slice()]
        .into_iter()
        .enumerate()
    {
        let directory = tempfile::tempdir()?;
        let path = directory.path().join(format!("partial-{index}.ndjson"));
        fs::write(&path, old)?;
        let mut cfg = config(directory.path());
        cfg.json_log_directory = None;
        cfg.json_log_path = Some(path.clone());
        capture(&cfg, &CancellationToken::new(), || {
            tracing::debug!("after incomplete tail")
        })?;
        let bytes = fs::read(&path)?;
        assert!(bytes.starts_with(old));
        assert_eq!(bytes[old.len()], b'\n');
        let lines = bytes
            .split(|b| *b == b'\n')
            .filter(|line| !line.is_empty())
            .collect::<Vec<_>>();
        assert_eq!(lines.len(), 2);
        let _: BTreeMap<String, facet_value::Value> = facet_json::from_slice(lines[1])?;
    }
    Ok(())
}

#[cfg(unix)]
#[test]
fn automatic_log_directory_is_created_private_and_unsafe_existing_modes_are_unchanged() -> Result<()>
{
    use std::os::unix::fs::PermissionsExt as _;
    let root = tempfile::tempdir()?;
    let directory = root.path().join("logs");
    prepare(&config(&directory), &CancellationToken::new())?;
    assert_eq!(
        fs::metadata(&directory)?.permissions().mode() & 0o777,
        0o700
    );
    fs::set_permissions(&directory, fs::Permissions::from_mode(0o777))?;
    assert!(prepare(&config(&directory), &CancellationToken::new()).is_err());
    assert_eq!(
        fs::metadata(&directory)?.permissions().mode() & 0o777,
        0o777
    );
    Ok(())
}

#[cfg(unix)]
#[test]
fn cancellation_signal_preserves_synchronous_records_and_shutdown_marker() -> Result<()> {
    let directory = tempfile::tempdir()?;
    let child = Command::new(std::env::current_exe()?)
        .args([
            "--exact",
            "logging::persistent_log::tests::persistent_log_process_worker",
            "--nocapture",
            "--test-threads=1",
        ])
        .env("SFM_LOG_TEST_DIRECTORY", directory.path())
        .env("SFM_LOG_TEST_CANCEL", "1")
        .env_remove("SFM_LOG_TEST_SHARED")
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()?;
    let mut worker = TestWorker(Some(child));
    let started = Instant::now();
    while !directory.path().join("signal-ready").exists() {
        ensure!(
            worker.0.as_mut().unwrap().try_wait()?.is_none(),
            "worker exited before ready"
        );
        ensure!(
            started.elapsed() < Duration::from_secs(5),
            "worker readiness timeout"
        );
        std::thread::sleep(Duration::from_millis(10));
    }
    let pid = worker.0.as_ref().unwrap().id().to_string();
    ensure!(
        Command::new("kill")
            .args(["-INT", &pid])
            .status()?
            .success(),
        "could not signal owned worker"
    );
    while worker.0.as_mut().unwrap().try_wait()?.is_none() {
        ensure!(
            started.elapsed() < Duration::from_secs(10),
            "worker shutdown timeout"
        );
        std::thread::sleep(Duration::from_millis(10));
    }
    let result = worker.0.take().unwrap().wait_with_output()?;
    ensure!(
        result.status.success(),
        "cancel worker failed: {}",
        String::from_utf8_lossy(&result.stderr)
    );
    let file = directory.path().join(latest(directory.path())?.file);
    let records = events(&file)?;
    assert!(
        records
            .iter()
            .any(|r| message(r) == Some("before cancellation signal"))
    );
    assert!(
        records
            .iter()
            .any(|r| message(r) == Some("cancelled_shutdown"))
    );
    Ok(())
}
