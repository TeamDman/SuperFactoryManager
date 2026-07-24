use crate::cancellation::CancellationToken;
use crate::jdk::resolve_java;
use crate::terminal_output::stdout_line;
use eyre::Context;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeSet;
use std::fs;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Child;
use std::process::Command;
use std::process::ExitStatus;
use std::process::Stdio;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::thread;
use std::time::Duration;
use std::time::Instant;
use zip::ZipArchive;

const METADATA_PATH: &str = "META-INF/jarjar/metadata.json";

#[derive(Clone, Debug)]
pub struct CleanLoaderProbeOptions {
    pub release_jar: PathBuf,
    pub forge_installer: PathBuf,
    pub instance_dir: PathBuf,
    pub success_marker: String,
    pub expected_nested: Vec<String>,
    pub required_nested_classes: Vec<String>,
    pub timeout: Duration,
    pub install_timeout: Duration,
    pub java_home: Option<PathBuf>,
    pub report_json: Option<PathBuf>,
    pub plan_only: bool,
}

#[derive(Debug)]
pub struct CleanLoaderProbeCommand {
    options: CleanLoaderProbeOptions,
    cancellation_token: CancellationToken,
}

impl CleanLoaderProbeCommand {
    #[must_use]
    pub fn new(options: CleanLoaderProbeOptions, cancellation_token: CancellationToken) -> Self {
        Self {
            options,
            cancellation_token,
        }
    }

