use crate::cancellation::CancellationToken;
use crate::jdk::resolve_java;
use crate::terminal_output::stdout_line;
use eyre::Context;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Read;
use std::io::Seek;
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
const JAVA_ENVIRONMENT_VARIABLES: [&str; 4] = [
    "CLASSPATH",
    "JAVA_TOOL_OPTIONS",
    "_JAVA_OPTIONS",
    "JDK_JAVA_OPTIONS",
];

#[derive(Clone, Debug)]
pub struct CleanLoaderProbeOptions {
    pub release_jar: PathBuf,
    pub expected_release_sha256: String,
    pub forge_installer: PathBuf,
    pub expected_forge_installer_sha256: String,
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
        verify_expected_sha256(
            "release JAR",
            &options.expected_release_sha256,
            &release_sha256,
        )?;
        verify_expected_sha256(
            "Forge installer JAR",
            &options.expected_forge_installer_sha256,
            &installer_sha256,
        )?;
        let mut report = CleanLoaderProbeReport {
            schema_version: 2,
            cli_source_revision: env!("GIT_REVISION").to_string(),
            source_commit: source_commit(),
            invocation: std::env::args().collect(),
            operating_system: std::env::consts::OS.to_string(),
            architecture: std::env::consts::ARCH.to_string(),
            status: "planned".to_string(),
            release_jar: release_jar.display().to_string(),
            expected_release_sha256: normalize_sha256(&options.expected_release_sha256)?,
            release_sha256,
            installed_release_sha256: None,
            forge_installer: forge_installer.display().to_string(),
            expected_forge_installer_sha256: normalize_sha256(
                &options.expected_forge_installer_sha256,
            )?,
            forge_installer_sha256: installer_sha256,
            java_executable: None,
            java_version_output: None,
            rejected_java_environment: java_environment_values(),
            instance_dir: absolute_path(&options.instance_dir)?.display().to_string(),
            nested_artifacts: inspection.nested_artifacts,
            required_nested_classes: options.required_nested_classes.clone(),
            mods: vec![release_jar.display().to_string()],
            launch_argfiles: Vec::new(),
            direct_nested_classpath_entries: Vec::new(),
            loose_nested_artifact_copies: Vec::new(),
            install_log: None,
            launch_log: None,
            success_marker: options.success_marker.clone(),
            success_marker_observed: false,
            loader_locator_observed: false,
            loader_locator_dependency_count: 0,
            required_class_evidence: Vec::new(),
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
            write_report_and_manifest(options.report_json.as_deref(), &report)?;
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
        let installed_release_sha256 = sha256_file(&installed_mod)?;
        verify_expected_sha256(
            "installed release JAR",
            &options.expected_release_sha256,
            &installed_release_sha256,
        )?;
        report.installed_release_sha256 = Some(installed_release_sha256);
        report.mods = vec![installed_mod.display().to_string()];
        fs::write(options.instance_dir.join("eula.txt"), "eula=true\r\n")
            .wrap_err("Failed to write clean-instance eula.txt")?;

        let java = resolve_java(options.java_home.as_deref(), 17)?;
        report.java_executable = Some(java.executable.display().to_string());
        report.java_version_output = Some(java.version_output.clone());
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
        if !install_result.status.success() || install_result.timed_out {
            report.status = "installer-failed".to_string();
            report.exit_code = install_result.status.code();
            report.timed_out = install_result.timed_out;
            write_report_and_manifest(options.report_json.as_deref(), &report)?;
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
            direct_nested_classpath_entries(&argfiles, &inspection.leakage_needles)?;
        report.loose_nested_artifact_copies = loose_nested_artifact_copies(
            &options.instance_dir,
            &installed_mod,
            &inspection.nested_sha256,
        )?;
        if !report.direct_nested_classpath_entries.is_empty() {
            report.status = "isolation-failed".to_string();
            write_report_and_manifest(options.report_json.as_deref(), &report)?;
            eyre::bail!(
                "Production launch plan directly references nested artifact(s): {}",
                report.direct_nested_classpath_entries.join(", ")
            );
        }
        if !report.loose_nested_artifact_copies.is_empty() {
            report.status = "isolation-failed".to_string();
            write_report_and_manifest(options.report_json.as_deref(), &report)?;
            eyre::bail!(
                "Clean instance contains loose copies of nested artifact bytes: {}",
                report.loose_nested_artifact_copies.join(", ")
            );
        }
        assert_mods_dir_isolated(&mods_dir, &installed_mod, &options.expected_release_sha256)?;

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
        report.loader_locator_dependency_count =
            loader_locator_dependency_count(&launch_result.output);
        report.loader_locator_observed = report.loader_locator_dependency_count >= 1;
        report.required_class_evidence = required_class_evidence(
            &launch_result.output,
            &inspection.required_class_nested_paths,
        );
        report.exit_code = launch_result.status.code();
        report.timed_out = launch_result.timed_out;
        report.status = if proof_passes(
            launch_result.status.success(),
            launch_result.timed_out,
            launch_result.marker_observed,
            report.loader_locator_dependency_count,
            report.required_class_evidence.len(),
            options.required_nested_classes.len(),
        ) {
            "passed".to_string()
        } else {
            "launch-failed".to_string()
        };
        write_report_and_manifest(options.report_json.as_deref(), &report)?;
        if report.status != "passed" {
            eyre::bail!(
                "Clean production Forge proof incomplete (exit_success={}, timed_out={}, marker={}, locator_count={}, class_evidence={}/{}). See {}",
                launch_result.status.success(),
                launch_result.timed_out,
                report.success_marker_observed,
                report.loader_locator_dependency_count,
                report.required_class_evidence.len(),
                options.required_nested_classes.len(),
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
    if options.expected_nested.is_empty() {
        eyre::bail!("At least one --expected-nested identity is required");
    }
    if options.required_nested_classes.is_empty() {
        eyre::bail!("At least one --required-nested-class is required");
    }
    normalize_sha256(&options.expected_release_sha256)?;
    normalize_sha256(&options.expected_forge_installer_sha256)?;
    let inherited = java_environment_values();
    if !inherited.is_empty() {
        let names = inherited
            .iter()
            .map(|entry| entry.name.as_str())
            .collect::<Vec<_>>()
            .join(", ");
        eyre::bail!(
            "Clean loader proof rejects inherited Java/classpath environment variables: {names}"
        );
    }
    Ok(())
}

fn normalize_sha256(value: &str) -> eyre::Result<String> {
    let value = value.trim();
    if value.len() != 64 || !value.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        eyre::bail!("Expected SHA-256 must contain exactly 64 hexadecimal characters: {value:?}");
    }
    Ok(value.to_ascii_uppercase())
}

fn verify_expected_sha256(label: &str, expected: &str, actual: &str) -> eyre::Result<()> {
    let expected = normalize_sha256(expected)?;
    if expected != actual {
        eyre::bail!("{label} SHA-256 mismatch: expected {expected}, got {actual}");
    }
    Ok(())
}

fn java_environment_values() -> Vec<EnvironmentValue> {
    JAVA_ENVIRONMENT_VARIABLES
        .iter()
        .filter_map(|name| {
            std::env::var(name).ok().and_then(|value| {
                (!value.is_empty()).then(|| EnvironmentValue {
                    name: (*name).to_string(),
                    value,
                })
            })
        })
        .collect()
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
    cli_source_revision: String,
    source_commit: String,
    invocation: Vec<String>,
    operating_system: String,
    architecture: String,
    status: String,
    release_jar: String,
    expected_release_sha256: String,
    release_sha256: String,
    installed_release_sha256: Option<String>,
    forge_installer: String,
    expected_forge_installer_sha256: String,
    forge_installer_sha256: String,
    java_executable: Option<String>,
    java_version_output: Option<String>,
    rejected_java_environment: Vec<EnvironmentValue>,
    instance_dir: String,
    nested_artifacts: Vec<NestedArtifactReport>,
    required_nested_classes: Vec<String>,
    mods: Vec<String>,
    launch_argfiles: Vec<String>,
    direct_nested_classpath_entries: Vec<String>,
    loose_nested_artifact_copies: Vec<String>,
    install_log: Option<String>,
    launch_log: Option<String>,
    success_marker: String,
    success_marker_observed: bool,
    loader_locator_observed: bool,
    loader_locator_dependency_count: u64,
    required_class_evidence: Vec<RequiredClassEvidence>,
    exit_code: Option<i32>,
    timed_out: bool,
}

#[derive(Clone, Debug, Facet)]
struct EnvironmentValue {
    name: String,
    value: String,
}

#[derive(Clone, Debug, Facet)]
struct CleanLoaderEvidenceManifest {
    schema_version: u8,
    source_commit: String,
    report: EvidenceFileHash,
    logs: Vec<EvidenceFileHash>,
}

#[derive(Clone, Debug, Facet)]
struct EvidenceFileHash {
    path: String,
    sha256: String,
}

#[derive(Clone, Debug, Facet, PartialEq, Eq)]
struct RequiredClassEvidence {
    class_name: String,
    source_line: String,
    loader_line: String,
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
    leakage_needles: BTreeSet<String>,
    nested_sha256: BTreeSet<String>,
    required_class_nested_paths: BTreeMap<String, String>,
}

#[expect(
    clippy::too_many_lines,
    reason = "artifact validation keeps ZIP, metadata, identity, class, and hash gates adjacent"
)]
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
    validate_zip_archive(&mut archive, "release JAR")?;
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
    let mut leakage_needles = BTreeSet::new();
    let mut nested_sha256 = BTreeSet::new();
    for class in required_classes {
        leakage_needles.insert(class.clone());
        leakage_needles.insert(class.replace('.', "/"));
    }
    let mut reports = Vec::new();
    let required_path_classes = required_classes
        .iter()
        .map(|class| (format!("{}.class", class.replace('.', "/")), class.clone()))
        .collect::<BTreeMap<_, _>>();
    let required_paths = required_path_classes
        .keys()
        .cloned()
        .collect::<BTreeSet<_>>();
    let mut found_required_paths = BTreeSet::new();
    let mut required_class_nested_paths = BTreeMap::new();
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
        leakage_needles.extend(nested_file_names.iter().cloned());
        leakage_needles.insert(entry.identifier.artifact.clone());
        leakage_needles.insert(identity);
        let mut nested_bytes = Vec::new();
        archive
            .by_name(&entry.path)
            .wrap_err_with(|| format!("Metadata references missing nested entry {}", entry.path))?
            .read_to_end(&mut nested_bytes)
            .wrap_err_with(|| format!("Failed to read nested entry {}", entry.path))?;
        let cursor = std::io::Cursor::new(&nested_bytes);
        let mut nested_archive = ZipArchive::new(cursor)
            .wrap_err_with(|| format!("Nested entry is not a valid JAR: {}", entry.path))?;
        validate_zip_archive(&mut nested_archive, &format!("nested JAR {}", entry.path))?;
        for name in nested_archive.file_names() {
            if let Some(class) = required_path_classes.get(name) {
                found_required_paths.insert(name.to_string());
                if required_class_nested_paths
                    .insert(class.clone(), entry.path.clone())
                    .is_some()
                {
                    eyre::bail!(
                        "Required class {class} is duplicated across nested JarJar artifacts"
                    );
                }
            }
        }
        let sha256 = sha256_bytes(&nested_bytes);
        nested_sha256.insert(sha256.clone());
        reports.push(NestedArtifactReport {
            group: entry.identifier.group,
            artifact: entry.identifier.artifact,
            range: entry.version.range,
            artifact_version: entry.version.artifact_version,
            path: entry.path,
            is_obfuscated: entry.is_obfuscated,
            sha256,
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
        leakage_needles,
        nested_sha256,
        required_class_nested_paths,
    })
}

