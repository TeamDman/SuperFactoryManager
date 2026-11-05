use super::*;

fn run_directory(cache: &Path) -> Result<PathBuf> {
    let run = cache.join("run/test");
    fs::create_dir_all(&run)?;
    fs::write(
        run.join("junit.java.args"),
        b"-cp\nverified-classpath\nRunner",
    )?;
    Ok(run)
}

#[test]
fn junit_logging_binds_owned_configuration_and_mutable_destination_to_launch() -> Result<()> {
    let cache = tempfile::tempdir()?;
    let run = run_directory(cache.path())?;
    let logging = JunitLogging::prepare(cache.path(), &run)?;
    logging.recheck()?;
    let record = run.join("junit-working-directory.json");
    let before = super::super::junit_fixture::write_working_directory_record(
        cache.path(),
        &record,
        cache.path(),
        None,
        Some(&logging),
    )?;
    let serialized = fs::read_to_string(&record)?;
    assert!(serialized.contains(&facet_json::to_string(logging.identity())?));
    assert!(serialized.contains(&logging.identity.configuration_sha256));
    assert!(serialized.contains(&logging.identity.argument_file_sha256));
    assert!(
        logging
            .argfile_bytes
            .starts_with(logging.identity.jvm_argument.as_bytes())
    );
    fs::write(&logging.log_file, b"expected mutable JVM log output")?;
    logging.recheck()?;
    let after = super::super::junit_fixture::write_working_directory_record(
        cache.path(),
        &record,
        cache.path(),
        None,
        Some(&logging),
    )?;
    assert_eq!(before, after);
    fs::write(
        run.join("junit.java.args"),
        b"-cp\nverified-classpath\nRunner",
    )?;
    let next = JunitLogging::prepare(cache.path(), &run)?;
    let changed = super::super::junit_fixture::write_working_directory_record(
        cache.path(),
        &record,
        cache.path(),
        None,
        Some(&next),
    )?;
    assert_ne!(before, changed);
    assert_eq!(
        fs::read(&logging.log_file)?,
        b"expected mutable JVM log output"
    );
    Ok(())
}

#[test]
fn junit_logging_refuses_changed_inputs_and_escaping_owners() -> Result<()> {
    for target in ["config", "arguments", "extra-output"] {
        let cache = tempfile::tempdir()?;
        let run = run_directory(cache.path())?;
        let logging = JunitLogging::prepare(cache.path(), &run)?;
        match target {
            "config" => fs::write(&logging.configuration, b"replaced configuration")?,
            "arguments" => fs::write(&logging.argfile, b"replaced arguments")?,
            "extra-output" => fs::write(
                logging.log_file.with_file_name("unexpected.log"),
                b"unknown",
            )?,
            _ => unreachable!(),
        }
        let _ = logging.recheck().unwrap_err();
    }
    let cache = tempfile::tempdir()?;
    let outside = tempfile::tempdir()?;
    let run = run_directory(outside.path())?;
    assert!(JunitLogging::prepare(cache.path(), &run).is_err());
    assert_eq!(fs::read_dir(run)?.count(), 1);
    Ok(())
}

#[test]
fn junit_logging_escapes_xml_and_refuses_property_lookup_paths() -> Result<()> {
    let base = tempfile::tempdir()?;
    let path = base.path().join("space & value/latest.log");
    let xml = String::from_utf8(configuration_bytes(&path)?)?;
    assert!(xml.contains("space &amp; value"));
    assert!(!xml.contains("${"));
    let _ = configuration_bytes(&base.path().join("${env:HOME}/latest.log")).unwrap_err();
    let _ = configuration_bytes(&base.path().join("new\nline/latest.log")).unwrap_err();
    let _ = configuration_bytes(Path::new("relative/latest.log")).unwrap_err();
    Ok(())
}

#[cfg(unix)]
#[test]
fn junit_logging_refuses_linked_log_outputs_without_writing_through_them() -> Result<()> {
    for hardlink in [false, true] {
        let cache = tempfile::tempdir()?;
        let run = run_directory(cache.path())?;
        let logging = JunitLogging::prepare(cache.path(), &run)?;
        let outside = tempfile::NamedTempFile::new()?;
        fs::write(outside.path(), b"must remain unchanged")?;
        fs::remove_file(&logging.log_file)?;
        if hardlink {
            fs::hard_link(outside.path(), &logging.log_file)?;
        } else {
            std::os::unix::fs::symlink(outside.path(), &logging.log_file)?;
        }
        let _ = logging.recheck().unwrap_err();
        assert_eq!(fs::read(outside.path())?, b"must remain unchanged");
    }
    Ok(())
}