    /// Inspect the release artifact, create a new production Forge installation, and launch it.
    ///
    /// # Errors
    ///
    /// Returns an error when artifact integrity, isolation, installation, or runtime proof fails.
    #[expect(
        clippy::too_many_lines,
        reason = "the probe deliberately keeps its ordered, fail-closed acceptance gates visible"
    )]
    pub fn invoke(self) -> eyre::Result<()> {
        self.cancellation_token.bail_if_cancelled()?;
        let options = &self.options;
        validate_probe_expectations(options)?;
        let release_jar = canonical_file(&options.release_jar, "release JAR")?;
        let forge_installer = canonical_file(&options.forge_installer, "Forge installer JAR")?;
        let inspection = inspect_release_jar(
            &release_jar,
            &options.expected_nested,
            &options.required_nested_classes,
        )?;
        let release_sha256 = sha256_file(&release_jar)?;
        let installer_sha256 = sha256_file(&forge_installer)?;
        let mut report = CleanLoaderProbeReport {
            schema_version: 1,
            status: "planned".to_string(),
            release_jar: release_jar.display().to_string(),
            release_sha256,
            forge_installer: forge_installer.display().to_string(),
            forge_installer_sha256: installer_sha256,
            instance_dir: absolute_path(&options.instance_dir)?.display().to_string(),
            nested_artifacts: inspection.nested_artifacts,
            required_nested_classes: options.required_nested_classes.clone(),
            mods: vec![release_jar.display().to_string()],
            launch_argfiles: Vec::new(),
            direct_nested_classpath_entries: Vec::new(),
            install_log: None,
            launch_log: None,
            success_marker: options.success_marker.clone(),
            success_marker_observed: false,
            loader_locator_observed: false,
            class_load_diagnostics: Vec::new(),
            exit_code: None,
            timed_out: false,
        };

        stdout_line(format!(
            "Clean loader probe release SHA-256: {}",
            report.release_sha256
        ))?;
        for nested in &report.nested_artifacts {
            stdout_line(format!(
                "Nested artifact {}:{}:{} at {} SHA-256 {}",
                nested.group, nested.artifact, nested.artifact_version, nested.path, nested.sha256
            ))?;
        }

        if options.plan_only {
            write_report(options.report_json.as_deref(), &report)?;
            stdout_line("Plan-only: artifact passed; no installer or loader process was started.")?;
            return Ok(());
        }

        ensure_absent_instance(&options.instance_dir)?;
        fs::create_dir_all(&options.instance_dir).wrap_err_with(|| {
            format!(
                "Failed to create clean loader instance {}",
                options.instance_dir.display()
            )
        })?;
        let mods_dir = options.instance_dir.join("mods");
        fs::create_dir_all(&mods_dir)
            .wrap_err_with(|| format!("Failed to create {}", mods_dir.display()))?;
        let release_name = release_jar
            .file_name()
            .ok_or_else(|| eyre::eyre!("Release JAR has no filename: {}", release_jar.display()))?;
        let installed_mod = mods_dir.join(release_name);
        fs::copy(&release_jar, &installed_mod).wrap_err_with(|| {
            format!(
                "Failed to copy {} to {}",
                release_jar.display(),
                installed_mod.display()
            )
        })?;
        report.mods = vec![installed_mod.display().to_string()];
        fs::write(options.instance_dir.join("eula.txt"), "eula=true\r\n")
            .wrap_err("Failed to write clean-instance eula.txt")?;

        let java = resolve_java(options.java_home.as_deref(), 17)?;
        let logs_dir = options.instance_dir.join("sfm-clean-loader-proof");
        fs::create_dir_all(&logs_dir)
            .wrap_err_with(|| format!("Failed to create {}", logs_dir.display()))?;
        let install_log = logs_dir.join("installer.log");
        report.install_log = Some(install_log.display().to_string());
        let mut install = Command::new(&java.executable);
        install
            .current_dir(&options.instance_dir)
            .args(["-jar"])
            .arg(&forge_installer)
            .arg("--installServer")
            .arg(&options.instance_dir);
        let install_result = run_captured(
            install,
            &install_log,
            options.install_timeout,
            &self.cancellation_token,
            None,
            false,
        )?;
        if !install_result.status.success() {
            report.status = "installer-failed".to_string();
            report.exit_code = install_result.status.code();
            report.timed_out = install_result.timed_out;
            write_report(options.report_json.as_deref(), &report)?;
            eyre::bail!(
                "Forge installer failed with status {}. See {}",
                install_result.status,
                install_log.display()
            );
        }

        let argfiles = discover_launch_argfiles(&options.instance_dir)?;
        enable_class_load_diagnostics(&argfiles[0])?;
        report.launch_argfiles = argfiles
            .iter()
            .map(|path| path.display().to_string())
            .collect();
        report.direct_nested_classpath_entries =
            direct_nested_classpath_entries(&argfiles, &inspection.nested_file_names)?;
        if !report.direct_nested_classpath_entries.is_empty() {
            report.status = "isolation-failed".to_string();
            write_report(options.report_json.as_deref(), &report)?;
            eyre::bail!(
                "Production launch plan directly references nested artifact(s): {}",
                report.direct_nested_classpath_entries.join(", ")
            );
        }
        assert_mods_dir_isolated(&mods_dir, &installed_mod)?;

        let launch_log = logs_dir.join("launch.log");
        report.launch_log = Some(launch_log.display().to_string());
        let mut launch = Command::new(&java.executable);
        launch.current_dir(&options.instance_dir);
        for argfile in &argfiles {
            launch.arg(format!("@{}", argfile.display()));
        }
        launch.arg("nogui");
        let launch_result = run_captured(
            launch,
            &launch_log,
            options.timeout,
            &self.cancellation_token,
            Some(&options.success_marker),
            true,
        )?;
        report.success_marker_observed = launch_result.marker_observed;
        report.loader_locator_observed = launch_result
            .output
            .lines()
            .any(|line| line.contains("JarInJarDependencyLocator") && line.contains("Found "));
        report.class_load_diagnostics = class_load_diagnostics(
            &launch_result.output,
            &options.required_nested_classes,
            &inspection.nested_file_names,
        );
        report.exit_code = launch_result.status.code();
        report.timed_out = launch_result.timed_out;
        report.status = if report.success_marker_observed
            && report.loader_locator_observed
            && !report.class_load_diagnostics.is_empty()
        {
            "passed".to_string()
        } else {
            "launch-failed".to_string()
        };
        write_report(options.report_json.as_deref(), &report)?;
        if report.status != "passed" {
            eyre::bail!(
                "Clean production Forge proof incomplete (marker={}, locator={}, class_diagnostics={}). See {}",
                report.success_marker_observed,
                report.loader_locator_observed,
                report.class_load_diagnostics.len(),
                launch_log.display()
            );
        }
        stdout_line(format!(
            "Clean production Forge launch observed {:?}; report status passed.",
            options.success_marker
        ))?;
        Ok(())
    }
}

