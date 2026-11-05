use super::*;
use crate::jar_build::engine::junit_logging::JunitLogging;

fn locked_log4j(name: &str, hash: &str) -> Result<PathBuf> {
    let path = PathBuf::from(std::env::var_os(name).ok_or_else(|| eyre::eyre!("Set {name}"))?);
    let expected = crate::jar_build::hash::ContentHash::parse(hash).map_err(eyre::Report::msg)?;
    ensure!(
        crate::jar_build::hash::ContentHash::from_path(&path, expected.algorithm)? == expected,
        "Diagnostic Log4j differs from its unchanged 26.1.2 lock pin"
    );
    Ok(path)
}

fn run_java(java: &std::ffi::OsStr, argfile: &Path, cwd: &Path) -> Result<()> {
    let mut command = std::process::Command::new(java);
    let trust = crate::jar_build::java_trust::JavaTrust::configure_java(&mut command, false, 25)?;
    let output = command
        .arg(format!("@{}", argfile.display()))
        .current_dir(cwd)
        .output()?;
    crate::jar_build::java_trust::JavaTrust::recheck_optional(trust.as_ref())?;
    ensure!(
        output.status.success(),
        "Log4j fixture probe failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    Ok(())
}

#[test]
#[ignore = "Requires explicit diagnostic Java and cached locked Log4j JARs; logging isolation only"]
fn historical_junit_logging_actual_log4j_preserves_immutable_fixture_and_failed_evidence()
-> Result<()> {
    let java = std::env::var_os("SFM_TEST_FIXTURE_JAVA")
        .ok_or_else(|| eyre::eyre!("Set SFM_TEST_FIXTURE_JAVA"))?;
    let api = locked_log4j(
        "SFM_TEST_LOG4J_API",
        "blake3:f895dc9ce77556cbf5498ef80398f730392db8ee",
    )?;
    let core = locked_log4j(
        "SFM_TEST_LOG4J_CORE",
        "blake3:e5141c0d66ac8d14de94449d307963ffda806c3f",
    )?;
    let cache = tempfile::Builder::new()
        .prefix("sfm-junit-log & data-")
        .tempdir()?;
    let run = cache.path().join("run/test");
    fs::create_dir_all(&run)?;
    let resources = cache.path().join("classpath-resources");
    fs::create_dir(&resources)?;
    // Reproduce the classpath resource's relative output independently of the
    // explicit logging launch contract. No cached resource JAR is modified.
    fs::write(resources.join("log4j2.xml"), br#"<Configuration status="WARN"><Appenders><File name="Relative" fileName="logs/latest.log"><PatternLayout pattern="%msg%n"/></File></Appenders><Loggers><Root level="info"><AppenderRef ref="Relative"/></Root></Loggers></Configuration>"#)?;
    let probe = cache.path().join("OwnedLoggingProbe.java");
    fs::write(&probe, b"import org.apache.logging.log4j.LogManager; public class OwnedLoggingProbe { public static void main(String[] args) { LogManager.getLogger(\"SfmOwnedProbe\").info(\"sfm-owned-log-marker\"); LogManager.shutdown(); } }")?;
    let classpath = crate::jar_build::engine::join_classpath(&[resources, api, core]);
    let arguments = [
        "-cp".to_owned(),
        classpath.clone(),
        probe.to_str().unwrap().to_owned(),
    ]
    .into_iter()
    .map(crate::jar_build::engine::escape_argfile_arg)
    .collect::<Vec<_>>()
    .join("\n");
    let argfile = run.join("junit.java.args");
    fs::write(&argfile, arguments)?;
    let cancel = CancellationToken::new();
    let mut legacy = sample()?;
    legacy.manifest.schema = "sfm:junit_fixture_snapshot@1".to_owned();
    let failed = publish(cache.path(), &legacy, &cancel)?;
    run_java(&java, &argfile, failed.root())?;
    let failed_log = failed.root().join("logs/latest.log");
    let retained = fs::read(&failed_log)?;
    assert!(String::from_utf8_lossy(&retained).contains("sfm-owned-log-marker"));
    assert!(verify(failed.root(), &legacy.manifest, &failed.manifest, &cancel).is_err());

    let current = sample()?;
    let fixture = publish(cache.path(), &current, &cancel)?;
    let logging = JunitLogging::prepare(cache.path(), &run)?;
    fs::write(run.join("testRuntimeClasspath.txt"), classpath)?;
    let record = run.join("junit-working-directory.json");
    let identity = write_working_directory_record(
        cache.path(),
        &record,
        fixture.root(),
        Some(&fixture),
        Some(&logging),
    )?;
    let launch = crate::jar_build::engine::junit_inputs::LaunchInputs::capture_for_directory(
        &run, &identity,
    )?;
    logging.recheck()?;
    run_java(&java, &argfile, fixture.root())?;
    logging.recheck()?;
    launch.recheck(&run)?;
    verify(
        fixture.root(),
        &current.manifest,
        &fixture.manifest,
        &cancel,
    )?;
    assert!(!fixture.root().join("logs").exists());
    let serialized = fs::read_to_string(record)?;
    assert!(serialized.contains(&facet_json::to_string(logging.identity())?));
    assert!(fs::read_to_string(logging.log_file())?.contains("sfm-owned-log-marker"));
    assert_eq!(fs::read(failed_log)?, retained);
    Ok(())
}
