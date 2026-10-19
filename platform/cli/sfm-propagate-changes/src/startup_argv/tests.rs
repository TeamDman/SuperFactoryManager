use super::StartupJsonFormatter;
use super::log;
use super::startup_arguments::StartupArguments;
use super::startup_fixture::StartupFixture;
use super::startup_log_record::StartupLogRecord;
use super::unicode_arguments;
use tracing_subscriber::fmt::writer::BoxMakeWriter;

fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

#[test]
fn reviewed_tokens_preserve_spaces_quotes_empty_and_unicode() {
    let raw = strings(&[
        "C:\\my tools\\cli.exe",
        "--request",
        "a b\\\"c日本語",
        "--request=",
        "--debug",
    ]);
    let record = StartupArguments::redact::<StartupFixture>(&raw).unwrap();
    assert_eq!(record.argv, raw);
    assert!(record.redacted_indices.is_empty());
    let json = facet_json::to_string(&record).unwrap();
    let decoded: StartupArguments = facet_json::from_str(&json).unwrap();
    assert_eq!(decoded, record);
}

#[test]
fn secrets_and_free_payloads_are_masked_before_serialization() {
    let raw = strings(&[
        "cli",
        "--token=SECRET_INLINE",
        "--password",
        "SECRET_SEPARATE",
        "--message",
        "SECRET_BODY",
        "--unknown=SECRET_UNKNOWN",
        "--",
        "SECRET_POSITIONAL",
    ]);
    let record = StartupArguments::redact::<StartupFixture>(&raw).unwrap();
    assert_eq!(
        record.argv,
        strings(&[
            "cli",
            "--token=[redacted]",
            "--password",
            "[redacted]",
            "--message",
            "[redacted]",
            "[redacted]",
            "--",
            "[redacted]"
        ])
    );
    assert_eq!(record.redacted_indices, vec![1, 3, 5, 6, 8]);
    assert!(!facet_json::to_string(&record).unwrap().contains("SECRET"));
    assert!(!format!("{record:?}").contains("SECRET"));
}

#[test]
fn url_credentials_and_credential_headers_in_safe_options_are_masked() {
    for value in [
        "https://user:SECRET@host.invalid/path",
        "https://host.invalid/?access_token=SECRET",
        "https://host.invalid/#token=SECRET",
        "https://host.invalid/?api%5fkey=SECRET",
        "Bearer SECRET",
        "bearer SECRET",
        "Authorization: Bearer SECRET",
        "Authorization:Bearer SECRET",
        "Cookie:session=SECRET",
        "https://host.invalid/#/login?token=SECRET",
        "https://host.invalid/#SECRET",
        "https://host.invalid/?x=1;token=SECRET",
        "Basic SECRET",
    ] {
        for raw in [
            strings(&["cli", "--request", value]),
            strings(&["cli", &format!("--request={value}")]),
        ] {
            let record = StartupArguments::redact::<StartupFixture>(&raw).unwrap();
            assert!(!facet_json::to_string(&record).unwrap().contains("SECRET"));
            assert_eq!(record.redacted_indices.len(), 1);
        }
    }
    let raw = strings(&["cli", "--request=https://host.invalid/path"]);
    assert_eq!(
        StartupArguments::redact::<StartupFixture>(&raw)
            .unwrap()
            .argv,
        raw
    );
}

#[test]
fn schema_commands_are_retained_and_unreviewed_positionals_are_opaque() {
    let raw = strings(&["cli", "--log-filter", "debug", "home", "path"]);
    let record = StartupArguments::redact::<crate::cli::Cli>(&raw).unwrap();
    assert_eq!(record.argv, raw);
    assert!(!record.policy.is_empty());
}

#[test]
fn json_sink_has_an_array_and_honors_debug_filter() {
    static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
    for level in [tracing::Level::DEBUG, tracing::Level::INFO] {
        let path = std::env::temp_dir().join(format!(
            "startup-array-{}-{}-{}.ndjson",
            std::process::id(),
            chrono::Utc::now().timestamp_nanos_opt().unwrap(),
            NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
        ));
        let file = std::fs::File::create(&path).unwrap();
        let subscriber = tracing_subscriber::fmt()
            .json()
            .event_format(StartupJsonFormatter::new(true))
            .with_writer(BoxMakeWriter::new(std::sync::Mutex::new(file)))
            .with_max_level(level)
            .finish();
        tracing::subscriber::with_default(subscriber, || {
            log::<StartupFixture>(&strings(&[
                "cli",
                "--request",
                "a b\"日本語",
                "--token",
                "SECRET",
            ]))
            .unwrap();
            tracing::info!("ordinary event");
        });
        let text = std::fs::read_to_string(&path).unwrap();
        assert!(!text.contains("SECRET"));
        assert!(text.contains("ordinary event"));
        let startup = text
            .lines()
            .find(|line| line.contains("\"event_kind\":\"startup\""));
        if level == tracing::Level::DEBUG {
            let decoded: StartupLogRecord = facet_json::from_str(startup.unwrap()).unwrap();
            assert_eq!(decoded.level, "DEBUG");
            assert_eq!(decoded.fields.argv[2], "a b\"日本語");
            assert_eq!(decoded.fields.argv[4], "[redacted]");
            assert_eq!(decoded.fields.redacted_indices, vec![4]);
        } else {
            assert!(startup.is_none());
        }
        std::fs::remove_file(path).unwrap();
    }
}

#[cfg(windows)]
#[test]
fn windows_non_unicode_arguments_fail_without_exposing_code_units() {
    use std::os::windows::ffi::OsStringExt;
    let invalid = std::ffi::OsString::from_wide(&[0x0053, 0x0045, 0xd800, 0x0043]);
    let error = unicode_arguments([invalid]).unwrap_err();
    assert_eq!(error.to_string(), "command arguments must be valid Unicode");
}