fn validate_probe_expectations(options: &CleanLoaderProbeOptions) -> eyre::Result<()> {
    if options.success_marker.trim().is_empty() {
        eyre::bail!("--success-marker must not be empty");
    }
    for identity in &options.expected_nested {
        let Some((group, artifact)) = identity.split_once(':') else {
            eyre::bail!("--expected-nested must use group:artifact syntax: {identity}");
        };
        if group.is_empty() || artifact.is_empty() || artifact.contains(':') {
            eyre::bail!("--expected-nested must use group:artifact syntax: {identity}");
        }
    }
    for class in &options.required_nested_classes {
        if class.trim().is_empty() {
            eyre::bail!("--required-nested-class must not be empty");
        }
    }
    Ok(())
}

#[derive(Clone, Debug, Facet)]
struct JarJarMetadata {
    jars: Vec<JarJarMetadataEntry>,
}

#[derive(Clone, Debug, Facet)]
struct JarJarMetadataEntry {
    identifier: JarJarIdentifier,
    version: JarJarVersion,
    path: String,
    #[facet(rename = "isObfuscated")]
    is_obfuscated: bool,
}

#[derive(Clone, Debug, Facet)]
struct JarJarIdentifier {
    group: String,
    artifact: String,
}

#[derive(Clone, Debug, Facet)]
struct JarJarVersion {
    range: String,
    #[facet(rename = "artifactVersion")]
    artifact_version: String,
}

#[derive(Clone, Debug, Facet)]
struct CleanLoaderProbeReport {
    schema_version: u8,
    status: String,
    release_jar: String,
    release_sha256: String,
    forge_installer: String,
    forge_installer_sha256: String,
    instance_dir: String,
    nested_artifacts: Vec<NestedArtifactReport>,
    required_nested_classes: Vec<String>,
    mods: Vec<String>,
    launch_argfiles: Vec<String>,
    direct_nested_classpath_entries: Vec<String>,
    install_log: Option<String>,
    launch_log: Option<String>,
    success_marker: String,
    success_marker_observed: bool,
    loader_locator_observed: bool,
    class_load_diagnostics: Vec<String>,
    exit_code: Option<i32>,
    timed_out: bool,
}

#[derive(Clone, Debug, Facet)]
struct NestedArtifactReport {
    group: String,
    artifact: String,
    range: String,
    artifact_version: String,
    path: String,
    is_obfuscated: bool,
    sha256: String,
}

#[derive(Debug)]
struct ArtifactInspection {
    nested_artifacts: Vec<NestedArtifactReport>,
    nested_file_names: BTreeSet<String>,
}

