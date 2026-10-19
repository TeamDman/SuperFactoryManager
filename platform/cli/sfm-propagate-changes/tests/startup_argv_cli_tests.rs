//! Real process checks for the startup event and builtin/filter boundaries.
use std::process::Command;

const BINARY: &str = env!("CARGO_BIN_EXE_sfm-propagate-changes");
const FILE_INDEPENDENT: bool = false;

fn scratch() -> std::path::PathBuf {
    static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
    let root = std::env::temp_dir().join(format!(
        "startup-cli-{}-{}-{}",
        std::process::id(),
        chrono::Utc::now().timestamp_nanos_opt().unwrap(),
        NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
    ));
    std::fs::create_dir_all(&root).unwrap();
    root
}

#[test]
fn debug_startup_is_a_json_array_and_explicit_filter_overrides_environment() {
    let root = scratch();
    let file = root.join("log with spaces 日本語.ndjson");
    let output = Command::new(BINARY)
        .args(["--log-filter", "debug", "--log-file"])
        .arg(&file)
        .args(["home", "path"])
        .env("RUST_LOG", "off")
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    let text = std::fs::read_to_string(&file).unwrap();
    let startup = text
        .lines()
        .find(|line| line.contains("\"event_kind\":\"startup\""))
        .unwrap();
    assert!(startup.contains("\"level\":\"DEBUG\""));
    assert!(startup.contains("\"argv\":["));
    assert!(startup.contains(&facet_json::to_string(&file.to_str().unwrap()).unwrap()));
    assert!(startup.contains("\"home\",\"path\""));
    assert!(startup.contains("\"redacted_indices\":[]"));
    assert!(
        !String::from_utf8(output.stdout)
            .unwrap()
            .contains("Process started")
    );
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn info_filters_preserve_each_cli_existing_sink_policy() {
    let root = scratch();
    let file = root.join("info.ndjson");
    let output = Command::new(BINARY)
        .args(["--log-filter", "info", "--log-file"])
        .arg(&file)
        .args(["home", "path"])
        .env_remove("RUST_LOG")
        .output()
        .unwrap();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    let text = std::fs::read_to_string(&file).unwrap();
    assert_eq!(
        text.contains("\"event_kind\":\"startup\""),
        FILE_INDEPENDENT
    );
    assert!(
        !String::from_utf8(output.stderr)
            .unwrap()
            .contains("Process started")
    );
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn help_and_version_exit_before_startup_logging_or_file_creation() {
    for builtin in ["--help", "--version"] {
        let root = scratch();
        let file = root.join("must-not-be-created.ndjson");
        let output = Command::new(BINARY)
            .args(["--debug", "--log-file"])
            .arg(&file)
            .arg(builtin)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        assert!(!file.exists());
        assert!(
            !String::from_utf8(output.stderr)
                .unwrap()
                .contains("Process started")
        );
        assert!(
            !String::from_utf8(output.stdout)
                .unwrap()
                .contains("Process started")
        );
        std::fs::remove_dir_all(root).unwrap();
    }
}

#[cfg(windows)]
#[test]
fn non_unicode_input_exits_safely_before_logging() {
    use std::os::windows::ffi::OsStringExt;
    let root = scratch();
    let file = root.join("must-not-be-created.ndjson");
    let argument = std::ffi::OsString::from_wide(&[0x0053, 0x0045, 0xd800, 0x0043]);
    let output = Command::new(BINARY)
        .args(["--debug", "--log-file"])
        .arg(&file)
        .args(["home", "path"])
        .arg(argument)
        .output()
        .unwrap();
    assert!(!output.status.success());
    assert!(!file.exists());
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(!stderr.contains("panicked"));
    assert!(!stderr.contains('\u{fffd}'));
    assert!(!stderr.contains("Process started"));
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn malformed_cli_cannot_echo_credential_payloads_or_open_logs() {
    let root = scratch();
    let file = root.join("must-not-be-created.ndjson");
    let output = Command::new(BINARY)
        .args(["--debug", "--log-file"])
        .arg(&file)
        .args(["--unknown-SECRET_ARGUMENT=value", "home", "path"])
        .output()
        .unwrap();
    assert!(!output.status.success());
    assert!(!file.exists());
    assert!(!String::from_utf8_lossy(&output.stderr).contains("SECRET_ARGUMENT"));
    assert!(!String::from_utf8_lossy(&output.stdout).contains("SECRET_ARGUMENT"));
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn incomplete_command_help_cannot_echo_argv_suggestions() {
    let root = scratch();
    let file = root.join("must-not-be-created.ndjson");
    let output = Command::new(BINARY)
        .args(["--debug", "--log-file"])
        .arg(&file)
        .args(["--log-filter", "SECRET_HELP_SUGGESTION"])
        .output()
        .unwrap();
    assert!(!file.exists());
    assert!(!String::from_utf8_lossy(&output.stderr).contains("SECRET_HELP_SUGGESTION"));
    assert!(!String::from_utf8_lossy(&output.stdout).contains("SECRET_HELP_SUGGESTION"));
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn missing_required_option_help_cannot_echo_request_value() {
    let root = scratch();
    let file = root.join("must-not-be-created.ndjson");
    let output = Command::new(BINARY)
        .args(["--debug", "--log-file"])
        .arg(&file)
        .args(["source", "compare-prepare", "--request", "SECRET_REQUEST"])
        .output()
        .unwrap();
    assert!(!file.exists());
    assert!(!String::from_utf8_lossy(&output.stderr).contains("SECRET_REQUEST"));
    assert!(!String::from_utf8_lossy(&output.stdout).contains("SECRET_REQUEST"));
    std::fs::remove_dir_all(root).unwrap();
}
