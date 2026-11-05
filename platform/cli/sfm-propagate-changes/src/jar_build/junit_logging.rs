//! Separate mutable JVM logging from immutable historical `JUnit` fixtures.
use crate::jar_build::build_snapshot_receipt::{checked_descendant, ensure_owned_directory};
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::provenance::sha256;
use eyre::{Result, ensure};
use facet::Facet;
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

const MAX_INPUT: u64 = 8 * 1024 * 1024;

#[derive(Clone, Debug, Facet)]
pub(super) struct LoggingIdentity {
    schema: String,
    configuration: String,
    configuration_sha256: String,
    jvm_argument: String,
    argument_file_sha256: String,
    log_file: String,
}

pub(super) struct JunitLogging {
    cache: PathBuf,
    root: PathBuf,
    configuration: PathBuf,
    config_bytes: Vec<u8>,
    argfile: PathBuf,
    argfile_bytes: Vec<u8>,
    log_file: PathBuf,
    identity: LoggingIdentity,
}

impl JunitLogging {
    pub(super) fn prepare(cache: &Path, run: &Path) -> Result<Self> {
        let cache = dunce::canonicalize(checked_directory(cache)?)?;
        let run = dunce::canonicalize(checked_directory(run)?)?;
        checked_descendant(&cache, &run)?;
        let argfile = run.join("junit.java.args");
        let original = read_input(&argfile)?;
        let root = tempfile::Builder::new()
            .prefix("junit-logging-")
            .tempdir_in(&run)?
            .keep();
        let logs = root.join("logs");
        ensure_owned_directory(&cache, &logs)?;
        let log_file = logs.join("latest.log");
        super::project_compilation_cache::write_record(&cache, &log_file, b"")?;
        let configuration = root.join("log4j2.xml");
        let config_bytes = configuration_bytes(&log_file)?;
        super::project_compilation_cache::write_record(&cache, &configuration, &config_bytes)?;
        let url = reqwest::Url::from_file_path(&configuration)
            .map_err(|()| eyre::eyre!("JUnit logging configuration has no file URI"))?;
        let jvm_argument = format!("-Dlog4j.configurationFile={url}");
        let mut argfile_bytes = super::escape_argfile_arg(jvm_argument.clone()).into_bytes();
        argfile_bytes.push(b'\n');
        argfile_bytes.extend(original);
        ensure!(
            argfile_bytes.len() as u64 <= MAX_INPUT,
            "JUnit logging arguments exceed their input bound"
        );
        super::project_compilation_cache::write_record(&cache, &argfile, &argfile_bytes)?;
        let identity = LoggingIdentity {
            schema: "sfm:junit_logging_output@1".to_owned(),
            configuration: unicode(&configuration)?.to_owned(),
            configuration_sha256: sha256(&config_bytes),
            jvm_argument,
            argument_file_sha256: sha256(&argfile_bytes),
            log_file: unicode(&log_file)?.to_owned(),
        };
        let logging = Self {
            cache,
            root,
            configuration,
            config_bytes,
            argfile,
            argfile_bytes,
            log_file,
            identity,
        };
        logging.recheck()?;
        Ok(logging)
    }

    pub(super) fn identity(&self) -> &LoggingIdentity {
        &self.identity
    }

    pub(super) fn log_file(&self) -> &Path {
        &self.log_file
    }

    pub(super) fn recheck(&self) -> Result<()> {
        checked_directory(&self.root)?;
        checked_descendant(&self.cache, &self.root)?;
        checked_directory(self.argfile.parent().expect("owned run directory"))?;
        ensure!(
            read_input(&self.configuration)? == self.config_bytes
                && read_input(&self.argfile)? == self.argfile_bytes,
            "JUnit logging configuration or arguments changed after admission"
        );
        let logs = self.log_file.parent().expect("owned logging directory");
        checked_directory(logs)?;
        // Bytes here are mutable output. Names, link safety and the selected
        // destination remain checked; no fixture inventory exception is used.
        for entry in fs::read_dir(logs)? {
            ensure!(
                entry?.path() == self.log_file,
                "Unexpected JUnit logging output path"
            );
        }
        super::project_compilation_cache::open_read(&self.log_file)?;
        Ok(())
    }
}

fn unicode(path: &Path) -> Result<&str> {
    path.to_str()
        .ok_or_else(|| eyre::eyre!("JUnit logging path must be Unicode"))
}

fn configuration_bytes(log_file: &Path) -> Result<Vec<u8>> {
    let text = unicode(log_file)?;
    ensure!(
        log_file.is_absolute() && !text.chars().any(|c| c == '$' || c.is_control()),
        "JUnit logging path must be absolute without property lookups or control characters"
    );
    let escaped = text
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&apos;");
    Ok(format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
<Configuration status=\"WARN\">\n\
  <Appenders>\n\
    <Console name=\"Console\" target=\"SYSTEM_OUT\"><PatternLayout pattern=\"[%level] %logger: %msg%n\"/></Console>\n\
    <File name=\"OwnedFile\" fileName=\"{escaped}\" append=\"true\" immediateFlush=\"true\"><PatternLayout pattern=\"[%level] %logger: %msg%n\"/></File>\n\
  </Appenders>\n\
  <Loggers><Root level=\"info\"><AppenderRef ref=\"Console\"/><AppenderRef ref=\"OwnedFile\"/></Root></Loggers>\n\
</Configuration>\n"
    )
    .into_bytes())
}

fn read_input(path: &Path) -> Result<Vec<u8>> {
    let file = super::project_compilation_cache::open_read(path)?;
    ensure!(
        file.metadata()?.len() <= MAX_INPUT,
        "JUnit logging input exceeds its bound"
    );
    let mut bytes = Vec::new();
    file.take(MAX_INPUT + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_INPUT,
        "JUnit logging input grew beyond its bound"
    );
    Ok(bytes)
}

#[cfg(test)]
#[path = "junit_logging_tests.rs"]
mod tests;