fn inspect_release_jar(
    release_jar: &Path,
    expected_nested: &[String],
    required_classes: &[String],
) -> eyre::Result<ArtifactInspection> {
    let file = fs::File::open(release_jar)
        .wrap_err_with(|| format!("Failed to open {}", release_jar.display()))?;
    let mut archive = ZipArchive::new(file).wrap_err_with(|| {
        format!(
            "Release artifact is not a valid ZIP: {}",
            release_jar.display()
        )
    })?;
    let mut metadata_json = String::new();
    archive
        .by_name(METADATA_PATH)
        .wrap_err_with(|| format!("Release artifact is missing {METADATA_PATH}"))?
        .read_to_string(&mut metadata_json)
        .wrap_err_with(|| format!("Failed to read {METADATA_PATH}"))?;
    let metadata: JarJarMetadata = facet_json::from_str(&metadata_json)
        .wrap_err_with(|| format!("Failed to parse {METADATA_PATH}"))?;
    if metadata.jars.is_empty() {
        eyre::bail!("{METADATA_PATH} contains no nested artifacts");
    }

    let mut identities = BTreeSet::new();
    let mut nested_file_names = BTreeSet::new();
    let mut reports = Vec::new();
    let required_paths = required_classes
        .iter()
        .map(|class| format!("{}.class", class.replace('.', "/")))
        .collect::<BTreeSet<_>>();
    let mut found_required_paths = BTreeSet::new();
    for entry in metadata.jars {
        validate_nested_path(&entry.path)?;
        let identity = format!("{}:{}", entry.identifier.group, entry.identifier.artifact);
        if !identities.insert(identity.clone()) {
            eyre::bail!("Duplicate nested Maven identity in metadata: {identity}");
        }
        let file_name = Path::new(&entry.path)
            .file_name()
            .and_then(|value| value.to_str())
            .ok_or_else(|| eyre::eyre!("Nested path has no UTF-8 filename: {}", entry.path))?
            .to_string();
        if !nested_file_names.insert(file_name) {
            eyre::bail!(
                "Duplicate nested artifact filename in metadata: {}",
                entry.path
            );
        }
        let mut nested_bytes = Vec::new();
        archive
            .by_name(&entry.path)
            .wrap_err_with(|| format!("Metadata references missing nested entry {}", entry.path))?
            .read_to_end(&mut nested_bytes)
            .wrap_err_with(|| format!("Failed to read nested entry {}", entry.path))?;
        let cursor = std::io::Cursor::new(&nested_bytes);
        let nested_archive = ZipArchive::new(cursor)
            .wrap_err_with(|| format!("Nested entry is not a valid JAR: {}", entry.path))?;
        for name in nested_archive.file_names() {
            if required_paths.contains(name) {
                found_required_paths.insert(name.to_string());
            }
        }
        reports.push(NestedArtifactReport {
            group: entry.identifier.group,
            artifact: entry.identifier.artifact,
            range: entry.version.range,
            artifact_version: entry.version.artifact_version,
            path: entry.path,
            is_obfuscated: entry.is_obfuscated,
            sha256: sha256_bytes(&nested_bytes),
        });
    }
    for expected in expected_nested {
        if !identities.contains(expected) {
            eyre::bail!("Required nested Maven identity is absent: {expected}");
        }
    }
    if let Some(required_path) = required_paths.difference(&found_required_paths).next() {
        eyre::bail!(
            "No nested artifact contains required class {}",
            required_path.trim_end_matches(".class").replace('/', ".")
        );
    }
    Ok(ArtifactInspection {
        nested_artifacts: reports,
        nested_file_names,
    })
}

fn validate_nested_path(path: &str) -> eyre::Result<()> {
    if !path.starts_with("META-INF/jarjar/")
        || path.contains('\\')
        || path.split('/').any(|part| matches!(part, "" | "." | ".."))
    {
        eyre::bail!("Unsafe JarJar metadata path: {path}");
    }
    Ok(())
}

fn ensure_absent_instance(instance_dir: &Path) -> eyre::Result<()> {
    if instance_dir.exists() {
        eyre::bail!(
            "Clean loader instance target already exists; choose a new absent path: {}",
            instance_dir.display()
        );
    }
    Ok(())
}

fn discover_launch_argfiles(instance_dir: &Path) -> eyre::Result<Vec<PathBuf>> {
    let user_args = instance_dir.join("user_jvm_args.txt");
    if !user_args.is_file() {
        eyre::bail!("Forge installer did not create {}", user_args.display());
    }
    let mut loader_args = Vec::new();
    let libraries = instance_dir.join("libraries");
    for entry in walkdir::WalkDir::new(&libraries).follow_links(false) {
        let entry = entry.wrap_err_with(|| format!("Failed to walk {}", libraries.display()))?;
        if entry.file_type().is_file()
            && matches!(
                entry.file_name().to_str(),
                Some("win_args.txt" | "unix_args.txt")
            )
        {
            loader_args.push(entry.into_path());
        }
    }
    loader_args.sort();
    loader_args.dedup();
    let preferred_name = if cfg!(windows) {
        "win_args.txt"
    } else {
        "unix_args.txt"
    };
    let preferred = loader_args
        .into_iter()
        .filter(|path| path.file_name().is_some_and(|name| name == preferred_name))
        .collect::<Vec<_>>();
    if preferred.len() != 1 {
        eyre::bail!(
            "Expected exactly one production Forge {preferred_name}, found {} under {}",
            preferred.len(),
            libraries.display()
        );
    }
    Ok(vec![user_args, preferred[0].clone()])
}