fn validate_zip_archive<R: Read + Seek>(
    archive: &mut ZipArchive<R>,
    label: &str,
) -> eyre::Result<()> {
    let mut exact_names = BTreeSet::new();
    let mut folded_names = BTreeSet::new();
    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .wrap_err_with(|| format!("Failed to inspect entry {index} in {label}"))?;
        let name = entry.name().to_string();
        if entry.enclosed_name().is_none()
            || name.contains('\\')
            || name.contains('\0')
            || name.split('/').any(|part| matches!(part, "." | ".."))
        {
            eyre::bail!("Unsafe ZIP entry in {label}: {name}");
        }
        if !exact_names.insert(name.clone()) {
            eyre::bail!("Duplicate ZIP entry in {label}: {name}");
        }
        if !folded_names.insert(name.to_ascii_lowercase()) {
            eyre::bail!("Case-colliding ZIP entry in {label}: {name}");
        }
    }
    Ok(())
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
    leakage_needles: &BTreeSet<String>,
) -> eyre::Result<Vec<String>> {
    let mut hits = BTreeSet::new();
    for argfile in argfiles {
        let content = fs::read_to_string(argfile)
            .wrap_err_with(|| format!("Failed to read {}", argfile.display()))?;
        for line in content.lines() {
            let normalized = line.replace('\\', "/").to_ascii_lowercase();
            for needle in leakage_needles {
                if normalized.contains(&needle.to_ascii_lowercase()) {
                    hits.insert(format!("{}:{}", argfile.display(), line.trim()));
                }
            }
        }
    }
    Ok(hits.into_iter().collect())
}

