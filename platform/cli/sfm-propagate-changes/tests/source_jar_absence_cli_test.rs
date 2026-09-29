use std::fs::File;
use std::io::Write;
use std::path::Path;
use std::process::Command;
use zip::ZipWriter;
use zip::write::SimpleFileOptions;

fn fixture_jar(path: &Path, entry: &str) {
    let mut writer = ZipWriter::new(File::create(path).unwrap());
    writer
        .start_file(entry, SimpleFileOptions::default())
        .unwrap();
    writer.write_all(b"fixture").unwrap();
    writer.finish().unwrap();
}

fn run_check(jar: &Path) -> std::process::Output {
    run_check_target(jar, "1.19.2")
}

fn run_check_target(jar: &Path, target: &str) -> std::process::Output {
    let manifest =
        Path::new(env!("CARGO_MANIFEST_DIR")).join("../../minecraft/source-projection.json");
    Command::new(env!("CARGO_BIN_EXE_source-jar-absence"))
        .args(["--manifest", manifest.to_str().unwrap()])
        .args(["--target", target, "--preset", "released-4.34.0"])
        .args(["--jar", jar.to_str().unwrap()])
        .output()
        .unwrap()
}

#[test]
fn cli_accepts_absent_feature_jar_and_rejects_echo_class() {
    let temporary = tempfile::tempdir().unwrap();
    let jar = temporary.path().join("candidate.jar");
    fixture_jar(&jar, "example/Unrelated.class");
    let accepted = run_check(&jar);
    assert!(
        accepted.status.success(),
        "{}",
        String::from_utf8_lossy(&accepted.stderr)
    );
    assert!(
        String::from_utf8_lossy(&accepted.stdout).contains("forbidden_rules=14"),
        "{}",
        String::from_utf8_lossy(&accepted.stdout)
    );

    fixture_jar(&jar, "ca/teamdman/sfm/client/action/EchoAction.class");
    let rejected = run_check(&jar);
    assert!(!rejected.status.success());
    assert!(
        String::from_utf8_lossy(&rejected.stderr).contains("EchoAction.class"),
        "{}",
        String::from_utf8_lossy(&rejected.stderr)
    );
}

#[test]
fn cli_labels_zero_applicable_features_without_claiming_absence() {
    let temporary = tempfile::tempdir().unwrap();
    let jar = temporary.path().join("candidate.jar");
    fixture_jar(&jar, "example/Unrelated.class");
    let output = run_check_target(&jar, "26.1.2");
    assert!(output.status.success());
    assert!(
        String::from_utf8_lossy(&output.stdout).starts_with("NO_APPLICABLE_FEATURES:"),
        "{}",
        String::from_utf8_lossy(&output.stdout)
    );
}

#[test]
fn package_retains_primary_cli_as_default_run() {
    assert!(
        include_str!("../Cargo.toml").contains("default-run = \"sfm-propagate-changes\""),
        "adding the inspection binary must not change plain cargo run"
    );
}