fn direct_nested_classpath_entries(
    argfiles: &[PathBuf],
    nested_file_names: &BTreeSet<String>,
) -> eyre::Result<Vec<String>> {
    let mut hits = BTreeSet::new();
    for argfile in argfiles {
        let content = fs::read_to_string(argfile)
            .wrap_err_with(|| format!("Failed to read {}", argfile.display()))?;
        for line in content.lines() {
            let normalized = line.replace('\\', "/");
            for file_name in nested_file_names {
                if normalized.contains(file_name) {
                    hits.insert(format!("{}:{}", argfile.display(), line.trim()));
                }
            }
        }
    }
    Ok(hits.into_iter().collect())
}

fn enable_class_load_diagnostics(user_jvm_args: &Path) -> eyre::Result<()> {
    let mut contents = fs::read_to_string(user_jvm_args)
        .wrap_err_with(|| format!("Failed to read {}", user_jvm_args.display()))?;
    if !contents.ends_with('\n') {
        contents.push('\n');
    }
    contents.push_str("-Xlog:class+load=trace\n");
    contents.push_str("-Dforge.logging.console.level=debug\n");
    fs::write(user_jvm_args, contents)
        .wrap_err_with(|| format!("Failed to update {}", user_jvm_args.display()))
}

fn class_load_diagnostics(
    output: &str,
    required_classes: &[String],
    nested_file_names: &BTreeSet<String>,
) -> Vec<String> {
    let lines = output.lines().collect::<Vec<_>>();
    let mut diagnostics = Vec::new();
    for (index, line) in lines.iter().enumerate() {
        let is_class_source = line.contains("[class,load]")
            && (required_classes.iter().any(|class| line.contains(class))
                || nested_file_names.iter().any(|name| line.contains(name)));
        if !is_class_source {
            continue;
        }
        diagnostics.push((*line).to_string());
        if let Some(loader_line) = lines
            .get(index + 1)
            .filter(|next| next.contains("[class,load]") && next.contains("loader:"))
        {
            diagnostics.push((*loader_line).to_string());
        }
    }
    diagnostics
}

fn assert_mods_dir_isolated(mods_dir: &Path, expected_mod: &Path) -> eyre::Result<()> {
    let expected_mod = dunce::canonicalize(expected_mod)
        .wrap_err_with(|| format!("Failed to canonicalize {}", expected_mod.display()))?;
    let mut mods = Vec::new();
    for entry in
        fs::read_dir(mods_dir).wrap_err_with(|| format!("Failed to read {}", mods_dir.display()))?
    {
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", mods_dir.display()))?;
        if entry
            .file_type()
            .wrap_err("Failed to inspect mods directory entry")?
            .is_file()
        {
            mods.push(dunce::canonicalize(entry.path())?);
        }
    }
    if mods != [expected_mod] {
        eyre::bail!(
            "Clean loader mods directory must contain only the release JAR; found {:?}",
            mods
        );
    }
    Ok(())
}

#[derive(Debug)]
struct CapturedProcess {
    status: ExitStatus,
    output: String,
    marker_observed: bool,
    timed_out: bool,
}