fn loose_nested_artifact_copies(
    instance_dir: &Path,
    installed_release: &Path,
    nested_sha256: &BTreeSet<String>,
) -> eyre::Result<Vec<String>> {
    let installed_release = dunce::canonicalize(installed_release)
        .wrap_err_with(|| format!("Failed to canonicalize {}", installed_release.display()))?;
    let mut copies = Vec::new();
    for entry in walkdir::WalkDir::new(instance_dir).follow_links(false) {
        let entry = entry.wrap_err_with(|| format!("Failed to walk {}", instance_dir.display()))?;
        if !entry.file_type().is_file() || entry.path().extension().is_none_or(|ext| ext != "jar") {
            continue;
        }
        let path = dunce::canonicalize(entry.path())
            .wrap_err_with(|| format!("Failed to canonicalize {}", entry.path().display()))?;
        if path == installed_release {
            continue;
        }
        let hash = sha256_file(&path)?;
        if nested_sha256.contains(&hash) {
            copies.push(path.display().to_string());
        }
    }
    copies.sort();
    Ok(copies)
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

fn loader_locator_dependency_count(output: &str) -> u64 {
    output
        .lines()
        .filter(|line| line.contains("JarInJarDependencyLocator"))
        .filter_map(|line| {
            let words = line.split_whitespace().collect::<Vec<_>>();
            words.windows(2).find_map(|pair| {
                (pair[0] == "Found")
                    .then(|| pair[1].parse::<u64>().ok())
                    .flatten()
            })
        })
        .max()
        .unwrap_or(0)
}

fn required_class_evidence(
    output: &str,
    required_class_nested_paths: &BTreeMap<String, String>,
) -> Vec<RequiredClassEvidence> {
    let lines = output.lines().collect::<Vec<_>>();
    let mut evidence = Vec::new();
    for (class, nested_path) in required_class_nested_paths {
        let class_token = format!("] {class} source:");
        let Some((source_index, source_line)) = lines.iter().enumerate().find(|(_index, line)| {
            line.contains("[class,load]")
                && line.contains(&class_token)
                && line.contains(nested_path)
        }) else {
            continue;
        };
        let Some(loader_line) = lines.get(source_index + 1).filter(|line| {
            line.contains("[class,load]")
                && line.contains("loader:")
                && line.contains("TransformingClassLoader")
        }) else {
            continue;
        };
        evidence.push(RequiredClassEvidence {
            class_name: class.clone(),
            source_line: (*source_line).to_string(),
            loader_line: (*loader_line).to_string(),
        });
    }
    evidence
}

fn proof_passes(
    exit_success: bool,
    timed_out: bool,
    marker_observed: bool,
    locator_dependency_count: u64,
    class_evidence_count: usize,
    required_class_count: usize,
) -> bool {
    exit_success
        && !timed_out
        && marker_observed
        && locator_dependency_count >= 1
        && required_class_count > 0
        && class_evidence_count == required_class_count
}

fn assert_mods_dir_isolated(
    mods_dir: &Path,
    expected_mod: &Path,
    expected_sha256: &str,
) -> eyre::Result<()> {
    let expected_mod = dunce::canonicalize(expected_mod)
        .wrap_err_with(|| format!("Failed to canonicalize {}", expected_mod.display()))?;
    let mut entries = Vec::new();
    for entry in
        fs::read_dir(mods_dir).wrap_err_with(|| format!("Failed to read {}", mods_dir.display()))?
    {
        let entry = entry.wrap_err_with(|| format!("Failed to read {}", mods_dir.display()))?;
        entries.push(entry.path());
    }
    if entries.len() != 1 {
        eyre::bail!(
            "Clean loader mods directory must contain exactly one entry; found {:?}",
            entries
        );
    }
    let only = &entries[0];
    let metadata = fs::symlink_metadata(only)
        .wrap_err_with(|| format!("Failed to inspect {}", only.display()))?;
    if metadata.file_type().is_symlink() || !metadata.file_type().is_file() {
        eyre::bail!(
            "Clean loader mods entry must be one regular non-symlink file: {}",
            only.display()
        );
    }
    let actual = dunce::canonicalize(only)
        .wrap_err_with(|| format!("Failed to canonicalize {}", only.display()))?;
    if actual != expected_mod {
        eyre::bail!(
            "Clean loader mods entry is not the installed release JAR: {}",
            actual.display()
        );
    }
    verify_expected_sha256(
        "isolated installed release JAR",
        expected_sha256,
        &sha256_file(&actual)?,
    )?;
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
    for name in JAVA_ENVIRONMENT_VARIABLES {
        command.env_remove(name);
    }
    configure_process_group(&mut command);
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
    let mut cancelled = false;
    let status = loop {
        if let Some(status) = child.try_wait().wrap_err("Failed to poll child process")? {
            break status;
        }
        if cancellation_token.is_cancelled() {
            cancelled = true;
            break terminate_process_tree(&mut child)?;
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
            break terminate_process_tree(&mut child)?;
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
    if cancelled {
        cancellation_token.bail_if_cancelled()?;
    }
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

#[cfg(windows)]
fn configure_process_group(command: &mut Command) {
    use std::os::windows::process::CommandExt;
    const CREATE_NEW_PROCESS_GROUP: u32 = 0x0000_0200;
    command.creation_flags(CREATE_NEW_PROCESS_GROUP);
}

#[cfg(unix)]
fn configure_process_group(command: &mut Command) {
    use std::os::unix::process::CommandExt;
    command.process_group(0);
}

fn terminate_process_tree(child: &mut Child) -> eyre::Result<ExitStatus> {
    if let Some(status) = child
        .try_wait()
        .wrap_err("Failed to poll process before tree termination")?
    {
        return Ok(status);
    }
    request_process_tree_termination(child.id(), false)?;
    if let Some(status) = wait_child_bounded(child, Duration::from_secs(5))? {
        return Ok(status);
    }
    request_process_tree_termination(child.id(), true)?;
    if let Err(error) = child.kill()
        && child
            .try_wait()
            .wrap_err("Failed to poll root process after force-kill race")?
            .is_none()
    {
        return Err(error).wrap_err("Failed to force-kill root process after tree termination");
    }
    wait_child_bounded(child, Duration::from_secs(5))?
        .ok_or_else(|| eyre::eyre!("Process tree did not terminate and reap within 10 seconds"))
}

fn wait_child_bounded(child: &mut Child, timeout: Duration) -> eyre::Result<Option<ExitStatus>> {
    let deadline = Instant::now() + timeout;
    loop {
        if let Some(status) = child
            .try_wait()
            .wrap_err("Failed to poll process while awaiting cleanup")?
        {
            return Ok(Some(status));
        }
        if Instant::now() >= deadline {
            return Ok(None);
        }
        thread::sleep(Duration::from_millis(50));
    }
}

#[cfg(windows)]
fn request_process_tree_termination(process_id: u32, _graceful: bool) -> eyre::Result<()> {
    let status = Command::new("taskkill")
        .args(["/PID", &process_id.to_string(), "/T", "/F"])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .wrap_err("Failed to start taskkill for process-tree cleanup")?;
    if !status.success() {
        tracing::warn!(
            process_id,
            %status,
            "taskkill did not report success; root-process kill fallback will run"
        );
    }
    Ok(())
}

#[cfg(unix)]
fn request_process_tree_termination(process_id: u32, force: bool) -> eyre::Result<()> {
    let signal = if force { "-KILL" } else { "-TERM" };
    let status = Command::new("kill")
        .args([signal, "--", &format!("-{process_id}")])
        .status()
        .wrap_err("Failed to start kill for process-group cleanup")?;
    if !status.success() {
        tracing::warn!(
            process_id,
            %status,
            "kill did not report success; root-process kill fallback will run"
        );
    }
    Ok(())
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

fn write_report_and_manifest(
    path: Option<&Path>,
    report: &CleanLoaderProbeReport,
) -> eyre::Result<()> {
    let Some(path) = path else {
        return Ok(());
    };
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    }
    let mut json = facet_json::to_string_pretty(report)?;
    json.push('\n');
    fs::write(path, json).wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    let report_hash = sha256_file(path)?;
    let mut logs = Vec::new();
    for log in [&report.install_log, &report.launch_log]
        .into_iter()
        .flatten()
    {
        let log = PathBuf::from(log);
        if log.is_file() {
            logs.push(EvidenceFileHash {
                path: log.display().to_string(),
                sha256: sha256_file(&log)?,
            });
        }
    }
    let manifest = CleanLoaderEvidenceManifest {
        schema_version: 1,
        source_commit: report.source_commit.clone(),
        report: EvidenceFileHash {
            path: path.display().to_string(),
            sha256: report_hash,
        },
        logs,
    };
    let manifest_path = path.with_extension("evidence.json");
    let mut manifest_json = facet_json::to_string_pretty(&manifest)?;
    manifest_json.push('\n');
    fs::write(&manifest_path, manifest_json)
        .wrap_err_with(|| format!("Failed to write {}", manifest_path.display()))?;
    Ok(())
}

fn source_commit() -> String {
    let source_dir = Path::new(env!("CARGO_MANIFEST_DIR"));
    Command::new("git")
        .args(["-C"])
        .arg(source_dir)
        .args(["rev-parse", "HEAD"])
        .output()
        .ok()
        .filter(|output| output.status.success())
        .and_then(|output| String::from_utf8(output.stdout).ok())
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| env!("GIT_REVISION").to_string())
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
    fn proof_requires_successful_untimed_exit_locator_and_every_class() {
        assert!(proof_passes(true, false, true, 1, 2, 2));
        assert!(!proof_passes(false, false, true, 1, 2, 2));
        assert!(!proof_passes(true, true, true, 1, 2, 2));
        assert!(!proof_passes(true, false, false, 1, 2, 2));
        assert!(!proof_passes(true, false, true, 0, 2, 2));
        assert!(!proof_passes(true, false, true, 1, 1, 2));
    }

    #[test]
    fn locator_count_requires_a_positive_found_count() {
        assert_eq!(
            loader_locator_dependency_count(
                "[INFO] [JarInJarDependencyLocator/]: Found 0 dependencies\n\
                 [INFO] [JarInJarDependencyLocator/]: Found 2 dependencies"
            ),
            2
        );
        assert_eq!(
            loader_locator_dependency_count(
                "[INFO] Loaded JarInJarDependencyLocator\n[INFO] Found one dependency"
            ),
            0
        );
    }

    #[test]
    fn required_class_evidence_correlates_name_nested_source_and_loader() {
        let expected = BTreeMap::from([
            (
                "org.example.A".to_string(),
                "META-INF/jarjar/probe.jar".to_string(),
            ),
            (
                "org.example.B".to_string(),
                "META-INF/jarjar/probe.jar".to_string(),
            ),
        ]);
        let output = "\
[info ][class,load] org.example.A source: union:/sfm.jar!/META-INF/jarjar/probe.jar!/\n\
[debug][class,load] loader: TransformingClassLoader\n\
[info ][class,load] org.example.B source: union:/sfm.jar!/META-INF/jarjar/probe.jar!/\n\
[debug][class,load] loader: AppClassLoader\n";
        let evidence = required_class_evidence(output, &expected);
        assert_eq!(
            evidence,
            vec![RequiredClassEvidence {
                class_name: "org.example.A".to_string(),
                source_line: "[info ][class,load] org.example.A source: union:/sfm.jar!/META-INF/jarjar/probe.jar!/".to_string(),
                loader_line: "[debug][class,load] loader: TransformingClassLoader".to_string(),
            }]
        );
    }

    #[test]
    fn explicit_sha256_mismatch_is_rejected() {
        let error = verify_expected_sha256(
            "release",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
        )
        .expect_err("mismatch must fail");
        assert!(error.to_string().contains("SHA-256 mismatch"));
        let _error = normalize_sha256("too-short").expect_err("invalid expected hash must fail");
    }

    #[test]
    fn loose_nested_copy_is_detected_even_when_renamed() {
        let temp = tempfile::tempdir().expect("temp");
        let installed = temp.path().join("mods").join("sfm.jar");
        fs::create_dir_all(installed.parent().expect("mods parent")).expect("mods");
        fs::write(&installed, b"release").expect("release");
        let renamed = temp.path().join("libraries").join("renamed.jar");
        fs::create_dir_all(renamed.parent().expect("libraries parent")).expect("libraries");
        fs::write(&renamed, b"nested").expect("nested");
        let hashes = BTreeSet::from([sha256_bytes(b"nested")]);
        let copies =
            loose_nested_artifact_copies(temp.path(), &installed, &hashes).expect("scan copies");
        assert_eq!(
            copies,
            vec![dunce::canonicalize(renamed).unwrap().display().to_string()]
        );
    }

    #[test]
    fn mods_isolation_rejects_any_second_entry() {
        let temp = tempfile::tempdir().expect("temp");
        let mods = temp.path().join("mods");
        fs::create_dir_all(&mods).expect("mods");
        let release = mods.join("sfm.jar");
        fs::write(&release, b"release").expect("release");
        fs::create_dir(mods.join("unexpected-directory")).expect("extra");
        let error = assert_mods_dir_isolated(&mods, &release, &sha256_bytes(b"release"))
            .expect_err("second entry must fail");
        assert!(error.to_string().contains("exactly one entry"));
    }

    #[test]
    fn zip_validation_rejects_duplicate_and_unsafe_entries() {
        let duplicate = zip_fixture(&["Same.class", "same.class"]);
        let mut duplicate =
            ZipArchive::new(std::io::Cursor::new(duplicate)).expect("duplicate zip");
        let duplicate_error =
            validate_zip_archive(&mut duplicate, "duplicate").expect_err("duplicate must fail");
        assert!(
            duplicate_error
                .to_string()
                .contains("Case-colliding ZIP entry")
        );

        let unsafe_zip = zip_fixture(&["../escape.class"]);
        let mut unsafe_zip = ZipArchive::new(std::io::Cursor::new(unsafe_zip)).expect("unsafe zip");
        let unsafe_error =
            validate_zip_archive(&mut unsafe_zip, "unsafe").expect_err("unsafe must fail");
        assert!(unsafe_error.to_string().contains("Unsafe ZIP entry"));
    }

    #[test]
    fn release_inspection_rejects_duplicate_metadata_identity_and_path() {
        let temp = tempfile::tempdir().expect("temp");
        let nested = nested_fixture_bytes("org/example/Probe.class");
        let jar = temp.path().join("duplicate-metadata.jar");
        write_release_fixture(&jar, Some(&nested), true);
        let error = inspect_release_jar(&jar, &["org.example:probe".to_string()], &[])
            .expect_err("duplicate metadata identity must fail");
        assert!(
            error
                .to_string()
                .contains("Duplicate nested Maven identity")
        );
    }

    #[test]
    fn process_tree_termination_reaps_the_root() {
        let mut command = long_running_process();
        configure_process_group(&mut command);
        command
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .stdin(Stdio::null());
        let mut child = command.spawn().expect("long-running child");
        let status = terminate_process_tree(&mut child).expect("tree cleanup");
        assert!(!status.success());
        assert!(child.try_wait().expect("reap check").is_some());
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

    fn zip_fixture(names: &[&str]) -> Vec<u8> {
        let mut cursor = std::io::Cursor::new(Vec::new());
        {
            let mut writer = ZipWriter::new(&mut cursor);
            for name in names {
                writer
                    .start_file(*name, SimpleFileOptions::default())
                    .expect("entry");
                writer.write_all(b"bytes").expect("bytes");
            }
            writer.finish().expect("zip finish")
        };
        cursor.into_inner()
    }

    #[cfg(windows)]
    fn long_running_process() -> Command {
        let mut command = Command::new("cmd");
        command.args([
            "/C",
            "powershell -NoProfile -NonInteractive -Command Start-Sleep -Seconds 30",
        ]);
        command
    }

    #[cfg(unix)]
    fn long_running_process() -> Command {
        let mut command = Command::new("sh");
        command.args(["-c", "sleep 30 & wait"]);
        command
    }
}