fn run_captured(
    mut command: Command,
    log_path: &Path,
    timeout: Duration,
    cancellation_token: &CancellationToken,
    marker: Option<&str>,
    stop_after_marker: bool,
) -> eyre::Result<CapturedProcess> {
    command
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::piped());
    let mut child = command
        .spawn()
        .wrap_err_with(|| format!("Failed to start process for {}", log_path.display()))?;
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| eyre::eyre!("Missing child stdout"))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| eyre::eyre!("Missing child stderr"))?;
    let lines = Arc::new(Mutex::new(Vec::<String>::new()));
    let marker_observed = Arc::new(AtomicBool::new(false));
    let marker_owned = marker.map(str::to_string);
    let stdout_thread = capture_stream(
        stdout,
        "stdout",
        Arc::clone(&lines),
        Arc::clone(&marker_observed),
        marker_owned.clone(),
    );
    let stderr_thread = capture_stream(
        stderr,
        "stderr",
        Arc::clone(&lines),
        Arc::clone(&marker_observed),
        marker_owned,
    );
    let started = Instant::now();
    let mut stop_sent = false;
    let mut stop_deadline = None;
    let mut timed_out = false;
    let status = loop {
        if let Some(status) = child.try_wait().wrap_err("Failed to poll child process")? {
            break status;
        }
        if cancellation_token.is_cancelled() {
            terminate_child(&mut child);
            cancellation_token.bail_if_cancelled()?;
        }
        if stop_after_marker && marker_observed.load(Ordering::Acquire) && !stop_sent {
            if let Some(stdin) = child.stdin.as_mut() {
                let _ = stdin.write_all(b"stop\n");
                let _ = stdin.flush();
            }
            stop_sent = true;
            stop_deadline = Some(Instant::now() + Duration::from_secs(30));
        }
        if started.elapsed() >= timeout
            || stop_deadline.is_some_and(|deadline| Instant::now() >= deadline)
        {
            timed_out = true;
            terminate_child(&mut child);
            break child
                .wait()
                .wrap_err("Failed to wait after terminating process")?;
        }
        thread::sleep(Duration::from_millis(100));
    };
    drop(child.stdin.take());
    stdout_thread
        .join()
        .map_err(|_panic_payload| eyre::eyre!("stdout capture thread panicked"))??;
    stderr_thread
        .join()
        .map_err(|_panic_payload| eyre::eyre!("stderr capture thread panicked"))??;
    let output = lines
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .join("\n");
    fs::write(log_path, format!("{output}\n"))
        .wrap_err_with(|| format!("Failed to write {}", log_path.display()))?;
    Ok(CapturedProcess {
        status,
        output,
        marker_observed: marker_observed.load(Ordering::Acquire),
        timed_out,
    })
}

fn capture_stream(
    stream: impl Read + Send + 'static,
    label: &'static str,
    lines: Arc<Mutex<Vec<String>>>,
    marker_observed: Arc<AtomicBool>,
    marker: Option<String>,
) -> thread::JoinHandle<eyre::Result<()>> {
    thread::spawn(move || {
        for line in BufReader::new(stream).lines() {
            let line = line.wrap_err("Failed to read child process output")?;
            if marker.as_ref().is_some_and(|marker| line.contains(marker)) {
                marker_observed.store(true, Ordering::Release);
            }
            lines
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .push(format!("[{label}] {line}"));
        }
        Ok(())
    })
}

fn terminate_child(child: &mut Child) {
    let _ = child.kill();
}

fn canonical_file(path: &Path, label: &str) -> eyre::Result<PathBuf> {
    if !path.is_file() {
        eyre::bail!(
            "{label} does not exist or is not a file: {}",
            path.display()
        );
    }
    dunce::canonicalize(path).wrap_err_with(|| format!("Failed to canonicalize {}", path.display()))
}

fn absolute_path(path: &Path) -> eyre::Result<PathBuf> {
    if path.is_absolute() {
        return Ok(path.to_path_buf());
    }
    Ok(std::env::current_dir()
        .wrap_err("Failed to resolve current directory")?
        .join(path))
}

fn sha256_file(path: &Path) -> eyre::Result<String> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    Ok(sha256_bytes(&bytes))
}

fn sha256_bytes(bytes: &[u8]) -> String {
    format!("{:X}", Sha256::digest(bytes))
}

fn write_report(path: Option<&Path>, report: &CleanLoaderProbeReport) -> eyre::Result<()> {
    let Some(path) = path else {
        return Ok(());
    };
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    }
    let mut json = facet_json::to_string_pretty(report)?;
    json.push('\n');
    fs::write(path, json).wrap_err_with(|| format!("Failed to write {}", path.display()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    #[test]
    fn launch_plan_rejects_direct_nested_library_entry() {
        let temp = tempfile::tempdir().expect("temp");
        let args = temp.path().join("win_args.txt");
        fs::write(&args, "-p\nlibraries/vox-java-1.0.jar\n").expect("args");
        let names = BTreeSet::from(["vox-java-1.0.jar".to_string()]);
        let hits = direct_nested_classpath_entries(&[args], &names).expect("scan");
        assert_eq!(hits.len(), 1);
        assert!(hits[0].contains("vox-java-1.0.jar"));
    }

    #[test]
    fn launch_plan_accepts_production_argfiles_without_nested_library_entry() {
        let temp = tempfile::tempdir().expect("temp");
        let args = temp.path().join("win_args.txt");
        fs::write(
            &args,
            "-p\nlibraries/cpw/mods/bootstraplauncher/1.1.2/bootstraplauncher-1.1.2.jar\n",
        )
        .expect("args");
        let names = BTreeSet::from(["vox-java-1.0.jar".to_string()]);
        let hits = direct_nested_classpath_entries(&[args], &names).expect("scan");
        assert!(hits.is_empty());
    }

    #[test]
    fn release_inspection_rejects_missing_nested_entry() {
        let temp = tempfile::tempdir().expect("temp");
        let jar = temp.path().join("missing.jar");
        write_release_fixture(&jar, None, false);
        let error = inspect_release_jar(&jar, &["org.example:probe".to_string()], &[])
            .expect_err("missing nested entry must fail");
        assert!(error.to_string().contains("missing nested entry"));
    }

    #[test]
    fn release_inspection_rejects_corrupt_nested_jar() {
        let temp = tempfile::tempdir().expect("temp");
        let jar = temp.path().join("corrupt.jar");
        write_release_fixture(&jar, Some(b"not a jar"), false);
        let error = inspect_release_jar(&jar, &["org.example:probe".to_string()], &[])
            .expect_err("corrupt nested entry must fail");
        assert!(error.to_string().contains("not a valid JAR"));
    }

    #[test]
    fn release_inspection_accepts_valid_nested_jar_and_class() {
        let temp = tempfile::tempdir().expect("temp");
        let nested = nested_fixture_bytes("org/example/Probe.class");
        let jar = temp.path().join("valid.jar");
        write_release_fixture(&jar, Some(&nested), false);
        let result = inspect_release_jar(
            &jar,
            &["org.example:probe".to_string()],
            &["org.example.Probe".to_string()],
        )
        .expect("valid release");
        assert_eq!(result.nested_artifacts.len(), 1);
    }

    fn write_release_fixture(path: &Path, nested: Option<&[u8]>, include_duplicate: bool) {
        let file = fs::File::create(path).expect("release fixture");
        let mut writer = ZipWriter::new(file);
        let options = SimpleFileOptions::default();
        writer.start_file(METADATA_PATH, options).expect("metadata");
        let extra = if include_duplicate {
            r#",{"identifier":{"group":"org.example","artifact":"probe"},"version":{"range":"[1.0]","artifactVersion":"1.0"},"path":"META-INF/jarjar/probe-1.0.jar","isObfuscated":false}"#
        } else {
            ""
        };
        write!(
            writer,
            r#"{{"jars":[{{"identifier":{{"group":"org.example","artifact":"probe"}},"version":{{"range":"[1.0]","artifactVersion":"1.0"}},"path":"META-INF/jarjar/probe-1.0.jar","isObfuscated":false}}{extra}]}}"#
        )
        .expect("metadata bytes");
        if let Some(bytes) = nested {
            writer
                .start_file("META-INF/jarjar/probe-1.0.jar", options)
                .expect("nested");
            writer.write_all(bytes).expect("nested bytes");
        }
        writer.finish().expect("release finish");
    }

    fn nested_fixture_bytes(class_path: &str) -> Vec<u8> {
        let mut cursor = std::io::Cursor::new(Vec::new());
        {
            let mut writer = ZipWriter::new(&mut cursor);
            writer
                .start_file(class_path, SimpleFileOptions::default())
                .expect("class");
            writer.write_all(b"class bytes").expect("class bytes");
            writer.finish().expect("nested finish")
        };
        cursor.into_inner()
    }
}
